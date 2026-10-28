package dev.quinton.goa.core.game.ai

import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.engine.GameState
import dev.quinton.goa.core.game.engine.MoveSpec
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.game.engine.cardValue
import dev.quinton.goa.core.game.engine.fastTravelDestinations
import dev.quinton.goa.core.game.engine.heavyImmune
import dev.quinton.goa.core.game.engine.reach
import dev.quinton.goa.core.game.engine.upgradesOf
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.model.catalog.PrimaryAction
import dev.quinton.goa.core.model.catalog.Stat
import dev.quinton.goa.core.model.match.Team
import kotlin.math.max
import kotlin.math.min

/**
 * Scores every option of a decision from one state without forking (STRATEGY.md §3 heuristics):
 * the rollout policy of every search, the opponent model's card guess, and Easy's whole brain.
 * Scores are in rough coin-equivalents so that noise temperatures mean the same everywhere.
 *
 * The state may be a determinized sample; the scores of a hero's PLAY_CARD use only that hero's
 * own cards and public positions (never another hero's unrevealed played card).
 */
internal class StaticPolicy(private val ev: Evaluator) {
    fun scores(state: GameState, d: Decision): DoubleArray {
        val ctx = ev.context(state)
        return when (d.kind) {
            DecisionKind.PLAY_CARD -> d.options.map { playScore(ctx, d.seat, it) }

            DecisionKind.CHOOSE_ACTION -> d.options.map { actionScore(ctx, d, it) }

            DecisionKind.CHOOSE_SPACE, DecisionKind.MINION_RETURN -> spaceScores(ctx, d)

            DecisionKind.RESPAWN -> d.options.map {
                if (it is Choice.Space) ctx.placement(d.seat, it.hex) else -8.0
            }

            DecisionKind.CHOOSE_UNIT -> d.options.map { unitScore(ctx, d, it) }

            DecisionKind.DEFEND -> defendScores(ctx, d)

            DecisionKind.CHOOSE_CARD -> d.options.map { cardChoiceScore(ctx, d, it) }

            DecisionKind.REMOVE_MINION -> d.options.map { removeScore(ctx, it) }

            DecisionKind.UPGRADE_CARD -> d.options.map { upgradeScore(ctx, d, it) }

            DecisionKind.KEEP_UPGRADE -> d.options.map { keepScore(ctx, d, it) }

            DecisionKind.CHOOSE_TOKEN -> d.options.map { tokenScore(ctx, d, it) }

            DecisionKind.CONFIRM -> d.options.map { if (it is Choice.Yes) 0.5 else 0.0 }

            DecisionKind.CHOOSE_NUMBER -> d.options.map { if (it is Choice.Number) it.value * 0.1 else 0.0 }

            DecisionKind.ACT_FIRST -> d.options.map { actFirstScore(ctx, it) }

            DecisionKind.CHOOSE_OPTION -> d.options.mapIndexed { i, _ -> -0.01 * i }
        }.toDoubleArray()
    }

    fun best(state: GameState, d: Decision): Choice {
        val s = scores(state, d)
        var bi = 0
        for (i in s.indices) if (s[i] > s[bi]) bi = i
        return d.options[bi]
    }

    // ---- PLAY_CARD -------------------------------------------------------------------------

    /** The round plan of STRATEGY §3.1: Gold, Green, Silver first; Red attacks late; Blue is the spare. */
    fun playScore(ctx: Ctx, seat: String, o: Choice): Double {
        if (o !is Choice.Card) return 0.0
        val hi = ctx.info(seat)
        val h = hi.h
        val p = ev.profile(o.cardId)
        val at = h.at ?: return -p.rating
        val turn = ctx.state.turn
        var v = when (p.color) {
            CardColor.BLUE -> if (turn < 4) -1.2 else 0.4
            CardColor.RED -> if (turn <= 1) -0.6 else 0.2
            CardColor.GOLD -> 0.3
            CardColor.GREEN, CardColor.SILVER -> 0.5
            else -> 0.0
        }
        val enemyUnits = enemyTargets(ctx, h.team)
        if (p.attackCard || p.forcesDiscard) {
            var best = -0.5
            for ((pos, value) in enemyUnits) {
                val d = HexGrid.distance(at, pos)
                if (d <= p.reach) {
                    best = max(best, value)
                } else if (d <= p.reach + 2) {
                    best = max(best, value * 0.3)
                }
            }
            v += best
        }
        if (p.skillCard) {
            val r = max(p.range, p.radius) + 1
            v += if (enemyUnits.any { HexGrid.distance(at, it.first) <= r }) 1.0 else 0.2
        }
        if (p.hasMovement) {
            val far = -ctx.presence(h.team, at)
            if (far > 0) v += min(far, 0.5 * p.movement)
        }
        // Keep the best defender while there is a threat and no spare.
        val exposure = ctx.exposure(hi, at)
        val spare = hi.hand.size - hi.playsLeft
        val bestDef = hi.hand.filter { it.canDefend }.maxOfOrNull { it.defense ?: 0 } ?: 0
        if (p.canDefend && (p.defense ?: 0) >= bestDef && spare <= 1 && turn < 4) v -= 0.8 + min(4.0, exposure * 0.3)
        // Under threat, a fast card that moves gets away before the attacker acts.
        if (exposure > 2 && p.hasMovement) v += p.initiative * 0.12
        return v
    }

