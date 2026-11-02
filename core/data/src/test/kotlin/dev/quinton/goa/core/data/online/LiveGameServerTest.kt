package dev.quinton.goa.core.data.online

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import dev.quinton.goa.core.model.board.BoardMap
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.catalog.CatalogFiles
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.online.ClientMessage
import dev.quinton.goa.core.online.Presence
import dev.quinton.goa.core.online.RoomStatus
import dev.quinton.goa.feature.board.play.game.GameAction
import dev.quinton.goa.feature.board.play.game.GameUiState
import dev.quinton.goa.feature.board.play.game.PlayUi
import dev.quinton.goa.feature.board.play.game.TurnStatus
import dev.quinton.goa.feature.board.play.online.AccountStage
import dev.quinton.goa.feature.board.play.online.GameConnection
import dev.quinton.goa.feature.board.play.online.GameConnector
import dev.quinton.goa.feature.board.play.online.LobbyUi
import dev.quinton.goa.feature.board.play.online.OnlineGameDriver
import dev.quinton.goa.feature.board.play.online.OnlineHomePresenter
import dev.quinton.goa.feature.board.play.online.OnlineIdentity
import dev.quinton.goa.feature.board.play.online.OnlineIdentityStore
import dev.quinton.goa.feature.board.play.online.OnlineLobbyPresenter
import dev.quinton.goa.feature.board.play.online.OnlineResult
import dev.quinton.goa.feature.board.play.online.OnlineState
import dev.quinton.goa.feature.board.play.online.inviteLink
import dev.quinton.goa.feature.board.play.online.leaderboard.LeaderboardCache
import dev.quinton.goa.feature.board.play.online.leaderboard.LeaderboardOrigin
import dev.quinton.goa.feature.board.play.online.leaderboard.LeaderboardRepository
import java.io.File
import java.util.concurrent.Executors
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * The phone's real online stack (the OkHttp seams, the presenters and the driver) playing a real game against a real
 * `:server:game` on this machine, as two people. Skipped unless `build/live-game.url` names the server:
 * docs/ONLINE.md "On Android" says how to run it. What only a device can show (the screens, the keyboard, a phone's
 * network going away) is not here.
 */
class LiveGameServerTest {
    private val url: String? = File("build/live-game.url").takeIf { it.exists() }?.readText()?.trim()
    private val thread = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val job = SupervisorJob()

    /** The one thread the presenters and the driver ask for, as a screen's main thread would be. */
    private val scope = CoroutineScope(thread + job)

    @After
    fun tearDown() {
        job.cancel()
        thread.close()
    }

    private fun repoRoot(): File = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").isFile }

    private fun asset(path: String) = File(repoRoot(), "app/src/main/assets/$path").readText()

    private val catalog: Catalog by lazy { CatalogFiles.load(::asset) }
    private val maps: List<BoardMap> by lazy {
        CatalogFiles.parseMapIndex(asset(CatalogFiles.MAPS_INDEX_PATH)).map {
            CatalogFiles.parseMap(it, asset(CatalogFiles.mapPath(it)))
        }
    }

    private class Identities : OnlineIdentityStore {
        var identity: OnlineIdentity? = null

        override suspend fun load() = identity

        override suspend fun save(identity: OnlineIdentity) {
            this.identity = identity
        }

        override suspend fun clear() {
            identity = null
        }
    }

    /** The real connector, remembering what it opened, so that a test can cut the line. */
    private class Tracking(private val real: GameConnector) : GameConnector {
        val hellos = mutableListOf<ClientMessage.Hello>()
        var last: GameConnection? = null

        override suspend fun open(hello: ClientMessage.Hello): GameConnection {
            hellos += hello
            return real.open(hello).also { last = it }
        }
    }

    private inner class Person(val name: String) {
        val identities = Identities()
        val hosts = GameHosts({ null }, GameServerDefaults(url, allowLocalHttp = true)) { System.currentTimeMillis() }
        val gateway = OkHttpOnlineGateway(OnlineHttpClients.rest(), hosts, identities)
        val connector = Tracking(OkHttpGameConnector(OnlineHttpClients.socket(), hosts))
        val home = OnlineHomePresenter(gateway, identities, scope)
        lateinit var lobby: OnlineLobbyPresenter

        suspend fun signUp() {
            home.start()
            home.state.first { it.stage == AccountStage.PICK_NAME }
            home.setName(name)
            home.createAccount()
            home.state.first { it.stage == AccountStage.SAVE_CODE }
            assertThat(identities.identity).isNull()
            home.recoveryCodeSaved()
            home.state.first { it.stage == AccountStage.READY }
            assertThat(identities.identity?.name).isEqualTo(name)
        }

        fun openLobby(code: String) {
            lobby = OnlineLobbyPresenter(
                code,
                gateway,
                connector,
                identities,
                catalog.heroes.map { it.id },
                { catalog.dataVersion },
                scope,
                "test/live",
            )
            lobby.start()
        }

        suspend fun pickAndReady(hero: String) {
            lobby.state.first { it.connected && it.yourSeat != null }
            lobby.pickHero(hero)
            lobby.state.first { it.yourSeat?.heroId == hero }
            lobby.setReady(true)
            lobby.state.first { it.yourSeat?.ready == true }
        }

        fun driver(gameId: String) = OnlineGameDriver(
            gameId,
            connector,
            identities,
            { catalog },
            { maps },
            { System.currentTimeMillis() },
            scope,
            "test/live",
        )
    }

