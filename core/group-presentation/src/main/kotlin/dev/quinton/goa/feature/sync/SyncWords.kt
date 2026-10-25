package dev.quinton.goa.feature.sync

import dev.quinton.goa.core.data.player.PlayerNames
import dev.quinton.goa.core.domain.sync.SyncChanges
import dev.quinton.goa.core.model.player.Player

/**
 * Words for group sync shared by the phone and the web app (ADR-043); the phone's `SyncText` adds
 * the face-to-face (Nearby) ones. Pure, so the copy is unit-tested.
 */
object SyncWords {
    private fun count(n: Int, one: String, many: String = "${one}s") = "$n ${if (n == 1) one else many}"

    /** "+5 matches, +1 player, +3 trivia rounds, 2 updated, 1 removed", or "Already up to date". */
    fun summary(changes: SyncChanges): String {
        val parts = buildList {
            if (changes.matchesAdded > 0) add("+${count(changes.matchesAdded, "match", "matches")}")
            if (changes.playersAdded > 0) add("+${count(changes.playersAdded, "player")}")
            if (changes.draftsAdded > 0) add("+${count(changes.draftsAdded, "draft")}")
            if (changes.triviaRoundsAdded > 0) add("+${count(changes.triviaRoundsAdded, "trivia round")}")
            val updated = changes.matchesUpdated + changes.playersUpdated + changes.draftsUpdated +
                changes.triviaRoundsUpdated
            if (updated > 0) add("$updated updated")
            val removed = changes.matchesRemoved + changes.draftsRemoved + changes.triviaRoundsRemoved
            if (removed > 0) add("$removed removed")
            if (changes.playersMerged > 0) add("${count(changes.playersMerged, "duplicate")} merged")
            if (changes.ratingConstantsChanged) add("new rating constants")
        }
        return if (parts.isEmpty()) "Already up to date" else parts.joinToString(", ")
    }

    /** "just now", "5 min ago", "3 h ago", "2 days ago". */
    fun ago(at: Long, now: Long): String {
        val minutes = (now - at).coerceAtLeast(0) / MINUTE_MS
        return when {
            minutes < 1 -> "just now"
            minutes < MINUTES_PER_HOUR -> "$minutes min ago"
            minutes < MINUTES_PER_DAY -> "${minutes / MINUTES_PER_HOUR} h ago"
            else -> "${count((minutes / MINUTES_PER_DAY).toInt(), "day")} ago"
        }
    }

    /** Names more than one (visible) player has: duplicates that "Merge into…" can fix. */
    fun duplicateNames(players: List<Player>): List<String> = players
        .groupBy { PlayerNames.key(it.name) }
        .filterValues { it.size > 1 }
        .map { (_, same) -> same.minBy { it.createdAt }.name }
        .sorted()

    fun duplicateHint(names: List<String>): String? = when (names.size) {
        0 -> null

        1 -> "Two players are called “${names.single()}”. If it's one person, open either and use Merge into…"

        else -> {
            val listed = names.joinToString { "“$it”" }
            "Some names appear twice ($listed). Open one of each and use Merge into…"
        }
    }

    private const val MINUTE_MS = 60_000L
    private const val MINUTES_PER_HOUR = 60L
    private const val MINUTES_PER_DAY = 24 * 60L
}
