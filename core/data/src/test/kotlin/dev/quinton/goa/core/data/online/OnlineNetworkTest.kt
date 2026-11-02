package dev.quinton.goa.core.data.online

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.GameLength
import dev.quinton.goa.core.online.ClientMessage
import dev.quinton.goa.core.online.CreateRoom
import dev.quinton.goa.core.online.OnlineProtocol
import dev.quinton.goa.core.online.OnlineRoutes
import dev.quinton.goa.core.online.Refusal
import dev.quinton.goa.core.online.ServerMessage
import dev.quinton.goa.feature.board.play.online.HostUnavailable
import dev.quinton.goa.feature.board.play.online.OnlineIdentity
import dev.quinton.goa.feature.board.play.online.OnlineIdentityStore
import dev.quinton.goa.feature.board.play.online.OnlineResult
import dev.quinton.goa.feature.board.play.online.OnlineWords
import dev.quinton.goa.feature.board.play.online.decodeServerFrame
import dev.quinton.goa.feature.board.play.online.leaderboard.LeaderboardFetcher
import java.io.IOException
import java.net.InetAddress
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerializationException
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.Cache
import okhttp3.CookieJar
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

/** The real OkHttp clients against a local server: what each call sends, and what its answers turn into. */
class OnlineNetworkTest {
    private val server = MockWebServer()
    private val token = "secret-token"

    private class Identities(var identity: OnlineIdentity?) : OnlineIdentityStore {
        override suspend fun load() = identity

        override suspend fun save(identity: OnlineIdentity) {
            this.identity = identity
        }

        override suspend fun clear() {
            identity = null
        }
    }

    private val identities = Identities(OnlineIdentity("acc", token, "Me"))
    private lateinit var base: String
    private lateinit var hosts: GameHosts

    private val noFiles = LeaderboardFetcher { null }

    @Before
    fun setUp() {
        server.start(InetAddress.getByName("127.0.0.1"), 0)
        base = "http://127.0.0.1:${server.port}/game"
        hosts = GameHosts(noFiles, GameServerDefaults(base, allowLocalHttp = true)) { 0L }
    }

    @After
    fun tearDown() = server.close()

    private val gateway by lazy { OkHttpOnlineGateway(OnlineHttpClients.rest(1, 2, 3), hosts, identities) }

