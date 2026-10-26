package dev.quinton.goa.core.domain.catalog

import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.model.catalog.Hero

/** Deck sections in rulebook order: basic Gold/Silver → Red/Green/Blue tiers → Ultimate. */
enum class DeckGroupKind(val title: String) {
    BASIC("Basic"),
    RED("Red"),
    GREEN("Green"),
    BLUE("Blue"),
    ULTIMATE("Ultimate"),
    OTHER("Other"),
}

/** Cards of one tier within a group; [tier] null = untiered (basic cards). */
data class TierRow(val tier: Int?, val cards: List<Card>)

data class DeckGroup(val kind: DeckGroupKind, val rows: List<TierRow>) {
    val cards: List<Card> get() = rows.flatMap { it.cards }
}

/**
 * A hero's deck laid out for display. Handles any data shape (extra cards, unexpected colours,
 * missing tiers) without dropping a card: anything that doesn't fit lands in [DeckGroupKind.OTHER].
 */
data class DeckLayout(
    val groups: List<DeckGroup>,
    val handicap: Card?,
    val extras: List<Card>,
) {
    /**
     * Extra cards by tier (untiered first, Ultimate last): Gydion's spellbook reads cantrips
     * (gold, no tier), Tier I, II, III, then Wish. Source order is kept within a tier.
     */
    val extraRows: List<TierRow> = byTier(extras)

    /** Every extra is a spellbook page (IDs `<hero>-spell-…`), so the section is the spellbook. */
    val extrasAreSpellbook: Boolean
        get() = extras.isNotEmpty() && extras.all { it.id.startsWith("${it.heroId}-spell-") }

    /** Every card in display order — the order the card viewer swipes through. */
    val orderedCards: List<Card>
        get() = groups.flatMap { it.cards } + listOfNotNull(handicap) + extraRows.flatMap { it.cards }

    companion object {
        fun of(hero: Hero): DeckLayout {
            val byGroup = hero.cards.groupBy { groupOf(it) }
            val groups = DeckGroupKind.entries.mapNotNull { kind ->
                val cards = byGroup[kind].orEmpty()
                if (cards.isEmpty()) return@mapNotNull null
                val rows = when (kind) {
                    DeckGroupKind.BASIC -> listOf(TierRow(null, cards.sortedBy { it.color != CardColor.GOLD }))
                    else -> byTier(cards)
                }
                DeckGroup(kind, rows)
            }
            return DeckLayout(groups = groups, handicap = hero.handicap, extras = hero.extras)
        }

        /** Rows by tier, untiered first; source order within a tier. */
        private fun byTier(cards: List<Card>): List<TierRow> = cards.groupBy { it.tier }
            .entries
            .sortedWith(compareBy(nullsFirst()) { it.key })
            .map { (tier, tierCards) -> TierRow(tier, tierCards) }

        private fun groupOf(card: Card): DeckGroupKind = when (card.color) {
            CardColor.GOLD, CardColor.SILVER -> DeckGroupKind.BASIC
            CardColor.RED -> DeckGroupKind.RED
            CardColor.GREEN -> DeckGroupKind.GREEN
            CardColor.BLUE -> DeckGroupKind.BLUE
            CardColor.PURPLE -> DeckGroupKind.ULTIMATE
            CardColor.OTHER -> DeckGroupKind.OTHER
        }
    }
}

/** Rulebook tier numerals: I, II, III, IV (Ultimate). */
fun tierNumeral(tier: Int): String = when (tier) {
    1 -> "I"
    2 -> "II"
    3 -> "III"
    4 -> "IV"
    else -> tier.toString()
}
