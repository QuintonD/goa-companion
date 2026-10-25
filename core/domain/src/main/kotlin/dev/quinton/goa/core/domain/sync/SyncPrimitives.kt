package dev.quinton.goa.core.domain.sync

/** Fills a byte array with secure random bytes; injectable so protocol tests are reproducible. */
fun interface SecureBytes {
    fun next(size: Int): ByteArray

    companion object {
        /** The platform's secure source: `SecureRandom` on the JVM, `crypto.getRandomValues` in the browser. */
        val System = SecureBytes { size -> secureRandomBytes(size) }
    }
}

/** [size] bytes from the platform's cryptographically secure random source. */
internal expect fun secureRandomBytes(size: Int): ByteArray

/** Data could not be decrypted: tampered with, or sealed with another key. */
class SyncCryptoException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Group keys (ADR-025): 256 random bits. */
object SyncKeys {
    const val KEY_BYTES = 32

    fun newGroupKey(random: SecureBytes = SecureBytes.System): ByteArray = random.next(KEY_BYTES)
}

/**
 * What group sync needs from the platform (ADR-043): AES-256-GCM and gzip. The Android app
 * implements it with `javax.crypto` and `java.util.zip` (`JvmSyncCipher`, synchronous underneath);
 * the browser with WebCrypto and `CompressionStream`, which are asynchronous, hence `suspend`.
 * Everything around it (key derivation, envelope, labels, validation) is shared code
 * ([SyncFileCodec], [SyncHash]), and `SyncVectors` in commonTest pins the bytes both must produce.
 */
interface SyncCipher {
    /**
     * AES-256-GCM with a 128-bit tag over [plaintext], [aad] authenticated but not encrypted:
     * `iv ‖ ciphertext ‖ tag`. [iv] is [IV_BYTES] fresh random bytes, chosen by the caller.
     */
    suspend fun seal(key: ByteArray, plaintext: ByteArray, aad: ByteArray, iv: ByteArray): ByteArray

    /** The plaintext of [seal]'s output; throws [SyncCryptoException] for a wrong key, other [aad] or tampering. */
    suspend fun open(key: ByteArray, sealed: ByteArray, aad: ByteArray): ByteArray

    /** gzip (RFC 1952) of [bytes]. The bytes may differ between platforms; [gunzip] of them may not. */
    suspend fun gzip(bytes: ByteArray): ByteArray

    /** Inflates gzip [bytes]; throws (any exception) when they aren't gzip or inflate past [maxBytes]. */
    suspend fun gunzip(bytes: ByteArray, maxBytes: Int): ByteArray

    companion object {
        const val IV_BYTES = 12
        const val TAG_BYTES = 16
    }
}

/**
 * SHA-256 (FIPS 180-4), HMAC-SHA256 (RFC 2104) and HKDF-SHA256 (RFC 5869) in plain Kotlin, the same
 * on every platform and synchronous, so the sync-server token, the group tag and the file key are
 * derived by one implementation everywhere (ADR-043). They agree with `javax.crypto` byte for byte
 * (`SyncHashTest` compares them on random inputs; `SyncVectors` pins published vectors).
 */
object SyncHash {
    private const val BLOCK = 64

    fun sha256(data: ByteArray): ByteArray = Sha256().apply { update(data) }.digest()

    /** HMAC-SHA256 of [data] under [key]. */
    fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val k = (if (key.size > BLOCK) sha256(key) else key).copyOf(BLOCK)
        val inner = Sha256().apply {
            update(ByteArray(BLOCK) { (k[it].toInt() xor IPAD).toByte() })
            update(data)
        }.digest()
        return Sha256().apply {
            update(ByteArray(BLOCK) { (k[it].toInt() xor OPAD).toByte() })
            update(inner)
        }.digest()
    }

    /** HMAC-SHA256 over length-prefixed [parts], so ("ab", "c") and ("a", "bc") never give the same MAC. */
    fun hmac(key: ByteArray, vararg parts: ByteArray): ByteArray {
        var data = ByteArray(0)
        parts.forEach { data += lengthOf(it) + it }
        return hmacSha256(key, data)
    }

    /** RFC 5869 HKDF with SHA-256; an empty [salt] counts as 32 zero bytes. */
    fun hkdf(ikm: ByteArray, salt: ByteArray, info: String, length: Int = SyncKeys.KEY_BYTES): ByteArray {
        val prk = hmacSha256(salt.takeIf { it.isNotEmpty() } ?: ByteArray(SyncKeys.KEY_BYTES), ikm)
        val infoBytes = info.encodeToByteArray()
        var out = ByteArray(0)
        var block = ByteArray(0)
        var counter = 1
        while (out.size < length) {
            block = hmacSha256(prk, block + infoBytes + byteArrayOf(counter.toByte()))
            out += block
            counter++
        }
        return out.copyOf(length)
    }

    /** Constant-time comparison for MACs. */
    fun same(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
        return diff == 0
    }

    /**
     * A short, non-secret tag for a group, shown in the Nearby endpoint name so phones find their
     * own group's hosts: the first 8 hex digits of SHA-256(groupId). It proves nothing; the key
     * proof does.
     */
    fun groupTag(groupId: String): String = hex(sha256(groupId.encodeToByteArray()).copyOf(4))

    /** Lowercase hex, two digits a byte. */
    fun hex(bytes: ByteArray): String = buildString(bytes.size * 2) {
        bytes.forEach { b ->
            val v = b.toInt() and 0xff
            append(HEX[v ushr 4])
            append(HEX[v and 0x0f])
        }
    }

    private const val HEX = "0123456789abcdef"
    private const val IPAD = 0x36
    private const val OPAD = 0x5c

    private fun lengthOf(part: ByteArray) = byteArrayOf(
        (part.size ushr 24).toByte(),
        (part.size ushr 16).toByte(),
        (part.size ushr 8).toByte(),
        part.size.toByte(),
    )
}

