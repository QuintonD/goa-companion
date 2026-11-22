package dev.quinton.goa.core.game.ai.heuristic

import dev.quinton.goa.core.domain.PortableMath
import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.game.engine.GameState
import dev.quinton.goa.core.game.engine.HeroState
import dev.quinton.goa.core.game.engine.PieceState
import dev.quinton.goa.core.game.engine.Step
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.game.engine.enemyUnitsInZone
import dev.quinton.goa.core.game.engine.respawnSpaces
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.catalog.Stat
import dev.quinton.goa.core.model.match.Team
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Hand-written position value in coin-equivalents (STRATEGY §2, ADR-085, HEVAL.md). One minion of lane
 * advantage at an even lane is about 21, one coin is 1, a card in hand is about 2.5.
 * Zero-sum: computed for Red and negated for Blue. A finished game is ±1000.
 *
 * The value is `Σ wᵢ · fᵢ` over the named terms of [HTerm] ([features]), with the weights of
 * [HEvalWeights.of] (the hand-set ones unless a knob or a calibrated set says otherwise).
 *
 * Enemy card identities are not read. Hand size, level, coins, items and faceup cards are public;
 * the threat inside [HContext.exposure] uses the possible pool.
 */
object HEval {
    const val WIN: Double = 1000.0

    /**
     * Share of the designer's push potential (0.5 a reaching attack card, 0.35 in 3v3) the projection
     * counts. Measured: the full share made a minion we can take worth half a minion, and the player
     * stopped taking them (h10 vs s8 35% at 1.0, 41% at 0.4, 256 games each).
     */
    private const val PP_SCALE = 0.4

    /** Share of a kill's lane value that a minion in reach of a card still to play is worth. */
    internal const val LANE_THREAT = 0.3

    /** Spaces one movement action covers in [HTerm.ACCESS] (a typical basic movement). */
    private const val MOVE = 3.0

    /** Share of a reachable attack play that becomes a minion kill in [HTerm.LANE_PROJ]'s projection. */
    private const val PROJ_KILL = 0.5

    private val TERMS = HTerm.entries.size

    fun evaluate(ctx: HContext, team: Team): Double = evaluate(ctx, team, null)

    /**
     * [evaluate], with [exposure] in place of the exposure of one hero ([CardSim]: the card play
     * splits it by who acts first).
     */
    internal fun evaluate(ctx: HContext, team: Team, exposure: Pair<String, Double>?): Double =
        value(ctx, team, exposure, HEvalWeights.of(ctx.profile))

    /** The value a search uses at its leaf: the `hevLeaf` weights (HEVAL.md). */
    fun evaluateLeaf(ctx: HContext, team: Team): Double = value(ctx, team, null, HEvalWeights.of(ctx.profile, leaf = true))

    /** [evaluate] with explicit [weights]. */
    fun value(ctx: HContext, team: Team, exposure: Pair<String, Double>?, weights: HEvalWeights): Double {
        val state = ctx.state
        val red = state.winner?.let { if (it == Team.RED) WIN - state.round else -WIN + state.round }
            ?: run {
                val f = terms(ctx, exposure, weights.w)
                var v = 0.0
                for (i in 0 until TERMS) if (weights.w[i] != 0.0) v += weights.w[i] * f[i]
                v
            }
        return if (team == Team.RED) red else -red
    }

    /**
     * Every term, Red minus Blue, in [HTerm] order (calibration, HEVAL.md). Null for a finished game.
     * From [team]'s side when given (each term negated for Blue).
     */
    fun features(ctx: HContext, team: Team = Team.RED): DoubleArray? {
        if (ctx.state.winner != null) return null
        val f = terms(ctx, null, null)
        if (team == Team.BLUE) for (i in f.indices) f[i] = -f[i]
        return f
    }

