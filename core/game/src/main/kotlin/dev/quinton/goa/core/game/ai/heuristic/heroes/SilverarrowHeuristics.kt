package dev.quinton.goa.core.game.ai.heuristic.heroes

import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.ai.heuristic.HContext
import dev.quinton.goa.core.game.ai.heuristic.HeroHeuristics
import dev.quinton.goa.core.game.ai.heuristic.HeroRole
import dev.quinton.goa.core.game.ai.heuristic.HeroStyle
import dev.quinton.goa.core.game.engine.GameState
import dev.quinton.goa.core.game.engine.Timing
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.catalog.Stat
import dev.quinton.goa.core.model.match.Team

/**
 * Silverarrow shoots an exact ring and leaves. Gold and the b-reds do not hit "anything in range"
 * (designer Arty, Discord 2025-05-01). Defense 1 means she does not end adjacent to a hero.
 */
object SilverarrowHeuristics : HeroHeuristics {
    override val heroId: String = "silverarrow"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.RANGED,
        idealDistance = 3,
        aggression = 0.35,
        laneWeight = 0.40,
        upgradeOrder = listOf(
            "silverarrow-red-2a",
            "silverarrow-red-3a",
            "silverarrow-blue-2a",
            "silverarrow-blue-3a",
            "silverarrow-green-2a",
            "silverarrow-green-3a",
            "red",
            "blue",
            "green",
        ),
        keepDefender = true,
    )

    override fun adjust(ctx: HContext, d: Decision, choice: Choice): Double = when (d.kind) {
        DecisionKind.PLAY_CARD -> play(ctx, choice)
        DecisionKind.CHOOSE_OPTION -> option(d, choice)
        DecisionKind.CHOOSE_UNIT -> unit(ctx, d, choice)
        DecisionKind.CHOOSE_SPACE -> space(ctx, choice)
        DecisionKind.DEFEND -> defend(d, choice)
        else -> 0.0
    }

    override fun evalTerms(state: GameState, team: Team, heroId: String): Double {
        if (heroId != this.heroId) return 0.0
        val at = state.hero(heroId)?.at ?: return 0.0
        val live = state.effects.any { effect ->
            effect.owner == heroId && effect.cardId == "silverarrow-silver" &&
                effect.timing == Timing.THIS_ROUND && !effect.spent
        }
        if (!live) return 0.0
        val ally = state.heroes.any { hero ->
            hero.team == state.hero(heroId)?.team && hero.id != heroId &&
                hero.at?.let { HexGrid.distance(at, it) in 1..4 } == true
        }
        // Trailblazer is up. The obstacle shortcut itself is a path the static term does not walk.
        return if (ally) BatchC.sign(state, heroId, team) * 2.0 else 0.0
    }

    override fun threatAdjust(ctx: HContext, enemyHeroId: String, target: Hex): Double {
        if (enemyHeroId != heroId) return 0.0
        val at = ctx.hero(heroId)?.at ?: return 0.0
        val distance = ctx.distance(at, target)
        val ring = 2 + ctx.itemBonus(heroId, Stat.RANGE)
        val exactOnly = ctx.possibleCards(heroId).all { card -> card.card.id !in disk }
        // Gold and the b-reds miss the inside of the ring. The general reach smear counts it.
        return if (exactOnly && distance in 1 until ring) -3.0 else 0.0
    }

    private fun play(ctx: HContext, choice: Choice): Double {
        val id = BatchC.cardId(choice) ?: return 0.0
        return exactGold(ctx, id) + roots(ctx, id) + peel(ctx, id)
    }

    private fun exactGold(ctx: HContext, id: String): Double {
        if (id != "silverarrow-gold" && id != "silverarrow-handicap" && id !in exact) return 0.0
        val printed = if (id.startsWith("silverarrow-gold") || id == "silverarrow-handicap") 2 else 3
        // H1 / H13: exact range. A unit closer than the ring is not a target.
        return if (enemyAt(ctx, printed + ctx.itemBonus(heroId, Stat.RANGE))) 2.0 else -6.0
    }

    private fun roots(ctx: HContext, id: String): Double {
        if (id != "silverarrow-blue-1" && id != "silverarrow-blue-2a" && id != "silverarrow-blue-3a") return 0.0
        val at = ctx.own()?.at ?: return 0.0
        val radius = when (id) {
            "silverarrow-blue-1" -> 2
            "silverarrow-blue-3a" -> 4
            else -> 3
        }
        val locked = BatchC.enemiesWithin(ctx, at, radius).any { enemy ->
            val played = enemy.unresolved.firstOrNull()?.let { ctx.card(it.id) } ?: return@any false
            played.initiative < ctx.card(id).initiative && played.hasMovement
        }
        // H8: Roots stops a Movement action that has not resolved yet.
        return if (locked) 5.0 else -1.5
    }

    private fun peel(ctx: HContext, id: String): Double {
        if (id != "silverarrow-green-1" && id != "silverarrow-green-2a" && id != "silverarrow-green-3a") return 0.0
        val at = ctx.own()?.at ?: return 0.0
        val adjacent = ctx.enemies().any { it.at?.let { pos -> ctx.distance(at, pos) == 1 } == true } ||
            ctx.state.minions.any { it.team != ctx.team && it.at?.let { pos -> ctx.distance(at, pos) == 1 } == true }
        // H6: the peel does not start with nobody adjacent. Take the move instead.
        return if (adjacent) 4.0 else -3.0
    }

    private fun option(d: Decision, choice: Choice): Double {
        if (d.sourceCardId !in snap || choice !is Choice.Option) return 0.0
        // H4: the isolated bullet is the shot. The adjacent bullet is how she finishes and leaves.
        return if (choice.index == 0) 3.0 else -1.0
    }

    private fun unit(ctx: HContext, d: Decision, choice: Choice): Double {
        if (d.sourceCardId != "silverarrow-green-2b" && d.sourceCardId != "silverarrow-green-3b") return 0.0
        val id = BatchC.unitId(choice) ?: return 0.0
        val hero = ctx.state.hero(id) ?: return -4.0
        if (hero.team != ctx.team || id == heroId) return -6.0
        val discarded = hero.cards.any { it.zone == Zone.DISCARD && !it.faceDown }
        // H7: offer the heal to an ally who has a discard. Never to her, never to an enemy.
        return if (discarded) 3.0 else -2.0
    }

    private fun space(ctx: HContext, choice: Choice): Double {
        val hex = BatchC.hexOf(choice) ?: return 0.0
        val beside = ctx.enemies().any { enemy -> enemy.at?.let { ctx.distance(hex, it) == 1 } == true }
        val redInHand = ctx.ownHand().any { it.id.startsWith("silverarrow-red-") }
        // H11: Defense 1. Do not end next to a hero unless red is the card about to block.
        return if (beside && !redInHand) -8.0 else 0.0
    }

    private fun defend(d: Decision, choice: Choice): Double {
        val id = BatchC.cardId(choice) ?: return 0.0
        if (!id.startsWith("silverarrow-red-") || !BatchC.meets(d, id)) return 0.0
        val cheaper = d.options.filterIsInstance<Choice.Card>().any { card ->
            card.cardId != id && BatchC.meets(d, card.cardId)
        }
        // H10: red is the only real Defense. Spend a cheaper card when one still holds.
        return if (cheaper) -2.0 else 2.0
    }

    private fun enemyAt(ctx: HContext, distance: Int): Boolean {
        val at = ctx.own()?.at ?: return false
        if (distance <= 0) return false
        val hero = ctx.enemies().any { enemy -> enemy.at?.let { ctx.distance(at, it) == distance } == true }
        val minion = ctx.state.minions.any { minion ->
            minion.team != ctx.team && minion.at?.let { ctx.distance(at, it) == distance } == true
        }
        return hero || minion
    }

    private val exact: Set<String> = setOf("silverarrow-red-2b", "silverarrow-red-3b")
    private val snap: Set<String> = setOf("silverarrow-red-1", "silverarrow-red-2a", "silverarrow-red-3a")
    private val disk: Set<String> = snap + setOf("silverarrow-green-1", "silverarrow-blue-2b", "silverarrow-blue-3b")
}
