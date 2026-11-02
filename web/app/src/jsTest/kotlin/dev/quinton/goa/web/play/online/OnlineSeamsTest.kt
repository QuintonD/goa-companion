package dev.quinton.goa.web.play.online

import dev.quinton.goa.core.data.sync.RelayHttp
import dev.quinton.goa.core.data.sync.RelayNetworkException
import dev.quinton.goa.core.data.sync.RelayNetworkFailure
import dev.quinton.goa.core.data.sync.RelayRequest
import dev.quinton.goa.core.data.sync.RelayResponse
import dev.quinton.goa.core.game.GameLength
import dev.quinton.goa.core.online.ClientMessage
import dev.quinton.goa.core.online.CreateRoom
import dev.quinton.goa.core.online.GameHost
import dev.quinton.goa.core.online.Me
import dev.quinton.goa.core.online.NetworkInfo
import dev.quinton.goa.core.online.OnlineProtocol
import dev.quinton.goa.core.online.OnlineRoutes
import dev.quinton.goa.core.online.Refusal
import dev.quinton.goa.core.online.ServerMessage
import dev.quinton.goa.feature.board.play.online.OnlineIdentity
import dev.quinton.goa.feature.board.play.online.OnlineResult
import dev.quinton.goa.feature.board.play.online.encodeClientFrame
import dev.quinton.goa.feature.board.play.online.leaderboard.LeaderboardOrigin
import dev.quinton.goa.feature.board.play.online.leaderboard.LeaderboardRepository
import dev.quinton.goa.web.storage.MemoryKeyValueStore
import dev.quinton.goa.web.storage.scoped
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException

private const val HOST = "https://game.example:8443/game"

/** A socket with no network: the test opens it and speaks for the server. */
private class FakeSocket : BrowserSocket {
    override var onOpen: () -> Unit = {}
    override var onText: (String) -> Unit = {}
    override var onClosed: () -> Unit = {}
    val sent = mutableListOf<String>()
    var closed = false

    override fun send(text: String) {
        sent += text
    }

    override fun close() {
        closed = true
    }
}

private class Reply(
    override val code: Int,
    private val text: String = "",
    val headers: Map<String, String> = emptyMap(),
) :
    RelayResponse {
    override fun header(name: String) = headers[name]

    override suspend fun body(max: Long) = text.encodeToByteArray()
}

/** Answers by address; an address it doesn't know is a network failure. */
private class FakeHttp(val replies: MutableMap<String, Reply> = mutableMapOf()) : RelayHttp {
    val requests = mutableListOf<RelayRequest>()

    override suspend fun <T> exchange(request: RelayRequest, read: suspend (RelayResponse) -> T): T {
        requests += request
        val reply = replies[request.url] ?: throw RelayNetworkException(RelayNetworkFailure.UNREACHABLE)
        return read(reply)
    }
}

class OnlineSeamsTest {
    // ---- The socket ----------------------------------------------------------------------------

    @Test
    fun theSocketAddressFollowsTheBaseAddress() {
        assertEquals("wss://game.example:8443/game/v1/play", gameSocketUrl(HOST))
        assertEquals("wss://game.example:8443/game/v1/play", gameSocketUrl("$HOST/"))
        assertEquals("ws://127.0.0.1:4100/v1/play", gameSocketUrl("http://127.0.0.1:4100"))
        assertFailsWith<IllegalStateException> { gameSocketUrl("ftp://game.example") }
    }

    private fun hello(token: String = "secret-token") = ClientMessage.Hello(
        OnlineProtocol.VERSION,
        token,
        "web/test",
        7,
    )

    @Test
    fun helloIsTheFirstFrameAndTheTokenNeverInTheAddress() = runTest {
        val socket = FakeSocket()
        val addresses = mutableListOf<String>()
        val connector = WebGameConnector({ HOST }) {
            addresses += it
            socket
        }
        val opening = async(start = CoroutineStart.UNDISPATCHED) { connector.open(hello()) }
        assertEquals(emptyList(), socket.sent, "nothing goes out before the socket opens")
        socket.onOpen()
        val connection = opening.await()
        assertEquals(listOf(encodeClientFrame(hello())), socket.sent)
        assertFalse(addresses.single().contains("secret-token"))
        connection.send(ClientMessage.Ping(5))
        assertEquals(encodeClientFrame(ClientMessage.Ping(5)), socket.sent[1])
    }

    @Test
    fun aSocketThatNeverOpensFailsTheOpen() = runTest {
        val socket = FakeSocket()
        val opening = async(start = CoroutineStart.UNDISPATCHED) {
            runCatching { WebGameConnector({ HOST }) { socket }.open(hello()) }
        }
        socket.onClosed()
        assertTrue(opening.await().isFailure)
    }

