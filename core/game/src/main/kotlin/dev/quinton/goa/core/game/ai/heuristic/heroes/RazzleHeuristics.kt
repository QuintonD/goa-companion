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
 * Razzle spreads figures, vanishes the ones that are caught, and does not stack them on a cleave.
 * Attack 2 is a poke. The bodies are the plan (Cryomancy, BGG 3659764).
 */
object RazzleHeuristics : HeroHeuristics {
    override val heroId: String = "razzle"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.SETUP,
        idealDistance = 1,
        aggression = 0.40,
        laneWeight = 0.55,
        upgradeOrder = listOf(
            "razzle-red-2a",
            "razzle-red-3a",
            "razzle-green-2a",
            "razzle-green-3a",
            "razzle-blue-2a",
            "razzle-blue-3a",
            "red",
            "green",
            "blue",
        ),
        keepDefender = true,
    )

    override fun adjust(ctx: HContext, d: Decision, choice: Choice): Double = when (d.kind) {
        DecisionKind.PLAY_CARD -> play(ctx, choice)
        DecisionKind.CHOOSE_SPACE -> space(ctx, d, choice)
        DecisionKind.CHOOSE_UNIT -> unit(ctx, d, choice)
        DecisionKind.DEFEND -> defend(ctx, d, choice)
        DecisionKind.RESPAWN -> respawn(ctx, choice)
        else -> 0.0
    }

    override fun evalTerms(state: GameState, team: Team, heroId: String): Double {
        if (heroId != this.heroId) return 0.0
        val figs = BatchC.bodies(state, heroId)
        if (figs.size < 2) return 0.0
        val sign = BatchC.sign(state, heroId, team)
        var v = 0.0
        val enemies = state.heroes.filter { it.team != state.hero(heroId)?.team && it.at != null }
        for (hex in figs) {
            val beside = enemies.any { HexGrid.distance(hex, it.at!!) == 1 }
            // Spread figures that are not standing on the same enemy.
            if (!beside) v += 3.0
        }
        for (enemy in enemies) {
            val touching = figs.count { HexGrid.distance(it, enemy.at!!) == 1 }
            if (touching >= 2) v -= 8.0
        }
        return sign * v
    }

    override fun threatAdjust(ctx: HContext, enemyHeroId: String, target: Hex): Double {
        if (enemyHeroId != heroId) return 0.0
        val figs = BatchC.bodies(ctx.state, heroId)
        if (figs.isEmpty()) return 0.0
        val touching = figs.count { ctx.distance(it, target) == 1 }
        // One attack into a shared neighbour threatens the one hand twice.
        if (touching >= 2) return 4.0
        val only = figs.singleOrNull() ?: return 0.0
        val gold = ctx.state.hero(heroId)?.let { !BatchC.onDashboard(it, "razzle-gold") } == true
        // H threat: a lone figure is Defense 1, and gold spawns in radius 1 of her.
        return if (gold && ctx.distance(only, target) == 1) 3.0 else 0.0
    }

    private fun play(ctx: HContext, choice: Choice): Double {
        val id = BatchC.cardId(choice) ?: return 0.0
        return openGold(ctx, id) + clustered(ctx, id)
    }

    private fun openGold(ctx: HContext, id: String): Double {
        if (id != "razzle-gold" && id != "razzle-handicap") return 0.0
        if (ctx.turn != 1 || BatchC.bodies(ctx.state, heroId).size != 1) return 0.0
        val at = ctx.own()?.at ?: return 0.0
        val holes = HexGrid.neighbors(at).filter { neighbour ->
            ctx.game.geo.isSpace(neighbour) &&
                ctx.state.heroes.none { it.at == neighbour } &&
                ctx.state.minions.none { it.at == neighbour }
        }
        if (holes.isEmpty()) return 0.0
        val covered = holes.all { BatchC.threatened(ctx, it) }
        // H1: do not spawn on turn 1 into a ring every enemy already reaches.
        return if (covered) -8.0 else 0.0
    }

    private fun clustered(ctx: HContext, id: String): Double {
        if (!stackedOn(ctx)) return 0.0
        // H16: two figures on one cleave. Remove or move; do not attack into the stack.
        return when (id) {
            "razzle-silver", "razzle-red-1", "razzle-red-2a", "razzle-red-3a" -> 8.0
            "razzle-green-1", "razzle-green-2a", "razzle-green-3a" -> 4.0
            "razzle-gold", "razzle-handicap" -> -5.0
            else -> 0.0
        }
    }

    private fun space(ctx: HContext, d: Decision, choice: Choice): Double {
        if (d.sourceCardId != "razzle-gold" && d.sourceCardId != "razzle-handicap") return 0.0
        val hex = BatchC.hexOf(choice) ?: return if (choice is Choice.Decline) 1.0 else 0.0
        val nearFigure = BatchC.bodies(ctx.state, heroId).any { ctx.distance(it, hex) in 1..3 }
        val token = ctx.state.pieces.any { it.token && it.at == hex }
        val bad = stackedLanding(ctx, hex)
        // H2: spawn inside silver's radius, on an empty space, and not onto a second cleave.
        var v = 0.0
        if (nearFigure) v += 4.0
        if (bad) v -= 6.0
        if (token) v -= 3.0
        return v
    }

    private fun unit(ctx: HContext, d: Decision, choice: Choice): Double {
        if (!d.prompt.startsWith("Which one of you")) return 0.0
        val id = BatchC.unitId(choice) ?: return 0.0
        val at = figureAt(ctx, id) ?: return 0.0
        val safe = !BatchC.threatened(ctx, at)
        val otherSafe = BatchC.bodies(ctx.state, heroId).any { hex ->
            hex != at && !BatchC.threatened(ctx, hex)
        }
        // H11: act with a figure that is not the only one left in an enemy's range.
        return if (safe && otherSafe) 0.0 else if (safe) 8.0 else if (otherSafe) -6.0 else 0.0
    }

    private fun defend(ctx: HContext, d: Decision, choice: Choice): Double {
        val id = BatchC.cardId(choice) ?: return 0.0
        if (id != "razzle-silver") return 0.0
        val at = d.context.subjectId?.let { figureAt(ctx, it) } ?: ctx.own()?.at ?: return 0.0
        val others = BatchC.bodies(ctx.state, heroId).count { it != at && ctx.distance(at, it) in 1..3 }
        val othersMeet = d.options.filterIsInstance<Choice.Card>().any { card ->
            card.cardId != id && BatchC.meets(d, card.cardId)
        }
        // H4: silver's Defense is 1 + 2 per other figure in radius. Alone, it is not the block.
        return when {
            others == 0 && othersMeet -> -4.0
            others > 0 && BatchC.meets(d, id) && !othersMeet -> 2.5
            else -> 0.0
        }
    }

    private fun respawn(ctx: HContext, choice: Choice): Double {
        val hex = BatchC.hexOf(choice) ?: return 0.0
        val safeNeighbours = HexGrid.neighbors(hex).count { neighbour ->
            ctx.enemies().none { enemy -> enemy.at?.let { ctx.distance(it, neighbour) == 1 } == true }
        }
        // H15: come back where the next gold has empty spawn spaces, toward the fight.
        return safeNeighbours * 0.7 - ctx.distanceToBattleZone(hex) * 0.6
    }

    private fun stackedOn(ctx: HContext): Boolean {
        val figs = BatchC.bodies(ctx.state, heroId)
        if (figs.size < 2) return false
        return ctx.enemies().any { enemy ->
            val at = enemy.at ?: return@any false
            figs.count { ctx.distance(it, at) == 1 } >= 2 && cleave(ctx, enemy.id)
        }
    }

    private fun stackedLanding(ctx: HContext, hex: Hex): Boolean =
        ctx.enemies().any { enemy ->
            val at = enemy.at ?: return@any false
            if (ctx.distance(hex, at) != 1 || !cleave(ctx, enemy.id)) return@any false
            BatchC.bodies(ctx.state, heroId).any { ctx.distance(it, at) == 1 }
        }

    private fun cleave(ctx: HContext, enemyId: String): Boolean =
        ctx.possibleCards(enemyId).any { it.card.id in multi }

    private fun figureAt(ctx: HContext, id: String): Hex? =
        ctx.state.hero(id)?.at ?: ctx.state.piece(id)?.at

    private val multi: Set<String> = setOf(
            "xargatha-gold",
            "xargatha-handicap",
            "xargatha-red-1",
            "xargatha-red-2b",
            "xargatha-red-3a",
            "xargatha-red-3b",
            "wasp-red-3a",
            "swift-red-2b",
            "swift-red-3b",
            "wuk-red-2b",
            "mortimer-red-1",
            "min-purple-4",
        )
}
