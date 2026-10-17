package dev.quinton.goa.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

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
    val all: List<PlayerColor> = listOf(
        swatch("Tide teal", 0xFF3FC1B0, 0xFF186E62),
        swatch("Bronze", 0xFFD9B44A, 0xFF7A5E24),
        swatch("Coral", 0xFFEE7F6F, 0xFFB23F2F),
        swatch("Sky", 0xFF62A8F2, 0xFF2A62AE),
        swatch("Amethyst", 0xFFB892E6, 0xFF6B45A0),
        swatch("Kelp", 0xFF6CC27A, 0xFF2A7037),
        swatch("Rose", 0xFFEE85B8, 0xFFA83A70),
        swatch("Ember", 0xFFEE9A52, 0xFF9A4E0E),
        swatch("Pearl", 0xFFC3CCD6, 0xFF56616C),
        swatch("Lime", 0xFFAECF55, 0xFF55691A),
    )

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