    @Test
    fun everyHostClosedFailsTheOpenWithoutASocket() = runTest {
        var sockets = 0
        val connector = WebGameConnector({ null }) {
            sockets++
            FakeSocket()
        }
        assertTrue(runCatching { connector.open(hello()) }.isFailure)
        assertEquals(0, sockets)
    }

    @Test
    fun framesArriveInOrderAndTheFlowEndsWhenTheSocketCloses() = runTest {
        val socket = FakeSocket()
        val opening = async(start = CoroutineStart.UNDISPATCHED) { WebGameConnector({ HOST }) { socket }.open(hello()) }
        socket.onOpen()
        val connection = opening.await()
        socket.onText("""{"type":"pong","nonce":1,"serverTime":10}""")
        socket.onText("""{"type":"pong","nonce":2,"serverTime":20}""")
        socket.onClosed()
        assertEquals(listOf(1L, 2L), connection.incoming.toList().map { (it as ServerMessage.Pong).nonce })
    }

    @Test
    fun aFrameThisBuildCantReadFailsTheFlowWithASerializationException() = runTest {
        // The driver takes exactly this exception to mean a newer server.
        val socket = FakeSocket()
        val opening = async(start = CoroutineStart.UNDISPATCHED) { WebGameConnector({ HOST }) { socket }.open(hello()) }
        socket.onOpen()
        val connection = opening.await()
        socket.onText("""{"type":"game.teleport","to":"mars"}""")
        assertFailsWith<SerializationException> { connection.incoming.toList() }
    }

    @Test
    fun closingTheConnectionClosesTheSocketAndTheFlow() = runTest {
        val socket = FakeSocket()
        val opening = async(start = CoroutineStart.UNDISPATCHED) { WebGameConnector({ HOST }) { socket }.open(hello()) }
        socket.onOpen()
        val connection = opening.await()
        val frames = async(start = CoroutineStart.UNDISPATCHED) { connection.incoming.toList() }
        connection.close()
        assertTrue(socket.closed)
        assertEquals(emptyList(), frames.await())
    }

    // ---- REST ------------------------------------------------------------------------------------

    private suspend fun gateway(http: RelayHttp, token: String? = "tok", base: String? = HOST): WebOnlineGateway {
        val identities = WebOnlineIdentityStore(MemoryKeyValueStore())
        if (token != null) identities.save(OnlineIdentity("a1", token, "Ann"))
        return WebOnlineGateway(http, identities) { base }
    }

    @Test
    fun aSignedCallCarriesTheBearerTokenAndAnOpenOneDoesNot() = runTest {
        val http = FakeHttp()
        http.replies["$HOST${OnlineRoutes.ME}"] = Reply(200, """{"accountId":"a1","name":"Ann"}""")
        http.replies["$HOST${OnlineRoutes.ACCOUNTS}"] =
            Reply(201, """{"accountId":"a2","token":"t2","recoveryCode":"R-1"}""")
        val gateway = gateway(http)
        assertEquals(OnlineResult.Ok(Me("a1", "Ann")), gateway.me())
        assertEquals("Bearer tok", http.requests.last().headers["Authorization"])
        assertEquals("GET", http.requests.last().method)
        assertIs<OnlineResult.Ok<*>>(gateway.createAccount("Bo"))
        assertNull(http.requests.last().headers["Authorization"])
        assertEquals("""{"name":"Bo"}""", http.requests.last().body?.decodeToString())
        assertEquals("application/json", http.requests.last().contentType)
    }

    @Test
    fun theRoutesGoWhereTheContractSays() = runTest {
        val http = FakeHttp()
        val room =
            """{"code":"ABCD2345","hostAccountId":"a1","mapId":"m","length":"quick","teamSize":1,"ranked":false,""" +
                """"seats":[],"status":"open","expiresAt":1}"""
        http.replies["$HOST/v1/rooms"] = Reply(201, """{"code":"ABCD2345"}""")
        http.replies["$HOST/v1/rooms/ABCD2345/join"] = Reply(200, room)
        http.replies["$HOST/v1/rooms/ABCD2345/leave"] = Reply(200, room)
        http.replies["$HOST/v1/me/games"] = Reply(200, """{"games":[]}""")
        http.replies["$HOST/v1/me"] = Reply(204)
        val gateway = gateway(http)
        assertIs<OnlineResult.Ok<*>>(gateway.createRoom(CreateRoom("m", GameLength.QUICK, 1, true)))
        assertEquals("POST", http.requests.last().method)
        assertIs<OnlineResult.Ok<*>>(gateway.join("ABCD2345"))
        assertIs<OnlineResult.Ok<*>>(gateway.leave("ABCD2345"))
        assertIs<OnlineResult.Ok<*>>(gateway.myGames())
        assertIs<OnlineResult.Ok<*>>(gateway.deleteMe())
        assertEquals("DELETE", http.requests.last().method)
    }

