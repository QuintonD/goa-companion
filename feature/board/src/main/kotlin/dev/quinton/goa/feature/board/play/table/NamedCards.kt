package dev.quinton.goa.feature.board.play.table

import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.feature.board.ui.cards.FocusCard
import dev.quinton.goa.feature.board.ui.cards.isSpell

/*
 * Cards a card names (ADR-045 extended): a hero's card that names its hero's extra cards in quotes —
 * Gydion's Lesser Evocation prints "Choose and cast one spell in the spellbook — • “Burning Hands”
 * • “Fireball”". The link is the printed text and the catalog's card names, nothing else: a quoted
 * name is matched to an extra card of the same hero by its letters and digits (the card prints
 * “Midas Touch”, the spell card is "Midas' Touch"). A quote that names no extra card names nothing.
 */

/** The extra cards of its hero that [card]'s text names in quotes, in the order printed, each once. */
internal fun namedExtras(card: Card, extras: List<Card>): List<Card> {
    if (extras.isEmpty() || card.text.isEmpty()) return emptyList()
    val byName = extras.associateBy { nameKey(it.name) }
    return quotedNames(card.text)
        .mapNotNull { byName[nameKey(it)] }
        .filter { it.id != card.id }
        .distinct()
}

/** The extra cards [card] names, from the table's catalog (its hero's `extras`). */
internal fun TableUi.namedBy(card: Card): List<Card> = namedExtras(card, heroes[card.heroId]?.extras.orEmpty())

/** The names a card's text puts in quotes (curly or straight), in order. */
internal fun quotedNames(text: String): List<String> = QUOTED.findAll(text).map { it.groupValues[1].trim() }.toList()

private fun nameKey(name: String): String = name.lowercase().filter { it.isLetterOrDigit() }

private val QUOTED = Regex("[“\"]([^”\"\n]+)[”\"]")

/**
 * The pages of a card viewer opened for a question: the cards and, per card, the answer confirming
 * it gives (null = shown to read, not an answer).
 */
internal data class FocusPages(val cards: List<FocusCard>, val answers: List<Choice.Card?>) {
    fun indexOf(key: String): Int = cards.indexOfFirst { it.key == key }.coerceAtLeast(0)
}

/** The key of the page showing [named] after the card [namer] (a spell may follow several cards). */
internal fun namedKey(namer: FocusCard, named: Card): String = "${namer.key}>${named.id}"

/**
 * [pages] with, after each card that names extra cards ([named]), those cards as pages of their own
 * to read ("A spell Lesser Evocation casts"), never answers: they point back to [answerKey] of the
 * card that names them, and say why in [reason]. The naming card lists them under it to go to.
 */
internal fun FocusPages.withNamed(
    named: (Card) -> List<Card>,
    answerKey: (FocusCard) -> String = { it.key },
    reason: (FocusCard) -> String,
): FocusPages {
    val cards = mutableListOf<FocusCard>()
    val answers = mutableListOf<Choice.Card?>()
    this.cards.forEachIndexed { i, page ->
        val extras = named(page.card)
        if (extras.isEmpty()) {
            cards += page
            answers += this.answers.getOrNull(i)
            return@forEachIndexed
        }
        cards += page.copy(related = extras.map { namedKey(page, it) }, relatedLabel = namedLabel(extras))
        answers += this.answers.getOrNull(i)
        extras.forEach { extra ->
            cards += FocusCard(
                card = extra,
                key = namedKey(page, extra),
                enabled = false,
                caption = namedCaption(page.card, extra),
                reason = reason(page),
                answeredBy = answerKey(page),
            )
            answers += null
        }
    }
    return FocusPages(cards, answers)
}

/** "A spell Lesser Evocation casts" over a named spell; "Named by …" for any other extra card. */
internal fun namedCaption(namer: Card, named: Card): String =
    if (named.isSpell) "A spell ${namer.name} casts" else "Named by ${namer.name}"

private fun namedLabel(extras: List<Card>): String = when {
    extras.all { it.isSpell } && extras.size == 1 -> "The spell it casts:"
    extras.all { it.isSpell } -> "The spells it chooses from:"
    else -> "The cards it names:"
}

/** What TalkBack adds to a card that names extra cards: ", casts Burning Hands or Fireball". */
internal fun spokenNamed(extras: List<Card>): String? = when {
    extras.isEmpty() -> null
    extras.all { it.isSpell } -> "casts " + extras.joinToString(" or ") { it.name }
    else -> "names " + extras.joinToString(", ") { it.name }
}
