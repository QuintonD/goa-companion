package dev.quinton.goa.feature.sync.nearby

import android.content.Context
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import dev.quinton.goa.core.domain.sync.protocol.SyncLink
import dev.quinton.goa.core.domain.sync.protocol.SyncLinkClosedException
import dev.quinton.goa.core.domain.sync.protocol.SyncPeer
import dev.quinton.goa.core.domain.sync.protocol.SyncTransport
import dev.quinton.goa.core.domain.sync.protocol.SyncTransportException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * [SyncTransport] over Google Nearby Connections (ADR-025): `P2P_STAR` (one host, many joiners)
 * over Bluetooth and Wi-Fi Direct, no router or internet. The service id is the application id,
 * so only this app (and the same build type) finds its peers.
 *
 * Nearby already encrypts each connection; the sync protocol still proves the group key and
 * encrypts every message with it, binding the proof to Nearby's per-connection auth token.
 * Connections are accepted automatically: the endpoint names carry the group tag, and a phone
 * without the key is hung up on by the protocol before any data moves. Messages are BYTES
 * payloads of at most [ConnectionsClient.MAX_BYTES_DATA_SIZE]; the protocol chunks larger ones.
 *
 * Can't be exercised on emulators; tested by hand on two physical phones (ROADMAP M6).
 */
class NearbySyncTransport(context: Context) : SyncTransport {
    private val client: ConnectionsClient = Nearby.getConnectionsClient(context.applicationContext)
    private val serviceId: String = context.packageName

    override val maxMessageBytes: Int = ConnectionsClient.MAX_BYTES_DATA_SIZE

    /** Open links by endpoint id. */
    private val links = ConcurrentHashMap<String, NearbyLink>()

