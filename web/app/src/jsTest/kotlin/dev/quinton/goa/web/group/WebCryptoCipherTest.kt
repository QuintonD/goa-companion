package dev.quinton.goa.web.group

import dev.quinton.goa.core.domain.sync.GroupCredentials
import dev.quinton.goa.core.domain.sync.SyncCipherConformance
import dev.quinton.goa.core.domain.sync.SyncCryptoException
import dev.quinton.goa.core.domain.sync.SyncFileCodec
import dev.quinton.goa.core.domain.sync.SyncHash
import dev.quinton.goa.core.domain.sync.SyncServerToken
import dev.quinton.goa.core.domain.sync.SyncVectors
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * The browser's cipher against the phones' published vectors (core:domain `SyncVectors`): seals the
 * same bytes as `javax.crypto`, opens a real phone upload, refuses tampering, other keys and other
 * AAD, and gzip/gunzip respect the size limit. Runs on Node's WebCrypto (the same API Safari has).
 */
class WebCryptoCipherTest {
    @Test
    fun sealsAndOpensLikeThePhones() = runTest {
        assertEquals(emptyList(), SyncCipherConformance.problems(WebCryptoCipher))
    }

    @Test
    fun opensAPhonesUploadAndItsDocumentMatchesTheVector() = runTest {
        val doc = SyncFileCodec.decodeFile(SyncVectors.FILE.encodeToByteArray(), SyncVectors.group(), WebCryptoCipher)
        assertEquals(SyncVectors.fileDocument(), doc)
    }

    @Test
    fun theRelayTokenIsTheDocumentedOne() {
        // docs/SYNC-SERVER.md's vector: the relay stores SHA-256 of this token.
        val key = ByteArray(32) { it.toByte() }
        val group = GroupCredentials("8c4f2b1e-3a5d-4c6e-9f70-1a2b3c4d5e6f", key, "Test")
        val token = SyncServerToken.derive(group)
        assertEquals("dFROVzWTpa17AzhaeXje8rW5l1okK5Q9nuEeVerIQTI", token)
        assertEquals(
            "cfb0311773dcc2650c1160044e1f82ed615b49d23428d9b9c935d82d01399e25",
            SyncHash.hex(SyncHash.sha256(token.encodeToByteArray())),
        )
    }

    @Test
    fun aRoundTripOfLargeDataStaysExact() = runTest {
        val data = ByteArray(300_000) { ((it * 31) xor (it shr 7)).toByte() }
        val zipped = WebCryptoCipher.gzip(data)
        assertTrue(zipped.size < data.size)
        assertContentEquals(data, WebCryptoCipher.gunzip(zipped, data.size))
        assertFailsWith<Throwable> { WebCryptoCipher.gunzip(zipped, data.size - 1) }
    }

    @Test
    fun aSealedMessageOpensOnlyWithItsKeyAndAad() = runTest {
        val key = ByteArray(32) { (it * 7).toByte() }
        val iv = ByteArray(12) { it.toByte() }
        val sealed = WebCryptoCipher.seal(key, "hello".encodeToByteArray(), "aad".encodeToByteArray(), iv)
        assertEquals(12 + 5 + 16, sealed.size)
        assertEquals("hello", WebCryptoCipher.open(key, sealed, "aad".encodeToByteArray()).decodeToString())
        assertFailsWith<SyncCryptoException> { WebCryptoCipher.open(key, sealed, "other".encodeToByteArray()) }
    }
}