    /** The terms; only those with a non-zero weight in [want] (all when null). */
    private fun terms(ctx: HContext, exposure: Pair<String, Double>?, want: DoubleArray?): DoubleArray {
        val f = DoubleArray(TERMS)
        fun on(t: HTerm) = want == null || want[t.ordinal] != 0.0
        val state = ctx.state
        lane(ctx, f)
        if (on(HTerm.LIFE)) f[HTerm.LIFE.ordinal] = life(ctx, state)
        if (on(HTerm.LIFE_DANGER)) {
            fun danger(t: Team) = if (ctx.deathsToLose(t) <= 1) 1.0 else 0.0
            f[HTerm.LIFE_DANGER.ordinal] = danger(Team.BLUE) - danger(Team.RED)
        }
        if (on(HTerm.PIECES)) f[HTerm.PIECES.ordinal] = pieces(ctx)
        if (on(HTerm.LANE_PROJ)) f[HTerm.LANE_PROJ.ordinal] = laneProjected(ctx)
        val early = state.round <= 2 && state.heroes.all { it.level <= 3 }
        for (h in state.heroes) {
            val sign = if (h.team == Team.RED) 1.0 else -1.0
            hero(ctx, h, early, exposure?.takeIf { it.first == h.id }?.second, sign, f, ::on)
        }
        if (ctx.profile.heroPlaybooks && on(HTerm.PLAYBOOK)) {
            var v = 0.0
            for (h in state.heroes) v += ctx.playbooks(h.id).evalTerms(state, Team.RED, h.id)
            f[HTerm.PLAYBOOK.ordinal] = v
        }
        return f
    }

    @Suppress("LongParameterList")
    private fun hero(
        ctx: HContext,
        h: HeroState,
        early: Boolean,
        exposure: Double?,
        sign: Double,
        f: DoubleArray,
        on: (HTerm) -> Boolean,
    ) {
        fun add(t: HTerm, v: Double) {
            f[t.ordinal] += sign * v
        }
        add(HTerm.COINS, h.coins * (if (early) 1.3 else 1.0))
        var levels = h.level * (h.level - 1) / 2.0 + 3.0 * (h.level - 1)
        if (h.coins >= h.level && h.level < 8) levels += 1.5
        if (h.ultimateUnlocked) levels += 8.0
        add(HTerm.LEVELS, levels)
        if (h.counters.isNotEmpty()) add(HTerm.COUNTERS, h.counters.entries.sumOf { (k, n) -> counter(k, n) })
        val readable = h.team == ctx.team || ctx.profile.knowHands
        if (!ctx.roundOver) {
            add(HTerm.HAND, hand(ctx, h))
            if (readable) {
                add(HTerm.HAND_RATING, 0.12 * h.hand.sumOf { ctx.card(it.id).rating })
            }
        }
        if (readable && (on(HTerm.DEFENSE) || on(HTerm.DEFENDERS) || on(HTerm.INITIATIVE))) handShape(ctx, h, ::add)
        val at = h.at
        if (at == null) {
            add(HTerm.OFF_BOARD, 1.0)
            if (on(HTerm.ACCESS)) add(HTerm.ACCESS, -respawnActions(ctx, h))
            return
        }
        val bodies = listOf(at) + ctx.state.pieces.filter { it.heroBody == h.id && it.at != null }.map { it.at!! }
        add(HTerm.PRESENCE, bodies.maxOf { ctx.presence(it) })
        if (on(HTerm.OPPORTUNITY)) add(HTerm.OPPORTUNITY, bodies.maxOf { ctx.opportunity(h.id, it) })
        if (on(HTerm.LANE_THREAT)) add(HTerm.LANE_THREAT, bodies.maxOf { laneThreat(ctx, h, it, 1.0) })
        if (ctx.profile.laneTiming && on(HTerm.ZONE_PULL)) add(HTerm.ZONE_PULL, bodies.maxOf { zonePull(ctx, h.id, it) })
        if (ctx.profile.threatMap && on(HTerm.EXPOSURE)) {
            add(HTerm.EXPOSURE, exposure ?: bodies.maxOf { ctx.exposure(h.id, it) })
        }
        if (on(HTerm.OPP_POOL)) add(HTerm.OPP_POOL, bodies.maxOf { reachingPlays(ctx, h, it).first })
        if (on(HTerm.ACCESS)) add(HTerm.ACCESS, -bodies.minOf { actionsTo(ctx, h.team, it, goalOf(ctx, h, it)) })
        if (on(HTerm.FORMATION)) add(HTerm.FORMATION, formation(ctx, h.team, at))
        if (on(HTerm.IDEAL_GAP)) {
            val nearest = ctx.state.heroes.filter { it.team != h.team && it.at != null }.minOfOrNull { HexGrid.distance(at, it.at!!) }
            if (nearest != null) add(HTerm.IDEAL_GAP, -kotlin.math.abs(nearest - ctx.style(h.id).idealDistance).toDouble())
        }
        if (on(HTerm.MINION_MOD)) add(HTerm.MINION_MOD, ctx.minionModifier(at, h.team).toDouble())
    }

