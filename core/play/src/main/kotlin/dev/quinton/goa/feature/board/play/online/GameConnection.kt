package dev.quinton.goa.feature.board.play.online

import dev.quinton.goa.core.online.ClientMessage
import dev.quinton.goa.core.online.OnlineProtocol
import dev.quinton.goa.core.online.Refusal
import dev.quinton.goa.core.online.ServerMessage
import dev.quinton.goa.feature.board.play.game.GameAction
import dev.quinton.goa.feature.board.play.game.GameUiState
import dev.quinton.goa.feature.board.play.table.TableTap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/*
 * The seam of online games (ADR-056): both game screens already draw from GameUiState and answer with
 * GameAction and TableTap, so an online game presents those and nothing below them changes.
 * A remote GameSession is not an option: the session API is synchronous, and fork, save and undo need
 * a whole game, which a phone never holds online.
 */

/**
 * One open socket. [incoming] ends when it closes; platform code owns the transport. A frame this build
 * can't read must fail the flow with the SerializationException [decodeServerFrame] throws: the driver
 * takes it to mean a newer server, and asks for an update.
 */
interface GameConnection {
    val incoming: Flow<ServerMessage>

    suspend fun send(message: ClientMessage)

    fun close()
}

/** Opens a socket to the game host and sends [hello] as its first frame. */
fun interface GameConnector {
    suspend fun open(hello: ClientMessage.Hello): GameConnection
}

fun decodeServerFrame(text: String): ServerMessage =
    OnlineProtocol.json.decodeFromString(ServerMessage.serializer(), text)

fun encodeClientFrame(message: ClientMessage): String =
    OnlineProtocol.json.encodeToString(ClientMessage.serializer(), message)

/** A person's account on this device (docs/ONLINE.md). The token never goes in a URL or a log. */
data class OnlineIdentity(val accountId: String, val token: String, val name: String)

interface OnlineIdentityStore {
    suspend fun load(): OnlineIdentity?

    suspend fun save(identity: OnlineIdentity)

    suspend fun clear()
}

sealed interface OnlineState {
    /** Before the first table. [note] says why it is taking long, once a try has failed. */
    data class Connecting(val note: String? = null) : OnlineState

    /** The connection dropped: the last table stays on screen while the driver reconnects. */
    data class Reconnecting(val game: GameUiState) : OnlineState

    /** `OnlineWords.refusal` says it. With [retryAfterSeconds] the driver tries again by itself. */
    data class Refused(val reason: Refusal, val retryAfterSeconds: Int? = null) : OnlineState

    data class InGame(val game: GameUiState) : OnlineState
}

/** Drives one screen's online game. Actions that need a whole game (take back, coach, pace) do nothing. */
interface OnlineGame {
    val state: StateFlow<OnlineState>

    fun onAction(action: GameAction)

    fun tap(tap: TableTap): Boolean

    /** The app came back to the foreground or the network returned: don't wait out the backoff. */
    fun resumeNow()

    /** Gives up the seat for good: the computer plays it to the end, and the team still wins or loses. */
    fun leave()
}
