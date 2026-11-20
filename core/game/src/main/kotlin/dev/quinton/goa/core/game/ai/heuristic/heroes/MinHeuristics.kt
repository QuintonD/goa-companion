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
 * Min hits in melee, then is gone. Gold copies a red only after that red is faceup
 * (resolved or discarded). The smoke bomb is the line that makes the green block real.
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
        DecisionKind.DEFEND -> defend(ctx, d, choice)
        else -> 0.0
    }

    override fun evalTerms(state: GameState, team: Team, heroId: String): Double {
        if (heroId != this.heroId) return 0.0
        val sign = BatchB.sign(state, team, heroId)
        val smoke = BatchB.tokens(state, heroId, MinHero.SMOKE)
        val grenades = BatchB.tokens(state, heroId, MinHero.GRENADE)
        var value = 0.0
        val minAt = state.hero(heroId)?.at
        val teamOf = state.hero(heroId)?.team
        if (minAt != null && teamOf != null && smoke.any { bomb ->
                state.heroes.any { enemy ->
                    enemy.team != teamOf && enemy.at != null && strictlyBetween(bomb.at!!, enemy.at!!, minAt)
                }
            }
        ) {
            value += 6.0
        }
        value += grenades.size * 4.0
        return sign * value
    }

    override fun threatAdjust(ctx: HContext, enemyHeroId: String, target: Hex): Double {
        if (enemyHeroId != heroId) return 0.0
        var threat = 0.0
        if (BatchB.besideToken(ctx.state, target, MinHero.GRENADE)) threat += 6.0
        val from = ctx.hero(heroId)?.at
        val smoke = BatchB.tokens(ctx.state, heroId, MinHero.SMOKE).firstOrNull()?.at
        if (from != null && smoke != null && strictlyBetween(smoke, from, target)) {
            // A shot through the bomb is not a shot. Cancel the printed threat with a negative.
            threat -= 8.0
        }
        return threat
    }

    private fun play(ctx: HContext, choice: Choice): Double {
        val id = BatchB.cardId(choice) ?: return 0.0
        return smoke(ctx, id) + gold(ctx, id) + grenade(ctx, id) + red(ctx, id)
    }

    // H1: place the bomb when it is not already on a line he cares about.
    private fun smoke(ctx: HContext, id: String): Double {
        if (id != "min-silver") return 0.0
        val at = ctx.own()?.at ?: return 0.0
        val placed = BatchB.tokens(ctx.state, heroId, MinHero.SMOKE)
        if (placed.isEmpty()) return 8.0
        val covering = placed.any { bomb ->
            ctx.enemies().any { enemy -> enemy.at?.let { strictlyBetween(bomb.at!!, it, at) } == true }
        }
        return if (covering) -2.0 else 6.0
    }

    // H6: gold is attack 2 until the red is faceup. Do not lead with it.
    private fun gold(ctx: HContext, id: String): Double {
        if (id != "min-gold") return 0.0
        return if (redFaceup(ctx)) 8.0 else -10.0
    }

    // H8: the grenade is a turn-1-to-3 tag, and a turn-4 defeat when the hero is empty.
    private fun grenade(ctx: HContext, id: String): Double {
        if (id != "min-blue-2b" && id != "min-blue-3b") return 0.0
        if (ctx.turn == 4) return 8.0
        return if (ctx.turn <= 3) 5.0 else -4.0
    }

    // H3: the slow red is the card that still threatens after a faster hero has walked.
    private fun red(ctx: HContext, id: String): Double {
        if (!id.startsWith("min-red")) return 0.0
        val at = ctx.own()?.at ?: return 0.0
        return if (BatchB.enemyHeroWithin(ctx, at, 1) || BatchB.enemyMinionWithin(ctx, at, 1)) 6.0 else 0.0
    }

    // H7: the green blocks only when the bomb is in that card's range. Otherwise it is not a defense.
    private fun defend(ctx: HContext, d: Decision, choice: Choice): Double {
        val id = BatchB.cardId(choice) ?: return 0.0
        if (id != "min-green-1" && id != "min-green-2a" && id != "min-green-3a") return 0.0
        val at = ctx.own()?.at ?: return 0.0
        val range = ctx.card(id).range.coerceAtLeast(3)
        val bomb = BatchB.tokens(ctx.state, heroId, MinHero.SMOKE).firstOrNull()?.at
        val inRange = bomb != null && ctx.distance(at, bomb) <= range
        val blocks = d.context.blockPreview[id] == true || inRange
        return if (blocks) 6.0 else -12.0
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
        return mid in line && mid != to
    }
}
