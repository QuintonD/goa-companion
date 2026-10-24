package dev.quinton.goa.core.game.ai

import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.game.engine.EngineData
import dev.quinton.goa.core.game.engine.Game
import dev.quinton.goa.core.game.engine.GameState
import dev.quinton.goa.core.game.engine.HeroState
import dev.quinton.goa.core.game.engine.RunMode
import dev.quinton.goa.core.game.engine.Step
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.game.engine.heavyImmune
import dev.quinton.goa.core.game.engine.itemBonus
import dev.quinton.goa.core.game.engine.lowestTier
import dev.quinton.goa.core.game.engine.minionModifier
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.ZoneKind
import dev.quinton.goa.core.model.catalog.Stat
import dev.quinton.goa.core.model.match.Team
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * Knobs of the evaluation that differ by level (STRATEGY.md §4.2): what a level *notices*.
 * [modifierBlind] makes Easy ignore minion defense modifiers; [nearestThreatOnly] makes it look only
 * at the closest enemy.
 */
internal data class EvalKnobs(
    val modifierBlind: Boolean = false,
    val nearestThreatOnly: Boolean = false,
    /** Weight of the opponent's threats next turn (Expert looks further). */
    val nextTurnThreat: Double = 0.22,
)

/**
 * The static evaluation (STRATEGY.md §2) in coin-equivalents for one team: Lane + Life + Σ our
 * heroes − Σ their heroes. Zero-sum: computed for Red and negated for Blue. Reads only the state it
 * is given — always a determinized copy or the AI's own knowledge, never a human's hidden cards.
 */
internal class Evaluator(val data: EngineData, val knobs: EvalKnobs = EvalKnobs()) {
    fun evaluate(state: GameState, team: Team): Double {
        val red = Ctx(this, state).scoreRed()
        return if (team == Team.RED) red else -red
    }

    fun context(state: GameState): Ctx = Ctx(this, state)

    fun profile(cardId: String): CardProfile = CardProfile.of(data.card(cardId))

    companion object {
        const val WIN: Double = 1000.0
        const val LANE_WEIGHT: Double = 60.0
        const val MINION_MARGIN: Double = 1.5

        /** Chance a revealed enemy attack card that can reach a hero is aimed at it this turn. */
        const val NOW_WEIGHT: Double = 0.6
        /** Per space outside the Battle Zone (a path of 3 spaces is worth about an action, BGG 2898177). */
        const val PRESENCE_PER_SPACE: Double = 0.55
        const val IN_BATTLE_ZONE: Double = 1.0

        fun sigmoid(x: Double): Double = 1.0 / (1.0 + exp(-x))

        fun lifeValue(counters: Int): Double {
            var v = 0.0
            for (i in 1..counters) {
                v += when (i) {
                    1 -> 20.0
                    2 -> 12.0
                    3 -> 8.0
                    else -> 5.0
                }
            }
            return v
        }

        /** P(win the lane) with [k] pushes to win, [m] to lose, [w] Wave counters, [p] for the next push. */
        fun laneWin(k: Int, m: Int, w: Int, p: Double): Double {
            if (k <= 0) return 1.0
            if (m <= 0) return 0.0
            val win = if (k == 1 || w <= 1) 1.0 else laneWin(k - 1, m + 1, w - 1, 0.5)
            val lose = if (m == 1 || w <= 1) 0.0 else laneWin(k + 1, m - 1, w - 1, 0.5)
            return p * win + (1 - p) * lose
        }
    }
}

/** Distances on a map, cached per map id. */
internal class MapInfo private constructor(g: Game) {
    private val geo = g.geo
    private val zoneDist = HashMap<String, Map<Hex, Int>>()

    /** The lane index of a zone in each lane (Red Throne = 0). */
    val laneZones: Map<String, List<String>> = geo.lanes.mapValues { it.value.zones }

    val throneSpawns: Map<Team, List<Hex>> = Team.entries.associateWith { t ->
        geo.map.zones.filter { it.kind == ZoneKind.THRONE && it.team == t }.flatMap { geo.zoneSpaces[it.id].orEmpty() }
            .filter { geo.spaces[it]?.spawn != null }
    }

    @Synchronized
    fun distanceToZone(hex: Hex, zone: String): Int {
        val table = zoneDist.getOrPut(zone) {
            val targets = geo.zoneSpaces[zone].orEmpty()
            geo.spaces.keys.associateWith { h -> targets.minOfOrNull { HexGrid.distance(h, it) } ?: 0 }
        }
        return table[hex] ?: targets(zone).minOfOrNull { HexGrid.distance(hex, it) } ?: 0
    }

