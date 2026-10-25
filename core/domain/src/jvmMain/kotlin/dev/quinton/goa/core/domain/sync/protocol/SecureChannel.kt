package dev.quinton.goa.core.domain.sync.protocol

import dev.quinton.goa.core.domain.sync.GroupCredentials
import dev.quinton.goa.core.domain.sync.SecureBytes
import dev.quinton.goa.core.domain.sync.SyncCrypto
import dev.quinton.goa.core.domain.sync.SyncCryptoException
import dev.quinton.goa.core.domain.sync.SyncDocument
import dev.quinton.goa.core.domain.sync.SyncJson
import java.nio.ByteBuffer
import java.util.Base64
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException

/** Why a sync with one phone (or the whole session) stopped. */
enum class SyncFailure {
    /** A stage took longer than its [SyncTimeouts] allowance. */
    TIMEOUT,

    /** The other phone went away (out of range, app closed, cancelled). */
    DISCONNECTED,

    /** The other phone can't prove it holds this group's key (another group, or a rotated key). */
    WRONG_KEY,

    /** The phones run different sync versions; update the app. */
    VERSION_MISMATCH,

    /** The other phone sent data the app refuses ([dev.quinton.goa.core.domain.sync.SyncValidation]). */
    INVALID_DATA,

    /** Saving the merged data failed; nothing was changed. */
    APPLY_FAILED,

    /** The other phone stopped the sync and said why. */
    REJECTED,

    /** No host of this group was found. */
    NO_HOST,

    /** Nearby can't run here: radios off, a permission missing, or no Play services. */
    UNAVAILABLE,

    /** The other phone broke the protocol. */
    PROTOCOL,
}

class SyncProtocolException(val failure: SyncFailure, message: String, cause: Throwable? = null) :
    Exception(message, cause)

/** Allowance for each stage, so a phone that wanders off never leaves the others hanging. */
data class SyncTimeouts(
    val handshakeMs: Long = 20_000,
    val uploadMs: Long = 60_000,
    /** A client waits this long for the host to tap "Sync" once its data is in. */
    val waitForHostMs: Long = 10 * 60_000,
    val downloadMs: Long = 60_000,
    val ackMs: Long = 60_000,
    val searchMs: Long = 2 * 60_000,
    /** After the last message, how long to wait for the other side to hang up first. */
    val graceMs: Long = 3_000,
)

internal enum class Role(val wire: String) {
    HOST("host"),
    CLIENT("client"),
}

/** Messages inside the encrypted channel. */
internal enum class MessageType(val code: Byte) {
    /** Client → host: its full [SyncDocument]. */
    UPLOAD(1),

    /** Host → client: upload received and valid; wait for the merged data. */
    RECEIVED(2),

    /** Host → client: the merged document. */
    MERGED(3),

    /** Client → host: the merged document was saved (or why not). */
    ACK(4),

    /** Either side: stopping, with a user-readable reason. */
    ABORT(5),
    ;

    companion object {
        fun of(code: Byte) = entries.firstOrNull { it.code == code }
    }
}

internal class Message(val type: MessageType, val data: ByteArray) {
    val text: String get() = data.toString(Charsets.UTF_8)
}

@Serializable
internal data class Hello(
    val protocol: Int,
    val role: String,
    val deviceId: String,
    val deviceName: String,
    val nonce: String,
    val documentVersion: Int,
    /** Informational (added without a protocol bump: older peers send none). */
    val appVersion: String? = null,
)

@Serializable
internal data class Ack(val ok: Boolean, val error: String? = null)

/**
 * Wire format: every transport message is `'G' ‖ kind ‖ body`. Before the key proof, kinds are
 * HELLO (JSON) and PROOF (HMAC); after it, only SEALED: AES-256-GCM over
 * `type ‖ messageId ‖ chunkIndex ‖ chunkCount ‖ data` with a per-session key and a per-direction
 * associated label (a message can't be reflected back to its sender).
 */
internal object Wire {
    const val PROTOCOL = 1
    private const val MAGIC: Byte = 0x47
    const val HELLO: Byte = 1
    const val PROOF: Byte = 2
    const val SEALED: Byte = 16

    fun frame(kind: Byte, body: ByteArray) = byteArrayOf(MAGIC, kind) + body

    fun kindOf(message: ByteArray): Byte? = if (message.size >= 2 && message[0] == MAGIC) message[1] else null

    fun bodyOf(message: ByteArray): ByteArray = message.copyOfRange(2, message.size)
}

/**
 * Both phones prove they hold the group key before any data moves: each sends a fresh nonce,
 * then `HMAC(authKey, role ‖ authToken ‖ nonces ‖ device ids)`. A wrong MAC ends the link. The
 * session key is derived from the group key, both nonces and the connection's token.
 */
