package dev.quinton.goa.core.game.ai.heuristic.heroes

import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.ai.heuristic.HContext
import dev.quinton.goa.core.game.ai.heuristic.HeroHeuristics
import dev.quinton.goa.core.game.ai.heuristic.HeroRole
import dev.quinton.goa.core.game.ai.heuristic.HeroStyle
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.catalog.Stat

/**
 * Silverarrow (heroes/silverarrow.md): Defense 1 outside the reds, gold and the b-reds hit only the exact
 * ring, the a-reds the isolated unit. Stay off adjacency, keep a unit on the ring, heal allies.
 */
object SilverarrowHeuristics : HeroHeuristics {
    override val heroId: String = "silverarrow"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.RANGED,
        idealDistance = 3,
        aggression = 0.40,
        laneWeight = 0.8,
        upgradeOrder = listOf(
            "silverarrow-red-2a",
            "silverarrow-red-3a",
            "silverarrow-blue-2a",
            "silverarrow-blue-3a",
            "red",
            "blue",
            "green",
        ),
        keepDefender = true,
    )

    /** "Target a unit at maximum range": printed range, plus Range items (rulings/silverarrow.md). */
    private val ringCards =
        setOf("silverarrow-gold", "silverarrow-handicap", "silverarrow-red-2b", "silverarrow-red-3b")
    private val peels = setOf("silverarrow-green-1", "silverarrow-green-2a", "silverarrow-green-3a")
    private val heals = setOf("silverarrow-green-2b", "silverarrow-green-3b")
    private val warnings = setOf("silverarrow-blue-2b", "silverarrow-blue-3b")

    override fun adjust(ctx: HContext, d: Decision, choice: Choice): Double = when (d.kind) {
        DecisionKind.PLAY_CARD -> play(ctx, choice)
        DecisionKind.CHOOSE_UNIT -> heal(ctx, d, choice)
        DecisionKind.CHOOSE_SPACE -> space(ctx, d, choice)
        DecisionKind.DEFEND -> defend(d, choice)
        else -> 0.0
    }

    /** Gold and the b-reds cannot hit inside their ring; the general threat smears the whole disk. */
    override fun threatAdjust(ctx: HContext, enemyHeroId: String, target: Hex): Double {
        if (enemyHeroId != heroId) return 0.0
        val at = ctx.hero(heroId)?.at ?: return 0.0
        val d = ctx.distance(at, target)
        val attacks = ctx.possibleCards(heroId).filter { it.card.attackCard }
        if (attacks.isEmpty() || attacks.any { it.card.id !in ringCards }) return 0.0
        val ring = attacks.minOf { it.card.range } + ctx.itemBonus(heroId, Stat.RANGE)
        return if (d in 1 until ring) -2.0 else 0.0
    }

    private fun play(ctx: HContext, choice: Choice): Double {
        val id = BatchC.cardId(choice) ?: return 0.0
        val at = ctx.own()?.at ?: return 0.0
        return when (id) {
            in ringCards -> ring(ctx, at, id)

            in peels -> if (adjacentEnemyUnit(ctx, at)) 2.0 else 0.0

            // H9: the warning shot makes an enemy hero in radius discard at end of turn; no hero, no value.
            in warnings -> BatchC.heroOnlyDiscard(ctx, id, BatchC.radiusOf(ctx, id))

            in heals -> if (healable(ctx, at, ctx.card(id).radius)) 1.5 else -1.0

            else -> 0.0
        }
    }

    /** H1 / H13: the general reach counts the whole disk; only a unit on the exact ring is a target. */
    private fun ring(ctx: HContext, at: Hex, id: String): Double {
        val r = ctx.card(id).range + ctx.itemBonus(heroId, Stat.RANGE)
        val onRing = ctx.state.minions.any { m -> BatchC.takeable(ctx, m) && ctx.distance(at, m.at!!) == r } ||
            ctx.enemies().any { e -> e.at?.let { ctx.distance(at, it) == r } == true }
        val inside = ctx.state.minions.any { m -> BatchC.takeable(ctx, m) && ctx.distance(at, m.at!!) < r } ||
            ctx.enemies().any { e -> e.at?.let { ctx.distance(at, it) < r } == true }
        return when {
            onRing -> 1.0
            inside -> -3.0
            else -> 0.0
        }
    }

    private fun adjacentEnemyUnit(ctx: HContext, at: Hex): Boolean =
        ctx.enemies().any { e -> e.at?.let { ctx.distance(at, it) == 1 } == true } ||
            ctx.state.minions.any { m -> m.team != ctx.team && m.at?.let { ctx.distance(at, it) == 1 } == true }

    private fun healable(ctx: HContext, at: Hex, radius: Int): Boolean =
        BatchC.alliesWithin(ctx, at, radius).any { a -> a.cards.any { it.zone == Zone.DISCARD && !it.faceDown } }

    /** H7: the heal goes to an ally with a discard; never to an enemy. */
    private fun heal(ctx: HContext, d: Decision, choice: Choice): Double {
        if (d.sourceCardId !in heals) return 0.0
        val hero = BatchC.unitId(choice)?.let { ctx.state.hero(it) } ?: return 0.0
        if (hero.team != ctx.team) return -8.0
        return if (hero.cards.any { it.zone == Zone.DISCARD && !it.faceDown }) 3.0 else -1.0
    }

    /** H11: Defense 1. Do not end her own move next to an enemy hero unless a red is still the block. */
    private fun space(ctx: HContext, d: Decision, choice: Choice): Double {
        if (!BatchC.movesSelf(ctx, d)) return 0.0
        val hex = BatchC.hexOf(choice) ?: return 0.0
        if (ctx.ownHand().any { it.color.name == "RED" }) return 0.0
        val beside = ctx.enemies().count { e -> e.at?.let { ctx.distance(hex, it) == 1 } == true }
        return -3.0 * beside
    }

    /** H10: spend the lowest card that still meets the attack; red only when it is the only one. */
    private fun defend(d: Decision, choice: Choice): Double {
        val id = BatchC.cardId(choice) ?: return 0.0
        return if (id.startsWith("silverarrow-red-") && BatchC.meets(d, id) && BatchC.otherMeets(d, id)) -2.0 else 0.0
    }
}
