package dev.quinton.goa.core.game.ai.heuristic

import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.game.engine.upgradesOf
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.model.catalog.Stat

/**
 * CHOOSE_CARD (G72–G76), UPGRADE_CARD (G103–G106) and KEEP_UPGRADE (G107–G112).
 *
 * Not implemented: G74 and G75 (naming the colour an enemy will discard or reveal) need a question
 * shape the engine asks as an option list; that list is opaque and goes through the one-ply look.
 * G105 and G112 (initiative clusters against every enemy Gold, front-line defense) are left to hero
 * playbooks' [HeroStyle.upgradeOrder]. G106 holds by construction: hidden upgrades are never read.
 */
internal object CardRules {
    /** G72 a discard throws the least useful card and keeps the answer to a lethal threat; G73, G76 take the best. */
    fun choose(ctx: HContext, d: Decision, o: Choice): Double {
        val h = ctx.own()
        if (o !is Choice.Card) {
            val fromHand = d.options.filterIsInstance<Choice.Card>().all { h?.card(it.cardId)?.zone == Zone.HAND }
            return if (fromHand) 0.15 else -2.0
        }
        val zone = h?.card(o.cardId)?.zone
        val card = ctx.card(o.cardId)
        if (zone == Zone.HAND || zone == null) {
            // G72: throwing our best defense while it is the spare costs the insurance too.
            val best = ctx.ownHand().filter { it.canDefend }.maxOfOrNull { it.defense ?: 0 } ?: 0
            val spare = if (card.canDefend && (card.defense ?: 0) >= best && ctx.spare(ctx.seat) <= 1 &&
                enemiesHaveAttacks(ctx)
            ) {
                spareWeightOf(ctx.turn)
            } else {
                0.0
            }
            return -card.rating - spare
        }
        // G73: a retrieve takes the defense when the hand has no spare, else the action value.
        val defense = if (ctx.spare(ctx.seat) <= 0) (card.defense ?: 0) * RETRIEVE_DEFENSE else 0.0
        return card.rating + defense
    }

    fun upgrade(ctx: HContext, d: Decision, o: Choice): Double {
        if (o !is Choice.Card) return 0.0
        val h = ctx.own() ?: return 0.0
        val next = ctx.session.engineData.upgradesOf(h, o.cardId)
        if (next.isEmpty()) return -5.0
        val best = next.maxOf { kept -> keepValue(ctx, d.seat, kept, next - kept) }
        return best - ctx.card(o.cardId).rating + upgradeBias(ctx, d.seat, o.cardId)
    }

    /** G107: keep the card the plan plays; the other becomes its item (G108–G111). */
    fun keep(ctx: HContext, d: Decision, o: Choice): Double {
        if (o !is Choice.Card) return 0.0
        val others = d.options.filterIsInstance<Choice.Card>().map { it.cardId } - o.cardId
        return keepValue(ctx, d.seat, o.cardId, others) + upgradeBias(ctx, d.seat, o.cardId) * 0.25
    }

    private fun keepValue(ctx: HContext, seat: String, keep: String, items: List<String>): Double =
        ctx.card(keep).rating + items.sumOf { itemValue(ctx, seat, ctx.card(it).item) }

    /** An item's worth: how many of the hero's round cards carry the matching icon. */
    private fun itemValue(ctx: HContext, seat: String, stat: Stat?): Double {
        val h = ctx.state.hero(seat) ?: return 0.0
        val cards = h.cards.filter { it.zone in ROUND_ZONES }.map { ctx.card(it.id) }
        return when (stat) {
            // G109: an attack point counts on every attack card (less from 7, past the threshold that matters).
            Stat.ATTACK -> cards.count { it.attackCard } * if (ctx.profile.initiativeReading) 0.8 else 1.0
            // G108: a defense point on every card that defends.
            Stat.DEFENSE -> cards.count { it.canDefend && it.defense != null } * 0.7
            // G110: initiative, a little on every card.
            Stat.INITIATIVE -> cards.size * 0.35
            // G111: movement, range and radius on the cards that use them.
            Stat.MOVEMENT -> cards.count { it.hasMovement } * 0.35
            Stat.RANGE -> cards.count { it.ranged } * 0.75
            Stat.RADIUS -> cards.count { it.radius > 0 } * 0.4
            null -> 0.0
        }
    }

    /** G104: the hero's [HeroStyle.upgradeOrder] (from 5, with playbooks); G103: Red by default. */
    private fun upgradeBias(ctx: HContext, seat: String, cardId: String): Double {
        val card = ctx.card(cardId)
        val order = ctx.style(seat).upgradeOrder
        val colour = card.color.name.lowercase()
        val rank = order.indexOfFirst { it.equals(colour, ignoreCase = true) || it == cardId }
        if (rank < 0) return if (card.color == CardColor.RED) 0.35 else 0.0
        return 1.3 - rank * 0.4
    }

    private const val RETRIEVE_DEFENSE = 0.5
    private val ROUND_ZONES = setOf(Zone.HAND, Zone.PLAYED, Zone.RESOLVED, Zone.DISCARD)
}
