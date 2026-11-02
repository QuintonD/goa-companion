package dev.quinton.goa.server.game.http

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.GameLength
import dev.quinton.goa.core.online.AccountCreated
import dev.quinton.goa.core.online.ClientMessage
import dev.quinton.goa.core.online.CreateAccount
import dev.quinton.goa.core.online.CreateRoom
import dev.quinton.goa.core.online.Refusal
import dev.quinton.goa.core.online.RoomCreated
import dev.quinton.goa.core.online.ServerMessage
import dev.quinton.goa.server.game.FakeRooms
import dev.quinton.goa.server.game.GameServerConfig
import dev.quinton.goa.server.game.board
import dev.quinton.goa.server.game.hello
import dev.quinton.goa.server.game.json
import dev.quinton.goa.server.game.parse
import dev.quinton.goa.server.game.room.LeaderboardSource
import dev.quinton.goa.server.game.store.SqliteGameStore
import dev.quinton.goa.server.game.testEngine
import io.ktor.http.HttpStatusCode
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.WebSocket
import java.net.http.WebSocketHandshakeException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.CompletionStage
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

/**
 * The real engine and the real wire, with the JDK's client: the test host skips the frame parser, the
 * upgrade and the socket, and those are what the size limit and the Origin rule rest on.
 */
class RealSocketTest {
    private lateinit var dir: Path
    private lateinit var store: SqliteGameStore
    private lateinit var server: EmbeddedServer<*, *>
    private var port = 0
    private val http = HttpClient.newHttpClient()

    private class Listener : WebSocket.Listener {
        val messages = LinkedBlockingQueue<ServerMessage>()
        val closed = CompletableFuture<Int>()
        private val text = StringBuilder()

        override fun onText(socket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
            text.append(data)
            if (last) {
                messages += ServerMessage.serializer().parse(text.toString())
                text.clear()
            }
            socket.request(1)
            return null
        }

        override fun onClose(socket: WebSocket, statusCode: Int, reason: String): CompletionStage<*>? {
            closed.complete(statusCode)
            return null
        }

        fun next(): ServerMessage = requireNotNull(messages.poll(WAIT_SECONDS, TimeUnit.SECONDS)) { "No frame came" }
    }

    @Before
    fun start() {
        dir = Files.createTempDirectory("goa-real-socket")
        store = SqliteGameStore.open(dir)
        val config = GameServerConfig(dir, Paths.get("unused"), helloTimeoutMillis = 300)
        server = embeddedServer(CIO, port = 0, host = "127.0.0.1") {
            gameServerModule(config, store, testEngine, FakeRooms(), LeaderboardSource { board() })
        }.start(wait = false)
        port = runBlocking { server.engine.resolvedConnectors().first().port }
    }

    @After
    fun stop() {
        server.stop(0, 0)
        store.close()
        dir.toFile().deleteRecursively()
    }

    private fun post(path: String, body: String, token: String? = null): HttpResponse<String> {
        val request = HttpRequest.newBuilder(URI("http://127.0.0.1:$port$path"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
        token?.let { request.header("Authorization", "Bearer $it") }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun account(): AccountCreated {
        val response = post("/game/v1/accounts", CreateAccount.serializer().json(CreateAccount("Somebody")))
        assertThat(response.statusCode()).isEqualTo(HttpStatusCode.Created.value)
        return AccountCreated.serializer().parse(response.body())
    }

    private fun open(listener: Listener, path: String = "/game/v1/play", origin: String? = null): WebSocket {
        val builder = http.newWebSocketBuilder()
        origin?.let { builder.header("Origin", it) }
        return builder.buildAsync(URI("ws://127.0.0.1:$port$path"), listener).join()
    }

    private fun WebSocket.say(message: ClientMessage) {
        sendText(ClientMessage.serializer().json(message), true).join()
    }

    @Test
    fun aHelloIsWelcomedAndAPingAnsweredOverARealSocket() {
        val account = account()
        for (path in listOf("/v1/play", "/game/v1/play")) {
            val listener = Listener()
            val socket = open(listener, path)
            socket.say(hello(account.token))
            assertThat((listener.next() as ServerMessage.Welcome).accountId).isEqualTo(account.accountId)
            socket.say(ClientMessage.Ping(7))
            assertThat((listener.next() as ServerMessage.Pong).nonce).isEqualTo(7)
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "done").join()
        }
    }

    @Test
    fun aRoomMadeOverHttpIsWatchedOverTheSocket() {
        val host = account()
        val made = post(
            "/game/v1/rooms",
            CreateRoom.serializer().json(CreateRoom("forgotten-island", GameLength.QUICK, 1, false)),
            host.token,
        )
        val code = RoomCreated.serializer().parse(made.body()).code
        val listener = Listener()
        val socket = open(listener)
        socket.say(hello(host.token))
        listener.next()
        socket.say(ClientMessage.WatchRoom(code))
        assertThat((listener.next() as ServerMessage.Lobby).room.code).isEqualTo(code)
        socket.sendClose(WebSocket.NORMAL_CLOSURE, "done").join()
    }

    @Test
    fun aBadTokenIsRefusedThenClosed() {
        val listener = Listener()
        val socket = open(listener)
        socket.say(hello("not-the-token-of-anyone"))
        assertThat((listener.next() as ServerMessage.Refused).reason).isEqualTo(Refusal.BAD_TOKEN)
        assertThat(listener.closed.get(WAIT_SECONDS, TimeUnit.SECONDS)).isEqualTo(NORMAL)
        assertThat(socket.isInputClosed).isTrue()
    }

    @Test
    fun noHelloInTimeIsClosedOnARealSocket() {
        val listener = Listener()
        open(listener)
        assertThat(listener.closed.get(WAIT_SECONDS, TimeUnit.SECONDS)).isEqualTo(POLICY)
        assertThat(listener.messages).isEmpty()
    }

    @Test
    fun aFrameOver4KibIsClosedAsTooBigOnARealSocket() {
        val listener = Listener()
        val socket = open(listener)
        socket.say(hello(account().token))
        listener.next()
        socket.sendText("x".repeat(MAX_FRAME_BYTES + 1), true).join()
        assertThat(listener.closed.get(WAIT_SECONDS, TimeUnit.SECONDS)).isEqualTo(TOO_BIG)
    }

    @Test
    fun anOriginOffTheListIsRefusedBeforeTheUpgrade() {
        val failure = assertThrows(CompletionException::class.java) {
            open(Listener(), origin = "https://evil.example")
        }
        val handshake = failure.cause as WebSocketHandshakeException
        assertThat(handshake.response.statusCode()).isEqualTo(HttpStatusCode.Forbidden.value)

        val listener = Listener()
        val socket = open(listener, origin = "https://quintond.github.io")
        socket.say(hello(account().token))
        assertThat(listener.next()).isInstanceOf(ServerMessage.Welcome::class.java)
        socket.sendClose(WebSocket.NORMAL_CLOSURE, "done").join()
    }

    private companion object {
        const val WAIT_SECONDS = 10L
        const val NORMAL = 1000
        const val POLICY = 1008
        const val TOO_BIG = 1009
    }
}
