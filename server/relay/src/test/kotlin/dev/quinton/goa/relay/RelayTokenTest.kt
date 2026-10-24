package dev.quinton.goa.relay

import org.junit.jupiter.api.Test
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.assertEquals

/**
 * The bearer token exactly as the phone derives it (docs/SYNC-SERVER.md, "Token"). The relay never
 * derives tokens; this test pins the shared vector that the Android side asserts too.
 */
class RelayTokenTest {
    @Test
    fun `the phone's token derivation matches the published vector`() {
        val key = hex(VECTOR_KEY_HEX)
        val token = phoneToken(key, VECTOR_GROUP_ID)
        assertEquals(VECTOR_TOKEN, token)
        assertEquals(VECTOR_TOKEN_SHA256, GroupStore.sha256Hex(token))
    }

    companion object {
        const val VECTOR_KEY_HEX = "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f"
        const val VECTOR_GROUP_ID = "8c4f2b1e-3a5d-4c6e-9f70-1a2b3c4d5e6f"
        const val VECTOR_TOKEN = "dFROVzWTpa17AzhaeXje8rW5l1okK5Q9nuEeVerIQTI"
        const val VECTOR_TOKEN_SHA256 = "cfb0311773dcc2650c1160044e1f82ed615b49d23428d9b9c935d82d01399e25"

        fun phoneToken(key: ByteArray, groupId: String): String = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(hkdf(ikm = key, salt = groupId.toByteArray(Charsets.UTF_8), info = "goa-relay/token/v1", length = 32))

        private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

        // Copied from core/domain/.../sync/SyncCrypto.kt (RFC 5869 HKDF-SHA256); test-only.
        private const val KEY_BYTES = 32
        private const val HMAC = "HmacSHA256"

        private fun hkdf(ikm: ByteArray, salt: ByteArray, info: String, length: Int = KEY_BYTES): ByteArray {
            val prk = rawHmac(salt.takeIf { it.isNotEmpty() } ?: ByteArray(KEY_BYTES), ikm)
            val out = java.io.ByteArrayOutputStream()
            var block = ByteArray(0)
            var counter = 1
            while (out.size() < length) {
                block = rawHmac(prk, block + info.toByteArray(Charsets.UTF_8) + byteArrayOf(counter.toByte()))
                out.write(block)
                counter++
            }
            return out.toByteArray().copyOf(length)
        }

        private fun rawHmac(key: ByteArray, data: ByteArray): ByteArray {
            val mac = Mac.getInstance(HMAC)
            mac.init(SecretKeySpec(key, HMAC))
            return mac.doFinal(data)
        }
    }
}
