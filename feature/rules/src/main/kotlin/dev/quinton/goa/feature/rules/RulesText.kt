package dev.quinton.goa.feature.rules

import dev.quinton.goa.core.designsystem.component.cardColorName
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.model.catalog.Hero

/** Labels for the rules screens (no rules content lives here). */
internal object RulesText {
    fun roman(tier: Int?): String = when (tier) {
        1 -> "I"
        2 -> "II"
        3 -> "III"
        4 -> "IV"
        else -> "–"
    }

    /** "p. 13" / "pp. 4, 7" — printed page numbers of the rulebook. */
    fun pages(pages: List<Int>): String = when (pages.size) {
        1 -> "p. ${pages.single()}"
        else -> "pp. ${pages.joinToString(", ")}"
    }

    fun spokenPages(pages: List<Int>): String = when (pages.size) {
        1 -> "rulebook page ${pages.single()}"
        else -> "rulebook pages ${pages.dropLast(1).joinToString(", ")} and ${pages.last()}"
    }

    fun cardLabel(hero: Hero, card: Card): String {
        val tier = card.tier?.let { "Tier ${roman(it)}" }
        return when {
            card.id == hero.handicap?.id -> "Handicap card"
            hero.extras.any { it.id == card.id } -> listOfNotNull("Extras", tier).joinToString(" · ")
            card.color == CardColor.GOLD || card.color == CardColor.SILVER -> cardColorName(card.color)
            else -> listOfNotNull(tier, cardColorName(card.color)).joinToString(" · ")
        }
    }
}
