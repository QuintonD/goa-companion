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
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.match.Team

/**
 * Wasp on the melee Red: one adjacent hit, a splash or a shove, then leave. The boomerang
 * and Telekinesis cannot target a straight line, and adjacent is a straight line.
 */
object WaspHeuristics : HeroHeuristics {
    override val heroId: String = "wasp"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.SUPPORT,
        idealDistance = 1,
        aggression = 0.45,
        laneWeight = 0.55,
        upgradeOrder = listOf(
            "wasp-red-2a",
            "wasp-green-2b",
            "wasp-blue-2b",
            "red",
            "blue",
            "green",
        ),
        keepDefender = true,
    )

    override fun adjust(ctx: HContext, d: Decision, choice: Choice): Double = when (d.kind) {
        DecisionKind.PLAY_CARD -> play(ctx, choice)
        else -> 0.0
    }

    override fun evalTerms(state: GameState, team: Team, heroId: String): Double {
        if (heroId != this.heroId) return 0.0
        val self = state.hero(heroId) ?: return 0.0
        val sign = if (self.team == team) 1.0 else -1.0
        val dome = state.effects.any { it.owner == heroId && it.cardId == "wasp-silver" && !it.spent }
        val lock = state.effects.any {
            it.owner == heroId && (it.cardId == "wasp-gold" || it.cardId == "wasp-handicap") &&
                it.timing == Timing.THIS_TURN && !it.spent
        }
        var v = 0.0
        if (dome) v += 3.0
        if (lock) v += 2.0
        return sign * v
    }

    override fun threatAdjust(ctx: HContext, enemyHeroId: String, target: Hex): Double {
        if (enemyHeroId != heroId) return 0.0
        val from = ctx.hero(heroId)?.at ?: return 0.0
        val curved = curve.any { BatchD.mayHold(ctx, heroId, it) }
        if (!curved) return 0.0
        val d = ctx.distance(from, target)
        if (d !in 2..3) return 0.0
        // The curve hits off the line. A straight neighbour is the melee Red, already in printed reach.
        return if (!HexGrid.inStraightLine(from, target)) 1.5 else 0.0
    }

    private fun play(ctx: HContext, choice: Choice): Double {
        if (choice !is Choice.Card) return 0.0
        val at = ctx.own()?.at ?: return 0.0
        val id = choice.cardId
        if (id in curve) {
            val range = ctx.attackReach(ctx.seat, ctx.card(id)).coerceAtLeast(ctx.card(id).range)
            val open = BatchD.offLine(ctx, at, range.coerceAtLeast(2))
            // Boomerang and Telekinesis have no target when every unit is on a line from her.
            return if (open) 5.0 else BatchD.FIZZLE
        }
        if (id in shock) {
            val adjacent = BatchD.adjacentEnemies(ctx.state, at, ctx.team) > 0
            val splash = BatchD.enemyWithin(ctx, at, 2, ctx.card(id).radius.coerceAtLeast(2), heroesOnly = true)
            // The primary is the neighbour. The discard is a hero who is in radius and not adjacent.
            return when {
                adjacent && splash -> 5.0
                adjacent -> 2.0
                else -> -4.0
            }
        }
        if (id == "wasp-silver") {
            val inside = BatchD.enemyWithin(ctx, at, 1, ctx.card(id).radius.coerceAtLeast(2), heroesOnly = true)
            val outside = ctx.enemies().any { e ->
                e.at != null && ctx.distance(at, e.at!!) > ctx.card(id).radius.coerceAtLeast(2)
            }
            // The dome matters when one enemy is inside it and another is not.
            return if (inside && outside) 4.0 else 0.0
        }
        if (id == "wasp-gold" || id == "wasp-handicap") {
            // The lock is attached to an attack. No neighbour, and there is no attack.
            return if (BatchD.adjacentEnemies(ctx.state, at, ctx.team) > 0) 3.0 else -4.0
        }
        if (id == "wasp-blue-2b" || id == "wasp-blue-3b") {
            // The push wants two neighbours, or one hero who will hit a wall. Neighbours are the part we can see.
            val n = BatchD.adjacentEnemies(ctx.state, at, ctx.team)
            return if (n >= 2) 4.0 else if (n == 1) 1.5 else -3.0
        }
        return 0.0
    }

    private val curve = setOf("wasp-red-2b", "wasp-red-3b", "wasp-green-2b", "wasp-green-3b")
    private val shock = setOf("wasp-red-1", "wasp-red-2a", "wasp-red-3a")
}
