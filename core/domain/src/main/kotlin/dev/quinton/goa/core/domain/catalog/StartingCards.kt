package dev.quinton.goa.core.domain.catalog

import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.model.catalog.CardText
import dev.quinton.goa.core.model.catalog.Hero

/** Where a card's own printed reminder text says it starts the game. */
enum class StartZone { HAND, DECK }

/**
 * Starting cards, found from the catalog by tier, colour and card text (never hard-coded).
 *
 * Rulebook p.6 (printed), Hero Setup: "the three Tier I cards and the two basic cards with no
 * Tier" — "You should end up with five Action cards". Basic cards are the Gold and Silver cards
 * (p.9). Card text overrides the rulebook (p.3), so a basic card printing "(This card starts the
 * game in your deck.)" — Takahide's extra golds — is not dealt to the hand.
 */
object StartingCards {
    /** The zone the card's printed reminder names, or null when it names none. */
    fun startZone(card: Card): StartZone? {
        val plain = CardText.plain(card.text).lowercase()
        return when {
            "this card starts the game in your hand" in plain -> StartZone.HAND
            "this card starts the game in your deck" in plain -> StartZone.DECK
            else -> null
        }
    }

    /** The hero's starting hand: Gold, Silver, then Tier I in colour order (red, green, blue). */
    fun of(hero: Hero): List<Card> {
        val basic = hero.cards.filter {
            it.tier == null && (it.color == CardColor.GOLD || it.color == CardColor.SILVER) &&
                startZone(it) != StartZone.DECK
        }
        val tierOne = hero.cards.filter { it.tier == 1 }
        return (basic + tierOne).sortedBy { it.color.ordinal }
    }
}
