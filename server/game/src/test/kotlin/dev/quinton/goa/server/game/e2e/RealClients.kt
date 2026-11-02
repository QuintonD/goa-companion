package dev.quinton.goa.server.game.e2e

import dev.quinton.goa.core.online.AccountCreated
import dev.quinton.goa.core.online.ClientMessage
import dev.quinton.goa.core.online.CreateAccount
import dev.quinton.goa.core.online.CreateRoom
import dev.quinton.goa.core.online.LinkAccount
import dev.quinton.goa.core.online.Me
import dev.quinton.goa.core.online.MyGames
import dev.quinton.goa.core.online.OnlineProtocol
import dev.quinton.goa.core.online.OnlineRoutes
import dev.quinton.goa.core.online.Rename
import dev.quinton.goa.core.online.RoomCreated
import dev.quinton.goa.core.online.RoomView
import dev.quinton.goa.core.online.ServerMessage
import dev.quinton.goa.feature.board.play.online.GameConnection
import dev.quinton.goa.feature.board.play.online.GameConnector
import dev.quinton.goa.feature.board.play.online.OnlineGateway
import dev.quinton.goa.feature.board.play.online.OnlineIdentity
import dev.quinton.goa.feature.board.play.online.OnlineIdentityStore
import dev.quinton.goa.feature.board.play.online.OnlineResult
import dev.quinton.goa.feature.board.play.online.decodeServerFrame
import dev.quinton.goa.feature.board.play.online.encodeClientFrame
import dev.quinton.goa.feature.board.play.online.onlineFailure
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.WebSocket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.future.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException

/** What the JDK's clients share: one per test JVM, as a phone has one network stack. */
internal val jdkHttp: HttpClient = HttpClient.newBuilder()
    .executor(Executors.newCachedThreadPool { task -> Thread(task, "e2e-http").apply { isDaemon = true } })
    .build()

/** One frame as it came off the socket, before anything was done with it. */
internal class WireFrame(val connection: Int, val raw: String, val message: ServerMessage)

/** A socket a phone holds: the JDK's client, decoding with the app's own [decodeServerFrame]. */
internal class JdkConnection(private val number: Int, private val wire: MutableList<WireFrame>) :
    GameConnection,
    WebSocket.Listener {
    private val frames = Channel<ServerMessage>(Channel.UNLIMITED)
    private val partial = StringBuilder()
    private val sending = Mutex()
    lateinit var socket: WebSocket

    override val incoming: Flow<ServerMessage> = frames.receiveAsFlow()

    override fun onText(socket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
        partial.append(data)
        if (last) {
            val raw = partial.toString()
            partial.clear()
            try {
                val message = decodeServerFrame(raw)
                wire += WireFrame(number, raw, message)
                frames.trySend(message)
            } catch (failure: SerializationException) {
                frames.close(failure)
            }
        }
        socket.request(1)
        return null
    }

    override fun onClose(socket: WebSocket, statusCode: Int, reason: String): CompletionStage<*>? {
        frames.close()
        return null
    }

    override fun onError(socket: WebSocket, error: Throwable) {
        frames.close()
    }

    /** The JDK's client takes one send at a time; a browser and OkHttp queue them. */
    override suspend fun send(message: ClientMessage) {
        sending.withLock { socket.sendText(encodeClientFrame(message), true).await() }
    }

    override fun close() {
        frames.close()
        if (!socket.isOutputClosed) {
            runCatching { socket.sendClose(WebSocket.NORMAL_CLOSURE, "") }
        }
        CompletableFuture.delayedExecutor(CLOSE_WAIT_MILLIS, TimeUnit.MILLISECONDS).execute { socket.abort() }
    }

    /** The network went away: nothing is said to the server, the connection just stops. */
    fun kill() {
        socket.abort()
    }

    private companion object {
        const val CLOSE_WAIT_MILLIS = 1_000L
    }
}

/**
 * The platform's half of the seam on the real wire. [offline] is a phone with no network: nothing opens.
 * Everything received, on every socket, is kept in [wire] for the tests to read.
 */
internal class JdkConnector(private val base: () -> String) : GameConnector {
    val wire: MutableList<WireFrame> = CopyOnWriteArrayList()
    val hellos: MutableList<ClientMessage.Hello> = CopyOnWriteArrayList()
    private val connections = CopyOnWriteArrayList<JdkConnection>()

