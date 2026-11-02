package dev.quinton.goa.server.game.e2e

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.GameLength
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.online.HealthInfo
import dev.quinton.goa.core.online.LeaderboardSnapshot
import dev.quinton.goa.core.online.OnlineProtocol
import dev.quinton.goa.core.online.OnlineRoutes
import dev.quinton.goa.core.online.RoomView
import dev.quinton.goa.core.online.ServerMessage
import dev.quinton.goa.feature.board.play.game.GameAction
import dev.quinton.goa.feature.board.play.game.GameUiState
import dev.quinton.goa.feature.board.play.game.PlayUi
import dev.quinton.goa.feature.board.play.online.AccountStage
import dev.quinton.goa.feature.board.play.online.LobbyUi
import dev.quinton.goa.feature.board.play.online.OnlineGameDriver
import dev.quinton.goa.feature.board.play.online.OnlineHomePresenter
import dev.quinton.goa.feature.board.play.online.OnlineLobbyPresenter
import dev.quinton.goa.feature.board.play.online.OnlineState
import dev.quinton.goa.feature.board.play.online.inviteLink
import dev.quinton.goa.feature.board.play.table.TableTap
import dev.quinton.goa.server.game.GameServer
import dev.quinton.goa.server.game.GameServerConfig
import dev.quinton.goa.server.game.room.RandomGames
import dev.quinton.goa.server.game.room.RoomRules
import dev.quinton.goa.server.game.room.TestRules
import java.net.BindException
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

internal const val WAIT_MILLIS = 20_000L
internal const val FORGOTTEN_ISLAND = "forgotten-island"
internal const val ACROSS_THE_RIVER = "across-the-river"
internal const val POLL_MILLIS = 2L
internal const val RETRY_MILLIS = 3_000L
internal const val GAME_LIMIT_MILLIS = 120_000L
private const val WAIT_STEP_MILLIS = 20L

/** Waits for what a screen would show; a state that never comes fails with what is on the screen instead. */
internal suspend fun <T> StateFlow<T>.until(what: String, limit: Long = WAIT_MILLIS, check: (T) -> Boolean): T =
    withTimeoutOrNull(limit) { first(check) } ?: error("Never saw $what. On the screen: $value")

/** For a test's own waiting, where a test has to wait out something real. */
internal suspend fun eventually(what: String, limit: Long = WAIT_MILLIS, check: suspend () -> Boolean) {
    withTimeoutOrNull(limit) {
        while (!check()) delay(WAIT_STEP_MILLIS)
    } ?: error("Never saw $what")
}

internal val OnlineState.play: PlayUi?
    get() = ((this as? OnlineState.InGame)?.game ?: (this as? OnlineState.Reconnecting)?.game)
        ?.let { (it as? GameUiState.Playing)?.play }

/** Where people find a server: what they see of it is its address, whichever process it runs in. */
internal abstract class Host(val dir: Path) : AutoCloseable {
    val people = CopyOnWriteArrayList<Person>()

    @Volatile
    var port = 0
        protected set

    val base: String get() = "http://127.0.0.1:$port/game"

    val log: GameLogs = GameLogs(dir)

    fun person(name: String, seed: Int = name.hashCode()): Person = Person(this, name, seed).also { people += it }

    /** People sign up on their phones, one each. */
    fun signedUp(vararg names: String): List<Person> = names.map(::person).also { everyone ->
        runBlocking { everyone.map { p -> p.scope.launch { p.signUp() } }.joinAll() }
    }

    suspend fun getText(path: String, headers: Map<String, String> = emptyMap()): HttpResponse<String> {
        val request = HttpRequest.newBuilder(URI(base + path))
        headers.forEach { (k, v) -> request.header(k, v) }
        return withContext(Dispatchers.IO) {
            jdkHttp.send(request.build(), HttpResponse.BodyHandlers.ofString())
        }
    }

    suspend fun health(): HealthInfo =
        OnlineProtocol.json.decodeFromString(HealthInfo.serializer(), getText(OnlineRoutes.HEALTH).body())

    suspend fun board(): LeaderboardSnapshot = OnlineProtocol.json.decodeFromString(
        LeaderboardSnapshot.serializer(),
        getText(OnlineRoutes.LEADERBOARD).body(),
    )

    protected fun closePeople() = people.forEach { it.close() }
}

