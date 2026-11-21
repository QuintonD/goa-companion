package dev.quinton.goa.core.game.ai.heuristic.heroes

import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.ai.heuristic.HCard
import dev.quinton.goa.core.game.ai.heuristic.HContext
import dev.quinton.goa.core.game.ai.heuristic.HeroHeuristics
import dev.quinton.goa.core.game.ai.heuristic.HeroRole
import dev.quinton.goa.core.game.ai.heuristic.HeroStyle
import dev.quinton.goa.core.game.engine.GameState
import dev.quinton.goa.core.game.heroes.IgnatiaHero
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.catalog.Stat
import dev.quinton.goa.core.model.match.Team

/**
 * Ignatia's card is two cards. The Tie Breaker coin picks the text when she acts (the Blue team's
 * side is the blue text, the Red team's the orange one, `IgnatiaHero.coinSide`). The coin is public,
 * so the face it shows now is the prediction; a tie before her action can still flip it.
 */
object IgnatiaHeuristics : HeroHeuristics {
    override val heroId: String = "ignatia"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.RANGED,
        idealDistance = 3,
        aggression = 0.55,
        laneWeight = 0.45,
        upgradeOrder = listOf("ignatia-red-2a", "ignatia-blue-2a", "ignatia-green-2a", "red", "blue", "green"),
        keepDefender = true,
    )

    override fun adjust(ctx: HContext, d: Decision, choice: Choice): Double = when (d.kind) {
        DecisionKind.PLAY_CARD -> play(ctx, choice)
        DecisionKind.DEFEND -> defend(ctx, d, choice)
        else -> 0.0
    }

    override fun evalTerms(state: GameState, team: Team, heroId: String): Double {
        if (heroId != this.heroId) return 0.0
        val me = state.hero(heroId) ?: return 0.0
        // evalTerms: magma beside an enemy hero turns her blue discard on. HEval already counts the token itself.
        val magma = BatchBSupport.tokens(state, heroId, IgnatiaHero.MAGMA)
        val primed = state.heroes.count { e ->
            e.team != me.team && e.at?.let { at -> magma.any { HexGrid.distance(it.at!!, at) == 1 } } == true
        }
        var value = primed * 1.5
        // evalTerms: Equilibrium in force (public: silver resolved faceup this round).
        if (BatchBSupport.effect(state, heroId, EQUILIBRIUM)) value += 3.0
        return BatchBSupport.sign(state, team, heroId) * value
    }

    override fun threatAdjust(ctx: HContext, enemyHeroId: String, target: Hex): Double {
        if (enemyHeroId != heroId) return 0.0
        val from = ctx.hero(heroId)?.at ?: return 0.0
        val distance = ctx.distance(from, target)
        if (distance > BLUE_REACH) return 0.0
        // threatAdjust: the blue discard needs a token or a minion beside the hero.
        val beside = HexGrid.neighbors(target).any { hex ->
            ctx.state.minions.any { it.at == hex } || ctx.state.pieces.any { it.token && it.at == hex }
        }
        if (!beside) return 0.0
        return 2.0 * BatchBSupport.hold(ctx, heroId) { it.id in BLUE_DISCARD }
    }

    private fun play(ctx: HContext, choice: Choice): Double {
        val id = BatchBSupport.cardId(choice) ?: return 0.0
        val at = ctx.own()?.at ?: return 0.0
        if (id == EQUILIBRIUM) return equilibrium(ctx, at)
        val card = ctx.card(id)
        val faces = faces(ctx, at, card) ?: return 0.0
        val (blue, orange) = faces
        // H1: with Equilibrium she picks the live face; without it, the coin does.
        if (BatchBSupport.effect(ctx.state, heroId, EQUILIBRIUM)) return if (blue || orange) 1.0 else 0.0
        val coinBlue = ctx.state.tieBreaker == Team.BLUE
        val live = if (coinBlue) blue else orange
        val other = if (coinBlue) orange else blue
        return when {
            // H1: both faces have a target, so a flipped coin still plays.
            live && other -> 1.5

            live -> 0.0

            // H1/H12: the face the coin shows has no target. The general book counted the other face's.
            else -> BatchBSupport.deadStrike(ctx, at, card) - DEAD_FACE
        }
    }

    /**
     * Whether each face of an attack or targeted skill has a target now (blue first). Null for a card
     * whose two faces both always do something (the magma and swap greens).
     */
    private fun faces(ctx: HContext, at: Hex, card: HCard): Pair<Boolean, Boolean>? {
        val id = card.id
        val units = BatchBSupport.enemyUnits(ctx)
        return when {
            id == "ignatia-gold" -> {
                val reach = ctx.attackReach(ctx.seat, card)
                val minion = units.any { (u, pos) -> ctx.state.minion(u) != null && ctx.distance(at, pos) == 1 }
                val hero = units.any { (u, pos) -> ctx.hero(u) != null && ctx.distance(at, pos) in 1..reach }
                minion to hero
            }

            id in ALT_RED -> {
                val reach = ctx.attackReach(ctx.seat, card)
                units.any { ctx.distance(at, it.second) == 1 } to units.any { ctx.distance(at, it.second) == reach }
            }

            id.startsWith("ignatia-red") -> {
                val reach = ctx.attackReach(ctx.seat, card)
                val inRange = units.filter { ctx.distance(at, it.second) in 1..reach }
                inRange.any { !HexGrid.inStraightLine(at, it.second) } to
                    inRange.any { HexGrid.inStraightLine(at, it.second) }
            }

            id in BLUE_DISCARD -> {
                val radius = card.radius + ctx.itemBonus(ctx.seat, Stat.RADIUS)
                val heroes = ctx.enemies().mapNotNull { it.at }
                val discard = heroes.any { pos ->
                    ctx.distance(at, pos) in 1..radius &&
                        HexGrid.neighbors(pos).any { occupiedByTokenOrMinion(ctx, it) }
                }
                // Orange: only the minion must be in radius; the enemy hero beside it may stand anywhere.
                val remove = ctx.state.minions.any { m ->
                    m.team != ctx.team && m.at?.let { p ->
                        ctx.distance(at, p) in 1..radius && heroes.any { ctx.distance(it, p) == 1 }
                    } == true
                }
                discard to remove
            }

            else -> null
        }
    }

    // H3: Equilibrium on turn 1 when she is not about to need its defense 7.
    private fun equilibrium(ctx: HContext, at: Hex): Double {
        if (ctx.turn != 1) return -1.0
        val exposed = ctx.threatsOn(at, ctx.team).any { it.attackTotal >= SILVER_DEFENSE - 1 }
        return if (exposed) -4.0 else 2.0
    }

    // H15: silver is defense 7 and Equilibrium; a hit another card also stops should take that card.
    private fun defend(ctx: HContext, d: Decision, choice: Choice): Double {
        if (BatchBSupport.cardId(choice) != EQUILIBRIUM) return 0.0
        val total = d.context.attackTotal ?: return 0.0
        val others = d.options.filterIsInstance<Choice.Card>().any { o ->
            o.cardId != EQUILIBRIUM && (d.context.defensePreview[o.cardId] ?: 0) >= total
        }
        return if (others && ctx.turn <= 2) -4.0 else 0.0
    }

    private fun occupiedByTokenOrMinion(ctx: HContext, hex: Hex): Boolean =
        ctx.state.minions.any { it.at == hex } || ctx.state.pieces.any { it.token && it.at == hex }

    private const val EQUILIBRIUM = "ignatia-silver"
    private const val DEAD_FACE = 1.5
    private const val SILVER_DEFENSE = 7
    private const val BLUE_REACH = 5
    private val ALT_RED = setOf("ignatia-red-2b", "ignatia-red-3b")
    private val BLUE_DISCARD = setOf("ignatia-blue-1", "ignatia-blue-2a", "ignatia-blue-3a")
}
