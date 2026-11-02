package dev.quinton.goa.server.game

import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.core.game.GameLength
import dev.quinton.goa.core.game.GameSession
import dev.quinton.goa.core.game.GameSetup
import dev.quinton.goa.core.game.engine.GameEngine
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.online.AccountCreated
import dev.quinton.goa.core.online.ClientMessage
import dev.quinton.goa.core.online.CreateAccount
import dev.quinton.goa.core.online.CreateRoom
import dev.quinton.goa.core.online.LeaderboardSnapshot
import dev.quinton.goa.core.online.OnlineProtocol
import dev.quinton.goa.core.online.Resume
import dev.quinton.goa.core.online.RoomCreated
import dev.quinton.goa.core.online.RoomView
import dev.quinton.goa.core.online.SeatPlayer
import dev.quinton.goa.core.online.ServerMessage
import dev.quinton.goa.core.online.TurnRef
import dev.quinton.goa.server.game.http.gameServerModule
import dev.quinton.goa.server.game.room.GameRooms
import dev.quinton.goa.server.game.room.LeaderboardSource
import dev.quinton.goa.server.game.room.SeatLink
import dev.quinton.goa.server.game.room.SeatSink
import dev.quinton.goa.server.game.store.SqliteGameStore
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.KSerializer

/** The shipped catalog and rules: a preflight here is the real one. */
internal val testEngine: GameEngine by lazy { loadEngine(Paths.get("../../app/src/main/assets")) }

internal fun <T> KSerializer<T>.json(value: T): String = OnlineProtocol.json.encodeToString(this, value)

internal fun <T> KSerializer<T>.parse(text: String): T = OnlineProtocol.json.decodeFromString(this, text)

/** A room core that keeps to the [GameRooms] contract, with a real session behind it for real frames. */
internal class FakeRooms(private val engine: GameEngine = testEngine) : GameRooms {
    class Game(
        val id: String,
        val roomCode: String,
        val setup: GameSetup,
        val players: List<SeatPlayer>,
        val ranked: Boolean,
        val session: GameSession,
    ) {
        val sinks = CopyOnWriteArrayList<Pair<String, SeatSink>>()
    }

    data class Answered(val gameId: String, val accountId: String, val ticket: Long, val choice: Choice)

    val games = LinkedHashMap<String, Game>()
    val answers = CopyOnWriteArrayList<Answered>()
    val reviews = CopyOnWriteArrayList<Pair<String, TurnRef?>>()
    val left = CopyOnWriteArrayList<Pair<String, String>>()
    val connects = CopyOnWriteArrayList<Triple<String, String, Int>>()
    val closedLinks = AtomicInteger()

    @Volatile
    var extraRunning = 0
    private val nextId = AtomicInteger()

    override val runningCount: Int get() = games.size + extraRunning

    override suspend fun restore() = Unit

    override suspend fun start(roomCode: String, setup: GameSetup, players: List<SeatPlayer>, ranked: Boolean): String {
        val id = "game-${nextId.incrementAndGet()}"
        games[id] = Game(id, roomCode, setup, players, ranked, engine.newGame(setup))
        return id
    }

    override suspend fun connect(gameId: String, accountId: String, haveEvents: Int, sink: SeatSink): SeatLink? {
        val game = games[gameId] ?: return null
        val seats = game.players.filter { it.accountId == accountId }.map { it.seat }.toSet()
        if (seats.isEmpty()) return null
        connects += Triple(gameId, accountId, haveEvents)
        val entry = accountId to sink
        game.sinks += entry
        sink.send(ServerMessage.GameStart(gameId, game.setup, game.players, seats, game.ranked))
        sink.send(table(game, seats.first(), seq = 1, from = haveEvents, events = emptyList()))
        return SeatLink {
            game.sinks -= entry
            closedLinks.incrementAndGet()
        }
    }

    override suspend fun answer(gameId: String, accountId: String, ticket: Long, choice: Choice) {
        answers += Answered(gameId, accountId, ticket, choice)
        sinkOf(gameId, accountId)?.send(ServerMessage.Ack(gameId, ticket, accepted = true))
    }

    override suspend fun review(gameId: String, accountId: String, rewind: TurnRef?) {
        reviews += accountId to rewind
        sinkOf(gameId, accountId)?.send(ServerMessage.Reviewed(gameId, emptyList()))
    }

    override suspend fun leave(gameId: String, accountId: String) {
        left += gameId to accountId
    }

    override suspend fun gamesOf(accountId: String): List<String> =
        games.values.filter { game -> game.players.any { it.accountId == accountId } }.map { it.id }

    override suspend fun runningOf(accountId: String): List<String> = gamesOf(accountId)

    fun table(game: Game, seat: String, seq: Long, from: Int, events: List<GameEvent>): ServerMessage.Table {
        val pending = game.session.pending
        return ServerMessage.Table(
            gameId = game.id,
            seq = seq,
            view = game.session.view(seat),
            decision = pending?.takeIf { it.seat == seat },
            eventsFrom = from,
            events = events,
        )
    }

    fun sendAll(game: Game, message: (accountId: String) -> ServerMessage) {
        game.sinks.forEach { (accountId, sink) -> sink.send(message(accountId)) }
    }

    private fun sinkOf(gameId: String, accountId: String): SeatSink? =
        games[gameId]?.sinks?.firstOrNull { it.first == accountId }?.second
}

internal fun board(sha: String = "abc123") = LeaderboardSnapshot(
    schema = LeaderboardSnapshot.SCHEMA,
    generatedAt = 1,
    logSha256 = sha,
    matches = 0,
    eloConfig = 1,
    players = emptyList(),
    heroes = emptyList(),
)