/**
 * A running server the way `main` runs it, real in everything: Ktor CIO on a local port, SQLite in a temporary
 * directory, the room core, the shipped catalog. [restart] opens a new one on the same database and port.
 */
internal class World(
    private val rules: RoomRules = RoomRules(),
    private val configure: (GameServerConfig) -> GameServerConfig = { it },
    private val clock: () -> Long = System::currentTimeMillis,
) : Host(Files.createTempDirectory("goa-e2e")) {
    private var server: GameServer? = null

    val store get() = checkNotNull(server).store

    val rooms get() = checkNotNull(server).rooms

    init {
        open()
    }

    private fun open() {
        val config = configure(
            GameServerConfig(
                dataDir = dir,
                assetsDir = dir,
                port = port,
                rulesBuild = "e2e",
                perClientPerMinute = ROOMY,
                globalPerMinute = ROOMY,
                accountsPerClientPerHour = ROOMY,
                accountsGlobalPerHour = ROOMY,
                socketFramesPerMinute = ROOMY,
            ),
        )
        repeat(BIND_TRIES) { attempt ->
            val fresh = GameServer(config, TestRules.engine, rules, clock)
            try {
                fresh.start(wait = false)
                server = fresh
                port = runBlocking { fresh.port() }
                return
            } catch (bound: BindException) {
                fresh.stop()
                if (attempt == BIND_TRIES - 1) throw bound
                Thread.sleep(BIND_WAIT_MILLIS)
            }
        }
    }

    fun stopServer() {
        server?.stop()
        server = null
    }

    fun startServer() = open()

    fun restart() {
        stopServer()
        startServer()
    }

    override fun close() {
        closePeople()
        stopServer()
        dir.toFile().deleteRecursively()
    }

    private companion object {
        const val ROOMY = 1_000_000
        const val BIND_TRIES = 20
        const val BIND_WAIT_MILLIS = 250L
    }
}

/** One person with one phone: their own thread, as a screen has, their own socket, and what came over it. */
internal class Person(val world: Host, val name: String, seed: Int) : AutoCloseable {
    private val thread = Executors.newSingleThreadExecutor { task ->
        Thread(task, "person-$name").apply { isDaemon = true }
    }
    private val dispatcher = thread.asCoroutineDispatcher()
    val scope = CoroutineScope(SupervisorJob() + dispatcher)
    val random = Random(seed)
    val identities = MemoryIdentities()
    val connector = JdkConnector { world.base }
    val gateway = JdkGateway({ world.base }, identities)
    val home = OnlineHomePresenter(gateway, identities, scope)

    var lastRecoveryCode: String? = null
        private set

    /** Does something on the person's own thread: the presenters and the driver are not to be shared. */
    suspend fun <T> onPhone(block: suspend () -> T): T = withContext(dispatcher) { block() }

    suspend fun signUp() = onPhone {
        home.start()
        home.state.until("the name field") { it.stage == AccountStage.PICK_NAME }
        home.setName(name)
        home.createAccount()
        val shown = home.state.until("the recovery code") { it.stage == AccountStage.SAVE_CODE }
        assertWithMessage("the code is shown once").that(shown.recoveryCode).isNotEmpty()
        assertWithMessage("nothing is saved before \"I've saved it\"").that(identities.identity).isNull()
        home.recoveryCodeSaved()
        home.state.until("the home screen") { it.stage == AccountStage.READY }
        assertThat(checkNotNull(identities.identity).name).isEqualTo(name)
        lastRecoveryCode = shown.recoveryCode
    }

    val accountId: String get() = checkNotNull(identities.identity).accountId

    fun lobby(code: String, initial: RoomView? = null) = OnlineLobbyPresenter(
        code = code,
        gateway = gateway,
        connector = connector,
        identities = identities,
        playableHeroes = TestRules.engine.playableHeroes.sorted(),
        catalogVersion = { TestRules.engine.data.catalog.dataVersion },
        scope = screen(),
        client = "e2e/1",
        initial = initial,
    )

    /** A screen: leaving it cancels this, which closes its socket. */
    fun screen(): CoroutineScope = CoroutineScope(scope.coroutineContext + Job(scope.coroutineContext[Job]))

    fun driver(gameId: String, screen: CoroutineScope = screen()) = OnlineGameDriver(
        gameId = gameId,
        connector = connector,
        identities = identities,
        catalog = { TestRules.engine.data.catalog },
        maps = { TestRules.engine.data.maps.values.toList() },
        clock = { System.currentTimeMillis() },
        scope = screen,
        client = "e2e/1",
    )

