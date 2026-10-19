package dev.quinton.goa.hub.web.ui

import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.core.presentation.players.PlayerLook

/**
 * The player palette (DESIGN.md, `PlayerColors` in core:designsystem): `Player.color` stores the
 * dark tone; each swatch has a theme tone for light pages. The CSP forbids inline styles, so a
 * player's colour is a class (`pc-0` … `pc-9`) defined in `static/app.css` for both themes.
 * Keep in step with `PlayerColors`.
 */
object PlayerPalette {
    /** (dark tone, light tone) per swatch, in the app's order. */
    val swatches: List<Pair<Long, Long>> = listOf(
        0xFF3FC1B0 to 0xFF186E62, // Tide teal
        0xFFD9B44A to 0xFF7A5E24, // Bronze
        0xFFEE7F6F to 0xFFB23F2F, // Coral
        0xFF62A8F2 to 0xFF2A62AE, // Sky
        0xFFB892E6 to 0xFF6B45A0, // Amethyst
        0xFF6CC27A to 0xFF2A7037, // Kelp
        0xFFEE85B8 to 0xFFA83A70, // Rose
        0xFFEE9A52 to 0xFF9A4E0E, // Ember
        0xFFC3CCD6 to 0xFF56616C, // Pearl
        0xFFAECF55 to 0xFF55691A, // Lime
    )

    const val CRESTS = 24
    const val UNKNOWN = "pc-unknown"

    /** A new player's colour and crest, a pair none of [players] has ([PlayerLook.newLook]). */
    fun newLook(players: List<Player>): PlayerLook.Look = PlayerLook.newLook(players, swatches.map { it.first }, CRESTS)

    /** The swatch index of a stored colour (either tone), or null for a custom value. */
    fun indexOf(argb: Long): Int? {
        val index = swatches.indexOfFirst { (dark, light) -> dark == argb || light == argb }
        return index.takeIf { it >= 0 }
    }

    /** The CSS class for a stored colour; a custom value gets the nearest swatch. */
    fun cssClass(argb: Long): String = "pc-${indexOf(argb) ?: nearest(argb)}"

    private fun nearest(argb: Long): Int = swatches.indices.minBy { i ->
        val c = swatches[i].first
        listOf(16, 8, 0).sumOf { shift ->
            val d = ((argb shr shift) and 0xFF) - ((c shr shift) and 0xFF)
            d * d
        }
    }
}
