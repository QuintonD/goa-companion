package dev.quinton.goa.core.domain.sync

import java.security.GeneralSecurityException
import java.security.MessageDigest
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * The few primitives group sync needs, from `javax.crypto` only (ADR-025): HKDF-SHA256 key
 * derivation, HMAC-SHA256 proofs and AES-256-GCM sealing. Keys are never used directly: each use
 * derives its own key from the group key with a distinct label.
 *
 * JVM only (ADR-043): the browser seals with WebCrypto through [SyncCipher]; [JvmSyncCipher] is
 * this object behind that interface, and the shared vectors (SyncVectors) hold both to the same bytes.
 */
object SyncCrypto {
    const val KEY_BYTES = SyncKeys.KEY_BYTES
    const val NONCE_BYTES = 16
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private const val HMAC = "HmacSHA256"

    fun newGroupKey(random: SecureBytes = SecureBytes.System): ByteArray = SyncKeys.newGroupKey(random)

    /** RFC 5869 HKDF with SHA-256. */
    fun hkdf(ikm: ByteArray, salt: ByteArray, info: String, length: Int = KEY_BYTES): ByteArray {
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

    /** HMAC-SHA256 over length-prefixed [parts]. */
    fun hmac(key: ByteArray, vararg parts: ByteArray): ByteArray {
        val mac = Mac.getInstance(HMAC)
        mac.init(SecretKeySpec(key, HMAC))
        parts.forEach { part ->
            // Length-prefixed, so ("ab", "c") and ("a", "bc") never give the same MAC.
            mac.update(lengthOf(part))
            mac.update(part)
        }
        return mac.doFinal()
    }

    /** Constant-time comparison for MACs. */
    fun same(a: ByteArray, b: ByteArray): Boolean = MessageDigest.isEqual(a, b)

    /** AES-256-GCM: `iv ‖ ciphertext ‖ tag`. [aad] is authenticated but not encrypted. */
    fun seal(
        key: ByteArray,
        plaintext: ByteArray,
        aad: ByteArray,
        random: SecureBytes = SecureBytes.System,
    ): ByteArray {
        val iv = random.next(IV_BYTES)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(aad)
        return iv + cipher.doFinal(plaintext)
    }

    @Suppress("ThrowsCount")
    fun open(key: ByteArray, sealed: ByteArray, aad: ByteArray): ByteArray {
        if (sealed.size < IV_BYTES + TAG_BITS / 8) throw SyncCryptoException("Sealed data is too short")
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(key, "AES"),
                GCMParameterSpec(TAG_BITS, sealed, 0, IV_BYTES),
            )
            cipher.updateAAD(aad)
            cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES)
        } catch (e: AEADBadTagException) {
            throw SyncCryptoException("Wrong key or tampered data", e)
        } catch (e: GeneralSecurityException) {
            throw SyncCryptoException("Can't decrypt", e)
        }
    }

    /**
     * A short, non-secret tag for a group, shown in the Nearby endpoint name so phones find their
     * own group's hosts: the first 8 hex digits of SHA-256(groupId). It proves nothing; the key
     * proof does.
     */
    fun groupTag(groupId: String): String = MessageDigest.getInstance("SHA-256")
        .digest(groupId.toByteArray(Charsets.UTF_8))
        .take(4)
        .joinToString("") { "%02x".format(it) }

    private fun lengthOf(part: ByteArray) = byteArrayOf(
        (part.size ushr 24).toByte(),
        (part.size ushr 16).toByte(),
        (part.size ushr 8).toByte(),
        part.size.toByte(),
    )
}
