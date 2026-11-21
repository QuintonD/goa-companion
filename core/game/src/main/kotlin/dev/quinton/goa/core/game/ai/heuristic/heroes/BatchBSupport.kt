package dev.quinton.goa.core.game.ai.heuristic.heroes

import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.ai.heuristic.HCard
import dev.quinton.goa.core.game.ai.heuristic.HContext
import dev.quinton.goa.core.game.engine.GameState
import dev.quinton.goa.core.game.engine.PieceState
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.game.engine.isTerrain
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.model.match.Team

/**
 * Lookups for this batch only. Hands stay behind [HContext.hand]: null means the pool, not the sample.
 */
internal object BatchBSupport {
    fun cardId(choice: Choice): String? = (choice as? Choice.Card)?.cardId

    fun counter(state: GameState, heroId: String, name: String): Int =
        state.hero(heroId)?.counters?.get(name) ?: 0

    fun tokens(state: GameState, owner: String, kind: String): List<PieceState> =
        state.pieces.filter { it.owner == owner && it.kind == kind && it.at != null }

    fun effect(state: GameState, owner: String, cardId: String): Boolean =
        state.effects.any { it.owner == owner && it.cardId == cardId && !it.spent }

    /** Faceup resolved or discarded card. That pile is public. */
    fun shown(state: GameState, heroId: String, predicate: (String) -> Boolean): Boolean {
        val hero = state.hero(heroId) ?: return false
        return hero.cards.any { card ->
            predicate(card.id) && !card.faceDown &&
                (card.zone == Zone.RESOLVED || card.zone == Zone.DISCARD)
        }
    }

    /**
     * Weight of a card [heroId] may hold. A known hand (own team, or strength 10) is 0 or 1.
     * Otherwise the public pool: one colour sums to about 1.
     */
    fun hold(ctx: HContext, heroId: String, predicate: (HCard) -> Boolean): Double {
        val known = ctx.hand(heroId)
        if (known != null) return if (known.any(predicate)) 1.0 else 0.0
        return ctx.possibleCards(heroId).filter { predicate(it.card) }.sumOf { it.weight }.coerceAtMost(1.0)
    }

    fun enemyMinionWithin(ctx: HContext, at: Hex, reach: Int): Boolean =
        ctx.state.minions.any { minion ->
            minion.team != ctx.team && minion.at != null && ctx.distance(at, minion.at!!) <= reach
        }

    fun enemyHeroWithin(ctx: HContext, at: Hex, reach: Int): Boolean =
        ctx.enemies().any { enemy -> enemy.at?.let { ctx.distance(at, it) <= reach } == true }

    fun lifeSpent(ctx: HContext): Boolean {
        val lanes = ctx.state.battleZones.size.coerceAtLeast(1)
        val start = ctx.session.engineData.lifeCounters(lanes, ctx.session.setup.length, ctx.state.heroes.size)
        return ctx.life(ctx.team) < start
    }

    /** The faceup card on the previous turn slot, or null on turn 1 and for a facedown slot. */
    fun previous(ctx: HContext, heroId: String): HCard? {
        val slot = ctx.turn - 1
        if (slot < 1) return null
        val hero = ctx.state.hero(heroId) ?: return null
        val card = hero.cards.firstOrNull { it.zone == Zone.RESOLVED && it.slot == slot && !it.faceDown }
        return card?.let { ctx.card(it.id) }
    }

    fun touchesTerrain(ctx: HContext, at: Hex): Boolean =
        HexGrid.neighbors(at).any { ctx.game.isTerrain(it) }

    fun besideToken(state: GameState, at: Hex, kind: String): Boolean =
        state.pieces.any { piece ->
            piece.kind == kind && piece.at != null && HexGrid.distance(at, piece.at!!) == 1
        }

    fun allyBeside(ctx: HContext, at: Hex): Boolean =
        ctx.allies().any { ally -> ally.at?.let { HexGrid.distance(it, at) == 1 } == true }

    fun sign(state: GameState, team: Team, heroId: String): Double =
        if (state.hero(heroId)?.team == team) 1.0 else -1.0

    fun basicAttack(card: HCard): Boolean =
        card.attackCard && (card.color == CardColor.GOLD || card.color == CardColor.SILVER)

    /**
     * Cancel the general book's strike estimate for [card] when its text has no legal target. The
     * general book reads printed reach; a card such as Hanu's red reaches far only beside an ally.
     */
    fun deadStrike(ctx: HContext, at: Hex, card: HCard): Double {
        val strike = ctx.strikeFrom(ctx.seat, at, card).coerceAtLeast(0.0)
        // The general outlook is max(strike, move) (+0.35 for both): keep the move, drop the strike.
        val move = if (card.hasMovement) moveOutlook(ctx, at, card) else 0.0
        val both = if (strike > 0.5 && move > 0.3) 0.35 else 0.0
        return -(maxOf(strike, move) - move) - both
    }

    /** The general book's movement outlook (General.moveOutlook), which a hero file cannot call. */
    private fun moveOutlook(ctx: HContext, at: Hex, card: HCard): Double {
        val far = ctx.distanceToBattleZone(at)
        if (far <= 0) return 0.15
        return minOf(far * 0.4, card.movement * 0.55)
    }

    /** Enemy units (heroes and minions) by id and space. */
    fun enemyUnits(ctx: HContext): List<Pair<String, Hex>> {
        val out = ArrayList<Pair<String, Hex>>()
        for (e in ctx.enemies()) e.at?.let { out += e.id to it }
        for (m in ctx.state.minions) if (m.team != ctx.team) m.at?.let { out += m.id to it }
        return out
    }

    /** The best minion worth among enemy minions at [at]'s distance [range] or less (0 when none). */
    fun bestMinion(ctx: HContext, at: Hex, range: Int): Double {
        var best = 0.0
        for (m in ctx.state.minions) {
            val pos = m.at ?: continue
            if (m.team == ctx.team || ctx.distance(at, pos) > range || ctx.heavyImmune(m.id)) continue
            best = maxOf(best, ctx.minionWorth(m.id))
        }
        return best
    }
}