    /** [HTerm.DEFENSE], [HTerm.DEFENDERS] and [HTerm.INITIATIVE] of a hand the evaluating team may read. */
    private fun handShape(ctx: HContext, h: HeroState, add: (HTerm, Double) -> Unit) {
        if (h.hand.isEmpty()) return
        val cards = h.hand.map { ctx.card(it.id) }
        val defenders = cards.filter { it.canDefend || it.block }
        add(HTerm.DEFENDERS, defenders.size.toDouble())
        val items = ctx.itemBonus(h.id, Stat.DEFENSE)
        defenders.mapNotNull { it.defense }.maxOrNull()?.let { add(HTerm.DEFENSE, (it + items).toDouble()) }
        val init = ctx.itemBonus(h.id, Stat.INITIATIVE)
        add(HTerm.INITIATIVE, (cards.maxOf { it.initiative } + init).toDouble())
    }

    /** Friendly melee minions adjacent, enemy ranged minions within 2 (the weights of `HContext.formation`). */
    private fun formation(ctx: HContext, team: Team, at: Hex): Double {
        var v = 0.0
        for (m in ctx.state.minions) {
            val pos = m.at ?: continue
            val d = HexGrid.distance(at, pos)
            if (m.team == team && m.type != MinionType.RANGED && d == 1) v += 1.0
            if (m.team != team && m.type == MinionType.RANGED && d in 1..2) v -= 1.35
        }
        return v
    }

    /** G51: the same pull [HContext.placement] uses, so a one-ply look sees the next zone. */
    private fun zonePull(ctx: HContext, heroId: String, at: Hex): Double {
        val goal = ctx.goalZone(heroId) ?: return 0.0
        val d = ctx.distanceToZone(at, goal)
        return if (d == 0) 2.5 else -0.7 * d
    }

    private fun hand(ctx: HContext, h: HeroState): Double {
        val n = h.hand.size
        val need = ctx.playsLeft(h.id)
        val spareW = when (ctx.state.turn) {
            1 -> 3.0
            2 -> 2.0
            3 -> 1.5
            else -> 0.8
        }
        return 2.5 * min(n, need) + spareW * max(0, n - need) - 3.0 * max(0, need - n)
    }

    private fun life(ctx: HContext, state: GameState): Double {
        val last = ctx.lastLifeWorth()
        return HContext.lifeLadder(state.lifeCounters[Team.RED] ?: 0, last) -
            HContext.lifeLadder(state.lifeCounters[Team.BLUE] ?: 0, last)
    }

    /** [HTerm.LANE], [HTerm.LANE_MATCH] and [HTerm.MARGIN]. */
    private fun lane(ctx: HContext, f: DoubleArray) {
        val state = ctx.state
        for ((lane, bz) in state.battleZones) {
            val zones = ctx.game.geo.lanes[lane]?.zones ?: continue
            val i = zones.indexOf(bz)
            if (i < 0) continue
            val red = ctx.minionCount(Team.RED, lane)
            val blue = ctx.minionCount(Team.BLUE, lane)
            val ppRed = push(ctx, Team.RED, bz)
            val ppBlue = push(ctx, Team.BLUE, bz)
            val a = max(0.0, red - ppBlue)
            val b = max(0.0, blue - ppRed)
            val waves = state.wavesLeft[lane] ?: 1
            val w = laneWin(zones.lastIndex - i, i, waves, pushChance(a, b))
            val match = ctx.profile.laneTiming && ctx.matchPoint(lane)
            f[HTerm.LANE.ordinal] += 60.0 * (2 * w - 1)
            if (match) f[HTerm.LANE_MATCH.ordinal] += 40.0 * (2 * w - 1)
            f[HTerm.MARGIN.ordinal] += (if (match) 3.0 else 1.5) * (red - blue)
        }
    }

