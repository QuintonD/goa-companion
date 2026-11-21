package dev.quinton.goa.core.game.ai.heuristic.heroes

import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.ai.heuristic.HCard
import dev.quinton.goa.core.game.ai.heuristic.HContext
import dev.quinton.goa.core.game.engine.ActiveEffect
import dev.quinton.goa.core.game.engine.GameState
import dev.quinton.goa.core.game.engine.HeroState
import dev.quinton.goa.core.game.engine.PieceState
import dev.quinton.goa.core.game.engine.Timing
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.game.engine.isEmpty
import dev.quinton.goa.core.game.engine.isInBattleZone
import dev.quinton.goa.core.game.engine.isMinionSpawnPoint
import dev.quinton.goa.core.game.engine.isTerrain
import dev.quinton.goa.core.game.heroes.TigerclawHero
import dev.quinton.goa.core.game.heroes.TrinketsHero
import dev.quinton.goa.core.game.heroes.WidgetHero
import dev.quinton.goa.core.game.heroes.WukHero
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.model.catalog.Stat
import dev.quinton.goa.core.model.match.Team
import kotlin.math.max
import kotlin.math.min

/**
 * Lookups for the tigerclaw–xargatha playbooks (batch D). No engine forks: distances, occupancy and
 * the public pieces the engine already stores.
 *
 * The general book scores a card's attack from its printed reach ([HContext.strikeFrom]). Many of
 * these heroes hit only a narrower set (a straight line, a unit beside a tree, an empty discard).
 * [strikeFix] re-scores the card's outlook on the units the text can really target, so a playbook
 * corrects the general score instead of piling a flat penalty on top of it.
 */
internal object BatchDSupport {
    fun pieces(state: GameState, kind: String): List<PieceState> =
        state.pieces.filter { it.kind == kind && it.at != null }

    fun turret(state: GameState): Hex? = pieces(state, TrinketsHero.TURRET).firstOrNull()?.at

    fun pyro(state: GameState): Hex? = pieces(state, WidgetHero.PYRO).firstOrNull()?.at

    fun trees(state: GameState): List<Hex> = pieces(state, WukHero.TREE).map { it.at!! }

    /** [Game.inForce] from the state alone, for [evalTerms] which has no engine. */
    fun inForce(state: GameState, e: ActiveEffect): Boolean {
        if (e.spent) return false
        return when (e.timing) {
            Timing.THIS_TURN -> e.round == state.round && e.turn == state.turn
            Timing.THIS_ROUND -> e.round == state.round
            Timing.NEXT_TURN -> e.round == state.round && state.turn == e.turn + 1
            Timing.UNTIL_CANCELLED -> true
            else -> false
        }
    }

    /** An effect of [cardIds] owned by [heroId] that is in force now, or (when [pending]) will be next turn. */
    fun effect(state: GameState, heroId: String, cardIds: Set<String>, pending: Boolean = false): Boolean =
        state.effects.any { e ->
            e.owner == heroId && e.cardId in cardIds &&
                (inForce(state, e) || (pending && !e.spent && e.round == state.round && e.timing == Timing.NEXT_TURN))
        }

    fun sign(state: GameState, heroId: String, team: Team): Double =
        if (state.hero(heroId)?.team == team) 1.0 else -1.0

    // ---- board ---------------------------------------------------------------------------------

    fun besideTerrain(ctx: HContext, at: Hex): Boolean = HexGrid.neighbors(at).any { ctx.game.isTerrain(it) }

    /** Enemy heroes and minions adjacent to [at], from [team]'s side. */
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

    fun enemyHeroesWithin(ctx: HContext, at: Hex, min: Int, max: Int): List<HeroState> =
        ctx.enemies().filter { e -> e.at?.let { ctx.distance(at, it) in min..max } == true }

    fun discardEmpty(hero: HeroState): Boolean = hero.cards.none { it.zone == Zone.DISCARD }

    fun resolvedSkill(ctx: HContext, hero: HeroState): Boolean =
        hero.cards.any { it.zone == Zone.RESOLVED && !it.faceDown && ctx.card(it.id).skillCard }

    /** Empty minion spawn points in the battle zone within [radius] of [at] (Whisper's steps and places). */
    fun emptyMinionSpawns(ctx: HContext, at: Hex, radius: Int): List<Hex> =
        HexGrid.within(at, radius).filter { hex ->
            ctx.game.isMinionSpawnPoint(hex) && ctx.game.isEmpty(hex) && ctx.game.isInBattleZone(hex)
        }

    fun minionSpawn(ctx: HContext, hex: Hex): Boolean = ctx.game.isMinionSpawnPoint(hex)

    fun poisonStrength(hero: HeroState): Int =
        hero.markers.firstOrNull { it.kind == TigerclawHero.POISON }?.data?.get("n")?.toIntOrNull() ?: 0

    // ---- knowledge -----------------------------------------------------------------------------

    /**
     * Public knowledge allows [heroId] to still hold [cardId] (the real hand for us and at strength 10),
     * or it is the card they revealed this turn and have still to resolve: a threat either way.
     */
    fun mayHold(ctx: HContext, heroId: String, cardId: String): Boolean =
        ctx.possibleCards(heroId).any { it.card.id == cardId }

