package dev.quinton.goa.core.domain.catalog

import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.model.catalog.CardText
import dev.quinton.goa.core.model.catalog.Hero
import dev.quinton.goa.core.model.catalog.HeroStats
import dev.quinton.goa.core.model.catalog.PrimaryAction
import dev.quinton.goa.core.model.catalog.SecondaryAction
import dev.quinton.goa.core.model.catalog.Stat
import dev.quinton.goa.core.model.catalog.StatRange

/*
 * The words the hero catalog shows and reads out for a card or a hero's deck, shared by the phone
 * (feature:catalog) and the web app (ADR-043): one wording, derived the same way on both.
 */

/** The word for a primary action, as its icon is read ("Defense / Skill"). */
val PrimaryAction.label: String
    get() = when (this) {
        PrimaryAction.ATTACK -> "Attack"
        PrimaryAction.SKILL -> "Skill"
        PrimaryAction.DEFENSE -> "Defense"
        PrimaryAction.MOVEMENT -> "Movement"
        PrimaryAction.DEFENSE_SKILL -> "Defense / Skill"
    }

/** The word for a secondary action, as its icon is read. */
val SecondaryAction.label: String
    get() = when (this) {
        SecondaryAction.MOVEMENT -> "Movement"
        SecondaryAction.DEFENSE -> "Defense"
        SecondaryAction.HOLD -> "Hold"
        SecondaryAction.FAST_TRAVEL -> "Fast Travel"
        SecondaryAction.CLEAR -> "Clear"
        SecondaryAction.ATTACK -> "Attack"
    }

/** One printed value; [plus] when the card prints it with a `+` (it grows per the card text). */
data class CardValue(val stat: Stat, val value: Int, val plus: Boolean) {
    /** As printed: "4" or "4+". */
    val printed: String get() = if (plus) "$value+" else "$value"

    /** For a screen reader: "Attack 4" or "Attack 4 plus". */
    val spoken: String get() = "${stat.label} " + if (plus) "$value plus" else "$value"
}

/** The order values are printed in on a card. */
private val VALUE_ORDER = listOf(Stat.ATTACK, Stat.DEFENSE, Stat.MOVEMENT, Stat.RANGE, Stat.RADIUS)

/** Non-null printed values in a stable order. */
fun Card.cardValues(): List<CardValue> = VALUE_ORDER.mapNotNull { stat ->
    values.get(stat)?.let { CardValue(stat, it, stat in values.plus) }
}

const val STARTS_IN_HAND = "Starts in hand"
const val STARTS_IN_DECK = "Starts in deck"

/** Where a basic card starts, from its printed reminder "(This card starts the game in your hand.)". */
fun Card.startsIn(): String? = when (StartingCards.startZone(this)) {
    StartZone.HAND -> STARTS_IN_HAND
    StartZone.DECK -> STARTS_IN_DECK
    null -> null
}

/** Spoken summary of a card for a screen reader: name, kind, initiative, actions, values, markers. */
fun Card.spokenSummary(isHandicap: Boolean = false): String = buildList {
    add(name)
    add(kindLabel(isHandicap))
    initiative?.let { add("Initiative $it") }
    primary?.let { add("Primary action ${it.label}") }
    cardValues().forEach { add(it.spoken) }
    if (ranged) add("Ranged")
    blockMarkerLabel()?.let { add(it) }
    item?.let { add("Item: plus 1 ${it.label}") }
    startsIn()?.let { add(it) }
    if (isHandicap) spokenHandicapBasis()?.let { add(it) }
}.joinToString(". ")

/** Rulebook p.20, under the Handicap section of a hero's deck. */
const val HANDICAP_EXPLANATION =
    "A weaker version of a basic card (usually Gold). With 5, 7 or 9 players, every hero on the " +
        "larger team replaces one of its basic cards with it. With 3 players, the player controlling " +
        "two heroes replaces a gold card of one of them with it. In even-count games, the most " +
        "experienced players may use it to even out skill."

/**
 * Rulebook p.20 has the 3-player Handicap card replace "a gold card of one of those heroes", but a
 * Handicap card without a gold frame (Takahide's is silver, and he has three gold cards) leaves
 * open which card it replaces. Shown as unresolved; the app never picks an answer.
 */
fun handicapConflict(hero: Hero, handicap: Card): String? {
    if (handicap.color == CardColor.GOLD) return null
    val golds = hero.cards.count { it.color == CardColor.GOLD }
    val frame = handicap.color.label.lowercase()
    val goldCards = if (golds > 1) " and ${hero.name} has $golds gold cards" else ""
    return "Unresolved: rulebook p.20 says that with 3 players the Handicap card replaces a gold card, but this " +
        "one has a $frame frame$goldCards. Neither the rulebook nor the card says which card it replaces."
}