    /** The frames of [gameId]'s tables that came over the wire, as they were sent. */
    fun tables(gameId: String): List<ServerMessage.Table> =
        connector.wire.map { it.message }.filterIsInstance<ServerMessage.Table>().filter { it.gameId == gameId }

    fun end(gameId: String): ServerMessage.End? =
        connector.wire.map { it.message }.filterIsInstance<ServerMessage.End>().lastOrNull { it.gameId == gameId }

    override fun close() {
        scope.cancel()
        thread.shutdownNow()
    }
}

/** Someone in a room, from the code to the game's id. */
internal class Sitting(val person: Person, val lobby: OnlineLobbyPresenter) {
    suspend fun enter() = person.onPhone {
        lobby.start()
        lobby.state.until("the live room") { it.connected }
    }

    suspend fun seat(team: Team, index: Int) = person.onPhone {
        lobby.pickSeat(team, index)
        lobby.state.until("the seat $team $index") { ui ->
            ui.seats.any { it.team == team && it.index == index && it.accountId == person.accountId }
        }
    }

    suspend fun hero(heroId: String) = person.onPhone {
        lobby.state.until("the room") { it.yourSeat != null }
        lobby.pickHero(heroId)
        lobby.state.until("the hero $heroId") { it.yourSeat?.heroId == heroId }
    }

    suspend fun ready() = person.onPhone {
        lobby.setReady(true)
        lobby.state.until("ready") { it.yourSeat?.ready == true }
    }
}

/** A table being set up: everything the screens do, through the presenters and the real wire. */
internal class Room(val code: String, val sitting: MutableList<Sitting>) {
    /** The [LobbyUi] the host sees, shorter to say. */
    val hostLobby: StateFlow<LobbyUi> get() = sitting.first().lobby.state

    suspend fun started(): String {
        val host = sitting.first()
        host.person.onPhone {
            host.lobby.state.until("everyone ready") { it.canStart }
            host.lobby.startGame()
        }
        return sitting.map { s -> s.lobby.state.until("the game") { it.gameId != null } }.first().gameId!!
    }

    /** Everyone picks a hero and says ready; [heroes] is by person, in the order of [sitting]. */
    suspend fun pickAndReady(heroes: List<String>) {
        sitting.zip(heroes).forEach { (s, hero) ->
            s.hero(hero)
            s.ready()
        }
    }

    /** Someone follows the room's link from their home screen and sits down at whatever seat is given. */
    suspend fun comeIn(person: Person) {
        val joined = person.onPhone {
            person.home.join(inviteLink(code))
            val ui = person.home.state.until("the room to open") { it.openRoom != null || it.message != null }
            check(ui.openRoom != null) { "${person.name} couldn't join: ${ui.message}" }
            checkNotNull(ui.openRoom).also { person.home.roomOpened() }
        }
        sitting += Sitting(person, person.lobby(code, joined)).also { it.enter() }
    }

    fun heroOf(person: Person): String =
        checkNotNull(hostLobby.value.seats.first { it.accountId == person.accountId }.heroId)

    fun teamOf(person: Person): Team = hostLobby.value.seats.first { it.accountId == person.accountId }.team

    /** The seats' heroes in the engine's order: by team, then by seat. */
    val order: List<String>
        get() = hostLobby.value.seats.sortedWith(compareBy({ it.team.ordinal }, { it.index })).map { it.heroId!! }
}

/** The host makes a room on the home screen; the others come in by its link. */
internal suspend fun openRoom(
    people: List<Person>,
    teamSize: Int,
    map: String = FORGOTTEN_ISLAND,
    length: GameLength = GameLength.QUICK,
    ranked: Boolean = true,
): Room {
    val host = people.first()
    val made = host.onPhone {
        host.home.setMap(map)
        host.home.setTeamSize(teamSize)
        host.home.setLength(length)
        host.home.setRanked(ranked)
        host.home.createRoom()
        val ui = host.home.state.until("the room to open") { it.openRoom != null || it.message != null }
        check(ui.openRoom != null) { "No room: ${ui.message}" }
        checkNotNull(ui.openRoom).also { host.home.roomOpened() }
    }
    val room = Room(made.code, ArrayList())
    room.sitting += Sitting(host, host.lobby(made.code, made)).also { it.enter() }
    people.drop(1).forEach { room.comeIn(it) }
    return room
}

