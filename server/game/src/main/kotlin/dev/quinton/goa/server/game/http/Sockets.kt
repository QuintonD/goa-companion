package dev.quinton.goa.server.game.http

import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.online.ClientMessage
import dev.quinton.goa.core.online.OnlineProtocol
import dev.quinton.goa.core.online.Refusal
import dev.quinton.goa.core.online.RoomStatus
import dev.quinton.goa.core.online.RoomView
import dev.quinton.goa.core.online.ServerMessage
import dev.quinton.goa.server.game.lobby.LobbyRefused
import dev.quinton.goa.server.game.room.GameRooms
import dev.quinton.goa.server.game.room.SeatLink
import dev.quinton.goa.server.game.room.SeatSink
import dev.quinton.goa.server.game.store.AccountRow
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("goa-game")

internal const val MAX_FRAME_BYTES = 4 * 1024
private const val MAX_RESUMED_GAMES = 16
internal const val MAX_WATCHED_ROOMS = 8
internal const val MAX_SOCKETS_PER_ACCOUNT = 4
private const val SEND_TIMEOUT_MILLIS = 30_000L
private const val CLOSE_TIMEOUT_MILLIS = 1_000L
private val CLIENT_LABEL = Regex("[A-Za-z0-9._/-]{1,40}")

/** One person's socket: its queue, and the games it is linked to. */
internal class PlayerSocket(
    val accountId: String,
    private val rooms: GameRooms,
    private val scope: CoroutineScope,
    val outbound: Outbound = Outbound(),
) {
    private class SentAnswer(val ticket: Long, val choice: Choice, var ack: ServerMessage.Ack? = null)

    private val guard = Any()
    private val links = HashMap<String, SeatLink?>()
    private val answers = HashMap<String, SentAnswer>()

    /** Rooms this socket watches, oldest first. Kept by [SocketHub], under its lock. */
    val watching = LinkedHashSet<String>()
    private var closed = false

    private val sink = SeatSink { message ->
        if (message is ServerMessage.Ack) {
            synchronized(guard) { answers[message.gameId]?.takeIf { it.ticket == message.ticket }?.ack = message }
        }
        outbound.offer(message)
    }

    fun send(message: ServerMessage) = outbound.offer(message)

    /** Links this socket to a game once; the room sends it the game start and the table. */
    @Suppress("TooGenericExceptionCaught") // A room's failure must not end the person's other games.
    suspend fun attach(gameId: String, haveEvents: Int) {
        synchronized(guard) {
            if (closed || gameId in links) return
            links[gameId] = null
        }
        val link = try {
            rooms.connect(gameId, accountId, haveEvents, sink)
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Exception) {
            log.warn("connect failed: {}", failure.javaClass.simpleName)
            null
        }
        val late = synchronized(guard) {
            when {
                closed -> true

                link == null -> {
                    links.remove(gameId)
                    false
                }

                else -> {
                    links[gameId] = link
                    false
                }
            }
        }
        if (late) link?.close()
    }

    fun attachLater(gameId: String) {
        scope.launch { attach(gameId, 0) }
    }

    /** True from the moment the room is asked: its first table goes out before `connect` returns. */
    fun isLinked(gameId: String): Boolean = synchronized(guard) { gameId in links }

    /**
     * Whether this same answer already went to the room over this socket. A repeat gets the answer's ack
     * again, or nothing yet if the room hasn't answered, and the room is not asked twice.
     */
    fun repeatOf(answer: ClientMessage.Answer): Repeat = synchronized(guard) {
        val sent = answers[answer.gameId]
        if (sent != null && sent.ticket == answer.ticket && sent.choice == answer.choice) {
            Repeat.Of(sent.ack)
        } else {
            answers[answer.gameId] = SentAnswer(answer.ticket, answer.choice)
            Repeat.New
        }
    }

    fun forgetAnswer(gameId: String) {
        synchronized(guard) { answers.remove(gameId) }
    }

    /** Tells every room this person left the table. Their seats stay theirs. */
    fun close() {
        val open = synchronized(guard) {
            closed = true
            links.values.filterNotNull().also { links.clear() }
        }
        open.forEach { it.close() }
        outbound.shut()
    }

    sealed interface Repeat {
        data object New : Repeat

        data class Of(val ack: ServerMessage.Ack?) : Repeat
    }
}

/** Who is connected, and who watches which room. */
internal class SocketHub {
    private val guard = Any()
    private val sockets = HashMap<String, LinkedHashSet<PlayerSocket>>()
    private val watchers = HashMap<String, LinkedHashSet<PlayerSocket>>()

    /** A phone that lost its network leaves a dead socket behind: the newest wins past the limit. */
    fun add(socket: PlayerSocket) {
        val evicted = synchronized(guard) {
            val own = sockets.getOrPut(socket.accountId) { LinkedHashSet() }
            own += socket
            if (own.size > MAX_SOCKETS_PER_ACCOUNT) own.first().also { own.remove(it) } else null
        }
        evicted?.outbound?.shut()
    }

