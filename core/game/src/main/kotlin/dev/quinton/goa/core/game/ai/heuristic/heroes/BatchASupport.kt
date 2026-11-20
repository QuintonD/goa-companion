package dev.quinton.goa.core.game.ai.heuristic.heroes

import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.game.ai.heuristic.HCard
import dev.quinton.goa.core.game.ai.heuristic.HContext
import dev.quinton.goa.core.game.engine.ActiveEffect
import dev.quinton.goa.core.game.engine.GameState
import dev.quinton.goa.core.game.engine.HeroState
import dev.quinton.goa.core.game.engine.MoveSpec
import dev.quinton.goa.core.game.engine.Timing
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.game.engine.isEmpty
import dev.quinton.goa.core.game.engine.isInBattleZone
import dev.quinton.goa.core.game.engine.isMinionSpawnPoint
import dev.quinton.goa.core.game.engine.isTerrain
import dev.quinton.goa.core.game.engine.reach
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.model.catalog.Stat
import dev.quinton.goa.core.model.match.Team

/**
 * Geometry and public-card lookups for the arien–garrus playbooks.
 * No state. A call reads the decision's [HContext] and returns.
 */
internal object BatchA {
    /** Enemy hero on one of the [n] spaces straight through [through] from [from], nearest first. */
    fun heroBehind(ctx: HContext, from: Hex, through: Hex, n: Int): HeroState? {
        val dir = HexGrid.straightLineDirection(from, through) ?: return null
        val step = HexGrid.DIRECTIONS[dir]
        for (i in 1..n) {
            val hex = Hex(through.q + step.q * i, through.r + step.r * i)
            val hero = ctx.enemies().firstOrNull { it.at == hex }
            if (hero != null) return hero
        }
        return null
    }

    fun enemiesWithin(ctx: HContext, at: Hex, radius: Int): List<HeroState> =
        ctx.enemies().filter { e -> e.at?.let { ctx.distance(at, it) in 1..radius } == true }

    /** A straight-line landing of length [min]..[max] that [end] accepts. The engine's own reach. */
    fun straightLandings(ctx: HContext, heroId: String, min: Int, max: Int, end: (Hex) -> Boolean): Set<Hex> =
        ctx.game.reach(
            MoveSpec(heroId, min, max, straightLine = true, endFilter = end),
        ).keys

    fun straightLandingsIgnoring(
        ctx: HContext,
        heroId: String,
        min: Int,
        max: Int,
        end: (Hex) -> Boolean,
    ): Set<Hex> = ctx.game.reach(
        MoveSpec(heroId, min, max, straightLine = true, ignoreObstacles = true, endFilter = end),
    ).keys

    fun besideEnemyUnit(ctx: HContext, hex: Hex): Boolean {
        if (ctx.enemies().any { e -> e.at?.let { ctx.distance(hex, it) == 1 } == true }) return true
        return ctx.state.minions.any { m ->
            m.team != ctx.team && m.at?.let { ctx.distance(hex, it) == 1 } == true
        }
    }

    fun besideEnemyHero(ctx: HContext, hex: Hex): Boolean =
        ctx.enemies().any { e -> e.at?.let { ctx.distance(hex, it) == 1 } == true }

    /**
     * Crossbow line: nothing but empty spaces between [from] and [to]. Tokens do not break it
     * (Bain's `clearShot`). A pull (`Get over here!`) uses obstacles, tokens included.
     */
    fun clearOfUnits(ctx: HContext, from: Hex, to: Hex): Boolean {
        val line = HexGrid.straightLine(from, to) ?: return false
        return line.dropLast(1).none { hex ->
            ctx.game.isTerrain(hex) || unitAt(ctx, hex)
        }
    }

    fun clearOfObstacles(ctx: HContext, from: Hex, to: Hex): Boolean {
        val line = HexGrid.straightLine(from, to) ?: return false
        return line.dropLast(1).none { hex -> !ctx.game.isEmpty(hex) }
    }

