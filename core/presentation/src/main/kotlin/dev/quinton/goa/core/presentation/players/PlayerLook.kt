package dev.quinton.goa.core.presentation.players

import dev.quinton.goa.core.model.player.Player

const val MAX_NAME_LENGTH = 24

/**
 * A player's name, colour and crest as the player editor (and the pilot picker's quick add) sets
 * them. The palette and crest count are the design system's (`PlayerColors`, `PlayerCrests` on the
 * phone, `PlayerPalette` on the hub); pass their stored ARGB values.
 */
object PlayerLook {
    /** A colour (stored ARGB) and a crest index. */
    data class Look(val color: Long, val crest: Int)

    /**
     * A new player's colour and crest, never a pair someone in [existing] already has (players
     * added on two phones before a sync could otherwise look identical). The colour is the first
     * one nobody uses yet, else the palette cycled by player count; the crest is cycled by player
     * count, moved on to the next free one when that pair is taken. Only when every pair is taken
     * (palette × crests players) can two look the same. Pass every player known, archived included.
     */
    fun newLook(existing: List<Player>, palette: List<Long>, crests: Int): Look {
        val usedColors = existing.mapTo(HashSet()) { it.color }
        val usedLooks = existing.mapTo(HashSet()) { Look(it.color, it.avatarId) }
        val crest = existing.size % crests
        val start = Math.floorMod(existing.size, palette.size)
        val cycled = palette.drop(start) + palette.take(start)
        val colors = palette.filter { it !in usedColors } + cycled.filter { it in usedColors }
        for (color in colors) {
            for (step in 0 until crests) {
                val look = Look(color, (crest + step) % crests)
                if (look !in usedLooks) return look
            }
        }
        return Look(palette[start], crest)
    }

    /**
     * Another active player who looks exactly like [player] (same colour and crest), or null.
     * Synced data is never changed for it; the player page offers "pick a different colour or crest?".
     */
    fun lookAlike(player: Player, players: List<Player>): Player? = if (player.archived) {
        null
    } else {
        players.firstOrNull {
            it.id != player.id && !it.archived && it.color == player.color && it.avatarId == player.avatarId
        }
    }

    /** The player page's hint for a [lookAlike]. */
    fun lookAlikeHint(other: Player): String = "Looks like ${other.name}: pick a different colour or crest?"

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