    fun remove(socket: PlayerSocket) {
        synchronized(guard) {
            sockets[socket.accountId]?.let { own ->
                own -= socket
                if (own.isEmpty()) sockets.remove(socket.accountId)
            }
            watchers.values.forEach { it -= socket }
            watchers.values.removeIf { it.isEmpty() }
        }
    }

    /** A socket keeps its most recent rooms: watching is free, so it can't be unlimited. */
    fun watch(socket: PlayerSocket, code: String) {
        synchronized(guard) {
            socket.watching -= code
            socket.watching += code
            if (socket.watching.size > MAX_WATCHED_ROOMS) {
                val oldest = socket.watching.first()
                socket.watching -= oldest
                watchers[oldest]?.remove(socket)
            }
            watchers.getOrPut(code) { LinkedHashSet() } += socket
        }
    }

    /** Sends the room to its watchers and to everyone seated; a started room links the seated to the game. */
    fun roomChanged(view: RoomView) {
        val (seated, audience) = synchronized(guard) {
            val seated = view.seats.mapNotNull { it.accountId }.flatMap { sockets[it].orEmpty() }
            val audience = (watchers[view.code].orEmpty() + seated).toList()
            if (view.status == RoomStatus.CLOSED) watchers.remove(view.code)
            seated to audience
        }
        audience.forEach { it.send(ServerMessage.Lobby(view)) }
        val gameId = view.gameId
        if (view.status == RoomStatus.STARTED && gameId != null) seated.forEach { it.attachLater(gameId) }
    }

    fun disconnect(accountId: String) {
        val own = synchronized(guard) { sockets[accountId].orEmpty().toList() }
        own.forEach { it.outbound.shut() }
    }
}

/** Sends [outbound] until it shuts. Lives apart from the socket so a test can stand in for a slow one. */
internal suspend fun pump(outbound: Outbound, send: suspend (ServerMessage) -> Unit) {
    while (true) {
        send(outbound.next() ?: return)
    }
}

/** `GET /v1/play`. The token comes in the first frame only: never from a header or a URL. */
internal suspend fun DefaultWebSocketServerSession.play(services: GameServices) {
    val hello = withTimeoutOrNull(services.config.helloTimeoutMillis) { receiveHello() }
        ?: return close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "hello"))
    val account = admit(services, hello) ?: return
    val socket = PlayerSocket(account.id, services.rooms, this)
    log.info("socket open: {}", CLIENT_LABEL.matchEntire(hello.client)?.value ?: "other")
    val writer = launch {
        pump(socket.outbound) { message ->
            val sent = withTimeoutOrNull(SEND_TIMEOUT_MILLIS) { sendFrame(message) }
            if (sent == null) socket.outbound.shut()
        }
        withTimeoutOrNull(CLOSE_TIMEOUT_MILLIS) { close(CloseReason(CloseReason.Codes.TRY_AGAIN_LATER, "behind")) }
        this@play.cancel()
    }
    try {
        val active = services.rooms.gamesOf(account.id)
        socket.send(
            ServerMessage.Welcome(OnlineProtocol.VERSION, account.id, account.name, services.clock(), active),
        )
        services.hub.add(socket)
        // Asked again now it can hear of starts: a game that began since the Welcome is not missed.
        val have = hello.resume.associate { it.gameId to it.haveEvents.coerceAtLeast(0) }
        (services.rooms.gamesOf(account.id) + have.keys).distinct().take(MAX_RESUMED_GAMES).forEach {
            socket.attach(it, have[it] ?: 0)
        }
        serve(services, socket)
    } finally {
        services.hub.remove(socket)
        socket.close()
        writer.cancel()
        log.info("socket closed")
    }
}

private suspend fun DefaultWebSocketServerSession.receiveHello(): ClientMessage.Hello? {
    val frame = incoming.receiveCatching().getOrNull() as? Frame.Text ?: return null
    if (frame.data.size > MAX_FRAME_BYTES) return null
    return decode(frame) as? ClientMessage.Hello
}

/** The account behind a Hello; otherwise says why, closes, and returns null. */
private suspend fun DefaultWebSocketServerSession.admit(
    services: GameServices,
    hello: ClientMessage.Hello,
): AccountRow? {
    if (hello.protocol < OnlineProtocol.VERSION || hello.catalogVersion < services.catalogVersion) {
        refuse(ServerMessage.Refused(Refusal.UPDATE_APP))
        return null
    }
    val account = services.accounts.authenticate(hello.token)
    if (account == null) refuse(ServerMessage.Refused(Refusal.BAD_TOKEN))
    return account
}

private suspend fun DefaultWebSocketServerSession.refuse(refused: ServerMessage.Refused) {
    sendFrame(refused)
    close(CloseReason(CloseReason.Codes.NORMAL, refused.reason.name.lowercase()))
}

