package dev.quinton.goa.core.domain.sync.protocol

import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.Flow

/**
 * A phone-to-phone link layer (ADR-025). The app's implementation is Google Nearby Connections
 * (feature:sync); tests use an in-memory one. The transport only moves opaque messages of at most
 * [maxMessageBytes]: authentication, encryption, chunking and the sync itself live in the
 * protocol ([SyncHost], [SyncClient]), so they are tested without radios.
 */
interface SyncTransport {
    /** Largest message [SyncLink.send] accepts (Nearby: `ConnectionsClient.MAX_BYTES_DATA_SIZE`). */
    val maxMessageBytes: Int

    /**
     * Advertises this phone as a host called [endpointName]; emits every phone that connects.
     * Advertising stops when collection stops; links already emitted stay open.
     */
    fun advertise(endpointName: String): Flow<SyncLink>

    /** Looks for advertising hosts; emits the current list whenever it changes. Stops with collection. */
    fun discover(): Flow<List<SyncPeer>>

    /** Connects to a discovered host, announcing [endpointName]. Throws if the host is gone or refuses. */
    suspend fun connect(endpointName: String, host: SyncPeer): SyncLink
}

/** The link closed (or a send failed) before a message got through. */
class SyncLinkClosedException(message: String = "Link closed", cause: Throwable? = null) :
    java.io.IOException(message, cause)

/** A phone seen by the transport: its transport id and the endpoint name it announces. */
data class SyncPeer(val id: String, val endpointName: String)

/** An open connection to one phone. */
interface SyncLink {
    val peer: SyncPeer

    /**
     * Bytes both ends of this one connection agree on (Nearby's raw authentication token). The key
     * proof covers them, so a relay in the middle, which holds two different connections, fails.
     */
    val authToken: ByteArray

    /** Messages in the order they arrived; closed when the link closes. */
    val incoming: ReceiveChannel<ByteArray>

    /**
     * Sends one message of at most [SyncTransport.maxMessageBytes] and returns once the transport
     * has delivered it. Throws [SyncLinkClosedException] if the link is closed or the send fails.
     */
    suspend fun send(message: ByteArray)

    fun close()
}

/**
 * Endpoint names: `goa1|<group tag>|<device name>`. The tag ([dev.quinton.goa.core.domain.sync.SyncCrypto.groupTag])
 * lets a phone ignore other groups' hosts; it is not secret and proves nothing.
 */
object SyncEndpointName {
    private const val PREFIX = "goa1"
    const val MAX_DEVICE_NAME = 24

    fun format(groupTag: String, deviceName: String): String =
        "$PREFIX|$groupTag|${deviceName.replace('|', ' ').trim().take(MAX_DEVICE_NAME)}"

    /** (tag, device name), or null for a name that isn't ours. */
    fun parse(endpointName: String): Pair<String, String>? {
        val parts = endpointName.split('|', limit = 3)
        return if (parts.size == 3 && parts[0] == PREFIX) parts[1] to parts[2] else null
    }
}
