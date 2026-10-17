package dev.quinton.goa.feature.catalog.ui

import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.model.catalog.Role
import dev.quinton.goa.core.model.catalog.Stat
import dev.quinton.goa.feature.catalog.model.tierNumeral
import java.util.Locale
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

/** Spellbook pages have IDs `<hero>-spell-<name>` (permanent, see DOMAIN.md). */
val Card.isSpell: Boolean get() = id.startsWith("$heroId-spell-")

/**
 * "Red · Tier II", "Gold · Basic", "Ultimate", "Handicap · Silver", "Spell · Red · Tier II",
 * "Spell · Gold". A Handicap card is a weaker version of the basic card of its frame colour.
 */
fun Card.kindLabel(isHandicap: Boolean = false): String {
    val tier = tier
    return when {
        isHandicap -> if (color == CardColor.GOLD || color == CardColor.SILVER) "Handicap · ${color.label}" else "Handicap"
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

fun elo(value: Double): String = String.format(Locale.ROOT, "%d", value.roundToInt())

fun plural(count: Int, one: String, many: String = "${one}s"): String = "$count ${if (count == 1) one else many}"