    /** Positions and values of enemy units a hero could attack: minions first, the last one most. */
    private fun enemyTargets(ctx: Ctx, team: Team): List<Pair<Hex, Double>> {
        val out = ArrayList<Pair<Hex, Double>>()
        for (m in ctx.state.minions) {
            val at = m.at ?: continue
            if (m.team == team || ctx.g.heavyImmune(m.id)) continue
            val last = ctx.state.minions.count { it.team == m.team && it.lane == m.lane && it.at != null } == 1
            out += at to (if (m.type == MinionType.HEAVY) 4.5 else 3.0) + if (last) 12.0 else 0.0
        }
        for (h in ctx.state.heroes) {
            val at = h.at ?: continue
            if (h.team == team) continue
            out += at to 2.0
        }
        return out
    }

    // ---- CHOOSE_ACTION ---------------------------------------------------------------------

    private fun actionScore(ctx: Ctx, d: Decision, o: Choice): Double {
        if (o !is Choice.Action) return 0.0
        val seat = d.seat
        val h = ctx.state.hero(seat) ?: return 0.0
        val at = h.at ?: return 0.0
        val cardId = d.sourceCardId ?: h.cards.firstOrNull { it.zone == Zone.PLAYED }?.id ?: return 0.0
        val p = ev.profile(cardId)
        val here = ctx.placement(seat, at)
        return when (o.action) {
            ActionType.HOLD -> 0.0

            ActionType.MOVEMENT -> bestMove(ctx, seat, at, cardId) - here

            ActionType.FAST_TRAVEL -> {
                val dests = ctx.g.fastTravelDestinations(seat)
                val best = dests.maxOfOrNull { ctx.placement(seat, it) } ?: here
                best - here - if (p.color == CardColor.RED || p.color == CardColor.GOLD) 1.5 else 0.3
            }

            ActionType.PRIMARY -> when {
                p.attackCard -> attackValue(ctx, seat, at, p.reach, p.attack, p.ranged) + 0.5

                ev.data.card(cardId).primary == PrimaryAction.MOVEMENT -> bestMove(ctx, seat, at, cardId) - here + 0.5

                else -> {
                    val r = max(p.range, p.radius) + 1
                    val near =
                        ctx.state.heroes.any {
                            it.team != h.team &&
                                it.at?.let { e -> HexGrid.distance(e, at) <= r } == true
                        } ||
                            ctx.state.minions.any {
                                it.team != h.team &&
                                    it.at?.let { m -> HexGrid.distance(m, at) <= r } == true
                            }
                    if (near) 1.5 else 0.3
                }
            }

            ActionType.ATTACK -> attackValue(ctx, seat, at, 1, p.attack, false)

            ActionType.CLEAR -> 0.1
        }
    }

    private fun bestMove(ctx: Ctx, seat: String, at: Hex, cardId: String): Double {
        val n = ctx.g.cardValue(seat, cardId, Stat.MOVEMENT) ?: 0
        if (n <= 0) return ctx.placement(seat, at)
        val dests = ctx.g.reach(MoveSpec(seat, 0, n, movementAction = true)).keys
        return dests.maxOfOrNull { ctx.placement(seat, it) } ?: ctx.placement(seat, at)
    }

    private fun attackValue(ctx: Ctx, seat: String, at: Hex, reach: Int, attack: Int, ranged: Boolean): Double {
        val hi = ctx.info(seat)
        var best = -1.0
        for (m in ctx.state.minions) {
            val pos = m.at ?: continue
            if (m.team == hi.h.team || ctx.g.heavyImmune(m.id) || HexGrid.distance(at, pos) > reach) continue
            best = max(best, minionValue(ctx, m.id))
        }
        for (e in ctx.state.heroes) {
            val pos = e.at ?: continue
            if (e.team == hi.h.team || HexGrid.distance(at, pos) > reach) continue
            best = max(best, heroHitValue(ctx, e.id, attack + hi.attackItems, ranged, pos))
        }
        return best
    }

