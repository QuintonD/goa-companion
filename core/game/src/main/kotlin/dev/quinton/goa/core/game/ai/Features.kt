package dev.quinton.goa.core.game.ai

import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.game.engine.EngineData
import dev.quinton.goa.core.game.engine.GameState
import dev.quinton.goa.core.game.engine.HeroState
import dev.quinton.goa.core.game.engine.Step
import dev.quinton.goa.core.game.engine.heavyImmune
import dev.quinton.goa.core.game.engine.itemBonus
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.catalog.Stat
import dev.quinton.goa.core.model.match.Team
import kotlin.math.max
import kotlin.math.min

/**
 * The inputs of the learned evaluation (AI.md "Learned evaluation", ADR-047 part B), read from one
 * state from one team's point of view. Team-size invariant: every hero gets the same [HERO] vector
 * (position against the Battle Zone and the lane, level and coins, hand and the printed numbers of
 * its cards, items, the played card, exposure-style threats, its neighbourhood), pooled per team as
 * sum and max; then the [GLOBAL] features (the hand evaluation's twelve terms, the lane, Life
 * counters, round and turn, team sizes). Like [Evaluator] it reads only the state it is given — a
 * determinized copy in a search — and nothing names a hero. Pure Kotlin (commonMain-safe).
 *
 * Changing a name, its order or its meaning changes [SCHEMA_HASH]: the checked-in weights then fail
 * `LearnedNetTest` until they are retrained (AI.md "How to retrain"). The hand evaluation's terms
 * among them are computed with the frozen [KNOBS] and [SCALES] the net was trained on, never a
 * profile's: a change to `AiPlayers.TUNED` (ADR-047 part A's 3v3 knobs) does not move the net's inputs.
 */
internal class Features(data: EngineData) {
    val ev: Evaluator = Evaluator(data, KNOBS)

    /**
     * Both points of view of [state]: Red's features into `out[0 until SIZE]`, Blue's into
     * `out[SIZE until 2 * SIZE]`.
     */
    fun extract(state: GameState, out: FloatArray) {
        val ctx = ev.context(state)
        val heroes = state.heroes
        val per = Array(heroes.size) { FloatArray(HERO.size) }
        val around = Around(ctx)
        for ((i, h) in heroes.withIndex()) heroVector(ctx, around, h, per[i])
        val terms = ctx.termsRed()
        for ((t, team) in listOf(Team.RED, Team.BLUE).withIndex()) {
            val base = t * SIZE
            out.fill(0f, base, base + SIZE)
            pool(heroes, per, team, out, base + OWN)
            pool(heroes, per, team.other(), out, base + ENEMY)
            global(ctx, team, terms, out, base + GLOBAL_AT)
        }
    }

    /** Where each team's heroes and minions stand, once per state. */
    private class Around(ctx: Ctx) {
        val heroes: Map<Team, List<Pair<String, Hex>>> = Team.entries.associateWith { t ->
            ctx.state.heroes.filter { it.team == t && it.at != null }.map { it.id to it.at!! }
        }
        val minions: Map<Team, List<Hex>> = Team.entries.associateWith { t ->
            ctx.state.minions.filter { it.team == t && it.at != null }.map { it.at!! }
        }
        val targets: Map<Team, List<Hex>> = Team.entries.associateWith { t ->
            ctx.state.minions.filter { it.team == t && it.at != null && !ctx.g.heavyImmune(it.id) }.map { it.at!! }
        }
    }

    private fun Team.other(): Team = if (this == Team.RED) Team.BLUE else Team.RED