private suspend fun DefaultWebSocketServerSession.sendFrame(message: ServerMessage) {
    send(Frame.Text(OnlineProtocol.json.encodeToString(ServerMessage.serializer(), message)))
}

private suspend fun DefaultWebSocketServerSession.serve(services: GameServices, socket: PlayerSocket) {
    for (frame in incoming) {
        problemWith(frame)?.let { return close(it) }
        val message = decode(frame as Frame.Text)
            ?: return close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "unreadable"))
        val wait = services.socketFrames.acquire(socket.accountId)
        if (wait != null) {
            socket.send(ServerMessage.Refused(Refusal.RATE_LIMITED, wait.toInt()))
        } else if (!handled(services, socket, message)) {
            return close(CloseReason(CloseReason.Codes.INTERNAL_ERROR, "failed"))
        }
    }
}

private fun problemWith(frame: Frame): CloseReason? = when {
    frame !is Frame.Text -> CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "text")
    frame.data.size > MAX_FRAME_BYTES -> CloseReason(CloseReason.Codes.TOO_BIG, "size")
    else -> null
}

/** Null if it can't be read. The failure's own text quotes the frame, which may hold a choice: its class only. */
private fun decode(frame: Frame.Text): ClientMessage? = try {
    OnlineProtocol.json.decodeFromString(ClientMessage.serializer(), frame.readText())
} catch (failure: IllegalArgumentException) {
    log.warn("unreadable frame: {}", failure.javaClass.simpleName)
    null
}

/** False if the frame broke something: a room's or a store's failure must end one socket, not the server. */
@Suppress("TooGenericExceptionCaught")
private suspend fun handled(services: GameServices, socket: PlayerSocket, message: ClientMessage): Boolean = try {
    handle(services, socket, message)
    true
} catch (failure: CancellationException) {
    throw failure
} catch (failure: Exception) {
    log.warn("frame failed: {}", failure.javaClass.simpleName)
    false
}

private suspend fun handle(services: GameServices, socket: PlayerSocket, message: ClientMessage) {
    when (message) {
        is ClientMessage.Hello -> Unit

        is ClientMessage.Ping -> socket.send(ServerMessage.Pong(message.nonce, services.clock()))

        is ClientMessage.WatchRoom -> lobbyFrame(services, socket, message.code) {
            services.lobby.view(message.code).also { services.hub.watch(socket, it.code) }
        }

        is ClientMessage.PickSeat -> lobbyFrame(services, socket, message.code) {
            services.lobby.pickSeat(socket.accountId, message.code, message.team, message.index, message.heroId)
                .also { services.hub.watch(socket, it.code) }
        }

        is ClientMessage.Ready -> lobbyFrame(services, socket, message.code) {
            services.lobby.ready(socket.accountId, message.code, message.ready)
        }

        is ClientMessage.Start -> lobbyFrame(services, socket, message.code) {
            services.lobby.start(socket.accountId, message.code)
        }

        is ClientMessage.Answer -> answer(services, socket, message)

        is ClientMessage.AskReview ->
            if (socket.isLinked(message.gameId)) {
                services.rooms.review(message.gameId, socket.accountId, message.rewind)
            }

        is ClientMessage.Leave ->
            if (socket.isLinked(message.gameId)) services.rooms.leave(message.gameId, socket.accountId)
    }
}

/** Sends the room to this socket, or why not along with how the room really stands. */
private suspend fun lobbyFrame(
    services: GameServices,
    socket: PlayerSocket,
    code: String,
    action: suspend () -> RoomView,
) {
    try {
        socket.send(ServerMessage.Lobby(action()))
    } catch (refused: LobbyRefused) {
        socket.send(ServerMessage.Refused(refused.error.refusal, refused.error.retryAfterSeconds))
        try {
            socket.send(ServerMessage.Lobby(services.lobby.view(code)))
        } catch (_: LobbyRefused) {
            return
        }
    }
}

@Suppress("TooGenericExceptionCaught") // The person hears of a failure as an ack, and can answer again.
private suspend fun answer(services: GameServices, socket: PlayerSocket, answer: ClientMessage.Answer) {
    if (!socket.isLinked(answer.gameId)) {
        socket.send(ServerMessage.Ack(answer.gameId, answer.ticket, accepted = false, reason = "not_in_game"))
        return
    }
    when (val repeat = socket.repeatOf(answer)) {
        is PlayerSocket.Repeat.Of -> repeat.ack?.let(socket::send)

        PlayerSocket.Repeat.New -> try {
            services.rooms.answer(answer.gameId, socket.accountId, answer.ticket, answer.choice)
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Exception) {
            log.warn("answer failed: {}", failure.javaClass.simpleName)
            socket.forgetAnswer(answer.gameId)
            socket.send(ServerMessage.Ack(answer.gameId, answer.ticket, accepted = false, reason = "server_error"))
        }
    }
}