    /** The designer's chance that Red wins the next push with [a] against [b] minions (STRATEGY §1.1). */
    private fun pushChance(a: Double, b: Double): Double {
        var p = sigmoid(1.1 * (a - b))
        if (a > b && 2 * b - a <= 0.25) p = max(p, 0.95)
        if (b > a && 2 * a - b <= 0.25) p = min(p, 0.05)
        return p
    }

    /** Enemy minions this team can still take (the designer's Push Potential). Public cards only. */
    private fun push(ctx: HContext, team: Team, bz: String): Double {
        if (ctx.roundOver) return 0.0
        val size = ctx.state.heroes.count { it.team == team }
        val per = (if (size >= 3) 0.35 else 0.5) * ctx.profile.knob("pp", PP_SCALE)
        var pp = 0.0
        for (h in ctx.state.heroes) {
            val at = h.at
            if (h.team != team || at == null || ctx.distanceToZone(at, bz) > 6) continue
            val attacks = expectedAttacks(ctx, h)
            val left = ctx.playsLeft(h.id).coerceAtLeast(0)
            pp += per * min(attacks, left.toDouble())
        }
        return pp
    }

    private fun expectedAttacks(ctx: HContext, h: HeroState): Double {
        if (h.team == ctx.team || ctx.profile.knowHands) {
            return (h.hand + h.unresolved).count { ctx.card(it.id).attackCard }.toDouble()
        }
        val spent = h.cards.count { inst ->
            // A facedown card (p.10) is hidden: its identity here is the sample's, not public.
            val face = (inst.zone == Zone.RESOLVED || inst.zone == Zone.DISCARD) && !inst.faceDown
            face && ctx.card(inst.id).attackCard
        }
        return (2.0 - spent).coerceAtLeast(0.0)
    }

    private fun pieces(ctx: HContext): Double {
        val turnsLeft = if (ctx.roundOver) 0 else 5 - ctx.state.turn
        var v = 0.0
        val grouped = ctx.state.pieces.mapNotNull { pieceWorth(ctx, it, turnsLeft) }.groupBy { it.first }
        for ((_, rows) in grouped) {
            val sum = rows.map { it.third }.sortedDescending().take(4).sum()
            v += if (rows.first().second == Team.RED) sum else -sum
        }
        return v
    }

    private fun pieceWorth(ctx: HContext, p: PieceState, turnsLeft: Int): Triple<String, Team, Double>? {
        val at = p.at ?: return null
        val owner = p.owner ?: return null
        if (p.heroBody != null) return null
        val team = p.team ?: ctx.state.hero(owner)?.team ?: return null
        var w = if (p.removeAtEndOfRound) 0.8 * turnsLeft / 4.0 else 1.2
        if (w <= 0.0) return null
        w *= max(0.25, 1.0 - 0.12 * ctx.distanceToBattleZone(at))
        val near = ctx.state.heroes.any { it.team != team && it.at?.let { e -> HexGrid.distance(e, at) <= 2 } == true }
        if (near) w += 0.4
        return Triple(owner, team, w)
    }

    private fun counter(key: String, n: Int): Double = when (key) {
        "spellbook" -> 1.5 * min(n, 5)
        "spellsRemoved" -> 0.1 * n
        "wishCasts" -> 10.0 * n
        else -> 0.0
    }

    // ---- wave 3 terms (HEVAL.md) -------------------------------------------------------------------

