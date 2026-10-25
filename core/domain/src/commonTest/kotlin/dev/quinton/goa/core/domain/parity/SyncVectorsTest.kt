package dev.quinton.goa.core.domain.parity

import dev.quinton.goa.core.domain.sync.GroupCredentials
import dev.quinton.goa.core.domain.sync.SyncDocument
import dev.quinton.goa.core.domain.sync.SyncHash
import dev.quinton.goa.core.domain.sync.SyncJson
import dev.quinton.goa.core.domain.sync.SyncServerToken
import dev.quinton.goa.core.domain.sync.SyncVectors
import dev.quinton.goa.core.domain.sync.jvmDoubleString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The hashes, key derivations and JSON of group sync give the phones' bytes on every platform (ADR-043). */
class SyncVectorsTest {
    private fun hex(bytes: ByteArray) = SyncHash.hex(bytes)

    @Test
    fun sha256MatchesFips180Examples() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            hex(SyncHash.sha256(ByteArray(0))),
        )
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            hex(SyncHash.sha256("abc".encodeToByteArray())),
        )
        // Two blocks, and a length that pads into a third.
        assertEquals(
            "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
            hex(SyncHash.sha256("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq".encodeToByteArray())),
        )
        assertEquals(
            "cdc76e5c9914fb9281a1c7e284d73e67f1809a48a497200e046d39ccc7112cd0",
            hex(SyncHash.sha256(ByteArray(1_000_000) { 'a'.code.toByte() })),
        )
    }

    @Test
    fun hmacMatchesRfc4231() {
        // Test case 2, and test case 6 (a key longer than the block).
        assertEquals(
            "5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843",
            hex(SyncHash.hmacSha256("Jefe".encodeToByteArray(), "what do ya want for nothing?".encodeToByteArray())),
        )
        assertEquals(
            "60e431591ee0b67f0d8a26aacbf5b77f8e0bc6213728c5140546040f0ee37f54",
            hex(
                SyncHash.hmacSha256(
                    ByteArray(131) { 0xaa.toByte() },
                    "Test Using Larger Than Block-Size Key - Hash Key First".encodeToByteArray(),
                ),
            ),
        )
    }

    @Test
    fun hkdfMatchesRfc5869() {
        // Test case 3: no salt, no info.
        assertEquals(
            "8da4e775a563c18f715f802a063c5a31b8a11f5c5ee1879ec3454e5f3c738d2d9d201395faa4b61a96c8",
            hex(SyncHash.hkdf(ByteArray(22) { 0x0b }, ByteArray(0), "", 42)),
        )
    }

    @Test
    fun derivationsMatchTheAndroidApp() {
        val key = SyncVectors.sealKey()
        assertEquals(SyncVectors.HKDF_HEX, hex(SyncHash.hkdf(key, "salt".encodeToByteArray(), "goa-sync/file/v1|x")))
        assertEquals(SyncVectors.HMAC_HEX, hex(SyncHash.hmac(key, "ab".encodeToByteArray(), "c".encodeToByteArray())))
        assertEquals(SyncVectors.GROUP_TAG, SyncVectors.group().tag)
    }

    @Test
    fun serverTokensMatchThePublishedVectors() {
        val token = SyncServerToken.derive(SyncVectors.group())
        assertEquals(SyncVectors.TOKEN, token)
        assertEquals(SyncVectors.TOKEN_HASH, hex(SyncHash.sha256(token.encodeToByteArray())))
        val relay = GroupCredentials(SyncVectors.RELAY_GROUP_ID, SyncVectors.groupKey(), "g")
        val relayToken = SyncServerToken.derive(relay)
        assertEquals(SyncVectors.RELAY_TOKEN, relayToken)
        assertEquals(SyncVectors.RELAY_TOKEN_HASH, hex(SyncHash.sha256(relayToken.encodeToByteArray())))
    }

    @Test
    fun syncJsonIsTheAndroidAppsText() {
        // Doubles included: 1200.0, not 1200 (the merge tie-break compares this text).
        assertEquals(
            SyncVectors.FILE_DOCUMENT_JSON,
            SyncJson.encodeToString(SyncDocument.serializer(), SyncVectors.fileDocument()),
        )
        assertEquals(
            SyncVectors.fileDocument(),
            SyncJson.decodeFromString(SyncDocument.serializer(), SyncVectors.FILE_DOCUMENT_JSON),
        )
    }

    @Test
    fun doublesAreWrittenAsTheJvmWritesThem() {
        val cases = listOf(
            0.0 to "0.0", -0.0 to "-0.0", 1.0 to "1.0", 1200.0 to "1200.0", 0.5 to "0.5", -2.25 to "-2.25",
            0.1 to "0.1", 0.001 to "0.001", 0.0001 to "1.0E-4", 1.25e-5 to "1.25E-5", 9999999.0 to "9999999.0",
            1.0e7 to "1.0E7", 1.2345e10 to "1.2345E10", 1.0e21 to "1.0E21", 1.0 / 3 to "0.3333333333333333",
            123456.789 to "123456.789", 1.0e-300 to "1.0E-300", Double.MAX_VALUE to "1.7976931348623157E308",
            Double.MIN_VALUE to "4.9E-324",
        )
        cases.forEach { (value, text) -> assertEquals(text, jvmDoubleString(value), "$value") }
    }

    @Test
    fun constantTimeComparison() {
        assertTrue(SyncHash.same(byteArrayOf(1, 2), byteArrayOf(1, 2)))
        assertFalse(SyncHash.same(byteArrayOf(1, 2), byteArrayOf(1, 3)))
        assertFalse(SyncHash.same(byteArrayOf(1, 2), byteArrayOf(1, 2, 3)))
    }
}
