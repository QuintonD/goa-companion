package dev.quinton.goa.core.online

import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.core.game.GameLength
import dev.quinton.goa.core.game.GameSetup
import dev.quinton.goa.core.game.GameView
import dev.quinton.goa.core.game.TurnReview
import dev.quinton.goa.core.game.WinReason
import dev.quinton.goa.core.model.match.Team
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/*
 * What crosses the wire in an online game (ADR-056, docs/ONLINE.md). A hidden card must never be able
 * to travel here: no saved game, no engine state, no answer records, and a Decision goes in full only
 * to the seat it asks.
 */
object OnlineProtocol {
    /**
     * Goes up with any new sealed subtype or enum value that can reach a client: old decoders fail on
     * them. A plain added field with a default does not need it. `WireNamesTest` beside this module's
     * tests pins every such name and fails until its list is changed along with this.
     */
    const val VERSION: Int = 1

    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        allowStructuredMapKeys = true
    }
}

@Serializable
sealed interface ClientMessage {
    /** The first frame on a socket: a browser cannot set a header there, and a URL gets logged. */
    @Serializable
    @SerialName("hello")
    data class Hello(
        val protocol: Int,
        val token: String,
        /** For the logs, e.g. `android/0.13.0`. */
        val client: String,
        val catalogVersion: Int,
        val resume: List<Resume> = emptyList(),
    ) : ClientMessage

    @Serializable
    @SerialName("ping")
    data class Ping(val nonce: Long) : ClientMessage

    @Serializable
    @SerialName("room.watch")
    data class WatchRoom(val code: String) : ClientMessage

    /** Take a seat, or change hero; a null [heroId] clears the pick. */
    @Serializable
    @SerialName("room.seat")
    data class PickSeat(val code: String, val team: Team, val index: Int, val heroId: String?) : ClientMessage

    @Serializable
    @SerialName("room.ready")
    data class Ready(val code: String, val ready: Boolean) : ClientMessage

    @Serializable
    @SerialName("room.start")
    data class Start(val code: String) : ClientMessage

    /** [ticket] is the `id` of the question this seat was sent: never the engine's decision id. */
    @Serializable
    @SerialName("game.answer")
    data class Answer(val gameId: String, val ticket: Long, val choice: Choice) : ClientMessage

    @Serializable
    @SerialName("game.review")
    data class AskReview(val gameId: String, val rewind: TurnRef? = null) : ClientMessage

    @Serializable
    @SerialName("game.leave")
    data class Leave(val gameId: String) : ClientMessage
}

/** Events this client already holds, so a reconnect does not resend hundreds of KB. */
@Serializable
data class Resume(val gameId: String, val haveEvents: Int)

@Serializable
data class TurnRef(val round: Int, val turn: Int)

@Serializable
sealed interface ServerMessage {
    @Serializable
    @SerialName("welcome")
    data class Welcome(
        val protocol: Int,
        val accountId: String,
        val name: String,
        val serverTime: Long,
        val activeGames: List<String> = emptyList(),
    ) : ServerMessage

    @Serializable
    @SerialName("refused")
    data class Refused(val reason: Refusal, val retryAfterSeconds: Int? = null) : ServerMessage

    @Serializable
    @SerialName("pong")
    data class Pong(val nonce: Long, val serverTime: Long) : ServerMessage

    @Serializable
    @SerialName("room")
    data class Lobby(val room: RoomView) : ServerMessage

    @Serializable
    @SerialName("game.start")
    data class GameStart(
        val gameId: String,
        val setup: GameSetup,
        val players: List<SeatPlayer>,
        /** The seats this person plays. Always pass these to the table, never `setup.humans`. */
        val you: Set<String>,
        val ranked: Boolean,
    ) : ServerMessage

