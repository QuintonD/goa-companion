package dev.quinton.goa.feature.board.board

import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.model.catalog.cardColorName
import dev.quinton.goa.feature.board.ui.cards.isSpell

/** "Basic Gold", "Tier II Red"; a spell card "Gold spell", "Tier II Red spell", "Purple spell" (Wish). */
fun cardKindLabel(card: Card): String = if (card.isSpell) {
    listOfNotNull(
        card.tier?.takeIf { it in 1..3 }?.let { "Tier ${tierNumeral(it)}" },
        cardColorName(card.color),
        "spell",
    ).joinToString(" ")
} else {
    "${tierLabel(card)} ${cardColorName(card.color)}"
}

/** "Basic", "Tier I", "Tier II", "Tier III", "Ultimate"; a spell card "Spell", "Tier II spell". */
fun tierLabel(card: Card): String = when {
    // Spellbook pages carry their printed spell level, null for the gold cantrips (catalog Card.tier).
    card.isSpell -> card.tier?.takeIf { it in 1..3 }?.let { "Tier ${tierNumeral(it)} spell" } ?: "Spell"

    card.color == CardColor.GOLD || card.color == CardColor.SILVER -> "Basic"

    card.color == CardColor.PURPLE || card.tier == ULTIMATE_TIER -> "Ultimate"

    else -> card.tier?.let { tier -> "Tier ${tierNumeral(tier)}" } ?: "Other"
}

fun tierNumeral(tier: Int): String = listOf("I", "II", "III", "IV").getOrElse(tier - 1) { tier.toString() }

const val ULTIMATE_TIER = 4
