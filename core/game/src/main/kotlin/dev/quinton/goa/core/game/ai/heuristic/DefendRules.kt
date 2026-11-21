package dev.quinton.goa.core.game.ai.heuristic

import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.model.catalog.CardColor

/**
 * DEFEND (GENERAL §6, G77–G84). A card that holds is always better than the defeat, except the priced
 * death of G81; among the cards that hold, the one we miss least. Never a slip ([HeuristicAi] treats
 * a defense as critical). G79 (defense text that still does something) is read through the one-ply
 * look the engine gives a card without a preview (G84); the catalog numbers carry no such flag.
 */
internal object DefendRules {
    fun score(ctx: HContext, d: Decision, o: Choice): Double {
        val total = d.context.attackTotal ?: 0
        val ranged = d.context.rangedAttack == true
        val sure = d.options.filterIsInstance<Choice.Card>()
            .filter { holds(ctx, d, it.cardId, total, ranged) }
            .map { it.cardId }
            .toSet()
        if (o !is Choice.Card) return decline(ctx, sure)
        val preview = d.context.defensePreview[o.cardId]
        val card = ctx.card(o.cardId)
        val keep = keepCost(ctx, d.seat, o.cardId)
        return when {
            o.cardId in sure && sacrifice(ctx, sure) && card.color == CardColor.RED -> -1.0

            // G78, G83: the cheapest card that holds. G82: a Block that holds before a numbered attack card.
            o.cardId in sure ->
                HOLDS - keep + if (d.context.blockPreview[o.cardId] == true && card.block) BLOCK else 0.0

            // G84: no preview; the text decides, so the look (or a small hope) does.
            preview == null && !card.blocksRanged && !card.blocksMelee -> 2.5 - keep * 0.15

            // G77: a short defense discards the card and the hero is still defeated.
            else -> -1000.0
        }
    }

    private fun decline(ctx: HContext, sure: Set<String>): Double = when {
        sure.isEmpty() -> 0.5
        sacrifice(ctx, sure) -> 2.0
        else -> -30.0
    }

    /**
     * G81 (from 9): a level 1–3 hero with Life to spare keeps its only Red attack and takes the defeat,
     * when the attack is worth more this round than a cheap death.
     */
    private fun sacrifice(ctx: HContext, sure: Set<String>): Boolean {
        if (!ctx.profile.twoTurnPlans || sure.size != 1) return false
        val h = ctx.own() ?: return false
        if (h.level > 3 || ctx.deathsToLose(h.team) < 2 || ctx.life(h.team) < 4 || ctx.turn < 3) return false
        if (ctx.playsLeft(h.id) <= 0) return false
        val card = ctx.card(sure.single())
        return card.color == CardColor.RED && card.attackCard
    }

    private fun holds(ctx: HContext, d: Decision, cardId: String, total: Int, ranged: Boolean): Boolean {
        if (d.context.blockPreview[cardId] == true) return true
        val preview = d.context.defensePreview[cardId]
        if (preview != null) return preview >= total
        val card = ctx.card(cardId)
        return (ranged && card.blocksRanged) || (!ranged && card.blocksMelee)
    }

    /**
     * What throwing [cardId] costs for the rest of the round (G78): its action value while turns remain,
     * the attack we still need, the spare we keep for later threats. G80: on turn 4 with no play left,
     * nothing in hand has an action value, so any card that holds is right.
     */
    private fun keepCost(ctx: HContext, seat: String, cardId: String): Double {
        val card = ctx.card(cardId)
        if (ctx.playsLeft(seat) <= 0) return card.rating * LAST_TURN_SHARE
        var v = card.rating
        if (card.attackCard) v += ATTACK_KEPT
        if (card.color == CardColor.BLUE && ctx.turn < 4 && ctx.style(seat).keepDefender) v += 0.8
        return v
    }

    private const val HOLDS = 24.0
    private const val BLOCK = 4.0
    private const val ATTACK_KEPT = 2.2
    private const val LAST_TURN_SHARE = 0.1
}
