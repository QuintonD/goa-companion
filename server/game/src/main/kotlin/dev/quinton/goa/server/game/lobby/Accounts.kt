package dev.quinton.goa.server.game.lobby

import dev.quinton.goa.core.online.AccountCreated
import dev.quinton.goa.server.game.store.AccountRow
import dev.quinton.goa.server.game.store.AccountStore
import dev.quinton.goa.server.game.store.Secrets

/** Accounts with nothing personal in them (docs/ONLINE.md): a display name, and hashes of two secrets. */
class Accounts(private val store: AccountStore, private val clock: () -> Long = System::currentTimeMillis) {
    suspend fun create(rawName: String): AccountCreated {
        val name = cleanName(rawName) ?: throw LobbyRefused(LobbyError.BAD_NAME)
        val id = Secrets.accountId()
        val token = Secrets.token()
        val recovery = Secrets.recoveryCode()
        store.createAccount(
            AccountRow(id, name, clock()),
            Secrets.hash(token),
            Secrets.hash(Secrets.normalizeCode(recovery)),
        )
        return AccountCreated(id, token, recovery)
    }

    /** Burns the code and issues a new one, with a new token; the other device's token keeps working. */
    suspend fun link(rawCode: String): AccountCreated {
        val token = Secrets.token()
        val recovery = Secrets.recoveryCode()
        val account = store.redeemRecovery(
            Secrets.hash(Secrets.normalizeCode(rawCode)),
            Secrets.hash(token),
            Secrets.hash(Secrets.normalizeCode(recovery)),
            clock(),
        ) ?: throw LobbyRefused(LobbyError.BAD_RECOVERY_CODE)
        return AccountCreated(account.id, token, recovery)
    }

    suspend fun authenticate(token: String): AccountRow? {
        if (!TOKEN.matches(token)) return null
        val hash = Secrets.hash(token)
        val match = store.accountByToken(hash, clock()) ?: return null
        return match.account.takeIf { Secrets.sameHash(match.storedHash, hash) }
    }

    suspend fun rename(account: AccountRow, rawName: String): AccountRow {
        val name = cleanName(rawName) ?: throw LobbyRefused(LobbyError.BAD_NAME)
        store.rename(account.id, name)
        return account.copy(name = name)
    }

    suspend fun delete(account: AccountRow) = store.anonymise(account.id, FORMER_PLAYER, clock())

    companion object {
        const val FORMER_PLAYER = "Former player"
        private const val MIN_NAME = 2
        private const val MAX_NAME = 24
        private val TOKEN = Regex("[A-Za-z0-9_-]{16,256}")

        /**
         * Trimmed with runs of spaces collapsed, 2 to 24 characters, nothing a screen or a log could trip over,
         * and something to see. Checked by code point, as a lone surrogate or a character beyond the first
         * plane would slip past a check of chars. Clients render names as text, but the characters of HTML are
         * refused anyway. Nobody can take the name deleted accounts get. `validate_leaderboard.py` holds
         * published names to the same rules: a name this lets through and that refuses stops the board.
         */
        fun cleanName(raw: String): String? {
            val name = raw.trim().replace(SPACES, " ")
            val points = name.codePoints().toArray()
            val fits = points.size in MIN_NAME..MAX_NAME
            val refused = points.any { it in REFUSED_CHARS || Character.getType(it).toByte() in HIDDEN }
            val visible = points.any { Character.getType(it).toByte() !in INVISIBLE }
            return name.takeIf { fits && !refused && visible && !it.equals(FORMER_PLAYER, ignoreCase = true) }
        }

        private val SPACES = Regex("\\p{Zs}+")

        /** The characters of HTML and of a quoted attribute, and those that look blank though they are letters. */
        private val REFUSED_CHARS: Set<Int> =
            "<>\"'&`".map { it.code }.toSet() + setOf(0x115F, 0x1160, 0x2800, 0x3164, 0xFFA0)

        /**
         * Bidi overrides, zero-width marks, control characters and line breaks make a name lie about itself;
         * a lone surrogate can't be encoded, and a character not assigned yet or private has no agreed look.
         */
        private val HIDDEN: Set<Byte> = setOf(
            Character.FORMAT,
            Character.SURROGATE,
            Character.UNASSIGNED,
            Character.CONTROL,
            Character.PRIVATE_USE,
            Character.LINE_SEPARATOR,
            Character.PARAGRAPH_SEPARATOR,
        )

        /** Spaces and marks with nothing to sit on: a name of only these can't be seen. */
        private val INVISIBLE: Set<Byte> =
            setOf(Character.SPACE_SEPARATOR, Character.NON_SPACING_MARK, Character.ENCLOSING_MARK)
    }
}
