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
 * Widget plays the dragon, then copies a skill that has already resolved. Breath, Gnaw and the
 * swaps need Pyro. Gold's own attack does not.
 */
object WidgetHeuristics : HeroHeuristics {
    override val heroId: String = "widget"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.SETUP,
        idealDistance = 2,
        aggression = 0.4,
        laneWeight = 0.65,
        upgradeOrder = listOf(
            "widget-blue-2a",
            "widget-green-2a",
            "widget-red-2a",
            "blue",
            "green",
            "red",
        ),
        keepDefender = true,
    )

    override fun adjust(ctx: HContext, d: Decision, choice: Choice): Double = when (d.kind) {
        DecisionKind.PLAY_CARD -> play(ctx, choice)
        DecisionKind.CHOOSE_OPTION -> option(d, choice)
        DecisionKind.CHOOSE_CARD -> copy(d, choice)
        DecisionKind.CHOOSE_SPACE -> space(ctx, d, choice)
        else -> 0.0
    }

    override fun evalTerms(state: GameState, team: Team, heroId: String): Double {
        if (heroId != this.heroId) return 0.0
        val self = state.hero(heroId) ?: return 0.0
        val sign = if (self.team == team) 1.0 else -1.0
        val pyro = BatchD.pyro(state) ?: return sign * -2.0
        var v = 5.0
        val besideMinion = state.minions.any { m ->
            m.team != self.team && m.at != null && HexGrid.distance(pyro, m.at!!) == 1
        }
        if (besideMinion) v += 3.0
        return sign * v
    }

    override fun threatAdjust(ctx: HContext, enemyHeroId: String, target: Hex): Double {
        if (enemyHeroId != heroId) return 0.0
        val pyro = BatchD.pyro(ctx.state) ?: return 0.0
        val breath = breaths.any { BatchD.mayHold(ctx, heroId, it) }
        if (!breath) return 0.0
        val d = ctx.distance(pyro, target)
        if (d !in 1..3 || !HexGrid.inStraightLine(pyro, target)) return 0.0
        // H16: Breath is measured from Pyro, which printed reach from Widget does not cover.
        return 2.0
    }

    private fun play(ctx: HContext, choice: Choice): Double {
        if (choice !is Choice.Card) return 0.0
        val at = ctx.own()?.at ?: return 0.0
        val id = choice.cardId
        val pyro = BatchD.pyro(ctx.state)
        val self = ctx.own()
        if (id == "widget-silver") {
            // H1: Pyro off the board is the reason to play Silver. A placed dragon can wait.
            return if (pyro == null) 8.0 else 1.0
        }
        if (id == "widget-gold" || id == "widget-handicap") {
            val copied = self != null && BatchD.resolvedSkill(ctx, self)
            val adjacent = BatchD.adjacentEnemies(ctx.state, at, ctx.team) > 0
            // H2: turn-1 Gold is only the attack. The copy needs a skill already on a turn slot.
            return when {
                copied && adjacent -> 5.0
                copied -> 2.0
                adjacent && ctx.turn >= 3 -> 1.0
                ctx.turn <= 2 && !copied -> -3.0
                else -> 0.0
            }
        }
        if (id in breaths) {
            if (pyro == null) return BatchD.FIZZLE
            val range = ctx.card(id).range.coerceAtLeast(1)
            val lined = ctx.enemies().any { e ->
                e.at != null && ctx.distance(pyro, e.at!!) in 1..range && HexGrid.inStraightLine(pyro, e.at!!)
            }
            // H4: the discard is a hero on a line from Pyro. Widget's own hex does not count.
            return if (lined) 6.0 else BatchD.FIZZLE
        }
        if (id == "widget-green-2b" || id == "widget-green-3b") {
            if (pyro == null) return BatchD.FIZZLE
            val range = ctx.card(id).range.coerceAtLeast(1)
            val meal = ctx.state.minions.any { m ->
                m.team != ctx.team && m.at != null && ctx.distance(at, m.at!!) in 1..range &&
                    ctx.distance(pyro, m.at!!) == 1
            }
            // H5: eat the minion beside Pyro. Then Pyro leaves, so this is the minion, not a setup.
            return if (meal) 6.0 else BatchD.FIZZLE
        }
        if (id in needsPyro) {
            // The swap and the drag fizzle while Pyro is off the board.
            return if (pyro == null) BatchD.FIZZLE else 3.0
        }
        return 0.0
    }

    private fun option(d: Decision, choice: Choice): Double {
        if (d.sourceCardId != "widget-silver" || choice !is Choice.Option) return 0.0
        // H10: index 0 is the place. With Pyro already out, moving both is the other bullet.
        return if (choice.index == 0) 1.0 else 0.5
    }

    private fun copy(d: Decision, choice: Choice): Double {
        if (choice !is Choice.Card) return 0.0
        if (d.sourceCardId != "widget-gold" && d.sourceCardId != "widget-handicap") return 0.0
        val id = choice.cardId
        // H3: Breath on a hero, then the minion, then a swap. Never the unit she just attacked — the engine excludes it.
        return when {
            id in breaths -> 4.0
            id == "widget-green-2b" || id == "widget-green-3b" -> 3.0
            id.startsWith("widget-blue") -> 2.0
            else -> 0.0
        }
    }

    private fun space(ctx: HContext, d: Decision, choice: Choice): Double {
        if (choice !is Choice.Space || d.sourceCardId != "widget-silver") return 0.0
        if (!d.prompt.contains("Pyro")) return 0.0
        val minionSpawn = ctx.game.geo.spaces[choice.hex]?.spawn?.kind?.minion != null
        // H18: a minion spawn deletes Pyro when the wave arrives.
        return if (minionSpawn) -6.0 else 0.0
    }

    private val breaths = setOf("widget-green-1", "widget-green-2a", "widget-green-3a")
    private val needsPyro = setOf(
        "widget-blue-1",
        "widget-blue-2a",
        "widget-blue-2b",
        "widget-blue-3a",
        "widget-blue-3b",
    )
}
