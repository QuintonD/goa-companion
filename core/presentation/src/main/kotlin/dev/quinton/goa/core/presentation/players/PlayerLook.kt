package dev.quinton.goa.core.presentation.players

import dev.quinton.goa.core.model.player.Player

const val MAX_NAME_LENGTH = 24

/**
 * A player's name, colour and crest as the player editor (and the pilot picker's quick add) sets
 * them. The palette and crest count are the design system's (`PlayerColors`, `PlayerCrests` on the
 * phone, `PlayerPalette` on the hub); pass their stored ARGB values.
 */
object PlayerLook {
    /** A new player's colour: the first one nobody uses yet, else the palette cycled by player count. */
    fun newColor(existing: List<Player>, palette: List<Long>): Long {
        val used = existing.map { it.color }.toSet()
        return palette.firstOrNull { it !in used } ?: palette[Math.floorMod(existing.size, palette.size)]
    }

    /** A new player's crest: cycled by player count. */
    fun newCrest(existing: List<Player>, crests: Int): Int = existing.size % crests

    /** The name field as typed: at most [MAX_NAME_LENGTH] characters. */
    fun nameInput(text: String): String = text.take(MAX_NAME_LENGTH)

    /**
     * Why [name] (trimmed) can't be saved, or null: empty, or another player (archived included)
     * already has it — [clash] is `findByName(name)`, [editingId] the player being edited.
     */
    fun nameError(name: String, clash: Player?, editingId: String?): String? = when {
        name.isEmpty() -> "Enter a name"
        clash != null && clash.id != editingId -> "“$name” is already in your group"
        else -> null
    }
}
