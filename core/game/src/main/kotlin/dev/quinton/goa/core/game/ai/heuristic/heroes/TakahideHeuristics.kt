package dev.quinton.goa.core.game.ai.heuristic.heroes

import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.ai.heuristic.HContext
import dev.quinton.goa.core.game.ai.heuristic.HeroHeuristics
import dev.quinton.goa.core.game.ai.heuristic.HeroRole
import dev.quinton.goa.core.game.ai.heuristic.HeroStyle
import dev.quinton.goa.core.game.engine.GameState
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.catalog.Stat
import dev.quinton.goa.core.model.match.Team

/**
 * Takahide's defender is Float (Defense 8), not a red. Shameful Display is a defeat
 * (designer, Discord 2025-03-06). A gold's primary swaps it away; walking or blocking does not.
 */
object TakahideHeuristics : HeroHeuristics {
    override val heroId: String = "takahide"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.SUPPORT,
        idealDistance = 3,
        aggression = 0.45,
        laneWeight = 0.35,
        upgradeOrder = listOf(
            "takahide-red-2a",
            "takahide-red-3a",
            "takahide-green-2a",
            "takahide-green-3a",
            "takahide-blue-2a",
            "takahide-blue-3a",
            "red",
            "green",
            "blue",
        ),
        keepDefender = true,
    )

    override fun adjust(ctx: HContext, d: Decision, choice: Choice): Double = when (d.kind) {
        DecisionKind.DEFEND -> defend(d, choice)
        DecisionKind.PLAY_CARD -> play(ctx, choice)
        DecisionKind.CHOOSE_CARD -> swap(ctx, d, choice)
        DecisionKind.CHOOSE_UNIT -> sting(ctx, d, choice)
        DecisionKind.CHOOSE_SPACE -> space(ctx, choice)
        else -> 0.0
    }

    override fun evalTerms(state: GameState, team: Team, heroId: String): Double {
        if (heroId != this.heroId) return 0.0
        val hero = state.hero(heroId) ?: return 0.0
        val float = hero.card(floatCard)
        val spent = float?.zone == Zone.RESOLVED || float?.zone == Zone.DISCARD
        // Float on the dashboard is the Defense 8 already spent. Which gold is in hand stays hidden.
        return if (spent) BatchC.sign(state, heroId, team) * -3.0 else 0.0
    }

    override fun threatAdjust(ctx: HContext, enemyHeroId: String, target: Hex): Double {
        if (enemyHeroId != heroId) return 0.0
        val at = ctx.hero(heroId)?.at ?: return 0.0
        val distance = ctx.distance(at, target)
        val sting = ctx.possibleCards(heroId).any { it.card.id == stingCard }
        // Sting is the exact ring, not the disk inside range 3.
        return if (sting && distance in 1..2) -3.0 else 0.0
    }

    private fun defend(d: Decision, choice: Choice): Double {
        val id = BatchC.cardId(choice) ?: return 0.0
        if (id == handicapCard) {
            // H1: choosing Shameful Display is the defeat. Decline, or spend a real card.
            return -40.0
        }
        if (id != floatCard || !BatchC.meets(d, id)) return 0.0
        val cheaper = d.options.filterIsInstance<Choice.Card>().any { card ->
            card.cardId != id && card.cardId != handicapCard && BatchC.meets(d, card.cardId)
        }
        // H2: Float meets almost everything. Keep it when a cheaper card also meets.
        return if (cheaper) -4.0 else 2.0
    }

    private fun play(ctx: HContext, choice: Choice): Double {
        val id = BatchC.cardId(choice) ?: return 0.0
        return floatMove(ctx, id) + allyCards(ctx, id) + wrongGold(ctx, id)
    }

    private fun floatMove(ctx: HContext, id: String): Double {
        if (id != floatCard) return 0.0
        val at = ctx.own()?.at ?: return 0.0
        val far = ctx.distanceToBattleZone(at) >= 3
        val deckGold = ctx.own()?.cards?.any { card ->
            card.id != floatCard && card.id.startsWith("takahide-gold") && card.zone == Zone.DECK
        } == true
        // H3: primary Float swaps, then moves 5. Do that to cross the map, not to spend the block.
        return when {
            far && deckGold -> 4.0
            !far -> -3.0
            else -> 0.0
        }
    }

    private fun allyCards(ctx: HContext, id: String): Double {
        if (id !in allyCards) return 0.0
        val at = ctx.own()?.at ?: return 0.0
        val radius = if (id == "takahide-green-1" || id == "takahide-blue-1") 3 else 4
        // H15: greens and blues do not start with no ally in range.
        return if (BatchC.alliesWithin(ctx, at, radius).isEmpty()) -4.0 else 1.5
    }

    private fun wrongGold(ctx: HContext, id: String): Double {
        if (id != "takahide-silver") return 0.0
        val hero = ctx.own() ?: return 0.0
        val holdingSting = BatchC.inHand(hero, stingCard)
        val holdingStrike = BatchC.inHand(hero, strikeCard)
        val deckHasFloat = hero.cards.any { it.id == floatCard && it.zone == Zone.DECK }
        // H6: Bushido fixes a gold that is not the one this round can use.
        return if ((holdingSting || holdingStrike) && deckHasFloat) 4.0 else -2.0
    }

    private fun swap(ctx: HContext, d: Decision, choice: Choice): Double {
        if (d.sourceCardId != floatCard && d.sourceCardId != "takahide-silver") return 0.0
        val id = BatchC.cardId(choice) ?: return 0.0
        val held = ctx.own()?.hand?.any { it.id == id } == true
        // He cannot swap a gold with itself (designer, Discord 2026-06-16).
        if (held) return -8.0
        // Arriving with Float next round is the block. Sting is the ring.
        return if (id == floatCard) 5.0 else 1.0
    }

    private fun sting(ctx: HContext, d: Decision, choice: Choice): Double {
        val src = d.sourceCardId
        if (src != stingCard && src != strikeCard) return 0.0
        val id = BatchC.unitId(choice) ?: return 0.0
        val at = ctx.own()?.at ?: return 0.0
        val pos = ctx.state.hero(id)?.at ?: ctx.state.minion(id)?.at
        val distance = pos?.let { ctx.distance(at, it) } ?: -1
        val ring = 3 + ctx.itemBonus(heroId, Stat.RANGE)
        // H4 / H5: Sting is the exact ring. Strike is adjacent, then he swaps back to Float.
        return when {
            src == stingCard && distance == ring -> 4.0
            src == stingCard -> -6.0
            distance == 1 -> 3.0
            else -> -4.0
        }
    }

    private fun space(ctx: HContext, choice: Choice): Double {
        if (ctx.turn != 4) return 0.0
        val hex = BatchC.hexOf(choice) ?: return 0.0
        val floatInHand = ctx.own()?.let { BatchC.inHand(it, floatCard) } == true
        val beside = ctx.enemies().any { enemy -> enemy.at?.let { ctx.distance(hex, it) == 1 } == true }
        // H13: turn 4 he does not leave. Do not end adjacent unless Float can still block.
        return if (beside && !floatInHand) -8.0 else 0.0
    }

    private val floatCard = "takahide-gold"
    private val stingCard = "takahide-gold-extra-1"
    private val strikeCard = "takahide-gold-extra-2"
    private val handicapCard = "takahide-handicap"
    private val allyCards: Set<String> = setOf(
            "takahide-green-1",
            "takahide-green-2a",
            "takahide-green-2b",
            "takahide-green-3a",
            "takahide-green-3b",
            "takahide-blue-1",
            "takahide-blue-2a",
            "takahide-blue-2b",
            "takahide-blue-3a",
            "takahide-blue-3b",
        )
}
