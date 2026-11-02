package dev.quinton.goa.server.game.store

import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.GameLength
import dev.quinton.goa.core.game.GameSetup
import dev.quinton.goa.core.game.SavedGame
import dev.quinton.goa.core.game.engine.AnswerRecord
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.online.VoidReason

/** Who produced an answer: the person, the clock's computer, or the server's lone-option pass. */
enum class AnswerOrigin { HUMAN, CLOCK, AUTO }

data class GameRow(
    val id: String,
    val roomCode: String,
    val setup: GameSetup,
    /** The game at its start: replaying [StoredAnswer]s on this rebuilds it. Never leaves the server. */
    val start: SavedGame,
    val rulesBuild: String,
    val engineVersion: Int,
    val ranked: Boolean,
    val mapId: String,
    val length: GameLength,
    val createdAt: Long,
    /** Since when nobody has been connected; null while someone is. Survives a restart, as a pause must. */
    val awaySince: Long? = null,
)

data class GameSeatRow(
    val gameId: String,
    val heroId: String,
    val accountId: String,
    val team: Team,
    val handicap: Boolean,
    val seatOrder: Int,
    /** The person gave this seat up for good: a restart must not hand it back, or leaving would dodge a result. */
    val left: Boolean = false,
)

data class StoredAnswer(
    val gameId: String,
    val seq: Long,
    val record: AnswerRecord,
    val origin: AnswerOrigin,
    val at: Long,
    /** Rolling hash of the event text so far; [stateHash] at turn starts. Replay compares both. */
    val eventsHash: String,
    val stateHash: String?,
)

/** An answer to a simultaneous question that the engine has not asked yet (docs/ONLINE.md). */
data class StoredLock(
    val gameId: String,
    val seat: String,
    val ordinal: Int,
    val round: Int,
    val turn: Int,
    val group: String,
    val choice: Choice,
    val origin: AnswerOrigin,
    val at: Long,
)

data class RunningGame(
    val game: GameRow,
    val seats: List<GameSeatRow>,
    val answers: List<StoredAnswer>,
    val locks: List<StoredLock>,
)

data class GameResult(val finishedAt: Long, val rated: Boolean)

/** A game that is over, for whoever comes back to see how it ended. [voided] is null when the rules finished it. */
data class ClosedGame(val game: RunningGame, val voided: VoidReason?, val rated: Boolean)

/**
 * The durable side of games. An append is on disk when it returns: a frame about an answer goes out
 * only afterwards. Ratings are not here on purpose: they are derived from [matches].
 */
interface GameStore {
    suspend fun createGame(game: GameRow, seats: List<GameSeatRow>)

    suspend fun appendAnswer(answer: StoredAnswer)

    suspend fun markLeft(gameId: String, heroId: String, at: Long)

    /** When the last person went, or null when someone came back. */
    suspend fun setAwaySince(gameId: String, at: Long?)

    suspend fun putLock(lock: StoredLock)

    suspend fun clearLocks(gameId: String, seat: String)

    /** One transaction: the game closes and, when rated, its match joins the log. */
    suspend fun finish(gameId: String, result: GameResult, match: Match?)

    suspend fun void(gameId: String, reason: VoidReason, at: Long)

    /** Oldest first. Reads ids only, so one game's bad row can't stop the others being looked at. */
    suspend fun runningGameIds(): List<String>

    /** One game, whole: throws if any of its rows can't be read, and the room decides what that means. */
    suspend fun runningGame(id: String): RunningGame

    /** Who sits in a game, from the plain ids alone: readable when the game itself is not. */
    suspend fun accountsOf(gameId: String): Set<String>

    suspend fun runningGames(): List<RunningGame> = runningGameIds().map { runningGame(it) }

    /** Null for a game still running and one that was never here. */
    suspend fun closedGame(gameId: String): ClosedGame?

    /** The rated log, in [dev.quinton.goa.core.domain.rating.EloCalculator]'s replay order. */
    suspend fun matches(): List<Match>

    /** The log from [at] on, for a look at recent games that needn't read it all. */
    suspend fun matchesSince(at: Long): List<Match> = matches().filter { it.playedAt >= at }
}

/** For tests of the rooms and the ratings. Nothing survives the process. */
class InMemoryGameStore : GameStore {
    private val games = LinkedHashMap<String, GameRow>()
    private val seats = HashMap<String, List<GameSeatRow>>()
    private val answers = HashMap<String, MutableList<StoredAnswer>>()
    private val locks = HashMap<String, MutableList<StoredLock>>()
    private val log = ArrayList<Match>()
    private val closed = HashMap<String, ClosedGame>()

    private fun close(gameId: String, voided: VoidReason?, rated: Boolean) {
        val row = games.remove(gameId) ?: return
        val game = RunningGame(row, seats[gameId].orEmpty(), answers[gameId].orEmpty().toList(), emptyList())
        closed[gameId] = ClosedGame(game, voided, rated)
    }

    override suspend fun closedGame(gameId: String): ClosedGame? = closed[gameId]

    override suspend fun createGame(game: GameRow, seats: List<GameSeatRow>) {
        games[game.id] = game
        this.seats[game.id] = seats
    }

    override suspend fun appendAnswer(answer: StoredAnswer) {
        answers.getOrPut(answer.gameId) { ArrayList() } += answer
    }

    override suspend fun markLeft(gameId: String, heroId: String, at: Long) {
        seats[gameId] = seats[gameId].orEmpty().map { if (it.heroId == heroId) it.copy(left = true) else it }
    }

    override suspend fun setAwaySince(gameId: String, at: Long?) {
        games[gameId]?.let { games[gameId] = it.copy(awaySince = at) }
    }

    override suspend fun putLock(lock: StoredLock) {
        locks.getOrPut(lock.gameId) { ArrayList() } += lock
    }

    override suspend fun clearLocks(gameId: String, seat: String) {
        locks[gameId]?.removeAll { it.seat == seat }
    }

    override suspend fun finish(gameId: String, result: GameResult, match: Match?) {
        close(gameId, voided = null, rated = match != null)
        if (match != null) log += match
    }

    override suspend fun void(gameId: String, reason: VoidReason, at: Long) {
        close(gameId, voided = reason, rated = false)
    }

    override suspend fun runningGameIds(): List<String> = games.keys.toList()

    override suspend fun runningGame(id: String): RunningGame {
        val g = games.getValue(id)
        return RunningGame(g, seats[id].orEmpty(), answers[id].orEmpty().toList(), locks[id].orEmpty().toList())
    }

    override suspend fun accountsOf(gameId: String): Set<String> = seats[gameId].orEmpty().map { it.accountId }.toSet()

    override suspend fun matches(): List<Match> =
        log.sortedWith(compareBy({ it.playedAt }, { it.createdAt }, { it.id }))
}
