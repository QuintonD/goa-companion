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
import dev.quinton.goa.core.game.engine.MoveSpec
import dev.quinton.goa.core.game.engine.isEmpty
import dev.quinton.goa.core.game.engine.reach
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.match.Team

/**
 * Cutter: a pusher. The plank shoves across a zone border. The grapple is how she arrives.
 * She does not spend the plank as a one-step walk.
 */
object CutterHeuristics : HeroHeuristics {
    override val heroId: String = "cutter"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.PUSHER,
        idealDistance = 2,
        aggression = 0.65,
        laneWeight = 0.60,
        upgradeOrder = listOf("blue", "red", "green"),
        keepDefender = true,
    )

    override fun adjust(ctx: HContext, d: Decision, choice: Choice): Double = when (d.kind) {
        DecisionKind.PLAY_CARD -> play(ctx, choice)
        DecisionKind.CHOOSE_OPTION -> option(ctx, d, choice)
        else -> 0.0
    }

    override fun evalTerms(state: GameState, team: Team, heroId: String): Double {
        if (heroId != this.heroId) return 0.0
        val me = state.hero(heroId) ?: return 0.0
        if (me.team != team) return 0.0
        if (!BatchA.ownHandHas(state, team, heroId, WIN)) return 0.0
        val extra = me.coins - 8
        if (extra <= 0) return 0.0
        // H eval: coins toward 13 matter only while X Marks the Spot can ask for three.
        return extra * 4.0
    }

    override fun threatAdjust(ctx: HContext, enemyHeroId: String, target: Hex): Double {
        if (enemyHeroId != heroId) return 0.0
        val from = ctx.hero(heroId)?.at ?: return 0.0
        if (!BatchA.poolHas(ctx, heroId, PLANK)) return 0.0
        if (HexGrid.distance(from, target) != 1) return 0.0
        return if (zonePush(ctx, from, target, 4)) 8.0 else 0.0
    }

    private fun play(ctx: HContext, choice: Choice): Double {
        if (choice !is Choice.Card) return 0.0
        return when (choice.cardId) {
            in PLANK -> plank(ctx)
            "cutter-silver" -> silver(ctx)
            in DASH -> dash(ctx, choice.cardId)
            in LUNGE -> lunge(ctx, choice.cardId)
            else -> 0.0
        }
    }

    // H2: do not open on the plank. It needs a border, and a later turn still has it.
    private fun plank(ctx: HContext): Double {
        val at = ctx.own()?.at ?: return 0.0
        val push = ctx.enemies().any { e ->
            val pos = e.at ?: return@any false
            ctx.distance(at, pos) == 1 && zonePush(ctx, at, pos, 4)
        }
        if (push && ctx.turn >= 3) return 8.0
        if (ctx.turn == 1) return -6.0
        return 0.0
    }

    // H4: the grapple on turn 1 or 2 puts Defense 2 out of the last hand and moves her.
    private fun silver(ctx: HContext): Double = when (ctx.turn) {
        1, 2 -> 4.0
        4 -> -6.0
        else -> 0.0
    }

    // H6: the dash, ignoring obstacles, ends next to an enemy hero. A hero already adjacent is not enough.
    private fun dash(ctx: HContext, cardId: String): Double {
        val max = DASH_MAX.getValue(cardId)
        val lands = BatchA.straightLandingsIgnoring(ctx, heroId, 3, max) { BatchA.besideEnemyHero(ctx, it) }
        if (lands.isEmpty()) return -4.0
        return if (ctx.turn in 2..3) 8.0 else 3.0
    }

    // H5: the lunge only when some legal step leaves an enemy hero in the next hex. Otherwise it fizzles.
    private fun lunge(ctx: HContext, cardId: String): Double {
        val max = LUNGE_MAX.getValue(cardId)
        return if (lungeConnects(ctx, max)) 8.0 else -6.0
    }

    // H1: the push bullet when a distance crosses a zone. Index 0 is the push; index 1 defeats the minion.
    private fun option(ctx: HContext, d: Decision, choice: Choice): Double {
        if (d.sourceCardId !in PLANK || choice !is Choice.Option) return 0.0
        val at = ctx.own()?.at ?: return 0.0
        val push = ctx.enemies().any { e ->
            val pos = e.at ?: return@any false
            ctx.distance(at, pos) == 1 && zonePush(ctx, at, pos, if (d.sourceCardId == "cutter-handicap") 3 else 4)
        }
        return when {
            choice.index == 0 && push -> 10.0
            choice.index == 1 && !push -> 8.0
            choice.index == 0 -> -4.0
            else -> 0.0
        }
    }

    private fun lungeConnects(ctx: HContext, max: Int): Boolean {
        val from = ctx.own()?.at ?: return false
        val lands = ctx.game.reach(MoveSpec(heroId, 1, max, straightLine = true))
        for (land in lands.keys) {
            val dir = HexGrid.straightLineDirection(from, land) ?: continue
            val step = HexGrid.DIRECTIONS[dir]
            val front = Hex(land.q + step.q, land.r + step.r)
            if (ctx.enemies().any { it.at == front }) return true
        }
        return false
    }

    private fun zonePush(ctx: HContext, from: Hex, heroAt: Hex, max: Int): Boolean {
        val dir = HexGrid.straightLineDirection(from, heroAt) ?: return false
        val step = HexGrid.DIRECTIONS[dir]
        val start = ctx.zoneOf(heroAt)
        for (i in 1..max) {
            val hex = Hex(heroAt.q + step.q * i, heroAt.r + step.r * i)
            if (!ctx.game.geo.isSpace(hex)) return false
            val zone = ctx.zoneOf(hex)
            if (zone != null && zone != start) return true
            if (!ctx.game.isEmpty(hex)) return false
        }
        return false
    }

}

private val PLANK = setOf("cutter-gold", "cutter-handicap")
private val DASH = setOf("cutter-green-1", "cutter-green-2a", "cutter-green-3a")
private val LUNGE = setOf("cutter-red-1", "cutter-red-2a", "cutter-red-3a")
private val WIN = setOf("cutter-blue-3b")
private val DASH_MAX = mapOf("cutter-green-1" to 3, "cutter-green-2a" to 4, "cutter-green-3a" to 5)
private val LUNGE_MAX = mapOf("cutter-red-1" to 1, "cutter-red-2a" to 2, "cutter-red-3a" to 3)