    fun holds(ctx: HContext, cardIds: Set<String>): Boolean = ctx.ownHand().any { it.id in cardIds }

    fun enemyGoldInit(ctx: HContext): Int {
        var best = 0
        for (e in ctx.enemies()) {
            for (p in ctx.possibleCards(e.id)) {
                if (p.card.color == CardColor.GOLD && p.card.initiative > best) best = p.card.initiative
            }
        }
        return best
    }

    /** True when the attacks the enemies might still hold are at least half ranged. */
    fun mostlyRanged(ctx: HContext): Boolean {
        var ranged = 0.0
        var melee = 0.0
        for (e in ctx.enemies()) {
            for (p in ctx.possibleCards(e.id)) {
                if (!p.card.attackCard) continue
                if (p.card.ranged) ranged += p.weight else melee += p.weight
            }
        }
        return ranged >= melee
    }

    /** The hero the threat map is asking about: whoever stands on [target], else the deciding hero. */
    fun victimAt(ctx: HContext, enemyHeroId: String, target: Hex): HeroState? {
        val enemyTeam = ctx.state.hero(enemyHeroId)?.team ?: return null
        ctx.state.heroes.firstOrNull { it.at == target && it.team != enemyTeam }?.let { return it }
        return ctx.own()?.takeIf { it.team != enemyTeam }
    }

    // ---- scoring -------------------------------------------------------------------------------

    /**
     * Best target worth among enemy units [valid] for, the way [HContext.strikeFrom] scores one:
     * a minion's worth, a hero's strike worth scaled by aggression. -0.5 when nothing qualifies.
     * [bonus] is extra Attack the text gives against that target.
     */
    fun strikeOn(
        ctx: HContext,
        card: HCard,
        valid: (id: String, at: Hex) -> Boolean,
        bonus: (id: String) -> Int = { 0 },
    ): Double {
        var best = -0.5
        for (m in ctx.state.minions) {
            val pos = m.at ?: continue
            if (m.team == ctx.team || !valid(m.id, pos)) continue
            best = max(best, ctx.minionWorth(m.id))
        }
        val total = card.attack + ctx.itemBonus(ctx.seat, Stat.ATTACK)
        val agg = 0.65 + 0.7 * ctx.style(ctx.seat).aggression
        for (e in ctx.enemies()) {
            val pos = e.at ?: continue
            if (!valid(e.id, pos)) continue
            best = max(best, ctx.heroStrikeWorth(e.id, total + bonus(e.id), card.ranged) * agg)
        }
        return best
    }

    /** All target worths, best first (for a repeat on a second unit). */
    fun strikeList(ctx: HContext, card: HCard, valid: (id: String, at: Hex) -> Boolean): List<Pair<String, Double>> {
        val out = ArrayList<Pair<String, Double>>()
        for (m in ctx.state.minions) {
            val pos = m.at ?: continue
            if (m.team == ctx.team || !valid(m.id, pos)) continue
            out += m.id to ctx.minionWorth(m.id)
        }
        val total = card.attack + ctx.itemBonus(ctx.seat, Stat.ATTACK)
        val agg = 0.65 + 0.7 * ctx.style(ctx.seat).aggression
        for (e in ctx.enemies()) {
            val pos = e.at ?: continue
            if (!valid(e.id, pos)) continue
            out += e.id to ctx.heroStrikeWorth(e.id, total, card.ranged) * agg
        }
        return out.sortedByDescending { it.second }
    }

    /**
     * The change to the general PLAY_CARD outlook when the card's real strike is [real] instead of
     * the printed-reach guess. The general outlook is the best of strike and movement, so a card that
     * cannot hit is still worth its move.
     */
    fun strikeFix(ctx: HContext, card: HCard, real: Double): Double {
        if (!ctx.patches) return 0.0
        val at = ctx.own()?.at ?: return 0.0
        val guess = ctx.strikeFrom(ctx.seat, at, card)
        val move = if (card.hasMovement) moveOutlook(ctx, at, card) else 0.0
        return max(real, move) - max(guess, move)
    }

    /**
     * A skill's real value [real] in place of the general outlook. A card that forces a discard is scored by the
     * general book as a strike on any unit in reach, so that guess is replaced; any other skill just adds [real].
     */
    fun outlookFix(ctx: HContext, card: HCard, real: Double): Double =
        if (card.attackCard || card.forcesDiscard) strikeFix(ctx, card, real) else real

    /** [General]'s movement outlook for a card (kept in step by hand: it is private there). */
    private fun moveOutlook(ctx: HContext, at: Hex, card: HCard): Double {
        val far = ctx.distanceToBattleZone(at)
        if (far <= 0) return 0.15
        return min(far * 0.4, card.movement * 0.55)
    }

    /**
     * A space question that places a piece, not this hero. The general book scores those as if the
     * hero stood there; [unplace] is the amount to take back so the playbook's own site value counts.
     */
    fun unplace(ctx: HContext, d: Decision, hex: Hex): Double {
        // The general book scores a piece's space as no hero's (HContext.placedHero): nothing to take back.
        if (ctx.profile.on("placeProbe", true)) return 0.0
        if (d.kind != DecisionKind.CHOOSE_SPACE || d.context.subjectId != null) return 0.0
        return -ctx.placement(d.seat, hex)
    }
}
