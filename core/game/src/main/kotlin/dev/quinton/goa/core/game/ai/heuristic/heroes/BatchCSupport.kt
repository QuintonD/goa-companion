package dev.quinton.goa.core.game.ai.heuristic.heroes

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
import dev.quinton.goa.core.model.catalog.Stat
import dev.quinton.goa.core.model.match.Team

/**
 * Lookups shared by the playbooks of Razzle, Rowenna, Sabina, Silverarrow, Snorri, Swift, Takahide and
 * Tali. No rules: each hero file names its own H-rules. Reads the deciding view only; a hidden enemy
 * hand stays in [HContext.possibleCards].
 */
internal object BatchC {
    fun cardId(choice: Choice): String? = (choice as? Choice.Card)?.cardId

    fun unitId(choice: Choice): String? = (choice as? Choice.Unit)?.unitId

    fun hexOf(choice: Choice): Hex? = (choice as? Choice.Space)?.hex

    /** The deciding hero's own card is in hand. Never call it for a hidden enemy. */
    fun inHand(h: HeroState, id: String): Boolean = h.card(id)?.zone == Zone.HAND

    /** Faceup on the dashboard: resolved or discarded. Public. */
    fun spent(h: HeroState, id: String): Boolean {
        val c = h.card(id) ?: return false
        return (c.zone == Zone.RESOLVED || c.zone == Zone.DISCARD) && !c.faceDown
    }

    /** Public knowledge still allows [heroId] to hold one of [ids] (their own team sees the real hand). */
    fun mayHold(ctx: HContext, heroId: String, ids: Set<String>): Boolean =
        ctx.possibleCards(heroId).any { it.card.id in ids }

    /** Every figure of [heroId]: the hero and the pieces that are that hero (Razzle). */
    fun bodies(state: GameState, heroId: String): List<Hex> {
        val self = state.hero(heroId)?.at
        val copies = state.pieces.mapNotNull { piece -> piece.at?.takeIf { piece.heroBody == heroId } }
        return listOfNotNull(self) + copies
    }

    /** The space of a unit: a hero, a minion or a piece. */
    fun posOf(ctx: HContext, id: String): Hex? = ctx.state.hero(id)?.at ?: ctx.state.minion(id)?.at ?: ctx.state.piece(id)?.at

    /** The rightmost faceup resolved card (p.7: turn slots fill left to right). */
    fun rightmost(h: HeroState): String? =
        h.cards.filter { it.zone == Zone.RESOLVED && !it.faceDown }.maxByOrNull { it.slot }?.id

    /** Snorri's rune below [turn] (public markers; `SnorriHero.placedRunes`). */
    fun activeRune(h: HeroState, turn: Int): SnorriHero.Rune? {
        for (marker in h.markers) {
            if (marker.from != h.id || marker.data[Marker.SLOT]?.toIntOrNull() != turn) continue
            return SnorriHero.Rune.entries.firstOrNull { it.kind == marker.kind } ?: continue
        }
        return null
    }

    fun runeCount(h: HeroState): Int =
        h.markers.count { marker -> marker.from == h.id && SnorriHero.Rune.entries.any { it.kind == marker.kind } }

    fun sign(state: GameState, heroId: String, team: Team): Double =
        if (state.hero(heroId)?.team == team) 1.0 else -1.0

    /** Heroes on the other side from [heroId] (for a threat term, where the context's team is the victim's). */
    fun foesOf(state: GameState, heroId: String): List<HeroState> {
        val team = state.hero(heroId)?.team ?: return emptyList()
        return state.heroes.filter { it.team != team }
    }

    fun enemiesWithin(ctx: HContext, at: Hex, radius: Int): List<HeroState> =
        ctx.enemies().filter { enemy -> enemy.at?.let { ctx.distance(at, it) in 1..radius } == true }

    fun alliesWithin(ctx: HContext, at: Hex, radius: Int): List<HeroState> =
        ctx.allies().filter { ally -> ally.at?.let { ctx.distance(at, it) in 1..radius } == true }

    fun minionsWithin(ctx: HContext, at: Hex, radius: Int, friendly: Boolean): List<MinionState> =
        ctx.state.minions.filter { minion ->
            val pos = minion.at ?: return@filter false
            (minion.team == ctx.team) == friendly && ctx.distance(at, pos) in 1..radius
        }

    /** An enemy minion that can actually be taken (not an immune heavy). */
    fun takeable(ctx: HContext, m: MinionState): Boolean = m.team != ctx.team && m.at != null && !ctx.heavyImmune(m.id)

    /** The space question moves the deciding hero (or one of its bodies), not a minion or a token. */
    fun movesSelf(ctx: HContext, d: Decision): Boolean {
        val subject = d.context.subjectId ?: return false
        return ctx.heroBody(subject) == ctx.seat
    }

    /** The engine's preview says [cardId] stops this attack. */
    fun meets(d: Decision, cardId: String): Boolean {
        if (d.context.blockPreview[cardId] == true) return true
        val total = d.context.attackTotal ?: return false
        val preview = d.context.defensePreview[cardId] ?: return false
        return preview >= total
    }

    /** Another defense option that also stops the attack. */
    fun otherMeets(d: Decision, cardId: String, except: Set<String> = emptySet()): Boolean =
        d.options.filterIsInstance<Choice.Card>().any { it.cardId != cardId && it.cardId !in except && meets(d, it.cardId) }

    /**
     * A skill that makes an enemy *hero* discard (no minion target). `CardProfile` marks it `forcesDiscard`,
     * and the general book then prices it as a strike on minions as well. With no enemy hero in [reach] that
     * value is not there: this returns minus the general strike outlook, else 0.
     */
    fun radiusOf(ctx: HContext, cardId: String): Int = ctx.card(cardId).radius + ctx.itemBonus(ctx.seat, Stat.RADIUS)

    fun heroOnlyDiscard(ctx: HContext, cardId: String, reach: Int): Double {
        val me = ctx.own() ?: return 0.0
        val at = me.at ?: return 0.0
        if (enemiesWithin(ctx, at, reach).isNotEmpty()) return 0.0
        return -ctx.strikeFrom(me.id, at, ctx.card(cardId)).coerceAtLeast(0.0)
    }

    /** "Which rune goes below turn slot 2?" → 2. */
    fun slotNumber(prompt: String): Int? {
        val key = "turn slot "
        val at = prompt.indexOf(key)
        if (at < 0) return null
        return prompt.drop(at + key.length).takeWhile { it.isDigit() }.toIntOrNull()
    }
}