/**
 * Takahide has three gold cards. Their own text says where each starts and how they swap
 * ("Swap this card with a different gold card in your deck."), so the note points there and
 * the tiles carry "Starts in hand" / "Starts in deck" from each card's reminder text. Null when
 * the basic cards need no note.
 */
fun multipleGoldNote(basicCards: List<Card>): String? {
    val golds = basicCards.filter { it.color == CardColor.GOLD }
    val hand = golds.count { it.startsIn() == STARTS_IN_HAND }
    val deck = golds.count { it.startsIn() == STARTS_IN_DECK }
    if (golds.size < 2 || hand + deck == 0) return null
    return "${golds.size} gold cards: $hand ${if (hand == 1) "starts" else "start"} in your hand, $deck in your " +
        "deck, as their card text says. Tap a card for how it swaps."
}

/** Under the Spellbook heading (Gydion's spell cards). */
const val SPELLBOOK_NOTE = "Spells by tier. Deck cards name the spells they cast; tap a spell for its full card."

/** "Gold (no tier) · 3", "Tier II · 9", "Ultimate · 1": a row of a hero's extra cards. */
fun extraRowTitle(row: TierRow): String {
    val tier = row.tier
    val name = when {
        tier == null && row.cards.all { it.color == CardColor.GOLD } -> "Gold (no tier)"
        tier == null -> "No tier"
        tier == 4 -> "Ultimate"
        else -> "Tier ${tierNumeral(tier)}"
    }
    return "$name · ${row.cards.size}"
}

/** The scale of the official Hero Profile card: 1 ("Very poor") to 8 ("Exceptional"). */
const val PROFILE_SCALE = 8

/** Order printed on the official Hero Profile card (rulebook p.21). */
val PROFILE_STATS: List<Stat> = listOf(Stat.ATTACK, Stat.INITIATIVE, Stat.DEFENSE, Stat.MOVEMENT)

/** One Hero Profile bar: [range] null when the profile doesn't give the stat. */
data class ProfileBar(val stat: Stat, val range: StatRange?) {
    /** "4", "4 → 6" or "—". */
    val rangeText: String
        get() = when {
            range == null -> "—"
            range.base == range.max -> "${range.base}"
            else -> "${range.base} → ${range.max}"
        }

    /** "Attack: 4 of 8, up to 6 conditionally". */
    val spoken: String
        get() = if (range == null) {
            "${stat.label}: unknown"
        } else {
            "${stat.label}: ${range.base} of $PROFILE_SCALE" +
                if (range.max > range.base) ", up to ${range.max} conditionally" else ""
        }

    /** Segment [level] (1..8): solid up to the base, conditional up to the max. */
    fun segment(level: Int): ProfileSegment = when {
        range == null -> ProfileSegment.EMPTY
        level <= range.base -> ProfileSegment.BASE
        level <= range.max -> ProfileSegment.CONDITIONAL
        else -> ProfileSegment.EMPTY
    }
}

enum class ProfileSegment { BASE, CONDITIONAL, EMPTY }

/** The hero's profile bars in the printed order. */
fun HeroStats.profileBars(): List<ProfileBar> = PROFILE_STATS.map { ProfileBar(it, get(it)) }

/**
 * The paragraph of card [text] (markup) that contains the query, so the result shows the
 * words that matched; the first paragraph when the match is in the card's name. A lead-in such
 * as "Choose one —" says nothing on its own, so it comes with the paragraphs after it (the
 * options; the row shows as many lines as fit).
 */
fun matchingParagraph(text: String, query: String): String {
    val paragraphs = text.split('\n').filter { it.isNotBlank() }
    if (paragraphs.isEmpty()) return ""
    val tokens = CatalogSearchIndex.tokenize(query)
    val plain = paragraphs.map { CatalogSearchIndex.normalize(CardText.plain(it)).trim() }
    val phrase = tokens.joinToString(" ")
    val index = if (tokens.isEmpty()) {
        0
    } else {
        plain.indexOfFirst { phrase in it }.takeIf { it >= 0 }
            ?: plain.indexOfFirst { p -> tokens.any { it in p } }.takeIf { it >= 0 }
            ?: 0
    }
    val leadIn = plain[index].endsWith('—') || plain[index].endsWith(':') || plain[index].endsWith("--")
    return if (leadIn && index < paragraphs.lastIndex) {
        paragraphs.subList(index, paragraphs.size).joinToString("\n")
    } else {
        paragraphs[index]
    }
}