    private fun targets(zone: String): List<Hex> = geo.zoneSpaces[zone].orEmpty()

    companion object {
        private val cache = HashMap<String, MapInfo>()

        fun of(g: Game): MapInfo = synchronized(cache) { cache.getOrPut(g.state.mapId) { MapInfo(g) } }
    }
}

/** One threat: an enemy card that may hit a hero this turn or the next. */
internal class Threat(val p: CardProfile, val attackTotal: Int, val reach: Int, val weight: Double)

/** A hero's defense at a moment: best numeric value (before minion modifiers) and its Block answers. */
internal class DefenseSet(
    val cards: Int,
    val best: Int,
    val bestIgnoresMods: Int,
    val blocksRanged: Boolean,
    val blocksMelee: Boolean,
) {
    fun total(mod: Int): Int = max(best + mod, bestIgnoresMods)
}

/**
 * Everything the evaluation derives from one state, computed lazily once. Also answers "how good
 * would this hero be standing at that space" for the rollout policy (no fork needed).
 */
internal class Ctx(val ev: Evaluator, val state: GameState) {
    val g: Game = Game(ev.data, state, emptyList(), RunMode.PROBE)
    val map: MapInfo = MapInfo.of(g)
    private val knobs = ev.knobs

    val roundOver: Boolean = state.step.ordinal >= Step.EOR_EFFECTS.ordinal && state.step != Step.GAME_OVER

    private val heroInfo = HashMap<String, HeroInfo>()
    private val modCache = HashMap<Long, Int>()

    fun info(id: String): HeroInfo = heroInfo.getOrPut(id) { HeroInfo(state.hero(id)!!) }

    inner class HeroInfo(val h: HeroState) {
        val hand: List<CardProfile> = h.cards.filter { it.zone == Zone.HAND }.map { ev.profile(it.id) }
        val unresolved: List<CardProfile> = h.cards.filter { it.zone == Zone.PLAYED }.map { ev.profile(it.id) }
        val all: List<CardProfile> = h.cards.filter {
            it.zone == Zone.HAND || it.zone == Zone.PLAYED || it.zone == Zone.RESOLVED || it.zone == Zone.DISCARD
        }.filter { !it.faceDown }.map { ev.profile(it.id) }
        val attackItems: Int = g.itemBonus(h.id, Stat.ATTACK)
        val defenseItems: Int = g.itemBonus(h.id, Stat.DEFENSE)
        val rangeItems: Int = g.itemBonus(h.id, Stat.RANGE)
        val tier: Int = g.lowestTier(h.id)

        /** Cards this hero must still play this round (PLAY steps left). */
        val playsLeft: Int = when {
            roundOver -> 0
            state.step == Step.TURN_START -> 5 - state.turn
            state.step == Step.PLAY -> (4 - state.turn) + if (h.seat >= state.stepSeat) 1 else 0
            else -> 4 - state.turn
        }

        val defenseNow: DefenseSet by lazy { defenseOf(hand) }

        /** Next turn: one more card will be played; assume it is not the best defender. */
        val defenseNext: DefenseSet by lazy {
            when {
                roundOver || (state.turn == 4 && state.step.ordinal >= Step.REVEAL.ordinal) -> defenseOf(all)
                hand.size <= playsLeft -> defenseOf(hand.sortedByDescending { it.defense ?: 0 }.drop(1))
                else -> defenseOf(hand)
            }
        }

        private fun defenseOf(cards: List<CardProfile>): DefenseSet {
            val defenders = cards.filter { it.canDefend }
            var best = Int.MIN_VALUE / 4
            var ignoring = Int.MIN_VALUE / 4
            for (c in defenders) {
                val d = c.defense ?: if (c.block && !c.blocksMelee && !c.blocksRanged) 5 else continue
                val v = d + defenseItems
                if (c.ignoresMinionModifiers) ignoring = max(ignoring, v) else best = max(best, v)
            }
            return DefenseSet(
                defenders.size, best, ignoring,
                defenders.any { it.blocksRanged }, defenders.any { it.blocksMelee },
            )
        }

        /** The threats every enemy hero poses to a hero of this team (positions of the enemies). */
        val threats: List<Pair<HeroState, List<Threat>>> by lazy { enemyThreats(h.team) }
    }

    fun minionMod(hex: Hex, team: Team): Int {
        if (knobs.modifierBlind) return 0
        val key = (hex.q.toLong() shl 32) xor (hex.r.toLong() and 0xffffffffL) xor (if (team == Team.RED) 1L shl 62 else 0L)
        return modCache.getOrPut(key) { g.minionModifier(hex, team, null) }
    }

