package dev.quinton.goa.core.game.ai.heuristic.heroes

import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.ai.heuristic.HContext
import dev.quinton.goa.core.game.ai.heuristic.HeroHeuristics
import dev.quinton.goa.core.game.ai.heuristic.HeroRole
import dev.quinton.goa.core.game.ai.heuristic.HeroStyle
import dev.quinton.goa.core.game.engine.HeroState
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.catalog.Stat

/**
 * Rowenna (heroes/rowenna.md) stands in the fight with Defense 6–8 reds, hands coins to allies, and
 * executes an empty hand. Gold lets the target retrieve before the swing, so a fat card in their
 * discard is a heal for them (CCplusplus, BGG 3447239; rulings/rowenna.md 1).
 */
object RowennaHeuristics : HeroHeuristics {
    override val heroId: String = "rowenna"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.TANK,
        idealDistance = 1,
        aggression = 0.60,
        laneWeight = 1.1,
        upgradeOrder = listOf(
            "rowenna-red-2a",
            "rowenna-red-3a",
            "rowenna-green-2a",
            "rowenna-green-3a",
            "red",
            "blue",
            "green",
        ),
        keepDefender = true,
    )

    private val golds = setOf("rowenna-gold", "rowenna-handicap")
    private val executes = setOf("rowenna-blue-2b", "rowenna-blue-3b")
    private val shots = setOf("rowenna-green-2b", "rowenna-green-3b")

    /** H11: an adjacent enemy hero with no cards in hand is the execute, when she holds it. */
    override fun override(ctx: HContext, d: Decision): Choice? {
        if (d.kind != DecisionKind.PLAY_CARD) return null
        val me = ctx.own() ?: return null
        val card = executes.firstOrNull { BatchC.inHand(me, it) } ?: return null
        if (emptyAdjacent(ctx, me) == null) return null
        return d.options.firstOrNull { it is Choice.Card && it.cardId == card }
    }

    override fun adjust(ctx: HContext, d: Decision, choice: Choice): Double = when (d.kind) {
        DecisionKind.PLAY_CARD -> play(ctx, choice)
        DecisionKind.CHOOSE_UNIT -> target(ctx, d, choice)
        DecisionKind.DEFEND -> defend(d, choice)
        else -> 0.0
    }

    /** Silver places her next to a hero in range 4: beyond Movement 2, but the hit comes a turn later. */
    override fun threatAdjust(ctx: HContext, enemyHeroId: String, target: Hex): Double {
        if (enemyHeroId != heroId) return 0.0
        val at = ctx.hero(heroId)?.at ?: return 0.0
        if (!BatchC.mayHold(ctx, heroId, setOf("rowenna-silver"))) return 0.0
        return if (ctx.distance(at, target) in 3..4) 1.5 else 0.0
    }

    private fun play(ctx: HContext, choice: Choice): Double {
        val id = BatchC.cardId(choice) ?: return 0.0
        val me = ctx.own() ?: return 0.0
        val at = me.at ?: return 0.0
        return when (id) {
            // H12: the execute is also the Defense 6–7 block. Do not walk with it before turn 4.
            in executes -> if (ctx.turn < 4 && emptyAdjacent(ctx, me) == null) -4.0 else 0.0

            in shots -> openingShots(ctx, me, at) + BatchC.heroOnlyDiscard(ctx, id, BatchC.radiusOf(ctx, id))

            "rowenna-silver" -> if (BatchC.enemiesWithin(ctx, at, 4).isEmpty()) -2.0 else 0.0

            in golds -> goldTarget(ctx, at)

            else -> 0.0
        }
    }

    /** H7 / H8: both discards must be empty; then it is a free card off them, best on turn 1. */
    private fun openingShots(ctx: HContext, me: HeroState, at: Hex): Double {
        if (me.cards.any { it.zone == Zone.DISCARD }) return -1.5
        val victim = BatchC.enemiesWithin(ctx, at, 3).any { e ->
            e.hand.isNotEmpty() &&
                e.cards.none { it.zone == Zone.DISCARD }
        }
        return when {
            !victim -> -1.5
            ctx.turn == 1 -> 3.0
            else -> 1.5
        }
    }

    /** H1 at the card: gold whose only hero target heals from the discard is worse than it looks. */
    private fun goldTarget(ctx: HContext, at: Hex): Double {
        val adjacent = ctx.enemies().filter { e -> e.at?.let { ctx.distance(at, it) == 1 } == true }
        if (adjacent.isEmpty()) return 0.0
        val minion = ctx.state.minions.any { m -> BatchC.takeable(ctx, m) && ctx.distance(at, m.at!!) == 1 }
        return if (!minion && adjacent.all { discardHolds(ctx, it) }) -2.5 else 0.0
    }

    private fun target(ctx: HContext, d: Decision, choice: Choice): Double {
        val id = BatchC.unitId(choice) ?: return 0.0
        if (d.sourceCardId !in golds || !d.prompt.startsWith("Target")) return 0.0
        val hero = ctx.state.hero(id) ?: return 0.0
        // H1: gold lets the target retrieve before the attack. A discard that meets the swing is their card back.
        return if (hero.team != ctx.team && discardHolds(ctx, hero)) -6.0 else 0.0
    }

    private fun defend(d: Decision, choice: Choice): Double {
        val id = BatchC.cardId(choice) ?: return 0.0
        // H13: gold can retrieve the red she blocks with; keep gold when a cheaper card also holds.
        return if (id in golds && BatchC.meets(d, id) && BatchC.otherMeets(d, id)) -2.5 else 0.0
    }

    private fun emptyAdjacent(ctx: HContext, me: HeroState): String? {
        val at = me.at ?: return null
        return ctx.enemies().firstOrNull { e ->
            e.hand.isEmpty() && e.at?.let { ctx.distance(at, it) == 1 } == true
        }?.id
    }

    /** A faceup card in their discard meets gold's Attack 4 + items (public: discards are faceup). */
    private fun discardHolds(ctx: HContext, hero: HeroState): Boolean {
        val at = hero.at ?: return false
        val total = ctx.card("rowenna-gold").attack + ctx.itemBonus(ctx.seat, Stat.ATTACK)
        val bonus = ctx.itemBonus(hero.id, Stat.DEFENSE) + ctx.minionModifier(at, hero.team)
        return hero.cards.any { c ->
            c.zone == Zone.DISCARD && !c.faceDown && (ctx.card(c.id).defense ?: -99) + bonus >= total
        }
    }
}
