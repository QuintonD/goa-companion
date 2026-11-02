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
         * Trimmed, 2 to 24 characters, nothing a screen or a log could trip over. Clients render names as
         * text, but `<` and `>` are refused anyway. Nobody can take the name deleted accounts get.
         */
        fun cleanName(raw: String): String? {
            val name = raw.trim()
            val fits = name.codePointCount(0, name.length) in MIN_NAME..MAX_NAME
            val refused = name.any { it == '<' || it == '>' || it.isISOControl() || it.category in HIDDEN }
            return name.takeIf { fits && !refused && !it.equals(FORMER_PLAYER, ignoreCase = true) }
        }

        /** Bidi overrides, zero-width marks and line separators: they make a name lie about itself. */
        private val HIDDEN = setOf(CharCategory.FORMAT, CharCategory.LINE_SEPARATOR, CharCategory.PARAGRAPH_SEPARATOR)
    }
}
