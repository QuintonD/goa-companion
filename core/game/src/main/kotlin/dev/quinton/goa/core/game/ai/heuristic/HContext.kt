package dev.quinton.goa.core.game.ai.heuristic

import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.game.ai.CardProfile
import dev.quinton.goa.core.game.engine.CardInst
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.game.engine.Game
import dev.quinton.goa.core.game.engine.GameState
import dev.quinton.goa.core.game.engine.HeroState
import dev.quinton.goa.core.game.engine.MinionState
import dev.quinton.goa.core.game.engine.MoveSpec
import dev.quinton.goa.core.game.engine.RunMode
import dev.quinton.goa.core.game.engine.Step
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.game.engine.cardValue
import dev.quinton.goa.core.game.engine.fastTravelDestinations
import dev.quinton.goa.core.game.engine.heavyImmune
import dev.quinton.goa.core.game.engine.itemBonus
import dev.quinton.goa.core.game.engine.minionModifier
import dev.quinton.goa.core.game.engine.reach
import dev.quinton.goa.core.game.engine.zoneOf
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.board.ZoneKind
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.model.catalog.Stat
import dev.quinton.goa.core.model.match.Team
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Facts for one decision, from the deciding team's determinized view (ADR-085).
 *
 * Hero playbooks read this and nothing else. Enemy hands are not the one sampled hand:
 * [possibleCards] is every card public knowledge still allows, and [hand] is null for a hidden
 * enemy hand. Strength 10 ([HeuristicProfile.knowHands]) is the exception the owner asked for:
 * the hand pool is real, and which card was played facedown is still not.
 *
 * Distances are hex distance. Reach includes a printed pre-attack move and range items, and it
 * does not walk the path, so a wall can make a "reachable" unit illegal; the engine's options
 * are the legal ones, and lookahead is how a playbook sees the text.
 */
