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
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.match.Team

/**
 * Whisper snipes a hero whose discard is empty, or steps through empty minion spawns.
 * Silver does nothing until someone in radius has already discarded.
 */
object WhisperHeuristics : HeroHeuristics {
    override val heroId: String = "whisper"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.ASSASSIN,
        idealDistance = 2,
        aggression = 0.6,
        laneWeight = 0.45,
        upgradeOrder = listOf(
            "whisper-blue-2a",
            "whisper-red-2a",
            "whisper-green-2a",
            "blue",
            "red",
            "green",
        ),
        keepDefender = false,
    )

    override fun adjust(ctx: HContext, d: Decision, choice: Choice): Double = when (d.kind) {
        DecisionKind.PLAY_CARD -> play(ctx, choice)
        else -> 0.0
    }

    override fun evalTerms(state: GameState, team: Team, heroId: String): Double {
        if (heroId != this.heroId) return 0.0
        val self = state.hero(heroId) ?: return 0.0
        val at = self.at ?: return 0.0
        val sign = if (self.team == team) 1.0 else -1.0
        val hurt = state.heroes.count { hero ->
            hero.team != self.team && hero.at != null &&
                HexGrid.distance(at, hero.at!!) in 1..3 &&
                !BatchD.discardEmpty(hero)
        }
        return sign * hurt.coerceAtMost(2) * 3.0
    }

    override fun threatAdjust(ctx: HContext, enemyHeroId: String, target: Hex): Double {
        if (enemyHeroId != heroId) return 0.0
        val from = ctx.hero(heroId)?.at ?: return 0.0
        if (!BatchD.mayHold(ctx, heroId, "whisper-gold")) return 0.0
        val range = 3
        if (ctx.distance(from, target) !in 1..range) return 0.0
        val victim = ctx.state.heroes.firstOrNull { it.team == ctx.team && it.at == target } ?: return 0.0
        // Gold's snipe is only the hero whose discard is still empty. A full discard is not this threat.
        return if (BatchD.discardEmpty(victim)) 2.0 else 0.0
    }

    private fun play(ctx: HContext, choice: Choice): Double {
        if (choice !is Choice.Card) return 0.0
        val at = ctx.own()?.at ?: return 0.0
        val id = choice.cardId
        if (id == "whisper-gold" || id == "whisper-handicap") {
            val range = ctx.card(id).range.coerceAtLeast(1)
            val snipe = ctx.enemies().any { e ->
                e.at != null && ctx.distance(at, e.at!!) in 1..range && BatchD.discardEmpty(e)
            }
            val melee = BatchD.adjacentEnemies(ctx.state, at, ctx.team) > 0
            // H1/H3: empty discard, or an adjacent unit. Neither bullet and the card does not start.
            return when {
                snipe -> 6.0
                melee -> 2.0
                else -> BatchD.FIZZLE
            }
        }
        if (id == "whisper-silver") {
            val radius = ctx.card(id).radius.coerceAtLeast(1)
            val hurt = ctx.enemies().any { e ->
                e.at != null && ctx.distance(at, e.at!!) in 1..radius && !BatchD.discardEmpty(e)
            }
            // H4: no discarded card in radius, and Silver returns without the immunity or the retrieve.
            return if (hurt) 6.0 else BatchD.FIZZLE
        }
        if (id in trails) {
            val radius = ctx.card(id).radius.coerceAtLeast(2)
            val steps = BatchD.emptyMinionSpawns(ctx, at, radius).coerceAtMost(3)
            val reach = 1 + steps
            val target = BatchD.enemyWithin(ctx, at, 1, reach)
            // H5: empty spawns in the battle zone are the steps. No spawn, he still swings if adjacent.
            return when {
                target && steps > 0 -> 3.0 + steps
                target -> 1.0
                else -> -6.0
            }
        }
        if (id == "whisper-red-2b" || id == "whisper-red-3b") {
            val hero = BatchD.enemyWithin(ctx, at, 1, 1, heroesOnly = true)
            val minion = BatchD.adjacentMinions(ctx.state, at, ctx.team) > 0
            // H6: the hero and the minion are the two hits, once someone in radius has discarded.
            return if (hero && minion) 5.0 else if (hero || minion) 2.0 else -4.0
        }
        if (id in places) {
            val range = ctx.card(id).range.coerceAtLeast(1)
            val spawns = BatchD.emptyMinionSpawns(ctx, at, range)
            // The place is an empty minion spawn in the battle zone. None, and it does not resolve.
            return if (spawns > 0) 4.0 else BatchD.FIZZLE
        }
        return 0.0
    }

    private val trails = setOf("whisper-red-1", "whisper-red-2a", "whisper-red-3a")
    private val places = setOf("whisper-green-1", "whisper-green-2a", "whisper-green-3a")
}
