package dev.quinton.goa.core.domain.sync.protocol

import dev.quinton.goa.core.domain.sync.GroupCredentials
import dev.quinton.goa.core.domain.sync.SecureBytes
import dev.quinton.goa.core.domain.sync.SyncChanges
import dev.quinton.goa.core.domain.sync.SyncCodec
import dev.quinton.goa.core.domain.sync.SyncDocument
import dev.quinton.goa.core.domain.sync.SyncFormatException
import dev.quinton.goa.core.domain.sync.SyncMerge
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

enum class PeerStage { VERIFYING, RECEIVING, READY, SENDING, CONFIRMING, DONE, FAILED }

/** One joined phone as the host sees it. */
data class PeerProgress(
    val linkId: String,
    val name: String,
    val device: SyncDevice? = null,
    val stage: PeerStage = PeerStage.VERIFYING,
    /** 0..1 while data moves, else null. */
    val progress: Float? = null,
    val failure: SyncFailure? = null,
    val message: String? = null,
    /**
     * The stage the phone was at when it failed: before READY its data never arrived; from READY on
     * it was merged; in CONFIRMING it may already have saved the result.
     */
    val failedAt: PeerStage? = null,
)

enum class HostPhase { WAITING, MERGING, SENDING, DONE, FAILED }

data class HostState(
    val phase: HostPhase = HostPhase.WAITING,
    val peers: List<PeerProgress> = emptyList(),
    /** What the sync changed on the host. */
    val changes: SyncChanges? = null,
    val failure: SyncFailure? = null,
    val message: String? = null,
) {
    /** At least one phone's data is in: "Sync now" can run. */
    val canSync: Boolean get() = phase == HostPhase.WAITING && peers.any { it.stage == PeerStage.READY }

    /** Phones that now hold the merged data. */
    val synced: List<SyncDevice> get() = peers.filter { it.stage == PeerStage.DONE }.mapNotNull { it.device }
}

/**
 * The host of a sync session (ADR-025): advertises, lets the group's phones join, collects each
 * phone's full document, merges them into its own data and sends the merged document back to
 * every phone, so all of them end up with identical data.
 *
 * [run] does the whole session; [syncNow] moves it from collecting to merging. Cancel the
 * coroutine running [run] to stop: links close, and phones that hadn't received the merged data
 * keep theirs untouched. A phone whose upload arrived before it dropped still has its data
 * merged (it gets the result next time).
 */
