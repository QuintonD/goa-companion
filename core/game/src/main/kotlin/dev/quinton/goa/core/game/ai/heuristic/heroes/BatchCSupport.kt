package dev.quinton.goa.core.game.ai.heuristic.heroes

import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.ai.heuristic.HContext
import dev.quinton.goa.core.game.engine.GameState
import dev.quinton.goa.core.game.engine.HeroState
import dev.quinton.goa.core.game.engine.Marker
import dev.quinton.goa.core.game.engine.MinionState
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.game.heroes.SnorriHero
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.match.Team

/**
 * Lookups shared by this batch of hero playbooks. No rules: each hero file names its own H-rules.
 * Reads the deciding view only. A hidden enemy hand stays in [HContext.possibleCards].
 */
internal object BatchC {
    fun cardId(choice: Choice): String? = (choice as? Choice.Card)?.cardId

    fun unitId(choice: Choice): String? = (choice as? Choice.Unit)?.unitId

    fun hexOf(choice: Choice): Hex? = (choice as? Choice.Space)?.hex

    fun inHand(h: HeroState, id: String): Boolean = h.card(id)?.zone == Zone.HAND

    /** On the dashboard already: resolved, discarded, or tucked. Not a peek at the hand. */
    fun onDashboard(h: HeroState, id: String): Boolean {
        val zone = h.card(id)?.zone ?: return false
        return zone == Zone.RESOLVED || zone == Zone.DISCARD || zone == Zone.ITEM
    }

    fun bodies(state: GameState, heroId: String): List<Hex> {
        val self = state.hero(heroId)?.at
        val copies = state.pieces.mapNotNull { piece ->
            piece.at?.takeIf { piece.heroBody == heroId }
        }
        return listOfNotNull(self) + copies
    }

    fun rightmost(h: HeroState): String? =
        h.cards.filter { it.zone == Zone.RESOLVED && !it.faceDown }.maxByOrNull { it.slot }?.id

    fun activeRune(h: HeroState, turn: Int): SnorriHero.Rune? {
        for (marker in h.markers) {
            if (marker.data[Marker.SLOT]?.toIntOrNull() != turn) continue
            return SnorriHero.Rune.entries.firstOrNull { it.kind == marker.kind }
        }
        return null
    }

    fun runeCount(h: HeroState): Int =
        h.markers.count { marker -> SnorriHero.Rune.entries.any { it.kind == marker.kind } }

    fun sign(state: GameState, heroId: String, team: Team): Double =
        if (state.hero(heroId)?.team == team) 1.0 else -1.0

    fun enemiesWithin(ctx: HContext, at: Hex, radius: Int): List<HeroState> =
        ctx.enemies().filter { enemy -> enemy.at?.let { ctx.distance(at, it) in 1..radius } == true }

    fun alliesWithin(ctx: HContext, at: Hex, radius: Int): List<HeroState> =
        ctx.allies().filter { ally -> ally.at?.let { ctx.distance(at, it) in 1..radius } == true }

    fun minionsWithin(ctx: HContext, at: Hex, radius: Int, friendly: Boolean): List<MinionState> =
        ctx.state.minions.filter { minion ->
            val pos = minion.at ?: return@filter false
            val mine = minion.team == ctx.team
            mine == friendly && ctx.distance(at, pos) in 1..radius
        }

    fun threatened(ctx: HContext, at: Hex): Boolean =
        ctx.threatsOn(at, ctx.team).any { it.weight >= 0.05 }

    fun straight(a: Hex, b: Hex): Boolean = HexGrid.inStraightLine(a, b)

    fun meets(d: Decision, cardId: String): Boolean {
        if (d.context.blockPreview[cardId] == true) return true
        val total = d.context.attackTotal ?: return false
        val preview = d.context.defensePreview[cardId] ?: return false
        return preview >= total
    }

    /** "Which rune goes below turn slot 2?" → 2. */
    fun slotNumber(prompt: String): Int? {
        val key = "turn slot "
        val at = prompt.indexOf(key)
        if (at < 0) return null
        return prompt.drop(at + key.length).takeWhile { it.isDigit() }.toIntOrNull()
    }
}