    /** Two people in one ranked 1v1, past the lobby. */
    private suspend fun startGame(ann: Person, bob: Person, heroes: Pair<String, String>): String {
        ann.signUp()
        bob.signUp()
        ann.home.setRanked(true)
        ann.home.createRoom()
        val room = ann.home.state.first { it.openRoom != null }.openRoom!!
        assertThat(room.seats.first { it.accountId != null }.team).isEqualTo(Team.RED)
        bob.home.join(inviteLink(room.code))
        bob.home.state.first { it.openRoom != null }
        ann.openLobby(room.code)
        bob.openLobby(room.code)
        ann.pickAndReady(heroes.first)
        bob.pickAndReady(heroes.second)
        val hostSees: LobbyUi = ann.lobby.state.first { it.canStart }
        assertThat(hostSees.room?.status).isEqualTo(RoomStatus.OPEN)
        ann.lobby.startGame()
        val gameId = ann.lobby.state.first { it.gameId != null }.gameId!!
        assertThat(bob.lobby.state.first { it.gameId != null }.gameId).isEqualTo(gameId)
        return gameId
    }

    private val OnlineState.play: PlayUi?
        get() = when (this) {
            is OnlineState.InGame -> game
            is OnlineState.Reconnecting -> game
            else -> null
        }.let { (it as? GameUiState.Playing)?.play }

    /** What one person saw along the way; a question they aren't asked must never show its options. */
    private class Seen {
        val statuses = mutableSetOf<String>()
        var clock = false
        var bothHere = false
        var answers = 0
        var reconnecting = false
    }

    /**
     * Answers each of the person's questions with a random option, at a person's pace. Always the first option can
     * go on for ever: a game ends by a push or by Life counters, and nobody need ever attack.
     */
    private fun CoroutineScope.play(driver: OnlineGameDriver, seen: Seen, states: StateFlow<OnlineState>): Job = launch {
        val random = Random(seen.hashCode())
        states.collectLatest { state ->
            if (state is OnlineState.Reconnecting) seen.reconnecting = true
            val play = state.play ?: return@collectLatest
            val decision = play.table.decision
            if (decision != null && decision.seat !in play.table.mine) {
                assertWithMessage("another seat's question").that(decision.options).isEmpty()
            }
            seen.statuses += play.status::class.simpleName.orEmpty()
            play.online?.let { online ->
                if (online.clockSeconds != null) seen.clock = true
                val here = online.presence.values.count { it == Presence.HERE }
                if (here == 2) seen.bothHere = true
            }
            if (state is OnlineState.InGame && play.table.asking && decision != null) {
                delay(ANSWER_MS)
                seen.answers++
                driver.onAction(GameAction.Answer(decision.options.random(random)))
            }
        }
    }

