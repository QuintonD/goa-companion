package dev.quinton.goa.feature.board

import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.catalog.Hero

/** A catalog hero as the board screens show it. Ids missing from the catalog read "Unknown hero". */
data class BoardHero(
    val id: String,
    val name: String,
    val title: String,
    val portrait: String?,
    val verified: Boolean,
    /** The deck in rulebook order: Basic (Gold, Silver), then Tier I–III by colour, then Ultimate. */
    val cards: List<Card>,
    /** The product the hero comes in: the pickers list owned products' heroes (Settings). */
    val product: String = "",
    /** Cards outside the deck the hero's cards use (Gydion's 22 spell cards), in the catalog's order. */
    val extras: List<Card> = emptyList(),
    /** The official deck symbol (ADR-042), null when the catalog has none. */
    val symbol: String? = null,
) {
    /** The hero's spell cards (Gydion's spellbook, ADR-040): extras with the `<hero>-spell-` ids. */
    val spells: List<Card> get() = extras.filter { it.id.startsWith("$id-spell-") }

    /** The deck, then the extras: every card the board can measure with (a spell has its own values). */
    val measurable: List<Card> get() = cards + extras

    companion object {
        fun of(hero: Hero) = BoardHero(
            id = hero.id,
            name = hero.name,
            title = hero.title,
            portrait = hero.portrait,
            verified = hero.verified,
            cards = deckOrder(hero.cards),
            product = hero.product,
            extras = hero.extras,
            symbol = hero.symbol,
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

/** The group owns this hero's product; [owned] empty = every product (Settings default). */
fun BoardHero.ownedIn(owned: Set<String>): Boolean = owned.isEmpty() || product in owned