    /** Sum and max over the heroes of [team] (an empty team leaves zeros). */
    private fun pool(heroes: List<HeroState>, per: Array<FloatArray>, team: Team, out: FloatArray, at: Int) {
        var first = true
        for ((i, h) in heroes.withIndex()) {
            if (h.team != team) continue
            val v = per[i]
            for (k in v.indices) {
                out[at + k] += v[k]
                out[at + HERO.size + k] = if (first) v[k] else max(out[at + HERO.size + k], v[k])
            }
            first = false
        }
    }

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    private fun heroVector(ctx: Ctx, around: Around, h: HeroState, v: FloatArray) {
        val state = ctx.state
        val hi = ctx.info(h.id)
        val g = ctx.g
        val bodies = h.at?.let {
            listOf(it) +
                state.pieces.filter { p -> p.heroBody == h.id && p.at != null }.map { p -> p.at!! }
        }
            .orEmpty()
        var k = 0
        fun put(x: Double) {
            v[k++] = x.toFloat()
        }
        fun flag(b: Boolean) = put(if (b) 1.0 else 0.0)
        val at = h.at
        flag(at != null)
        val spawn = ctx.map.throneSpawns[h.team].orEmpty().firstOrNull()
        put(
            if (at == null) {
                (-4.0 + (spawn?.let { ctx.presence(h.team, it) } ?: -4.0)) / 4
            } else {
                bodies.maxOf { ctx.presence(h.team, it) } / 4
            },
        )
        val dz = if (at == null) 4 else bodies.minOf { fightDistance(ctx, it) }
        put(min(dz, 12) / 6.0)
        flag(at != null && dz == 0)
        put(if (at == null) 0.0 else bodies.maxOf { progress(ctx, h.team, it) } / 20)
        put(h.level / 8.0)
        put(h.coins / 8.0)
        flag(h.coins >= h.level && h.level < 8)
        flag(h.ultimateUnlocked)
        put(hi.tier / 4.0)
        val hand = hi.hand
        val plays = hi.playsLeft
        put(hand.size / 5.0)
        put(plays / 4.0)
        put(max(0, hand.size - plays) / 3.0)
        put(max(0, plays - hand.size) / 3.0)
        val attacks = hand.filter { it.attackCard }
        put(attacks.size / 3.0)
        put((attacks.maxOfOrNull { it.attack + hi.attackItems } ?: 0) / 8.0)
        put((hand.maxOfOrNull { it.reach + if (it.attackCard && it.ranged) hi.rangeItems else 0 } ?: 0) / 4.0)
        val def = hi.defenseNow
        put(if (def.cards == 0) 0.0 else max(def.best, def.bestIgnoresMods).coerceAtLeast(0) / 8.0)
        put(def.cards / 3.0)
        flag(def.blocksMelee || def.blocksRanged)
        put((hand.maxOfOrNull { it.initiative } ?: 0) / 12.0)
        put((hand.minOfOrNull { it.initiative } ?: 0) / 12.0)
        put((hand.maxOfOrNull { it.movement } ?: 0) / 4.0)
        put(hand.sumOf { it.rating } / 15.0)
        val played = hi.unresolved.firstOrNull()
        flag(played != null)
        put((played?.initiative ?: 0) / 12.0)
        put(if (played != null && played.attackCard) (played.attack + hi.attackItems) / 8.0 else 0.0)
        put((played?.reach ?: 0) / 4.0)
        put((played?.movement ?: 0) / 4.0)
        put(hi.attackItems / 3.0)
        put(hi.defenseItems / 3.0)
        put(hi.rangeItems / 3.0)
        put(g.itemBonus(h.id, Stat.MOVEMENT) / 3.0)
        put(g.itemBonus(h.id, Stat.INITIATIVE) / 3.0)
        put(if (at == null) 0.0 else bodies.maxOf { ctx.exposure(hi, it) } / 10)
        put(if (at == null) 0.0 else bodies.maxOf { ctx.opportunity(hi, it) } / 4.5)
        put(ctx.killCost(hi) / 30)
        put(ctx.discardCost(hi) / 3)
        put(if (at == null) 0.0 else ctx.minionMod(at, h.team) / 2.0)
        val foe = h.team.other()
        fun near(list: List<Hex>, r: Int) = if (at == null) 0 else list.count { HexGrid.distance(at, it) <= r }
        val enemyAt = around.heroes.getValue(foe).map { it.second }
        put(near(enemyAt, 2) / 2.0)
        put(near(enemyAt, 4) / 2.0)
        put(near(around.heroes.getValue(h.team).filter { it.first != h.id }.map { it.second }, 2) / 2.0)
        put(near(around.targets.getValue(foe), 2) / 3.0)
        put(near(around.minions.getValue(h.team), 2) / 3.0)
        put(if (at == null) 1.0 else (enemyAt.minOfOrNull { HexGrid.distance(at, it) } ?: 10).coerceAtMost(10) / 10.0)
        val (killNow, killNext) = if (at == null) 0 to 0 else killThreats(ctx, hi, at)
        put(killNow / 3.0)
        put(killNext / 3.0)
        put(h.counters.entries.sumOf { (key, n) -> Evaluator.counterValue(key, n) } / 5)
        put(state.pieces.count { it.owner == h.id && it.heroBody == null && it.at != null } / 4.0)
        flag(at != null && state.revealed && hi.unresolved.isEmpty())
        val myInit = played?.initiative
        put(
            if (!state.revealed || myInit == null) {
                0.0
            } else {
                around.heroes.getValue(foe).count { (e, _) ->
                    ctx.info(e).unresolved.firstOrNull()?.let { it.initiative < myInit } == true
                } / 3.0
            },
        )
        put((bodies.size - 1).coerceAtLeast(0).toDouble())
        check(k == HERO.size) { "Hero features: wrote $k of ${HERO.size}" }
    }

