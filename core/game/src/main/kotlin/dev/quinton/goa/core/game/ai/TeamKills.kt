package dev.quinton.goa.core.game.ai

import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.game.engine.GameState
import dev.quinton.goa.core.model.match.Team

/**
 * Kills a team could make on the next turn with several heroes together (ADR-061), read from a state's
 * printed values only: an enemy hero dies when more of our heroes can hit it than it will have cards to
 * defend with ("If the attacked hero has no cards in hand ... that hero is defeated", p.13; a Defense
 * action discards the card, p.10). Each attacker counts once (one card a turn, p.7). It plays a card
 * itself first, so it defends with one card fewer than it holds. A card hits when its attack (with "Before
 * the attack: Move N") reaches the hero's space now, or it forces a discard there ([CardProfile.reach]);
 * whether its Attack beats the Defense left is not asked, so this is a chance, not a certainty. Nothing
 * names a hero; in a search it reads only the determinized copy it is given.
 */
internal object TeamKills {
    /** [target] can be killed by [attackers] (hero → the card it plays for it); it holds [hand] cards. */
    class Chance(val target: String, val attackers: Map<String, String>, val hand: Int) {
        /** More than one of the attackers is needed: the target has a card to answer the first. */
        val needsTwo: Boolean get() = hand >= 2
    }

    /** Every enemy hero [team] could kill next turn, the surest first (most attackers over its cards). */
    fun chances(ev: Evaluator, s: GameState, team: Team): List<Chance> {
        val out = ArrayList<Chance>()
        for (e in s.heroes) {
            if (e.team == team) continue
            val at = e.at ?: continue
            val hand = e.hand.size
            val attackers = LinkedHashMap<String, String>()
            for (h in s.heroes) {
                if (h.team != team) continue
                val from = h.at ?: continue
                val d = HexGrid.distance(from, at)
                val card = h.hand.map { ev.profile(it.id) }
                    .filter { (it.attackCard || it.forcesDiscard) && it.reach >= d }
                    .maxWithOrNull(compareBy({ weight(it) }, { it.id })) ?: continue
                attackers[h.id] = card.id
            }
            if (attackers.isNotEmpty() && attackers.size >= hand) out += Chance(e.id, attackers, hand)
        }
        return out.sortedWith(compareBy({ it.hand - it.attackers.size }, { it.target }))
    }

    /** What a card is worth for a kill: a discard that cannot be answered first, then the Attack. */
    private fun weight(p: CardProfile): Double =
        p.attack + (if (p.discardOrDefeat) DISCARD_OR_DEFEAT else 0.0) + (if (p.forcesDiscard) DISCARD else 0.0)

    private const val DISCARD_OR_DEFEAT = 3.0
    private const val DISCARD = 2.0
}
