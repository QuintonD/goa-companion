package dev.quinton.goa.server.game.room

import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.GameSetup
import dev.quinton.goa.core.game.WinReason
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.online.LeaderboardSnapshot
import dev.quinton.goa.core.online.SeatPlayer
import dev.quinton.goa.core.online.ServerMessage
import dev.quinton.goa.core.online.TurnRef
import dev.quinton.goa.server.game.store.GameRow
import dev.quinton.goa.server.game.store.GameSeatRow

/** Timings of a game (docs/ONLINE.md "Rules of the mode"); tests pass their own. */
data class RoomRules(
    val playCardMillis: Long = 60_000,
    val otherMillis: Long = 45_000,
    val disconnectedMillis: Long = 15_000,
    val allAwayPauseMillis: Long = 10 * 60_000,
    val abandonMillis: Long = 24 * 60 * 60_000,
    val reviewEveryMillis: Long = 5_000,
)

/** Where one person's frames go; the HTTP layer keeps only the latest table per connection. */
fun interface SeatSink {
    fun send(message: ServerMessage)
}

/** A person's link to a running game. Closing it marks them away; their seat is not given up. */
fun interface SeatLink {
    fun close()
}

/**
 * Running games, between the socket (http) and the rules (room). The lobby calls [start]; each socket
 * calls [connect] for the person's games. Implemented by `RoomCore`; the HTTP stream tests with a fake.
 */
interface GameRooms {
    /** Everything [restore] and [start] have running; used for the cap and `/v1/health`. */
    val runningCount: Int

    /** Rebuilds running games from the store after a restart; voids those whose past changed. */
    suspend fun restore()

    /** Creates the game, durably, and returns its id. Frames go out as people [connect]. */
    suspend fun start(roomCode: String, setup: GameSetup, players: List<SeatPlayer>, ranked: Boolean): String

    /** Sends [ServerMessage.GameStart] and the current table, events from [haveEvents] on. */
    suspend fun connect(gameId: String, accountId: String, haveEvents: Int, sink: SeatSink): SeatLink?

    /** The result comes back as [ServerMessage.Ack] on the person's sink; a repeated ticket is harmless. */
    suspend fun answer(gameId: String, accountId: String, ticket: Long, choice: Choice)

    suspend fun review(gameId: String, accountId: String, rewind: TurnRef?)

    /** The person gives up their seat for good: the computer plays it to the end. */
    suspend fun leave(gameId: String, accountId: String)

    /** Games to go back to: those running, and those that ended with this person not there to hear it. */
    suspend fun gamesOf(accountId: String): List<String>

    /** Those still running: what the limit on games counts. A game that is over takes up nobody's time. */
    suspend fun runningGamesOf(accountId: String): List<String> = gamesOf(accountId)
}

/** Builds the log's [Match] for a finished game. In `ratings/`, passed to `RoomCore`. */
fun interface MatchMapper {
    fun matchOf(game: GameRow, seats: List<GameSeatRow>, winner: Team, reason: WinReason, finishedAt: Long): Match
}

/** The current board, derived from the match log on demand. In `ratings/`, served by http. */
fun interface LeaderboardSource {
    suspend fun snapshot(): LeaderboardSnapshot
}