    fun minionValue(ctx: Ctx, minionId: String): Double {
        val m = ctx.state.minion(minionId) ?: return 0.0
        val last = ctx.state.minions.count { it.team == m.team && it.lane == m.lane && it.at != null } == 1
        return (if (m.type == MinionType.HEAVY) 5.0 else 3.5) + if (last) 15.0 else 0.0
    }

    /** Expected worth of attacking enemy hero [id] with [total]: a kill, or the card it must throw. */
    fun heroHitValue(ctx: Ctx, id: String, total: Int, ranged: Boolean, pos: Hex): Double {
        val t = ctx.info(id)
        val set = t.defenseNow
        val safe = set.cards > 0 && (
            (ranged && set.blocksRanged) || (!ranged && set.blocksMelee) ||
                set.total(ctx.minionMod(pos, t.h.team)) >= total
            )
        return if (safe) ctx.discardCost(t) + 0.5 else ctx.killCost(t)
    }

    // ---- spaces ----------------------------------------------------------------------------

    private fun spaceScores(ctx: Ctx, d: Decision): List<Double> {
        val subject = d.context.subjectId ?: d.seat
        val deciderTeam = ctx.state.hero(d.seat)?.team ?: d.team
        val heroBody = ctx.g.heroBodyOf(subject)
        val here = ctx.g.posOf(subject)
        return d.options.map { o ->
            val hex = when (o) {
                is Choice.Space -> o.hex
                else -> here
            } ?: return@map 0.0
            when {
                heroBody != null -> {
                    val sign = if (ctx.state.hero(heroBody)!!.team == deciderTeam) 1.0 else -1.0
                    sign * ctx.placement(heroBody, hex)
                }

                ctx.state.minion(subject) != null -> minionSpaceScore(ctx, subject, hex, deciderTeam)

                else -> 0.0
            }
        }
    }

    /** A minion at [hex]: the Defense it gives or takes from the heroes around it (p.14). */
    private fun minionSpaceScore(ctx: Ctx, minionId: String, hex: Hex, team: Team): Double {
        val m = ctx.state.minion(minionId) ?: return 0.0
        var v = 0.0
        for (h in ctx.state.heroes) {
            val at = h.at ?: continue
            val d = HexGrid.distance(at, hex)
            val effect = when {
                m.type != MinionType.RANGED && d == 1 -> if (m.team == h.team) 1 else -1
                m.type == MinionType.RANGED && d in 1..2 && m.team != h.team -> -1
                else -> 0
            }
            v += if (h.team == team) effect else -effect
            // Our heroes want enemy minions near them to take them.
            if (m.team != team && h.team == team && d == 1) v += 0.3
        }
        return v
    }

    // ---- units, tokens, cards --------------------------------------------------------------

    private fun unitScore(ctx: Ctx, d: Decision, o: Choice): Double {
        if (o !is Choice.Unit) return if (o is Choice.Decline) -0.5 else 0.0
        val id = o.unitId
        val team = ctx.state.hero(d.seat)?.team ?: d.team
        val unitTeam = ctx.g.teamOf(id) ?: return 0.0
        val pos = ctx.g.posOf(id) ?: return 0.0
        if (unitTeam == team) return -1.0
        ctx.state.minion(id)?.let { return minionValue(ctx, id) }
        val body = ctx.g.heroBodyOf(id) ?: return 0.5
        val src = d.sourceCardId?.let { ev.profile(it) }
        val actor = ctx.info(d.seat)
        val total = (src?.attack ?: 0) + actor.attackItems
        return heroHitValue(ctx, body, total, src?.ranged == true, pos)
    }

    private fun tokenScore(ctx: Ctx, d: Decision, o: Choice): Double {
        if (o !is Choice.Token) return 0.0
        val team = ctx.state.hero(d.seat)?.team ?: d.team
        val p = ctx.state.piece(o.tokenId) ?: return 0.0
        val owner = p.team ?: p.owner?.let { ctx.state.hero(it)?.team }
        return if (owner != null && owner != team) 0.5 else -0.5
    }

    private fun defendScores(ctx: Ctx, d: Decision): List<Double> {
        val total = d.context.attackTotal ?: 0
        val ranged = d.context.rangedAttack == true
        val hi = ctx.info(d.seat)
        val usefulLater = hi.playsLeft > 0
        val sure = d.options.filterIsInstance<Choice.Card>().filter { c ->
            val pv = d.context.defensePreview[c.cardId]
            val p = ev.profile(c.cardId)
            if (pv != null) pv >= total else (ranged && p.blocksRanged) || (!ranged && p.blocksMelee)
        }.map { it.cardId }.toSet()
        return d.options.map { o ->
            when (o) {
                is Choice.Card -> {
                    val p = ev.profile(o.cardId)
                    val keep = p.rating + if (usefulLater && p.attackCard) 2.0 else 0.0
                    when {
                        o.cardId in sure -> 20.0 - keep

                        d.context.defensePreview[o.cardId] == null && !p.blocksRanged && !p.blocksMelee ->
                            2.0 -
                                keep * 0.1

                        else -> -10.0 - keep
                    }
                }

                else -> if (sure.isEmpty()) 0.0 else -20.0
            }
        }
    }