    /** Enemy cards that can reach [at] and would beat the hero's best defense there: this turn, next turn. */
    private fun killThreats(ctx: Ctx, hi: Ctx.HeroInfo, at: Hex): Pair<Int, Int> {
        val mod = ctx.minionMod(at, hi.h.team)
        var now = 0
        var next = 0
        for ((e, list) in hi.threats) {
            val d = HexGrid.distance(e.at!!, at)
            for (t in list) {
                if (d > t.reach) continue
                val set = if (t.now) hi.defenseNow else hi.defenseNext
                val kills = when {
                    set.cards == 0 -> true
                    !t.p.attackCard -> false
                    t.p.ranged && set.blocksRanged -> false
                    !t.p.ranged && set.blocksMelee -> false
                    else -> t.attackTotal > set.total(mod)
                }
                if (kills) if (t.now) now++ else next++
            }
        }
        return now to next
    }

    private fun fightDistance(ctx: Ctx, at: Hex): Int = ctx.map.laneZones.keys.mapNotNull { ctx.state.battleZones[it] }
        .minOfOrNull { ctx.map.distanceToZone(at, it) } ?: 0

    /** Spaces nearer the enemy Throne than the own one, averaged over the lanes. */
    private fun progress(ctx: Ctx, team: Team, at: Hex): Double {
        var v = 0.0
        for (zones in ctx.map.laneZones.values) {
            val own = if (team == Team.RED) zones.first() else zones.last()
            val theirs = if (team == Team.RED) zones.last() else zones.first()
            v += ctx.map.distanceToZone(at, own) - ctx.map.distanceToZone(at, theirs)
        }
        return v / max(1, ctx.map.laneZones.size)
    }

    @Suppress("LongMethod")
    private fun global(ctx: Ctx, team: Team, terms: DoubleArray, out: FloatArray, at: Int) {
        val state = ctx.state
        val sign = if (team == Team.RED) 1.0 else -1.0
        val enemy = team.other()
        var k = at
        fun put(x: Double) {
            out[k++] = x.toFloat()
        }
        fun flag(b: Boolean) = put(if (b) 1.0 else 0.0)
        for (term in Term.entries) put(sign * terms[term.ordinal] / 20)
        val tuned = SCALES
        put(sign * Term.entries.sumOf { terms[it.ordinal] * (tuned[it] ?: 1.0) } / 30)
        // The lane(s), averaged.
        val lanes = ctx.map.laneZones
        var toWin = 0.0
        var toLose = 0.0
        var waves = 0.0
        var matchWin = 0.0
        var matchLose = 0.0
        val own = DoubleArray(3)
        val theirs = DoubleArray(3)
        for ((lane, zones) in lanes) {
            val bz = state.battleZones[lane] ?: continue
            val i = zones.indexOf(bz)
            if (i < 0) continue
            val redToWin = zones.lastIndex - i
            val win = if (team == Team.RED) redToWin else i
            val lose = if (team == Team.RED) i else redToWin
            toWin += win
            toLose += lose
            if (win == 1) matchWin += 1.0
            if (lose == 1) matchLose += 1.0
            waves += state.wavesLeft[lane] ?: 0
            for (m in state.minions) {
                if (m.lane != lane || m.at == null) continue
                val arr = if (m.team == team) own else theirs
                arr[m.type.index()] += 1.0
            }
        }
        val n = max(1, lanes.size).toDouble()
        put(toWin / n / 4)
        put(toLose / n / 4)
        put(waves / n / 5)
        put(matchWin / n)
        put(matchLose / n)
        for (x in own) put(x / n / 3)
        for (x in theirs) put(x / n / 3)
        put(pushPotential(ctx, team) / 3)
        put(pushPotential(ctx, enemy) / 3)
        val lifeOwn = state.lifeCounters[team] ?: 0
        val lifeEnemy = state.lifeCounters[enemy] ?: 0
        put(lifeOwn / 10.0)
        put(lifeEnemy / 10.0)
        put(Evaluator.lifeValue(lifeOwn) / 50)
        put(Evaluator.lifeValue(lifeEnemy) / 50)
        // One defeat away from losing on Life counters.
        flag(state.heroes.any { it.team == team && lifeOwn - ctx.info(it.id).tier <= 0 })
        flag(state.heroes.any { it.team == enemy && lifeEnemy - ctx.info(it.id).tier <= 0 })
        put(state.round / 8.0)
        put(state.turn / 4.0)
        flag(state.revealed)
        flag(ctx.roundOver)
        flag(state.step == Step.PLAY)
        put(state.heroes.count { it.team == team } / 5.0)
        put(state.heroes.count { it.team == enemy } / 5.0)
        put(if (state.tieBreaker == team) 1.0 else -1.0)
        check(k - at == GLOBAL.size) { "Global features: wrote ${k - at} of ${GLOBAL.size}" }
    }

