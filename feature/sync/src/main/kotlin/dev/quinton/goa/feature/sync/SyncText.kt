package dev.quinton.goa.feature.sync

import dev.quinton.goa.core.domain.sync.SyncChanges
import dev.quinton.goa.core.domain.sync.protocol.ClientPhase
import dev.quinton.goa.core.domain.sync.protocol.HostPhase
import dev.quinton.goa.core.domain.sync.protocol.HostState
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

        PeerStage.RECEIVING -> peer.progress?.let { "Sending its data… ${(it * PERCENT).toInt()}%" }
            ?: "Sending its data…"

        PeerStage.READY -> "Ready"

        PeerStage.SENDING -> "Receiving the merged data…"

        PeerStage.CONFIRMING -> "Saving…"

        PeerStage.DONE -> "Synced"

        PeerStage.FAILED -> peerFailure(peer)
    }

    /** The phones the merge takes: every phone that joined and hasn't failed (ready, or still sending its data). */
    fun included(peers: List<PeerProgress>): List<PeerProgress> = peers.filter { it.stage != PeerStage.FAILED }

    /** "2 phones included · 1 excluded": the count only; each phone's own row says where it stands and why. */
    fun hostIncluded(peers: List<PeerProgress>): String {
        val included = included(peers).size
        val excluded = peers.size - included
        return listOfNotNull(
            "$included ${if (included == 1) "phone" else "phones"} included",
            "$excluded excluded".takeIf { excluded > 0 },
        ).joinToString(" · ")
    }

    /** Collecting, and every included phone's data is in (none still sending): Merge runs now; auto-start fires. */
    fun readyToMerge(state: HostState): Boolean = state.phase == HostPhase.WAITING &&
        included(state.peers).let { it.isNotEmpty() && it.all { peer -> peer.stage == PeerStage.READY } }

    /**
     * The host's one command. Every included phone's data in: "Merge data from 2 phones", enabled. A phone still
     * sending: off, naming whom it waits for with the upload's live percent ("Waiting for Cid · 60%"). No phone yet:
     * "Waiting for a phone to join…".
     */
    fun hostAction(peers: List<PeerProgress>): String {
        val included = included(peers)
        val sending = sending(peers)
        return when {
            included.isEmpty() -> "Waiting for a phone to join…"
            sending.isNotEmpty() -> waitingFor(sending)
            else -> "Merge data from ${included.size} ${if (included.size == 1) "phone" else "phones"}"
        }
    }

    /** "Waiting for Cid · 60%", "Waiting for Cid and Dee · 45%", "Waiting for 3 phones · 20%", "Waiting for Cid…". */
    private fun waitingFor(sending: List<PeerProgress>): String {
        val who = who(sending)
        if (sending.none { it.progress != null }) return "Waiting for $who…"
        val percent = (sending.sumOf { (it.progress ?: 0f).toDouble() } / sending.size * PERCENT).toInt()
        return "Waiting for $who · $percent%"
    }

    /** The auto-start switch beside the command, a control of its own: off until the player arms it. */
    const val AUTO_START = "Start automatically when ready"

    /** The armed switch's state, under its label: "On · merges once Cid's data is in". */
    fun autoStartArmed(peers: List<PeerProgress>): String {
        val sending = sending(peers)
        return if (sending.isEmpty()) "On · merges now" else "On · merges once ${who(sending)}'s data is in"
    }

    /** Where a host's sync stands, as the step it is on (0 collect, 1 merge, 2 send back, 3 done). */
    fun hostStep(phase: HostPhase): Int = when (phase) {
        HostPhase.WAITING -> 0
        HostPhase.MERGING -> 1
        HostPhase.SENDING -> 2
        HostPhase.DONE -> 3
        HostPhase.FAILED -> 0
    }

    /** The steps a host's sync goes through, named for the stepper. */
    val hostSteps: List<String> = listOf("Collect", "Merge", "Send back", "Done")

    /** The host merging; a phone that joined at the last moment is still awaited ("Waiting for Cid's data…"). */
    fun hostMerging(peers: List<PeerProgress>): String =
        sending(peers).takeIf { it.isNotEmpty() }?.let { "Waiting for ${who(it)}'s data…" } ?: "Merging…"

    private fun who(peers: List<PeerProgress>) =
        if (peers.size <= NAMED_PHONES) names(peers) else "${peers.size} phones"

    /**
     * The final word on a finished sync: "Every phone is up to date: Bob and Cid", or who made it and who didn't
     * ("Synced with Bob. Cid didn't finish: sync again.").
     */
    fun hostDoneTitle(all: List<PeerProgress>): String {
        val peers = all.filter { it.failure != SyncFailure.WRONG_KEY }
        val done = peers.filter { it.stage == PeerStage.DONE }
        val missed = peers.filter { it.stage != PeerStage.DONE }
        return when {
            done.isEmpty() -> "No other phone got the result"
            missed.isEmpty() && done.size <= NAMED_PHONES -> "Every phone is up to date: ${names(done)}"
            missed.isEmpty() -> "All ${done.size} phones are up to date"
            done.size <= NAMED_PHONES -> "Synced with ${names(done)}"
            else -> "Synced with ${done.size} phones"
        }
    }

    /** Phones that didn't end with the result, for the summary: "Cid didn't finish: sync again to include it." */
    fun hostDoneMissed(all: List<PeerProgress>): String? {
        // A phone with the wrong key was never in the sync: its own row says to scan the invite again.
        val missed = all.filter { it.stage != PeerStage.DONE && it.failure != SyncFailure.WRONG_KEY }
        if (missed.isEmpty()) return null
        val them = if (missed.size == 1) "it" else "them"
        return "${names(missed)} didn't finish: sync again to include $them."
    }

    private fun sending(peers: List<PeerProgress>) =
        peers.filter { it.stage == PeerStage.VERIFYING || it.stage == PeerStage.RECEIVING }

    private fun names(peers: List<PeerProgress>): String {
        val all = peers.map { it.name }
        return if (all.size <= 1) all.joinToString() else all.dropLast(1).joinToString(", ") + " and " + all.last()
    }

    private const val PERCENT = 100

    /** Up to this many phones, the host's command names them. */
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
