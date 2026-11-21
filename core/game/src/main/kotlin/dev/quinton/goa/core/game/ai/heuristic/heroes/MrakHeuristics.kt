package dev.quinton.goa.core.game.ai.heuristic.heroes

import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.ai.heuristic.HContext
import dev.quinton.goa.core.game.ai.heuristic.HeroHeuristics
import dev.quinton.goa.core.game.ai.heuristic.HeroRole
import dev.quinton.goa.core.game.ai.heuristic.HeroStyle
import dev.quinton.goa.core.game.engine.GameState
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.game.heroes.MrakHero
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.catalog.Stat
import dev.quinton.goa.core.model.match.Team

/**
 * Mrak makes the ground worse, then hits. Seismic defeats a hero with no cards who is already
 * touching terrain or a rock. The attack on the same card is the stick, and it is adjacent only.
 */
object MrakHeuristics : HeroHeuristics {
    override val heroId: String = "mrak"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.TANK,
        idealDistance = 1,
        aggression = 0.35,
        laneWeight = 0.40,
        upgradeOrder = listOf("mrak-red-2a", "mrak-green-2b", "mrak-blue-2a", "red", "green", "blue"),
        keepDefender = true,
    )

    override fun adjust(ctx: HContext, d: Decision, choice: Choice): Double = when (d.kind) {
        DecisionKind.PLAY_CARD -> play(ctx, choice)
        DecisionKind.CHOOSE_ACTION -> action(ctx, d, choice)
        else -> 0.0
    }

    override fun evalTerms(state: GameState, team: Team, heroId: String): Double {
        if (heroId != this.heroId) return 0.0
        val sign = BatchB.sign(state, team, heroId)
        val rocks = BatchB.tokens(state, heroId, MrakHero.ROCK)
        var value = 0.0
        for (rock in rocks) {
            val beside = state.heroes.any { enemy ->
                enemy.team != state.hero(heroId)?.team &&
                    enemy.at?.let { HexGrid.distance(it, rock.at!!) == 1 } == true
            }
            // evalTerms: a rock beside an enemy hero turns seismic on against them.
            if (beside) value += 2.0
        }
        val carapace = state.hero(heroId)?.cards?.any { card ->
            (card.id == "mrak-red-2b" || card.id == "mrak-red-3b") &&
                card.zone == Zone.RESOLVED && !card.faceDown
        } == true
        // evalTerms: carapace on its slot covers one discard or one defense.
        if (carapace) value += 4.0
        return sign * value
    }

    override fun threatAdjust(ctx: HContext, enemyHeroId: String, target: Hex): Double {
        if (enemyHeroId != heroId) return 0.0
        val from = ctx.hero(heroId)?.at ?: return 0.0
        val distance = ctx.distance(from, target)
        val ground = BatchB.touchesTerrain(ctx, target) || BatchB.besideToken(ctx.state, target, MrakHero.ROCK)
        val red = BatchB.hold(ctx, heroId) { card ->
            card.id == "mrak-red-1" || card.id == "mrak-red-2a" || card.id == "mrak-red-3a"
        }
        if (distance > SEISMIC_REACH || !ground || red < 0.15) return 0.0
        // threatAdjust: seismic is a discard on ground next to terrain or a rock, a defeat on an empty hand.
        val empty = ctx.state.heroes.any { hero -> hero.at == target && ctx.handCount(hero.id) == 0 }
        return red * (if (empty) 8.0 else 2.5)
    }

    private fun play(ctx: HContext, choice: Choice): Double {
        val id = BatchB.cardId(choice) ?: return 0.0
        return seismic(ctx, id) + grip(ctx, id) + fissure(ctx, id) + carapace(ctx, id)
    }

    // H2: the skill defeats an empty-handed hero who is already on terrain or a rock. With no such hero
    // the skill cannot start, and the general book's printed-radius strike is not there.
    private fun seismic(ctx: HContext, id: String): Double {
        if (id != "mrak-red-1" && id != "mrak-red-2a" && id != "mrak-red-3a") return 0.0
        val at = ctx.own()?.at ?: return 0.0
        val card = ctx.card(id)
        val victims = grounded(ctx, card.radius + ctx.itemBonus(ctx.seat, Stat.RADIUS))
        if (victims.isEmpty()) return BatchB.deadStrike(ctx, at, card)
        return if (victims.any { ctx.handCount(it) == 0 }) 8.0 else 1.0
    }

    // H6: Stone Grip needs a hero in range with three empty adjacent spaces.
    private fun grip(ctx: HContext, id: String): Double {
        if (id != "mrak-silver") return 0.0
        return if (openHero(ctx)) 3.0 else -4.0
    }

    // H14: gold picks rocks back up. A wall that is already sealing someone is worth more than attack 4.
    private fun fissure(ctx: HContext, id: String): Double {
        if (id != "mrak-gold") return 0.0
        val rocks = BatchB.tokens(ctx.state, heroId, MrakHero.ROCK).size
        val sealing = rocks >= 3 && grounded(ctx, SEISMIC_REACH).isNotEmpty()
        return if (sealing) -4.0 else 0.0
    }

    // H7: the carapace is the walk on turn 1 or 2, then a defense that sits on the slot.
    private fun carapace(ctx: HContext, id: String): Double {
        if (id != "mrak-red-2b" && id != "mrak-red-3b") return 0.0
        val at = ctx.own()?.at ?: return 0.0
        val far = !ctx.inBattleZone(at)
        return if (far && ctx.turn <= 2) 3.0 else 0.0
    }

    // H3: adjacent, and they still have a card, the attack kills and the skill only discards.
    private fun action(ctx: HContext, d: Decision, choice: Choice): Double {
        val source = d.sourceCardId.orEmpty()
        val seismic = source == "mrak-red-1" || source == "mrak-red-2a" || source == "mrak-red-3a"
        val act = (choice as? Choice.Action)?.action
        val at = ctx.own()?.at
        if (!seismic || act == null || at == null) return 0.0
        val radius = ctx.card(source).radius + ctx.itemBonus(ctx.seat, Stat.RADIUS)
        val defeat = grounded(ctx, radius).any { ctx.handCount(it) == 0 }
        val adjacent = ctx.enemies().any { enemy ->
            enemy.at?.let { ctx.distance(at, it) == 1 } == true && ctx.handCount(enemy.id) > 0
        }
        return when {
            act == ActionType.PRIMARY && defeat -> 8.0
            act == ActionType.ATTACK && adjacent && !defeat -> 2.0
            else -> 0.0
        }
    }

    /** Enemy heroes in [radius] standing next to terrain or one of his rocks (the seismic condition). */
    private fun grounded(ctx: HContext, radius: Int): List<String> {
        val at = ctx.own()?.at ?: return emptyList()
        return ctx.enemies().filter { enemy ->
            val pos = enemy.at ?: return@filter false
            ctx.distance(at, pos) in 1..radius &&
                (BatchB.touchesTerrain(ctx, pos) || BatchB.besideToken(ctx.state, pos, MrakHero.ROCK))
        }.map { it.id }
    }

    private const val SEISMIC_REACH = 4

    private fun openHero(ctx: HContext): Boolean {
        val at = ctx.own()?.at ?: return false
        return ctx.enemies().any { enemy ->
            val pos = enemy.at ?: return@any false
            ctx.distance(at, pos) <= 3 && emptyAround(ctx, pos) >= 3
        }
    }

    private fun emptyAround(ctx: HContext, at: Hex): Int =
        HexGrid.neighbors(at).count { hex -> ctx.game.geo.isSpace(hex) && occupant(ctx, hex) == null }

    private fun occupant(ctx: HContext, hex: Hex): String? {
        ctx.state.heroes.firstOrNull { it.at == hex }?.let { return it.id }
        ctx.state.minions.firstOrNull { it.at == hex }?.let { return it.id }
        ctx.state.pieces.firstOrNull { it.at == hex && !it.passable }?.let { return it.id }
        return null
    }
}
