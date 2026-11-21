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
import dev.quinton.goa.core.game.engine.HeroState
import dev.quinton.goa.core.game.engine.MoveSpec
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.game.engine.isEmpty
import dev.quinton.goa.core.game.engine.reach
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.catalog.Stat
import dev.quinton.goa.core.model.match.Team
import kotlin.math.max

/**
 * Cutter: a pusher. The plank (gold) pushes an adjacent hero up to 4; across a zone border that
 * hero discards, or is defeated. The lunge hits the hero in front of a straight step at +2 Attack;
 * the dash lands beside a hero and makes them discard.
 */
object CutterHeuristics : HeroHeuristics {
    override val heroId: String = "cutter"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.PUSHER,
        idealDistance = 2,
        aggression = 0.65,
        // Research: 0.60. Ablation, strength 8, 96 games: 46.9% with 0.60, 54.2% with 1.0 (2026-11-21).
        laneWeight = 1.0,
        upgradeOrder = listOf("blue", "red", "green"),
        keepDefender = true,
    )

    override fun adjust(ctx: HContext, d: Decision, choice: Choice): Double = when (d.kind) {
        DecisionKind.PLAY_CARD -> play(ctx, choice)
        DecisionKind.CHOOSE_OPTION -> option(ctx, d, choice)
        DecisionKind.CHOOSE_NUMBER -> number(ctx, d, choice)
        DecisionKind.CONFIRM -> confirm(ctx, d, choice)
        else -> 0.0
    }

    /**
     * H16 (eval): A Fistful of Coins wins outright at 13 coins. Which tier-III blue she kept is
     * public, so the coins above 9 count while it is in her round.
     */
    override fun evalTerms(state: GameState, team: Team, heroId: String): Double {
        if (heroId != this.heroId) return 0.0
        val me = state.hero(heroId) ?: return 0.0
        val fistful = me.cards.any { it.id == "cutter-blue-3b" && it.zone != Zone.DECK && it.zone != Zone.ITEM }
        if (!fistful) return 0.0
        val extra = me.coins - 9
        if (extra <= 0) return 0.0
        return BatchA.sign(state, team, heroId) * extra * 2.0
    }

    private fun play(ctx: HContext, choice: Choice): Double {
        if (choice !is Choice.Card) return 0.0
        val me = ctx.own() ?: return 0.0
        val at = me.at ?: return 0.0
        return when (choice.cardId) {
            in PLANK -> plank(ctx, at, choice.cardId)
            in LUNGE -> lunge(ctx, me, at, choice.cardId)
            in DASH -> dash(ctx, choice.cardId)
            in BARRAGE -> barrage(ctx, at, choice.cardId)
            "cutter-silver" -> silver(ctx)
            else -> 0.0
        }
    }

    /**
     * H1 / H2: the plank is worth the best push across a zone border, or the adjacent minion it
     * defeats. With neither it is Movement 1: hold it.
     */
    private fun plank(ctx: HContext, at: Hex, cardId: String): Double {
        val (push, minion) = plankBullets(ctx, at, cardId)
        val best = max(push, minion)
        if (best <= 0.0) return if (ctx.turn < 4) -2.5 else 0.0
        return best - 1.4
    }

    private fun plankBullets(ctx: HContext, at: Hex, cardId: String): Pair<Double, Double> {
        val max = if (cardId == "cutter-handicap") 3 else 4
        var push = 0.0
        for (e in BatchA.enemiesWithin(ctx, at, 1)) {
            if (zonePush(ctx, at, e.at!!, max) > 0) push = max(push, BatchA.discardValue(ctx, e, orDefeat = true))
        }
        val minion = if (cardId == "cutter-handicap") 0.0 else BatchA.bestMinionOn(ctx, HexGrid.neighbors(at).toSet())
        return push to minion
    }

    /**
     * H5: the lunge is a straight step of 1..N and a hit at +2 on the hero in front; the other
     * bullet is a plain adjacent hit, always there. The general book saw the printed Attack.
     */
    private fun lunge(ctx: HContext, me: HeroState, at: Hex, cardId: String): Double {
        val card = ctx.card(cardId)
        val total = card.attack + ctx.itemBonus(me.id, Stat.ATTACK)
        val (step, adjacent) = lungeBullets(ctx, me, at, cardId, total)
        return BatchA.correction(ctx, me.id, at, card, max(step, adjacent))
    }

    private fun lungeBullets(ctx: HContext, me: HeroState, at: Hex, cardId: String, total: Int): Pair<Double, Double> {
        val n = LUNGE_MAX.getValue(cardId)
        var step = 0.0
        for (land in ctx.game.reach(MoveSpec(heroId, 1, n, straightLine = true)).keys) {
            val dir = HexGrid.straightLineDirection(at, land) ?: continue
            val front = BatchA.step(land, dir, 1)
            val e = ctx.enemies().firstOrNull { it.at == front } ?: continue
            step = max(step, BatchA.heroHit(ctx, me.id, e.id, total + 2, false))
        }
        return step to BatchA.bestAdjacent(ctx, me.id, at, total)
    }

    /** H6: the dash lands beside an enemy hero, who discards. No landing, the card is Movement 2 at best. */
    private fun dash(ctx: HContext, cardId: String): Double {
        var best = -1.0
        val lands = ctx.game.reach(
            MoveSpec(
                heroId,
                3,
                DASH_MAX.getValue(cardId),
                straightLine = true,
                ignoreObstacles = true,
                endFilter = { BatchA.besideEnemyHero(ctx, it) },
            ),
        ).keys
        for (land in lands) {
            for (e in BatchA.enemiesWithin(ctx, land, 1)) best = max(best, BatchA.discardValue(ctx, e, orDefeat = false))
        }
        return if (best < 0.0) -2.0 else best - 1.4
    }

    /** H8: Bombardment family: an enemy hero in radius, beside another enemy unit, not beside her, discards. */
    private fun barrage(ctx: HContext, at: Hex, cardId: String): Double {
        val radius = BatchA.radiusOf(ctx, heroId, ctx.card(cardId).radius)
        val values = BatchA.enemiesWithin(ctx, at, radius).filter { e ->
            val pos = e.at!!
            ctx.distance(at, pos) > 1 && (
                ctx.enemies().any { o -> o.id != e.id && o.at?.let { ctx.distance(it, pos) == 1 } == true } ||
                    ctx.state.minions.any { m -> m.team == e.team && m.at?.let { ctx.distance(it, pos) == 1 } == true }
                )
        }.map { BatchA.discardValue(ctx, it, orDefeat = false) }.sortedDescending()
        if (values.isEmpty()) return -3.0
        val repeat = if (cardId == "cutter-blue-3a" && values.size > 1) values[1] else 0.0
        return values[0] + repeat - 1.4
    }

    // H4: the grapple early, so its Defense 2 is not the last card in hand.
    private fun silver(ctx: HContext): Double = when (ctx.turn) {
        1, 2 -> 1.0
        4 -> -2.0
        else -> 0.0
    }

    /** H1 / H5: "Choose one —" on the plank and the lunge, each bullet at its own value. */
    private fun option(ctx: HContext, d: Decision, choice: Choice): Double {
        if (choice !is Choice.Option) return 0.0
        val me = ctx.own() ?: return 0.0
        val at = me.at ?: return 0.0
        val src = d.sourceCardId ?: return 0.0
        val bullets = when (src) {
            in PLANK -> plankBullets(ctx, at, src)
            in LUNGE -> {
                val card = ctx.card(src)
                lungeBullets(ctx, me, at, src, card.attack + ctx.itemBonus(me.id, Stat.ATTACK))
            }
            else -> return 0.0
        }
        return if (choice.index == 0) bullets.first else bullets.second
    }

    /** H1: push exactly far enough to cross the zone border; 0 is never the answer. */
    private fun number(ctx: HContext, d: Decision, choice: Choice): Double {
        if (d.sourceCardId !in PLANK || choice !is Choice.Number) return 0.0
        val at = ctx.own()?.at ?: return 0.0
        // The pushed hero is adjacent; the decision does not name it, so take the nearest border.
        val cross = BatchA.enemiesWithin(ctx, at, 1).map { zonePush(ctx, at, it.at!!, if (d.sourceCardId == "cutter-handicap") 3 else 4) }.filter { it > 0 }.minOrNull() ?: 0
        return when {
            choice.value == 0 -> -3.0
            cross > 0 && choice.value >= cross -> 6.0
            else -> 0.0
        }
    }

    // H9: Broadside repeats on a second hero who still has a card.
    private fun confirm(ctx: HContext, d: Decision, choice: Choice): Double {
        if (d.sourceCardId != "cutter-blue-3a" || choice !is Choice.Yes) return 0.0
        val at = ctx.own()?.at ?: return 0.0
        return if (BatchA.enemiesWithin(ctx, at, 4).any { it.hand.isNotEmpty() }) 2.0 else -1.0
    }

    /**
     * The first push distance 1..[max] from [heroAt] (away from [from]) that ends in a different
     * zone, or 0. A push stops at the first obstacle, as the engine's pushAway does.
     */
    private fun zonePush(ctx: HContext, from: Hex, heroAt: Hex, max: Int): Int {
        val dir = HexGrid.straightLineDirection(from, heroAt) ?: return 0
        val start = ctx.zoneOf(heroAt)
        for (i in 1..max) {
            val hex = BatchA.step(heroAt, dir, i)
            if (!ctx.game.isEmpty(hex)) return 0
            if (ctx.zoneOf(hex) != start) return i
        }
        return 0
    }
}

private val PLANK = setOf("cutter-gold", "cutter-handicap")
private val DASH = setOf("cutter-green-1", "cutter-green-2a", "cutter-green-3a")
private val LUNGE = setOf("cutter-red-1", "cutter-red-2a", "cutter-red-3a")
private val BARRAGE = setOf("cutter-blue-1", "cutter-blue-2a", "cutter-blue-3a")
private val DASH_MAX = mapOf("cutter-green-1" to 3, "cutter-green-2a" to 4, "cutter-green-3a" to 5)
private val LUNGE_MAX = mapOf("cutter-red-1" to 1, "cutter-red-2a" to 2, "cutter-red-3a" to 3)
