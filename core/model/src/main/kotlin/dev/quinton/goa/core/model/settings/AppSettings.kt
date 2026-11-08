package dev.quinton.goa.core.model.settings

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.booleanOrNull

@Serializable
enum class ThemeMode { DARK, LIGHT, SYSTEM }

/** How the running timer shows the round (a device preference, switchable from the timer). */
@Serializable
enum class TimerLayout {
    /** One step at a time with the big dial; Next and Back walk through the round. */
    STEPS,

    /** The whole round on one scrolling list, the current step expanded with its live clock. */
    ROUND,
}

/** How pieces and spaces look on the painted board (a device preference, ADR-053). */
@Serializable
enum class BoardStyle {
    /** The rulebook's miniatures with a small type badge; the painting's own spaces, no grid. */
    MINIATURES,

    /** A hairline hex grid over the painting, and discs with the type's glyph. */
    GRID_AND_DISCS,
}

/** How the Heroes tab shows the heroes (a device preference, switchable above the results). */
@Serializable
enum class CatalogLayout {
    /** Art-led tiles with the title, stars and roles: two or three columns on a phone. */
    LARGE_GRID,

    /** The portrait and name only: three columns on a phone, more on a wider screen. */
    SMALL_GRID,

    /** One hero a row, the portrait at the row's side. */
    LIST,
}

@OptIn(ExperimentalSerializationApi::class)
@KeepGeneratedSerializer
@Serializable(with = AppSettingsSerializer::class)
data class AppSettings(
    val theme: ThemeMode = ThemeMode.DARK,
    /** Product IDs the group owns; empty = all products. */
    val ownedProducts: Set<String> = emptySet(),
    val hapticsEnabled: Boolean = true,
    val soundsEnabled: Boolean = true,
    val catalogLayout: CatalogLayout = CatalogLayout.LARGE_GRID,
    val timerLayout: TimerLayout = TimerLayout.STEPS,
    /** Puzzles nudge when the player is stuck (after a miss, or a while without progress); off = only when asked. */
    val puzzleNudges: Boolean = true,
    /** The computer strength (1..10, ADR-039) last chosen on New game; the next game starts there. */
    val computerStrength: Int = 5,
    val boardStyle: BoardStyle = BoardStyle.MINIATURES,
)

/**
 * [AppSettings] as JSON (the web's store, backups), reading an older build's values: its `catalogListView: true`
 * (the grid/list switch before the small grid) is the list.
 */
@OptIn(ExperimentalSerializationApi::class)
internal object AppSettingsSerializer : JsonTransformingSerializer<AppSettings>(AppSettings.generatedSerializer()) {
    private const val LEGACY_LIST = "catalogListView"
    private const val LAYOUT = "catalogLayout"

    override fun transformDeserialize(element: JsonElement): JsonElement {
        if (element !is JsonObject || LEGACY_LIST !in element) return element
        val list = (element[LEGACY_LIST] as? JsonPrimitive)?.booleanOrNull == true
        val migrated = element - LEGACY_LIST
        return if (LAYOUT in element || !list) {
            JsonObject(migrated)
        } else {
            JsonObject(migrated + (LAYOUT to JsonPrimitive(CatalogLayout.LIST.name)))
        }
    }
}
