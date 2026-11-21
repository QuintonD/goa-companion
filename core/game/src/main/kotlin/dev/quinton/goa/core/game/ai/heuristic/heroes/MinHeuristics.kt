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
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.game.heroes.MinHero
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.match.Team

/**
 * Min hits in melee, then is gone. Gold copies a red's After-the-attack text only once that red is
 * faceup (resolved or discarded). The green blocks only by swapping with the smoke bomb in range
 * (rulings/min.md 5), and the grenade lands at end of turn on whoever is still beside it.
 */
object MinHeuristics : HeroHeuristics {
    override val heroId: String = "min"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.FIGHTER,
        idealDistance = 1,
        aggression = 0.65,
        laneWeight = 0.45,
        upgradeOrder = listOf("min-red-2a", "min-green-2a", "min-blue-2b", "red", "green", "blue"),
        keepDefender = true,
    )

    override fun adjust(ctx: HContext, d: Decision, choice: Choice): Double = when (d.kind) {
        DecisionKind.PLAY_CARD -> play(ctx, choice)
        DecisionKind.DEFEND -> defend(d, choice)
        DecisionKind.CHOOSE_SPACE -> if (d.sourceCardId in GRENADES) grenadeSpace(ctx, choice) else 0.0
        else -> 0.0
    }

    override fun evalTerms(state: GameState, team: Team, heroId: String): Double {
        if (heroId != this.heroId) return 0.0
        val min = state.hero(heroId) ?: return 0.0
        val minAt = min.at ?: return 0.0
        var value = 0.0
        // evalTerms: the smoke bomb on a straight line between an enemy hero and Min stops that shot.
        val bomb = BatchBSupport.tokens(state, heroId, MinHero.SMOKE).firstOrNull()?.at
        if (bomb != null) {
            val shielded = state.heroes.any { e ->
                e.team != min.team && e.at?.let { strictlyBetween(bomb, it, minAt) } == true
            }
            if (shielded) value += 2.5
        }
        // evalTerms: a grenade with enemy heroes beside it discards or defeats them at end of turn.
        for (g in BatchBSupport.tokens(state, heroId, MinHero.GRENADE)) {
            val at = g.at ?: continue
            value +=
                2.0 *
                state.heroes.count { e ->
                    e.team != min.team && e.at?.let { HexGrid.distance(it, at) == 1 } == true
                }
        }
        return BatchBSupport.sign(state, team, heroId) * value
    }

    override fun threatAdjust(ctx: HContext, enemyHeroId: String, target: Hex): Double {
        if (enemyHeroId != heroId) return 0.0
        // threatAdjust: the space beside his grenade is a discard (or a defeat) at end of turn.
        return if (BatchBSupport.besideToken(ctx.state, target, MinHero.GRENADE)) 3.0 else 0.0
    }

    private fun play(ctx: HContext, choice: Choice): Double {
        val id = BatchBSupport.cardId(choice) ?: return 0.0
        val at = ctx.own()?.at ?: return 0.0
        return when {
            id == "min-silver" -> smoke(ctx, at)
            id == "min-gold" -> gold(ctx, at)
            id in GRENADES -> grenade(ctx, at, id)
            id == "min-green-2b" || id == "min-green-3b" -> doubled(ctx)
            else -> 0.0
        }
    }

    // H1: put the bomb out when it is not already on a line between Min and an enemy hero.
    private fun smoke(ctx: HContext, at: Hex): Double {
        if (!BatchBSupport.enemyHeroWithin(ctx, at, SMOKE_WATCH)) return 0.0
        val bomb = BatchBSupport.tokens(ctx.state, heroId, MinHero.SMOKE).firstOrNull()?.at
            ?: return 2.5
        val covering = ctx.enemies().any { enemy -> enemy.at?.let { strictlyBetween(bomb, it, at) } == true }
        return if (covering) -2.0 else 1.0
    }

    // H6: gold is attack 2 until a red is faceup; then it carries that red's push or swap.
    private fun gold(ctx: HContext, at: Hex): Double {
        if (!redFaceup(ctx)) return -1.5
        val target = BatchBSupport.enemyUnits(ctx).any { (_, pos) -> ctx.distance(at, pos) == 1 }
        return if (target) 3.0 else 0.0
    }

    // H8: the grenade needs an enemy hero next to a space in its radius; an empty hand there is a defeat.
    private fun grenade(ctx: HContext, at: Hex, id: String): Double {
        val reach = ctx.card(id).radius + 1
        val near = ctx.enemies().filter { e -> e.at?.let { ctx.distance(at, it) <= reach } == true }
        if (near.isEmpty()) return -2.0
        val empty = near.any { ctx.handCount(it.id) <= 1 && ctx.turn >= 3 }
        return if (empty) 5.0 else 2.0
    }

    // H8: place it beside heroes who have already acted (they stay), or beside one whose hand is empty.
    private fun grenadeSpace(ctx: HContext, choice: Choice): Double {
        val hex = (choice as? Choice.Space)?.hex ?: return 0.0
        var value = 0.0
        for (e in ctx.enemies()) {
            val pos = e.at ?: continue
            if (HexGrid.distance(pos, hex) != 1) continue
            val stays = e.id in ctx.state.turnRecord.resolved
            value += if (stays) 6.0 else 2.5
            if (ctx.handCount(e.id) == 0) value += ctx.killWorth(e.id) * (if (stays) 0.8 else 0.3)
        }
        return value
    }

    // H10: double the items only when there are items to double.
    private fun doubled(ctx: HContext): Double {
        val items = ctx.own()?.cards?.count { it.zone == Zone.ITEM } ?: 0
        return if (items >= 2) items * 1.0 else -2.0
    }

    // H7: the green blocks only by swapping with the bomb in range. The engine's preview says whether it can.
    private fun defend(d: Decision, choice: Choice): Double {
        val id = BatchBSupport.cardId(choice) ?: return 0.0
        if (id !in POOF) return 0.0
        return if (d.context.blockPreview[id] == false) -20.0 else 0.0
    }

    private fun redFaceup(ctx: HContext): Boolean {
        val hero = ctx.own() ?: return false
        return hero.cards.any { card ->
            card.id.startsWith("min-red") && !card.faceDown &&
                (card.zone == Zone.RESOLVED || card.zone == Zone.DISCARD)
        }
    }

    private fun strictlyBetween(mid: Hex, from: Hex, to: Hex): Boolean {
        if (mid == from || mid == to) return false
        val line = HexGrid.straightLine(from, to) ?: return false
        return mid in line
    }

    private const val SMOKE_WATCH = 6
    private val GRENADES = setOf("min-blue-2b", "min-blue-3b")
    private val POOF = setOf("min-green-1", "min-green-2a", "min-green-3a")
}
