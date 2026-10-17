package dev.quinton.goa.core.model.settings

import kotlinx.serialization.Serializable

@Serializable
enum class ThemeMode { DARK, LIGHT, SYSTEM }

@Serializable
data class AppSettings(
    val theme: ThemeMode = ThemeMode.DARK,
    /** Product IDs the group owns; empty = all products. */
    val ownedProducts: Set<String> = emptySet(),
    val hapticsEnabled: Boolean = true,
    val soundsEnabled: Boolean = true,
    /** Heroes tab shows the compact list instead of the portrait grid. */
    val catalogListView: Boolean = false,
)
