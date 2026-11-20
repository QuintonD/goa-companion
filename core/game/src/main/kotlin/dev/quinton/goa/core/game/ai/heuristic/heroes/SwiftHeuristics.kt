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
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.model.match.Team

/**
 * Swift's gold copies the rightmost resolved card, not "the red from earlier"
 * (rulings/swift.md 1). The rifle is a straight line. The shotgun needs a hero on the target.
 */
object SwiftHeuristics : HeroHeuristics {
    override val heroId: String = "swift"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.RANGED,
        idealDistance = 4,
        aggression = 0.55,
        laneWeight = 0.45,
        upgradeOrder = listOf(
            "swift-red-2a",
            "swift-red-3a",
            "swift-blue-2a",
            "swift-blue-3a",
            "swift-green-2a",
            "swift-green-3a",
            "red",
            "blue",
            "green",
        ),
        keepDefender = true,
    )

    override fun adjust(ctx: HContext, d: Decision, choice: Choice): Double = when (d.kind) {
        DecisionKind.PLAY_CARD -> play(ctx, choice)
        DecisionKind.CHOOSE_UNIT -> unit(ctx, d, choice)
        DecisionKind.CHOOSE_SPACE -> space(ctx, choice)
        DecisionKind.DEFEND -> defend(ctx, d, choice)
        DecisionKind.CONFIRM -> confirm(choice, d)
        else -> 0.0
    }

    override fun evalTerms(state: GameState, team: Team, heroId: String): Double {
        if (heroId != this.heroId) return 0.0
        val hero = state.hero(heroId) ?: return 0.0
        val right = BatchC.rightmost(hero) ?: return 0.0
        val gold = hero.card("swift-gold")
        val live = gold != null && gold.zone != Zone.RESOLVED && gold.zone != Zone.DISCARD
        if (!live) return 0.0
        val useful = right.startsWith("swift-red-") || right.startsWith("swift-blue-")
        // The repeat is live while gold is still unplayed and the previous slot is the payload.
        return if (useful) BatchC.sign(state, heroId, team) * 3.0 else 0.0
    }

    override fun threatAdjust(ctx: HContext, enemyHeroId: String, target: Hex): Double {
        if (enemyHeroId != heroId) return 0.0
        val at = ctx.hero(heroId)?.at ?: return 0.0
        if (ctx.distance(at, target) > 2) return 0.0
        val shotgun = ctx.possibleCards(heroId).any { it.card.id == "swift-red-2b" || it.card.id == "swift-red-3b" }
        if (!shotgun) return 0.0
        val onMinion = ctx.state.minions.any { minion ->
            minion.at?.let { ctx.distance(it, target) == 1 && ctx.distance(at, it) <= 2 } == true
        }
        // The shotgun discards the hero standing on the target, not only the target.
        return if (onMinion) 4.0 else 0.0
    }

    private fun play(ctx: HContext, choice: Choice): Double {
        val id = BatchC.cardId(choice) ?: return 0.0
        return reload(ctx, id) + beforeGold(ctx, id)
    }

    private fun reload(ctx: HContext, id: String): Double {
        if (id != "swift-gold" && id != "swift-handicap") return 0.0
        val hero = ctx.own() ?: return 0.0
        val right = BatchC.rightmost(hero)
        if (right == null) {
            val at = hero.at ?: return 0.0
            val minion = BatchC.minionsWithin(ctx, at, 1, friendly = false).isNotEmpty()
            // H2: with nothing to copy, gold defeats the adjacent minion. Coins are paid.
            return if (minion) 4.0 else -2.0
        }
        val card = ctx.card(right)
        val payload = card.color == CardColor.RED || card.attackCard || right == "swift-silver" ||
            right.startsWith("swift-blue-")
        // H1: copy a card whose primary can still be the point of the turn.
        return if (payload) 6.0 else -4.0
    }

    private fun beforeGold(ctx: HContext, id: String): Double {
        if (id == "swift-gold" || id == "swift-handicap") return 0.0
        val hero = ctx.own() ?: return 0.0
        val right = BatchC.rightmost(hero)
        val payload = right != null && BatchC.inHand(hero, "swift-gold") && copies(right)
        // H3: the card played now becomes the one gold copies. Do not cover a payload.
        return if (payload) -4.0 else 0.0
    }

    private fun copies(cardId: String): Boolean =
        cardId.startsWith("swift-red-") || cardId == "swift-silver" || cardId.startsWith("swift-blue-")

    private fun unit(ctx: HContext, d: Decision, choice: Choice): Double {
        val src = d.sourceCardId ?: return 0.0
        val id = BatchC.unitId(choice) ?: return 0.0
        if (src != "swift-red-2b" && src != "swift-red-3b") return rifle(ctx, src, id)
        val at = ctx.state.hero(id)?.at ?: ctx.state.minion(id)?.at ?: return 0.0
        val heroBeside = ctx.enemies().any { enemy ->
            enemy.id != id && enemy.at?.let { ctx.distance(it, at) == 1 } == true
        }
        // H5: the shotgun does not start unless an enemy hero is adjacent to the target.
        return if (heroBeside) 4.0 else -6.0
    }

    private fun rifle(ctx: HContext, src: String, id: String): Double {
        if (src != "swift-red-1" && src != "swift-red-2a" && src != "swift-red-3a") return 0.0
        val from = ctx.own()?.at ?: return 0.0
        val at = ctx.state.hero(id)?.at ?: ctx.state.minion(id)?.at ?: return 0.0
        if (!BatchC.straight(from, at)) return -8.0
        val distance = ctx.distance(from, at)
        // Snipe is the ring only. Prepared Shot and Killshot cannot target their own adjacency.
        val legal = when (src) {
            "swift-red-1" -> distance == 4
            "swift-red-2a" -> distance in 2..4
            else -> distance in 2..5
        }
        return if (legal) 2.0 else -6.0
    }

    private fun space(ctx: HContext, choice: Choice): Double {
        val hex = BatchC.hexOf(choice) ?: return 0.0
        val rifle = ctx.ownHand().any { it.id == "swift-red-1" || it.id == "swift-red-2a" || it.id == "swift-red-3a" }
        val beside = ctx.enemies().any { enemy -> enemy.at?.let { ctx.distance(hex, it) == 1 } == true }
        // H14: the rifle cannot shoot the hero she is standing next to.
        return if (rifle && beside) -6.0 else 0.0
    }

    private fun defend(ctx: HContext, d: Decision, choice: Choice): Double {
        val id = BatchC.cardId(choice) ?: return 0.0
        if (!id.startsWith("swift-red-") || !BatchC.meets(d, id)) return 0.0
        val otherAttack = ctx.ownHand().any { card ->
            card.id != id && (card.color == CardColor.RED || card.id == "swift-gold")
        }
        val blueHolds = d.options.filterIsInstance<Choice.Card>().any { card ->
            card.cardId.startsWith("swift-blue-") && BatchC.meets(d, card.cardId)
        }
        // H13: red is the attack gold copies. Spend a blue that still holds when red is the only shot.
        return if (!otherAttack && blueHolds) -5.0 else 0.0
    }

    private fun confirm(choice: Choice, d: Decision): Double {
        if (d.sourceCardId != "swift-purple-4") return 0.0
        // H11: the extra primary is legal on a minion. The same enemy hero is not.
        return if (choice is Choice.Yes) 2.0 else 0.0
    }
}
