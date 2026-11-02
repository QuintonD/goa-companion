package dev.quinton.goa.feature.board.play.online

import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.online.ClientMessage
import dev.quinton.goa.core.online.OnlineProtocol
import dev.quinton.goa.core.online.Refusal
import dev.quinton.goa.core.online.RoomSeat
import dev.quinton.goa.core.online.RoomStatus
import dev.quinton.goa.core.online.RoomView
import dev.quinton.goa.core.online.ServerMessage
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException

/** A room as the person sees it. */
data class LobbyUi(
    val code: String,
    val inviteLink: String = inviteLink(code),
    val room: RoomView? = null,
    /** The person's account id. */
    val you: String? = null,
    /** The heroes that can be played online, in the order to offer them. */
    val playable: List<String> = emptyList(),
    /** The socket is up: seats and readiness are live. */
    val connected: Boolean = false,
    /** What went wrong, in words. */
    val message: String? = null,
    /** The game has started: the screen opens it with an [OnlineGameDriver]. */
    val gameId: String? = null,
    /** The person has left: the screen goes back. */
    val left: Boolean = false,
) {
    val seats: List<RoomSeat> get() = room?.seats.orEmpty()

    val yourSeat: RoomSeat? get() = seats.firstOrNull { it.accountId != null && it.accountId == you }

    val isHost: Boolean get() = room != null && room.hostAccountId == you

    /** Playable and not picked by anyone (your own pick stays yours until you change it). */
    val heroes: List<String> get() = playable.filter { hero -> seats.none { it.heroId == hero } }

    val readyCount: Int get() = seats.count { it.ready }

    val canReady: Boolean get() = connected && yourSeat?.heroId != null

    /** Every seat taken, with a hero, and ready. */
    val everyoneReady: Boolean
        get() = seats.isNotEmpty() && seats.all { it.accountId != null && it.heroId != null && it.ready }

    val canStart: Boolean get() = connected && isHost && everyoneReady && room?.status == RoomStatus.OPEN
}

/**
 * One room's lobby (ADR-056): REST to join, then a socket for the live seats. Seats and heroes are the
 * server's to give: a pick shows once the room says so. [scope] is the screen's and runs on one thread.
 * Pass [initial] when the home presenter has already joined.
 */