    @Volatile
    var offline = false

    override suspend fun open(hello: ClientMessage.Hello): GameConnection {
        if (offline) throw IOException("No network")
        val connection = JdkConnection(connections.size, wire)
        val url = URI(base().replaceFirst("http", "ws") + OnlineRoutes.PLAY)
        connection.socket = jdkHttp.newWebSocketBuilder().buildAsync(url, connection).await()
        connections += connection
        hellos += hello
        connection.send(hello)
        return connection
    }

    /** Every socket dies at once, the way a phone going into a tunnel looks to the server. */
    fun killSockets() = connections.forEach { it.kill() }
}

internal class MemoryIdentities(@Volatile var identity: OnlineIdentity? = null) : OnlineIdentityStore {
    override suspend fun load(): OnlineIdentity? = identity

    override suspend fun save(identity: OnlineIdentity) {
        this.identity = identity
    }

    override suspend fun clear() {
        identity = null
    }
}

/** The platform's half of REST, on the real wire, as the seam's documentation says a platform does it. */
internal class JdkGateway(private val base: () -> String, private val identities: OnlineIdentityStore) : OnlineGateway {
    private suspend fun <T> call(
        method: String,
        path: String,
        body: String?,
        read: (String) -> T,
    ): OnlineResult<T> {
        val request = HttpRequest.newBuilder(URI(base() + path))
            .method(
                method,
                body?.let { HttpRequest.BodyPublishers.ofString(it) } ?: HttpRequest.BodyPublishers.noBody(),
            )
            .header("Content-Type", "application/json")
        identities.load()?.let { request.header("Authorization", "Bearer ${it.token}") }
        val response = try {
            jdkHttp.sendAsync(request.build(), HttpResponse.BodyHandlers.ofString()).await()
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Exception) {
            return OnlineResult.Failed()
        }
        val retryAfter = response.headers().firstValue("Retry-After").map { it.toIntOrNull() }.orElse(null)
        return if (response.statusCode() in OK_STATUSES) {
            OnlineResult.Ok(read(response.body()))
        } else {
            onlineFailure(response.body(), retryAfter)
        }
    }

    private fun <T> KSerializer<T>.text(value: T): String = OnlineProtocol.json.encodeToString(this, value)

    private fun <T> KSerializer<T>.reader(): (String) -> T = { OnlineProtocol.json.decodeFromString(this, it) }

    override suspend fun createAccount(name: String): OnlineResult<AccountCreated> = call(
        "POST",
        OnlineRoutes.ACCOUNTS,
        CreateAccount.serializer().text(CreateAccount(name)),
        AccountCreated.serializer().reader(),
    )

    override suspend fun link(recoveryCode: String): OnlineResult<AccountCreated> = call(
        "POST",
        OnlineRoutes.ACCOUNTS_LINK,
        LinkAccount.serializer().text(LinkAccount(recoveryCode)),
        AccountCreated.serializer().reader(),
    )

    override suspend fun me(): OnlineResult<Me> = call("GET", OnlineRoutes.ME, null, Me.serializer().reader())

    override suspend fun rename(name: String): OnlineResult<Me> =
        call("PATCH", OnlineRoutes.ME, Rename.serializer().text(Rename(name)), Me.serializer().reader())

    override suspend fun deleteMe(): OnlineResult<Unit> = call("DELETE", OnlineRoutes.ME, null) { }

    override suspend fun createRoom(room: CreateRoom): OnlineResult<RoomCreated> =
        call("POST", OnlineRoutes.ROOMS, CreateRoom.serializer().text(room), RoomCreated.serializer().reader())

    override suspend fun join(code: String): OnlineResult<RoomView> =
        call("POST", OnlineRoutes.join(code), "", RoomView.serializer().reader())

    override suspend fun leave(code: String): OnlineResult<RoomView> =
        call("POST", OnlineRoutes.leave(code), "", RoomView.serializer().reader())

    override suspend fun myGames(): OnlineResult<MyGames> =
        call("GET", OnlineRoutes.MY_GAMES, null, MyGames.serializer().reader())

    private companion object {
        val OK_STATUSES = 200..299
    }
}
