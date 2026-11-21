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
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.catalog.Stat
import dev.quinton.goa.core.model.match.Team
import kotlin.math.max
import kotlin.math.min

/**
 * Garrus: a fighter who grows with his own discard pile. Gold is +1 Attack per discarded card;
 * the primary red steps to a hero only once a card is in the discard. Chilling Howl cages the
 * enemies around him and can put a resolved card into the discard.
 */
object GarrusHeuristics : HeroHeuristics {
    override val heroId: String = "garrus"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.FIGHTER,
        idealDistance = 1,
        aggression = 0.62,
        // Research: 0.42 (king of the hill). Ablation, strength 8, 96 games: 44.8% with 0.42, 52.1% with 1.0.
        laneWeight = 1.0,
        upgradeOrder = listOf("red", "blue", "green"),
        keepDefender = true,
    )

    override fun adjust(ctx: HContext, d: Decision, choice: Choice): Double = when (d.kind) {
        DecisionKind.PLAY_CARD -> play(ctx, choice)
        DecisionKind.CHOOSE_OPTION -> option(ctx, d, choice)
        else -> 0.0
    }

    /** H4 (eval): the first discards are his gold's Attack and his hunt's step. H1: Howl in force cages. */
    override fun evalTerms(state: GameState, team: Team, heroId: String): Double {
        if (heroId != this.heroId) return 0.0
        val me = state.hero(heroId) ?: return 0.0
        var v = min(BatchASupport.discardCount(state, heroId), 3) * 0.8
        val at = me.at
        if (at != null && BatchASupport.effectLive(state, heroId, HOWL)) {
            v += state.heroes.count { e ->
                e.team != me.team && e.unresolved.isNotEmpty() && e.at?.let { HexGrid.distance(at, it) <= 3 } == true
            } * 1.5
        }
        return BatchASupport.sign(state, team, heroId) * v
    }

    /** H4 (threat): next to him, gold hits for 4 plus his discard pile, not the printed 4. */
    override fun threatAdjust(ctx: HContext, enemyHeroId: String, target: Hex): Double {
        if (enemyHeroId != heroId) return 0.0
        val from = ctx.hero(heroId)?.at ?: return 0.0
        if (ctx.distance(from, target) != 1) return 0.0
        val discards = BatchASupport.discardCount(ctx.state, heroId)
        if (discards < 2) return 0.0
        return min(discards, 4) * 0.5 * BatchASupport.chancePlays(ctx, heroId) { it.id in GOLD }
    }

    private fun play(ctx: HContext, choice: Choice): Double {
        if (choice !is Choice.Card) return 0.0
        val me = ctx.own() ?: return 0.0
        val at = me.at ?: return 0.0
        return when (choice.cardId) {
            in GOLD -> gold(ctx, me, at, choice.cardId)

            in HUNT -> {
                val b = hunt(ctx, me, at, choice.cardId)
                BatchASupport.correction(ctx, me.id, at, ctx.card(choice.cardId), max(b.first, b.second))
            }

            "garrus-silver" -> howl(ctx, me, at)

            in PILUM -> pilum(ctx, at, choice.cardId)

            in STAND -> stand(ctx, me, at, choice.cardId)

            else -> 0.0
        }
    }

    /** H4: gold's Attack is 4 plus the cards in his discard; the general book read the printed 4. */
    private fun gold(ctx: HContext, me: HeroState, at: Hex, cardId: String): Double {
        val card = ctx.card(cardId)
        val total = card.attack + ctx.itemBonus(me.id, Stat.ATTACK) + BatchASupport.discardCount(ctx.state, me.id)
        val v = BatchASupport.correction(
            ctx,
            me.id,
            at,
            card,
            BatchASupport.bestAdjacent(
                ctx,
                me.id,
                at,
                total,
                heroesOnly =
                    cardId == "garrus-handicap",
            ),
        )
        // An empty pile on turn 1 makes it a plain Attack 4: hold it for the second swing.
        return if (ctx.turn == 1 && BatchASupport.discardCount(ctx.state, me.id) == 0) v - 1.5 else v
    }

    /**
     * H5: the hunt's hero bullet steps (1, up to 2, up to 3) only with a card in the discard;
     * without one it reaches only an adjacent hero. The other bullet is any adjacent unit.
     */
    private fun hunt(ctx: HContext, me: HeroState, at: Hex, cardId: String): Pair<Double, Double> {
        val card = ctx.card(cardId)
        val total = card.attack + ctx.itemBonus(me.id, Stat.ATTACK)
        val reach = if (BatchASupport.discardCount(ctx.state, me.id) > 0) 1 + HUNT_STEP.getValue(cardId) else 1
        var hero = 0.0
        for (e in BatchASupport.enemiesWithin(ctx, at, reach)) {
            hero =
                max(hero, BatchASupport.heroHit(ctx, me.id, e.id, total, false))
        }
        return hero to BatchASupport.bestAdjacent(ctx, me.id, at, total)
    }

    /**
     * H1 / H2: Howl cages enemies in radius 3 for the round and may discard a resolved card (fuel
     * for gold and the hunt). Nobody in radius, or turn 4, it is a Defense 3 spent for nothing.
     */
    private fun howl(ctx: HContext, me: HeroState, at: Hex): Double {
        val radius = BatchASupport.radiusOf(ctx, heroId, 3)
        val near = BatchASupport.enemiesWithin(ctx, at, radius)
        if (near.isEmpty()) return -3.0
        if (ctx.turn >= 4) return -2.0
        var v = if (ctx.turn <= 2) 1.5 else 0.5
        val fuel = BatchASupport.discardCount(ctx.state, me.id) == 0 && me.cards.any { it.zone == Zone.RESOLVED }
        if (fuel && ctx.ownHand().any { it.id in GOLD || it.id in HUNT }) v += 1.0
        return v
    }

    /** H10: the pilum makes an enemy hero in range 2 discard (Heavy Pilum: or be defeated). Nobody, it fizzles. */
    private fun pilum(ctx: HContext, at: Hex, cardId: String): Double {
        val orDefeat = cardId == "garrus-green-3b"
        val reach = 2 + ctx.itemBonus(heroId, Stat.RANGE)
        val best = BatchASupport.enemiesWithin(ctx, at, reach).maxOfOrNull {
            BatchASupport.discardValue(ctx, it, orDefeat)
        }
        return if (best == null || best <= 0.0) -3.0 else best - 1.4
    }

    /** H11: Hold Ground retrieves a discard only with two enemy heroes in radius. */
    private fun stand(ctx: HContext, me: HeroState, at: Hex, cardId: String): Double {
        val radius = BatchASupport.radiusOf(ctx, heroId, ctx.card(cardId).radius)
        val ready =
            BatchASupport.enemiesWithin(ctx, at, radius).size >= 2 && BatchASupport.discardCount(ctx.state, me.id) > 0
        return if (ready) 1.0 else -1.0
    }

    /** H5: the hunt's "Choose one —": index 0 the hero (with the step), index 1 the adjacent unit. */
    private fun option(ctx: HContext, d: Decision, choice: Choice): Double {
        val src = d.sourceCardId ?: return 0.0
        if (src !in HUNT || choice !is Choice.Option) return 0.0
        val me = ctx.own() ?: return 0.0
        val at = me.at ?: return 0.0
        val b = hunt(ctx, me, at, src)
        return if (choice.index == 0) b.first else b.second
    }
}

private val HOWL = setOf("garrus-silver")
private val GOLD = setOf("garrus-gold", "garrus-handicap")
private val HUNT = setOf("garrus-red-1", "garrus-red-2a", "garrus-red-3a")
private val PILUM = setOf("garrus-green-2b", "garrus-green-3b")
private val STAND = setOf("garrus-green-1", "garrus-green-2a", "garrus-green-3a")
private val HUNT_STEP = mapOf("garrus-red-1" to 1, "garrus-red-2a" to 2, "garrus-red-3a" to 3)
