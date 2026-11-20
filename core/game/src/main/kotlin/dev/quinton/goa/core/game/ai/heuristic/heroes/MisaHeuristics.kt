package dev.quinton.goa.core.game.ai.heuristic.heroes

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
 * Misa has one attack. She places herself onto it (gold is exact range, not "up to") and
 * spends the sword when that attack is the kill or the lane. Focus steps happen next turn.
 */
object MisaHeuristics : HeroHeuristics {
    override val heroId: String = "misa"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.FIGHTER,
        idealDistance = 4,
        aggression = 0.55,
        laneWeight = 0.55,
        upgradeOrder = listOf("misa-red-2a", "misa-blue-2a", "misa-green-2a", "red", "blue", "green"),
        keepDefender = true,
    )

    override fun adjust(ctx: HContext, d: Decision, choice: Choice): Double = when (d.kind) {
        DecisionKind.PLAY_CARD -> play(ctx, choice)
        DecisionKind.DEFEND -> defend(choice)
        else -> 0.0
    }

    override fun evalTerms(state: GameState, team: Team, heroId: String): Double {
        if (heroId != this.heroId) return 0.0
        val sign = BatchB.sign(state, team, heroId)
        val step = state.effects.any { effect ->
            effect.owner == heroId && effect.cardId.startsWith("misa-green-") &&
                effect.cardId.endsWith("a") && effect.timing == Timing.NEXT_TURN && !effect.spent
        }
        return if (step) sign * 4.0 else 0.0
    }

    override fun threatAdjust(ctx: HContext, enemyHeroId: String, target: Hex): Double {
        if (enemyHeroId != heroId) return 0.0
        val from = ctx.hero(heroId)?.at ?: return 0.0
        val distance = ctx.distance(from, target)
        val range = 4 + ctx.itemBonus(heroId, dev.quinton.goa.core.model.catalog.Stat.RANGE)
        // Gold places her on the empty space at exact range. The spaces in between are not the threat.
        return if (distance == range && BatchB.hold(ctx, heroId) { it.id == "misa-gold" } > 0.2) 6.0 else 0.0
    }

    private fun play(ctx: HContext, choice: Choice): Double {
        val id = BatchB.cardId(choice) ?: return 0.0
        return gold(ctx, id) + sword(ctx, id) + focus(ctx, id) + swoop(ctx, id)
    }

    // H5: outside the zone, gold is how she enters. Exact range 4, or the adjacent minion once she is there.
    private fun gold(ctx: HContext, id: String): Double {
        if (id != "misa-gold") return 0.0
        val at = ctx.own()?.at ?: return 0.0
        val outside = !ctx.inBattleZone(at)
        val minion = BatchB.enemyMinionWithin(ctx, at, 1)
        return when {
            minion && !outside -> 8.0
            outside -> 8.0
            else -> 0.0
        }
    }

    // H1: the sword is the one attack. Play it when a body is already adjacent.
    private fun sword(ctx: HContext, id: String): Double {
        if (id != "misa-red-1" && id != "misa-red-2a" && id != "misa-red-3a") return 0.0
        val at = ctx.own()?.at ?: return 0.0
        val hero = ctx.enemies().any { enemy ->
            enemy.at?.let { ctx.distance(at, it) == 1 } == true && ctx.handCount(enemy.id) == 0
        }
        val minion = BatchB.enemyMinionWithin(ctx, at, 1)
        return when {
            hero -> 14.0
            minion -> 8.0
            else -> -4.0
        }
    }

    // H3: Focus on turn 4 has no next turn. On turns 1–3 it is the step the sword does not have.
    private fun focus(ctx: HContext, id: String): Double {
        if (id != "misa-green-1" && id != "misa-green-2a" && id != "misa-green-3a") return 0.0
        if (ctx.turn >= 4) return -8.0
        val at = ctx.own()?.at ?: return 0.0
        val adjacent = BatchB.enemyHeroWithin(ctx, at, 1) || BatchB.enemyMinionWithin(ctx, at, 1)
        return if (adjacent) -2.0 else 5.0
    }

    // H6: Swoop needs a space touching two enemy units. Without one the primary does not start.
    private fun swoop(ctx: HContext, id: String): Double {
        if (id != "misa-silver") return 0.0
        val at = ctx.own()?.at ?: return 0.0
        return if (twoEnemies(ctx, at)) 6.0 else -8.0
    }

    // H13: the unplayed sword is the attack she still needs. A small defense should take something else.
    private fun defend(choice: Choice): Double {
        val id = BatchB.cardId(choice) ?: return 0.0
        val sword = id == "misa-red-1" || id == "misa-red-2a" || id == "misa-red-3a"
        return if (sword) -6.0 else 0.0
    }

    private fun twoEnemies(ctx: HContext, at: Hex): Boolean {
        var count = 0
        for (enemy in ctx.enemies()) {
            if (enemy.at?.let { ctx.distance(at, it) <= 3 } == true) count++
        }
        for (minion in ctx.state.minions) {
            if (minion.team != ctx.team && minion.at?.let { ctx.distance(at, it) <= 3 } == true) count++
        }
        return count >= 2
    }
}