class OnlineLobbyPresenter(
    code: String,
    private val gateway: OnlineGateway,
    private val connector: GameConnector,
    private val identities: OnlineIdentityStore,
    playableHeroes: List<String>,
    private val catalogVersion: suspend () -> Int,
    private val scope: CoroutineScope,
    private val client: String,
    initial: RoomView? = null,
    private val random: Random = Random.Default,
) {
    private val mutableState = MutableStateFlow(LobbyUi(code = code, room = initial, playable = playableHeroes))
    val state: StateFlow<LobbyUi> = mutableState.asStateFlow()

    private var loop: Job? = null
    private var live: GameConnection? = null
    private var attempts = 0

    /** Why no socket was opened, when `network.json` said not to. */
    private var hostNote: String? = null

    /** No use going on: the app is too old, the account is gone, the game has begun, the person left. */
    private var stopped = false

    fun start() {
        if (loop?.isActive == true) return
        stopped = false
        loop = scope.launch { run() }
    }

    private suspend fun run() {
        val identity = identities.load() ?: return stop(OnlineWords.refusal(Refusal.BAD_TOKEN))
        mutableState.update { it.copy(you = identity.accountId) }
        if (state.value.room == null && !join()) return
        adopt(state.value.room ?: return)
        while (!stopped) {
            watch(identity)
            mutableState.update { it.copy(connected = false) }
            if (stopped) return
            say(hostNote ?: OnlineWords.UNREACHABLE)
            delay(reconnectDelay(attempts++, random))
        }
    }

    private suspend fun join(): Boolean = when (val joined = gateway.join(state.value.code)) {
        is OnlineResult.Ok -> {
            mutableState.update { it.copy(room = joined.value, message = null) }
            true
        }

        // No room to show: nothing else on this screen works, so it is the last word. A network failure
        // can be tried again with start().
        else -> {
            say(joined.words())
            false
        }
    }

    private suspend fun watch(identity: OnlineIdentity) {
        val hello = ClientMessage.Hello(OnlineProtocol.VERSION, identity.token, client, catalogVersion())
        val opened = runCatching { connector.open(hello) }
        hostNote = null
        when (val failure = opened.exceptionOrNull()) {
            is CancellationException -> throw failure

            is HostUnavailable -> if (failure.tooOld) {
                stop(OnlineWords.refusal(Refusal.UPDATE_APP))
            } else {
                hostNote = failure.note ?: OnlineWords.HOST_CLOSED
            }

            else -> Unit
        }
        val connection = opened.getOrNull() ?: return
        try {
            live = connection
            val failure = runCatching {
                connection.send(ClientMessage.WatchRoom(state.value.code))
                mutableState.update { it.copy(connected = true) }
                connection.incoming.takeWhile(::take).collect()
            }.exceptionOrNull()
            when (failure) {
                is CancellationException -> throw failure

                // A frame this build can't read: a newer server, never a crash.
                is SerializationException -> stop(OnlineWords.refusal(Refusal.UPDATE_APP))

                // Anything else is a dropped socket: the loop reconnects.
                else -> Unit
            }
        } finally {
            live = null
            connection.close()
        }
    }

    /** Takes in one frame; false ends the connection. */
    private fun take(frame: ServerMessage): Boolean = when (frame) {
        is ServerMessage.Lobby -> {
            attempts = 0
            if (frame.room.code == state.value.code) adopt(frame.room)
            !stopped
        }

        is ServerMessage.GameStart -> {
            opened(frame.gameId)
            false
        }

        is ServerMessage.Refused -> {
            if (frame.reason == Refusal.UPDATE_APP || frame.reason == Refusal.BAD_TOKEN) {
                stop(OnlineWords.refusal(frame.reason, frame.retryAfterSeconds))
                false
            } else {
                // A seat or hero just taken, the room gone: said, and the room carries on being shown.
                say(OnlineWords.refusal(frame.reason, frame.retryAfterSeconds))
                true
            }
        }

        else -> true
    }

    private fun adopt(room: RoomView) {
        // A refusal stays until something replaces it; only "can't be reached" is over now.
        mutableState.update { it.copy(room = room, message = it.message.takeIf { m -> m != OnlineWords.UNREACHABLE }) }
        val gameId = room.gameId
        when {
            room.status == RoomStatus.STARTED && gameId != null -> opened(gameId)
            room.status == RoomStatus.CLOSED -> stop(OnlineWords.ROOM_CLOSED)
        }
    }

    private fun opened(gameId: String) {
        stopped = true
        mutableState.update { it.copy(gameId = gameId) }
    }

    private fun stop(words: String) {
        stopped = true
        mutableState.update { it.copy(message = words) }
    }

    // ---- Actions -----------------------------------------------------------------------------------

    /** Sits at a free seat, keeping the hero already picked. */
    fun pickSeat(team: Team, index: Int) {
        val ui = state.value
        val seat = ui.seats.firstOrNull { it.team == team && it.index == index } ?: return
        if (seat.accountId != null && seat.accountId != ui.you) return say(OnlineWords.SEAT_TAKEN)
        send(ClientMessage.PickSeat(ui.code, team, index, ui.yourSeat?.heroId))
    }

    /** Picks an unpicked hero for the person's seat; null clears the pick. */
    fun pickHero(heroId: String?) {
        val ui = state.value
        val seat = ui.yourSeat ?: return say(OnlineWords.SEAT_FIRST)
        if (heroId != null && heroId !in ui.heroes) return say(OnlineWords.HERO_TAKEN)
        send(ClientMessage.PickSeat(ui.code, seat.team, seat.index, heroId))
    }

    fun setReady(ready: Boolean) {
        val ui = state.value
        if (ui.yourSeat?.heroId == null) return say(OnlineWords.HERO_FIRST)
        send(ClientMessage.Ready(ui.code, ready))
    }

    /** The host begins, once everyone is ready; the server flips the coin (p.5 step 3). */
    fun startGame() {
        if (state.value.canStart) send(ClientMessage.Start(state.value.code))
    }

    fun leave() {
        stopped = true
        scope.launch {
            val left = gateway.leave(state.value.code)
            loop?.cancel()
            mutableState.update { it.copy(left = true, connected = false, message = left.words()) }
        }
    }

    private fun send(message: ClientMessage) {
        val connection = live ?: return say(OnlineWords.UNREACHABLE)
        scope.launch {
            val sent = runCatching { connection.send(message) }
            sent.exceptionOrNull()?.let {
                if (it is CancellationException) throw it
                say(OnlineWords.UNREACHABLE)
            }
        }
    }

    private fun say(text: String?) = mutableState.update { it.copy(message = text) }
}