/** Heroes for [size] seats, all different, in the order of [Room.sitting]. */
internal fun lineup(size: Int, random: Random): List<String> =
    TestRules.engine.playableHeroes.sorted().shuffled(random).take(size)

/** How one person's phone answers. */
internal fun interface Brain {
    fun choose(ui: PlayUi, question: Decision, random: Random): Choice
}

internal val LeansIn = Brain { _, question, random -> RandomGames.choose(question, random) }

/**
 * One person looking at their phone: reads the question off [PlayUi], taps what can be tapped and confirms
 * it, as the screens do. [think] is how long before answering. [hold] keeps them from answering while it
 * says so. [look] sees every screen, for what must hold whatever the moment.
 */
internal class Player(
    val person: Person,
    val gameId: String,
    private val brain: Brain = LeansIn,
    private val think: suspend (Decision) -> Unit = { },
    @Volatile var hold: (PlayUi) -> Boolean = { false },
    private val look: (PlayUi) -> Unit = {},
) {
    private val screen = person.screen()
    val driver: OnlineGameDriver = person.driver(gameId, screen)
    val screens: OnlineState get() = driver.state.value
    private var job: Job? = null

    @Volatile
    var seen: PlayUi? = null
        private set

    fun start() {
        if (job != null) return
        job = screen.launch {
            driver.start()
            var tried: Long? = null
            var triedAt = 0L
            while (isActive) {
                val ui = driver.state.value.play
                if (ui != null) {
                    seen = ui
                    look(ui)
                    if (ended(ui)) return@launch
                    val question = ui.table.decision
                    val again = question?.id != tried || System.currentTimeMillis() - triedAt > RETRY_MILLIS
                    if (ui.table.asking && question != null && again && !hold(ui)) {
                        think(question)
                        val now = driver.state.value.play
                        if (now?.table?.decision?.id == question.id && now.table.asking && !hold(now)) {
                            answer(now, question)
                            tried = question.id
                            triedAt = System.currentTimeMillis()
                        }
                    }
                }
                delay(POLL_MILLIS)
            }
        }
    }

    /** The last table comes a moment before the frame that says whether it counted: both are the end. */
    private fun ended(ui: PlayUi) = (ui.over != null && ui.online?.rated != null) || ui.online?.closed != null

    /** Taps a space or a piece on the board when that is what is asked, picks it otherwise, then confirms. */
    private fun answer(ui: PlayUi, question: Decision) {
        val choice = brain.choose(ui, question, person.random)
        val tapped = when (choice) {
            is Choice.Space -> driver.tap(TableTap.Space(choice.hex))
            is Choice.Unit -> driver.tap(TableTap.Piece(choice.unitId))
            is Choice.Token -> driver.tap(TableTap.Piece(choice.tokenId))
            else -> false
        }
        if (!tapped) driver.onAction(GameAction.Select(choice))
        assertWithMessage("${person.name} selected what they tapped")
            .that(driver.state.value.play?.table?.selected).isEqualTo(choice)
        driver.onAction(GameAction.Answer(choice))
    }

    suspend fun finished(limit: Long): Boolean = withTimeoutOrNull(limit) { job?.join() } != null

    /** Off the screen: the game goes on without it, and its socket closes. */
    fun leaveScreen() = screen.cancel()

    fun describe(): String {
        val ui = screens.play
        val d = ui?.table?.decision
        return "${person.name}: ${screens::class.simpleName}, ${ui?.status}, asking=${ui?.table?.asking}, " +
            "q=${d?.kind}/${d?.seat}/${d?.id}, round=${ui?.table?.view?.round}.${ui?.table?.view?.turn}, " +
            "log=${ui?.log?.size}, over=${ui?.over != null}, rated=${ui?.online?.rated}, " +
            "frames=${person.connector.wire.size}"
    }
}

/** Everyone plays until the game is over for all of them; a table that stops moving fails with its screens. */
internal suspend fun playToEnd(players: List<Player>, limit: Long = GAME_LIMIT_MILLIS) {
    players.forEach { it.start() }
    val done = withTimeoutOrNull(limit) { players.forEach { it.finished(limit) } } != null
    val over = players.all { it.seen?.over != null || it.seen?.online?.closed != null }
    if (!done || !over) error("The game didn't finish:\n" + players.joinToString("\n") { it.describe() })
    players.forEach { it.leaveScreen() }
}
