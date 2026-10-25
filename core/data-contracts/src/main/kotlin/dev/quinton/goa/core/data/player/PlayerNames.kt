package dev.quinton.goa.core.data.player

import dev.quinton.goa.core.domain.sync.PlayerMerges
import dev.quinton.goa.core.model.player.Player

/**
 * Player names are unique in the group, compared trimmed and case-insensitively, so one person
 * never ends up with two ids (and two split ratings).
 */
object PlayerNames {
    /** Locale-independent on every platform (Kotlin's `lowercase()` is `Locale.ROOT` on the JVM). */
    fun key(name: String): String = name.trim().lowercase()

    fun same(a: String, b: String): Boolean = key(a) == key(b)

    /**
     * [dev.quinton.goa.core.data.PlayerRepository.findByName] over the stored [players] (oldest
     * first, archived included): the first visible one called [name], merged-away duplicates
     * skipped. Null if none.
     */
    fun find(players: List<Player>, merges: PlayerMerges, name: String): Player? {
        val key = key(name)
        return merges.visible(players).firstOrNull { key(it.name) == key }
    }
}
