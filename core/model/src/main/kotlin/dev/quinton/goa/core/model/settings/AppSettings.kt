package dev.quinton.goa.core.model.settings

import kotlinx.serialization.Serializable

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

@Serializable
data class AppSettings(
    val theme: ThemeMode = ThemeMode.DARK,
    /** Product IDs the group owns; empty = all products. */
    val ownedProducts: Set<String> = emptySet(),
    val hapticsEnabled: Boolean = true,
    val soundsEnabled: Boolean = true,
    /** Heroes tab shows the compact list instead of the portrait grid. */
    val catalogListView: Boolean = false,
    val timerLayout: TimerLayout = TimerLayout.STEPS,
    /** Puzzles nudge when the player is stuck (after a miss, or a while without progress); off = only when asked. */
    val puzzleNudges: Boolean = true,
    /** The computer strength (1..10, ADR-039) last chosen on New game; the next game starts there. */
    val computerStrength: Int = 5,
    val boardStyle: BoardStyle = BoardStyle.MINIATURES,
)
