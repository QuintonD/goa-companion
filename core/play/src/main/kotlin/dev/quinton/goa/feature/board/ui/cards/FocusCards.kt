package dev.quinton.goa.feature.board.ui.cards

import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.model.catalog.Stat
import dev.quinton.goa.feature.board.play.table.CARD_ASPECT

/**
 * One card in the focused viewer. [enabled] false = shown but marked, and it can't be confirmed;
 * [reason] says why (shown under the card). [caption] is a short context line ("Turn 2 · Active"),
 * [verdict] a defense card's Holds / Falls against the Attack, [notes] what choosing it means
 * ("Magnetic Dagger goes to your hand"), [related] the [key]s of other cards in the viewer that
 * come with it (the spells a Gydion card names), shown small under [relatedLabel]; a tap goes to
 * them. [itemLine] false leaves out "As an item: +1 …" (a note says it). [key] matches the thumbnail's
 * [Modifier.cardFocusOrigin], so the card grows out of it and shrinks back into it.
 * [answeredBy] marks a card shown with a question that isn't one of its answers but what an answer
 * leads to (an upgrade card, ADR-045): the [key] of the card that is the answer. It can't be
 * confirmed (keep [enabled] false) but isn't veiled; [reason] says what to choose instead, and the
 * panel offers to go to that card.
 */
data class FocusCard(
    val card: Card,
    val key: String = card.id,
    val enabled: Boolean = true,
    val reason: String? = null,
    val caption: String? = null,
    val verdict: CardVerdict? = null,
    val notes: List<CardNote> = emptyList(),
    val related: List<String> = emptyList(),
    val relatedLabel: String? = null,
    val answeredBy: String? = null,
    val itemLine: Boolean = true,
)

/** A defense card against the Attack (p.14): "Holds" / "Falls" / "Decided by its text", and why. */
data class CardVerdict(val holds: Boolean?, val label: String, val detail: String)

/** A line of what choosing a card means; [item] puts the item icon of that stat before it (p.16). */
data class CardNote(val text: String, val item: Stat? = null)

/** "Discard Magnetic Dagger" → "discard Magnetic Dagger", for "Tap the card again to …". */
fun lowerFirst(text: String): String = text.replaceFirstChar { it.lowercaseChar() }

/** The focus viewer's Confirm button: the [action], else why this card can't be chosen now. */
fun confirmLabel(item: FocusCard, canConfirm: Boolean, action: String): String = when {
    !item.enabled -> "Not an option"
    !canConfirm -> "Wait…"
    else -> action
}

/** Over Confirm, only when the card can be chosen now: "Tap the card again to play this card". */
fun confirmHint(action: String): String = "Tap the card again to ${lowerFirst(action)}"

/** Gydion's spell card scans are 737 × 510 (official/SOURCES.md). */
const val LANDSCAPE_CARD_ASPECT = 737f / 510f

/** A spell card of a hero's spellbook (Gydion's 22): printed landscape. */
val Card.isSpell: Boolean get() = id.startsWith("$heroId-spell-")

/** Width / height of [card]'s scan; a facedown card (null) is a portrait card back. */
fun cardAspect(card: Card?): Float = if (card?.isSpell == true) LANDSCAPE_CARD_ASPECT else CARD_ASPECT

/**
 * A Handicap card's basis under the focused card, which the card itself doesn't print: derived from
 * its frame colour (rulebook p.20: "weaker versions of the regular basic cards (Usually Gold)"), as
 * the catalog says it; null for any other card.
 */
fun focusHandicapBasis(card: Card): String? {
    if (!card.id.endsWith(HANDICAP_SUFFIX)) return null
    val colour = when (card.color) {
        CardColor.GOLD -> "Gold"
        CardColor.SILVER -> "Silver"
        else -> return null
    }
    return "Handicap: a weaker version of the $colour card (frame colour, p.20)."
}

/** A hero's Handicap card is `<hero>-handicap` in the catalog (the hero JSON's `handicap`). */
private const val HANDICAP_SUFFIX = "-handicap"