internal class TestClock(var now: Long = 1_000_000L) {
    fun read(): Long = now
}

/** A whole server on a temporary database. */
internal class TestServer(
    builder: ApplicationTestBuilder,
    val config: GameServerConfig,
    val store: SqliteGameStore,
    val rooms: FakeRooms,
    val clock: TestClock,
) {
    val http: HttpClient = builder.client
    val ws: HttpClient by lazy { builder.createClient { install(WebSockets) } }
    private val names = AtomicInteger()

    suspend fun account(name: String = "Player ${names.incrementAndGet()}"): AccountCreated {
        val response = http.post("/v1/accounts") { jsonBody(CreateAccount.serializer(), CreateAccount(name)) }
        return AccountCreated.serializer().parse(response.bodyAsText())
    }

    suspend fun createRoom(
        token: String,
        teamSize: Int = 1,
        mapId: String = FOREST,
        length: GameLength = GameLength.QUICK,
        ranked: Boolean = false,
        from: String? = null,
    ): HttpResponse = http.post("/v1/rooms") {
        bearerAuth(token)
        from?.let { header(HttpHeaders.XForwardedFor, it) }
        jsonBody(CreateRoom.serializer(), CreateRoom(mapId, length, teamSize, ranked))
    }

    suspend fun room(token: String, teamSize: Int = 1, ranked: Boolean = false, from: String? = null): String =
        RoomCreated.serializer().parse(createRoom(token, teamSize, ranked = ranked, from = from).bodyAsText()).code

    suspend fun join(token: String, code: String): RoomView =
        RoomView.serializer().parse(http.post("/v1/rooms/$code/join") { bearerAuth(token) }.bodyAsText())

    /** A started 1v1 between two new accounts, played into being over the sockets. */
    suspend fun startedGame(): StartedGame {
        val red = account()
        val blue = account()
        val code = room(red.token)
        join(blue.token, code)
        ws.play(blue.token) {
            sendMessage(ClientMessage.PickSeat(code, Team.BLUE, 0, "arien"))
            sendMessage(ClientMessage.Ready(code, true))
            nextOf<ServerMessage.Lobby> { lobby -> lobby.room.seats.any { it.ready } }
        }
        ws.play(red.token) {
            sendMessage(ClientMessage.PickSeat(code, Team.RED, 0, "wasp"))
            sendMessage(ClientMessage.Ready(code, true))
            nextOf<ServerMessage.Lobby> { lobby -> lobby.room.seats.all { it.ready } }
            sendMessage(ClientMessage.Start(code))
            nextOf<ServerMessage.GameStart>()
        }
        return StartedGame(red, blue, rooms.games.keys.last())
    }

    companion object {
        const val FOREST = "forgotten-island"
        const val RIVER = "across-the-river"
    }
}

internal data class StartedGame(val red: AccountCreated, val blue: AccountCreated, val gameId: String)

internal fun <T> HttpRequestBuilder.jsonBody(serializer: KSerializer<T>, value: T) {
    contentType(ContentType.Application.Json)
    setBody(serializer.json(value))
}

internal fun serverTest(
    configure: (GameServerConfig) -> GameServerConfig = { it },
    rooms: FakeRooms = FakeRooms(),
    leaderboard: LeaderboardSource = LeaderboardSource { board() },
    block: suspend TestServer.() -> Unit,
) {
    val dir: Path = Files.createTempDirectory("goa-game-test")
    val store = SqliteGameStore.open(dir)
    val clock = TestClock()
    try {
        val roomy = GameServerConfig(
            dir,
            Paths.get("unused"),
            rulesBuild = "test",
            perClientPerMinute = 100_000,
            globalPerMinute = 100_000,
            accountsPerClientPerHour = 1_000,
            accountsGlobalPerHour = 1_000,
        )
        val config = configure(roomy)
        testApplication {
            application { gameServerModule(config, store, testEngine, rooms, leaderboard, clock::read) }
            TestServer(this, config, store, rooms, clock).block()
        }
    } finally {
        store.close()
        dir.toFile().deleteRecursively()
    }
}

// Socket helpers ---------------------------------------------------------------------------------

internal const val WAIT_MILLIS = 10_000L

internal suspend fun HttpClient.play(
    token: String,
    resume: List<Resume> = emptyList(),
    block: suspend DefaultClientWebSocketSession.() -> Unit,
) {
    webSocket("/v1/play") {
        sendMessage(hello(token, resume))
        nextOf<ServerMessage.Welcome>()
        block()
    }
}

internal fun hello(
    token: String,
    resume: List<Resume> = emptyList(),
    protocol: Int = OnlineProtocol.VERSION,
    catalogVersion: Int = testEngine.data.catalog.dataVersion,
) = ClientMessage.Hello(protocol, token, "test/1", catalogVersion, resume)

internal suspend fun DefaultClientWebSocketSession.sendMessage(message: ClientMessage) {
    send(Frame.Text(ClientMessage.serializer().json(message)))
}

internal suspend fun DefaultClientWebSocketSession.next(): ServerMessage = withTimeout(WAIT_MILLIS) {
    val frame = incoming.receive() as Frame.Text
    ServerMessage.serializer().parse(frame.readText())
}

/** Skips other frames up to the first [T] that [where] accepts. */
internal suspend inline fun <reified T : ServerMessage> DefaultClientWebSocketSession.nextOf(
    where: (T) -> Boolean = { true },
): T {
    while (true) {
        val message = next()
        if (message is T && where(message)) return message
    }
}