class HContext(
    val session: EngineSession,
    val decision: dev.quinton.goa.core.game.Decision?,
    /** The team this answer is for. Scores that name a side use this. */
    val team: Team,
    val profile: HeuristicProfile,
    val playbooks: (String) -> HeroHeuristics = HeroPlaybooks::forHero,
    /** This move ignores minion defense modifiers (a low strength, some of the time). */
    val blindModifiers: Boolean = false,
    /** Cards each enemy has shown since their last level-up. Empty unless the profile counts. */
    val seen: Map<String, Set<String>> = emptyMap(),
    /** Inside a one-ply finish: the caller must not look ahead again. */
    val finishing: Boolean = false,
    /** A state to read instead of [session]'s (a card simulation's result). Same engine data. */
    stateOverride: GameState? = null,
) {
    val state: GameState = stateOverride ?: session.state
    val seat: String = decision?.seat.orEmpty()

    val game: Game by lazy { Game(session.engineData, state, emptyList(), RunMode.PROBE) }

    val round: Int get() = state.round
    val turn: Int get() = state.turn
    val revealed: Boolean get() = state.revealed

    val roundOver: Boolean =
        state.step.ordinal >= Step.EOR_EFFECTS.ordinal && state.step != Step.GAME_OVER

    private val cardCache = HashMap<String, HCard>()
    private val poolCache = HashMap<String, List<PossibleCard>>()
    private val threatCache = HashMap<String, List<ThreatInfo>>()
    private val modCache = HashMap<Long, Int>()

    /** The intent for this turn, derived from the state. Stable for the whole decision. */
    val plan: TurnPlan by lazy { TurnPlan.derive(this) }

    fun hero(id: String): HeroState? = state.hero(id)

    fun own(): HeroState? = state.hero(seat)

    /** This hero's style, or [HeroStyle.DEFAULT] when playbooks are off. */
    fun style(heroId: String): HeroStyle =
        if (profile.heroPlaybooks) playbooks(heroId).style else HeroStyle.DEFAULT

    fun card(id: String): HCard = cardCache.getOrPut(id) {
        HCard.from(CardProfile.of(session.engineData.card(id)))
    }

    fun allies(): List<HeroState> = state.heroes.filter { it.team == team && it.id != seat }

    fun enemies(): List<HeroState> = state.heroes.filter { it.team != team }

    fun distance(a: Hex, b: Hex): Int = HexGrid.distance(a, b)

    fun zoneOf(hex: Hex): String? = game.zoneOf(hex)

    fun battleZone(lane: String): String? = state.battleZones[lane]

    fun lanes(): Set<String> = state.battleZones.keys

    fun inBattleZone(hex: Hex): Boolean {
        val zone = zoneOf(hex) ?: return false
        return state.battleZones.values.any { it == zone }
    }

    /** Hexes from [hex] to the nearest space of [zone]. 0 when [hex] is in the zone. */
    fun distanceToZone(hex: Hex, zone: String): Int {
        if (zoneOf(hex) == zone) return 0
        val spaces = game.geo.zoneSpaces[zone].orEmpty()
        return spaces.minOfOrNull { HexGrid.distance(hex, it) } ?: 0
    }

    fun distanceToBattleZone(hex: Hex): Int {
        val zones = state.battleZones.values
        if (zones.isEmpty()) return 0
        return zones.minOf { distanceToZone(hex, it) }
    }

    /**
     * Minion defense modifier at [hex] for [team] (p.14). 0 when this strength is not looking
     * at modifiers, or when it forgot them for this move.
     */
    fun minionModifier(hex: Hex, team: Team): Int {
        if (blindModifiers || !profile.threatMap) return 0
        val key = (hex.q.toLong() shl 42) xor (hex.r.toLong() shl 1) xor if (team == Team.RED) 1L else 0L
        return modCache.getOrPut(key) { game.minionModifier(hex, team, null) }
    }

    fun heavyImmune(minionId: String): Boolean = game.heavyImmune(minionId)

    fun itemBonus(heroId: String, stat: Stat): Int = game.itemBonus(heroId, stat)

    /** The icon's value now (printed + items + effects), or null when the card has no such icon. */
    fun cardValue(heroId: String, cardId: String, stat: Stat): Int? = game.cardValue(heroId, cardId, stat)

    fun movementDestinations(heroId: String, cardId: String): Set<Hex> {
        val n = game.cardValue(heroId, cardId, Stat.MOVEMENT) ?: return emptySet()
        if (n <= 0) return emptySet()
        return game.reach(MoveSpec(heroId, 0, n, movementAction = true)).keys
    }

    fun fastTravelDestinations(heroId: String): List<Hex> = game.fastTravelDestinations(heroId)

    fun canFastTravel(heroId: String): Boolean = fastTravelDestinations(heroId).isNotEmpty()

    /** Spaces at which [card] can hit from its hero, with range items. An approximation of the path. */
    fun attackReach(heroId: String, card: HCard): Int {
        val base = when {
            card.reach > 0 -> card.reach
            card.attackCard || card.forcesDiscard -> 1
            else -> 0
        }
        if (base == 0) return 0
        val extra = if (card.ranged) game.itemBonus(heroId, Stat.RANGE) else 0
        return base + extra
    }

    /**
     * The deciding hero's hand, the real cards. A teammate's hand is real for a computer team
     * (they may say what they hold). An enemy's hand is real only at strength 10; otherwise null,
     * and the playbook uses [possibleCards].
     */
    fun hand(heroId: String): List<HCard>? {
        val h = state.hero(heroId) ?: return null
        if (h.team != team && !profile.knowHands) return null
        return h.hand.map { card(it.id) }
    }

    fun ownHand(): List<HCard> = hand(seat).orEmpty()

    fun handCount(heroId: String): Int = state.hero(heroId)?.hand?.size ?: 0

    /**
     * Cards this hero still has to play this round, from the public turn: the turns after this one,
     * plus this turn's card while the hero has neither played nor resolved one.
     */
    fun playsLeft(heroId: String): Int {
        val h = state.hero(heroId) ?: return 0
        if (roundOver) return 0
        val later = (4 - state.turn).coerceAtLeast(0)
        val thisTurn = when (state.step) {
            Step.TURN_START -> true
            Step.PLAY, Step.REVEAL, Step.RESOLVE ->
                h.unresolved.isEmpty() && h.hand.isNotEmpty() && heroId !in state.turnRecord.resolved &&
                    (state.step != Step.PLAY || h.seat >= state.stepSeat)
            else -> false
        }
        return later + if (thisTurn) 1 else 0
    }

    /** Hand cards beyond the ones still needed as actions. Negative means a turn will be passed. */
    fun spare(heroId: String): Int = handCount(heroId) - playsLeft(heroId)

    fun coins(heroId: String): Int = state.hero(heroId)?.coins ?: 0

    fun level(heroId: String): Int = state.hero(heroId)?.level ?: 1

    fun life(team: Team): Int = state.lifeCounters[team] ?: 0

    /** Life counters divided by the death cost of the team's lowest-level hero. */
    fun deathsToLose(team: Team): Int {
        val tier = state.heroes.filter { it.team == team }.minOfOrNull { deathTier(it.level) } ?: 1
        val counters = life(team)
        return if (tier <= 0) counters else counters / tier
    }

    /**
     * Cards [heroId] might still hold or be resolving, from public knowledge.
     * Our own team (and strength 10) gets the real hand. A hidden enemy gets one weighted card
     * per colour that is still in the round, never the sampled hand.
     */
    fun possibleCards(heroId: String): List<PossibleCard> = poolCache.getOrPut(heroId) { poolOf(heroId) }

    /** Enemy cards that can hit [hex] (this turn, and the next when the profile looks ahead). */
    fun threatsOn(hex: Hex, victimTeam: Team): List<Threat> {
        if (!profile.threatMap) return emptyList()
        val out = ArrayList<Threat>()
        for (enemy in state.heroes) {
            if (enemy.team == victimTeam || enemy.at == null) continue
            val from = enemy.at!!
            for (t in threatsFrom(enemy.id)) {
                if (distance(from, hex) <= t.reach) out += t
            }
        }
        return out
    }

    fun enemiesWhoCanAttack(hex: Hex, victimTeam: Team, thisTurnOnly: Boolean = false): List<String> =
        threatsOn(hex, victimTeam).filter { !thisTurnOnly || it.thisTurn }.map { it.enemyId }.distinct()

    /**
     * Expected loss, in coin-equivalents, of [heroId] standing at [at] (STRATEGY H4).
     * 0 when the strength has no threat map. Uses the possible pool, not one guessed hand.
     */
    fun exposure(heroId: String, at: Hex): Double {
        if (!profile.threatMap) return 0.0
        val h = state.hero(heroId) ?: return 0.0
        val (pKill, pHit) = threatChances(h) { at }
        var v = pKill * killWorth(heroId) + pHit * discardWorth(h)
        if (profile.heroPlaybooks) {
            for (e in state.heroes) {
                if (e.team != h.team) v += playbooks(e.id).threatAdjust(this, e.id, at)
            }
        }
        return v
    }

    /** How good it is for [heroId] to stand at [at], in coin-equivalents, before a full evaluation. */
    fun placement(heroId: String, at: Hex): Double {
        val style = style(heroId)
        var v = presence(at) * style.laneWeight
        v += opportunity(heroId, at) * 2.0 * profile.knob("opp", 1.0)
        v += formation(heroId, at, style)
        if (profile.threatMap) v -= exposurePenalty(heroId, at) * profile.knob("fear", 1.0)
        if (profile.turnPlans && heroId == seat) v += planPull(at)
        // G51: once the push is decided, stand in the zone it is moving into.
        if (profile.laneTiming) v += zonePull(heroId, at)
        return v
    }

    /** How much [at] agrees with [goalZone]. */
    private fun zonePull(heroId: String, at: Hex): Double {
        val goal = goalZone(heroId) ?: return 0.0
        val d = distanceToZone(at, goal)
        return if (d == 0) 2.5 else -0.7 * d
    }

    /** Lane presence: inside the Battle Zone is the fight; each space outside costs a fraction of an action. */
    fun presence(at: Hex): Double {
        val zones = state.battleZones.values
        if (zones.isEmpty()) return 0.0
        var v = 0.0
        for (bz in zones) {
            val d = distanceToZone(at, bz)
            v += if (d == 0) 1.0 else -0.55 * d
        }
        return v / zones.size
    }

    /** Attack cards in hand that reach an enemy minion from [at]. Capped so it does not swamp the lane. */
    fun opportunity(heroId: String, at: Hex): Double {
        val h = state.hero(heroId) ?: return 0.0
        val attacks = attackCardsOf(h)
        if (attacks.isEmpty()) return 0.0
        val minions = state.minions.filter { it.team != h.team && it.at != null && !heavyImmune(it.id) }
        var v = 0.0
        for (c in attacks) {
            val reach = attackReach(heroId, c)
            if (minions.any { distance(at, it.at!!) <= reach }) v += 1.6
        }
        return v.coerceAtMost(4.8)
    }

    /**
     * Worth of defeating the minion, to [team]: coins, the battle margin, the ranged zone,
     * and a push when it is the last one. Our own minion is negative (do not kill it).
     * Immune heavies are not a target.
     */
    fun minionWorth(minionId: String): Double {
        val m = state.minion(minionId) ?: return 0.0
        if (m.team == team) return -6.0
        if (heavyImmune(minionId)) return -3.0
        if (!profile.on("laneMinion", true)) return oldMinionWorth(m)
        // G58: the coins, plus the change in the lane. The last minion is the push, not a flat bonus.
        var v = if (m.type == MinionType.HEAVY) 4.0 else 2.0
        v += laneKillValue(m.lane) * profile.knob("laneShare", 1.0)
        if (m.type == MinionType.RANGED && rangedCovers(m, team)) v += 4.0 // G62
        if (m.type != MinionType.RANGED && adjacentTo(m, team)) v += 2.0 // G63
        val theirs = minionCount(m.team, m.lane)
        if (profile.strength >= 7 && theirs > 1 && laneMargin(m.lane) >= 2 && !matchPoint(m.lane)) v -= 4.0
        return v
    }

    /** The foundation's flat minion price (measured against [minionWorth]'s lane price). */
    private fun oldMinionWorth(m: MinionState): Double {
        val alive = state.minions.count { it.team == m.team && it.lane == m.lane && it.at != null }
        var v = if (m.type == MinionType.HEAVY) 6.0 else 4.4
        if (m.type == MinionType.RANGED && rangedCovers(m, team)) v += 3.6
        if (m.type != MinionType.RANGED && adjacentTo(m, team)) v += 2.0
        if (alive == 1) v += 16.0
        if (alive == 1 && matchPoint(m.lane)) v += if (profile.laneTiming) 14.0 else 6.0
        if (profile.strength >= 7 && laneMargin(m.lane) >= 3) v -= 2.0
        return v
    }

    /**
     * Worth of an attack of [attackTotal] into [targetId] from [team]'s side.
     * A sure kill (it beats every defense they can still hold, or the hand is empty) is the
     * kill. Otherwise it is the card they throw. An ally is negative.
     */
    fun heroStrikeWorth(targetId: String, attackTotal: Int, ranged: Boolean): Double {
        val h = state.hero(targetId) ?: return 0.4
        if (h.team == team) return -8.0
        val at = h.at ?: return 0.0
        // G11: an empty hand, or the last card about to be played, is a defeat.
        if (h.hand.isEmpty() || defenseless(targetId)) return killWorth(targetId)
        val mod = minionModifier(at, h.team)
        val chance = killChance(h, attackTotal, ranged, mod)
        val kill = killWorth(targetId)
        val strip = discardWorth(h)
        // A poke that only spends a card loses the minion we could have farmed. A real kill does not.
        var v = chance * kill + (1.0 - chance) * strip * (if (chance < 0.4) 0.65 else 1.0)
        // G60: a teammate still to act turns a failed poke into the card they will not have.
        if (profile.focusFire && allyCanReach(h)) v += if (h.hand.size <= 2) 6.0 else 3.5
        if (profile.twoTurnPlans && chance < 0.55 && h.hand.size <= 3 && turn in 2..3) v += 2.0
        return v
    }

    /** Best attack or discard this card can land from [at]. Negative when nothing is there. */
    fun strikeFrom(heroId: String, at: Hex, card: HCard): Double {
        val reach = attackReach(heroId, card)
        if (reach <= 0 && !card.attackCard && !card.forcesDiscard) return 0.0
        val r = if (reach > 0) reach else 1
        val owner = state.hero(heroId)?.team ?: return 0.0
        var best = -0.5
        for (m in state.minions) {
            val pos = m.at ?: continue
            if (m.team == owner) continue
            val d = distance(at, pos)
            // [minionWorth] is from this context's team, which is the answerer's team.
            val worth = minionWorth(m.id)
            if (d <= r) {
                best = max(best, worth)
            } else if (d <= r + 2) {
                best = max(best, worth * 0.25)
            }
        }
        val total = card.attack + itemBonus(heroId, Stat.ATTACK)
        val agg = 0.65 + 0.7 * style(heroId).aggression
        for (e in state.heroes) {
            val pos = e.at ?: continue
            if (e.team == owner) continue
            val d = distance(at, pos)
            if (d > r + 2) continue
            var worth = heroStrikeWorth(e.id, total, card.ranged) * agg
            if (d > r) worth *= 0.25
            best = max(best, worth)
        }
        return best
    }

    /** Coin-equivalents of defeating [heroId], Life included. Public: the level band is the death cost. */
    fun killWorth(heroId: String): Double {
        val h = state.hero(heroId) ?: return 8.0
        val tier = deathTier(h.level)
        val life = life(h.team)
        val last = lastLifeWorth()
        val loss = lifeLadder(life, last) - lifeLadder((life - tier).coerceAtLeast(0), last)
        val enemies = state.heroes.count { it.team != h.team }
        var v = loss + h.level + tier * (enemies - 1).coerceAtLeast(0) + KILL_TEMPO
        if (deathsToLose(h.team) <= 1) v *= profile.knob("lastMult", 1.15)
        return v
    }

    /** The last Life counter: its loss is the game (GENERAL §2), priced so a sure death outweighs any farm. */
    fun lastLifeWorth(): Double = profile.knob("lastLife", LAST_LIFE)

    /** What throwing one card costs that hero, from the spare they have left. */
    fun discardWorth(h: HeroState): Double {
        if (roundOver) return 0.0
        val spare = h.hand.size - playsLeft(h.id)
        val base = when {
            state.turn >= 4 && playsLeft(h.id) <= 0 -> 1.2
            spare >= 2 -> 2.0
            spare == 1 -> 3.2
            spare == 0 -> 5.0
            else -> 7.0
        }
        return base
    }

    fun matchPoint(lane: String): Boolean {
        val waves = state.wavesLeft[lane] ?: return false
        if (waves <= 1) return true
        val bz = state.battleZones[lane] ?: return false
        val adjacent = game.geo.adjacentZones[bz].orEmpty()
        return adjacent.any { id -> game.geo.zones[id]?.kind == ZoneKind.THRONE }
    }

    /** A push is already likely: more farms are less urgent than a hero (the opening, STRATEGY 1.2). */
    fun opening(lane: String): Boolean {
        val ours = state.minions.count { it.team == team && it.lane == lane && it.at != null }
        val theirs = state.minions.count { it.team != team && it.lane == lane && it.at != null }
        return ours - theirs >= 2
    }

    fun minionCount(minionTeam: Team, lane: String): Int =
        state.minions.count { it.team == minionTeam && it.lane == lane && it.at != null }

    /** Our minions minus theirs on [lane]. */
    fun laneMargin(lane: String): Int = minionCount(team, lane) - minionCount(other(team), lane)

    fun coinsOf(heroId: String): Int = coins(heroId)

    /** The body a unit id acts as: the hero, or a piece that is that hero (Razzle). */
    fun heroBody(id: String): String? = state.hero(id)?.id ?: state.piece(id)?.heroBody

    /** Highest initiative among enemy attacks that reach [at] with a real chance. Null when none do. */
    fun incomingInitiative(at: Hex): Int? {
        var best: Int? = null
        for (t in threatsOn(at, team)) {
            if (!t.card.threatens || t.weight < 0.08) continue
            val init = t.card.initiative + itemBonus(t.enemyId, Stat.INITIATIVE)
            if (best == null || init > best) best = init
        }
        return best
    }

    private fun poolOf(heroId: String): List<PossibleCard> {
        val h = state.hero(heroId) ?: return emptyList()
        if (h.team == team || profile.knowHands) return realPool(h)
        return hiddenPool(h)
    }

    private fun realPool(h: HeroState): List<PossibleCard> {
        val playing = state.revealed
        return (h.hand + h.unresolved).map { inst ->
            PossibleCard(card(inst.id), 1.0, playing && inst.zone == Zone.PLAYED)
        }
    }

    private fun hiddenPool(h: HeroState): List<PossibleCard> {
        val upgrades = (h.level - 1).coerceIn(0, 6)
        val seenIds = if (profile.cardCounting) seen[h.id].orEmpty() else emptySet()
        val out = ArrayList<PossibleCard>()
        val groups = h.cards.groupBy { card(it.id).color }
        for ((color, insts) in groups) {
            if (color == CardColor.PURPLE || color == CardColor.OTHER) continue
            val shown = insts.filter { isPublic(it) }
            if (shown.isNotEmpty()) {
                for (inst in shown) {
                    if (inst.zone == Zone.PLAYED) out += PossibleCard(card(inst.id), 1.0, true)
                }
                continue
            }
            val remembered = insts.filter { it.id in seenIds }
            val candidates = (if (remembered.isNotEmpty()) remembered else insts)
                .filter { tierPossible(card(it.id).tier, upgrades) && inDeckShape(it) }
            if (candidates.isEmpty()) continue
            val w = 1.0 / candidates.size
            for (inst in candidates) out += PossibleCard(card(inst.id), w, false)
        }
        return out
    }

    private fun isPublic(inst: CardInst): Boolean = when (inst.zone) {
        Zone.RESOLVED -> !inst.faceDown
        Zone.DISCARD -> !inst.faceDown
        Zone.PLAYED -> state.revealed
        else -> false
    }

    private fun inDeckShape(inst: CardInst): Boolean =
        inst.zone != Zone.ULTIMATE && inst.zone != Zone.BOX && inst.zone != Zone.EXTRA

    private fun tierPossible(tier: Int?, upgrades: Int): Boolean = when (tier) {
        null -> true
        1 -> upgrades <= 2
        2 -> upgrades in 1..5
        3 -> upgrades >= 4
        else -> false
    }

    private fun threatsFrom(enemyId: String): List<Threat> = threatInfo(enemyId).map { it.threat }

    /**
     * An enemy's threats with what [threatChances] needs beyond [Threat]: the card's share of its
     * colour in the possible pool, and whether the card is already revealed.
     */
    private fun threatInfo(enemyId: String): List<ThreatInfo> = threatCache.getOrPut(enemyId) {
        state.hero(enemyId) ?: return@getOrPut emptyList()
        val pool = possibleCards(enemyId)
        val attackItems = itemBonus(enemyId, Stat.ATTACK)
        val initItems = itemBonus(enemyId, Stat.INITIATIVE)
        val out = ArrayList<ThreatInfo>()
        fun add(p: PossibleCard, weight: Double, thisTurn: Boolean, approach: Int) {
            if (weight <= 0.0 || !p.card.threatens) return
            val reach = attackReach(enemyId, p.card) + approach
            if (reach <= 0) return
            val t = Threat(enemyId, p.card, p.card.attack + attackItems, reach, weight, thisTurn)
            out += ThreatInfo(t, p.weight, p.playingNow, p.card.initiative + initItems)
        }
        val playing = pool.filter { it.playingNow }
        if (playing.isNotEmpty()) {
            for (p in playing) add(p, 0.72, thisTurn = true, approach = 0)
            val approach = playing.maxOf { if (it.card.hasMovement) it.card.movement else 0 }
            for (p in pool) if (!p.playingNow) add(p, 0.18 * p.weight, thisTurn = false, approach = approach)
        } else {
            // One card this turn. Each colour is equally likely; [PossibleCard.weight] is its share of that colour.
            val colours = pool.map { it.card.color }.distinct().size.coerceAtLeast(1)
            for (p in pool) {
                add(p, 0.65 * p.weight / colours, thisTurn = true, approach = 0)
                if (state.turn < 4) add(p, 0.22 * p.weight / colours, thisTurn = false, approach = 1)
            }
        }
        out
    }

    private fun threatsFromEnemies(victim: HeroState): List<Pair<String, List<ThreatInfo>>> =
        state.heroes.filter { it.team != victim.team && it.at != null }.map { it.id to threatInfo(it.id) }

    /**
     * Expected loss of [heroId] when an enemy acting before [initiative] meets it at [before] and one
     * acting after meets it at [after] (GENERAL §2 and §5, G7–G9): the card play's view of the turn,
     * before anyone's card is revealed. Ties go to the side the coin shows. Next turn's threats meet
     * it at [after]. [before] null is off the board (a respawn). 0 without a threat map.
     */
    fun exposureSplit(heroId: String, before: Hex?, after: Hex, initiative: Int): Double {
        if (!profile.threatMap) return 0.0
        val h = state.hero(heroId) ?: return 0.0
        val ours = state.tieBreaker == h.team
        val (pKill, pHit) = threatChances(h) { t ->
            if (!t.threat.thisTurn) {
                after
            } else {
                val first = t.initiative > initiative || (t.initiative == initiative && !ours)
                if (first) before else after
            }
        }
        var v = pKill * killWorth(heroId) + pHit * discardWorth(h)
        if (profile.heroPlaybooks) {
            for (e in state.heroes) {
                if (e.team != h.team) v += playbooks(e.id).threatAdjust(this, e.id, after)
            }
        }
        return v
    }

    /**
     * P(defeat) and P(a card thrown | not defeated) from the possible pool, each threat met at the
     * space [where] names. An empty defense pool is counted in full: any attack that reaches is the
     * kill (GENERAL §2). A hidden card that would defeat us is the card they play when they hold it
     * (G2): its chance is its share of the colour, not a fifth of that.
     */
    private fun threatChances(h: HeroState, where: (ThreatInfo) -> Hex?): Pair<Double, Double> {
        var noKill = 1.0
        var noHit = 1.0
        var attackReaches = false
        // Cards in hand while this turn resolves, and while the next one does (one more is played).
        val pending = playsThisTurn(h)
        val nowCards = h.hand.size - if (pending) 1 else 0
        val emptyNext = state.turn < 4 && nowCards - 1 <= 0
        val attackers = ArrayList<Double>()
        for ((enemyId, list) in threatsFromEnemies(h)) {
            val from = state.hero(enemyId)?.at ?: continue
            val acted = alreadyActed(enemyId)
            var killNow = 0.0
            var hitNow = 0.0
            var killNext = 0.0
            var hitNext = 0.0
            for (info in list) {
                val t = info.threat
                if (t.thisTurn && acted) continue
                val at = where(info) ?: continue
                if (distance(from, at) > t.reach) continue
                var w = t.weight.coerceIn(0.0, 0.9)
                val attack = t.card.attackCard || t.card.discardOrDefeat
                // G11 and section 2: no card left after the one still to play is a defeat by any attack.
                val lethal = kills(h, t, minionModifier(at, h.team)) || (!t.thisTurn && emptyNext && attack)
                if (t.thisTurn && attack) attackReaches = true
                if (lethal && !info.known) {
                    val pull = when {
                        t.thisTurn -> profile.knob("lethal", LETHAL_NOW)
                        emptyNext -> profile.knob("lethalEmpty", LETHAL_EMPTY)
                        else -> profile.knob("lethalNext", LETHAL_NEXT)
                    }
                    w = max(w, pull * info.poolWeight)
                }
                when {
                    t.thisTurn && lethal -> killNow += w
                    t.thisTurn -> hitNow += if (attack) w else w * 0.5
                    lethal -> killNext += w
                    else -> hitNext += w
                }
            }
            val nextCap = if (emptyNext) profile.knob("emptyCap", 0.6) else 0.28
            noKill *= (1.0 - min(0.70, killNow)) * (1.0 - min(nextCap, killNext))
            noHit *= (1.0 - min(0.70, hitNow)) * (1.0 - min(0.28, hitNext))
            if (hitNow > 0.0) attackers += min(0.70, hitNow)
        }
        var pKill = min(0.92, 1.0 - noKill)
        if (attackReaches && defenseless(h.id)) pKill = max(pKill, 0.85)
        // One card to defend with and two enemies who can hit: the second hit is the defeat.
        if (nowCards == 1 && attackers.size >= 2 && profile.on("twoHits", true)) {
            val none = attackers.fold(1.0) { a, p -> a * (1.0 - p) }
            val one = attackers.sumOf { p -> p * none / (1.0 - p).coerceAtLeast(0.05) }
            val two = (1.0 - none - one).coerceIn(0.0, 1.0)
            pKill = 1.0 - (1.0 - pKill) * (1.0 - two)
        }
        val pHit = (1.0 - pKill) * min(0.9, 1.0 - noHit)
        return pKill to pHit
    }

    /** This hero still has this turn's card to play (it is in hand, not yet facedown or resolved). */
    private fun playsThisTurn(h: HeroState): Boolean =
        playsLeft(h.id) > (4 - state.turn).coerceAtLeast(0)

    /**
     * Chance [heroId] is defeated where it stands before it acts on [initiative]: an enemy whose card
     * may act first, at [before] (G7, G8). [CardSim] scales what the action gains by the rest.
     */
    fun defeatBefore(heroId: String, before: Hex, initiative: Int): Double {
        if (!profile.threatMap) return 0.0
        val h = state.hero(heroId) ?: return 0.0
        val ours = state.tieBreaker == h.team
        return threatChances(h) { t ->
            val first = t.threat.thisTurn && (t.initiative > initiative || (t.initiative == initiative && !ours))
            if (first) before else null
        }.first
    }

    /**
     * Chance [heroId] acts before [initiative] this turn: the share of its possible cards that are
     * faster (ties by the coin). 1 or 0 once its card is revealed (G7).
     */
    fun actsBefore(heroId: String, initiative: Int): Double {
        val h = state.hero(heroId) ?: return 0.0
        if (alreadyActed(heroId)) return 1.0
        val ours = state.tieBreaker != h.team
        val items = itemBonus(heroId, Stat.INITIATIVE)
        val pool = possibleCards(heroId)
        val playing = pool.filter { it.playingNow }
        val cards = playing.ifEmpty { pool }
        val total = cards.sumOf { it.weight }
        if (total <= 0.0) return 0.0
        return cards.filter { p ->
            val i = p.card.initiative + items
            i > initiative || (i == initiative && !ours)
        }.sumOf { it.weight } / total
    }

    /** Resolved already this turn. The hero whose question is pending has not acted yet. */
    private fun alreadyActed(heroId: String): Boolean = heroId in state.turnRecord.resolved

    private fun kills(h: HeroState, t: Threat, mod: Int): Boolean {
        if (h.hand.isEmpty()) return true
        if (!t.card.attackCard && !t.card.discardOrDefeat) return false
        val options = defensesOf(h)
        if (options.isEmpty()) return true
        return options.none { holds(h.id, it.first, t.attackTotal, t.card.ranged, mod) }
    }

    private fun killChance(h: HeroState, total: Int, ranged: Boolean, mod: Int): Double {
        if (h.hand.isEmpty()) return 1.0
        val options = defensesOf(h)
        if (options.isEmpty()) return 1.0
        val holding = options.filter { holds(h.id, it.first, total, ranged, mod) }
        if (holding.isEmpty()) return 1.0
        if (h.team == team || profile.knowHands) return 0.0
        val likely = holding.any { it.second >= 0.5 }
        return if (likely) 0.08 else 0.35
    }

    /** Defense cards they might hold, with the colour-weight of [possibleCards]. */
    private fun defensesOf(h: HeroState): List<Pair<HCard, Double>> {
        val cards = if (h.team == team || profile.knowHands) {
            h.hand.map { card(it.id) to 1.0 }
        } else {
            possibleCards(h.id).map { it.card to it.weight }
        }
        return cards.filter { it.first.canDefend || it.first.block }
    }

    private fun holds(heroId: String, card: HCard, total: Int, ranged: Boolean, mod: Int): Boolean {
        if (ranged && card.blocksRanged) return true
        if (!ranged && card.blocksMelee) return true
        val printed = card.defense ?: return false
        if (!card.canDefend && !card.block) return false
        val mods = if (card.ignoresMinionModifiers) 0 else mod
        return printed + itemBonus(heroId, Stat.DEFENSE) + mods >= total
    }

    private fun attackCardsOf(h: HeroState): List<HCard> = if (h.team == team || profile.knowHands) {
        (h.hand + h.unresolved).map { card(it.id) }.filter { it.attackCard }
    } else {
        possibleCards(h.id).map { it.card }.filter { it.attackCard }.distinctBy { it.id }
    }

    private fun allyCanReach(target: HeroState): Boolean {
        val at = target.at ?: return false
        return state.heroes.any { ally ->
            ally.team == team && ally.id != seat && ally.at != null &&
                attackCardsOf(ally).any { c -> distance(ally.at!!, at) <= attackReach(ally.id, c) + 2 }
        }
    }

    private fun rangedCovers(m: MinionState, victimTeam: Team): Boolean {
        val at = m.at ?: return false
        return state.heroes.any { h ->
            h.team == victimTeam && h.at?.let { distance(it, at) in 1..2 } == true
        }
    }

    private fun adjacentTo(m: MinionState, heroTeam: Team): Boolean {
        val at = m.at ?: return false
        return state.heroes.any { h ->
            h.team == heroTeam && h.at?.let { distance(it, at) == 1 } == true
        }
    }

    private fun formation(heroId: String, at: Hex, style: HeroStyle): Double {
        var v = 0.0
        val owner = state.hero(heroId)?.team ?: return 0.0
        val nearest = state.heroes.filter { it.team != owner && it.at != null }
            .minOfOrNull { distance(at, it.at!!) }
        if (nearest != null) v -= abs(nearest - style.idealDistance) * 0.35
        if (!blindModifiers && profile.threatMap) {
            val friendly = state.minions.count { m ->
                m.team == owner && m.type != MinionType.RANGED && m.at?.let { distance(at, it) == 1 } == true
            }
            val enemyRanged = state.minions.count { m ->
                m.team != owner && m.type == MinionType.RANGED && m.at?.let { distance(at, it) in 1..2 } == true
            }
            v += friendly * 0.85
            v -= enemyRanged * 1.15
        }
        if (inBattleZone(at)) v += 0.8 * style.laneWeight
        return v
    }

    private fun planPull(at: Hex): Double {
        val target = plan.targetId?.let { state.hero(it)?.at ?: state.minion(it)?.at } ?: return 0.0
        return when (plan.intent) {
            Intent.KILL, Intent.FARM, Intent.PUSH, Intent.CLEAR_MINION, Intent.WIN, Intent.SETUP ->
                -0.35 * distance(at, target)

            Intent.RETREAT, Intent.SURVIVE, Intent.LET_PUSH, Intent.ROTATE -> 0.25 * distance(at, target)
            else -> 0.0
        }
    }

    /** Insurance value of a spare card on this turn (3 / 2 / 1.5 / 0.8). */
    fun spareWeight(): Double = spareWeightOf(turn)

    /** No defense left, or the last card is about to be played and will not be in hand (G11). */
    fun defenseless(heroId: String): Boolean = heroIsDefenseless(this, heroId)

    /** This hero has not resolved a card yet and still will this turn. */
    fun stillToAct(heroId: String): Boolean = heroStillToAct(this, heroId)

    /**
     * The zone [heroId] should be standing in. Null until lane timing is on.
     * At match point it is the current Battle Zone; once a push is decided, the next one (G51).
     */
    fun goalZone(heroId: String): String? = goalZoneOf(this, heroId)

    /** The zone a push by [pushing] moves [lane] into, or null when that zone is a Throne or absent. */
    fun nextBattleZone(lane: String, pushing: Team): String? = nextZoneOf(this, lane, pushing)

    /** Coin change in `60 × W` from defeating one enemy minion on [lane] (G58). */
    fun laneKillValue(lane: String): Double = laneKillDelta(this, lane)

    /** Mild danger is a tax. A likely death this turn is not a place to stand. */
    private fun exposurePenalty(heroId: String, at: Hex): Double {
        val loss = exposure(heroId, at)
        return if (loss > killWorth(heroId) * 0.45) loss else loss * 0.35
    }

    /** A threat, the card's share of its colour, whether it is revealed, and its initiative with items. */
    private class ThreatInfo(val threat: Threat, val poolWeight: Double, val known: Boolean, val initiative: Int)

    companion object {
        private const val KILL_TEMPO = 5.0

        /** Chance an enemy plays the card that defeats us, when it holds it: this turn, next turn. */
        private const val LETHAL_NOW = 0.55
        private const val LETHAL_NEXT = 0.2

        /** Next turn, when the card still to play leaves the hand empty. */
        private const val LETHAL_EMPTY = 0.5

        fun deathTier(level: Int): Int = when {
            level >= 7 -> 3
            level >= 4 -> 2
            else -> 1
        }

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

        fun other(team: Team): Team = if (team == Team.RED) Team.BLUE else Team.RED

        /** Worth of the last Life counter in [lifeLadder] (the doc's "terminal" in exposure units). */
        const val LAST_LIFE: Double = 60.0

        /**
         * Worth of holding [counters] Life counters (GENERAL §2): the counter that would leave 0 is
         * [last], then 22, 14, 9, 6 and 4 for each one above.
         */
        fun lifeLadder(counters: Int, last: Double = LAST_LIFE): Double {
            var v = 0.0
            for (i in 1..counters) {
                v += when (i) {
                    1 -> last
                    2 -> 22.0
                    3 -> 14.0
                    4 -> 9.0
                    5 -> 6.0
                    else -> 4.0
                }
            }
            return v
        }
    }
}
