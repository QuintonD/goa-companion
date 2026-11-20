package dev.quinton.goa.core.game.ai.heuristic.heroes

import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.ai.heuristic.HContext
import dev.quinton.goa.core.game.ai.heuristic.HeroHeuristics
import dev.quinton.goa.core.game.ai.heuristic.HeroRole
import dev.quinton.goa.core.game.ai.heuristic.HeroStyle
import dev.quinton.goa.core.game.engine.GameState
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/**
 * Ursafar gets angry on a minion, then stands where they have to walk. A card's own
 * "you are enraged" does not satisfy that card. Green never enrages him.
 */
object UrsafarHeuristics : HeroHeuristics {
    override val heroId: String = "ursafar"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.FIGHTER,
        idealDistance = 1,
        aggression = 0.4,
        laneWeight = 0.55,
        upgradeOrder = listOf(
            "ursafar-blue-2a",
            "ursafar-red-2a",
            "ursafar-green-2a",
            "blue",
            "red",
            "green",
        ),
        keepDefender = true,
    )

    override fun adjust(ctx: HContext, d: Decision, choice: Choice): Double = when (d.kind) {
        DecisionKind.PLAY_CARD -> play(ctx, choice)
        DecisionKind.CHOOSE_CARD -> copy(ctx, d, choice)
        else -> 0.0
    }

    override fun evalTerms(state: GameState, team: Team, heroId: String): Double {
        if (heroId != this.heroId) return 0.0
        val self = state.hero(heroId) ?: return 0.0
        val sign = if (self.team == team) 1.0 else -1.0
        val at = self.at ?: return sign * -2.0
        val angry = BatchD.enraged(state, heroId)
        val beside = BatchD.adjacentMinions(state, at, self.team, MinionType.MELEE) +
            BatchD.adjacentMinions(state, at, self.team, MinionType.RANGED) +
            BatchD.adjacentMinions(state, at, self.team, MinionType.HEAVY)
        val v = when {
            angry -> 6.0
            beside > 0 -> 2.0
            state.turn <= 2 -> -3.0
            else -> 0.0
        }
        return sign * v
    }

    override fun threatAdjust(ctx: HContext, enemyHeroId: String, target: Hex): Double {
        if (enemyHeroId != heroId) return 0.0
        if (!BatchD.enraged(ctx.state, heroId)) return 0.0
        if (!BatchD.mayHold(ctx, heroId, "ursafar-gold")) return 0.0
        val from = ctx.hero(heroId)?.at ?: return 0.0
        // H18: once enraged, Gold's step reaches a hero two away. Calm, that step is not offered.
        return if (ctx.distance(from, target) == 2) 1.5 else 0.0
    }

    private fun play(ctx: HContext, choice: Choice): Double {
        if (choice !is Choice.Card) return 0.0
        val at = ctx.own()?.at ?: return 0.0
        val id = choice.cardId
        val calm = !BatchD.enraged(ctx.state, ctx.seat)
        val beside = BatchD.adjacentEnemies(ctx.state, at, ctx.team) > 0
        val besideMinion = BatchD.adjacentMinions(ctx.state, at, ctx.team) > 0
        if (id in greens) {
            // Greens return immediately while he is calm, and the text does not enrage him.
            return if (calm) BatchD.FIZZLE else 4.0
        }
        if (id == "ursafar-silver") {
            // A calm roar still enrages him. The copy happens only when he already is.
            return if (calm) 2.0 else 6.0
        }
        if (id.startsWith("ursafar-blue")) {
            // H2: the one-space Blue is how a calm bear reaches the minion and gets angry.
            return if (calm && !besideMinion) 5.0 else if (calm) 1.0 else 0.0
        }
        if (id in swings) {
            if (!beside) return BatchD.FIZZLE
            // H1: adjacent, the swing connects and he becomes enraged. No neighbour, it does not.
            return if (calm && besideMinion) 6.0 else 2.0
        }
        return 0.0
    }

    private fun copy(ctx: HContext, d: Decision, choice: Choice): Double {
        if (d.sourceCardId != "ursafar-silver" || choice !is Choice.Card) return 0.0
        val card = ctx.card(choice.cardId)
        // The copied primary should be an attack he has already turned on.
        return if (card.attackCard) 3.0 else -2.0
    }

    private val greens = setOf(
        "ursafar-green-1",
        "ursafar-green-2a",
        "ursafar-green-2b",
        "ursafar-green-3a",
        "ursafar-green-3b",
    )
    private val swings = setOf(
        "ursafar-gold",
        "ursafar-handicap",
        "ursafar-red-1",
        "ursafar-red-2a",
        "ursafar-red-2b",
        "ursafar-red-3a",
        "ursafar-red-3b",
    )
}
