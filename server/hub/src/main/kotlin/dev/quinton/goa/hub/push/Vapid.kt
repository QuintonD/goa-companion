package dev.quinton.goa.hub.push

import dev.quinton.goa.hub.store.AtomicFiles
import java.net.URI
import java.nio.file.Path
import java.security.KeyPair
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The hub's VAPID identity (RFC 8292): a P-256 key pair made at first start and kept in
 * `vapid.json` (owner-only). Browsers subscribe with [publicKey]; every push carries a JWT signed
 * with the private key, so push services know the messages come from the same server.
 */
class Vapid(val keys: KeyPair, private val subject: () -> String) {
    /** The application server key browsers subscribe with (base64url of the 65-byte point). */
    val publicKey: String = b64(P256.encodePublic(keys.public as ECPublicKey))

    /** `Authorization` header value for a push to [endpoint], valid for [ttlSeconds] from [nowSeconds]. */
    fun authorization(endpoint: String, nowSeconds: Long, ttlSeconds: Long = DEFAULT_TTL_SECONDS): String {
        val uri = URI(endpoint)
        val audience = "${uri.scheme}://${uri.host}${if (uri.port != -1) ":${uri.port}" else ""}"
        val header = b64("""{"typ":"JWT","alg":"ES256"}""".toByteArray())
        val claims = buildJsonObject {
            put("aud", audience)
            put("exp", nowSeconds + ttlSeconds)
            put("sub", subject())
        }
        val body = b64(claims.toString().toByteArray())
        val signature = Signature.getInstance("SHA256withECDSAinP1363Format").run {
            initSign(keys.private)
            update("$header.$body".toByteArray(Charsets.US_ASCII))
            sign()
        }
        return "vapid t=$header.$body.${b64(signature)}, k=$publicKey"
    }

    companion object {
        /** Push services refuse tokens valid for more than 24 h; 12 h is the usual choice. */
        const val DEFAULT_TTL_SECONDS = 12 * 3600L

        @Serializable
        private data class Stored(val publicKey: String, val privateKey: String)

        /** Loads the key pair from [file], making and storing one on first use. */
        fun loadOrCreate(file: Path, subject: () -> String): Vapid {
            val stored = AtomicFiles.readOrNull(file)?.let {
                Json.decodeFromString(Stored.serializer(), it.decodeToString())
            }
            val keys = if (stored != null) {
                P256.keyPair(unb64(stored.publicKey), unb64(stored.privateKey))
            } else {
                P256.generate().also { pair ->
                    val fresh = Stored(
                        b64(P256.encodePublic(pair.public as ECPublicKey)),
                        b64(P256.encodePrivate(pair.private as java.security.interfaces.ECPrivateKey)),
                    )
                    AtomicFiles.write(
                        file,
                        Json.encodeToString(Stored.serializer(), fresh).toByteArray(),
                        private = true,
                    )
                }
            }
            return Vapid(keys, subject)
        }

        fun b64(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

        fun unb64(text: String): ByteArray = Base64.getUrlDecoder().decode(text.trim().trimEnd('='))
    }
}
