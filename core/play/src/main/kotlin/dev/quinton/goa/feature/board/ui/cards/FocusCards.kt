package dev.quinton.goa.feature.board.ui.cards

import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.catalog.Stat
import dev.quinton.goa.feature.board.play.table.CARD_ASPECT

/**
 * One card in the focused viewer. [enabled] false = shown but marked, and it can't be confirmed;
 * [reason] says why (shown under the card). [caption] is a short context line ("Turn 2 · Active"),
 * [verdict] a defense card's Holds / Falls against the Attack, [notes] what choosing it means
 * ("Magnetic Dagger goes to your hand"), [related] other cards that come with it (the two of the
 * next Tier when upgrading) under [relatedLabel]. [key] matches the thumbnail's
 * [Modifier.cardFocusOrigin], so the card grows out of it and shrinks back into it.
 */
data class FocusCard(
    val card: Card,
    val key: String = card.id,
    val enabled: Boolean = true,
    val reason: String? = null,
    val caption: String? = null,
    val verdict: CardVerdict? = null,
    val notes: List<CardNote> = emptyList(),
    val related: List<Card> = emptyList(),
    val relatedLabel: String? = null,
)

/** A defense card against the Attack (p.14): "Holds" / "Falls" / "Decided by its text", and why. */
data class CardVerdict(val holds: Boolean?, val label: String, val detail: String)

/** A line of what choosing a card means; [item] puts the item icon of that stat before it (p.16). */
data class CardNote(val text: String, val item: Stat? = null)

/** "Discard Magnetic Dagger" → "discard Magnetic Dagger", for "Tap the card again to …". */
fun lowerFirst(text: String): String = text.replaceFirstChar { it.lowercaseChar() }

/** Gydion's spell card scans are 737 × 510 (official/SOURCES.md). */
const val LANDSCAPE_CARD_ASPECT = 737f / 510f

/** A spell card of a hero's spellbook (Gydion's 22): printed landscape. */
val Card.isSpell: Boolean get() = id.startsWith("$heroId-spell-")

/** Width / height of [card]'s scan; a facedown card (null) is a portrait card back. */
fun cardAspect(card: Card?): Float = if (card?.isSpell == true) LANDSCAPE_CARD_ASPECT else CARD_ASPECT
