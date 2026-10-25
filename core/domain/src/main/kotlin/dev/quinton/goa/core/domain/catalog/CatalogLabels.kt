package dev.quinton.goa.core.domain.catalog

import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.model.catalog.CardText
import dev.quinton.goa.core.model.catalog.Role
import dev.quinton.goa.core.model.catalog.Stat
import kotlin.math.roundToInt

/** Official role names (rulebook terminology). */
val Role.label: String
    get() = when (this) {
        Role.TACTICIAN -> "Tactician"
        Role.DISABLER -> "Disabler"
        Role.DURABLE -> "Durable"
        Role.MELEE -> "Melee"
        Role.PUSHER -> "Pusher"
        Role.DAMAGER -> "Damager"
        Role.SNIPER -> "Sniper"
        Role.TOKENS -> "Tokens"
        Role.FARMING -> "Farming"
        Role.HEALER -> "Healer"
    }

/** Official complexity names (rulebook p.21): ★ Challenging … ★★★★ Wizard. */
fun complexityName(level: Int): String? = when (level) {
    1 -> "Challenging"
    2 -> "Complex"
    3 -> "Very Complex"
    4 -> "Wizard"
    else -> null
}

/** "★★★ Very Complex". */
fun complexityLabel(
    level: Int,
): String = listOfNotNull("★".repeat(level.coerceIn(0, 8)), complexityName(level)).joinToString(" ")

val Stat.label: String
    get() = when (this) {
        Stat.ATTACK -> "Attack"
        Stat.DEFENSE -> "Defense"
        Stat.INITIATIVE -> "Initiative"
        Stat.MOVEMENT -> "Movement"
        Stat.RANGE -> "Range"
        Stat.RADIUS -> "Radius"
    }

val CardColor.label: String
    get() = when (this) {
        CardColor.GOLD -> "Gold"
        CardColor.SILVER -> "Silver"
        CardColor.RED -> "Red"
        CardColor.GREEN -> "Green"
        CardColor.BLUE -> "Blue"
        CardColor.PURPLE -> "Purple"
        CardColor.OTHER -> "Special"
    }

/** Gold or Silver: the colours of basic cards (and the frame colours of Handicap cards). */
val CardColor.isBasic: Boolean get() = this == CardColor.GOLD || this == CardColor.SILVER

/**
 * What a Handicap card is a weaker version of, derived from its frame colour; the card itself
 * prints nothing about it. Rulebook p.20: "These cards are weaker versions of the regular basic
 * cards (Usually Gold)." Null for a card without a basic frame colour.
 */
fun Card.handicapBasis(): String? =
    if (color.isBasic) "Weaker version of the ${color.label} card (frame colour)" else null

/** [handicapBasis] for TalkBack. */
fun Card.spokenHandicapBasis(): String? =
    if (color.isBasic) "Weaker version of the ${color.label} card, judged from its frame colour" else null

private val BlockWord = Regex("\\bblock\\b", RegexOption.IGNORE_CASE)

/**
 * The marker for a Defense card printed with "!" ([Card.block]). Rulebook p.14: "Defense cards
 * with an exclamation mark on the icon allow you to Block an attack by fulfilling a Block
 * condition in the card text. If the Block condition is fulfilled, you successfully defend,
 * regardless of the attack value. If not, you are defeated." A "!" card whose text has no Block
 * condition (Takahide's Handicap, "(You are defeated.)") can never block, so it is not labelled
 * Block. Null for a card without the "!".
 */
fun Card.blockMarkerLabel(): String? = when {
    !block -> null
    BlockWord.containsMatchIn(CardText.plain(text)) -> "Block"
    else -> "No Block condition"
}

/** Spellbook pages have IDs `<hero>-spell-<name>` (permanent, see DOMAIN.md). */
val Card.isSpell: Boolean get() = id.startsWith("$heroId-spell-")

/**
 * "Red · Tier II", "Gold · Basic", "Ultimate", "Handicap · Silver", "Spell · Red · Tier II",
 * "Spell · Gold". A Handicap card is a weaker version of the basic card of its frame colour.
 */
fun Card.kindLabel(isHandicap: Boolean = false): String {
    val tier = tier
    return when {
        isHandicap -> if (color.isBasic) "Handicap · ${color.label}" else "Handicap"

        isSpell -> listOfNotNull(
            "Spell",
            color.label.takeIf { color != CardColor.PURPLE && color != CardColor.OTHER },
            tier?.let { if (it == 4) "Ultimate" else "Tier ${tierNumeral(it)}" },
        ).joinToString(" · ")

        color == CardColor.PURPLE || tier == 4 -> "Ultimate"

        color == CardColor.GOLD || color == CardColor.SILVER -> "${color.label} · Basic"

        tier != null -> "${color.label} · Tier ${tierNumeral(tier)}"

        else -> color.label
    }
}

/** "57%" — whole percent, for win/pick/ban rates in 0..1. */
fun percent(rate: Double): String = "${(rate * 100).roundToInt()}%"

fun elo(value: Double): String = value.roundToInt().toString()

fun plural(count: Int, one: String, many: String = "${one}s"): String = "$count ${if (count == 1) one else many}"