    private fun cardChoiceScore(ctx: Ctx, d: Decision, o: Choice): Double {
        val h = ctx.state.hero(d.seat)
        if (o !is Choice.Card) {
            // Declining a retrieve wastes it; declining an optional discard keeps a card.
            val fromHand = d.options.filterIsInstance<Choice.Card>().all { h?.card(it.cardId)?.zone == Zone.HAND }
            return if (fromHand) 0.0 else -2.0
        }
        val p = ev.profile(o.cardId)
        val zone = h?.card(o.cardId)?.zone
        return if (zone == Zone.HAND) -p.rating else p.rating
    }

    private fun removeScore(ctx: Ctx, o: Choice): Double {
        if (o !is Choice.Unit) return 0.0
        val m = ctx.state.minion(o.unitId) ?: return 0.0
        val at = m.at ?: return 0.0
        val enemyDist = ctx.state.heroes.filter { it.team != m.team && it.at != null }
            .minOfOrNull { HexGrid.distance(it.at!!, at) } ?: 9
        val guardsAlly = ctx.state.heroes.any {
            it.team == m.team &&
                it.at?.let { a -> HexGrid.distance(a, at) == 1 } == true
        }
        // Remove the minion the enemy would kill anyway (a kill pays 2 coins, a removal none).
        return -enemyDist - if (guardsAlly && m.type != MinionType.RANGED) 2.0 else 0.0
    }

    private fun actFirstScore(ctx: Ctx, o: Choice): Double {
        if (o !is Choice.Unit) return 0.0
        val h = ctx.state.hero(o.unitId) ?: return 0.0
        val card = h.cards.firstOrNull { it.zone == Zone.PLAYED }?.id ?: return 0.0
        val p = ev.profile(card)
        // A discard effect first strips a defense card for the teammate's attack.
        return (if (p.forcesDiscard) 1.0 else 0.0) + if (p.hasMovement && !p.attackCard) 0.5 else 0.0
    }

    // ---- level up --------------------------------------------------------------------------

    private fun upgradeScore(ctx: Ctx, d: Decision, o: Choice): Double {
        if (o !is Choice.Card) return 0.0
        val h = ctx.state.hero(d.seat) ?: return 0.0
        val card = ev.data.card(o.cardId)
        // The engine's own selection (the preview in the decision's context reads the same).
        val next = ev.data.upgradesOf(h, o.cardId)
        if (next.isEmpty()) return -5.0
        val bestPair = next.maxOf { k -> keepValue(ctx, d.seat, k, next - k) }
        return bestPair - ev.profile(o.cardId).rating + colourBias(card.color)
    }

    private fun keepScore(ctx: Ctx, d: Decision, o: Choice): Double {
        if (o !is Choice.Card) return 0.0
        val others = d.options.filterIsInstance<Choice.Card>().map { it.cardId } - o.cardId
        return keepValue(ctx, d.seat, o.cardId, others)
    }

    private fun keepValue(ctx: Ctx, seat: String, keep: String, items: List<String>): Double =
        ev.profile(keep).rating + items.sumOf { itemValue(ctx, seat, ev.profile(it).item) }

    /** An item's worth: how many of the hero's cards carry the matching icon (STRATEGY §3.10). */
    private fun itemValue(ctx: Ctx, seat: String, stat: Stat?): Double {
        val h = ctx.state.hero(seat) ?: return 0.0
        val cards = h.cards.filter { it.zone in ROUND_ZONES }.map { ev.profile(it.id) }
        return when (stat) {
            Stat.ATTACK -> cards.count { it.attackCard } * 0.9
            Stat.DEFENSE -> cards.count { it.canDefend && it.defense != null } * 0.55
            Stat.INITIATIVE -> cards.size * 0.3
            Stat.MOVEMENT -> cards.count { it.hasMovement } * 0.3
            Stat.RANGE -> cards.count { it.ranged } * 0.7
            Stat.RADIUS -> cards.count { it.radius > 0 } * 0.35
            null -> 0.0
        }
    }

    private fun colourBias(c: CardColor): Double = when (c) {
        CardColor.RED -> 0.6
        CardColor.BLUE -> 0.2
        else -> 0.0
    }

    companion object {
        private val ROUND_ZONES = setOf(Zone.HAND, Zone.PLAYED, Zone.RESOLVED, Zone.DISCARD)
    }
}