internal suspend fun handshake(
    link: SyncLink,
    role: Role,
    group: GroupCredentials,
    me: SyncDevice,
    random: SecureBytes,
    maxMessageBytes: Int,
): Pair<SecureChannel, SyncDevice> {
    val nonce = random.next(SyncCrypto.NONCE_BYTES)
    val hello = Hello(Wire.PROTOCOL, role.wire, me.deviceId, me.name, b64(nonce), SyncDocument.VERSION, me.appVersion)
    sendRaw(link, Wire.frame(Wire.HELLO, SyncJson.encodeToString(Hello.serializer(), hello).toByteArray()))
    val theirs = try {
        SyncJson.decodeFromString(Hello.serializer(), expect(link, Wire.HELLO).toString(Charsets.UTF_8))
    } catch (e: SerializationException) {
        throw SyncProtocolException(SyncFailure.PROTOCOL, "Unexpected greeting", e)
    }
    val theirNonce = check(theirs, role)
    val peer = SyncDevice(theirs.deviceId, theirs.deviceName, theirs.appVersion)
    val (hostNonce, clientNonce) = if (role == Role.HOST) nonce to theirNonce else theirNonce to nonce
    val (hostId, clientId) = if (role == Role.HOST) me.deviceId to peer.deviceId else peer.deviceId to me.deviceId
    val authKey = SyncCrypto.hkdf(group.key, ByteArray(0), "goa-sync/auth/v1|${group.groupId}")
    fun proof(of: Role) = SyncCrypto.hmac(
        authKey,
        "proof".toByteArray(),
        of.wire.toByteArray(),
        link.authToken,
        hostNonce,
        clientNonce,
        hostId.toByteArray(),
        clientId.toByteArray(),
    )
    sendRaw(link, Wire.frame(Wire.PROOF, proof(role)))
    val other = if (role == Role.HOST) Role.CLIENT else Role.HOST
    if (!SyncCrypto.same(expect(link, Wire.PROOF), proof(other))) {
        throw SyncProtocolException(SyncFailure.WRONG_KEY, "${peer.name} isn't in this group, or has an old key.")
    }
    val sessionKey = SyncCrypto.hkdf(
        group.key,
        hostNonce + clientNonce + link.authToken,
        "goa-sync/session/v1|${group.groupId}",
    )
    return SecureChannel(link, sessionKey, role, maxMessageBytes, random) to peer
}

/** Their nonce, once their greeting is one this phone can sync with. */
@Suppress("ThrowsCount")
private fun check(theirs: Hello, role: Role): ByteArray {
    val newer = theirs.documentVersion > SyncDocument.VERSION || theirs.protocol > Wire.PROTOCOL
    when {
        theirs.protocol != Wire.PROTOCOL || theirs.documentVersion != SyncDocument.VERSION ->
            throw SyncProtocolException(
                SyncFailure.VERSION_MISMATCH,
                if (newer) {
                    "${theirs.deviceName} has a newer version of the app. Update this phone."
                } else {
                    "${theirs.deviceName} has an older version of the app. Update it."
                },
            )

        theirs.role == role.wire -> throw SyncProtocolException(SyncFailure.PROTOCOL, "Both phones are ${role.wire}s")
    }
    return runCatching { unb64(theirs.nonce) }.getOrNull()?.takeIf { it.size == SyncCrypto.NONCE_BYTES }
        ?: throw SyncProtocolException(SyncFailure.PROTOCOL, "Bad nonce")
}

private suspend fun expect(link: SyncLink, kind: Byte): ByteArray {
    val message = receiveRaw(link)
    if (Wire.kindOf(message) != kind) throw SyncProtocolException(SyncFailure.PROTOCOL, "Unexpected message")
    return Wire.bodyOf(message)
}

private suspend fun sendRaw(link: SyncLink, message: ByteArray) = try {
    link.send(message)
} catch (e: SyncLinkClosedException) {
    throw SyncProtocolException(SyncFailure.DISCONNECTED, "The other phone disconnected", e)
}

private suspend fun receiveRaw(link: SyncLink): ByteArray = try {
    link.incoming.receive()
} catch (e: ClosedReceiveChannelException) {
    throw SyncProtocolException(SyncFailure.DISCONNECTED, "The other phone disconnected", e)
}

private fun b64(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)

private fun unb64(text: String) = Base64.getDecoder().decode(text)