    /** Per enemy hero of [team]: the cards it may hit with this turn (weighted) and next turn. */
    private fun enemyThreats(team: Team): List<Pair<HeroState, List<Threat>>> = state.heroes
        .filter { it.team != team && it.at != null }
        .map { e -> e to threatsOf(info(e.id)) }

    private fun threatsOf(e: HeroInfo): List<Threat> {
        val out = ArrayList<Threat>()
        fun add(c: CardProfile, w: Double, approach: Int) {
            if (c.reach <= 0) return
            val reach = c.reach + approach + if (c.attackCard && c.ranged) e.rangeItems else 0
            out += Threat(c, c.attack + e.attackItems, reach, w)
        }
        if (roundOver) {
            e.all.forEach { add(it, 0.2, 0) }
            return out
        }
        val revealed = state.revealed
        val pool = if (revealed) e.unresolved else e.unresolved + e.hand
        // This turn: a revealed card is certain to be played (the target is still a choice).
        if (pool.isNotEmpty() && (e.unresolved.isNotEmpty() || !revealed)) {
            val w = if (revealed) Evaluator.NOW_WEIGHT else Evaluator.NOW_WEIGHT * min(1.0, 2.0 / pool.size)
            pool.forEach { add(it, w, 0) }
        }
        // Next turn: what is left in hand, after an approach with this turn's card.
        val approach = when {
            revealed -> e.unresolved.filter { it.hasMovement }.maxOfOrNull { it.movement } ?: 0
            else -> 0
        }
        if (state.turn < 4) {
            e.hand.forEach { add(it, knobs.nextTurnThreat, approach) }
        } else {
            e.all.forEach { add(it, 0.15, approach) }
        }
        return out
    }

    /** Expected loss of [hero] standing at [at] from enemy attacks (STRATEGY H4), in coin-equivalents. */
    fun exposure(hero: HeroInfo, at: Hex): Double {
        val team = hero.h.team
        val mod = minionMod(at, team)
        var noKill = 1.0
        var noHit = 1.0
        val threats = if (knobs.nearestThreatOnly) {
            hero.threats.minByOrNull { (e, _) -> HexGrid.distance(e.at!!, at) }?.let { listOf(it) } ?: emptyList()
        } else {
            hero.threats
        }
        val nowSet = hero.defenseNow
        val nextSet = hero.defenseNext
        for ((e, list) in threats) {
            val d = HexGrid.distance(e.at!!, at)
            for (t in list) {
                if (d > t.reach) continue
                val set = if (t.weight >= 0.5) nowSet else nextSet
                val kills = when {
                    set.cards == 0 -> true
                    !t.p.attackCard -> false
                    t.p.ranged && set.blocksRanged -> false
                    !t.p.ranged && set.blocksMelee -> false
                    else -> t.attackTotal > set.total(mod)
                }
                if (kills) noKill *= 1 - t.weight else noHit *= 1 - t.weight
            }
        }
        val pKill = min(0.9, 1 - noKill)
        val pHit = (1 - pKill) * (1 - noHit)
        return pKill * killCost(hero) + pHit * discardCost(hero)
    }

    fun killCost(hero: HeroInfo): Double {
        val team = hero.h.team
        val life = state.lifeCounters[team] ?: 0
        val lifeLoss = if (life - hero.tier <= 0) {
            300.0
        } else {
            Evaluator.lifeValue(life) - Evaluator.lifeValue(life - hero.tier)
        }
        val enemies = state.heroes.count { it.team != team }
        return lifeLoss + hero.h.level + hero.tier * (enemies - 1) + 5.0
    }

    fun discardCost(hero: HeroInfo): Double {
        if (roundOver) return 0.0
        val spare = hero.hand.size - hero.playsLeft
        return if (spare >= 1) spareWeight() else 2.5
    }

    private fun spareWeight(): Double = when (state.turn) {
        1 -> 3.0
        2 -> 2.0
        3 -> 1.5
        else -> 0.8
    }

    /** Minions this hero could take with the attack cards it still has, standing at [at]. */
    fun opportunity(hero: HeroInfo, at: Hex): Double {
        if (roundOver) return 0.0
        val team = hero.h.team
        val enemyMinions = state.minions.filter { it.team != team && it.at != null && !g.heavyImmune(it.id) }
        if (enemyMinions.isEmpty()) return 0.0
        var v = 0.0
        val acted = state.revealed && hero.unresolved.isEmpty()
        val nowCards = if (!acted && state.revealed) hero.unresolved.filter { it.attackCard } else emptyList()
        for (c in nowCards) {
            if (enemyMinions.any { HexGrid.distance(at, it.at!!) <= c.reach }) v += 3.0
        }
        val later = hero.hand.filter { it.attackCard }
        if (later.isNotEmpty() && enemyMinions.any { m -> later.any { HexGrid.distance(at, m.at!!) <= it.reach } }) {
            v += 1.5
        }
        return min(v, 4.5)
    }

