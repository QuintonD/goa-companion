package dev.quinton.goa.core.domain.sync

import dev.quinton.goa.core.domain.sync.protocol.SyncLink
import dev.quinton.goa.core.domain.sync.protocol.SyncLinkClosedException
import dev.quinton.goa.core.domain.sync.protocol.SyncPeer
import dev.quinton.goa.core.domain.sync.protocol.SyncTransport
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Phones in one room for protocol tests: [transport] gives each phone a [SyncTransport]; hosts
 * advertise into the room and clients connect to them over in-memory channels. Hooks simulate
 * what radios do: [dropAfter] cuts a phone's links once it has sent N messages, and
 * [relayInTheMiddle] gives the two ends of every new link different auth tokens (a relay).
 */
class InMemorySyncNetwork(val maxMessageBytes: Int = 32 * 1024) {
    private class Advert(val peer: SyncPeer, val deviceName: String, val incoming: Channel<SyncLink>)

    private val adverts = MutableStateFlow<Map<String, Advert>>(emptyMap())
    private val dropAfter = HashMap<Pair<String, String?>, Int>()
    private var nextId = 0
    private var nextToken = 0

    /** Every link end created, in order. */
    val links = mutableListOf<InMemoryLink>()

    var relayInTheMiddle = false

    /**
     * Cuts a link once [sender] has sent [messages] messages on it: every link of [sender], or
     * only its link to [receiver] (e.g. the host's link to one client, mid-download).
     */
    fun dropAfter(sender: String, messages: Int, receiver: String? = null) {
        dropAfter[sender to receiver] = messages
    }

    private fun dropFor(sender: String, receiver: String): Int? =
        dropAfter[sender to receiver] ?: dropAfter[sender to null]

    fun transport(deviceName: String): SyncTransport = object : SyncTransport {
        override val maxMessageBytes: Int get() = this@InMemorySyncNetwork.maxMessageBytes

        override fun advertise(endpointName: String): Flow<SyncLink> = channelFlow {
            val id = "ep${nextId++}"
            val incoming = Channel<SyncLink>(Channel.UNLIMITED)
            adverts.update { it + (id to Advert(SyncPeer(id, endpointName), deviceName, incoming)) }
            val forward = launch { for (link in incoming) send(link) }
            awaitClose {
                adverts.update { it - id }
                forward.cancel()
            }
        }

        override fun discover(): Flow<List<SyncPeer>> =
            adverts.map { all -> all.values.map { it.peer }.sortedBy { it.id } }.distinctUntilChanged()

        override suspend fun connect(endpointName: String, host: SyncPeer): SyncLink {
            val advert = adverts.value[host.id] ?: throw SyncLinkClosedException("Host gone")
            val token = byteArrayOf((nextToken++).toByte(), 7, 7, 7)
            val hostToken = if (relayInTheMiddle) byteArrayOf(token[0], 9, 9, 9) else token
            val toHost = Channel<ByteArray>(Channel.UNLIMITED)
            val toClient = Channel<ByteArray>(Channel.UNLIMITED)
            val clientEnd =
                InMemoryLink(host, token, toClient, toHost, deviceName, dropFor(deviceName, advert.deviceName))
            val hostEnd = InMemoryLink(
                SyncPeer("c${nextId++}", endpointName),
                hostToken,
                toHost,
                toClient,
                advert.deviceName,
                dropFor(advert.deviceName, deviceName),
            )
            clientEnd.other = hostEnd
            hostEnd.other = clientEnd
            links += clientEnd
            links += hostEnd
            advert.incoming.send(hostEnd)
            return clientEnd
        }
    }
}

/** One end of an in-memory link. Closing either end closes both. */
class InMemoryLink internal constructor(
    override val peer: SyncPeer,
    override val authToken: ByteArray,
    private val inbox: Channel<ByteArray>,
    private val outbox: Channel<ByteArray>,
    val ownerName: String,
    private val dropAfterMessages: Int?,
) : SyncLink {
    internal var other: InMemoryLink? = null

    var sent = 0
        private set

    override val incoming: ReceiveChannel<ByteArray> get() = inbox

    override suspend fun send(message: ByteArray) {
        if (dropAfterMessages != null && sent >= dropAfterMessages) {
            close()
            throw SyncLinkClosedException("Dropped")
        }
        if (outbox.trySend(message.copyOf()).isFailure) throw SyncLinkClosedException()
        sent++
    }

    override fun close() {
        inbox.close()
        outbox.close()
        other?.let {
            it.inbox.close()
            it.outbox.close()
        }
    }
}
