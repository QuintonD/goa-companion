package dev.quinton.goa.core.game.ai.heuristic.heroes

import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.game.ai.heuristic.HCard
import dev.quinton.goa.core.game.ai.heuristic.HContext
import dev.quinton.goa.core.game.engine.ActiveEffect
import dev.quinton.goa.core.game.engine.GameState
import dev.quinton.goa.core.game.engine.HeroState
import dev.quinton.goa.core.game.engine.Timing
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.game.engine.isEmpty
import dev.quinton.goa.core.game.engine.isInBattleZone
import dev.quinton.goa.core.game.engine.isMinionSpawnPoint
import dev.quinton.goa.core.game.engine.isTerrain
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.model.catalog.Stat
import dev.quinton.goa.core.model.match.Team
import kotlin.math.max
import kotlin.math.pow

/**
 * Shared lookups for the arien–garrus playbooks (ADR-085). No state: a call reads the
 * decision's [HContext] (or a [GameState] for evalTerms) and returns.
 *
 * Most hero notes here are a *correction* of the general book's strike estimate: the general
 * book reads a card's printed reach, and these heroes' cards add a condition (a straight line,
 * a charge landing, a discard pile, a crowd). [correction] swaps the general estimate for the
 * one the card text allows, so a note does not count the same minion twice.
 */
internal object BatchA {
    /** The hex [k] steps from [from] in direction [dir] (an index into [HexGrid.DIRECTIONS]). */
    fun step(from: Hex, dir: Int, k: Int): Hex {
        val d = HexGrid.DIRECTIONS[dir]
        return Hex(from.q + d.q * k, from.r + d.r * k)
    }

    /** The general book's scale on a hero hit (`HContext.strikeFrom`). */
    fun aggression(ctx: HContext, heroId: String): Double = 0.65 + 0.7 * ctx.style(heroId).aggression

    /** What the general book thinks [card] lands from [at]: the number a correction replaces. */
    fun generalStrike(ctx: HContext, heroId: String, at: Hex, card: HCard): Double =
        max(0.0, ctx.strikeFrom(heroId, at, card))

    /** Replace the general strike estimate by [accurate]. */
    fun correction(ctx: HContext, heroId: String, at: Hex, card: HCard, accurate: Double): Double =
        max(0.0, accurate) - generalStrike(ctx, heroId, at, card)

    /** An enemy hero hit with [total], as the general book values it for [heroId]. */
    fun heroHit(ctx: HContext, heroId: String, targetId: String, total: Int, ranged: Boolean): Double =
        ctx.heroStrikeWorth(targetId, total, ranged) * aggression(ctx, heroId)

    /** Best enemy minion standing on one of [hexes]; immune heavies excluded. */
    fun bestMinionOn(ctx: HContext, hexes: Collection<Hex>): Double {
        var best = 0.0
        for (m in ctx.state.minions) {
            val pos = m.at ?: continue
            if (m.team == ctx.team || ctx.heavyImmune(m.id) || pos !in hexes) continue
            best = max(best, ctx.minionWorth(m.id))
        }
        return best
    }

    /** Best adjacent enemy unit for a plain adjacent attack of [total]. */
    fun bestAdjacent(ctx: HContext, heroId: String, at: Hex, total: Int, heroesOnly: Boolean = false): Double {
        var best = 0.0
        if (!heroesOnly) best = bestMinionOn(ctx, HexGrid.neighbors(at).toSet())
        for (e in ctx.enemies()) {
            val pos = e.at ?: continue
            if (ctx.distance(at, pos) == 1) best = max(best, heroHit(ctx, heroId, e.id, total, false))
        }
        return best
    }

    /**
     * A forced discard on [h]: the card it costs, or the defeat when [orDefeat] and the hand is
     * empty. "If able" on an empty hand is worth nothing.
     */
    fun discardValue(ctx: HContext, h: HeroState, orDefeat: Boolean): Double = when {
        h.hand.isNotEmpty() -> ctx.discardWorth(h)
        orDefeat -> ctx.killWorth(h.id)
        else -> 0.0
    }

    fun enemiesWithin(ctx: HContext, at: Hex, radius: Int): List<HeroState> =
        ctx.enemies().filter { e -> e.at?.let { ctx.distance(at, it) in 1..radius } == true }

    fun besideEnemyUnit(ctx: HContext, hex: Hex): Boolean {
        if (besideEnemyHero(ctx, hex)) return true
        return ctx.state.minions.any { m ->
            m.team != ctx.team && m.at?.let { ctx.distance(hex, it) == 1 } == true
        }
    }

    fun besideEnemyHero(ctx: HContext, hex: Hex): Boolean =
        ctx.enemies().any { e -> e.at?.let { ctx.distance(hex, it) == 1 } == true }

    /** No unit and no terrain strictly between [from] and [to] on a straight line (a crossbow line). */
    fun clearOfUnits(ctx: HContext, from: Hex, to: Hex): Boolean {
        val line = HexGrid.straightLine(from, to) ?: return false
        return line.dropLast(1).none { hex -> ctx.game.isTerrain(hex) || unitAt(ctx, hex) }
    }

    /** No obstacle strictly between (tokens count). [mover] is never its own obstacle. */
    fun clearOfObstacles(ctx: HContext, from: Hex, to: Hex, mover: String? = null): Boolean {
        val line = HexGrid.straightLine(from, to) ?: return false
        return line.dropLast(1).none { hex -> !ctx.game.isEmpty(hex, mover) }
    }

