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
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.match.Team

/**
 * Garrus: a fighter who holds the middle. One card in the discard turns the gold into a
 * second hit and lets the hunt step. Howl is the cage, not a discard bought for its own sake.
 */
object GarrusHeuristics : HeroHeuristics {
    override val heroId: String = "garrus"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.FIGHTER,
        idealDistance = 1,
        aggression = 0.62,
        laneWeight = 0.42,
        upgradeOrder = listOf("red", "blue", "green"),
        keepDefender = true,
    )

    override fun adjust(ctx: HContext, d: Decision, choice: Choice): Double = when (d.kind) {
        DecisionKind.PLAY_CARD -> play(ctx, choice)
        DecisionKind.CHOOSE_OPTION -> option(ctx, d, choice)
        DecisionKind.DEFEND -> defend(choice)
        else -> 0.0
    }

    override fun evalTerms(state: GameState, team: Team, heroId: String): Double {
        if (heroId != this.heroId) return 0.0
        val me = state.hero(heroId) ?: return 0.0
        val sign = BatchA.sign(state, team, heroId)
        var v = 0.0
        val discards = me.cards.count { it.zone == Zone.DISCARD }
        // H eval: the first discard is public and unlocks the step. Further cards are only +1 Attack.
        if (discards > 0) v += 3.0 + (discards - 1)
        if (BatchA.effectLive(state, heroId, HOWL)) {
            val at = me.at
            if (at != null) {
                val caged = state.heroes.count { e ->
                    e.team != me.team && e.at?.let { HexGrid.distance(at, it) in 1..3 } == true
                }
                v += caged * 4.0
            }
        }
        return sign * v
    }

    override fun threatAdjust(ctx: HContext, enemyHeroId: String, target: Hex): Double {
        if (enemyHeroId != heroId) return 0.0
        val from = ctx.hero(heroId)?.at ?: return 0.0
        val distance = HexGrid.distance(from, target)
        var v = 0.0
        if (distance in 1..3 && BatchA.poolHas(ctx, heroId, HOWL)) {
            v += if (BatchA.effectLive(ctx, heroId, HOWL)) 6.0 else 3.0
        }
        // H threat: with a discard, the hunt reaches 1, then 2, then 3 past the adjacent hex.
        if (BatchA.discardCount(ctx, heroId) > 0 && BatchA.poolHas(ctx, heroId, HUNT)) {
            val step = huntStep(ctx)
            if (distance in 1..step) v += 6.0
        }
        return v
    }

    private fun play(ctx: HContext, choice: Choice): Double {
        if (choice !is Choice.Card) return 0.0
        return when (choice.cardId) {
            in HOWL -> howl(ctx)
            in GOLD -> gold(ctx)
            in HUNT -> hunt(ctx)
            in PILUM -> pilum(ctx)
            else -> 0.0
        }
    }

    // H1 / H2: Howl on turn 1 or 2 when an enemy is inside the cage. Nobody in radius: do not buy a discard.
    private fun howl(ctx: HContext): Double {
        val at = ctx.own()?.at ?: return 0.0
        val radius = 3 + ctx.itemBonus(heroId, dev.quinton.goa.core.model.catalog.Stat.RADIUS)
        val near = BatchA.enemiesWithin(ctx, at, radius)
        if (near.isEmpty()) return -8.0
        if (ctx.turn >= 4) return -6.0
        return if (ctx.turn <= 2) 10.0 else 4.0
    }

    // H4: gold is the second swing. At 0 discards it is Attack 4, and turn 1 should not spend it.
    private fun gold(ctx: HContext): Double {
        val discards = BatchA.discardCount(ctx, heroId)
        val at = ctx.own()?.at ?: return 0.0
        if (discards == 0 && ctx.turn == 1) return -6.0
        if (discards >= 1 && BatchA.besideEnemyUnit(ctx, at)) return 4.0 + discards
        return 0.0
    }

    // H5: the step bullet exists only with a discard. Without one, the adjacent bullet is the card.
    private fun hunt(ctx: HContext): Double {
        if (BatchA.discardCount(ctx, heroId) == 0) return 0.0
        val at = ctx.own()?.at ?: return 0.0
        val step = huntStep(ctx)
        val hero = ctx.enemies().any { e -> e.at?.let { ctx.distance(at, it) in 1..step } == true }
        return if (hero) 8.0 else 0.0
    }

    // H10: the pilum is a discard on a hero in range 2. No hero, the engine does not offer the step.
    private fun pilum(ctx: HContext): Double {
        val at = ctx.own()?.at ?: return 0.0
        val victim = ctx.enemies().filter { e -> e.at?.let { ctx.distance(at, it) in 1..2 } == true }
        if (victim.isEmpty()) return -6.0
        val fewest = victim.minOf { ctx.handCount(it.id) }
        return when (fewest) {
            0 -> -4.0
            1 -> 12.0
            else -> 4.0
        }
    }

    // H5: index 0 is the hunt step, offered only when a discard lets him move. Take it toward a hero.
    private fun option(ctx: HContext, d: Decision, choice: Choice): Double {
        if (d.sourceCardId !in HUNT || choice !is Choice.Option) return 0.0
        if (BatchA.discardCount(ctx, heroId) == 0) return if (choice.index == 1) 4.0 else -6.0
        return if (choice.index == 0) 8.0 else 0.0
    }

    // H13: blue and alt red are the blocks. Gold and silver are Defense 3.
    private fun defend(choice: Choice): Double {
        if (choice !is Choice.Card) return 0.0
        return if (choice.cardId in GOLD || choice.cardId in HOWL) -4.0 else 0.0
    }

    private fun huntStep(ctx: HContext): Int {
        val id = ctx.possibleCards(heroId).firstOrNull { it.card.id in HUNT }?.card?.id
        return when (id) {
            "garrus-red-3a" -> 4
            "garrus-red-2a" -> 3
            else -> 2
        }
    }

}

private val HOWL = setOf("garrus-silver")
private val GOLD = setOf("garrus-gold", "garrus-handicap")
private val HUNT = setOf("garrus-red-1", "garrus-red-2a", "garrus-red-3a")
private val PILUM = setOf("garrus-green-2b", "garrus-green-3b")
