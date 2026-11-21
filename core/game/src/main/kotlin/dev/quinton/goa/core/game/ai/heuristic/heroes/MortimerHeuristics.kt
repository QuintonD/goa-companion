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
import dev.quinton.goa.core.game.heroes.MortimerHero
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.match.Team

/**
 * Mortimer's body is slow. The herd does the work: silver raises it, gold spends only the
 * zombies the kill needs, and green B replaces a minion that already has two beside it.
 */
object MortimerHeuristics : HeroHeuristics {
    override val heroId: String = "mortimer"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.PUSHER,
        idealDistance = 2,
        aggression = 0.48,
        laneWeight = 0.65,
        upgradeOrder = listOf("mortimer-blue-2a", "mortimer-red-2a", "mortimer-green-2b", "blue", "red", "green"),
        keepDefender = true,
    )

    override fun adjust(ctx: HContext, d: Decision, choice: Choice): Double = when (d.kind) {
        DecisionKind.PLAY_CARD -> play(ctx, choice)
        DecisionKind.CHOOSE_SPACE -> avoidSpawn(ctx, d, choice)
        else -> 0.0
    }

    override fun evalTerms(state: GameState, team: Team, heroId: String): Double {
        if (heroId != this.heroId) return 0.0
        val sign = BatchB.sign(state, team, heroId)
        val zombies = BatchB.tokens(state, heroId, MortimerHero.ZOMBIE)
        // evalTerms: HEval already counts each zombie as a lasting token; these are the herd's jobs.
        var value = 0.0
        val hero = state.hero(heroId)?.at
        if (hero != null) {
            val packed = zombies.count { zombie ->
                state.heroes.any { enemy ->
                    enemy.team != state.hero(heroId)?.team &&
                        enemy.at?.let { HexGrid.distance(it, zombie.at!!) == 1 } == true &&
                        HexGrid.distance(hero, enemy.at!!) <= 4
                }
            }
            value += packed * 1.5
        }
        return sign * value
    }

    override fun threatAdjust(ctx: HContext, enemyHeroId: String, target: Hex): Double {
        if (enemyHeroId != heroId) return 0.0
        val from = ctx.hero(heroId)?.at ?: return 0.0
        if (ctx.distance(from, target) > 4) return 0.0
        val beside = BatchB.tokens(ctx.state, heroId, MortimerHero.ZOMBIE).any { zombie ->
            HexGrid.distance(zombie.at!!, target) == 1
        }
        // threatAdjust: the red's discard reaches a hero next to a zombie in radius 4, not next to him.
        return if (beside) 2.5 * BatchB.hold(ctx, heroId) { it.id in DISCARD_REDS } else 0.0
    }

    private fun play(ctx: HContext, choice: Choice): Double {
        val id = BatchB.cardId(choice) ?: return 0.0
        return awaken(ctx, id) + knife(ctx, id) + replace(ctx, id) + whiff(ctx, id)
    }

    // H1: raise the herd while it is under two, and not instead of a minion that is already adjacent.
    private fun awaken(ctx: HContext, id: String): Double {
        if (id != "mortimer-silver") return 0.0
        val at = ctx.own()?.at ?: return 0.0
        val herd = BatchB.tokens(ctx.state, heroId, MortimerHero.ZOMBIE).size
        if (herd >= 2) return 0.0
        if (BatchB.enemyMinionWithin(ctx, at, 1)) return -2.0
        return if (herd == 0) 5.0 else 2.5
    }

    // H3: gold is attack 4 until a zombie is already on the target. Do not spend the herd on a whiff.
    private fun knife(ctx: HContext, id: String): Double {
        if (id != "mortimer-gold") return 0.0
        val at = ctx.own()?.at ?: return 0.0
        val ready = ctx.state.minions.any { minion ->
            val pos = minion.at ?: return@any false
            minion.team != ctx.team && ctx.distance(at, pos) == 1 && zombiesBeside(ctx, pos) > 0
        } || ctx.enemies().any { enemy ->
            val pos = enemy.at ?: return@any false
            ctx.distance(at, pos) == 1 && zombiesBeside(ctx, pos) > 0
        }
        return if (ready) 3.0 else 0.0
    }

    // H6: two zombies on a minion in range is the replace. It pushes without paying coins.
    private fun replace(ctx: HContext, id: String): Double {
        if (id != "mortimer-green-2b" && id != "mortimer-green-3b") return 0.0
        return if (packedMinion(ctx)) 10.0 else -1.0
    }

    // H12: an attack whose only target can still walk is a wasted red.
    private fun whiff(ctx: HContext, id: String): Double {
        if (!id.startsWith("mortimer-red") && id != "mortimer-gold") return 0.0
        val at = ctx.own()?.at ?: return 0.0
        val adjacent = BatchB.enemyMinionWithin(ctx, at, 1) || BatchB.enemyHeroWithin(ctx, at, 1)
        return if (adjacent) 0.0 else -2.0
    }

    // H1 placement: a spawn the wave will use deletes the zombie. Prefer any other empty space.
    private fun avoidSpawn(ctx: HContext, d: Decision, choice: Choice): Double {
        if (d.sourceCardId != "mortimer-silver") return 0.0
        val hex = (choice as? Choice.Space)?.hex ?: return 0.0
        val spawn = ctx.game.geo.spaces[hex]?.spawn != null
        return if (spawn) -2.0 else 0.0
    }

    private val DISCARD_REDS = setOf("mortimer-red-1", "mortimer-red-2a", "mortimer-red-3a")

    private fun zombiesBeside(ctx: HContext, at: Hex): Int =
        BatchB.tokens(ctx.state, heroId, MortimerHero.ZOMBIE).count { zombie ->
            HexGrid.distance(zombie.at!!, at) == 1
        }

    private fun packedMinion(ctx: HContext): Boolean {
        val at = ctx.own()?.at ?: return false
        return ctx.state.minions.any { minion ->
            val pos = minion.at ?: return@any false
            minion.team != ctx.team && ctx.distance(at, pos) <= 4 && zombiesBeside(ctx, pos) >= 2
        }
    }
}