    @Test
    fun `two people play a ranked game to its end, through a dropped socket, and it counts`() = runBlocking(thread) {
        assumeTrue(url != null)
        val ann = Person("Ann")
        val bob = Person("Bob")
        val gameId = startGame(ann, bob, "arien" to "brogan")
        val listed = ann.gateway.myGames() as OnlineResult.Ok
        assertThat(listed.value.games.map { it.gameId }).contains(gameId)

        val annDriver = ann.driver(gameId)
        val bobDriver = bob.driver(gameId)
        annDriver.start()
        bobDriver.start()
        val annSeen = Seen()
        val bobSeen = Seen()
        val playing = listOf(
            scope.play(annDriver, annSeen, annDriver.state),
            scope.play(bobDriver, bobSeen, bobDriver.state),
        )

        // For watching a long game: build/live-game.log says where it has got to.
        val progress = File("build/live-game.log")
        val watching = scope.launch {
            while (true) {
                delay(WATCH_MS)
                listOf("Ann" to annDriver, "Bob" to bobDriver).forEach { (who, driver) ->
                    val state = driver.state.value
                    val play = state.play
                    progress.appendText(
                        "${System.currentTimeMillis() / 1000} $who ${state::class.simpleName} " +
                            "R${play?.table?.view?.round} T${play?.table?.view?.turn} ${play?.table?.view?.phase} " +
                            "${play?.status} asking=${play?.table?.asking} busy=${play?.busy} " +
                            "kind=${play?.table?.decision?.kind} seat=${play?.table?.decision?.seat} " +
                            "id=${play?.table?.decision?.id} opts=${play?.table?.decision?.options?.size} " +
                            "clock=${play?.online?.clockSeat}:${play?.online?.clockSeconds} lines=${play?.log?.size} " +
                            "answers=${if (who == "Ann") annSeen.answers else bobSeen.answers} msg=${play?.message}\n",
                    )
                }
            }
        }

        annDriver.state.first { (it.play?.log?.size ?: 0) > CUT_AFTER_LINES }
        val before = ann.connector.hellos.size
        ann.connector.last?.close()
        annDriver.state.first { it is OnlineState.Reconnecting }
        annDriver.resumeNow()
        annDriver.state.first { it is OnlineState.InGame && ann.connector.hellos.size > before }
        assertThat(ann.connector.hellos.last().resume.single().haveEvents).isGreaterThan(0)

        val annEnd = withTimeout(GAME_LIMIT_MS) { annDriver.state.first { it.play?.online?.rated != null }.play!! }
        val bobEnd = withTimeout(GAME_LIMIT_MS) { bobDriver.state.first { it.play?.online?.rated != null }.play!! }
        playing.forEach { it.cancel() }
        watching.cancel()

        println(
            "LIVE: ${annEnd.over?.rounds} rounds, ${annEnd.log.size} log lines, " +
                "answers Ann ${annSeen.answers} Bob ${bobSeen.answers}, " +
                "${annEnd.over?.winner} by ${annEnd.over?.reason}, statuses ${annSeen.statuses} / ${bobSeen.statuses}, " +
                "Ann's hellos ${ann.connector.hellos.size}",
        )
        assertThat(annEnd.online?.rated).isTrue()
        assertThat(bobEnd.online?.rated).isTrue()
        val annOver = annEnd.over!!
        val bobOver = bobEnd.over!!
        assertThat(annOver.winner).isEqualTo(bobOver.winner)
        assertThat(annOver.youWon).isNotEqualTo(bobOver.youWon)
        // The public log, the same for both, line for line, a reconnect in the middle or not.
        assertThat(annEnd.log.map { it.text }).isEqualTo(bobEnd.log.map { it.text })
        assertThat(annSeen.reconnecting).isTrue()
        assertThat(annSeen.clock).isTrue()
        assertThat(annSeen.bothHere).isTrue()
        assertThat(annSeen.statuses).containsAtLeast("YourTurn", "OtherPlayer", "Waiting")

        val after = ann.gateway.myGames() as OnlineResult.Ok
        assertThat(after.value.games.map { it.gameId }).doesNotContain(gameId)
        val board = withContext(Dispatchers.IO) {
            LeaderboardRepository(
                OkHttpLeaderboardFetcher(OnlineHttpClients.rest()),
                object : LeaderboardCache {
                    override suspend fun read(): String? = null

                    override suspend fun write(text: String) = Unit
                },
                url!!,
            ) { System.currentTimeMillis() }.load()
        }
        assertThat(board?.origin).isEqualTo(LeaderboardOrigin.SERVER)
        assertThat(board?.snapshot?.matches).isAtLeast(1)
    }

    @Test
    fun `leaving hands the seat to the computer, and the other person is told`() = runBlocking(thread) {
        assumeTrue(url != null)
        val ann = Person("Cid")
        val bob = Person("Dee")
        val gameId = startGame(ann, bob, "wasp" to "tigerclaw")
        val annDriver = ann.driver(gameId)
        val bobDriver = bob.driver(gameId)
        annDriver.start()
        bobDriver.start()
        val playing = scope.play(annDriver, Seen(), annDriver.state)
        bobDriver.state.first { it is OnlineState.InGame }
        bobDriver.leave()
        val play = withTimeout(LEAVE_LIMIT_MS) {
            annDriver.state.first { it.play?.online?.presence?.containsValue(Presence.COMPUTER) == true }.play!!
        }
        playing.cancel()
        val bobSeat = play.online!!.presence.entries.first { it.value == Presence.COMPUTER }.key
        assertThat(bobSeat).isEqualTo("tigerclaw")
        val games = bob.gateway.myGames() as OnlineResult.Ok
        assertThat(games.value.games.map { it.gameId }).doesNotContain(gameId)
    }

    private companion object {
        const val ANSWER_MS = 220L
        const val CUT_AFTER_LINES = 12
        const val WATCH_MS = 5_000L
        const val GAME_LIMIT_MS = 15 * 60_000L
        const val LEAVE_LIMIT_MS = 30_000L
    }
}
