package dev.quinton.goa.core.presentation.catalog

import dev.quinton.goa.core.domain.catalog.StartZone
import dev.quinton.goa.core.domain.catalog.StartingCards
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.catalog.CardText
import dev.quinton.goa.core.model.catalog.PrimaryAction
import dev.quinton.goa.core.model.catalog.SecondaryAction
import dev.quinton.goa.core.model.catalog.Stat

/**
 * The words for the game icons: inline icon tokens in card text ([CardText.ICONS]), card values,
 * primary and secondary actions. The phone draws a glyph and TalkBack reads these; the web shows a
 * glyph with these as its accessible name. Unknown tokens have no label (rendered as text).
 */
object GameIconLabels {
    const val ATTACK = "Attack"
    const val DEFENSE = "Defense"
    const val INITIATIVE = "Initiative"
    const val MOVEMENT = "Movement"
    const val RANGE = "Range"
    const val RADIUS = "Radius"
    const val SKILL = "Skill"
    const val RANGED = "Ranged"
    const val BLOCK = "Block"
    const val ITEM = "Item"
    const val ULTIMATE = "Ultimate"
    const val HOLD = "Hold"
    const val FAST_TRAVEL = "Fast Travel"
    const val CLEAR = "Clear"
    const val DEFENSE_SKILL = "Defense / Skill"

    /** Label of every markup token in card text. */
    val byToken: Map<String, String> = linkedMapOf(
        "attack" to ATTACK,
        "defense" to DEFENSE,
        "initiative" to INITIATIVE,
        "movement" to MOVEMENT,
        "range" to RANGE,
        "radius" to RADIUS,
        "skill" to SKILL,
        "coin" to "Coin",
        "hold" to HOLD,
        "fast_travel" to FAST_TRAVEL,
        "clear" to CLEAR,
        "ranged" to RANGED,
        "block" to BLOCK,
        "item" to ITEM,
        "ultimate" to ULTIMATE,
        "minion" to "Minion",
        "heavy_minion" to "Heavy minion",
        "spawn" to "Spawn point",
        "life" to "Life counter",
        "wave" to "Wave counter",
        // Hero-specific symbols printed in card text (tokens, markers, runes, tie breaker sides).
        "tiebreaker_blue" to "Tie Breaker, blue side",
        "tiebreaker_orange" to "Tie Breaker, orange side",
        "marker_bounty" to "Bounty marker",
        "marker_poison" to "Poison marker",
        "rune_anvil" to "Anvil rune",
        "rune_axe" to "Axe rune",
        "rune_bird" to "Bird rune",
        "rune_horn" to "Horn rune",
        "rune_anvil_marker" to "Anvil rune marker",
        "rune_axe_marker" to "Axe rune marker",
        "rune_bird_marker" to "Bird rune marker",
        "rune_horn_marker" to "Horn rune marker",
        "token_barrier" to "Barrier token",
        "token_blast" to "Blast token",
        "token_dud" to "Dud token",
        "token_familiar" to "Familiar token",
        "token_glitch" to "Glitch token",
        "token_grenade" to "Grenade token",
        "token_ice" to "Ice token",
        "token_illusion" to "Illusion token",
        "token_magma" to "Magma token",
        "token_rock" to "Rock token",
        "token_smoke_bomb" to "Smoke bomb token",
        "token_totem" to "Totem token",
        "token_tree" to "Tree token",
        "token_zombie" to "Zombie token",
    )

    /** The label of a markup token such as `attack`; null for unknown names. */
    fun forToken(name: String): String? = byToken[name]

    fun forStat(stat: Stat): String = stat.label

    fun forPrimary(action: PrimaryAction): String = when (action) {
        PrimaryAction.ATTACK -> ATTACK
        PrimaryAction.SKILL -> SKILL
        PrimaryAction.DEFENSE -> DEFENSE
        PrimaryAction.MOVEMENT -> MOVEMENT
        PrimaryAction.DEFENSE_SKILL -> DEFENSE_SKILL
    }

    fun forSecondary(action: SecondaryAction): String = when (action) {
        SecondaryAction.MOVEMENT -> MOVEMENT
        SecondaryAction.DEFENSE -> DEFENSE
        SecondaryAction.HOLD -> HOLD
        SecondaryAction.FAST_TRAVEL -> FAST_TRAVEL
        SecondaryAction.CLEAR -> CLEAR
        SecondaryAction.ATTACK -> ATTACK
    }
}

/**
 * What a screen reader says for an inline icon: its label, or a blank when the printed text right
 * after it already names it ("{life} Life counter" reads "Life counter" once, not twice). A blank,
 * not an empty string: inline content on the phone needs non-empty alternate text.
 */
fun spokenIconLabel(label: String, next: CardText.Segment?): String {
    val following = (next as? CardText.Segment.Text)?.value?.trimStart().orEmpty()
    return if (following.startsWith(label, ignoreCase = true)) " " else label
}

/** One printed value; [plus] when the card prints it with a `+` (it grows per the card text). */
data class PrintedValue(val stat: Stat, val value: Int, val plus: Boolean) {
    /** As printed: "4" or "4+". */
    val printed: String get() = if (plus) "$value+" else "$value"

    /** For screen readers: "Attack 4" or "Attack 4 plus". */
    val spoken: String get() = "${stat.label} " + if (plus) "$value plus" else "$value"
}

/** Card values in the order the tiles show them. */
private val VALUE_ORDER = listOf(Stat.ATTACK, Stat.DEFENSE, Stat.MOVEMENT, Stat.RANGE, Stat.RADIUS)

/** Non-null printed values in a stable order (Attack, Defense, Movement, Range, Radius). */
fun Card.printedValues(): List<PrintedValue> = VALUE_ORDER.mapNotNull { stat ->
    values.get(stat)?.let { PrintedValue(stat, it, stat in values.plus) }
}

const val STARTS_IN_HAND = "Starts in hand"
const val STARTS_IN_DECK = "Starts in deck"

/** Where a basic card starts, from its printed reminder "(This card starts the game in your hand.)". */
fun Card.startsIn(): String? = when (StartingCards.startZone(this)) {
    StartZone.HAND -> STARTS_IN_HAND
    StartZone.DECK -> STARTS_IN_DECK
    null -> null
}

/** Spoken summary of a card for screen readers. */
fun Card.spokenSummary(isHandicap: Boolean = false): String = buildList {
    add(name)
    add(kindLabel(isHandicap))
    initiative?.let { add("Initiative $it") }
    primary?.let { add("Primary action ${GameIconLabels.forPrimary(it)}") }
    printedValues().forEach { add(it.spoken) }
    if (ranged) add(GameIconLabels.RANGED)
    blockMarkerLabel()?.let { add(it) }
    item?.let { add("Item: plus 1 ${it.label}") }
    startsIn()?.let { add(it) }
    if (isHandicap) spokenHandicapBasis()?.let { add(it) }
}.joinToString(". ")
