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
)
