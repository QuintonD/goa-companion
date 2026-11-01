package dev.quinton.goa.feature.board.play.table

import dev.quinton.goa.core.game.GamePhase
import dev.quinton.goa.core.game.HeroView
import dev.quinton.goa.core.game.MarkerView
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.feature.board.ui.cards.CardNote

// Markers as the table says and shows them (Android HeroState, the web app's HeroDetail), shared. A marker
// with a Turn slot is one of Snorri's runes (rulings/snorri.md 1, 2): "A rune is active as long as it is
// below the turn slot matching the current turn". Built only from the engine's view (open information).

/** "Axe rune", "Poison marker": a marker kind (`marker_rune_axe`, `marker_poison`) in words. */
fun markerName(kind: String): String {
    val name = kind.removePrefix("marker_")
    return if (name.startsWith(RUNE)) {
        "${titled(name.removePrefix(RUNE))} rune"
    } else {
        "${titled(name.replace('_', ' '))} marker"
    }
}

private fun titled(word: String): String = word.replaceFirstChar { it.uppercase() }

/** The marker's token, bundled by the catalog import (`official/SOURCES.md`). */
fun markerArt(kind: String): String = "official/icons/${kind.replace('_', '-')}.webp"

/** "Poison marker · from Bain", "Axe rune · below Turn 1 · active". */
fun markerLine(table: TableUi, hero: HeroView, marker: MarkerView): String = buildList {
    add(markerName(marker.kind))
    val slot = marker.slot
    if (slot != null) {
        add("below Turn $slot")
        if (marker == activeMarker(table, hero)) add("active")
    } else {
        marker.fromHeroId?.let { add("from ${table.hero(it).name}") }
    }
}.joinToString(" · ")

/** The marker below the current turn's slot; between turns (end of round, game over) none is read. */
fun activeMarker(table: TableUi, hero: HeroView): MarkerView? {
    val phase = table.view.phase
    if (phase != GamePhase.PLAY_CARDS && phase != GamePhase.RESOLVE) return null
    return hero.markers.firstOrNull { it.slot == table.view.turn }
}

/** A marker below one of the dashboard's Turn slots. */
data class SlotMarker(val kind: String, val active: Boolean)

/** How TalkBack and the web's labels say a [SlotMarker]: "Bird rune, active". */
fun spokenSlotMarker(marker: SlotMarker): String =
    markerName(marker.kind) + if (marker.active) ", active" else ""

/** What a hero's slot markers mean now, under the dashboard and over the hand. */
data class SlotMarkers(
    /** The active marker's kind, for its token; null between turns. */
    val activeKind: String?,
    /** "Bird rune active this turn". */
    val headline: String,
    /** "Next turn: Horn rune", null when the game is over. */
    val next: String?,
    /** What else holds: Rune Mastery, that the markers stay. */
    val notes: List<String>,
) {
    /** The headline and what comes next, on one line. */
    val line: String get() = listOfNotNull(headline, next).joinToString(" · ")
}

/** The summary of [hero]'s slot markers, or null when no marker lies below a slot. */
fun slotMarkers(table: TableUi, hero: HeroView): SlotMarkers? {
    val placed = hero.markers.filter { it.slot != null }
    if (placed.isEmpty()) return null
    val view = table.view
    val active = activeMarker(table, hero)
    val nextSlot = if (active == null || view.turn >= TURNS_IN_ROUND) 1 else view.turn + 1
    val next = placed.firstOrNull { it.slot == nextSlot }?.takeIf { view.phase != GamePhase.GAME_OVER }?.let { marker ->
        val turn = if (nextSlot == 1) "Next round, Turn 1" else "Next turn"
        "$turn: ${markerName(marker.kind)}"
    }
    return SlotMarkers(
        activeKind = active?.kind,
        headline = active?.let { "${markerName(it.kind)} active this turn" } ?: "No rune active between turns",
        next = next,
        notes = buildList {
            if (hero.ultimate) {
                add("Rune Mastery: each time you perform an action, one inactive rune you choose also counts.")
            }
            add("Rune markers are not removed at the end of round, nor if you are defeated.")
        },
    )
}

/**
 * What the rune active this turn does on [card], for the hand ([hero] the one playing it): every rune line
 * of a card is printed with its rune's symbol (`{rune_axe}`, the catalog's markup). Null for a card that
 * reads no rune.
 */
fun runeNote(table: TableUi, hero: HeroView, card: Card): CardNote? {
    val reads = RUNE_TOKEN.findAll(card.text).map { it.groupValues[1] }.toSet()
    if (reads.isEmpty()) return null
    if (hero.markers.none { it.slot != null }) {
        return CardNote("No rune is active yet: this card's rune lines do nothing.")
    }
    val active = activeMarker(table, hero) ?: return null
    val name = markerName(active.kind)
    return CardNote(
        when {
            active.kind.removePrefix("marker_$RUNE") in reads -> "$name active: this card has a line for it."
            hero.ultimate -> "$name active: this card has no line for it. Rune Mastery can count another rune."
            else -> "$name active: this card has no line for it."
        },
    )
}

private const val RUNE = "rune_"
private val RUNE_TOKEN = Regex("""\{rune_([a-z]+)\}""")
