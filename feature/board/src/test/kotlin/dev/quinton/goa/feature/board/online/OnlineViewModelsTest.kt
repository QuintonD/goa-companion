package dev.quinton.goa.feature.board.online

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.data.EditClock
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.GameLength
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.navigation.OnlineGameRoute
import dev.quinton.goa.core.navigation.OnlineHomeRoute
import dev.quinton.goa.core.navigation.OnlineLobbyRoute
import dev.quinton.goa.core.online.ClientMessage
import dev.quinton.goa.core.online.CreateRoom
import dev.quinton.goa.core.online.DecisionClock
import dev.quinton.goa.core.online.LeaderRow
import dev.quinton.goa.core.online.LeaderboardSnapshot
import dev.quinton.goa.core.online.MyGame
import dev.quinton.goa.core.online.MyGames
import dev.quinton.goa.core.online.OnlineProtocol
import dev.quinton.goa.core.online.OnlineRoutes
import dev.quinton.goa.core.online.Presence
import dev.quinton.goa.core.online.Refusal
import dev.quinton.goa.core.online.RoomStatus
import dev.quinton.goa.core.online.ServerMessage
import dev.quinton.goa.core.online.VoidReason
import dev.quinton.goa.core.testing.FakeBoardMapRepository
import dev.quinton.goa.core.testing.FakeCatalogRepository
import dev.quinton.goa.feature.board.bundledCatalog
import dev.quinton.goa.feature.board.bundledMaps
import dev.quinton.goa.feature.board.play.fakeFactory
import dev.quinton.goa.feature.board.play.game.GameAction
import dev.quinton.goa.feature.board.play.game.GameUiState
import dev.quinton.goa.feature.board.play.game.PlayUi
import dev.quinton.goa.feature.board.play.game.TurnStatus
import dev.quinton.goa.feature.board.play.online.AccountStage
import dev.quinton.goa.feature.board.play.online.OnlineIdentity
import dev.quinton.goa.feature.board.play.online.OnlineResult
import dev.quinton.goa.feature.board.play.online.OnlineState
import dev.quinton.goa.feature.board.play.online.OnlineWords
import dev.quinton.goa.feature.board.play.online.leaderboard.LeaderboardCache
import dev.quinton.goa.feature.board.play.online.leaderboard.LeaderboardPresenter
import dev.quinton.goa.feature.board.play.online.leaderboard.LeaderboardRepository
import dev.quinton.goa.feature.board.play.online.leaderboard.LeaderboardTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.SerializationException
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * The online screens' ViewModels on fakes of the platform's seams: the logic under them is tested where it lives, in
 * core:play; this is what the ViewModels add, and that the seams are wired the way docs/ONLINE.md asks.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OnlineViewModelsTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private val maps = FakeBoardMapRepository(bundledMaps)
    private val catalog = FakeCatalogRepository(bundledCatalog)

    // ---- Home ----------------------------------------------------------------------------------

    private fun home(
        identities: FakeIdentities = FakeIdentities(null),
        gateway: FakeGateway = FakeGateway(),
        route: OnlineHomeRoute = OnlineHomeRoute(),
        savedState: SavedStateHandle = SavedStateHandle(),
    ) = OnlineHomeViewModel(route, savedState, gateway, identities, maps)

    @Test
    fun `a new account is saved on the device only after I've saved it`() = runTest(dispatcher) {
        val identities = FakeIdentities(null)
        val vm = home(identities)
        runCurrent()
        assertThat(vm.uiState.value.stage).isEqualTo(AccountStage.PICK_NAME)
        vm.setName("Ann")
        vm.createAccount()
        runCurrent()
        assertThat(vm.uiState.value.stage).isEqualTo(AccountStage.SAVE_CODE)
        assertThat(vm.uiState.value.recoveryCode).isEqualTo("RECOVER-1")
        // The gate: the app dying here loses nothing, because nothing is kept yet.
        assertThat(identities.identity).isNull()
        vm.recoveryCodeSaved()
        runCurrent()
        assertThat(identities.identity).isEqualTo(OnlineIdentity("acc", "new-token", "Ann"))
        assertThat(vm.uiState.value.stage).isEqualTo(AccountStage.READY)
        assertThat(vm.uiState.value.recoveryCode).isNull()
        vm.viewModelScope.cancel()
    }

    @Test
    fun `nothing passes the gate but saying it is saved`() = runTest(dispatcher) {
        val gateway = FakeGateway()
        val vm = home(gateway = gateway)
        runCurrent()
        vm.setName("Ann")
        vm.createAccount()
        runCurrent()
        vm.createRoom()
        vm.join(CODE)
        vm.deleteAccount()
        runCurrent()
        assertThat(gateway.rooms).isEmpty()
        assertThat(gateway.joins).isEmpty()
        assertThat(vm.uiState.value.stage).isEqualTo(AccountStage.SAVE_CODE)
        vm.viewModelScope.cancel()
    }

    @Test
    fun `linking a device shows its new code, which has to be saved too`() = runTest(dispatcher) {
        val identities = FakeIdentities(null)
        val vm = home(identities)
        runCurrent()
        vm.setLinkCode("OLD-CODE")
        vm.linkDevice()
        runCurrent()
        assertThat(vm.uiState.value.stage).isEqualTo(AccountStage.SAVE_CODE)
        assertThat(vm.uiState.value.recoveryCode).isEqualTo("RECOVER-2")
        assertThat(identities.identity).isEqualTo(OnlineIdentity("acc", "linked-token", "Linked"))
        vm.viewModelScope.cancel()
    }

    @Test
    fun `the account comes back after the process died, with the games to go back to`() = runTest(dispatcher) {
        val gateway = FakeGateway()
        val game = MyGame(GAME, CODE, "forgotten-island", emptyList())
        gateway.games = OnlineResult.Ok(MyGames(listOf(game)))
        val vm = home(FakeIdentities(), gateway)
        runCurrent()
        assertThat(vm.uiState.value.stage).isEqualTo(AccountStage.READY)
        assertThat(vm.uiState.value.name).isEqualTo("Ann")
        assertThat(vm.uiState.value.games).containsExactly(game)
        val island = bundledMaps.first { it.id == "forgotten-island" }
        assertThat(vm.mapNames.value[island.id]).isEqualTo(island.name)
        vm.viewModelScope.cancel()
    }

    @Test
    fun `a room is made with what the form says and opened after joining it`() = runTest(dispatcher) {
        val gateway = FakeGateway()
        val vm = home(FakeIdentities(), gateway)
        runCurrent()
        vm.setMap("across-the-river")
        vm.setRanked(true)
        vm.createRoom()
        runCurrent()
        assertThat(gateway.rooms).containsExactly(CreateRoom("across-the-river", GameLength.QUICK, 4, ranked = true))
        assertThat(gateway.joins).containsExactly(CODE)
        assertThat(vm.uiState.value.openRoom?.code).isEqualTo(CODE)
        vm.roomOpened()
        assertThat(vm.uiState.value.openRoom).isNull()
        vm.viewModelScope.cancel()
    }

    @Test
    fun `a code, a whole link or a deep link all join, and anything else says so`() = runTest(dispatcher) {
        val gateway = FakeGateway()
        val vm = home(FakeIdentities(), gateway)
        runCurrent()
        listOf("abcd-1234", "${OnlineRoutes.INVITE_BASE}ABCD1234", "goa-companion://play/ABCD1234").forEach { text ->
            vm.setJoinText(text)
            vm.join()
            runCurrent()
        }
        assertThat(gateway.joins).containsExactly(CODE, CODE, CODE)
        vm.join("https://example.com/nothing-here")
        runCurrent()
        assertThat(vm.uiState.value.message).isEqualTo(OnlineWords.NOT_A_CODE)
        assertThat(gateway.joins).hasSize(3)
        vm.viewModelScope.cancel()
    }

    @Test
    fun `an invite link waits for the account, then joins once, even after the process died`() = runTest(dispatcher) {
        val gateway = FakeGateway()
        val saved = SavedStateHandle()
        val vm = home(FakeIdentities(null), gateway, OnlineHomeRoute(invite = CODE), saved)
        runCurrent()
        assertThat(gateway.joins).isEmpty()
        vm.setName("Ann")
        vm.createAccount()
        runCurrent()
        assertThat(gateway.joins).isEmpty()
        vm.recoveryCodeSaved()
        runCurrent()
        assertThat(gateway.joins).containsExactly(CODE)
        assertThat(vm.uiState.value.openRoom?.code).isEqualTo(CODE)
        vm.viewModelScope.cancel()

        val again = home(FakeIdentities(), gateway, OnlineHomeRoute(invite = CODE), saved)
        runCurrent()
        assertThat(gateway.joins).hasSize(1)
        again.viewModelScope.cancel()
    }

    @Test
    fun `the server being away is said in its words, and playing the computer still works`() = runTest(dispatcher) {
        val gateway = FakeGateway()
        gateway.games = OnlineResult.Failed()
        val vm = home(FakeIdentities(), gateway)
        runCurrent()
        assertThat(vm.uiState.value.message).isEqualTo(OnlineWords.UNREACHABLE)
        assertThat(vm.uiState.value.message).contains("Playing against the computer still works")
        vm.viewModelScope.cancel()
    }

    @Test
    fun `deleting the account forgets it on this device`() = runTest(dispatcher) {
        val identities = FakeIdentities()
        val vm = home(identities)
        runCurrent()
        vm.deleteAccount()
        runCurrent()
        assertThat(identities.identity).isNull()
        assertThat(vm.uiState.value.stage).isEqualTo(AccountStage.PICK_NAME)
        vm.viewModelScope.cancel()
    }

    // ---- Lobby ---------------------------------------------------------------------------------

    private class Lobby(val vm: OnlineLobbyViewModel, val gateway: FakeGateway, val connector: FakeConnector)

    private fun lobby(identities: FakeIdentities = FakeIdentities(), gateway: FakeGateway = FakeGateway()): Lobby {
        val connector = FakeConnector()
        val factory = fakeFactory()
        val vm = OnlineLobbyViewModel(
            OnlineLobbyRoute(CODE),
            gateway,
            connector,
            identities,
            catalog,
            maps,
            { factory },
            "android/test",
        )
        return Lobby(vm, gateway, connector)
    }

    @Test
    fun `the lobby joins, watches the room on a socket, and starts with the playable heroes`() = runTest(dispatcher) {
        val rig = lobby()
        runCurrent()
        val hello = rig.connector.hellos.single()
        assertThat(hello.token).isEqualTo("secret-token")
        assertThat(hello.client).isEqualTo("android/test")
        assertThat(hello.catalogVersion).isEqualTo(bundledCatalog.dataVersion)
        assertThat(rig.gateway.joins).containsExactly(CODE)
        assertThat(rig.connector.last.sent).containsExactly(ClientMessage.WatchRoom(CODE))
        val ui = rig.vm.uiState.value
        assertThat(ui.connected).isTrue()
        assertThat(ui.you).isEqualTo("acc")
        assertThat(ui.isHost).isTrue()
        assertThat(ui.playable).containsExactlyElementsIn(fakeFactory().playableHeroes)
        assertThat(rig.vm.heroes.value).containsKey("arien")
        rig.vm.viewModelScope.cancel()
    }

    @Test
    fun `seat, hero, ready and start go to the server, and only its answer changes the seats`() = runTest(dispatcher) {
        val rig = lobby()
        runCurrent()
        val socket = rig.connector.last
        rig.vm.pickHero("arien")
        runCurrent()
        assertThat(socket.said.last()).isEqualTo(ClientMessage.PickSeat(CODE, Team.RED, 0, "arien"))
        assertThat(rig.vm.uiState.value.yourSeat?.heroId).isNull()

        socket.feed(ServerMessage.Lobby(room(hero = { "arien" })))
        runCurrent()
        assertThat(rig.vm.uiState.value.yourSeat?.heroId).isEqualTo("arien")
        assertThat(rig.vm.uiState.value.heroes).doesNotContain("arien")

        rig.vm.setReady(true)
        runCurrent()
        assertThat(socket.said.last()).isEqualTo(ClientMessage.Ready(CODE, true))

        // Bob takes the other seat; once all are ready, the host can start.
        val full = room(filled = 2, hero = { listOf("arien", "brogan")[it] }, ready = { true })
        socket.feed(ServerMessage.Lobby(full))
        runCurrent()
        assertThat(rig.vm.uiState.value.canStart).isTrue()
        rig.vm.startGame()
        runCurrent()
        assertThat(socket.said.last()).isEqualTo(ClientMessage.Start(CODE))
        rig.vm.viewModelScope.cancel()
    }

    @Test
    fun `the game beginning opens it and stops watching`() = runTest(dispatcher) {
        val rig = lobby()
        runCurrent()
        rig.connector.last.feed(ServerMessage.Lobby(room(status = RoomStatus.STARTED, gameId = GAME)))
        runCurrent()
        assertThat(rig.vm.uiState.value.gameId).isEqualTo(GAME)
        rig.vm.viewModelScope.cancel()
    }

    @Test
    fun `a refusal is said, and the room stays on screen`() = runTest(dispatcher) {
        val rig = lobby()
        runCurrent()
        rig.connector.last.feed(ServerMessage.Refused(Refusal.ROOM_TAKEN))
        runCurrent()
        assertThat(rig.vm.uiState.value.message).isEqualTo(OnlineWords.refusal(Refusal.ROOM_TAKEN))
        assertThat(rig.vm.uiState.value.room).isNotNull()
        rig.vm.viewModelScope.cancel()
    }

    @Test
    fun `a room that couldn't be joined can be tried again`() = runTest(dispatcher) {
        val gateway = FakeGateway()
        gateway.joined = OnlineResult.Failed()
        val rig = lobby(gateway = gateway)
        runCurrent()
        assertThat(rig.vm.uiState.value.room).isNull()
        assertThat(rig.vm.uiState.value.message).isEqualTo(OnlineWords.UNREACHABLE)

        gateway.joined = OnlineResult.Ok(room())
        rig.vm.retry()
        runCurrent()
        assertThat(rig.vm.uiState.value.room).isNotNull()
        assertThat(rig.vm.uiState.value.connected).isTrue()
        rig.vm.viewModelScope.cancel()
    }

    @Test
    fun `leaving asks the server and goes back`() = runTest(dispatcher) {
        val rig = lobby()
        runCurrent()
        rig.vm.leave()
        runCurrent()
        assertThat(rig.gateway.leaves).containsExactly(CODE)
        assertThat(rig.vm.uiState.value.left).isTrue()
        rig.vm.viewModelScope.cancel()
    }

    // ---- Game ----------------------------------------------------------------------------------

    private class Game(val vm: OnlineGameViewModel, val connector: FakeConnector, val frames: GameFrames)

    private fun TestScope.game(
        frames: GameFrames = GameFrames(),
        greet: Boolean = true,
        identities: FakeIdentities = FakeIdentities(),
    ): Game {
        val connector = FakeConnector()
        if (greet) {
            connector.onOpen = { socket ->
                socket.feed(frames.start())
                socket.feed(frames.table(have = connector.hellos.last().resume.single().haveEvents))
            }
        }
        val vm = OnlineGameViewModel(
            OnlineGameRoute(GAME),
            connector,
            identities,
            catalog,
            maps,
            EditClock { currentTime },
            "android/test",
        )
        runCurrent()
        return Game(vm, connector, frames)
    }

    private val OnlineGameViewModel.play: PlayUi
        get() {
            val shown = when (val state = uiState.value) {
                is OnlineState.InGame -> state.game
                is OnlineState.Reconnecting -> state.game
                else -> error("no table: $state")
            }
            return (shown as GameUiState.Playing).play
        }

    @Test
    fun `Hello carries the token and asks to resume, and the existing table state comes back`() = runTest(dispatcher) {
        val rig = game()
        val hello = rig.connector.hellos.single()
        assertThat(hello.protocol).isEqualTo(OnlineProtocol.VERSION)
        assertThat(hello.token).isEqualTo("secret-token")
        assertThat(hello.resume.single().gameId).isEqualTo(GAME)
        assertThat(hello.resume.single().haveEvents).isEqualTo(0)
        assertThat(rig.vm.uiState.value).isInstanceOf(OnlineState.InGame::class.java)
        assertThat(rig.vm.play.table.mine).containsExactly("arien")
        rig.vm.viewModelScope.cancel()
    }

    @Test
    fun `what needs a whole game is off, and names, presence and the clock are on`() = runTest(dispatcher) {
        val frames = GameFrames()
        val rig = game(frames, greet = false)
        val socket = rig.connector.last
        socket.feed(frames.start())
        val deadline = currentTime + 42_000L
        val presence = mapOf("arien" to Presence.HERE, "brogan" to Presence.AWAY, "wasp" to Presence.COMPUTER)
        socket.feed(frames.table(clock = DecisionClock("arien", deadline), presence = presence))
        runCurrent()
        val play = rig.vm.play
        assertThat(play.coach).isFalse()
        assertThat(play.canTakeBack).isFalse()
        assertThat(play.canSuggest).isFalse()
        val online = play.online!!
        assertThat(online.players["arien"]).isEqualTo("Ann")
        assertThat(online.presence["brogan"]).isEqualTo(Presence.AWAY)
        assertThat(online.clockSeat).isEqualTo("arien")
        assertThat(online.clockSeconds).isEqualTo(42)
        assertThat(online.ranked).isTrue()
        advanceTimeBy(2_500L)
        assertThat(rig.vm.play.online!!.clockSeconds).isLessThan(42)
        rig.vm.viewModelScope.cancel()
    }

    @Test
    fun `an answer goes to the server with its ticket`() = runTest(dispatcher) {
        val rig = game()
        val decision = rig.vm.play.table.decision!!
        assertThat(rig.vm.play.status).isEqualTo(TurnStatus.YourTurn)
        val choice = decision.options.first()
        rig.vm.onAction(GameAction.Answer(choice))
        runCurrent()
        val sent = rig.connector.last.said.filterIsInstance<ClientMessage.Answer>().single()
        assertThat(sent.ticket).isEqualTo(decision.id)
        assertThat(sent.choice).isEqualTo(choice)
        rig.vm.viewModelScope.cancel()
    }

    @Test
    fun `take back, coach and pace do nothing online`() = runTest(dispatcher) {
        val rig = game()
        val before = rig.vm.play
        listOf(GameAction.TakeBack, GameAction.SetCoach(true), GameAction.AskSuggestions).forEach(rig.vm::onAction)
        runCurrent()
        assertThat(rig.vm.play.coach).isEqualTo(before.coach)
        assertThat(rig.vm.play.suggest).isNull()
        assertThat(rig.connector.last.said.filterIsInstance<ClientMessage.Answer>()).isEmpty()
        rig.vm.viewModelScope.cancel()
    }

    @Test
    fun `someone else's question shows who, never what`() = runTest(dispatcher) {
        val frames = GameFrames(you = "wasp")
        frames.answerUntil { it.seat != "wasp" && it.kind != DecisionKind.PLAY_CARD }
        val rig = game(frames)
        val status = rig.vm.play.status as TurnStatus.OtherPlayer
        assertThat(status.heroId).isEqualTo(frames.session.pending!!.seat)
        assertThat(status.name).isEqualTo(frames.players.first { it.seat == status.heroId }.name)
        assertThat(rig.vm.play.table.decision!!.options).isEmpty()
        assertThat(rig.vm.play.table.asking).isFalse()
        rig.vm.viewModelScope.cancel()
    }

    @Test
    fun `a dropped socket keeps the table, and comes back at once on resumeNow`() = runTest(dispatcher) {
        val rig = game()
        val table = rig.vm.play.table
        rig.connector.last.drop()
        runCurrent()
        val reconnecting = rig.vm.uiState.value as OnlineState.Reconnecting
        assertThat((reconnecting.game as GameUiState.Playing).play.table.map).isEqualTo(table.map)
        assertThat(rig.connector.connections).hasSize(1)

        rig.vm.resumeNow()
        runCurrent()
        assertThat(rig.connector.connections).hasSize(2)
        val held = rig.frames.session.events.size
        assertThat(rig.connector.hellos.last().resume.single().haveEvents).isEqualTo(held)
        assertThat(rig.vm.uiState.value).isInstanceOf(OnlineState.InGame::class.java)
        rig.vm.viewModelScope.cancel()
    }

    @Test
    fun `leaving tells the server before the screen goes`() = runTest(dispatcher) {
        val rig = game()
        val socket = rig.connector.last
        rig.vm.leave()
        runCurrent()
        assertThat(socket.said).contains(ClientMessage.Leave(GAME))
        assertThat(socket.closed).isTrue()
        rig.vm.viewModelScope.cancel()
    }

    @Test
    fun `clearing the ViewModel closes the socket`() = runTest(dispatcher) {
        val rig = game()
        rig.vm.viewModelScope.cancel()
        runCurrent()
        assertThat(rig.connector.last.closed).isTrue()
    }

    @Test
    fun `a voided game says why, and no result`() = runTest(dispatcher) {
        val rig = game()
        rig.connector.last.feed(ServerMessage.End(GAME, null, null, rated = false, voided = VoidReason.RULES_CHANGED))
        runCurrent()
        assertThat(rig.vm.play.over).isNull()
        assertThat(rig.vm.play.online!!.closed).isEqualTo(OnlineWords.voided(VoidReason.RULES_CHANGED))
        assertThat(rig.vm.play.online!!.rated).isFalse()
        rig.vm.viewModelScope.cancel()
    }

    @Test
    fun `a frame this build can't read asks for an update`() = runTest(dispatcher) {
        val rig = game()
        rig.connector.last.fail(SerializationException("Unknown frame"))
        runCurrent()
        assertThat(rig.vm.uiState.value).isEqualTo(OnlineState.Refused(Refusal.UPDATE_APP))
        rig.vm.viewModelScope.cancel()
    }

    @Test
    fun `no account here says so, rather than a table`() = runTest(dispatcher) {
        val rig = game(identities = FakeIdentities(null))
        assertThat(rig.vm.uiState.value).isEqualTo(OnlineState.Refused(Refusal.BAD_TOKEN))
        rig.vm.viewModelScope.cancel()
    }

    // ---- Leaderboard ---------------------------------------------------------------------------

    private val snapshot = LeaderboardSnapshot(
        schema = LeaderboardSnapshot.SCHEMA,
        generatedAt = 1_000,
        logSha256 = "x",
        matches = 12,
        eloConfig = 1,
        players = listOf(LeaderRow("acc-bob", "Bob", 1_620, 20, 14), LeaderRow("acc", "Ann", 1_540, 9, 5)),
        heroes = listOf(LeaderRow("arien", "Arien", 1_580, 30, 18)),
    )

    private class Cache(var text: String? = null) : LeaderboardCache {
        override suspend fun read() = text

        override suspend fun write(text: String) {
            this.text = text
        }
    }

    private fun board(
        files: Map<String, String>,
        cache: Cache = Cache(),
        identities: FakeIdentities = FakeIdentities(),
    ) = OnlineLeaderboardViewModel(
        LeaderboardRepository({ url -> files[url] }, cache, "https://host.example/game") { 1_000 + 12 * MINUTE },
        identities,
    )

    private val snapshotText = OnlineProtocol.json.encodeToString(LeaderboardSnapshot.serializer(), snapshot)

    @Test
    fun `the board loads from Pages, marks your row, and says how old it is`() = runTest(dispatcher) {
        val vm = board(mapOf(OnlineRoutes.LEADERBOARD_FILE to snapshotText))
        assertThat(vm.uiState.value.loading).isTrue()
        runCurrent()
        val ui = vm.uiState.value
        assertThat(ui.loading).isFalse()
        assertThat(ui.rows.map { it.name }).containsExactly("Bob", "Ann").inOrder()
        assertThat(ui.rows.single { it.isYou }.name).isEqualTo("Ann")
        assertThat(ui.status).isEqualTo("Updated 12 min ago")
        assertThat(ui.stale).isFalse()
        vm.viewModelScope.cancel()
    }

    @Test
    fun `the Heroes tab shows the heroes, and marks nobody`() = runTest(dispatcher) {
        val vm = board(mapOf(OnlineRoutes.LEADERBOARD_FILE to snapshotText))
        runCurrent()
        vm.selectTab(LeaderboardTab.HEROES)
        val ui = vm.uiState.value
        assertThat(ui.tab).isEqualTo(LeaderboardTab.HEROES)
        assertThat(ui.rows.map { it.name }).containsExactly("Arien")
        assertThat(ui.rows.none { it.isYou }).isTrue()
        vm.viewModelScope.cancel()
    }

    @Test
    fun `nothing reachable falls back to the last copy, and says it is one`() = runTest(dispatcher) {
        val vm = board(emptyMap(), Cache(snapshotText))
        runCurrent()
        val ui = vm.uiState.value
        assertThat(ui.rows).hasSize(2)
        assertThat(ui.stale).isTrue()
        assertThat(ui.status).contains("Last copy")
        vm.viewModelScope.cancel()
    }

    @Test
    fun `no copy anywhere says the board can't be reached, and a refresh tries again`() = runTest(dispatcher) {
        val files = mutableMapOf<String, String>()
        val vm = OnlineLeaderboardViewModel(
            LeaderboardRepository({ url -> files[url] }, Cache(), "https://host.example/game") { 1_000L },
            FakeIdentities(null),
        )
        runCurrent()
        assertThat(vm.uiState.value.empty).isEqualTo(LeaderboardPresenter.NO_BOARD)

        files[OnlineRoutes.LEADERBOARD_FILE] = snapshotText
        vm.refresh()
        runCurrent()
        assertThat(vm.uiState.value.rows).hasSize(2)
        assertThat(vm.uiState.value.rows.none { it.isYou }).isTrue()
        vm.viewModelScope.cancel()
    }

    // ---- Invites -------------------------------------------------------------------------------

    @Test
    fun `a deep link gives its code, read as people retype it, and nothing else is one`() {
        assertThat(onlineInviteCode("goa-companion://play/ABCD1234")).isEqualTo(CODE)
        assertThat(onlineInviteCode("GOA-COMPANION://play/abcd-1234/")).isEqualTo(CODE)
        assertThat(onlineInviteCode("goa-companion://play/ABCD1234?x=1")).isEqualTo(CODE)
        assertThat(onlineInviteCode("goa-companion://play/short")).isNull()
        assertThat(onlineInviteCode("https://quintond.github.io/goa-companion/#play=ABCD1234")).isNull()
        assertThat(onlineInviteCode("content://files/a.goasync")).isNull()
    }

    @Test
    fun `the clock reads as minutes and seconds`() {
        assertThat(clockText(42)).isEqualTo("0:42")
        assertThat(clockText(60)).isEqualTo("1:00")
        assertThat(clockText(-3)).isEqualTo("0:00")
        assertThat(spacedCode(CODE)).isEqualTo("ABCD 1234")
    }

    private companion object {
        const val MINUTE = 60_000L
    }
}