    /**
     * Attack plays of [h] still to come this round that reach an enemy minion from [at]: (in reach,
     * within two more spaces), each card weighted by the pool for a hidden hand, together at most the
     * plays left. The lane of the nearest such minion, or null.
     */
    private fun reachingPlays(ctx: HContext, h: HeroState, at: Hex): Pair<Double, String?> {
        if (ctx.roundOver) return 0.0 to null
        val left = ctx.playsLeft(h.id)
        if (left <= 0) return 0.0 to null
        val targets = ctx.state.minions.filter { it.team != h.team && it.at != null && !ctx.heavyImmune(it.id) }
        if (targets.isEmpty()) return 0.0 to null
        val cards: List<Pair<HCard, Double>> = if (h.team == ctx.team || ctx.profile.knowHands) {
            (h.hand + h.unresolved).map { ctx.card(it.id) to 1.0 }
        } else {
            ctx.possibleCards(h.id).map { it.card to it.weight }
        }
        val nearest = targets.minBy { ctx.distance(at, it.at!!) }
        val d = ctx.distance(at, nearest.at!!)
        var near = 0.0
        var far = 0.0
        for ((c, w) in cards) {
            if (!c.attackCard || !c.hitsMinions) continue
            val reach = ctx.attackReach(h.id, c).coerceAtLeast(1)
            if (d <= reach) near += w else if (d <= reach + 2) far += w
        }
        val cap = left.toDouble()
        val n = min(near, cap) + 0.35 * min(far, (cap - near).coerceAtLeast(0.0))
        return n to nearest.lane
    }

    /** The zone [h] heads for: its goal zone (lane timing), else the nearest Battle Zone. */
    private fun goalOf(ctx: HContext, h: HeroState, at: Hex): String? =
        ctx.goalZone(h.id) ?: ctx.state.battleZones.values.minByOrNull { ctx.distanceToZone(at, it) }

    /**
     * Actions a hero of [team] at [at] needs to stand in [goal]: walking [MOVE] spaces an action, or
     * Fast Travel (p.11: no enemy unit in the zone left or entered) then walking.
     */
    private fun actionsTo(ctx: HContext, team: Team, at: Hex, goal: String?): Double {
        goal ?: return 0.0
        val d = ctx.distanceToZone(at, goal)
        if (d == 0) return 0.0
        val walk = ceil(d / MOVE)
        val zone = ctx.zoneOf(at) ?: return walk
        if (ctx.game.enemyUnitsInZone(team, zone)) return walk
        val geo = ctx.game.geo
        var best = walk
        for (z in geo.adjacentZones[zone].orEmpty() + zone) {
            if (ctx.game.enemyUnitsInZone(team, z)) continue
            val ft = if (z == goal) 1.0 else 1.0 + ceil(zoneDistance(ctx, z, goal) / MOVE)
            if (ft < best) best = ft
        }
        return best
    }

    /** Fewest spaces from any space of [from] into [to]. */
    private fun zoneDistance(ctx: HContext, from: String, to: String): Int {
        val key = "hev.zd.$from.$to"
        (ctx.memo[key] as? Int)?.let { return it }
        val d = ctx.game.geo.zoneSpaces[from].orEmpty().minOfOrNull { ctx.distanceToZone(it, to) } ?: 0
        ctx.memo[key] = d
        return d
    }

    /** An off-board hero respawns in its Throne Zone when its card resolves (p.7), then acts from there. */
    private fun respawnActions(ctx: HContext, h: HeroState): Double {
        val spaces = ctx.game.respawnSpaces(h.id)
        if (spaces.isEmpty()) return 2.0
        val goal = ctx.state.battleZones.values.minByOrNull { z -> spaces.minOf { ctx.distanceToZone(it, z) } }
        return spaces.minOf { actionsTo(ctx, h.team, it, goal) }
    }

