package dev.quinton.goa.core.presentation.group

import dev.quinton.goa.core.domain.sync.SyncChanges
import java.time.Duration

/** Words for group sync shared by the phone (`SyncText`) and the hub's pages. */
object SyncWords {
    private fun count(n: Int, one: String, many: String = "${one}s") = "$n ${if (n == 1) one else many}"

    /** "+5 matches, +1 player, 2 updated, 1 removed", or "Already up to date". */
    fun summary(changes: SyncChanges): String {
        val parts = buildList {
            if (changes.matchesAdded > 0) add("+${count(changes.matchesAdded, "match", "matches")}")
            if (changes.playersAdded > 0) add("+${count(changes.playersAdded, "player")}")
            if (changes.draftsAdded > 0) add("+${count(changes.draftsAdded, "draft")}")
            val updated = changes.matchesUpdated + changes.playersUpdated + changes.draftsUpdated
            if (updated > 0) add("$updated updated")
            val removed = changes.matchesRemoved + changes.draftsRemoved
            if (removed > 0) add("$removed removed")
            if (changes.playersMerged > 0) add("${count(changes.playersMerged, "duplicate")} merged")
            if (changes.ratingConstantsChanged) add("new rating constants")
        }
        return if (parts.isEmpty()) "Already up to date" else parts.joinToString(", ")
    }

    /** "just now", "5 min ago", "3 h ago", "2 days ago". */
    fun ago(at: Long, now: Long): String {
        val d = Duration.ofMillis((now - at).coerceAtLeast(0))
        return when {
            d.toMinutes() < 1 -> "just now"
            d.toHours() < 1 -> "${d.toMinutes()} min ago"
            d.toDays() < 1 -> "${d.toHours()} h ago"
            else -> "${count(d.toDays().toInt(), "day")} ago"
        }
    }
}
