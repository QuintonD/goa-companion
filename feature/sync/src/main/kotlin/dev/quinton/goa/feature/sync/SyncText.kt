package dev.quinton.goa.feature.sync

import dev.quinton.goa.core.domain.player.PlayerNames
import dev.quinton.goa.core.domain.sync.SyncChanges
import dev.quinton.goa.core.domain.sync.protocol.ClientPhase
import dev.quinton.goa.core.domain.sync.protocol.PeerProgress
import dev.quinton.goa.core.domain.sync.protocol.PeerStage
import dev.quinton.goa.core.domain.sync.protocol.SyncDevice
import dev.quinton.goa.core.domain.sync.protocol.SyncFailure
import dev.quinton.goa.core.model.player.Player
import java.time.Duration

/** Words for group sync (ADR-025). Pure, so the copy is unit-tested. */
object SyncText {
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

    fun peerStage(peer: PeerProgress): String = when (peer.stage) {
        PeerStage.VERIFYING -> "Checking the group key…"
        PeerStage.RECEIVING -> "Sending its data…"
        PeerStage.READY -> "Ready"
        PeerStage.SENDING -> "Receiving the merged data…"
        PeerStage.CONFIRMING -> "Saving…"
        PeerStage.DONE -> "Synced"
        PeerStage.FAILED -> peerFailure(peer)
    }

    /** A joined phone's failure, as the host should read it (its data may or may not be in). */
    fun peerFailure(peer: PeerProgress): String = when (peer.failure) {
        SyncFailure.WRONG_KEY -> "Not in this group (or an old invite). Scan the invite again."

        SyncFailure.VERSION_MISMATCH, SyncFailure.INVALID_DATA, SyncFailure.REJECTED ->
            peer.message ?: "Stopped"

        else -> when (peer.failedAt) {
            PeerStage.CONFIRMING -> "Lost contact while saving: it may be up to date already. Sync again to be sure."
            PeerStage.READY, PeerStage.SENDING -> "Dropped out. Its data is in; it gets everyone's at the next sync."
            else -> "Dropped out before its data arrived. Sync again."
        }
    }

    fun clientPhase(phase: ClientPhase, host: String?): String = when (phase) {
        ClientPhase.SEARCHING -> "Looking for the host…"
        ClientPhase.CHOOSING -> "More than one phone is hosting. Which one?"
        ClientPhase.CONNECTING -> "Connecting to ${host ?: "the host"}…"
        ClientPhase.VERIFYING -> "Checking the group key…"
        ClientPhase.SENDING -> "Sending this phone's data…"
        ClientPhase.WAITING -> "Waiting for ${host ?: "the host"} to tap Sync…"
        ClientPhase.RECEIVING -> "Receiving the merged data…"
        ClientPhase.APPLYING -> "Saving…"
        ClientPhase.DONE -> "Synced with ${host ?: "the host"}"
        ClientPhase.FAILED -> "Sync stopped"
    }

    /** What went wrong, and what to do. [detail] is the protocol's own message, when it has one. */
    fun failure(failure: SyncFailure, detail: String?): String = when (failure) {
        SyncFailure.TIMEOUT -> "It took too long. Keep the phones close together and try again."
        SyncFailure.DISCONNECTED -> "The connection dropped. Nothing changed on this phone; try again."
        SyncFailure.WRONG_KEY -> "That phone isn't in this group (or has an old invite). Scan the invite again."
        SyncFailure.VERSION_MISMATCH -> detail ?: "The phones have different versions of the app. Update both."
        SyncFailure.INVALID_DATA -> detail ?: "The data couldn't be used."
        SyncFailure.APPLY_FAILED -> "The data couldn't be saved. Nothing changed."
        SyncFailure.REJECTED -> detail ?: "The other phone stopped the sync."
        SyncFailure.NO_HOST -> "No host found. One phone taps Host, the others tap Join, all close together."
        SyncFailure.UNAVAILABLE -> detail ?: "Nearby isn't available. Use a sync file instead."
        SyncFailure.PROTOCOL -> "Something went wrong talking to the other phone. Try again."
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

    /**
     * Phones on another app version may know different heroes, so hero ratings differ between
     * them until everyone updates (the data itself is the same). Null when versions match.
     */
    fun versionHint(mine: String?, others: List<SyncDevice>): String? {
        if (mine == null) return null
        val different = others.filter { it.appVersion != null && it.appVersion != mine }.distinctBy { it.deviceId }
        if (different.isEmpty()) return null
        val list = different.joinToString { "${it.name} (${it.appVersion})" }
        return "Not every phone has the same app version: $list, this phone $mine. The data is the same, but " +
            "ratings can differ where a phone doesn't know a hero yet. Update every phone to the same version."
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
}
