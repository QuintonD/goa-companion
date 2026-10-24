package dev.quinton.goa.core.game.engine

import dev.quinton.goa.core.model.match.Team
import kotlin.random.Random

/**
 * Samples a state consistent with what [viewer]'s team can see (p.4 "Hidden Information"), for the AI
 * (ADR-035: it "never reads hidden cards"). For each hero of the other team:
 * - its hidden cards are those in hand, its facedown played card before the reveal, and facedown
 *   resolved or discarded cards; deck cards are hidden too;
 * - a hidden card may be any card of the same colour and Tier among the hidden and deck cards (the
 *   public items and level fix one card per colour slot and its Tier, so this keeps them);
 * - then hand and the unrevealed played card are dealt again at random from that hero's hidden hand
 *   pool: which card was played is not known before the reveal.
 * Facedown tokens of the other team get their kinds shuffled among themselves.
 *
 * The sample depends only on public information and [rng]: hidden positions are canonicalised (sorted
 * card ids) before shuffling, so two states that differ only in hidden cards give the same sample.
 */
object Determinizer {
    fun sample(data: EngineData, source: GameState, viewer: Team, rng: Random): GameState {
        val s = source.copy()
        for (h in s.heroes) {
            if (h.team == viewer) continue
            resampleIdentities(data, s, h, rng)
            redealHand(s, h, rng)
        }
        shuffleFacedownTokens(s, viewer, rng)
        s.stamp++
        return s
    }

    private fun hidden(state: GameState?, c: CardInst): Boolean = when (c.zone) {
        Zone.HAND, Zone.DECK -> true
        Zone.PLAYED -> state?.revealed == false
        Zone.RESOLVED, Zone.DISCARD -> c.faceDown
        else -> false
    }

    /** Swap identities within each (colour, tier) class among hidden positions (deck included). */
    private fun resampleIdentities(data: EngineData, s: GameState, h: HeroState, rng: Random) {
        val hiddenCards = h.cards.filter { hidden(s, it) }
        val byClass = hiddenCards.groupBy { data.card(it.id).let { c -> c.color to c.tier } }
        for ((_, members) in byClass.entries.sortedBy { it.value.minOf { c -> c.id } }) {
            if (members.size < 2) continue
            // Positions in a canonical order (public), identities dealt to them at random.
            val states = members.map { Snapshot(it.zone, it.slot, it.faceDown, it.played, it.tag) }
                .sortedWith(compareBy({ it.zone.ordinal }, { it.slot }, { it.faceDown }, { it.played }, { it.tag ?: "" }))
            val ids = members.map { it.id }.sorted().shuffled(rng)
            val byId = h.cards.associateBy { it.id }
            for ((i, id) in ids.withIndex()) states[i].applyTo(byId.getValue(id))
        }
    }

    private class Snapshot(val zone: Zone, val slot: Int, val faceDown: Boolean, val played: Boolean, val tag: String?) {
        fun applyTo(c: CardInst) {
            c.zone = zone
            c.slot = slot
            c.faceDown = faceDown
            c.played = played
            c.tag = tag
        }
    }

    /** Before the reveal, any card of the hand pool may be the one played facedown. */
    private fun redealHand(s: GameState, h: HeroState, rng: Random) {
        if (s.revealed) return
        val pool = h.cards.filter { it.zone == Zone.HAND || it.zone == Zone.PLAYED }.sortedBy { it.id }
        val playedCount = pool.count { it.zone == Zone.PLAYED }
        if (playedCount == 0) return
        val shuffled = pool.shuffled(rng)
        for ((i, c) in shuffled.withIndex()) {
            if (i < playedCount) {
                c.zone = Zone.PLAYED
                c.played = true
            } else {
                c.zone = Zone.HAND
                c.played = false
            }
        }
    }

    private fun shuffleFacedownTokens(s: GameState, viewer: Team, rng: Random) {
        val hiddenPieces = s.pieces.filter { it.faceDown && it.team != viewer }.groupBy { it.owner }
        for ((_, pieces) in hiddenPieces) {
            if (pieces.size < 2) continue
            val kinds = pieces.sortedBy { it.id }.map { it.kind to it.label }.sortedBy { it.first }.shuffled(rng)
            val sorted = pieces.sortedBy { it.id }
            for ((i, p) in sorted.withIndex()) {
                val idx = s.pieces.indexOf(p)
                s.pieces[idx] = PieceState(
                    p.id, kinds[i].first, kinds[i].second, p.at, p.owner, p.team, p.token, p.unit,
                    p.removeAtEndOfRound, p.passable, p.faceDown, p.heroBody, LinkedHashMap(p.data),
                )
            }
        }
    }
}