    /** Lane presence (STRATEGY H5): spaces from [at] to the Battle Zone, a bonus inside it. */
    @Suppress("UNUSED_PARAMETER")
    fun presence(team: Team, at: Hex): Double {
        var v = 0.0
        for (lane in map.laneZones.keys) {
            val bz = state.battleZones[lane] ?: continue
            val d = map.distanceToZone(at, bz)
            v += if (d == 0) Evaluator.IN_BATTLE_ZONE else -Evaluator.PRESENCE_PER_SPACE * d
        }
        return v / max(1, map.laneZones.size)
    }

    /** How good it is for [heroId] to stand at [at] (its own team's point of view). */
    fun placement(heroId: String, at: Hex): Double {
        val hi = info(heroId)
        return presence(hi.h.team, at) + opportunity(hi, at) - exposure(hi, at)
    }

    private fun heroValue(h: HeroState): Double {
        val hi = info(h.id)
        val early = state.round <= 2 && state.heroes.all { it.level <= 3 }
        var v = h.coins * (if (early) 1.3 else 1.0) + h.level * (h.level - 1) / 2.0 + 3.0 * (h.level - 1)
        if (h.coins >= h.level && h.level < 8) v += 1.0
        if (h.ultimateUnlocked) v += 8.0
        if (!roundOver) {
            val needed = hi.playsLeft
            val n = hi.hand.size
            v += 2.5 * min(n, needed) + spareWeight() * max(0, n - needed) - 3.0 * max(0, needed - n)
            v += 0.12 * hi.hand.sumOf { it.rating }
        }
        val at = h.at
        v += if (at == null) {
            val spawn = map.throneSpawns[h.team].orEmpty().firstOrNull()
            -4.0 + (spawn?.let { presence(h.team, it) } ?: -4.0)
        } else {
            placement(h.id, at)
        }
        return v
    }

    private fun laneValueRed(): Double {
        var total = 0.0
        for ((lane, zones) in map.laneZones) {
            val bz = state.battleZones[lane] ?: continue
            val i = zones.indexOf(bz)
            if (i < 0) continue
            val red = state.minions.count { it.team == Team.RED && it.lane == lane && it.at != null }
            val blue = state.minions.count { it.team == Team.BLUE && it.lane == lane && it.at != null }
            val ppRed = pushPotential(Team.RED, bz)
            val ppBlue = pushPotential(Team.BLUE, bz)
            val a = max(0.0, red - ppBlue)
            val b = max(0.0, blue - ppRed)
            var p = Evaluator.sigmoid(1.1 * (a - b))
            if (a > b && 2 * b - a <= 0.25) p = max(p, 0.95)
            if (b > a && 2 * a - b <= 0.25) p = min(p, 0.05)
            val waves = state.wavesLeft[lane] ?: 1
            val w = Evaluator.laneWin(zones.lastIndex - i, i, waves, p)
            total += Evaluator.LANE_WEIGHT * (2 * w - 1) + Evaluator.MINION_MARGIN * (red - blue)
        }
        return total
    }

    /** Enemy minions [team] can still take this round (the designer's Push Potential, STRATEGY L2). */
    private fun pushPotential(team: Team, bz: String): Double {
        if (roundOver) return 0.0
        var pp = 0.0
        for (h in state.heroes) {
            if (h.team != team) continue
            val at = h.at ?: continue
            if (map.distanceToZone(at, bz) > 6) continue
            val hi = info(h.id)
            val attacks = hi.hand.count { it.attackCard } + hi.unresolved.count { it.attackCard }
            pp += 0.5 * min(attacks, max(hi.playsLeft, hi.unresolved.size))
        }
        return pp
    }

    fun scoreRed(): Double {
        state.winner?.let { return if (it == Team.RED) Evaluator.WIN - state.round else -Evaluator.WIN + state.round }
        var v = laneValueRed()
        v += Evaluator.lifeValue(state.lifeCounters[Team.RED] ?: 0) - Evaluator.lifeValue(state.lifeCounters[Team.BLUE] ?: 0)
        for (h in state.heroes) {
            val hv = heroValue(h)
            v += if (h.team == Team.RED) hv else -hv
        }
        return v
    }
}
