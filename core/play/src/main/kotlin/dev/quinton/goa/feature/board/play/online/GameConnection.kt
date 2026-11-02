package dev.quinton.goa.feature.board.play.online

import dev.quinton.goa.core.online.ClientMessage
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

/** One open socket. [incoming] ends when it closes; platform code owns the transport. */
interface GameConnection {
    val incoming: Flow<ServerMessage>

    suspend fun send(message: ClientMessage)

    fun close()
}

/** Opens a socket to the game host and sends [hello] as its first frame. */
fun interface GameConnector {
    suspend fun open(hello: ClientMessage.Hello): GameConnection
}

/** A person's account on this device (docs/ONLINE.md). The token never goes in a URL or a log. */
data class OnlineIdentity(val accountId: String, val token: String, val name: String)

interface OnlineIdentityStore {
    suspend fun load(): OnlineIdentity?

    suspend fun save(identity: OnlineIdentity)

    suspend fun clear()
}

sealed interface OnlineState {
    data object Connecting : OnlineState

    /** The connection dropped: the last table stays on screen while the driver reconnects. */
    data class Reconnecting(val game: GameUiState) : OnlineState

    data class Refused(val reason: Refusal, val retryAfterSeconds: Int? = null) : OnlineState

    data class InGame(val game: GameUiState) : OnlineState
}

/** Drives one screen's online game. Actions that need a whole game (take back, coach, pace) do nothing. */
interface OnlineGame {
    val state: StateFlow<OnlineState>

    fun onAction(action: GameAction)

    fun tap(tap: TableTap): Boolean
}
