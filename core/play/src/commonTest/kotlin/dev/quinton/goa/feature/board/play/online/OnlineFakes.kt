package dev.quinton.goa.feature.board.play.online

import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Controller
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionContext
import dev.quinton.goa.core.game.GameSession
import dev.quinton.goa.core.game.GameSetup
import dev.quinton.goa.core.game.SeatSetup
import dev.quinton.goa.core.game.engine.GameEngine
import dev.quinton.goa.core.model.board.BoardMap
import dev.quinton.goa.core.model.catalog.CatalogFiles
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.online.AccountCreated
import dev.quinton.goa.core.online.ClientMessage
import dev.quinton.goa.core.online.CreateRoom
import dev.quinton.goa.core.online.DecisionClock
import dev.quinton.goa.core.online.Me
import dev.quinton.goa.core.online.MyGames
import dev.quinton.goa.core.online.Presence
import dev.quinton.goa.core.online.RoomCreated
import dev.quinton.goa.core.online.RoomView
import dev.quinton.goa.core.online.SeatPlayer
import dev.quinton.goa.core.online.ServerMessage
import dev.quinton.goa.feature.board.board.readRepoFile
import dev.quinton.goa.feature.board.play.game.GameUiState
import dev.quinton.goa.feature.board.play.game.PlayUi
import kotlin.random.Random
import kotlin.test.assertIs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent

/** The shipped catalog and maps, read once: every test of a class would otherwise parse them again. */
internal object OnlineFixture {
    private fun asset(path: String) = readRepoFile("app/src/main/assets/$path")

    val catalog by lazy { CatalogFiles.load(::asset) }
    val maps: List<BoardMap> by lazy {
        CatalogFiles.parseMapIndex(asset(CatalogFiles.MAPS_INDEX_PATH)).map {
            CatalogFiles.parseMap(it, asset(CatalogFiles.mapPath(it)))
        }
    }
    val engine by lazy { GameEngine(catalog, maps) }

    /** The rulebook's first game (p.5), every seat a person: online that is every game. */
    val setup = GameSetup(
        mapId = "forgotten-island",
        seats = listOf("wasp" to Team.RED, "xargatha" to Team.RED, "arien" to Team.BLUE, "brogan" to Team.BLUE)
            .map { (id, team) -> SeatSetup(id, team, Controller.Human) },
        tieBreaker = Team.RED,
    )
}

/**
 * A real game and the frames a server would make of it, as `SeatProjector` must: [view] of the seat, its
 * own question with a ticket for an id, a stub for anyone else's, public events from what it holds.
 */
internal class FakeServer(val session: GameSession = OnlineFixture.engine.newGame(OnlineFixture.setup)) {
    private var seq = 0L
    val players = session.setup.seats.map { SeatPlayer(it.heroId, "acc-${it.heroId}", "${it.heroId} person", it.team) }

    fun start(vararg you: String, gameId: String = GAME) =
        ServerMessage.GameStart(gameId, session.setup, players, you.toSet(), ranked = true)

    fun ticket(decision: Decision): Long = decision.id + TICKET_BASE

    fun question(seat: String): Decision? = session.pending?.let { pending ->
        if (pending.seat == seat) pending.copy(id = ticket(pending)) else stub(pending)
    }

    fun table(
        seat: String,
        have: Int = 0,
        decision: Decision? = question(seat),
        locked: List<String> = emptyList(),
        clock: DecisionClock? = null,
        presence: Map<String, Presence> = emptyMap(),
        gameId: String = GAME,
    ) = ServerMessage.Table(
        gameId = gameId,
        seq = ++seq,
        view = session.view(seat),
        decision = decision,
        eventsFrom = have,
        events = session.events.drop(have),
        locked = locked,
        clock = clock,
        presence = presence,
    )

    /** Answers the question in play with its first option. */
    fun answerFirst() {
        val pending = checkNotNull(session.pending) { "the game is over" }
        session.answer(pending.id, pending.options.first())
    }

    companion object {
        const val GAME = "game-1"
        const val TICKET_BASE = 1_000L

        fun stub(decision: Decision) = decision.copy(
            id = 0,
            prompt = "",
            sourceCardId = null,
            options = emptyList(),
            context = DecisionContext(),
        )
    }
}

/** One socket: frames go in with [feed], what the client sent is in [sent]; a ping gets its pong. */
internal class FakeGameConnection(private val serverTime: () -> Long) : GameConnection {
    private val frames = Channel<ServerMessage>(Channel.UNLIMITED)
    val sent = mutableListOf<ClientMessage>()
    var closed = false
        private set

    /** False: a socket gone dead without anyone being told. */
    var answersPings = true

    override val incoming: Flow<ServerMessage> = frames.receiveAsFlow()

    override suspend fun send(message: ClientMessage) {
        check(!closed) { "sent on a closed socket" }
        sent += message
        if (message is ClientMessage.Ping && answersPings) feed(ServerMessage.Pong(message.nonce, serverTime()))
    }

