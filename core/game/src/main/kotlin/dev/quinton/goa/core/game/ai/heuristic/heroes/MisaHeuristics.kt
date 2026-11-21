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
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.game.engine.isEmpty
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
        idealDistance = 1,
        aggression = 0.55,
        laneWeight = 0.85,
        upgradeOrder = listOf("misa-red-2a", "misa-blue-2a", "misa-green-2a", "red", "blue", "green"),
        keepDefender = true,
    )

    override fun adjust(ctx: HContext, d: Decision, choice: Choice): Double = when (d.kind) {
        DecisionKind.PLAY_CARD -> play(ctx, choice)
        DecisionKind.DEFEND -> defend(ctx, d, choice)
        else -> 0.0
    }

    override fun evalTerms(state: GameState, team: Team, heroId: String): Double {
        if (heroId != this.heroId) return 0.0
        // evalTerms: a Focus / Discipline / Mastery step pending for the coming turn.
        val step = state.effects.any { effect ->
            effect.owner == heroId && effect.cardId in FOCUS && effect.timing == Timing.NEXT_TURN && !effect.spent
        }
        return if (step) BatchBSupport.sign(state, team, heroId) * 2.0 else 0.0
    }

    private fun play(ctx: HContext, choice: Choice): Double {
        val id = BatchBSupport.cardId(choice) ?: return 0.0
        val at = ctx.own()?.at ?: return 0.0
        return when {
            id == "misa-gold" -> gold(ctx, at)
            id == "misa-silver" -> swoop(ctx, at)
            id in SWORD -> sword(ctx, at)
            id in FOCUS -> focus(ctx, at)
            else -> 0.0
        }
    }

    // H5: gold defeats an adjacent enemy minion outright (no attack), or places her at exact range 4.
    private fun gold(ctx: HContext, at: Hex): Double {
        val minion = BatchBSupport.bestMinion(ctx, at, 1)
        // The card look resolves the minion bullet itself.
        if (minion > 0.0) return if (ctx.patches) minion * 0.7 else 0.0
        return if (!ctx.inBattleZone(at)) 3.0 else 0.0
    }

    // H1/H2: the sword is her one attack. With nothing adjacent it is a walk and a defense 6-7 kept for later.
    private fun sword(ctx: HContext, at: Hex): Double {
        val adjacent = BatchBSupport.enemyUnits(ctx).any { (_, pos) -> ctx.distance(at, pos) == 1 }
        return if (adjacent) 0.0 else -1.5
    }

    // H3: Focus on turn 4 has no next turn. On turns 1-3 it is the step the sword does not have.
    private fun focus(ctx: HContext, at: Hex): Double {
        if (ctx.turn >= LAST_TURN) return -6.0
        val adjacent = BatchBSupport.enemyUnits(ctx).any { (_, pos) -> ctx.distance(at, pos) == 1 }
        val sword = ctx.ownHand().any { it.id in SWORD }
        return if (!adjacent && sword) 2.0 else 0.0
    }

    // H6: Swoop needs a space in radius 3 next to two enemy units, or the primary does not start.
    private fun swoop(ctx: HContext, at: Hex): Double {
        if (!swoopSpace(ctx, at)) return -3.0
        val discard = ctx.own()?.cards?.any { it.zone == Zone.DISCARD } == true
        return if (discard) 4.0 else 1.5
    }

    // H13: the unplayed sword is the attack she still needs. A hit another card also stops takes that card.
    private fun defend(ctx: HContext, d: Decision, choice: Choice): Double {
        val id = BatchBSupport.cardId(choice) ?: return 0.0
        if (id !in SWORD || ctx.turn >= LAST_TURN) return 0.0
        val total = d.context.attackTotal ?: return 0.0
        val other = d.options.filterIsInstance<Choice.Card>().any { o ->
            o.cardId !in SWORD && (d.context.defensePreview[o.cardId] ?: 0) >= total
        }
        return if (other) -4.0 else 0.0
    }

    private fun swoopSpace(ctx: HContext, at: Hex): Boolean {
        val enemies = BatchBSupport.enemyUnits(ctx).map {
            it.second
        }.filter { ctx.distance(at, it) <= SWOOP_RADIUS + 1 }
        if (enemies.size < 2) return false
        val candidates = enemies.flatMap { HexGrid.neighbors(it) }.toSet()
        return candidates.any { h ->
            ctx.distance(at, h) <= SWOOP_RADIUS && ctx.game.isEmpty(h) &&
                enemies.count { HexGrid.distance(it, h) == 1 } >= 2
        }
    }

    private const val LAST_TURN = 4
    private const val SWOOP_RADIUS = 3
    private val SWORD = setOf("misa-red-1", "misa-red-2a", "misa-red-3a")
    private val FOCUS = setOf("misa-green-1", "misa-green-2a", "misa-green-3a")
}
