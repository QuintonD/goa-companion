package dev.quinton.goa.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import dev.quinton.goa.core.model.player.PlayerPalette

/**
 * One swatch of the player palette. [argb] is the value stored in `Player.color` (it equals the
 * dark-theme tone); [dark]/[light] are the tones to draw with in each theme. Every tone reaches
 * ≥ 4.5:1 against background and surface of its theme (docs/DESIGN.md), so player colours are
 * safe for names and labels as well as crests.
 */
@Immutable
data class PlayerColor(val name: String, val argb: Long, val dark: Color, val light: Color)

/** The player editor's palette: 10 colours readable in both themes. */
object PlayerColors {
    /** The palette's values live in core:model ([PlayerPalette]), shared with the web app. */
    val all: List<PlayerColor> = PlayerPalette.all.map { swatch(it.name, it.dark, it.light) }

    /** Suggested colour for the n-th new player (cycles through the palette). */
    fun default(index: Int): PlayerColor = all[Math.floorMod(index, all.size)]

    /** Palette entry for a stored colour, or null for a custom/unknown value. */
    fun find(argb: Long): PlayerColor? = all.firstOrNull { it.argb == argb || it.light.toArgbLong() == argb }

    private fun swatch(name: String, dark: Long, light: Long) = PlayerColor(name, dark, Color(dark), Color(light))

    private fun Color.toArgbLong(): Long = toArgb().toLong() and 0xFFFFFFFFL
}

/**
 * The tone to draw a stored player colour with in the current theme. Palette colours map to their
 * theme tone; unknown values are drawn as stored.
 */
@Composable
@ReadOnlyComposable
fun playerColor(argb: Long): Color {
    val swatch = PlayerColors.find(argb) ?: return Color(argb.toInt())
    return if (GoaTheme.isDark) swatch.dark else swatch.light
}
