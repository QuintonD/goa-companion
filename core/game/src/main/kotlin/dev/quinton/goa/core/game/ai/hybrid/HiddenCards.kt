package dev.quinton.goa.core.game.ai.hybrid

import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.game.engine.GameState
import dev.quinton.goa.core.game.engine.HeroState
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.model.match.Team

/**
 * What a modelled player may know of the other team's facedown cards (ADR-062; Wave 4 Track F,
 * review-3/V2-code.md §1.3). Strength 10 knows every enemy's hand pool, but which card of it was played
 * facedown stays hidden until the reveal. Inside the search's copies the played card is plainly in
 * [Zone.PLAYED], so the book answering for a seat that chooses *after* it would read it (its defense
 * reads, `HContext.defensesOf`, see only [HeroState.hand]). Its view puts every such card back in hand:
 * the hand as it was before the facedown choice.
 */
internal object HiddenCards {
    /** Heroes not of [team] with a card played facedown this turn and not yet revealed. */
    fun playedAgainst(s: GameState, team: Team): List<HeroState> =
        if (s.revealed) emptyList() else s.heroes.filter { h -> h.team != team && h.cards.any { it.zone == Zone.PLAYED } }

    /** [h]'s unrevealed played cards back into its hand, stamps cleared (they would tell which was played). */
    fun unplay(h: HeroState) {
        for (c in h.cards) {
            if (c.zone == Zone.PLAYED) {
                c.zone = Zone.HAND
                c.played = false
            }
            if (c.zone == Zone.HAND) c.stamp = 0
        }
    }

    /** [team]'s view of [s]: every other-team unrevealed played card back in hand (in place). */
    fun hideFrom(s: GameState, team: Team) {
        for (h in playedAgainst(s, team)) unplay(h)
    }

    /** [team]'s view of [es] (the same session when nothing of the other team is facedown). */
    fun viewOf(es: EngineSession, team: Team): EngineSession =
        if (playedAgainst(es.state, team).isEmpty()) {
            es
        } else {
            es.viewWith { hideFrom(it, team) }
        }

    /** [heroId] plays [cardId] facedown (a dealt opponent card, [HybridAi]'s card deals), in place. */
    fun play(s: GameState, heroId: String, cardId: String): Boolean {
        val h = s.hero(heroId) ?: return false
        val c = h.cards.firstOrNull { it.id == cardId && it.zone == Zone.HAND } ?: return false
        c.zone = Zone.PLAYED
        c.played = true
        return true
    }
}
