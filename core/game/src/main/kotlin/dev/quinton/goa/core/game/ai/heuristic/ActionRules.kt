package dev.quinton.goa.core.game.ai.heuristic

import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.catalog.CardColor
import kotlin.math.max

/**
 * CHOOSE_ACTION (GENERAL §6, G32–G42). From strength 5 the action is looked at through the engine
 * ([CardSim.after]); these scores order the options, answer below that strength, and finish a look.
 * G36 (an illegal Fast Travel) is the engine's: it offers only legal destinations.
 */
internal object ActionRules {
    fun score(ctx: HContext, d: Decision, o: Choice): Double {
        if (o !is Choice.Action) return 0.0
        val h = ctx.own() ?: return 0.0
        val at = h.at ?: return if (o.action == ActionType.HOLD) 0.0 else -1.0
        val cardId = d.sourceCardId ?: h.unresolved.firstOrNull()?.id ?: return 0.0
        val card = ctx.card(cardId)
        val here = ctx.placement(h.id, at)
        val strike = ctx.strikeFrom(h.id, at, card)
        val base = when (o.action) {
            // G39: Hold with a minion in reach is a wasted action. G40: otherwise Hold is the floor, 0.
            ActionType.HOLD -> if (strike >= 4.0 && ctx.exposure(h.id, at) < ctx.killWorth(h.id) * 0.5) -8.0 else 0.0

            ActionType.MOVEMENT -> bestMove(ctx, h.id, at, cardId) - here

            // G34: Fast Travel reaches a zone a walk cannot.
            ActionType.FAST_TRAVEL -> bestTravel(ctx, h.id, here, card)

            // G33: a primary the engine already knows does nothing. G32: one that does is the plan.
            ActionType.PRIMARY ->
                if (d.context.primaryPreview?.noEffect != null) -8.0 else primary(ctx, h.id, at, card, here)

            // G37: the secondary attack, when the primary cannot reach.
            ActionType.ATTACK -> strike

            // G38: Clear when the piece is worth more than the attack it costs.
            ActionType.CLEAR -> clearValue(ctx, at) - max(0.0, strike) * 0.5
        }
        return base + planBonus(ctx, o.action)
    }

    /** G35: Fast Travel is not what Red or Gold are for, once a real action is on the card. */
    fun commitment(ctx: HContext, d: Decision, o: Choice): Double {
        if (o !is Choice.Action) return 0.0
        var v = 0.0
        if (o.action == ActionType.FAST_TRAVEL) {
            val card = d.sourceCardId?.let { ctx.card(it) }
            v += when (card?.color) {
                CardColor.RED -> -8.0
                CardColor.GOLD -> -4.0
                else -> 0.0
            }
        }
        // G41: the plan is to be in the next zone; a minion that is not the marginal one waits.
        if (ctx.profile.turnPlans && ctx.plan.intent in LEAVING &&
            (o.action == ActionType.MOVEMENT || o.action == ActionType.FAST_TRAVEL)
        ) {
            v += LEAVE_WEIGHT * ctx.profile.knob("leave", 1.0)
        }
        return v
    }

    fun bestMove(ctx: HContext, heroId: String, at: Hex, cardId: String): Double {
        val dests = ctx.movementDestinations(heroId, cardId)
        if (dests.isEmpty()) return ctx.placement(heroId, at)
        return dests.maxOf { ctx.placement(heroId, it) }
    }

    private fun bestTravel(ctx: HContext, heroId: String, here: Double, card: HCard): Double {
        val dests = ctx.fastTravelDestinations(heroId)
        val best = dests.maxOfOrNull { ctx.placement(heroId, it) } ?: here
        val tax = if (card.color == CardColor.RED || card.color == CardColor.GOLD) 1.7 else 0.25
        return best - here - tax
    }

    private fun primary(ctx: HContext, heroId: String, at: Hex, card: HCard, here: Double): Double = when {
        card.attackCard || card.forcesDiscard -> ctx.strikeFrom(heroId, at, card) + 0.45
        card.hasMovement && card.movement > 0 -> bestMove(ctx, heroId, at, card.id) - here + 0.4
        else -> skillOutlook(ctx, at, card) + 0.2
    }

    fun skillOutlook(ctx: HContext, at: Hex, card: HCard): Double {
        val r = max(card.range, card.radius) + 1
        val nearHero = ctx.enemies().any { e -> e.at?.let { ctx.distance(at, it) <= r } == true }
        val nearMinion = ctx.state.minions.any { m ->
            m.team != ctx.team && m.at?.let { ctx.distance(at, it) <= r } == true
        }
        return if (nearHero || nearMinion) 1.4 else 0.3
    }

    /** G38: an adjacent enemy token at the section-2 piece floor, more when it sits near the fight. */
    private fun clearValue(ctx: HContext, at: Hex): Double {
        var v = 0.15
        for (p in ctx.state.pieces) {
            val pos = p.at ?: continue
            if (!p.token || p.heroBody != null || ctx.distance(at, pos) != 1) continue
            val owner = p.team ?: p.owner?.let { ctx.state.hero(it)?.team }
            if (owner != null && owner != ctx.team) v += if (ctx.inBattleZone(pos)) PIECE_NEAR else PIECE_FAR
        }
        return v
    }

    /** G32, G41, G42: the action that serves the turn's intent. */
    private fun planBonus(ctx: HContext, action: ActionType): Double {
        if (!ctx.profile.turnPlans) return 0.0
        val serves = when (ctx.plan.intent) {
            Intent.WIN, Intent.KILL, Intent.FARM, Intent.PUSH, Intent.CLEAR_MINION, Intent.SETUP ->
                action == ActionType.PRIMARY || action == ActionType.ATTACK

            Intent.FAST_TRAVEL, Intent.LET_PUSH, Intent.ROTATE ->
                action == ActionType.FAST_TRAVEL || action == ActionType.MOVEMENT

            Intent.RETREAT, Intent.RESPAWN, Intent.SURVIVE ->
                action == ActionType.MOVEMENT || action == ActionType.FAST_TRAVEL

            Intent.SET_UP, Intent.BLOCK -> action == ActionType.PRIMARY || action == ActionType.MOVEMENT

            Intent.DEFEND_LANE, Intent.HOLD_LINE ->
                action == ActionType.MOVEMENT || action == ActionType.HOLD || action == ActionType.PRIMARY

            Intent.SPAWN_CAMP -> action == ActionType.MOVEMENT || action == ActionType.FAST_TRAVEL
        }
        return if (serves) PLAN_WEIGHT else 0.0
    }

    private val LEAVING = setOf(Intent.RETREAT, Intent.LET_PUSH, Intent.ROTATE)
    private const val LEAVE_WEIGHT = 8.0
    private const val PLAN_WEIGHT = 4.5
    private const val PIECE_NEAR = 1.2
    private const val PIECE_FAR = 0.6
}