    /**
     * One seat's table. [decision] is this seat's own question, whose `id` is a ticket, or a stub
     * saying who is being asked (`id` 0, no options); null once the game is over.
     */
    @Serializable
    @SerialName("game.table")
    data class Table(
        val gameId: String,
        /** Per seat, never global: a shared counter would tell one seat about another's frames. */
        val seq: Long,
        val view: GameView,
        val decision: Decision?,
        val eventsFrom: Int,
        val events: List<GameEvent>,
        /** Seats whose answer to the current simultaneous question is in. */
        val locked: List<String> = emptyList(),
        val clock: DecisionClock? = null,
        val presence: Map<String, Presence> = emptyMap(),
        /** A long catch-up comes in pages: this one's events count, and the rest of the table follows. */
        val more: Boolean = false,
    ) : ServerMessage

    @Serializable
    @SerialName("game.ack")
    data class Ack(
        val gameId: String,
        val ticket: Long,
        val accepted: Boolean,
        val reason: String? = null,
    ) : ServerMessage

    @Serializable
    @SerialName("game.reviewed")
    data class Reviewed(
        val gameId: String,
        val reviews: List<TurnReview>,
        val pushes: Map<Team, Int> = emptyMap(),
        val rewind: GameView? = null,
    ) : ServerMessage

    @Serializable
    @SerialName("game.end")
    data class End(
        val gameId: String,
        val winner: Team?,
        val reason: WinReason?,
        val rated: Boolean,
        val voided: VoidReason? = null,
    ) : ServerMessage
}

@Serializable
enum class Refusal {
    @SerialName("update_app")
    UPDATE_APP,

    @SerialName("rooms_full")
    ROOMS_FULL,

    @SerialName("no_such_room")
    NO_SUCH_ROOM,

    @SerialName("room_taken")
    ROOM_TAKEN,

    @SerialName("bad_token")
    BAD_TOKEN,

    @SerialName("rate_limited")
    RATE_LIMITED,

    /** No new rooms while a deploy waits for running games to finish. */
    @SerialName("draining")
    DRAINING,

    @SerialName("not_host")
    NOT_HOST,

    /** Every seat must be taken, with a hero, and ready. */
    @SerialName("not_ready")
    NOT_READY,

    @SerialName("too_many_games")
    TOO_MANY_GAMES,

    @SerialName("too_many_rooms")
    TOO_MANY_ROOMS,
}

@Serializable
enum class VoidReason {
    /** A rules fix changed this game's past: replaying its answers no longer gives the same game. */
    @SerialName("rules_changed")
    RULES_CHANGED,

    /** Nobody came back for 24 hours. */
    @SerialName("abandoned")
    ABANDONED,
}

@Serializable
enum class Presence {
    @SerialName("here")
    HERE,

    @SerialName("away")
    AWAY,

    /** The person has been gone long enough that the computer answers for this seat; they can return. */
    @SerialName("computer")
    COMPUTER,
}

/** [deadlineAt] is the server's clock: correct for the phone's with [ServerMessage.Pong.serverTime]. */
@Serializable
data class DecisionClock(val seat: String, val deadlineAt: Long)

@Serializable
data class SeatPlayer(val seat: String, val accountId: String, val name: String, val team: Team)

@Serializable
enum class RoomStatus {
    @SerialName("open")
    OPEN,

    @SerialName("started")
    STARTED,

    @SerialName("closed")
    CLOSED,
}

@Serializable
data class RoomSeat(
    val team: Team,
    val index: Int,
    val accountId: String? = null,
    val name: String? = null,
    val heroId: String? = null,
    val ready: Boolean = false,
)

@Serializable
data class RoomView(
    val code: String,
    val hostAccountId: String,
    val mapId: String,
    val length: GameLength,
    val teamSize: Int,
    val ranked: Boolean,
    val seats: List<RoomSeat>,
    val status: RoomStatus,
    val expiresAt: Long,
    val gameId: String? = null,
)

// REST bodies ---------------------------------------------------------------------------------