    private val payloads = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            val bytes = payload.asBytes() ?: return // only BYTES payloads are used
            links[endpointId]?.deliver(bytes)
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            links[endpointId]?.transferUpdate(update)
        }
    }

    override fun advertise(endpointName: String): Flow<SyncLink> = callbackFlow {
        val names = ConcurrentHashMap<String, ConnectionInfo>()
        val lifecycle = object : ConnectionLifecycleCallback() {
            override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
                names[endpointId] = info
                client.acceptConnection(endpointId, payloads)
            }

            override fun onConnectionResult(endpointId: String, resolution: ConnectionResolution) {
                val info = names.remove(endpointId) ?: return
                if (resolution.status.isSuccess) {
                    val link = NearbyLink(SyncPeer(endpointId, info.endpointName), info.rawAuthenticationToken)
                    links[endpointId] = link
                    trySend(link)
                }
            }

            override fun onDisconnected(endpointId: String) {
                links.remove(endpointId)?.closedByPeer()
            }
        }
        try {
            client.startAdvertising(
                endpointName,
                serviceId,
                lifecycle,
                AdvertisingOptions.Builder().setStrategy(Strategy.P2P_STAR).build(),
            ).await()
        } catch (e: ApiException) {
            throw unavailable(e)
        }
        awaitClose { client.stopAdvertising() }
    }

    override fun discover(): Flow<List<SyncPeer>> = callbackFlow {
        val found = LinkedHashMap<String, SyncPeer>()
        val callback = object : EndpointDiscoveryCallback() {
            override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
                synchronized(found) {
                    found[endpointId] = SyncPeer(endpointId, info.endpointName)
                    trySend(found.values.toList())
                }
            }

            override fun onEndpointLost(endpointId: String) {
                synchronized(found) {
                    found.remove(endpointId)
                    trySend(found.values.toList())
                }
            }
        }
        try {
            client.startDiscovery(
                serviceId,
                callback,
                DiscoveryOptions.Builder().setStrategy(Strategy.P2P_STAR).build(),
            ).await()
        } catch (e: ApiException) {
            throw unavailable(e)
        }
        trySend(emptyList())
        awaitClose { client.stopDiscovery() }
    }

    override suspend fun connect(endpointName: String, host: SyncPeer): SyncLink {
        val connected = CompletableDeferred<SyncLink>()
        var token = ByteArray(0)
        val lifecycle = object : ConnectionLifecycleCallback() {
            override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
                token = info.rawAuthenticationToken
                client.acceptConnection(endpointId, payloads)
            }

            override fun onConnectionResult(endpointId: String, resolution: ConnectionResolution) {
                if (resolution.status.isSuccess) {
                    val link = NearbyLink(host, token)
                    links[endpointId] = link
                    connected.complete(link)
                } else {
                    connected.completeExceptionally(SyncLinkClosedException("Connection refused"))
                }
            }

            override fun onDisconnected(endpointId: String) {
                links.remove(endpointId)?.closedByPeer()
                connected.completeExceptionally(SyncLinkClosedException("Disconnected"))
            }
        }
        try {
            client.requestConnection(endpointName, host.id, lifecycle).await()
        } catch (e: ApiException) {
            if (e.statusCode == ConnectionsStatusCodes.STATUS_ALREADY_CONNECTED_TO_ENDPOINT) {
                client.disconnectFromEndpoint(host.id)
            }
            throw if (isRadioProblem(e.statusCode)) unavailable(e) else SyncLinkClosedException("Couldn't connect", e)
        }
        return try {
            connected.await()
        } catch (e: kotlinx.coroutines.CancellationException) {
            client.disconnectFromEndpoint(host.id)
            throw e
        }
    }

    /** Stops advertising, discovery and every connection (the sync screen closed). */
    fun shutdown() {
        client.stopAdvertising()
        client.stopDiscovery()
        client.stopAllEndpoints()
        links.values.forEach { it.closedByPeer() }
        links.clear()
    }

    private inner class NearbyLink(override val peer: SyncPeer, token: ByteArray) : SyncLink {
        override val authToken: ByteArray = token.copyOf()
        private val inbox = Channel<ByteArray>(Channel.UNLIMITED)
        private val sending = ConcurrentHashMap<Long, CompletableDeferred<Unit>>()
        private val closed = AtomicBoolean(false)

        override val incoming: ReceiveChannel<ByteArray> get() = inbox

        override suspend fun send(message: ByteArray) {
            if (closed.get()) throw SyncLinkClosedException()
            val payload = Payload.fromBytes(message)
            val delivered = CompletableDeferred<Unit>()
            sending[payload.id] = delivered
            try {
                client.sendPayload(peer.id, payload).await()
                delivered.await() // the transfer update says it arrived
            } catch (e: ApiException) {
                throw SyncLinkClosedException("Send failed", e)
            } finally {
                sending.remove(payload.id)
            }
        }

        fun deliver(bytes: ByteArray) {
            inbox.trySend(bytes)
        }

        fun transferUpdate(update: PayloadTransferUpdate) {
            val waiting = sending[update.payloadId] ?: return
            when (update.status) {
                PayloadTransferUpdate.Status.SUCCESS -> waiting.complete(Unit)

                PayloadTransferUpdate.Status.FAILURE, PayloadTransferUpdate.Status.CANCELED ->
                    waiting.completeExceptionally(SyncLinkClosedException("Transfer failed"))

                else -> Unit
            }
        }

        override fun close() {
            if (closed.compareAndSet(false, true)) {
                links.remove(peer.id)
                client.disconnectFromEndpoint(peer.id)
                finish()
            }
        }

        fun closedByPeer() {
            if (closed.compareAndSet(false, true)) finish()
        }

        private fun finish() {
            inbox.close()
            sending.values.forEach { it.completeExceptionally(SyncLinkClosedException()) }
        }
    }

    private companion object {
        fun isRadioProblem(code: Int) = code == ConnectionsStatusCodes.STATUS_BLUETOOTH_ERROR ||
            code == ConnectionsStatusCodes.STATUS_RADIO_ERROR ||
            ConnectionsStatusCodes.getStatusCodeString(code).contains("PERMISSION")

        /** A user-facing reason Nearby can't start, with what to do about it. */
        fun unavailable(e: ApiException): SyncTransportException {
            val code = e.statusCode
            val name = ConnectionsStatusCodes.getStatusCodeString(code)
            val message = when {
                name.contains("PERMISSION") ->
                    "Nearby devices permission is off. Allow it in the app's settings, or use a sync file."

                code == ConnectionsStatusCodes.STATUS_BLUETOOTH_ERROR ||
                    code == ConnectionsStatusCodes.STATUS_RADIO_ERROR ->
                    "Turn on Bluetooth and Wi-Fi (no network needed), then try again."

                code == ConnectionsStatusCodes.STATUS_ALREADY_ADVERTISING ||
                    code == ConnectionsStatusCodes.STATUS_ALREADY_DISCOVERING ->
                    "A sync is already running on this phone. Close it and try again."

                else -> "Nearby isn't available on this phone ($name). Use a sync file instead."
            }
            return SyncTransportException(message, e)
        }
    }
}
