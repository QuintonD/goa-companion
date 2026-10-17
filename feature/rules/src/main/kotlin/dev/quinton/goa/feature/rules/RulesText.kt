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

    /** "1 coin", "2 coins", "1 Life counter". */
    fun count(n: Int, one: String, many: String = "${one}s"): String = "$n ${if (n == 1) one else many}"

    /**
     * The same card label the Heroes search shows (catalog `Card.kindLabel`): "Red · Tier II",
     * "Gold · Basic", "Ultimate", "Handicap · Silver", "Spell · Red · Tier III". Feature modules
     * don't depend on each other, so the format is mirrored here; keep the two in step.
     */
    fun cardLabel(hero: Hero, card: Card): String {
        val tier = card.tier
        val basic = card.color == CardColor.GOLD || card.color == CardColor.SILVER
        return when {
            card.id == hero.handicap?.id -> if (basic) "Handicap · ${colorLabel(card.color)}" else "Handicap"

            card.id.startsWith("${hero.id}-spell-") -> listOfNotNull(
                "Spell",
                colorLabel(card.color).takeIf { card.color != CardColor.PURPLE && card.color != CardColor.OTHER },
                tier?.let { if (it == ULTIMATE_TIER) "Ultimate" else "Tier ${roman(it)}" },
            ).joinToString(" · ")

            card.color == CardColor.PURPLE || tier == ULTIMATE_TIER -> "Ultimate"

            basic -> "${colorLabel(card.color)} · Basic"

            tier != null -> "${colorLabel(card.color)} · Tier ${roman(tier)}"

            else -> colorLabel(card.color)
        }
    }

    private fun colorLabel(color: CardColor): String = if (color == CardColor.OTHER) "Special" else cardColorName(color)

    private const val ULTIMATE_TIER = 4
}