@Serializable
data class HealthInfo(
    val ok: Boolean,
    val protocol: Int,
    val rulesBuild: String,
    val status: String,
    /** Running games: what a deploy waits for. */
    val rooms: Int,
    val maxRooms: Int,
    /** Rooms still waiting for players; a drain doesn't wait for them, and they survive a restart. */
    val openRooms: Int = 0,
)

/** Every REST error: `error` is stable for code, `message` is for people. */
@Serializable
data class ApiError(val error: String, val message: String? = null)

@Serializable
data class CreateAccount(val name: String)

@Serializable
data class LinkAccount(val recoveryCode: String)

/** The recovery code is shown once: the server keeps only its hash. */
@Serializable
data class AccountCreated(val accountId: String, val token: String, val recoveryCode: String)

@Serializable
data class Me(val accountId: String, val name: String)

@Serializable
data class Rename(val name: String)

@Serializable
data class CreateRoom(val mapId: String, val length: GameLength, val teamSize: Int, val ranked: Boolean)

@Serializable
data class RoomCreated(val code: String)

@Serializable
data class MyGames(val games: List<MyGame>)

/**
 * A game to go back to. [asked]: the seats with a question now (the table's `asked`, public to every seat), so
 * the list can say "Your turn" or "Waiting for Bob"; [over]: it ended before the person heard. Both are empty
 * and false from a server before them.
 */
@Serializable
data class MyGame(
    val gameId: String,
    val roomCode: String,
    val mapId: String,
    val seats: List<SeatPlayer>,
    val asked: List<String> = emptyList(),
    val over: Boolean = false,
)

// Static files on GitHub Pages ------------------------------------------------------------------

/** `network.json`: where live games are, read before connecting; the built-in address is the fallback. */
@Serializable
data class NetworkInfo(
    val schema: Int,
    val minProtocol: Int,
    val games: List<GameHost>,
) {
    companion object {
        const val SCHEMA: Int = 1
    }
}

/** [status] is `open` or `closed`, a person's choice and not a health check: a dead host just fails. */
@Serializable
data class GameHost(val origin: String, val status: String, val note: String? = null)

/** `leaderboard.json`, or `GET /v1/leaderboard`. Derived from the match log, never stored as truth. */
@Serializable
data class LeaderboardSnapshot(
    val schema: Int,
    val generatedAt: Long,
    /**
     * Hash of the match log it was derived from. A rename or a deleted account changes the board and not
     * the log, so "is this board new?" compares the rows too: a deleted name must leave the public file.
     */
    val logSha256: String,
    val matches: Int,
    val eloConfig: Int,
    val players: List<LeaderRow>,
    val heroes: List<LeaderRow>,
) {
    companion object {
        const val SCHEMA: Int = 1
    }
}

/** A player's row or a hero's: [id] is the account id or the hero id. */
@Serializable
data class LeaderRow(val id: String, val name: String, val rating: Int, val games: Int, val wins: Int)

/** Addresses, relative to a host's base address (docs/ONLINE.md "Routes"). */
object OnlineRoutes {
    const val HEALTH = "/v1/health"
    const val ACCOUNTS = "/v1/accounts"
    const val ACCOUNTS_LINK = "/v1/accounts/link"
    const val ME = "/v1/me"
    const val MY_GAMES = "/v1/me/games"
    const val ROOMS = "/v1/rooms"
    const val PLAY = "/v1/play"
    const val LEADERBOARD = "/v1/leaderboard"

    /** The fragment of an invite link (`.../goa-companion/#play=<code>`). */
    const val INVITE_FRAGMENT = "play="

    const val PAGES_ORIGIN = "https://quintond.github.io"
    const val NETWORK_FILE = "$PAGES_ORIGIN/goa-leaderboards/network.json"
    const val LEADERBOARD_FILE = "$PAGES_ORIGIN/goa-leaderboards/leaderboard.json"
    const val INVITE_BASE = "$PAGES_ORIGIN/goa-companion/#$INVITE_FRAGMENT"

    fun join(code: String): String = "$ROOMS/$code/join"

    fun leave(code: String): String = "$ROOMS/$code/leave"
}