    override fun close() {
        closed = true
        frames.close()
    }

    fun feed(frame: ServerMessage) {
        frames.trySend(frame)
    }

    /** What a frame this build can't read does to the platform's flow. */
    fun failWith(cause: Throwable) {
        frames.close(cause)
    }

    /** What the client sent, without the pings. */
    val said: List<ClientMessage> get() = sent.filter { it !is ClientMessage.Ping }
}

internal class FakeConnector(private val serverTime: () -> Long) : GameConnector {
    val hellos = mutableListOf<ClientMessage.Hello>()
    val connections = mutableListOf<FakeGameConnection>()
    var unreachable = false
    var onOpen: (FakeGameConnection) -> Unit = {}

    val last: FakeGameConnection get() = connections.last()

    override suspend fun open(hello: ClientMessage.Hello): GameConnection {
        hellos += hello
        if (unreachable) error("no route to host")
        return FakeGameConnection(serverTime).also {
            connections += it
            onOpen(it)
        }
    }
}

internal class FakeIdentities(var identity: OnlineIdentity? = OnlineIdentity("acc", "secret-token", "Me")) :
    OnlineIdentityStore {
    override suspend fun load(): OnlineIdentity? = identity

    override suspend fun save(identity: OnlineIdentity) {
        this.identity = identity
    }

    override suspend fun clear() {
        identity = null
    }
}

/** REST with each route's answer set by the test. */
internal class FakeGateway : OnlineGateway {
    var account: OnlineResult<AccountCreated> = OnlineResult.Ok(AccountCreated("acc", "token", "RECOVER-1"))
    var linked: OnlineResult<AccountCreated> = OnlineResult.Ok(AccountCreated("acc", "token2", "RECOVER-2"))
    var who: OnlineResult<Me> = OnlineResult.Ok(Me("acc", "Linked"))
    var renamed: OnlineResult<Me> = OnlineResult.Ok(Me("acc", "New name"))
    var deleted: OnlineResult<Unit> = OnlineResult.Ok(Unit)
    var created: OnlineResult<RoomCreated> = OnlineResult.Ok(RoomCreated("ABCD1234"))
    var joined: OnlineResult<RoomView>? = null
    var left: OnlineResult<RoomView>? = null
    var games: OnlineResult<MyGames> = OnlineResult.Ok(MyGames(emptyList()))
    val rooms = mutableListOf<CreateRoom>()
    val joins = mutableListOf<String>()

    override suspend fun createAccount(name: String) = account

    override suspend fun link(recoveryCode: String) = linked

    override suspend fun me() = who

    override suspend fun rename(name: String) = renamed

    override suspend fun deleteMe() = deleted

    override suspend fun createRoom(room: CreateRoom): OnlineResult<RoomCreated> {
        rooms += room
        return created
    }

    override suspend fun join(code: String): OnlineResult<RoomView> {
        joins += code
        return checkNotNull(joined) { "join wasn't expected" }
    }

    override suspend fun leave(code: String): OnlineResult<RoomView> = left ?: OnlineResult.Failed()

    override suspend fun myGames() = games
}

/** One person's driver on a [FakeServer]: with a [seat], the server greets every new socket as it would. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class DriverRig(private val test: TestScope, seat: String?, val server: FakeServer, skew: Long = 0) {
    val connector = FakeConnector { test.currentTime + skew }
    val identities = FakeIdentities()
    val driver = OnlineGameDriver(
        gameId = FakeServer.GAME,
        connector = connector,
        identities = identities,
        catalog = { OnlineFixture.catalog },
        maps = { OnlineFixture.maps },
        clock = { test.currentTime },
        scope = test.backgroundScope,
        client = "test/1",
        random = Random(1),
    )

    init {
        if (seat != null) {
            connector.onOpen = { connection ->
                connection.feed(server.start(seat))
                connection.feed(server.table(seat, have = connector.hellos.last().resume.single().haveEvents))
            }
        }
    }

    fun open() {
        driver.start()
        test.runCurrent()
    }

    fun feed(frame: ServerMessage) {
        connector.last.feed(frame)
        test.runCurrent()
    }

    val play: PlayUi
        get() {
            val game = when (val state = driver.state.value) {
                is OnlineState.InGame -> state.game
                is OnlineState.Reconnecting -> state.game
                else -> error("no table: $state")
            }
            return assertIs<GameUiState.Playing>(game).play
        }

    val answers: List<ClientMessage.Answer>
        get() = connector.connections.flatMap { it.said }.filterIsInstance<ClientMessage.Answer>()
}

internal fun TestScope.rig(seat: String? = "wasp", server: FakeServer = FakeServer(), skew: Long = 0) =
    DriverRig(this, seat, server, skew).also { it.open() }

internal fun Decision.firstCard(): Choice = options.first()

internal const val BACKOFF_FIRST = 1_001L
internal const val BACKOFF_SECOND = 2_001L
internal const val GAME_GONE = 5_001L
internal const val RESUME_SILENT = 10_001L
internal const val MINUTE = 60_000L
