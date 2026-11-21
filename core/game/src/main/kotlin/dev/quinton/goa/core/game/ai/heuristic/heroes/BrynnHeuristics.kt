package dev.quinton.goa.core.game.ai.heuristic.heroes

import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.ai.heuristic.HContext
import dev.quinton.goa.core.game.ai.heuristic.HeroHeuristics
import dev.quinton.goa.core.game.ai.heuristic.HeroRole
import dev.quinton.goa.core.game.ai.heuristic.HeroStyle
import dev.quinton.goa.core.game.engine.HeroState
import dev.quinton.goa.core.game.engine.isObstacle
import dev.quinton.goa.core.game.heroes.BrynnHero
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.catalog.Stat
import kotlin.math.max

/**
 * Brynn: a fighter whose cards grow against a hero adjacent to 3 or more obstacles ("bound").
 * She is one of the obstacles. Her primary red is +2 Attack on a bound hero; gold and the traps
 * reach a bound hero at range.
 */
object BrynnHeuristics : HeroHeuristics {
    override val heroId: String = "brynn"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.FIGHTER,
        idealDistance = 1,
        aggression = 0.55,
        laneWeight = 0.50,
        upgradeOrder = listOf("red", "blue", "green"),
        keepDefender = true,
    )

    override fun adjust(ctx: HContext, d: Decision, choice: Choice): Double = when (d.kind) {
        DecisionKind.PLAY_CARD -> play(ctx, choice)
        DecisionKind.CHOOSE_OPTION -> option(ctx, d, choice)
        DecisionKind.CONFIRM -> confirm(d, choice)
        DecisionKind.KEEP_UPGRADE -> keep(choice)
        else -> 0.0
    }

    /** H1 (threat): standing crowded next to her is primary red at +2. */
    override fun threatAdjust(ctx: HContext, enemyHeroId: String, target: Hex): Double {
        if (enemyHeroId != heroId) return 0.0
        val from = ctx.hero(heroId)?.at ?: return 0.0
        if (ctx.distance(from, target) != 1) return 0.0
        val vacated = ctx.own()?.at
        val crowd = HexGrid.neighbors(target).count { n ->
            n != vacated && (ctx.game.isObstacle(n) || ctx.game.isObstacle(n, heroId))
        }
        if (crowd < 3 && !BatchASupport.ultimateUnlocked(ctx.state, heroId)) return 0.0
        return 2.0 * BatchASupport.chancePlays(ctx, heroId) { it.id in MELEE }
    }

    private fun play(ctx: HContext, choice: Choice): Double {
        if (choice !is Choice.Card) return 0.0
        val me = ctx.own() ?: return 0.0
        val at = me.at ?: return 0.0
        return when (choice.cardId) {
            in MELEE -> melee(ctx, me, at, choice.cardId)
            in GOLD -> gold(ctx, me, at, choice.cardId)
            in TRAP -> trap(ctx, at, choice.cardId)
            "brynn-silver" -> silver(ctx)
            else -> 0.0
        }
    }

    /** H1: +2 Attack on a bound adjacent hero; the general book saw the printed Attack only. */
    private fun melee(ctx: HContext, me: HeroState, at: Hex, cardId: String): Double {
        val card = ctx.card(cardId)
        val total = card.attack + ctx.itemBonus(me.id, Stat.ATTACK)
        var best = BatchASupport.bestMinionOn(ctx, HexGrid.neighbors(at).toSet())
        for (e in BatchASupport.enemiesWithin(ctx, at, 1)) {
            val bonus = if (bound(ctx, e)) 2 else 0
            best = max(best, BatchASupport.heroHit(ctx, me.id, e.id, total + bonus, false))
        }
        return BatchASupport.correction(ctx, me.id, at, card, best)
    }

    /**
     * H2: gold hits an adjacent unit, or a bound hero in range 3. The general book thought it hit
     * anything in range.
     */
    private fun gold(ctx: HContext, me: HeroState, at: Hex, cardId: String): Double {
        val card = ctx.card(cardId)
        val v = goldBullets(ctx, me, at, cardId)
        return BatchASupport.correction(ctx, me.id, at, card, max(v.first, v.second))
    }

    /** The two bullets of Familiar Ground: (adjacent unit, bound hero in range). */
    private fun goldBullets(ctx: HContext, me: HeroState, at: Hex, cardId: String): Pair<Double, Double> {
        val card = ctx.card(cardId)
        val total = card.attack + ctx.itemBonus(me.id, Stat.ATTACK)
        val reach = ctx.attackReach(me.id, card)
        val adjacent = BatchASupport.bestAdjacent(ctx, me.id, at, total, heroesOnly = cardId == "brynn-handicap")
        var range = 0.0
        for (e in BatchASupport.enemiesWithin(ctx, at, reach)) {
            if (bound(ctx, e)) range = max(range, BatchASupport.heroHit(ctx, me.id, e.id, total, true))
        }
        return adjacent to range
    }

    /** H4: a trap discards an adjacent enemy hero, or a bound one in radius. Nobody, it fizzles. */
    private fun trap(ctx: HContext, at: Hex, cardId: String): Double {
        val v = trapBullets(ctx, at, cardId)
        val best = max(v.first, v.second)
        return if (best <= 0.0) -3.0 else best - 1.4
    }

    private fun trapBullets(ctx: HContext, at: Hex, cardId: String): Pair<Double, Double> {
        val orDefeat = cardId == "brynn-green-3a"
        val radius = BatchASupport.radiusOf(ctx, heroId, ctx.card(cardId).radius)
        var adjacent = 0.0
        var range = 0.0
        for (e in BatchASupport.enemiesWithin(ctx, at, radius)) {
            val v = BatchASupport.discardValue(ctx, e, orDefeat)
            if (ctx.distance(at, e.at!!) == 1) adjacent = max(adjacent, v)
            if (bound(ctx, e)) range = max(range, v)
        }
        return adjacent to range
    }

    // H14: Decoy on turn 4 is a Defense 2 shove with no card left to use the crowd.
    private fun silver(ctx: HContext): Double = if (ctx.turn >= 4 && ctx.ownHand().size > 1) -3.0 else 0.0

    /** H2 / H4: "Choose one —" on gold and the traps: index 0 is adjacent, index 1 the bound hero. */
    private fun option(ctx: HContext, d: Decision, choice: Choice): Double {
        if (choice !is Choice.Option) return 0.0
        val me = ctx.own() ?: return 0.0
        val at = me.at ?: return 0.0
        val src = d.sourceCardId ?: return 0.0
        val bullets = when (src) {
            in GOLD -> goldBullets(ctx, me, at, src)
            in TRAP -> trapBullets(ctx, at, src)
            else -> return 0.0
        }
        return if (choice.index == 0) bullets.first else bullets.second
    }

    // H8: Peak Precision connected on a bound hero: take the card back.
    private fun confirm(d: Decision, choice: Choice): Double =
        if (d.sourceCardId == "brynn-red-3a" && choice is Choice.Yes) 4.0 else 0.0

    // H12: primary red is kept; Split Attack is tucked for the Defense item.
    private fun keep(choice: Choice): Double {
        if (choice !is Choice.Card) return 0.0
        return if (choice.cardId == "brynn-red-2a" || choice.cardId == "brynn-red-3a") 1.5 else 0.0
    }

    /** The engine's own test: 3 or more obstacles around, Brynn's included; Over the Top makes every enemy bound. */
    private fun bound(ctx: HContext, e: HeroState): Boolean {
        if (BatchASupport.ultimateUnlocked(ctx.state, heroId)) return true
        return with(BrynnHero) { ctx.game.obstaclesAround(e.id, heroId) } >= 3
    }
}

private val GOLD = setOf("brynn-gold", "brynn-handicap")
private val MELEE = setOf("brynn-red-1", "brynn-red-2a", "brynn-red-3a")
private val TRAP = setOf("brynn-green-1", "brynn-green-2a", "brynn-green-3a")