/** Sends and receives whole messages over a [SyncLink], encrypted and split into transport-sized chunks. */
internal class SecureChannel(
    private val link: SyncLink,
    private val key: ByteArray,
    role: Role,
    maxMessageBytes: Int,
    private val random: SecureBytes,
) {
    private val sendLabel = "goa-sync/v1|${if (role == Role.HOST) "h2c" else "c2h"}".toByteArray()
    private val receiveLabel = "goa-sync/v1|${if (role == Role.HOST) "c2h" else "h2c"}".toByteArray()
    private val chunkBytes = (maxMessageBytes - OVERHEAD).also { require(it > 0) { "Transport messages too small" } }
    private var nextId = 0
    private val partial = HashMap<Int, Partial>()

    private class Partial(val type: MessageType, val count: Int) {
        val chunks = arrayOfNulls<ByteArray>(count)
        var received = 0
    }

    /** [onProgress] gets the fraction sent after each chunk. */
    suspend fun send(type: MessageType, data: ByteArray = ByteArray(0), onProgress: (Float) -> Unit = {}) {
        val id = nextId++
        val count = maxOf(1, (data.size + chunkBytes - 1) / chunkBytes)
        for (index in 0 until count) {
            val slice = data.copyOfRange(index * chunkBytes, minOf(data.size, (index + 1) * chunkBytes))
            val plain = ByteBuffer.allocate(HEADER + slice.size)
                .put(type.code).putInt(id).putInt(index).putInt(count).put(slice).array()
            sendRaw(link, Wire.frame(Wire.SEALED, SyncCrypto.seal(key, plain, sendLabel, random)))
            onProgress((index + 1).toFloat() / count)
        }
    }

    /** The next whole message; [onProgress] gets each chunk's type and the fraction received. */
    suspend fun receive(onProgress: (MessageType, Float) -> Unit = { _, _ -> }): Message {
        while (true) {
            val chunk = open(receiveRaw(link))
            val entry = partial.getOrPut(chunk.id) { Partial(chunk.type, chunk.count) }
            if (entry.type != chunk.type || entry.count != chunk.count) throw protocolError("Bad chunk")
            if (entry.chunks[chunk.index] == null) {
                entry.chunks[chunk.index] = chunk.data
                entry.received++
            }
            onProgress(chunk.type, entry.received.toFloat() / chunk.count)
            if (entry.received == chunk.count) {
                partial.remove(chunk.id)
                val out = java.io.ByteArrayOutputStream()
                entry.chunks.forEach { out.write(requireNotNull(it)) }
                return Message(chunk.type, out.toByteArray())
            }
        }
    }

    private class Chunk(val type: MessageType, val id: Int, val index: Int, val count: Int, val data: ByteArray)

    @Suppress("ThrowsCount")
    private fun open(raw: ByteArray): Chunk {
        if (Wire.kindOf(raw) != Wire.SEALED) throw protocolError("Unexpected message")
        val plain = try {
            SyncCrypto.open(key, Wire.bodyOf(raw), receiveLabel)
        } catch (e: SyncCryptoException) {
            throw SyncProtocolException(SyncFailure.WRONG_KEY, "Received data that doesn't check out", e)
        }
        if (plain.size < HEADER) throw protocolError("Short chunk")
        val buffer = ByteBuffer.wrap(plain)
        val type = MessageType.of(buffer.get()) ?: throw protocolError("Unknown message")
        val id = buffer.int
        val index = buffer.int
        val count = buffer.int
        if (count !in 1..MAX_CHUNKS || index !in 0 until count) throw protocolError("Bad chunk")
        return Chunk(type, id, index, count, plain.copyOfRange(HEADER, plain.size))
    }

    private fun protocolError(message: String) = SyncProtocolException(SyncFailure.PROTOCOL, message)

    suspend fun sendAck(ok: Boolean, error: String? = null) =
        send(MessageType.ACK, SyncJson.encodeToString(Ack.serializer(), Ack(ok, error)).toByteArray())

    suspend fun sendAbort(reason: String) = runCatching { send(MessageType.ABORT, reason.toByteArray()) }

    /** Waits up to [ms] for the other side to hang up, so a last message isn't cut off by our close. */
    suspend fun awaitHangUp(ms: Long) {
        withTimeoutOrNull(ms) {
            do {
                val next = link.incoming.receiveCatching()
            } while (next.isSuccess)
        }
    }

    private companion object {
        const val HEADER = 13
        const val OVERHEAD = 2 + 12 + 16 + HEADER + 19 // frame, IV, tag, header, slack
        const val MAX_CHUNKS = 4096
    }
}

internal fun parseAck(message: Message): Ack = try {
    SyncJson.decodeFromString(Ack.serializer(), message.text)
} catch (e: SerializationException) {
    throw SyncProtocolException(SyncFailure.PROTOCOL, "Bad confirmation", e)
}
