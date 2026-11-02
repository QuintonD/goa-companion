package dev.quinton.goa.server.game.store

import dev.quinton.goa.core.game.GameLength
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.online.RoomStatus

data class AccountRow(val id: String, val name: String, val createdAt: Long, val deletedAt: Long? = null)

/** A token's row found by its hash, with the hash as stored so the caller can compare it in constant time. */
data class TokenMatch(val storedHash: String, val account: AccountRow)

/** The people side of the store. Only hashes of secrets reach it. */
interface AccountStore {
    suspend fun createAccount(account: AccountRow, tokenHash: String, recoveryHash: String)

    /** The live account behind an unrevoked token; marks it as seen, at most once in a while. */
    suspend fun accountByToken(tokenHash: String, now: Long): TokenMatch?

    suspend fun account(id: String): AccountRow?

    /** One transaction: burns the code, issues a new token and a new code. Null if the code is no good. */
    suspend fun redeemRecovery(
        recoveryHash: String,
        newTokenHash: String,
        newRecoveryHash: String,
        now: Long,
    ): AccountRow?

    suspend fun rename(id: String, name: String)

    /** Keeps the id and every match row; the name goes, every token and code stops working. */
    suspend fun anonymise(id: String, name: String, now: Long)
}

data class RoomSeatRow(
    val team: Team,
    val index: Int,
    val heroId: String? = null,
    val accountId: String? = null,
    val ready: Boolean = false,
)

data class RoomRow(
    val code: String,
    val hostAccount: String,
    val mapId: String,
    val length: GameLength,
    val teamSize: Int,
    val ranked: Boolean,
    val createdAt: Long,
    val expiresAt: Long,
    val status: RoomStatus,
    val seats: List<RoomSeatRow>,
    val gameId: String? = null,
)

/** What `GET /v1/me/games` lists for one game. */
data class GameSummary(
    val gameId: String,
    val roomCode: String,
    val mapId: String,
    val seats: List<GameSeatRow>,
)

/** Rooms before they are games. */
interface LobbyStore {
    /** Writes the room and all its seats in one transaction. */
    suspend fun putRoom(room: RoomRow)

    suspend fun room(code: String): RoomRow?

    suspend fun openRooms(): List<RoomRow>

    /**
     * After a crash between a game's creation and its room's update: the game exists, so the room has
     * started. Returns how many rooms this repaired.
     */
    suspend fun markStartedRooms(): Int

    suspend fun names(accountIds: Collection<String>): Map<String, String>

    /** Rows that exist for [gameIds], in that order. */
    suspend fun gameSummaries(gameIds: List<String>): List<GameSummary>

    suspend fun isAvailable(): Boolean
}
