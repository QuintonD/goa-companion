package dev.quinton.goa.core.domain.sync

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

/**
 * The Android app's synchronous face of [SyncFileCodec] (ADR-043): the same shared code, sealed and
 * gzipped by [JvmSyncCipher], which never suspends. Nearby payloads ([encode], [decode]), the
 * "Share sync file" fallback and the sync server's uploads ([encodeFile], [decodeFile]).
 */
object SyncCodec {
    const val FILE_FORMAT = SyncFileCodec.FILE_FORMAT
    const val FILE_EXTENSION = SyncFileCodec.FILE_EXTENSION
    const val FILE_MIME_TYPE = SyncFileCodec.FILE_MIME_TYPE

    fun encode(doc: SyncDocument): ByteArray = immediate { SyncFileCodec.encode(doc, JvmSyncCipher) }

    /** Inflates, parses and validates; throws [SyncFormatException]. */
    fun decode(bytes: ByteArray): SyncDocument = immediate { SyncFileCodec.decode(bytes, JvmSyncCipher) }

    /** [SyncFileCodec.encodeFile]. */
    fun encodeFile(
        doc: SyncDocument,
        group: GroupCredentials,
        createdAt: Long,
        random: SecureBytes = SecureBytes.System,
        withGroupName: Boolean = true,
    ): ByteArray = immediate { SyncFileCodec.encodeFile(doc, group, createdAt, JvmSyncCipher, random, withGroupName) }

    /** Opens a sync file for this phone's [group] (null = not in a group); throws [SyncFileException]. */
    fun decodeFile(bytes: ByteArray, group: GroupCredentials?): SyncDocument =
        immediate { SyncFileCodec.decodeFile(bytes, group, JvmSyncCipher) }

    /** Runs [block], which must not suspend ([JvmSyncCipher] never does), and returns or throws its outcome. */
    private fun <T> immediate(block: suspend () -> T): T {
        var outcome: Result<T>? = null
        block.startCoroutine(Continuation(EmptyCoroutineContext) { outcome = it })
        return checkNotNull(outcome) { "SyncCodec suspended" }.getOrThrow()
    }
}

/** [SyncCipher] on `javax.crypto` ([SyncCrypto]) and `java.util.zip`: the Android app's, unchanged. */
object JvmSyncCipher : SyncCipher {
    override suspend fun seal(key: ByteArray, plaintext: ByteArray, aad: ByteArray, iv: ByteArray): ByteArray {
        require(iv.size == SyncCipher.IV_BYTES) { "IV must be ${SyncCipher.IV_BYTES} bytes" }
        return SyncCrypto.seal(key, plaintext, aad) { size -> iv.copyOf(size) }
    }

    override suspend fun open(key: ByteArray, sealed: ByteArray, aad: ByteArray): ByteArray =
        SyncCrypto.open(key, sealed, aad)

    override suspend fun gzip(bytes: ByteArray): ByteArray = ByteArrayOutputStream().also { out ->
        GZIPOutputStream(out).use { it.write(bytes) }
    }.toByteArray()

    override suspend fun gunzip(bytes: ByteArray, maxBytes: Int): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPInputStream(ByteArrayInputStream(bytes)).use { input ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                out.write(buffer, 0, read)
                if (out.size() > maxBytes) throw IOException("Sync data too large")
            }
        }
        return out.toByteArray()
    }
}
