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
 * Wuk plants trees and acts after the table. Gold takes an adjacent minion, or spends a tree
 * for a shot. Two trees already in the fight means he should attack, not plant again.
 */
object WukHeuristics : HeroHeuristics {
    override val heroId: String = "wuk"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.SETUP,
        idealDistance = 2,
        aggression = 0.3,
        laneWeight = 0.7,
        upgradeOrder = listOf(
            "wuk-red-2a",
            "wuk-green-2a",
            "wuk-blue-2a",
            "red",
            "green",
            "blue",
        ),
        keepDefender = true,
    )

    override fun adjust(ctx: HContext, d: Decision, choice: Choice): Double = when (d.kind) {
        DecisionKind.PLAY_CARD -> play(ctx, choice)
        DecisionKind.CHOOSE_OPTION -> option(ctx, d, choice)
        DecisionKind.CONFIRM -> confirm(d, choice)
        DecisionKind.RESPAWN -> respawn(ctx, choice)
        else -> 0.0
    }

    override fun evalTerms(state: GameState, team: Team, heroId: String): Double {
        if (heroId != this.heroId) return 0.0
        val self = state.hero(heroId) ?: return 0.0
        val sign = if (self.team == team) 1.0 else -1.0
        val trees = BatchD.trees(state)
        var v = trees.size.coerceAtMost(3) * 2.5
        val useful = trees.count { tree ->
            state.minions.any { m ->
                m.team != self.team && m.at != null && HexGrid.distance(tree, m.at!!) == 1
            }
        }
        v += useful * 1.5
        return sign * v
    }

    override fun threatAdjust(ctx: HContext, enemyHeroId: String, target: Hex): Double {
        if (enemyHeroId != heroId) return 0.0
        val from = ctx.hero(heroId)?.at ?: return 0.0
        val shoots = treeRed.any { BatchD.mayHold(ctx, heroId, it) }
        if (!shoots) return 0.0
        val nearTree = BatchD.trees(ctx.state).any { HexGrid.distance(it, target) == 1 }
        if (!nearTree || ctx.distance(from, target) !in 1..3) return 0.0
        // A hex beside his tree is the shot. Printed reach does not know the tree is the condition.
        return 1.2
    }

    private fun play(ctx: HContext, choice: Choice): Double {
        if (choice !is Choice.Card) return 0.0
        val at = ctx.own()?.at ?: return 0.0
        val id = choice.cardId
        val trees = BatchD.trees(ctx.state)
        if (id == "wuk-silver") {
            // H1/H2: plant until two trees are in the fight. After that, attack.
            return if (trees.size < 2) 6.0 else -4.0
        }
        if (id == "wuk-gold" || id == "wuk-handicap") {
            val minion = if (id == "wuk-handicap") {
                BatchD.enemyWithin(ctx, at, 1, 1, heroesOnly = true)
            } else {
                BatchD.adjacentMinions(ctx.state, at, ctx.team) > 0
            }
            val tree = trees.any { ctx.distance(at, it) == 1 }
            val shot = tree && BatchD.enemyWithin(ctx, at, 1, 2)
            // H3: the minion bullet spends no tree. No minion and no tree, and neither bullet starts.
            return when {
                minion -> 5.0
                shot -> 3.0
                else -> BatchD.FIZZLE
            }
        }
        if (id in treeRed) {
            val shot = trees.any { tree ->
                ctx.distance(at, tree) <= 3 && ctx.state.minions.any { m ->
                    m.team != ctx.team && m.at != null && ctx.distance(tree, m.at!!) == 1 &&
                        ctx.distance(at, m.at!!) in 1..3
                }
            }
            // H4: the tree bullet wants a unit beside a tree, inside his range.
            return if (shot) 4.0 else 0.0
        }
        if (id == "wuk-blue-2b" || id == "wuk-blue-3b") {
            val n = BatchD.adjacentMinions(ctx.state, at, ctx.team)
            // H9: Dominance excludes minions he is standing on. None, and the battle does not change.
            return if (n >= 2) 5.0 else if (n == 1) 2.0 else -3.0
        }
        return 0.0
    }

    private fun option(ctx: HContext, d: Decision, choice: Choice): Double {
        if (choice !is Choice.Option) return 0.0
        if (d.sourceCardId != "wuk-gold" && d.sourceCardId != "wuk-handicap") return 0.0
        val at = ctx.own()?.at ?: return 0.0
        val minionBullet = choice.index == 0
        val adjacent = BatchD.adjacentMinions(ctx.state, at, ctx.team) > 0
        // H3: keep the tree when the minion is already adjacent.
        return if (minionBullet && adjacent) 4.0 else if (!minionBullet && !adjacent) 3.0 else 0.0
    }

    private fun confirm(d: Decision, choice: Choice): Double {
        if (d.sourceCardId != "wuk-purple-4") return 0.0
        // H11: the Ultimate's plant is a tree. Decline is how a spawn hex gets skipped; Yes is the default.
        return if (choice is Choice.Yes) 1.5 else 0.0
    }

    private fun respawn(ctx: HContext, choice: Choice): Double {
        if (choice !is Choice.Space) return 0.0
        val trees = BatchD.trees(ctx.state)
        if (trees.isEmpty()) return 0.0
        val near = trees.minOf { ctx.distance(choice.hex, it) }
        // H20: the trees are still there. Respawn toward them.
        return (3 - near).coerceAtLeast(0) * 0.5
    }

    private val treeRed = setOf("wuk-red-1", "wuk-red-2a", "wuk-red-3a")
}