    fun discardCount(state: GameState, heroId: String): Int =
        state.hero(heroId)?.cards?.count { it.zone == Zone.DISCARD } ?: 0

    fun ultimateUnlocked(state: GameState, heroId: String): Boolean =
        state.hero(heroId)?.cards?.any { it.zone == Zone.ULTIMATE } == true

    fun markerHolder(state: GameState, kind: String): String? =
        state.heroes.firstOrNull { h -> h.markers.any { it.kind == kind } }?.id

    /** The same cases as `Game.inForce`, for [evalTerms] which has no session. */
    fun live(state: GameState, effect: ActiveEffect): Boolean {
        if (effect.spent) return false
        return when (effect.timing) {
            Timing.THIS_TURN -> effect.round == state.round && effect.turn == state.turn
            Timing.THIS_ROUND -> effect.round == state.round
            Timing.NEXT_TURN -> effect.round == state.round && state.turn == effect.turn + 1
            Timing.UNTIL_CANCELLED -> true
            else -> false
        }
    }

    fun effectLive(ctx: HContext, owner: String, cardIds: Set<String>): Boolean =
        ctx.state.effects.any { it.owner == owner && it.cardId in cardIds && ctx.game.inForce(it) }

    fun effectLive(state: GameState, owner: String, cardIds: Set<String>): Boolean =
        state.effects.any { it.owner == owner && it.cardId in cardIds && live(state, it) }

    /** The played card we may read: our own team's, or anyone's once cards are revealed. */
    fun played(ctx: HContext, heroId: String): HCard? {
        val h = ctx.hero(heroId) ?: return null
        if (!ctx.revealed && h.team != ctx.team) return null
        val id = h.unresolved.firstOrNull()?.id ?: return null
        return ctx.card(id)
    }

    fun poolHas(ctx: HContext, heroId: String, ids: Set<String>): Boolean =
        ctx.possibleCards(heroId).any { it.card.id in ids }

    /**
     * The chance [heroId]'s card this turn satisfies [test]. The played card once revealed (or a
     * teammate's), else the hand when we may see it, else the public pool: each colour's weights
     * sum to about one, and one colour is played.
     */
    fun chancePlays(ctx: HContext, heroId: String, test: (HCard) -> Boolean): Double {
        played(ctx, heroId)?.let { return if (test(it)) 1.0 else 0.0 }
        ctx.hand(heroId)?.let { hand ->
            if (hand.isEmpty()) return 0.0
            return hand.count(test).toDouble() / hand.size
        }
        val pool = ctx.possibleCards(heroId)
        if (pool.isEmpty()) return 0.0
        val colours = pool.map { it.card.color }.distinct().size.coerceAtLeast(1)
        return (pool.filter { test(it.card) }.sumOf { it.weight } / colours).coerceIn(0.0, 1.0)
    }

    /**
     * The chance [h] holds at least one card passing [test] among its [n] hand cards, from what
     * we may see. Used where the hand, not the played card, matters (a defense).
     */
    fun chanceHolds(ctx: HContext, h: HeroState, test: (HCard) -> Boolean): Double {
        ctx.hand(h.id)?.let { hand -> return if (hand.any(test)) 1.0 else 0.0 }
        val n = h.hand.size
        if (n == 0) return 0.0
        val pool = ctx.possibleCards(h.id)
        if (pool.isEmpty()) return 0.5
        val total = pool.sumOf { it.weight }.coerceAtLeast(1e-6)
        val frac = (pool.filter { test(it.card) }.sumOf { it.weight } / total).coerceIn(0.0, 1.0)
        return 1.0 - (1.0 - frac).pow(n)
    }

    fun radiusOf(ctx: HContext, heroId: String, printed: Int): Int = printed + ctx.itemBonus(heroId, Stat.RADIUS)

    /** Dodger's test: an empty Battle Zone minion spawn, or any empty space with Tide of Darkness. */
    fun emptySpawn(ctx: HContext, hex: Hex, tide: Boolean): Boolean {
        if (!ctx.game.geo.isSpace(hex) || !ctx.game.isEmpty(hex)) return false
        return tide || (ctx.game.isMinionSpawnPoint(hex) && ctx.game.isInBattleZone(hex))
    }

    fun besideEmptySpawn(ctx: HContext, at: Hex, tide: Boolean): Boolean =
        HexGrid.neighbors(at).any { emptySpawn(ctx, it, tide) }

    fun emptySpawnsWithin(ctx: HContext, at: Hex, radius: Int, tide: Boolean): Int =
        HexGrid.within(at, radius).count { emptySpawn(ctx, it, tide) }

    /** +1 when [heroId] is on [team], −1 when not. */
    fun sign(state: GameState, team: Team, heroId: String): Double {
        val owner = state.hero(heroId)?.team ?: return 0.0
        return if (owner == team) 1.0 else -1.0
    }

    /** A non-gold skill, the kind Arien's Spell Break stops. */
    fun nonGoldSkill(card: HCard): Boolean = card.skillCard && !card.attackCard && card.color != CardColor.GOLD

    private fun unitAt(ctx: HContext, hex: Hex): Boolean =
        ctx.state.heroes.any { it.at == hex } || ctx.state.minions.any { it.at == hex }
}
