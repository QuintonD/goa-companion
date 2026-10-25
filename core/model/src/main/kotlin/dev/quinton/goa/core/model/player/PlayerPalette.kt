package dev.quinton.goa.core.model.player

/**
 * One swatch of the player palette (docs/DESIGN.md): [dark] is the value stored in `Player.color`
 * and the tone drawn in the dark theme, [light] the tone drawn in the light theme (both ARGB).
 */
data class PlayerSwatch(val name: String, val dark: Long, val light: Long)

/**
 * The player palette: 10 colours readable in both themes. The values live here so the phone's
 * design system (`PlayerColors`) and the web app draw and assign the same colours (ADR-043).
 */
object PlayerPalette {
    val all: List<PlayerSwatch> = listOf(
        PlayerSwatch("Tide teal", 0xFF3FC1B0, 0xFF186E62),
        PlayerSwatch("Bronze", 0xFFD9B44A, 0xFF7A5E24),
        PlayerSwatch("Coral", 0xFFEE7F6F, 0xFFB23F2F),
        PlayerSwatch("Sky", 0xFF62A8F2, 0xFF2A62AE),
        PlayerSwatch("Amethyst", 0xFFB892E6, 0xFF6B45A0),
        PlayerSwatch("Kelp", 0xFF6CC27A, 0xFF2A7037),
        PlayerSwatch("Rose", 0xFFEE85B8, 0xFFA83A70),
        PlayerSwatch("Ember", 0xFFEE9A52, 0xFF9A4E0E),
        PlayerSwatch("Pearl", 0xFFC3CCD6, 0xFF56616C),
        PlayerSwatch("Lime", 0xFFAECF55, 0xFF55691A),
    )

    /** Palette entry for a stored colour (either tone), or null for a custom/unknown value. */
    fun find(argb: Long): PlayerSwatch? = all.firstOrNull { it.dark == argb || it.light == argb }

    /**
     * Palette colours for players added in a hurry (the draft's quick add), minus Coral and Sky: next
     * to the Red and Blue team colours they read as a team.
     */
    val quickAdd: List<PlayerSwatch> = all.filter { it.name != "Coral" && it.name != "Sky" }

    /** Number of player crests (`design/avatars/crest_NN.svg`). */
    const val AVATAR_COUNT = 24
}
