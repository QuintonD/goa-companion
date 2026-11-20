package dev.quinton.goa.core.game.ai.heuristic.heroes

import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.game.ai.heuristic.HContext
import dev.quinton.goa.core.game.engine.GameState
import dev.quinton.goa.core.game.engine.HeroState
import dev.quinton.goa.core.game.engine.PieceState
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.game.engine.isEmpty
import dev.quinton.goa.core.game.engine.isTerrain
import dev.quinton.goa.core.game.heroes.TigerclawHero
import dev.quinton.goa.core.game.heroes.TrinketsHero
import dev.quinton.goa.core.game.heroes.UrsafarHero
import dev.quinton.goa.core.game.heroes.WidgetHero
import dev.quinton.goa.core.game.heroes.WukHero
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.model.match.Team

/**
 * Lookups for the tigerclaw–xargatha playbooks. No engine forks: distances, occupancy and the
 * public pieces the engine already stores. A primary the profile will overvalue because printed
 * reach is wider than the text is scored [FIZZLE], which is larger than one minion.
 */
internal object BatchD {
    const val FIZZLE: Double = -28.0

    fun piece(state: GameState, kind: String): PieceState? =
        state.pieces.firstOrNull { it.kind == kind && it.at != null }

    fun pieces(state: GameState, kind: String): List<PieceState> =
        state.pieces.filter { it.kind == kind && it.at != null }

    fun turret(state: GameState): Hex? = piece(state, TrinketsHero.TURRET)?.at

    fun pyro(state: GameState): Hex? = piece(state, WidgetHero.PYRO)?.at

    fun trees(state: GameState): List<Hex> = pieces(state, WukHero.TREE).map { it.at!! }

    /** Blink Strike's line: an enemy unit one step away and an empty hex on the far side. */
    fun blink(ctx: HContext, from: Hex, team: Team, heroId: String, heroOnly: Boolean): Boolean {
        for (dir in HexGrid.DIRECTIONS) {
            val mid = from + dir
            val id = ctx.game.occupant(mid) ?: continue
            if (!isEnemyUnit(ctx, id, team)) continue
            if (heroOnly && ctx.state.hero(id) == null) continue
            if (ctx.game.isEmpty(mid + dir, heroId)) return true
        }
        return false
    }

    fun besideTerrain(ctx: HContext, at: Hex): Boolean =
        HexGrid.neighbors(at).any { ctx.game.isTerrain(it) }

    fun isEnemyUnit(ctx: HContext, id: String, team: Team): Boolean {
        ctx.state.hero(id)?.let { return it.team != team }
        ctx.state.minion(id)?.let { return it.team != team }
        val body = ctx.state.piece(id) ?: return false
        return body.unit && body.team != null && body.team != team
    }

    fun adjacentEnemies(state: GameState, at: Hex, team: Team): Int {
        var n = 0
        for (h in state.heroes) if (h.team != team && h.at != null && HexGrid.distance(at, h.at!!) == 1) n++
        for (m in state.minions) if (m.team != team && m.at != null && HexGrid.distance(at, m.at!!) == 1) n++
        return n
    }

    fun adjacentMinions(state: GameState, at: Hex, team: Team, type: MinionType? = null): Int =
        state.minions.count { m ->
            m.team != team && m.at != null && HexGrid.distance(at, m.at!!) == 1 && (type == null || m.type == type)
        }

    fun enemyWithin(ctx: HContext, at: Hex, min: Int, max: Int, heroesOnly: Boolean = false): Boolean {
        if (ctx.enemies().any { e -> e.at?.let { ctx.distance(at, it) in min..max } == true }) return true
        if (heroesOnly) return false
        return ctx.state.minions.any { m ->
            m.team != ctx.team && m.at != null && ctx.distance(at, m.at!!) in min..max
        }
    }

    /** A unit in range that is not on one of the six lines. Adjacent is always a line. */
    fun offLine(ctx: HContext, at: Hex, range: Int): Boolean {
        if (range < 2) return false
        if (ctx.enemies().any { e -> off(ctx, at, e.at, range) }) return true
        return ctx.state.minions.any { m -> m.team != ctx.team && off(ctx, at, m.at, range) }
    }

    fun discardEmpty(hero: HeroState): Boolean = hero.cards.none { it.zone == Zone.DISCARD }

    fun resolvedSkill(ctx: HContext, hero: HeroState): Boolean =
        hero.cards.any { it.zone == Zone.RESOLVED && !it.faceDown && ctx.card(it.id).skillCard }

    fun enraged(state: GameState, heroId: String): Boolean {
        val hero = state.hero(heroId) ?: return false
        val fury = hero.at != null && hero.cards.any { it.id == "ursafar-purple-4" && it.zone == Zone.ULTIMATE }
        if (fury) return true
        return state.effects.any { it.owner == heroId && it.key == UrsafarHero.ENRAGED && !it.spent }
    }

    fun mayHold(ctx: HContext, heroId: String, cardId: String): Boolean =
        ctx.possibleCards(heroId).any { it.card.id == cardId }

    fun enemyGoldInit(ctx: HContext): Int {
        var best = 0
        for (e in ctx.enemies()) {
            for (p in ctx.possibleCards(e.id)) {
                if (p.card.color == CardColor.GOLD && p.card.initiative > best) best = p.card.initiative
            }
        }
        return best
    }

    /** True when the attacks they might still hold are at least half ranged. */
    fun mostlyRanged(ctx: HContext): Boolean {
        var ranged = 0
        var melee = 0
        for (e in ctx.enemies()) {
            for (p in ctx.possibleCards(e.id)) {
                if (!p.card.attackCard) continue
                if (p.card.ranged) ranged++ else melee++
            }
        }
        return ranged >= melee
    }

    fun emptyMinionSpawns(ctx: HContext, at: Hex, radius: Int): Int {
        var n = 0
        for (hex in HexGrid.within(at, radius)) {
            if (!ctx.inBattleZone(hex)) continue
            val spawn = ctx.game.geo.spaces[hex]?.spawn ?: continue
            if (spawn.kind.minion == null || !ctx.game.isEmpty(hex)) continue
            n++
        }
        return n
    }

    fun poisonStrength(hero: HeroState): Int =
        hero.markers.firstOrNull { it.kind == TigerclawHero.POISON }?.data?.get("n")?.toIntOrNull() ?: 0

    private fun off(ctx: HContext, from: Hex, at: Hex?, range: Int): Boolean {
        if (at == null) return false
        val d = ctx.distance(from, at)
        return d in 2..range && !HexGrid.inStraightLine(from, at)
    }
}
