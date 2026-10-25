package dev.quinton.goa.web.group

import dev.quinton.goa.core.domain.sync.SyncCipher
import dev.quinton.goa.core.domain.sync.SyncCryptoException
import kotlin.js.Promise
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.await
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.khronos.webgl.Uint8Array

/**
 * The browser's [SyncCipher] (ADR-043): AES-256-GCM with a 128-bit tag from WebCrypto
 * (`crypto.subtle`), gzip from `CompressionStream` / `DecompressionStream`. Byte for byte what the
 * phones' `javax.crypto` seals (`SyncCipherConformance`, checked on Node in `WebCryptoCipherTest`
 * and in WebKit by the end-to-end suite). Keys are imported non-extractable, per call; nothing is
 * kept.
 */
object WebCryptoCipher : SyncCipher {
    private val subtle: dynamic get() = js("globalThis.crypto.subtle")

    override suspend fun seal(key: ByteArray, plaintext: ByteArray, aad: ByteArray, iv: ByteArray): ByteArray {
        require(iv.size == SyncCipher.IV_BYTES) { "IV must be ${SyncCipher.IV_BYTES} bytes" }
        val sealed = subtle.encrypt(gcm(iv, aad), importKey(key, "encrypt"), view(plaintext))
            .unsafeCast<Promise<ArrayBuffer>>().await()
        return iv + bytes(sealed)
    }

    @Suppress("TooGenericExceptionCaught", "ThrowsCount") // WebCrypto rejects with a plain DOMException
    override suspend fun open(key: ByteArray, sealed: ByteArray, aad: ByteArray): ByteArray {
        if (sealed.size < SyncCipher.IV_BYTES + SyncCipher.TAG_BYTES) throw SyncCryptoException("Sealed data too short")
        val iv = sealed.copyOfRange(0, SyncCipher.IV_BYTES)
        val body = sealed.copyOfRange(SyncCipher.IV_BYTES, sealed.size)
        return try {
            val plain = subtle.decrypt(gcm(iv, aad), importKey(key, "decrypt"), view(body))
                .unsafeCast<Promise<ArrayBuffer>>().await()
            bytes(plain)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            throw SyncCryptoException("Could not decrypt: wrong key or tampered data", e)
        }
    }

    override suspend fun gzip(bytes: ByteArray): ByteArray {
        val stream = blobOf(bytes).stream().pipeThrough(js("new CompressionStream('gzip')"))
        return readAll(stream, Int.MAX_VALUE) ?: error("gzip output too large")
    }

    override suspend fun gunzip(bytes: ByteArray, maxBytes: Int): ByteArray {
        val stream = blobOf(bytes).stream().pipeThrough(js("new DecompressionStream('gzip')"))
        return readAll(stream, maxBytes) ?: throw IllegalStateException("Sync data too large")
    }

    private suspend fun importKey(key: ByteArray, use: String): dynamic =
        subtle.importKey("raw", view(key), "AES-GCM", false, arrayOf(use)).unsafeCast<Promise<dynamic>>().await()

    private fun gcm(iv: ByteArray, aad: ByteArray): dynamic {
        val params: dynamic = js("({})")
        params.name = "AES-GCM"
        params.iv = view(iv)
        params.additionalData = view(aad)
        params.tagLength = TAG_BITS
        return params
    }

    private const val TAG_BITS = 128
}

/** The bytes of a Kotlin/JS [ByteArray] (an `Int8Array`) as a `Uint8Array`, without copying. */
internal fun view(bytes: ByteArray): Uint8Array {
    val int8 = bytes.unsafeCast<Int8Array>()
    return Uint8Array(int8.buffer, int8.byteOffset, int8.length)
}

/** An `ArrayBuffer` (or a view's buffer) as a Kotlin [ByteArray]. */
internal fun bytes(buffer: ArrayBuffer): ByteArray = Int8Array(buffer).unsafeCast<ByteArray>()

private fun blobOf(bytes: ByteArray): dynamic {
    val parts = arrayOf<Any>(view(bytes))
    return js("new Blob(parts)")
}

/**
 * Reads a `ReadableStream` of bytes to the end; null as soon as it passes [max] bytes (the rest is
 * cancelled unread). A broken stream (not gzip, a dropped connection) throws.
 */
internal suspend fun readAll(stream: dynamic, max: Int): ByteArray? {
    val reader = stream.getReader()
    val chunks = mutableListOf<Int8Array>()
    var total = 0
    while (true) {
        val step = reader.read().unsafeCast<Promise<dynamic>>().await()
        if (step.done == true) break
        val chunk: dynamic = step.value
        val piece = Int8Array(chunk.buffer.unsafeCast<ArrayBuffer>(), chunk.byteOffset as Int, chunk.byteLength as Int)
        total += piece.length
        if (total > max) {
            reader.cancel()
            return null
        }
        chunks += piece
    }
    val out = Int8Array(total)
    var at = 0
    chunks.forEach {
        out.set(it, at)
        at += it.length
    }
    return out.unsafeCast<ByteArray>()
}