/** Streaming SHA-256 (FIPS 180-4 §6.2). */
@Suppress("MagicNumber")
private class Sha256 {
    private val h = intArrayOf(
        0x6a09e667.toInt(),
        0xbb67ae85.toInt(),
        0x3c6ef372.toInt(),
        0xa54ff53a.toInt(),
        0x510e527f.toInt(),
        0x9b05688c.toInt(),
        0x1f83d9ab.toInt(),
        0x5be0cd19.toInt(),
    )
    private val buffer = ByteArray(64)
    private var buffered = 0
    private var total = 0L
    private val w = IntArray(64)

    fun update(data: ByteArray) {
        for (b in data) {
            buffer[buffered++] = b
            if (buffered == 64) {
                compress()
                buffered = 0
            }
        }
        total += data.size
    }

    fun digest(): ByteArray {
        val bits = total * 8
        update(byteArrayOf(0x80.toByte()))
        while (buffered != 56) update(byteArrayOf(0))
        update(ByteArray(8) { (bits ushr (56 - 8 * it)).toByte() })
        return ByteArray(32) { (h[it / 4] ushr (24 - 8 * (it % 4))).toByte() }
    }

    private fun compress() {
        for (t in 0 until 16) {
            w[t] = ((buffer[4 * t].toInt() and 0xff) shl 24) or
                ((buffer[4 * t + 1].toInt() and 0xff) shl 16) or
                ((buffer[4 * t + 2].toInt() and 0xff) shl 8) or
                (buffer[4 * t + 3].toInt() and 0xff)
        }
        for (t in 16 until 64) {
            val s0 = w[t - 15].rotateRight(7) xor w[t - 15].rotateRight(18) xor (w[t - 15] ushr 3)
            val s1 = w[t - 2].rotateRight(17) xor w[t - 2].rotateRight(19) xor (w[t - 2] ushr 10)
            w[t] = w[t - 16] + s0 + w[t - 7] + s1
        }
        var a = h[0]
        var b = h[1]
        var c = h[2]
        var d = h[3]
        var e = h[4]
        var f = h[5]
        var g = h[6]
        var hh = h[7]
        for (t in 0 until 64) {
            val s1 = e.rotateRight(6) xor e.rotateRight(11) xor e.rotateRight(25)
            val ch = (e and f) xor (e.inv() and g)
            val t1 = hh + s1 + ch + K[t] + w[t]
            val s0 = a.rotateRight(2) xor a.rotateRight(13) xor a.rotateRight(22)
            val maj = (a and b) xor (a and c) xor (b and c)
            val t2 = s0 + maj
            hh = g
            g = f
            f = e
            e = d + t1
            d = c
            c = b
            b = a
            a = t1 + t2
        }
        h[0] += a
        h[1] += b
        h[2] += c
        h[3] += d
        h[4] += e
        h[5] += f
        h[6] += g
        h[7] += hh
    }

    private companion object {
        val K = intArrayOf(
            0x428a2f98.toInt(), 0x71374491.toInt(), 0xb5c0fbcf.toInt(), 0xe9b5dba5.toInt(),
            0x3956c25b.toInt(), 0x59f111f1.toInt(), 0x923f82a4.toInt(), 0xab1c5ed5.toInt(),
            0xd807aa98.toInt(), 0x12835b01.toInt(), 0x243185be.toInt(), 0x550c7dc3.toInt(),
            0x72be5d74.toInt(), 0x80deb1fe.toInt(), 0x9bdc06a7.toInt(), 0xc19bf174.toInt(),
            0xe49b69c1.toInt(), 0xefbe4786.toInt(), 0x0fc19dc6.toInt(), 0x240ca1cc.toInt(),
            0x2de92c6f.toInt(), 0x4a7484aa.toInt(), 0x5cb0a9dc.toInt(), 0x76f988da.toInt(),
            0x983e5152.toInt(), 0xa831c66d.toInt(), 0xb00327c8.toInt(), 0xbf597fc7.toInt(),
            0xc6e00bf3.toInt(), 0xd5a79147.toInt(), 0x06ca6351.toInt(), 0x14292967.toInt(),
            0x27b70a85.toInt(), 0x2e1b2138.toInt(), 0x4d2c6dfc.toInt(), 0x53380d13.toInt(),
            0x650a7354.toInt(), 0x766a0abb.toInt(), 0x81c2c92e.toInt(), 0x92722c85.toInt(),
            0xa2bfe8a1.toInt(), 0xa81a664b.toInt(), 0xc24b8b70.toInt(), 0xc76c51a3.toInt(),
            0xd192e819.toInt(), 0xd6990624.toInt(), 0xf40e3585.toInt(), 0x106aa070.toInt(),
            0x19a4c116.toInt(), 0x1e376c08.toInt(), 0x2748774c.toInt(), 0x34b0bcb5.toInt(),
            0x391c0cb3.toInt(), 0x4ed8aa4a.toInt(), 0x5b9cca4f.toInt(), 0x682e6ff3.toInt(),
            0x748f82ee.toInt(), 0x78a5636f.toInt(), 0x84c87814.toInt(), 0x8cc70208.toInt(),
            0x90befffa.toInt(), 0xa4506ceb.toInt(), 0xbef9a3f7.toInt(), 0xc67178f2.toInt(),
        )
    }
}
