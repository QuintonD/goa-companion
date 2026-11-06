package dev.quinton.goa.feature.sync

import dev.quinton.goa.core.domain.sync.SyncChanges
import dev.quinton.goa.core.domain.sync.protocol.ClientPhase
import dev.quinton.goa.core.domain.sync.protocol.PeerProgress
import dev.quinton.goa.core.domain.sync.protocol.PeerStage
import dev.quinton.goa.core.domain.sync.protocol.SyncDevice
import dev.quinton.goa.core.domain.sync.protocol.SyncFailure
import dev.quinton.goa.core.model.player.Player

/** Words for group sync (ADR-025). Pure, so the copy is unit-tested. */
object SyncText {
    /** "+5 matches, +1 player, +3 trivia rounds, 2 updated, 1 removed", or "Already up to date". */
    fun summary(changes: SyncChanges): String = SyncWords.summary(changes)

    fun peerStage(peer: PeerProgress): String = when (peer.stage) {
        PeerStage.VERIFYING -> "Checking the group key…"
        PeerStage.RECEIVING -> "Sending its data…"
        PeerStage.READY -> "Ready"
        PeerStage.SENDING -> "Receiving the merged data…"
        PeerStage.CONFIRMING -> "Saving…"
        PeerStage.DONE -> "Synced"
        PeerStage.FAILED -> peerFailure(peer)
    }

    /**
     * The phones that Sync takes, while the host waits: those ready and those still sending their data
     * (Sync waits for their uploads). Failed phones are left out.
     */
    fun syncing(peers: List<PeerProgress>): List<PeerProgress> =
        peers.filter {
            it.stage == PeerStage.READY || it.stage == PeerStage.VERIFYING || it.stage == PeerStage.RECEIVING
        }

    /** "1 ready · 1 sending its data · 1 needs a new invite": every waiting phone counted by state. */
    fun hostWaiting(peers: List<PeerProgress>): String {
        val ready = peers.count { it.stage == PeerStage.READY }
        val sending = peers.count { it.stage == PeerStage.VERIFYING || it.stage == PeerStage.RECEIVING }
        val failed = peers.filter { it.stage == PeerStage.FAILED }
        val invite = failed.count { it.failure == SyncFailure.WRONG_KEY }
        val dropped = failed.size - invite
        return listOfNotNull(
            "$ready ready".takeIf { ready > 0 || peers.isEmpty() },
            "$sending sending ${if (sending == 1) "its" else "their"} data".takeIf { sending > 0 },
            "$invite ${if (invite == 1) "needs" else "need"} a new invite".takeIf { invite > 0 },
            "$dropped dropped out".takeIf { dropped > 0 },
        ).joinToString(" · ")
    }

    /** What tapping Sync does with these phones, in a line; null when no phone is in. */
    fun hostWaitingHint(peers: List<PeerProgress>): String? {
        val sending = peers.filter { it.stage == PeerStage.VERIFYING || it.stage == PeerStage.RECEIVING }
        val out = peers.filter { it.stage == PeerStage.FAILED }
        if (syncing(peers).isEmpty()) return null
        return listOfNotNull(
            "Sync waits for ${names(sending)}'s data.".takeIf { sending.isNotEmpty() },
            "${names(out)} ${if (out.size == 1) "isn't" else "aren't"} included.".takeIf { out.isNotEmpty() },
        ).joinToString(" ").ifEmpty { null }
    }

    /** The host's button: "Sync with Bob and Cid", "Sync 4 phones", or "Sync" before anyone is in. */
    fun syncLabel(peers: List<PeerProgress>): String {
        val taken = syncing(peers)
        return when {
            taken.isEmpty() -> "Sync"
            taken.size <= NAMED_PHONES -> "Sync with ${names(taken)}"
            else -> "Sync ${taken.size} phones"
        }
    }

    private fun names(peers: List<PeerProgress>): String {
        val all = peers.map { it.name }
        return if (all.size <= 1) all.joinToString() else all.dropLast(1).joinToString(", ") + " and " + all.last()
    }

    /** Up to this many phones, the Sync button names them. */
    private const val NAMED_PHONES = 2

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
    fun ago(at: Long, now: Long): String = SyncWords.ago(at, now)

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
    fun duplicateNames(players: List<Player>): List<String> = SyncWords.duplicateNames(players)

    fun duplicateHint(names: List<String>): String? = SyncWords.duplicateHint(names)
}
