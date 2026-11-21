package dev.quinton.goa.core.game.ai.heuristic

import dev.quinton.goa.core.domain.PortableMath
import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.game.engine.GameState
import dev.quinton.goa.core.game.engine.HeroState
import dev.quinton.goa.core.game.engine.PieceState
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.match.Team
import kotlin.math.max
import kotlin.math.min

/**
 * Hand-written position value in coin-equivalents (STRATEGY §2, ADR-085). One minion of lane
 * advantage at an even lane is about 15, one coin is 1, a card in hand is about 2.5.
 * Zero-sum: computed for Red and negated for Blue. A finished game is ±1000.
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

    /**
     * Scale on the lane's worth (60 per lane, 100 at match point). Measured with the 32 playbooks
     * (integration, line-ups 1000–1383, 768 games each): 1.4 beat 1.0 head to head 54.9% and took h10
     * against the old s8 from 49.7% to 54.3%; 0.7 lost 43.4%; 1.8 and 2.5 tied 1.4.
     */
    private const val LANE = 1.4

    /** Share of a kill's lane value that a minion in reach of a card still to play is worth. */
    internal const val LANE_THREAT = 0.3

    fun evaluate(ctx: HContext, team: Team): Double = evaluate(ctx, team, null)

    /**
     * [evaluate], with [exposure] in place of the exposure of one hero ([CardSim]: the card play
     * splits it by who acts first).
     */
    internal fun evaluate(ctx: HContext, team: Team, exposure: Pair<String, Double>?): Double {
        val red = scoreRed(ctx, exposure)
        return if (team == Team.RED) red else -red
    }

    private fun scoreRed(ctx: HContext, exposure: Pair<String, Double>?): Double {
        val state = ctx.state
        state.winner?.let { return if (it == Team.RED) WIN - state.round else -WIN + state.round }
        var v = lane(ctx) + life(ctx, state) + pieces(ctx)
        val early = state.round <= 2 && state.heroes.all { it.level <= 3 }
        for (h in state.heroes) {
            val sign = if (h.team == Team.RED) 1.0 else -1.0
            v += sign * hero(ctx, h, early, exposure?.takeIf { it.first == h.id }?.second)
        }
        if (ctx.profile.heroPlaybooks) {
            for (h in state.heroes) v += ctx.playbooks(h.id).evalTerms(state, Team.RED, h.id)
        }
        return v
    }

    private fun hero(ctx: HContext, h: HeroState, early: Boolean, exposure: Double?): Double {
        var v = h.coins * (if (early) 1.3 else 1.0)
        var levels = h.level * (h.level - 1) / 2.0 + 3.0 * (h.level - 1)
        if (h.coins >= h.level && h.level < 8) levels += 1.5
        if (h.ultimateUnlocked) levels += 8.0
        v += levels
        for ((key, n) in h.counters) v += counter(key, n)
        if (!ctx.roundOver) v += hand(ctx, h)
        val at = h.at
        if (at == null) {
            v += -8.0
            return v
        }
        val bodies = listOf(at) + ctx.state.pieces.filter { it.heroBody == h.id && it.at != null }.map { it.at!! }
        v += bodies.maxOf { ctx.presence(it) }
        v += bodies.maxOf { ctx.opportunity(h.id, it) } * 2.0 * ctx.profile.knob("opp", 1.0)
        val share = ctx.profile.knob("oppLane", LANE_THREAT)
        if (share > 0.0) v += bodies.maxOf { laneThreat(ctx, h, it, share) }
        if (ctx.profile.laneTiming) v += bodies.maxOf { zonePull(ctx, h.id, it) }
        if (ctx.profile.threatMap) v -= exposure ?: bodies.maxOf { ctx.exposure(h.id, it) }
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
        var v = 2.5 * min(n, need) + spareW * max(0, n - need) - 3.0 * max(0, need - n)
        if (h.team == ctx.team || ctx.profile.knowHands) v += 0.12 * h.hand.sumOf { ctx.card(it.id).rating }
        return v
    }

    private fun life(ctx: HContext, state: GameState): Double {
        val last = ctx.lastLifeWorth()
        return HContext.lifeLadder(state.lifeCounters[Team.RED] ?: 0, last) -
            HContext.lifeLadder(state.lifeCounters[Team.BLUE] ?: 0, last)
    }

    private fun lane(ctx: HContext): Double {
        var v = 0.0
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
            var p = sigmoid(1.1 * (a - b))
            if (a > b && 2 * b - a <= 0.25) p = max(p, 0.95)
            if (b > a && 2 * a - b <= 0.25) p = min(p, 0.05)
            val waves = state.wavesLeft[lane] ?: 1
            val w = laneWin(zones.lastIndex - i, i, waves, p)
            val match = ctx.profile.laneTiming && ctx.matchPoint(lane)
            val laneWeight = (if (match) 100.0 else 60.0) * ctx.profile.knob("lane", LANE)
            val margin = if (match) 3.0 else 1.5
            v += laneWeight * (2 * w - 1) + margin * (red - blue)
        }
        return v
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