    @Test
    fun aRefusalAndItsRetryAfterComeBackAsARefusal() = runTest {
        val http = FakeHttp()
        http.replies["$HOST/v1/rooms"] = Reply(503, """{"error":"rooms_full"}""", mapOf("Retry-After" to "300"))
        val gateway = gateway(http)
        val result = gateway.createRoom(CreateRoom("m", GameLength.QUICK, 1, true))
        assertEquals(OnlineResult.Refused(Refusal.ROOMS_FULL, 300), result)
    }

    @Test
    fun aTokenTheServerDoesntKnowIsBadTokenAndABadRecoveryCodeIsNot() = runTest {
        val http = FakeHttp()
        http.replies["$HOST/v1/me/games"] = Reply(401, """{"error":"unauthorized"}""")
        http.replies["$HOST/v1/accounts/link"] = Reply(401, """{"error":"bad_recovery_code"}""")
        val gateway = gateway(http)
        assertEquals(OnlineResult.Refused(Refusal.BAD_TOKEN), gateway.myGames())
        val linked = assertIs<OnlineResult.Failed>(gateway.link("nope"))
        assertTrue("recovery code" in linked.message.orEmpty())
    }

    @Test
    fun noTokenOnThisDeviceIsBadTokenWithoutACall() = runTest {
        val http = FakeHttp()
        assertEquals(OnlineResult.Refused(Refusal.BAD_TOKEN), gateway(http, token = null).myGames())
        assertEquals(emptyList(), http.requests)
    }

    @Test
    fun noAnswerAnUnreadableAnswerAndClosedHostsAreFailedNeverThrown() = runTest {
        assertEquals(OnlineResult.Failed(), gateway(FakeHttp()).myGames())
        val http = FakeHttp()
        http.replies["$HOST/v1/me/games"] = Reply(200, "not json")
        assertEquals(OnlineResult.Failed(), gateway(http).myGames())
        assertEquals(OnlineResult.Failed(), gateway(FakeHttp(), base = null).myGames())
    }

    // ---- The account on this device ----------------------------------------------------------------

    @Test
    fun theIdentityLivesUnderOnlineAndSurvivesARead() = runTest {
        val store = MemoryKeyValueStore()
        val identities = WebOnlineIdentityStore(store.scoped("online/"))
        assertNull(identities.load())
        identities.save(OnlineIdentity("a1", "tok", "Ann"))
        assertEquals(OnlineIdentity("a1", "tok", "Ann"), identities.load())
        assertEquals(listOf("online/identity"), store.keys())
        identities.clear()
        assertNull(identities.load())
        assertEquals(emptyList(), store.keys())
    }

    @Test
    fun aDamagedIdentityReadsAsNoAccount() = runTest {
        val store = MemoryKeyValueStore()
        store.put("identity", "{not json")
        assertNull(WebOnlineIdentityStore(store).load())
    }

    // ---- Which host --------------------------------------------------------------------------------

    private fun network(vararg games: Pair<String, String>) =
        NetworkInfo(NetworkInfo.SCHEMA, 1, games.map { GameHost(it.first, it.second) })

    @Test
    fun theFirstOpenHostThisPageMayReachIsUsed() {
        val all = network("https://a" to "closed", "https://b/" to "open", "https://c" to "open")
        assertEquals("https://b", chooseHost(all, HOST) { true })
        assertEquals("https://c", chooseHost(all, HOST) { it == "https://c" })
    }

    @Test
    fun theBuiltInAddressIsTheFallback() {
        assertEquals(HOST, chooseHost(null, HOST) { true })
        assertEquals(HOST, chooseHost(network(), HOST) { true })
        // A new host only the file knows of: this build's policy can't reach it yet.
        assertEquals(HOST, chooseHost(network("https://new" to "open"), HOST) { false })
    }

    @Test
    fun everyHostClosedMeansNoHost() {
        assertNull(chooseHost(network("https://a" to "closed", "$HOST/" to "closed"), HOST) { true })
        // The built-in address closed, the open one out of reach: nowhere to go either.
        assertNull(chooseHost(network("$HOST/" to "closed", "https://new" to "open"), HOST) { false })
    }

    private val policy = "default-src 'self'; connect-src 'self' https://relay.example:8443 wss://relay.example:8443 " +
        "http://127.0.0.1:*; worker-src 'self'"

    @Test
    fun thePagesPolicyDecidesWhichHostsCanBeReached() {
        assertTrue(connectSrcAllows(policy, "https://relay.example:8443/game"))
        assertFalse(connectSrcAllows(policy, "https://other.example:8443/game"))
        assertTrue(connectSrcAllows(policy, "http://127.0.0.1:4100"))
        assertTrue(connectSrcAllows(null, "https://anything.example"), "the dev server has no policy")
        assertFalse(connectSrcAllows("default-src 'self'", "https://relay.example"))
    }

