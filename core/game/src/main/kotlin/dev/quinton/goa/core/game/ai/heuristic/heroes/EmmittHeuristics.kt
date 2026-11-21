package dev.quinton.goa.core.game.ai.heuristic.heroes

import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.ai.heuristic.HContext
import dev.quinton.goa.core.game.ai.heuristic.HeroHeuristics
import dev.quinton.goa.core.game.ai.heuristic.HeroRole
import dev.quinton.goa.core.game.ai.heuristic.HeroStyle
import dev.quinton.goa.core.game.engine.HeroState
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.catalog.Stat
import kotlin.math.max

/**
 * Emmitt: a support who bends the turn order. His primary red is a big punch the defender
 * answers with Initiative instead of Defense, so a fast card is the block and a slow one is not.
 * The blue traps make a hero who has already resolved a card this turn discard.
 */
object EmmittHeuristics : HeroHeuristics {
    override val heroId: String = "emmitt"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.SUPPORT,
        idealDistance = 1,
        aggression = 0.40,
        laneWeight = 0.40,
        upgradeOrder = listOf("red", "blue", "green"),
        keepDefender = true,
    )

    override fun adjust(ctx: HContext, d: Decision, choice: Choice): Double = when (d.kind) {
        DecisionKind.PLAY_CARD -> play(ctx, choice)
        DecisionKind.CHOOSE_UNIT -> unit(ctx, d, choice)
        else -> 0.0
    }

    private fun play(ctx: HContext, choice: Choice): Double {
        if (choice !is Choice.Card) return 0.0
        val me = ctx.own() ?: return 0.0
        val at = me.at ?: return 0.0
        return when (choice.cardId) {
            in PUNCH -> punch(ctx, me, at, choice.cardId)

            in SNARE -> snare(ctx, me, at, choice.cardId)

            in GOLD -> if (ctx.turn == 1) -2.0 else 0.0

            // H2: no flip on turn 1
            else -> 0.0
        }
    }

    /**
     * H3: the punch against a hero is decided by the Initiative of the card they defend with
     * (plus Initiative items and minion modifiers). The general book read their Defense; this
     * replaces its estimate for adjacent heroes.
     */
    private fun punch(ctx: HContext, me: HeroState, at: Hex, cardId: String): Double {
        val card = ctx.card(cardId)
        val total = card.attack + ctx.itemBonus(me.id, Stat.ATTACK)
        var best = BatchASupport.bestMinionOn(ctx, HexGrid.neighbors(at).toSet())
        for (e in BatchASupport.enemiesWithin(ctx, at, 1)) {
            best = max(best, punchWorth(ctx, e, total) * BatchASupport.aggression(ctx, me.id))
        }
        return BatchASupport.correction(ctx, me.id, at, card, best)
    }

    /** The punch on [e]: a kill unless they hold a defense card whose Initiative total reaches [total]. */
    private fun punchWorth(ctx: HContext, e: HeroState, total: Int): Double {
        if (e.hand.isEmpty()) return ctx.killWorth(e.id)
        val pos = e.at ?: return 0.0
        val bonus = ctx.itemBonus(e.id, Stat.INITIATIVE) + ctx.minionModifier(pos, e.team)
        val hold = BatchASupport.chanceHolds(ctx, e) { c ->
            (c.block && c.blocksMelee) || (c.canDefend && !c.block && c.initiative + bonus >= total)
        }
        val chance = 1.0 - hold
        return chance * ctx.killWorth(e.id) + (1.0 - chance) * ctx.discardWorth(e)
    }

    /**
     * H10: the trap hits an enemy in range who has already resolved a card this turn: forward,
     * the one whose card is faster than this one; in Reverse Time, slower. "If able" on an empty
     * hand does nothing; Time Bomb defeats instead.
     */
    private fun snare(ctx: HContext, me: HeroState, at: Hex, cardId: String): Double {
        val card = ctx.card(cardId)
        val ours = card.initiative + ctx.itemBonus(me.id, Stat.INITIATIVE)
        val reverse = BatchASupport.effectLive(ctx, heroId, GOLD)
        val orDefeat = cardId == "emmitt-blue-3a"
        val reach = card.range + ctx.itemBonus(me.id, Stat.RANGE)
        var best = 0.0
        for (e in BatchASupport.enemiesWithin(ctx, at, reach)) {
            val theirs = ctx.itemBonus(e.id, Stat.INITIATIVE)
            val p = if (e.id in ctx.state.turnRecord.resolved) {
                1.0
            } else {
                BatchASupport.chancePlays(ctx, e.id) { c ->
                    if (reverse) c.initiative + theirs < ours else c.initiative + theirs > ours
                }
            }
            best = max(best, p * BatchASupport.discardValue(ctx, e, orDefeat))
        }
        return if (best <= 0.0) -2.0 else best - 1.4
    }

    private fun unit(ctx: HContext, d: Decision, choice: Choice): Double {
        if (choice !is Choice.Unit) return 0.0
        val src = d.sourceCardId ?: return 0.0
        // H3 at resolution: the same Initiative reading on the punch's target.
        if (src in PUNCH) {
            val e = ctx.hero(choice.unitId)?.takeIf { it.team != ctx.team } ?: return 0.0
            val total = ctx.card(src).attack + ctx.itemBonus(d.seat, Stat.ATTACK)
            return punchWorth(ctx, e, total) - ctx.heroStrikeWorth(e.id, total, false)
        }
        // H6: the Glitch is picked by the enemy who can least punish the landing: one who has acted.
        if (src == "emmitt-silver") {
            return if (choice.unitId in ctx.state.turnRecord.resolved) 2.0 else 0.0
        }
        return 0.0
    }
}

private val GOLD = setOf("emmitt-gold", "emmitt-handicap")
private val PUNCH = setOf("emmitt-red-1", "emmitt-red-2a", "emmitt-red-3a")
private val SNARE = setOf("emmitt-blue-1", "emmitt-blue-2a", "emmitt-blue-3a")
