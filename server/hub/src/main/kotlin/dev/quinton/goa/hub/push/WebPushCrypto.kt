package dev.quinton.goa.hub.push

import java.math.BigInteger
import java.nio.ByteBuffer
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPrivateKeySpec
import java.security.spec.ECPublicKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * P-256 keys as Web Push writes them: the uncompressed point `0x04 ‖ X ‖ Y` (65 bytes) and the raw
 * 32-byte private scalar. JDK crypto only.
 */
object P256 {
    const val PUBLIC_BYTES = 65
    private const val COORD_BYTES = 32

    val params: ECParameterSpec = AlgorithmParameters.getInstance("EC").run {
        init(ECGenParameterSpec("secp256r1"))
        getParameterSpec(ECParameterSpec::class.java)
    }

    fun generate(random: SecureRandom = SecureRandom()): KeyPair =
        KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"), random)
        }.generateKeyPair()

    fun encodePublic(key: ECPublicKey): ByteArray =
        byteArrayOf(UNCOMPRESSED) + unsigned(key.w.affineX) + unsigned(key.w.affineY)

    fun decodePublic(bytes: ByteArray): ECPublicKey {
        require(bytes.size == PUBLIC_BYTES && bytes[0] == UNCOMPRESSED) { "Not an uncompressed P-256 point" }
        val x = BigInteger(1, bytes.copyOfRange(1, 1 + COORD_BYTES))
        val y = BigInteger(1, bytes.copyOfRange(1 + COORD_BYTES, PUBLIC_BYTES))
        return KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(ECPoint(x, y), params)) as ECPublicKey
    }

    fun encodePrivate(key: ECPrivateKey): ByteArray = unsigned(key.s)

    fun decodePrivate(bytes: ByteArray): ECPrivateKey =
        KeyFactory.getInstance("EC").generatePrivate(ECPrivateKeySpec(BigInteger(1, bytes), params)) as ECPrivateKey

    /** Rebuilds a pair from its encoded halves. */
    fun keyPair(
        publicKey: ByteArray,
        privateKey: ByteArray,
    ) = KeyPair(decodePublic(publicKey), decodePrivate(privateKey))

    private fun unsigned(value: BigInteger): ByteArray {
        val raw = value.toByteArray()
        return when {
            raw.size == COORD_BYTES -> raw
            raw.size > COORD_BYTES -> raw.copyOfRange(raw.size - COORD_BYTES, raw.size)
            else -> ByteArray(COORD_BYTES - raw.size) + raw
        }
    }

    private const val UNCOMPRESSED: Byte = 0x04
}

/**
 * Message encryption for Web Push, RFC 8291 with the `aes128gcm` content coding of RFC 8188: one
 * record, the sender's ephemeral key in the header. Checked against RFC 8291's worked example.
 */
object WebPushCrypto {
    const val RECORD_SIZE = 4096
    private const val SALT_BYTES = 16
    private const val KEY_BYTES = 16
    private const val NONCE_BYTES = 12
    private const val TAG_BITS = 128
    private const val PADDING_DELIMITER: Byte = 0x02

    /** Largest plaintext that fits in one record (record size minus tag, delimiter and header slack). */
    const val MAX_PLAINTEXT = 3993

    /**
     * Encrypts [plaintext] for a subscription with public key [uaPublic] (65 bytes) and [authSecret]
     * (16 bytes). [senderKeys] and [salt] are fresh per message; tests pin them.
     */
    fun encrypt(
        plaintext: ByteArray,
        uaPublic: ByteArray,
        authSecret: ByteArray,
        senderKeys: KeyPair = P256.generate(),
        salt: ByteArray = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) },
    ): ByteArray {
        require(plaintext.size <= MAX_PLAINTEXT) { "Push payload too large" }
        require(salt.size == SALT_BYTES) { "Salt must be 16 bytes" }
        val asPublic = P256.encodePublic(senderKeys.public as ECPublicKey)
        val ecdhSecret = KeyAgreement.getInstance("ECDH").run {
            init(senderKeys.private)
            doPhase(P256.decodePublic(uaPublic), true)
            generateSecret()
        }
        // RFC 8291 §3.3: combine the ECDH secret with the auth secret, bound to both public keys.
        val prkKey = hmac(authSecret, ecdhSecret)
        val keyInfo = "WebPush: info".toByteArray() + 0 + uaPublic + asPublic
        val ikm = hmac(prkKey, keyInfo + 1)
        // RFC 8188 §2.2: content encryption key and nonce from the salt.
        val prk = hmac(salt, ikm)
        val cek = hmac(prk, "Content-Encoding: aes128gcm".toByteArray() + 0 + 1).copyOf(KEY_BYTES)
        val nonce = hmac(prk, "Content-Encoding: nonce".toByteArray() + 0 + 1).copyOf(NONCE_BYTES)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(cek, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        }
        val ciphertext = cipher.doFinal(plaintext + PADDING_DELIMITER)
        val header = ByteBuffer.allocate(SALT_BYTES + Int.SIZE_BYTES + 1 + asPublic.size)
            .put(salt).putInt(RECORD_SIZE).put(asPublic.size.toByte()).put(asPublic).array()
        return header + ciphertext
    }

    private fun hmac(key: ByteArray, data: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(key, "HmacSHA256"))
            doFinal(data)
        }

    private operator fun ByteArray.plus(byte: Int): ByteArray = this + byte.toByte()
}
