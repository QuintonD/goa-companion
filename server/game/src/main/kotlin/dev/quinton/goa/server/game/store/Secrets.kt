package dev.quinton.goa.server.game.store

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Tokens, codes and ids. A token and a recovery code are stored only as their SHA-256; a hash is compared
 * in constant time, even after an index found it.
 */
object Secrets {
    private const val CROCKFORD = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    private const val TOKEN_BYTES = 32
    private const val ACCOUNT_ID_CHARS = 20
    const val ROOM_CODE_CHARS = 8
    private const val RECOVERY_CHARS = 25
    private const val RECOVERY_GROUP = 5

    private val random = SecureRandom()

    /** 256 random bits, URL-safe. */
    fun token(): String {
        val bytes = ByteArray(TOKEN_BYTES)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    /** Public: it goes on the board and in every room view. */
    fun accountId(): String = crockford(ACCOUNT_ID_CHARS).lowercase()

    fun gameId(): String = crockford(ACCOUNT_ID_CHARS).lowercase()

    /** 40 random bits: enough for a room that lives two hours behind a rate limit. */
    fun roomCode(): String = crockford(ROOM_CODE_CHARS)

    /** 125 random bits in groups, to be copied by hand: `XXXXX-XXXXX-XXXXX-XXXXX-XXXXX`. */
    fun recoveryCode(): String = crockford(RECOVERY_CHARS).chunked(RECOVERY_GROUP).joinToString("-")

    /** The same code however it was typed: case, dashes, spaces, and Crockford's look-alike letters. */
    fun normalizeCode(raw: String): String = buildString {
        for (c in raw.uppercase()) {
            when (c) {
                '-', ' ' -> Unit
                'O' -> append('0')
                'I', 'L' -> append('1')
                else -> append(c)
            }
        }
    }

    fun isRoomCode(code: String): Boolean = code.length == ROOM_CODE_CHARS && code.all { it in CROCKFORD }

    fun hash(secret: String): String = MessageDigest.getInstance("SHA-256")
        .digest(secret.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    fun sameHash(a: String, b: String): Boolean =
        MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))

    fun coin(): Boolean = random.nextBoolean()

    private fun crockford(length: Int): String =
        String(CharArray(length) { CROCKFORD[random.nextInt(CROCKFORD.length)] })
}