    private fun hosts(http: RelayHttp, override: String? = null, now: () -> Long = { 0L }) = OnlineHosts(
        read = { readPublicFile(http, it) },
        builtIn = HOST,
        override = { override },
        now = now,
        allowed = { true },
    )

    private fun networkFile(vararg games: Pair<String, String>) =
        OnlineProtocol.json.encodeToString(NetworkInfo.serializer(), network(*games))

    @Test
    fun theFileIsReadBeforeConnectingAndKeptAWhile() = runTest {
        var clock = 0L
        val http = FakeHttp()
        http.replies[OnlineRoutes.NETWORK_FILE] = Reply(200, networkFile("https://b" to "open"))
        val hosts = hosts(http) { clock }
        assertEquals("https://b", hosts.base())
        assertEquals("https://b", hosts.base())
        assertEquals(1, http.requests.size)
        clock = 10 * 60_000L
        assertEquals("https://b", hosts.base())
        assertEquals(2, http.requests.size)
    }

    @Test
    fun anUnreadableOrNewerFileFallsBackToTheBuiltInAddress() = runTest {
        val http = FakeHttp()
        assertEquals(HOST, hosts(http).base())
        http.replies[OnlineRoutes.NETWORK_FILE] = Reply(200, "<html>")
        assertEquals(HOST, hosts(http).base())
        val newer = """{"schema":99,"minProtocol":1,"games":[{"origin":"https://b","status":"open"}]}"""
        http.replies[OnlineRoutes.NETWORK_FILE] = Reply(200, newer)
        assertEquals(HOST, hosts(http).base())
    }

    @Test
    fun aTestServerOverridesTheHostsAndTheFileIsNotRead() = runTest {
        val http = FakeHttp()
        val hosts = hosts(http, override = "http://127.0.0.1:4100")
        assertEquals("http://127.0.0.1:4100", hosts.base())
        assertEquals("http://127.0.0.1:4100/v1/leaderboard", hosts.rewrite("$HOST/v1/leaderboard"))
        assertEquals(emptyList(), http.requests)
    }

    // ---- The leaderboard's seams -------------------------------------------------------------------

    private val board = """{"schema":1,"generatedAt":1000,"logSha256":"x","matches":0,"eloConfig":1,""" +
        """"players":[{"id":"a1","name":"Ann","rating":1500,"games":5,"wins":3}],"heroes":[]}"""

    private fun repository(http: FakeHttp, store: MemoryKeyValueStore = MemoryKeyValueStore()): LeaderboardRepository {
        val hosts = hosts(http)
        return LeaderboardRepository(WebLeaderboardFetcher(http, hosts), WebLeaderboardCache(store), HOST) { 61_000L }
    }

    @Test
    fun aHealthyPagesFileSendsTheGameHostNothing() = runTest {
        val http = FakeHttp()
        http.replies[OnlineRoutes.LEADERBOARD_FILE] = Reply(200, board)
        val load = repository(http).load()
        assertEquals(LeaderboardOrigin.PAGES, load?.origin)
        assertEquals(listOf(OnlineRoutes.LEADERBOARD_FILE), http.requests.map { it.url })
    }

    @Test
    fun aHostTheFileClosedIsSkippedForTheNextOne() = runTest {
        val http = FakeHttp()
        http.replies[OnlineRoutes.NETWORK_FILE] =
            Reply(200, networkFile("https://shut" to "closed", "https://b" to "open"))
        http.replies["https://shut${OnlineRoutes.LEADERBOARD}"] = Reply(200, board)
        http.replies["https://b${OnlineRoutes.LEADERBOARD}"] = Reply(200, board)
        val load = repository(http).load()
        assertEquals(LeaderboardOrigin.SERVER, load?.origin)
        assertFalse(http.requests.any { it.url.startsWith("https://shut") }, "a closed host is never asked")
    }

    @Test
    fun aBadStatusReadsAsNothingAndTheLastCopyIsUsed() = runTest {
        val store = MemoryKeyValueStore()
        val http = FakeHttp()
        http.replies[OnlineRoutes.LEADERBOARD_FILE] = Reply(200, board)
        assertEquals(LeaderboardOrigin.PAGES, repository(http, store).load()?.origin)
        http.replies[OnlineRoutes.LEADERBOARD_FILE] = Reply(500, "oops")
        val load = repository(http, store).load()
        assertEquals(LeaderboardOrigin.LAST_COPY, load?.origin)
        assertEquals(60_000L, load?.ageMillis)
    }
}
