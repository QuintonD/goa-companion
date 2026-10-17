package dev.quinton.goa.core.domain.sync.protocol

import dev.quinton.goa.core.domain.sync.GroupCredentials
import dev.quinton.goa.core.domain.sync.SecureBytes
import dev.quinton.goa.core.domain.sync.SyncChanges
import dev.quinton.goa.core.domain.sync.SyncCodec
import dev.quinton.goa.core.domain.sync.SyncFormatException
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
import kotlinx.coroutines.withTimeoutOrNull

enum class ClientPhase {
    SEARCHING,
    CHOOSING,
    CONNECTING,
    VERIFYING,
    SENDING,
    WAITING,
    RECEIVING,
    APPLYING,
    DONE,
    FAILED,
}

/** A host of this phone's group, found nearby. */
data class HostOption(val peer: SyncPeer, val name: String)

data class ClientState(
    val phase: ClientPhase = ClientPhase.SEARCHING,
    /** Hosts of this group in range (more than one: the user picks). */
    val hosts: List<HostOption> = emptyList(),
    /** The host's name once chosen. */
    val hostName: String? = null,
    /** The host's device, once verified. */
    val host: SyncDevice? = null,
    /** 0..1 while data moves. */
    val progress: Float? = null,
    val changes: SyncChanges? = null,
    val failure: SyncFailure? = null,
    val message: String? = null,
)

/**
 * A phone joining a sync session (ADR-025): finds its group's host (automatically when there is
 * exactly one), proves the key, sends its full document, waits for the merged document and
 * merges it in with [SyncDataPort.apply]. Nothing is written before the merged document has
 * arrived whole and passed validation, so a dropped connection leaves this phone untouched.
 */
class SyncClient(
    private val transport: SyncTransport,
    private val group: GroupCredentials,
    private val me: SyncDevice,
    private val data: SyncDataPort,
    private val random: SecureBytes = SecureBytes.System,
    private val timeouts: SyncTimeouts = SyncTimeouts(),
) {
    private val mutableState = MutableStateFlow(ClientState())
    val state: StateFlow<ClientState> = mutableState.asStateFlow()

    private val chosen = CompletableDeferred<SyncPeer>()

    /** Joins [host] (when several hosts of this group are in range). */
    fun choose(host: SyncPeer) {
        chosen.complete(host)
    }

    @Suppress("SwallowedException") // A timeout becomes the TIMEOUT state.
    suspend fun run() {
        val host = search() ?: return
        val name = SyncEndpointName.parse(host.endpointName)?.second ?: "Host"
        mutableState.update { it.copy(phase = ClientPhase.CONNECTING, hostName = name) }
        val link = try {
            withTimeout(timeouts.handshakeMs) { transport.connect(SyncEndpointName.format(group.tag, me.name), host) }
        } catch (e: TimeoutCancellationException) {
            return fail(SyncFailure.TIMEOUT, "Couldn't reach $name.")
        } catch (e: SyncLinkClosedException) {
            return fail(SyncFailure.DISCONNECTED, e.message ?: "Couldn't reach $name.")
        }
        try {
            exchange(link)
        } finally {
            link.close()
        }
    }

    private suspend fun search(): SyncPeer? = coroutineScope {
        val watching = launch {
            transport.discover().collect { all ->
                val mine = all.mapNotNull { peer ->
                    SyncEndpointName.parse(peer.endpointName)
                        ?.takeIf { (tag, _) -> tag == group.tag }
                        ?.let { (_, name) -> HostOption(peer, name) }
                }
                mutableState.update {
                    it.copy(hosts = mine, phase = if (mine.size > 1) ClientPhase.CHOOSING else ClientPhase.SEARCHING)
                }
                if (mine.size == 1) chosen.complete(mine.single().peer)
            }
        }
        val host = withTimeoutOrNull(timeouts.searchMs) { chosen.await() }
        watching.cancel()
        if (host == null) {
            fail(SyncFailure.NO_HOST, "No host found. One phone taps Host, the others tap Join.")
        }
        host
    }

    @Suppress("SwallowedException", "ThrowsCount")
    private suspend fun exchange(link: SyncLink) {
        var channel: SecureChannel? = null
        try {
            mutableState.update { it.copy(phase = ClientPhase.VERIFYING) }
            val (secure, host) = withTimeout(timeouts.handshakeMs) {
                handshake(link, Role.CLIENT, group, me, random, transport.maxMessageBytes)
            }
            channel = secure
            mutableState.update {
                it.copy(phase = ClientPhase.SENDING, host = host, hostName = host.name, progress = 0f)
            }
            withTimeout(timeouts.uploadMs) {
                secure.send(MessageType.UPLOAD, SyncCodec.encode(data.export())) { f ->
                    mutableState.update { it.copy(progress = f) }
                }
                expect(secure.receive(), MessageType.RECEIVED)
            }
            mutableState.update { it.copy(phase = ClientPhase.WAITING, progress = null) }
            val merged = withTimeout(timeouts.waitForHostMs) {
                secure.receive { _, f -> mutableState.update { it.copy(phase = ClientPhase.RECEIVING, progress = f) } }
            }
            expect(merged, MessageType.MERGED)
            val doc = try {
                SyncCodec.decode(merged.data)
            } catch (e: SyncFormatException) {
                secure.sendAck(ok = false, error = e.message)
                throw SyncProtocolException(SyncFailure.INVALID_DATA, e.message.orEmpty(), e)
            }
            mutableState.update { it.copy(phase = ClientPhase.APPLYING, progress = null) }
            val result = try {
                data.apply(doc)
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                secure.sendAck(ok = false, error = "${me.name} couldn't save the data.")
                throw SyncProtocolException(SyncFailure.APPLY_FAILED, e.message ?: "Couldn't save", e)
            }
            mutableState.update { it.copy(phase = ClientPhase.DONE, changes = result.changes) }
            // The data is saved; a lost confirmation only means the host shows this phone as unsure.
            runCatching { secure.sendAck(ok = true) }
            secure.awaitHangUp(timeouts.graceMs)
        } catch (e: TimeoutCancellationException) {
            channel?.sendAbort("${me.name} gave up waiting.")
            fail(SyncFailure.TIMEOUT, "The sync took too long. Nothing changed on this phone.")
        } catch (e: SyncProtocolException) {
            fail(e.failure, e.message)
        }
    }

    private fun expect(message: Message, type: MessageType) {
        when (message.type) {
            type -> Unit
            MessageType.ABORT -> throw SyncProtocolException(SyncFailure.REJECTED, message.text)
            else -> throw SyncProtocolException(SyncFailure.PROTOCOL, "Unexpected message")
        }
    }

    private fun fail(failure: SyncFailure, message: String?) {
        mutableState.update {
            if (it.phase ==
                ClientPhase.DONE
            ) {
                it
            } else {
                it.copy(phase = ClientPhase.FAILED, failure = failure, message = message)
            }
        }
    }
}
