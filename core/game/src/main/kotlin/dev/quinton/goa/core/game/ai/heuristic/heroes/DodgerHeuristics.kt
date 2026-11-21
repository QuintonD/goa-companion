package dev.quinton.goa.core.game.ai.heuristic.heroes

import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.ai.heuristic.HContext
import dev.quinton.goa.core.game.ai.heuristic.HeroHeuristics
import dev.quinton.goa.core.game.ai.heuristic.HeroRole
import dev.quinton.goa.core.game.ai.heuristic.HeroStyle
import dev.quinton.goa.core.game.engine.HeroState
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.catalog.Stat
import kotlin.math.max

/**
 * Dodger: a ranged finisher who reads empty Battle Zone spawn points. Her gold shoots at range
 * only from beside one; her primary red shoots at range only a hero with a card in the discard.
 * Tide of Darkness (ultimate) makes every empty space such a spawn point during her actions.
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
        DecisionKind.CHOOSE_OPTION -> option(ctx, d, choice)
        else -> 0.0
    }

    private fun play(ctx: HContext, choice: Choice): Double {
        if (choice !is Choice.Card) return 0.0
        val me = ctx.own() ?: return 0.0
        val at = me.at ?: return 0.0
        return when (choice.cardId) {
            "dodger-silver" -> if (ctx.patches) silver(ctx, at) else 0.0

            in GOLD -> {
                val b = razor(ctx, me, at, choice.cardId)
                BatchASupport.correction(ctx, me.id, at, ctx.card(choice.cardId), max(b.first, b.second))
            }

            in FINGER -> {
                val b = finger(ctx, me, at, choice.cardId)
                val both = if (choice.cardId == "dodger-red-3a") b.first + b.second else max(b.first, b.second)
                BatchASupport.correction(ctx, me.id, at, ctx.card(choice.cardId), both)
            }

            in RITUAL -> ritual(ctx, at, choice.cardId)

            else -> 0.0
        }
    }

    private fun tide(ctx: HContext): Boolean = BatchASupport.ultimateUnlocked(ctx.state, heroId)

    /**
     * H1 / H2: Death Trap makes an enemy hero in radius 4 who stands beside an empty Battle Zone
     * spawn discard ("if able": an empty hand loses nothing). Nobody there, it fizzles.
     */
    private fun silver(ctx: HContext, at: Hex): Double {
        val radius = BatchASupport.radiusOf(ctx, heroId, 4)
        val best = BatchASupport.enemiesWithin(ctx, at, radius)
            .filter { e -> BatchASupport.besideEmptySpawn(ctx, e.at!!, tide(ctx)) }
            .maxOfOrNull { BatchASupport.discardValue(ctx, it, orDefeat = false) }
        return if (best == null || best <= 0.0) -4.0 else best - 1.4
    }

    /** H3: Dread Razor: (adjacent unit, any unit in range while she is beside an empty spawn). */
    private fun razor(ctx: HContext, me: HeroState, at: Hex, cardId: String): Pair<Double, Double> {
        val card = ctx.card(cardId)
        val total = card.attack + ctx.itemBonus(me.id, Stat.ATTACK)
        val heroOnly = cardId == "dodger-handicap"
        val adjacent = BatchASupport.bestAdjacent(ctx, me.id, at, total, heroesOnly = heroOnly)
        if (!BatchASupport.besideEmptySpawn(ctx, at, tide(ctx))) return adjacent to 0.0
        val reach = ctx.attackReach(me.id, card)
        var range = 0.0
        if (!heroOnly) {
            for (m in ctx.state.minions) {
                val pos = m.at ?: continue
                if (m.team != ctx.team && !ctx.heavyImmune(m.id) && ctx.distance(at, pos) in 1..reach) {
                    range = max(range, ctx.minionWorth(m.id))
                }
            }
        }
        for (e in BatchASupport.enemiesWithin(ctx, at, reach)) {
            range =
                max(range, BatchASupport.heroHit(ctx, me.id, e.id, total, true))
        }
        return adjacent to range
    }

    /** H5: Finger of Death: (adjacent unit, hero in range with a card in the discard). */
    private fun finger(ctx: HContext, me: HeroState, at: Hex, cardId: String): Pair<Double, Double> {
        val card = ctx.card(cardId)
        val total = card.attack + ctx.itemBonus(me.id, Stat.ATTACK)
        val adjacent = BatchASupport.bestAdjacent(ctx, me.id, at, total)
        val reach = ctx.attackReach(me.id, card)
        var range = 0.0
        for (e in BatchASupport.enemiesWithin(ctx, at, reach)) {
            if (BatchASupport.discardCount(ctx.state, e.id) >
                0
            ) {
                range = max(range, BatchASupport.heroHit(ctx, me.id, e.id, total, true))
            }
        }
        return adjacent to range
    }

    /** H8: the ritual pays coins only with 2 or more empty Battle Zone spawns in radius 3. */
    private fun ritual(ctx: HContext, at: Hex, cardId: String): Double {
        val radius = BatchASupport.radiusOf(ctx, heroId, 3)
        if (BatchASupport.emptySpawnsWithin(ctx, at, radius, tide(ctx)) < 2) return -1.0
        return if (cardId == "dodger-green-1") 0.0 else 1.0
    }

    /** H4: "Choose one —" (Middlefinger: "or both") at each bullet's value. */
    private fun option(ctx: HContext, d: Decision, choice: Choice): Double {
        if (choice !is Choice.Option) return 0.0
        val me = ctx.own() ?: return 0.0
        val at = me.at ?: return 0.0
        val src = d.sourceCardId ?: return 0.0
        val b = when (src) {
            in GOLD -> razor(ctx, me, at, src)
            in FINGER -> finger(ctx, me, at, src)
            else -> return 0.0
        }
        return when {
            choice.label.startsWith("Both") -> b.first + b.second
            choice.index == 0 -> b.first
            else -> b.second
        }
    }
}

private val GOLD = setOf("dodger-gold", "dodger-handicap")
private val FINGER = setOf("dodger-red-1", "dodger-red-2a", "dodger-red-3a")
private val RITUAL = setOf("dodger-green-1", "dodger-green-2a", "dodger-green-3a")