    fun discardCount(ctx: HContext, heroId: String): Int =
        ctx.hero(heroId)?.cards?.count { it.zone == Zone.DISCARD } ?: 0

    fun ultimateUnlocked(state: GameState, heroId: String): Boolean =
        state.hero(heroId)?.cards?.any { it.zone == Zone.ULTIMATE } == true

    fun markerHolder(state: GameState, kind: String): String? =
        state.heroes.firstOrNull { h -> h.markers.any { it.kind == kind } }?.id

    /** Same cases as [dev.quinton.goa.core.game.engine.Game.inForce], for [evalTerms] which has no session. */
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

    /** Cards we may name: the real hand for our team and at strength 10, otherwise the public pool. */
    fun visible(ctx: HContext, heroId: String): List<HCard> {
        ctx.hand(heroId)?.let { return it }
        return ctx.possibleCards(heroId).map { it.card }
    }

    fun played(ctx: HContext, heroId: String): HCard? {
        if (!ctx.revealed && ctx.hero(heroId)?.team != ctx.team) return null
        val id = ctx.hero(heroId)?.unresolved?.firstOrNull()?.id ?: return null
        return ctx.card(id)
    }

    fun poolHas(ctx: HContext, heroId: String, ids: Set<String>): Boolean =
        ctx.possibleCards(heroId).any { it.card.id in ids }

    /**
     * True when every card still hidden or in hand is an attack or a plain move, so a skill
     * cancel has nothing to catch. A revealed non-gold skill is never this.
     */
    fun onlyAttacksOrMoves(ctx: HContext, heroId: String): Boolean {
        val shown = played(ctx, heroId)
        if (shown != null) return !shown.skillCard || shown.color == CardColor.GOLD
        val cards = visible(ctx, heroId)
        if (cards.isEmpty()) return true
        return cards.none { it.skillCard && it.color != CardColor.GOLD }
    }

    fun nonGoldSkillShowing(ctx: HContext, heroId: String): Boolean {
        val shown = played(ctx, heroId)
        if (shown != null) return shown.skillCard && shown.color != CardColor.GOLD
        if (ctx.revealed) return false
        val cards = visible(ctx, heroId)
        val skill = cards.any { it.skillCard && it.color != CardColor.GOLD }
        val attack = cards.any { it.attackCard }
        return skill && !attack
    }

    fun radiusOf(ctx: HContext, card: HCard): Int =
        card.radius + if (card.radius > 0) ctx.itemBonus(ctx.seat, Stat.RADIUS) else 0

    /** Empty Battle Zone minion spawn, or any empty space once [tide] (Dodger's ultimate) is on. */
    fun emptySpawn(ctx: HContext, hex: Hex, tide: Boolean): Boolean {
        if (!ctx.game.geo.isSpace(hex) || !ctx.game.isEmpty(hex)) return false
        return tide || (ctx.game.isMinionSpawnPoint(hex) && ctx.game.isInBattleZone(hex))
    }

    fun besideEmptySpawn(ctx: HContext, at: Hex, tide: Boolean): Boolean =
        HexGrid.neighbors(at).any { emptySpawn(ctx, it, tide) }

    fun emptySpawnsWithin(ctx: HContext, at: Hex, radius: Int, tide: Boolean): Int =
        HexGrid.within(at, radius).count { emptySpawn(ctx, it, tide) }

    fun sign(state: GameState, team: Team, heroId: String): Double {
        val owner = state.hero(heroId)?.team ?: return 0.0
        return if (owner == team) 1.0 else -1.0
    }

    /** Our own hand is real. An enemy's specific hand card is not, so this is false for them. */
    fun ownHandHas(state: GameState, team: Team, heroId: String, ids: Set<String>): Boolean {
        val h = state.hero(heroId) ?: return false
        if (h.team != team) return false
        return h.hand.any { it.id in ids }
    }

    private fun unitAt(ctx: HContext, hex: Hex): Boolean =
        ctx.state.heroes.any { it.at == hex } || ctx.state.minions.any { it.at == hex }
}