class SyncHost(
    private val transport: SyncTransport,
    private val group: GroupCredentials,
    private val me: SyncDevice,
    private val data: SyncDataPort,
    private val random: SecureBytes = SecureBytes.System,
    private val timeouts: SyncTimeouts = SyncTimeouts(),
) {
    private val mutableState = MutableStateFlow(HostState())
    val state: StateFlow<HostState> = mutableState.asStateFlow()

    private val start = CompletableDeferred<Unit>()
    private val peers = ArrayList<Peer>()

    @Volatile private var accepting = true

    private class Peer(val link: SyncLink) {
        val upload = CompletableDeferred<SyncDocument?>()
        val merged = CompletableDeferred<SyncDocument?>()
    }

    /** Stop accepting phones; merge what has arrived (waiting for uploads still under way). */
    fun syncNow() {
        start.complete(Unit)
    }

    suspend fun run() {
        try {
            coroutineScope {
                val session = this
                val advertising = launch {
                    try {
                        transport.advertise(SyncEndpointName.format(group.tag, me.name)).collect { link ->
                            accept(link)?.let { peer -> session.launch { serve(peer) } }
                        }
                    } catch (e: SyncTransportException) {
                        mutableState.update {
                            it.copy(phase = HostPhase.FAILED, failure = SyncFailure.UNAVAILABLE, message = e.message)
                        }
                        start.completeExceptionally(e)
                    }
                }
                val started = try {
                    start.await()
                    true
                } catch (_: SyncTransportException) {
                    false // already shown as FAILED
                }
                accepting = false
                advertising.cancel()
                val joined = synchronized(peers) { peers.toList() }
                if (started) {
                    mutableState.update { it.copy(phase = HostPhase.MERGING) }
                    merge(joined)
                } else {
                    joined.forEach { it.merged.complete(null) }
                }
            }
            // Every phone's serve() has ended (DONE or FAILED) once the scope returns.
            mutableState.update { if (it.phase == HostPhase.SENDING) it.copy(phase = HostPhase.DONE) else it }
        } finally {
            synchronized(peers) { peers.forEach { it.link.close() } }
        }
    }

    /** A phone of this group joining while the host still accepts; anyone else is hung up on. */
    private fun accept(link: SyncLink): Peer? {
        val (tag, name) = SyncEndpointName.parse(link.peer.endpointName) ?: ("" to "")
        if (!accepting || tag != group.tag) {
            link.close()
            return null
        }
        val peer = Peer(link)
        synchronized(peers) { peers += peer }
        mutableState.update { it.copy(peers = it.peers + PeerProgress(link.peer.id, name.ifBlank { "Phone" })) }
        return peer
    }

    private suspend fun merge(joined: List<Peer>) {
        val uploads = joined.map { it to it.upload.await() }.filter { it.second != null }
        if (uploads.isEmpty()) {
            joined.forEach { it.merged.complete(null) }
            mutableState.update {
                it.copy(
                    phase = HostPhase.FAILED,
                    failure = SyncFailure.DISCONNECTED,
                    message = "No phone sent its data.",
                )
            }
            return
        }
        val result = try {
            data.apply(SyncMerge.mergeAll(uploads.mapNotNull { it.second }))
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            joined.forEach { it.merged.complete(null) }
            mutableState.update {
                it.copy(phase = HostPhase.FAILED, failure = SyncFailure.APPLY_FAILED, message = e.message)
            }
            return
        }
        mutableState.update { it.copy(phase = HostPhase.SENDING, changes = result.changes) }
        joined.forEach { it.merged.complete(result.document) }
    }

    @Suppress("SwallowedException", "ThrowsCount") // A timeout becomes the peer's TIMEOUT state.
    private suspend fun serve(peer: Peer) {
        val id = peer.link.peer.id
        var channel: SecureChannel? = null
        try {
            val (secure, device) = withTimeout(timeouts.handshakeMs) {
                handshake(peer.link, Role.HOST, group, me, random, transport.maxMessageBytes)
            }
            channel = secure
            update(id) { it.copy(name = device.name, device = device, stage = PeerStage.RECEIVING, progress = 0f) }
            val upload = withTimeout(timeouts.uploadMs) {
                secure.receive { _, f -> update(id) { it.copy(progress = f) } }
            }
            if (upload.type != MessageType.UPLOAD) throw SyncProtocolException(SyncFailure.PROTOCOL, "Expected data")
            val doc = try {
                SyncCodec.decode(upload.data)
            } catch (e: SyncFormatException) {
                secure.sendAbort(e.message.orEmpty())
                throw SyncProtocolException(SyncFailure.INVALID_DATA, e.message.orEmpty(), e)
            }
            secure.send(MessageType.RECEIVED)
            peer.upload.complete(doc)
            update(id) { it.copy(stage = PeerStage.READY, progress = null) }

            val merged = peer.merged.await()
            if (merged == null) {
                secure.sendAbort("The host couldn't finish the sync. Nothing changed on this phone.")
                throw SyncProtocolException(SyncFailure.APPLY_FAILED, "Stopped before sending")
            }
            update(id) { it.copy(stage = PeerStage.SENDING, progress = 0f) }
            withTimeout(timeouts.downloadMs) {
                secure.send(MessageType.MERGED, SyncCodec.encode(merged)) { f -> update(id) { it.copy(progress = f) } }
            }
            update(id) { it.copy(stage = PeerStage.CONFIRMING, progress = null) }
            val reply = withTimeout(timeouts.ackMs) { secure.receive() }
            val ack = when (reply.type) {
                MessageType.ACK -> parseAck(reply)
                MessageType.ABORT -> Ack(ok = false, error = reply.text)
                else -> throw SyncProtocolException(SyncFailure.PROTOCOL, "Expected a confirmation")
            }
            if (!ack.ok) {
                throw SyncProtocolException(
                    SyncFailure.REJECTED,
                    ack.error ?: "The phone couldn't save the data.",
                )
            }
            update(id) { it.copy(stage = PeerStage.DONE) }
        } catch (e: TimeoutCancellationException) {
            channel?.sendAbort("The sync took too long.")
            fail(id, SyncFailure.TIMEOUT, "Took too long")
        } catch (e: SyncProtocolException) {
            fail(id, e.failure, e.message)
        } finally {
            peer.upload.complete(null)
            peer.link.close()
        }
    }

    private fun fail(id: String, failure: SyncFailure, message: String?) {
        update(id) {
            it.copy(
                stage = PeerStage.FAILED,
                failure = failure,
                message = message,
                progress = null,
                failedAt = it.stage,
            )
        }
    }

    private fun update(linkId: String, transform: (PeerProgress) -> PeerProgress) = mutableState.update { state ->
        state.copy(peers = state.peers.map { if (it.linkId == linkId) transform(it) else it })
    }
}
