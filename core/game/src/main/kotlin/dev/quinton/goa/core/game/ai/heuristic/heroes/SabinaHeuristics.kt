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
 * Sabina wins the lane by standing with friendly minions. Without them every skill is a walk
 * (Cryomancy, BGG 3461122). The a-red jumps only when the target played an attack this turn.
 */
object SabinaHeuristics : HeroHeuristics {
    override val heroId: String = "sabina"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.PUSHER,
        idealDistance = 2,
        aggression = 0.50,
        laneWeight = 0.75,
        upgradeOrder = listOf(
            "sabina-red-2b",
            "sabina-red-3b",
            "sabina-green-2a",
            "sabina-green-3a",
            "sabina-blue-2a",
            "sabina-blue-3a",
            "red",
            "green",
            "blue",
        ),
        keepDefender = true,
    )

    override fun adjust(ctx: HContext, d: Decision, choice: Choice): Double = when (d.kind) {
        DecisionKind.PLAY_CARD -> play(ctx, choice)
        DecisionKind.CHOOSE_UNIT -> unit(ctx, d, choice)
        DecisionKind.DEFEND -> defend(d, choice)
        DecisionKind.KEEP_UPGRADE, DecisionKind.UPGRADE_CARD -> upgrade(ctx, choice)
        else -> 0.0
    }

    override fun evalTerms(state: GameState, team: Team, heroId: String): Double {
        if (heroId != this.heroId) return 0.0
        val at = state.hero(heroId)?.at ?: return 0.0
        val mine = state.hero(heroId)?.team
        val near = state.minions.count { minion ->
            minion.team == mine && minion.at?.let { HexGrid.distance(at, it) in 1..2 } == true
        }
        return BatchC.sign(state, heroId, team) * (near.coerceAtMost(3) * 2.0)
    }

    override fun threatAdjust(ctx: HContext, enemyHeroId: String, target: Hex): Double {
        if (enemyHeroId != heroId) return 0.0
        val at = ctx.hero(heroId)?.at ?: return 0.0
        val held = ctx.state.hero(heroId) ?: return 0.0
        if (ctx.distance(at, target) > 3) return 0.0
        val standing = ctx.state.heroes.any { it.id != heroId && it.at == target }
        val besideMinion = ctx.state.minions.any { minion ->
            minion.team == held.team && minion.at?.let { ctx.distance(it, target) == 1 } == true
        }
        if (!standing || !besideMinion) return 0.0
        val spent = BatchC.onDashboard(held, "sabina-green-2b") && BatchC.onDashboard(held, "sabina-green-3b")
        if (spent) return 0.0
        val empty = ctx.state.heroes.any { it.at == target && it.hand.isEmpty() && it.id != heroId }
        // Covering Fire kills an empty hand on her minion. Close Support is the discard.
        return if (empty && !BatchC.onDashboard(held, "sabina-green-3b")) 6.0 else 2.5
    }

    private fun play(ctx: HContext, choice: Choice): Double {
        val id = BatchC.cardId(choice) ?: return 0.0
        return needsMinions(ctx, id) + goldPush(ctx, id) + covering(ctx, id)
    }

    private fun needsMinions(ctx: HContext, id: String): Double {
        if (id !in minionCards) return 0.0
        val at = ctx.own()?.at ?: return 0.0
        // H15: silver, the greens and the blues do not start with no friendly minion in radius.
        return if (BatchC.minionsWithin(ctx, at, 2, friendly = true).isEmpty()) -5.0 else 0.0
    }

    private fun goldPush(ctx: HContext, id: String): Double {
        if (id != "sabina-gold" && id != "sabina-handicap") return 0.0
        val at = ctx.own()?.at ?: return 0.0
        val adjacent = BatchC.enemiesWithin(ctx, at, 1).isNotEmpty() ||
            BatchC.minionsWithin(ctx, at, 1, friendly = false).isNotEmpty()
        // H4: gold is the init-12 push, and the push only happens while they are adjacent.
        return if (adjacent) 4.0 else -2.0
    }

    private fun covering(ctx: HContext, id: String): Double {
        if (id != "sabina-green-2b" && id != "sabina-green-3b") return 0.0
        val at = ctx.own()?.at ?: return 0.0
        val ready = BatchC.enemiesWithin(ctx, at, 3).any { enemy ->
            val pos = enemy.at ?: return@any false
            ctx.state.minions.any { minion ->
                minion.team == ctx.team && minion.at?.let { ctx.distance(it, pos) == 1 } == true
            }
        }
        if (!ready) return -3.0
        // H7: the discard (or the kill, on Covering Fire) needs them already on a friendly minion.
        return if (id == "sabina-green-3b") 5.0 else 3.0
    }

    private fun unit(ctx: HContext, d: Decision, choice: Choice): Double {
        val src = d.sourceCardId ?: return 0.0
        if (src != "sabina-red-1" && src != "sabina-red-2a" && src != "sabina-red-3a") return 0.0
        val id = BatchC.unitId(choice) ?: return 0.0
        val attacked = id in ctx.state.turnRecord.playedAttack
        // H1: the +3 / +4 is only for a hero who played an attack this turn. The engine remembers it.
        return if (attacked) 6.0 else if (ctx.state.hero(id) != null) -2.0 else 0.0
    }

    private fun defend(d: Decision, choice: Choice): Double {
        val id = BatchC.cardId(choice) ?: return 0.0
        val red = id.startsWith("sabina-red-")
        if (!red || !BatchC.meets(d, id)) return 0.0
        val cheaper = d.options.filterIsInstance<Choice.Card>().any { card ->
            card.cardId != id && BatchC.meets(d, card.cardId)
        }
        // H12: red is the attack and the block. A smaller card that still holds keeps it.
        return if (cheaper) -6.0 else 1.0
    }

    private fun upgrade(ctx: HContext, choice: Choice): Double {
        val id = BatchC.cardId(choice) ?: return 0.0
        val behind = ctx.lanes().any { ctx.laneMargin(it) < 0 }
        val branchB = id == "sabina-red-2b" || id == "sabina-red-3b"
        val branchA = id == "sabina-red-2a" || id == "sabina-red-3a"
        // H14: behind on the lane, delete minions. Ahead, threaten the attack cards.
        return when {
            behind && branchB -> 2.0
            behind && branchA -> -1.5
            !behind && branchA -> 2.0
            !behind && branchB -> -1.0
            else -> 0.0
        }
    }

    private val minionCards: Set<String> = setOf(
            "sabina-silver",
            "sabina-green-1",
            "sabina-green-2a",
            "sabina-green-2b",
            "sabina-green-3a",
            "sabina-green-3b",
            "sabina-blue-1",
            "sabina-blue-2a",
            "sabina-blue-2b",
            "sabina-blue-3a",
            "sabina-blue-3b",
        )
}