    private fun MinionType.index(): Int = when (this) {
        MinionType.MELEE -> 0
        MinionType.RANGED -> 1
        else -> 2
    }

    /** Enemy minions [team] can still take this round (as [Ctx]'s Push Potential). */
    private fun pushPotential(ctx: Ctx, team: Team): Double {
        if (ctx.roundOver) return 0.0
        var pp = 0.0
        for (lane in ctx.map.laneZones.keys) {
            val bz = ctx.state.battleZones[lane] ?: continue
            for (h in ctx.state.heroes) {
                if (h.team != team) continue
                val at = h.at ?: continue
                if (ctx.map.distanceToZone(at, bz) > 6) continue
                val hi = ctx.info(h.id)
                val attacks = hi.hand.count { it.attackCard } + hi.unresolved.count { it.attackCard }
                pp += 0.5 * min(attacks, max(hi.playsLeft, hi.unresolved.size))
            }
        }
        return pp
    }

    companion object {
        /**
         * The hand evaluation's knobs for the net's inputs: the defaults, as when the checked-in net
         * was trained (no team-size knobs: Push Potential 0.5 per attack card, no defenseless floor).
         */
        val KNOBS: EvalKnobs = EvalKnobs()

        /** The scales of the "tunedScore" input: v0.8's `TUNED` (exposure × 0.3, minion opportunity × 2). */
        val SCALES: Map<Term, Double> = mapOf(Term.EXPOSURE to 0.3, Term.OPPORTUNITY to 2.0)

        /** Per hero, pooled per team as sum then max. */
        val HERO: List<String> = listOf(
            "onBoard", "presence", "fightDistance", "inBattleZone", "laneProgress",
            "level", "coins", "levelDue", "ultimate", "lowestTier",
            "hand", "playsLeft", "spare", "missing",
            "attackCards", "bestAttack", "bestReach", "bestDefense", "defenders", "blocks",
            "maxInitiative", "minInitiative", "maxMovement", "handRating",
            "played", "playedInitiative", "playedAttack", "playedReach", "playedMovement",
            "attackItems", "defenseItems", "rangeItems", "movementItems", "initiativeItems",
            "exposure", "opportunity", "killCost", "discardCost", "minionModifier",
            "enemiesWithin2", "enemiesWithin4", "alliesWithin2", "enemyMinionsWithin2", "ownMinionsWithin2",
            "nearestEnemy", "killThreatsNow", "killThreatsNext",
            "counters", "pieces", "acted", "enemiesActingAfter", "extraBodies",
        )

        val GLOBAL: List<String> = Term.entries.map { "term:${it.name}" } + listOf(
            "tunedScore",
            "pushesToWin", "pushesToLose", "wavesLeft", "matchPointOwn", "matchPointEnemy",
            "ownMelee", "ownRanged", "ownHeavy", "enemyMelee", "enemyRanged", "enemyHeavy",
            "pushPotentialOwn", "pushPotentialEnemy",
            "lifeOwn", "lifeEnemy", "lifeValueOwn", "lifeValueEnemy", "lastLifeOwn", "lastLifeEnemy",
            "round", "turn", "revealed", "roundOver", "playStep", "teamSize", "enemyTeamSize", "tieBreaker",
        )

        const val OWN: Int = 0
        val ENEMY: Int = 2 * HERO.size
        val GLOBAL_AT: Int = 4 * HERO.size
        val SIZE: Int = GLOBAL_AT + GLOBAL.size

        /** Every input's name, in order. */
        val NAMES: List<String> = listOf("own", "enemy").flatMap { side ->
            listOf("sum", "max").flatMap { p -> HERO.map { "$side:$p:$it" } }
        } + GLOBAL

        /** FNV-1a of the names and the version of their meaning: what the weights were trained on. */
        const val MEANING_VERSION: Int = 1
        val SCHEMA_HASH: Long = run {
            var x = -0x340d631b7bdddcdbL
            for (c in (NAMES + "v$MEANING_VERSION").joinToString("|")) {
                x = (x xor c.code.toLong()) * 0x100000001b3L
            }
            x
        }
    }
}