    private fun answer(code: Int, body: String, vararg headers: Pair<String, String>) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val builder = MockResponse.Builder().code(code).body(body)
                headers.forEach { (name, value) -> builder.addHeader(name, value) }
                return builder.build()
            }
        }
    }

    // ---- REST ----------------------------------------------------------------------------------

    @Test
    fun `a signed-in call sends the token as a bearer header, under the base address`() = runBlocking {
        answer(200, """{"accountId":"acc","name":"Me"}""")
        val result = gateway.me()
        assertThat((result as OnlineResult.Ok).value.name).isEqualTo("Me")
        val request = server.takeRequest()
        assertThat(request.url.encodedPath).isEqualTo("/game/v1/me")
        assertThat(request.headers["Authorization"]).isEqualTo("Bearer $token")
        assertThat(request.url.toString()).doesNotContain(token)
    }

    @Test
    fun `creating and linking an account send no token`() = runBlocking {
        answer(201, """{"accountId":"a","token":"t","recoveryCode":"R"}""")
        assertThat(gateway.createAccount("Ann")).isInstanceOf(OnlineResult.Ok::class.java)
        assertThat(server.takeRequest().headers["Authorization"]).isNull()
        assertThat(gateway.link("R")).isInstanceOf(OnlineResult.Ok::class.java)
        val linked = server.takeRequest()
        assertThat(linked.headers["Authorization"]).isNull()
        assertThat(linked.url.encodedPath).isEqualTo("/game" + OnlineRoutes.ACCOUNTS_LINK)
    }

    @Test
    fun `a room is created with its form, and joined by its route`() = runBlocking {
        answer(201, """{"code":"ABCD1234"}""")
        gateway.createRoom(CreateRoom("forgotten-island", GameLength.QUICK, 2, ranked = true))
        val body = server.takeRequest().body!!.utf8()
        assertThat(body).contains("forgotten-island")
        assertThat(body).contains("\"ranked\":true")
        answer(404, """{"error":"no_such_room"}""")
        assertThat(gateway.join("ABCD1234")).isEqualTo(OnlineResult.Refused(Refusal.NO_SUCH_ROOM))
        assertThat(server.takeRequest().url.encodedPath).isEqualTo("/game" + OnlineRoutes.join("ABCD1234"))
    }

    @Test
    fun `a refusal carries its Retry-After`() = runBlocking {
        answer(503, """{"error":"rooms_full"}""", "Retry-After" to "60")
        assertThat(gateway.join("ABCD1234")).isEqualTo(OnlineResult.Refused(Refusal.ROOMS_FULL, 60))
    }

    @Test
    fun `a dead token reads as BAD_TOKEN and a mistyped code as itself, not as can't be reached`() = runBlocking {
        answer(401, """{"error":"unauthorized"}""")
        assertThat(gateway.myGames()).isEqualTo(OnlineResult.Refused(Refusal.BAD_TOKEN))
        answer(401, """{"error":"bad_recovery_code"}""")
        assertThat(gateway.link("x")).isEqualTo(OnlineResult.Failed(OnlineWords.apiError("bad_recovery_code")))
    }

    @Test
    fun `a delete answers no content`() = runBlocking {
        answer(204, "")
        assertThat(gateway.deleteMe()).isEqualTo(OnlineResult.Ok(Unit))
        assertThat(server.takeRequest().method).isEqualTo("DELETE")
    }

    @Test
    fun `no account on this device doesn't call the server`() = runBlocking {
        identities.identity = null
        assertThat(gateway.me()).isEqualTo(OnlineResult.Refused(Refusal.BAD_TOKEN))
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test
    fun `an answer this build can't read means a newer server`() = runBlocking {
        answer(200, """{"status":"unexpected"}""")
        assertThat(gateway.me()).isEqualTo(OnlineResult.Refused(Refusal.UPDATE_APP))
    }

    @Test
    fun `a page that isn't the server's is a failed call`() = runBlocking {
        answer(502, "<html>Bad gateway</html>")
        assertThat(gateway.me()).isEqualTo(OnlineResult.Failed())
    }

    @Test
    fun `the network being gone is a failed call, never thrown`() = runBlocking {
        server.close()
        assertThat(gateway.me()).isEqualTo(OnlineResult.Failed())
    }

    @Test
    fun `a redirect isn't followed, so the token can't go with it`() = runBlocking {
        val elsewhere = MockWebServer().also { it.start(InetAddress.getByName("127.0.0.1"), 0) }
        answer(302, "", "Location" to "http://127.0.0.1:${elsewhere.port}/stolen")
        assertThat(gateway.me()).isEqualTo(OnlineResult.Failed())
        assertThat(elsewhere.requestCount).isEqualTo(0)
        elsewhere.close()
    }

    @Test
    fun `a closed host stops REST with its note, and a newer file asks for an update`() = runBlocking {
        val note = LeaderboardFetcher {
            """{"schema":1,"minProtocol":1,"games":[{"origin":"$base","status":"closed","note":"Back soon"}]}"""
        }
        val closed = OkHttpOnlineGateway(
            OnlineHttpClients.rest(),
            GameHosts(note, GameServerDefaults(base, allowLocalHttp = true)) { 0L },
            identities,
        )
        assertThat(closed.me()).isEqualTo(OnlineResult.Failed("Back soon"))

        val newer = LeaderboardFetcher {
            """{"schema":1,"minProtocol":${OnlineProtocol.VERSION + 1},"games":[{"origin":"$base","status":"open"}]}"""
        }
        val tooOld = OkHttpOnlineGateway(
            OnlineHttpClients.rest(),
            GameHosts(newer, GameServerDefaults(base, allowLocalHttp = true)) { 0L },
            identities,
        )
        assertThat(tooOld.me()).isEqualTo(OnlineResult.Refused(Refusal.UPDATE_APP))
        assertThat(server.requestCount).isEqualTo(0)
    }

    // ---- The clients ---------------------------------------------------------------------------

    @Test
    fun `the socket's client has no call timeout, pings, and carries nothing a token could leak through`() {
        val socket = OnlineHttpClients.socket()
        assertThat(socket.callTimeoutMillis).isEqualTo(0)
        assertThat(socket.readTimeoutMillis).isEqualTo(0)
        assertThat(socket.pingIntervalMillis).isEqualTo(20_000)
        assertThat(socket.followRedirects).isFalse()
        assertThat(socket.followSslRedirects).isFalse()
        assertThat(socket.interceptors).isEmpty()
        assertThat(socket.networkInterceptors).isEmpty()
        assertThat(socket.cookieJar).isEqualTo(CookieJar.NO_COOKIES)
        assertThat(socket.cache).isEqualTo(null as Cache?)

        val rest = OnlineHttpClients.rest()
        assertThat(rest.callTimeoutMillis).isGreaterThan(0)
        assertThat(rest.followRedirects).isFalse()
        assertThat(rest.interceptors).isEmpty()
        assertThat(rest.cookieJar).isEqualTo(CookieJar.NO_COOKIES)
        assertThat(rest.cache).isEqualTo(null as Cache?)
    }

    // ---- The socket ----------------------------------------------------------------------------

    private val hello = ClientMessage.Hello(OnlineProtocol.VERSION, token, "test/1", catalogVersion = 7)

    /** A server's end of a socket: what it received, and the socket to send on. */
    private inner class ServerEnd : WebSocketListener() {
        val received = LinkedBlockingQueue<String>()
        val opened = LinkedBlockingQueue<WebSocket>()
        val closes = LinkedBlockingQueue<Int>()

        override fun onOpen(webSocket: WebSocket, response: Response) {
            opened.put(webSocket)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            received.put(text)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            closes.put(code)
            webSocket.close(1000, null)
        }

        fun socket(): WebSocket = checkNotNull(opened.poll(WAIT_SECONDS, TimeUnit.SECONDS)) { "never opened" }

        fun next(): String = checkNotNull(received.poll(WAIT_SECONDS, TimeUnit.SECONDS)) { "nothing sent" }
    }

    private fun upgrade(end: ServerEnd) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse.Builder().webSocketUpgrade(end).build()
        }
    }

    private val connector by lazy { OkHttpGameConnector(OnlineHttpClients.socket(1, 2, 60), hosts) }

    @Test
    fun `Hello is the first frame, and the token is in no address or header`() = runBlocking {
        val end = ServerEnd()
        upgrade(end)
        val connection = connector.open(hello)
        val first = OnlineProtocol.json.decodeFromString(ClientMessage.serializer(), end.next())
        assertThat(first).isEqualTo(hello)
        val request = server.takeRequest()
        assertThat(request.url.encodedPath).isEqualTo("/game" + OnlineRoutes.PLAY)
        assertThat(request.url.toString()).doesNotContain(token)
        assertThat(request.headers.toString()).doesNotContain(token)
        assertThat(request.headers["Origin"]).isNull()
        assertThat(request.headers["Authorization"]).isNull()
        connection.close()
    }

    @Test
    fun `frames are decoded, what is sent reaches the server, and a close ends the flow`() = runBlocking {
        val end = ServerEnd()
        upgrade(end)
        val connection = connector.open(hello)
        end.next()
        connection.send(ClientMessage.Ping(5))
        assertThat(OnlineProtocol.json.decodeFromString(ClientMessage.serializer(), end.next()))
            .isEqualTo(ClientMessage.Ping(5))
        val socket = end.socket()
        socket.send("""{"type":"pong","nonce":5,"serverTime":99}""")
        assertThat(withTimeout(WAIT_MS) { connection.incoming.first() }).isEqualTo(ServerMessage.Pong(5, 99))
        socket.close(1013, "behind")
        assertThat(withTimeout(WAIT_MS) { connection.incoming.toList() }).isEmpty()
    }

    @Test
    fun `sending on a closed socket fails, so that the driver reconnects`() {
        val end = ServerEnd()
        upgrade(end)
        runBlocking {
            val connection = connector.open(hello)
            connection.close()
            assertThrows(IOException::class.java) { runBlocking { connection.send(ClientMessage.Ping(1)) } }
        }
    }

    @Test
    fun `a frame this build can't read fails the flow with the SerializationException`() {
        val end = ServerEnd()
        upgrade(end)
        runBlocking {
            val connection = connector.open(hello)
            end.socket().send("""{"type":"a.frame.from.the.future","x":1}""")
            val failure = runCatching { withTimeout(WAIT_MS) { connection.incoming.first() } }.exceptionOrNull()
            assertThat(failure).isInstanceOf(SerializationException::class.java)
            connection.close()
        }
    }

    @Test
    fun `a host that won't open a socket fails the open, and a redirect isn't followed`() {
        answer(302, "", "Location" to "http://127.0.0.1:1/elsewhere")
        assertThrows(IOException::class.java) { runBlocking { connector.open(hello) } }
    }

    @Test
    fun `no open host fails the open`() {
        val closed = LeaderboardFetcher {
            """{"schema":1,"minProtocol":1,"games":[{"origin":"$base","status":"closed"}]}"""
        }
        val none = OkHttpGameConnector(
            OnlineHttpClients.socket(),
            GameHosts(closed, GameServerDefaults(base, allowLocalHttp = true)) { 0L },
        )
        assertThrows(HostUnavailable::class.java) { runBlocking { none.open(hello) } }
    }

    @Test
    fun `the protocol's pong reads back as the type the driver expects`() {
        val frame = decodeServerFrame("""{"type":"pong","nonce":1,"serverTime":2}""")
        assertThat(frame).isEqualTo(ServerMessage.Pong(1, 2))
    }

    // ---- Public files --------------------------------------------------------------------------

    @Test
    fun `a public file is fetched with no token, and any failure is null`() = runBlocking {
        val fetcher = OkHttpLeaderboardFetcher(OnlineHttpClients.rest(1, 2, 3))
        answer(200, "{}")
        assertThat(fetcher.get(server.url("/leaderboard.json").toString())).isEqualTo("{}")
        assertThat(server.takeRequest().headers["Authorization"]).isNull()
        answer(404, "nope")
        assertThat(fetcher.get(server.url("/leaderboard.json").toString())).isNull()
        assertThat(fetcher.get("not an address")).isNull()
        server.close()
        assertThat(fetcher.get(base + "/leaderboard.json")).isNull()
    }

    private companion object {
        const val WAIT_SECONDS = 5L
        const val WAIT_MS = 5_000L
    }
}
