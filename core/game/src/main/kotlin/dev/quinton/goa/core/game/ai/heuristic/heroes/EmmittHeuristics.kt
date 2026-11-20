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
 * Emmitt: a support who flips the order. The punch is the threat. Gold on turn 2 or 3
 * is the card, and only when the flip helps.
 */
object EmmittHeuristics : HeroHeuristics {
    override val heroId: String = "emmitt"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.SUPPORT,
        idealDistance = 1,
        aggression = 0.40,
        laneWeight = 0.40,
        upgradeOrder = listOf("red", "blue", "green"),
        keepDefender = true,
    )

    override fun adjust(ctx: HContext, d: Decision, choice: Choice): Double = when (d.kind) {
        DecisionKind.PLAY_CARD -> play(ctx, choice)
        DecisionKind.CHOOSE_UNIT -> unit(ctx, d, choice)
        else -> 0.0
    }

    override fun evalTerms(state: GameState, team: Team, heroId: String): Double {
        if (heroId != this.heroId) return 0.0
        if (!BatchA.effectLive(state, heroId, GOLD)) return 0.0
        val sign = BatchA.sign(state, team, heroId)
        // H eval: Reverse Time is in force. The slow cards are the fast ones this turn.
        return sign * 6.0
    }

    override fun threatAdjust(ctx: HContext, enemyHeroId: String, target: Hex): Double {
        if (enemyHeroId != heroId) return 0.0
        val from = ctx.hero(heroId)?.at ?: return 0.0
        if (HexGrid.distance(from, target) != 1) return 0.0
        if (!BatchA.poolHas(ctx, heroId, PUNCH)) return 0.0
        // H threat: the punch replaces Defense with initiative. A slow block is not a block.
        return 6.0
    }

    private fun play(ctx: HContext, choice: Choice): Double {
        if (choice !is Choice.Card) return 0.0
        return when (choice.cardId) {
            in GOLD -> gold(ctx)
            in PUNCH -> punch(ctx)
            in SNARE -> snare(ctx)
            "emmitt-silver" -> silver(ctx)
            else -> 0.0
        }
    }

    // H1 / H2: gold on turn 2 or 3, into a unit that will not block Attack 4. Turn 1 and turn 4 waste the flip.
    private fun gold(ctx: HContext): Double {
        if (ctx.turn == 1 || ctx.turn >= 4) return -8.0
        val at = ctx.own()?.at ?: return 0.0
        if (!BatchA.besideEnemyUnit(ctx, at)) return -4.0
        return 10.0
    }

    // H3: in reverse time the punch is slow. Forward, it is the threat when he is already adjacent.
    private fun punch(ctx: HContext): Double {
        val at = ctx.own()?.at ?: return 0.0
        val adjacent = ctx.enemies().any { e -> e.at?.let { ctx.distance(at, it) == 1 } == true }
        if (!adjacent) return 0.0
        return if (reverse(ctx)) -6.0 else 6.0
    }

    // H10: the trap wants a hero who has already resolved a card this turn.
    private fun snare(ctx: HContext): Double {
        val at = ctx.own()?.at ?: return 0.0
        val ready = ctx.enemies().any { e ->
            val pos = e.at ?: return@any false
            ctx.distance(at, pos) in 1..3 && e.id in ctx.state.turnRecord.resolved && ctx.handCount(e.id) >= 2
        }
        return if (ready) 10.0 else -4.0
    }

    // H18: hold the teleport while time is still forward and he is not the one about to be hit.
    private fun silver(ctx: HContext): Double = if (reverse(ctx)) 6.0 else -4.0

    // H6: the enemy who picks the Glitch should be the one who has already acted.
    private fun unit(ctx: HContext, d: Decision, choice: Choice): Double {
        if (d.sourceCardId != "emmitt-silver" || choice !is Choice.Unit) return 0.0
        return if (choice.unitId in ctx.state.turnRecord.resolved) 5.0 else 0.0
    }

    private fun reverse(ctx: HContext): Boolean =
        ctx.state.effects.any { e ->
            e.owner == heroId && e.cardId in GOLD && ctx.game.inForce(e)
        }

}

private val GOLD = setOf("emmitt-gold", "emmitt-handicap")
private val PUNCH = setOf("emmitt-red-1", "emmitt-red-2a", "emmitt-red-3a")
private val SNARE = setOf("emmitt-blue-1", "emmitt-blue-2a", "emmitt-blue-3a")
