package dev.quinton.goa.core.model.player

/**
 * The player palette and crest count, shared by the phone (core:designsystem `PlayerColors`,
 * `PlayerCrests`) and the web app, so a player added on either gets the same look (ADR-043).
 */
object PlayerPalette {
    /**
     * One swatch: [argb] is the value stored in [Player.color] and the tone drawn in the dark theme,
     * [lightArgb] the tone drawn in the light theme.
     */
    data class Swatch(val name: String, val argb: Long, val lightArgb: Long)

    /** Ten colours readable in both themes (docs/DESIGN.md). */
    val all: List<Swatch> = listOf(
        Swatch("Tide teal", 0xFF3FC1B0, 0xFF186E62),
        Swatch("Bronze", 0xFFD9B44A, 0xFF7A5E24),
        Swatch("Coral", 0xFFEE7F6F, 0xFFB23F2F),
        Swatch("Sky", 0xFF62A8F2, 0xFF2A62AE),
        Swatch("Amethyst", 0xFFB892E6, 0xFF6B45A0),
        Swatch("Kelp", 0xFF6CC27A, 0xFF2A7037),
        Swatch("Rose", 0xFFEE85B8, 0xFFA83A70),
        Swatch("Ember", 0xFFEE9A52, 0xFF9A4E0E),
        Swatch("Pearl", 0xFFC3CCD6, 0xFF56616C),
        Swatch("Lime", 0xFFAECF55, 0xFF55691A),
    )

    /** How many original crests there are (`design/avatars/crest_NN.svg`); [Player.avatarId] indexes them. */
    const val CREST_COUNT = 24

    /**
     * Palette colours for players added in a hurry (the draft's quick add), minus Coral and Sky: next
     * to the Red and Blue team colours they read as a team.
     */
    val quickAdd: List<Swatch> = all.filter { it.name != "Coral" && it.name != "Sky" }

    /** Suggested colour for the n-th new player (cycles through the palette). */
    fun default(index: Int): Swatch = all[index.mod(all.size)]

    /** Palette entry for a stored colour (either tone), or null for a custom/unknown value. */
    fun find(argb: Long): Swatch? = all.firstOrNull { it.argb == argb || it.lightArgb == argb }

    /**
     * A new player's colour and crest when added in passing (trivia's "Add player", "Who are you?"):
     * the first palette colour nobody in [existing] (archived included) has, else the next in turn;
     * the crest after the last one handed out.
     */
    fun forNew(existing: List<Player>): Pair<Long, Int> {
        val used = existing.map { it.color }.toSet()
        val color = (all.firstOrNull { it.argb !in used } ?: default(existing.size)).argb
        return color to existing.size % CREST_COUNT
    }
}
