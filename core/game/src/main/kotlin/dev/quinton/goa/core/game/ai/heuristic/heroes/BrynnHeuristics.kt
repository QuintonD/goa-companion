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
import dev.quinton.goa.core.game.heroes.BrynnHero
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.match.Team

/**
 * Brynn: a fighter whose red becomes a real hit when the target is crowded.
 * She is one of the obstacles. Three around the hero is the bonus.
 */
object BrynnHeuristics : HeroHeuristics {
    override val heroId: String = "brynn"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.FIGHTER,
        idealDistance = 1,
        aggression = 0.55,
        laneWeight = 0.50,
        upgradeOrder = listOf("red", "blue", "green"),
        keepDefender = true,
    )

    override fun adjust(ctx: HContext, d: Decision, choice: Choice): Double = when (d.kind) {
        DecisionKind.PLAY_CARD -> play(ctx, choice)
        DecisionKind.CHOOSE_OPTION -> option(ctx, d, choice)
        DecisionKind.CHOOSE_SPACE -> space(ctx, choice)
        DecisionKind.KEEP_UPGRADE -> keep(choice)
        else -> 0.0
    }

    override fun evalTerms(state: GameState, team: Team, heroId: String): Double {
        if (heroId != this.heroId) return 0.0
        // The crowd includes terrain and the water ring. Those live on the map, which this
        // function is not given, so the bound-hero bonus is scored in adjust and threatAdjust.
        return 0.0
    }

    override fun threatAdjust(ctx: HContext, enemyHeroId: String, target: Hex): Double {
        if (enemyHeroId != heroId) return 0.0
        val from = ctx.hero(heroId)?.at ?: return 0.0
        val standing = ctx.state.heroes.firstOrNull { it.at == target } ?: return 0.0
        if (standing.team == ctx.hero(heroId)?.team) return 0.0
        val crowded = with(BrynnHero) { ctx.game.obstaclesAround(standing.id, heroId) } >= 3
        if (!crowded) return 0.0
        val near = HexGrid.distance(from, target)
        if (near == 1 && BatchA.poolHas(ctx, heroId, MELEE)) return 8.0
        if (near in 1..3 && BatchA.poolHas(ctx, heroId, GOLD)) return 6.0
        return 0.0
    }

    private fun play(ctx: HContext, choice: Choice): Double {
        if (choice !is Choice.Card) return 0.0
        return when (choice.cardId) {
            in MELEE -> melee(ctx)
            in GOLD -> gold(ctx)
            "brynn-silver" -> silver(ctx)
            else -> 0.0
        }
    }

    // H1: primary red on a hero who already has two obstacles besides her (the engine counts three).
    private fun melee(ctx: HContext): Double {
        val at = ctx.own()?.at ?: return 0.0
        val bound = ctx.enemies().any { e ->
            e.at?.let { ctx.distance(at, it) == 1 } == true && bound(ctx, e.id)
        }
        return if (bound) 8.0 else 0.0
    }

    // H2: gold on turn 3 or 4 when a bound hero is already in range 3.
    private fun gold(ctx: HContext): Double {
        if (ctx.turn < 3) return 0.0
        val at = ctx.own()?.at ?: return 0.0
        val bound = ctx.enemies().any { e ->
            e.at?.let { ctx.distance(at, it) in 1..3 } == true && bound(ctx, e.id)
        }
        return if (bound) 8.0 else 0.0
    }

    // H14: turn 4 silver is a Defense 2 shove with no later card to use the crowd.
    private fun silver(ctx: HContext): Double {
        if (ctx.turn < 4) return 0.0
        val later = ctx.ownHand().any { it.id in GOLD || it.id in MELEE || it.hasMovement }
        return if (later) -6.0 else 0.0
    }

    // H2: the range bullet is the bound hero. Index 1 is that bullet when both are offered.
    private fun option(ctx: HContext, d: Decision, choice: Choice): Double {
        if (d.sourceCardId !in GOLD || choice !is Choice.Option) return 0.0
        return if (choice.index == 1) 6.0 else 0.0
    }

    // H15: stand next to the hero the melee red will crowd.
    private fun space(ctx: HContext, choice: Choice): Double {
        if (choice !is Choice.Space) return 0.0
        if (ctx.ownHand().none { it.id in MELEE }) return 0.0
        val crowded = ctx.enemies().any { e ->
            val pos = e.at ?: return@any false
            ctx.distance(choice.hex, pos) == 1 && bound(ctx, e.id)
        }
        return if (crowded) 6.0 else 0.0
    }

    private fun keep(choice: Choice): Double {
        if (choice !is Choice.Card) return 0.0
        return if (choice.cardId == "brynn-red-2a" || choice.cardId == "brynn-red-3a") 1.5 else 0.0
    }

    private fun bound(ctx: HContext, enemyId: String): Boolean {
        if (BatchA.ultimateUnlocked(ctx.state, heroId)) return true
        return with(BrynnHero) { ctx.game.obstaclesAround(enemyId, heroId) } >= 3
    }

}

private val GOLD = setOf("brynn-gold", "brynn-handicap")
private val MELEE = setOf("brynn-red-1", "brynn-red-2a", "brynn-red-3a")