    /** [HTerm.LANE_PROJ]: one projected minion battle per lane. */
    private fun laneProjected(ctx: HContext): Double {
        val state = ctx.state
        var v = 0.0
        for ((lane, bz) in state.battleZones) {
            val zones = ctx.game.geo.lanes[lane]?.zones ?: continue
            val i = zones.indexOf(bz)
            if (i < 0) continue
            val waves = state.wavesLeft[lane] ?: 1
            val due = state.pushDue[lane]
            val w = if (due != null) {
                afterPush(ctx, zones, i, waves, due == Team.RED)
            } else {
                var red = ctx.minionCount(Team.RED, lane).toDouble()
                var blue = ctx.minionCount(Team.BLUE, lane).toDouble()
                val battleDone = ctx.roundOver && state.step.ordinal > Step.MINION_BATTLE.ordinal
                if (!ctx.roundOver) {
                    var killsByRed = 0.0
                    var killsByBlue = 0.0
                    for (h in state.heroes) {
                        val at = h.at ?: continue
                        val (n, l) = reachingPlays(ctx, h, at)
                        if (l != lane) continue
                        if (h.team == Team.RED) killsByRed += n else killsByBlue += n
                    }
                    blue = max(0.0, blue - PROJ_KILL * min(killsByRed, blue))
                    red = max(0.0, red - PROJ_KILL * min(killsByBlue, red))
                }
                val endOfRound = !battleDone && (ctx.roundOver || state.turn >= 4)
                if (endOfRound) roundEnd(ctx, zones, i, waves, red, blue) else laneWinV2(ctx, zones, i, waves, pushChance(red, blue))
            }
            val weight = if (ctx.profile.laneTiming && ctx.matchPoint(lane)) 100.0 else 60.0
            v += weight * (2 * w - 1)
        }
        return v
    }

    /**
     * The round's minion battle with [red] against [blue] (p.8): the loser removes the difference, a
     * push when that is all of them (a ≥ 2b), else the carry-over 2b − a meets a. Soft at the edges
     * because the counts are expectations.
     */
    @Suppress("LongParameterList")
    private fun roundEnd(ctx: HContext, zones: List<String>, i: Int, waves: Int, red: Double, blue: Double): Double {
        val redPush = if (red > blue) sigmoid(3.0 * (red - 2 * blue) + 1.5) else 0.0
        val bluePush = if (blue > red) sigmoid(3.0 * (blue - 2 * red) + 1.5) else 0.0
        val a = if (red > blue) red else max(0.0, 2 * red - blue)
        val b = if (blue > red) blue else max(0.0, 2 * blue - red)
        val stay = laneWinV2(ctx, zones, i, waves, pushChance(a, b))
        var w = (1 - redPush - bluePush) * stay
        if (redPush > 0) w += redPush * afterPush(ctx, zones, i, waves, true)
        if (bluePush > 0) w += bluePush * afterPush(ctx, zones, i, waves, false)
        return w
    }

    /** P(Red wins the lane) just after a push from zone index [i] (one Wave counter flipped). */
    private fun afterPush(ctx: HContext, zones: List<String>, i: Int, waves: Int, redPushed: Boolean): Double {
        val j = if (redPushed) i + 1 else i - 1
        if (j >= zones.lastIndex) return 1.0
        if (j <= 0) return 0.0
        if (waves <= 1) return if (redPushed) 1.0 else 0.0
        return laneWinV2(ctx, zones, j, waves - 1, spawnChance(ctx, zones[j]))
    }

    /** The next push's chance from the minions a fresh wave spawns in [zone] (#14: 6 v 5 is not even). */
    private fun spawnChance(ctx: HContext, zone: String): Double =
        pushChance(spawnCount(ctx, zone, Team.RED).toDouble(), spawnCount(ctx, zone, Team.BLUE).toDouble())

    /** [laneWin] with the chance after a push read from the new zone's spawns. */
    private fun laneWinV2(ctx: HContext, zones: List<String>, i: Int, waves: Int, p: Double): Double {
        val k = zones.lastIndex - i
        if (k <= 0) return 1.0
        if (i <= 0) return 0.0
        return p * afterPush(ctx, zones, i, waves, true) + (1 - p) * afterPush(ctx, zones, i, waves, false)
    }

    private fun sigmoid(x: Double): Double = 1.0 / (1.0 + PortableMath.exp(-x))

    /** P(Red wins the lane) with [k] pushes still needed to win and [m] to lose. */
    private fun laneWin(k: Int, m: Int, w: Int, p: Double): Double {
        if (k <= 0) return 1.0
        if (m <= 0) return 0.0
        val win = if (k == 1 || w <= 1) 1.0 else laneWin(k - 1, m + 1, w - 1, 0.5)
        val lose = if (m == 1 || w <= 1) 0.0 else laneWin(k + 1, m - 1, w - 1, 0.5)
        return p * win + (1 - p) * lose
    }
}
