package dev.quinton.goa.feature.board

import androidx.compose.runtime.Immutable
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.catalog.Hero

/** A catalog hero as the board screens show it. Ids missing from the catalog read "Unknown hero". */
@Immutable
data class BoardHero(
    val id: String,
    val name: String,
    val title: String,
    val portrait: String?,
    val verified: Boolean,
    /** The deck in rulebook order: Basic (Gold, Silver), then Tier I–III by colour, then Ultimate. */
    val cards: List<Card>,
) {
    companion object {
        fun of(hero: Hero) = BoardHero(
            id = hero.id,
            name = hero.name,
            title = hero.title,
            portrait = hero.portrait,
            verified = hero.verified,
            cards = deckOrder(hero.cards),
        )

        fun unknown(id: String) = BoardHero(id, "Unknown hero", "", null, verified = false, cards = emptyList())

        /** Basic → Tier I → Tier II → Tier III → Ultimate; within a tier Red, Green, Blue, then source order. */
        fun deckOrder(cards: List<Card>): List<Card> = cards.sortedWith(
            compareBy<Card> { card ->
                when {
                    card.color == CardColor.GOLD -> 0
                    card.color == CardColor.SILVER -> 1
                    card.color == CardColor.PURPLE -> 10
                    card.color == CardColor.OTHER -> 11
                    else -> 1 + (card.tier ?: 0)
                }
            }.thenBy { card ->
                when (card.color) {
                    CardColor.RED -> 0
                    CardColor.GREEN -> 1
                    CardColor.BLUE -> 2
                    else -> 3
                }
            },
        )
    }
}

/** Every catalog hero by id. */
fun Catalog.boardHeroes(): Map<String, BoardHero> = heroes.associate { it.id to BoardHero.of(it) }

fun Map<String, BoardHero>.hero(id: String): BoardHero = this[id] ?: BoardHero.unknown(id)
