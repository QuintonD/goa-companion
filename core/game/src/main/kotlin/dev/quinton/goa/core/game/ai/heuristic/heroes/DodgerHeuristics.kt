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
 * Dodger: a ranged finisher who shoots from beside an empty Battle Zone spawn.
 * Death Trap is the discard that makes the red and the turn-4 gold legal.
 */
object DodgerHeuristics : HeroHeuristics {
    override val heroId: String = "dodger"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.RANGED,
        idealDistance = 2,
        aggression = 0.55,
        laneWeight = 0.50,
        upgradeOrder = listOf("green", "blue", "red"),
        keepDefender = true,
    )

    override fun adjust(ctx: HContext, d: Decision, choice: Choice): Double = when (d.kind) {
        DecisionKind.PLAY_CARD -> play(ctx, choice)
        DecisionKind.CHOOSE_OPTION -> option(d, choice)
        DecisionKind.DEFEND -> defend(ctx, d, choice)
        else -> 0.0
    }

    override fun evalTerms(state: GameState, team: Team, heroId: String): Double {
        if (heroId != this.heroId) return 0.0
        val sign = BatchA.sign(state, team, heroId)
        // H eval: Tide of Darkness is public. The spawn tests stay on for the rest of the match.
        return if (BatchA.ultimateUnlocked(state, heroId)) sign * 4.0 else 0.0
    }

    override fun threatAdjust(ctx: HContext, enemyHeroId: String, target: Hex): Double {
        if (enemyHeroId != heroId) return 0.0
        val from = ctx.hero(heroId)?.at ?: return 0.0
        if (!BatchA.poolHas(ctx, heroId, setOf("dodger-silver"))) return 0.0
        if (HexGrid.distance(from, target) !in 1..4) return 0.0
        val tide = BatchA.ultimateUnlocked(ctx.state, heroId)
        return if (BatchA.besideEmptySpawn(ctx, target, tide)) 8.0 else 0.0
    }

    private fun play(ctx: HContext, choice: Choice): Double {
        if (choice !is Choice.Card) return 0.0
        return when (choice.cardId) {
            "dodger-silver" -> silver(ctx)
            in GOLD -> gold(ctx)
            in FINGER -> finger(ctx)
            else -> 0.0
        }
    }

    // H1 / H2: Death Trap only when an enemy in radius is already beside an empty Battle Zone spawn.
    private fun silver(ctx: HContext): Double {
        val at = ctx.own()?.at ?: return 0.0
        val tide = BatchA.ultimateUnlocked(ctx.state, heroId)
        val victim = ctx.enemies().filter { e -> e.at?.let { ctx.distance(at, it) in 1..4 } == true }
            .filter { e -> e.at?.let { BatchA.besideEmptySpawn(ctx, it, tide) } == true }
        if (victim.isEmpty()) return -8.0
        val fewest = victim.minOf { ctx.handCount(it.id) }
        return when (fewest) {
            0 -> 2.0
            1 -> 14.0
            2 -> 8.0
            else -> 3.0
        }
    }

    // H3: turn-4 gold finishes a hero with no cards, from beside an empty spawn. Turn 1 is a walk.
    private fun gold(ctx: HContext): Double {
        if (ctx.turn == 1) return -6.0
        val at = ctx.own()?.at ?: return 0.0
        val tide = BatchA.ultimateUnlocked(ctx.state, heroId)
        if (!BatchA.besideEmptySpawn(ctx, at, tide)) return 0.0
        val empty = ctx.enemies().any { e ->
            e.at?.let { ctx.distance(at, it) in 1..2 } == true && ctx.handCount(e.id) == 0
        }
        return if (empty && ctx.turn >= 4) 12.0 else 0.0
    }

    // H5: the ranged bullet wants a hero who has already discarded. No such hero: this is not the snipe.
    private fun finger(ctx: HContext): Double {
        val at = ctx.own()?.at ?: return 0.0
        val discarded = ctx.enemies().any { e ->
            val pos = e.at ?: return@any false
            ctx.distance(at, pos) in 1..3 && BatchA.discardCount(ctx, e.id) > 0
        }
        return if (discarded) 8.0 else 0.0
    }

    // H4: when both bullets are offered, the ranged one is index 1.
    private fun option(d: Decision, choice: Choice): Double {
        if (d.sourceCardId !in GOLD && d.sourceCardId !in FINGER) return 0.0
        if (choice !is Choice.Option) return 0.0
        return if (choice.index == 1) 4.0 else 0.0
    }

    // H7: the shield's bonus is what meets the hit only when two empty spawns sit in radius.
    private fun defend(ctx: HContext, d: Decision, choice: Choice): Double {
        if (choice !is Choice.Card || choice.cardId !in SHIELD) return 0.0
        val at = ctx.own()?.at ?: return 0.0
        val tide = BatchA.ultimateUnlocked(ctx.state, heroId)
        val holes = BatchA.emptySpawnsWithin(ctx, at, 2, tide)
        return if (holes >= 2) 6.0 else -2.0
    }

}

private val GOLD = setOf("dodger-gold", "dodger-handicap")
private val FINGER = setOf("dodger-red-1", "dodger-red-2a", "dodger-red-3a")
private val SHIELD = setOf("dodger-blue-1", "dodger-blue-2a", "dodger-blue-3a")
