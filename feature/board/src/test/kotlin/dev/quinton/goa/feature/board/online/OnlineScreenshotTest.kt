package dev.quinton.goa.feature.board.online

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.data.game.AiPace
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.game.Controller
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionContext
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.GameSession
import dev.quinton.goa.core.game.WinReason
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.settings.ThemeMode
import dev.quinton.goa.core.online.LeaderRow
import dev.quinton.goa.core.online.LeaderboardSnapshot
import dev.quinton.goa.core.online.MyGame
import dev.quinton.goa.core.online.Presence
import dev.quinton.goa.core.online.Refusal
import dev.quinton.goa.core.online.SeatPlayer
import dev.quinton.goa.core.online.VoidReason
import dev.quinton.goa.feature.board.BundledImages
import dev.quinton.goa.feature.board.boardHeroes
import dev.quinton.goa.feature.board.bundledCatalog
import dev.quinton.goa.feature.board.bundledMaps
import dev.quinton.goa.feature.board.play.fakeFactory
import dev.quinton.goa.feature.board.play.fakeSetup
import dev.quinton.goa.feature.board.play.game.GameOverUi
import dev.quinton.goa.feature.board.play.game.GameUiState
import dev.quinton.goa.feature.board.play.game.HeroResult
import dev.quinton.goa.feature.board.play.game.OnlineUi
import dev.quinton.goa.feature.board.play.game.PlayUi
import dev.quinton.goa.feature.board.play.game.TurnStatus
import dev.quinton.goa.feature.board.play.online.AccountStage
import dev.quinton.goa.feature.board.play.online.LobbyUi
import dev.quinton.goa.feature.board.play.online.OnlineHomeUi
import dev.quinton.goa.feature.board.play.online.OnlineState
import dev.quinton.goa.feature.board.play.online.OnlineWords
import dev.quinton.goa.feature.board.play.online.leaderboard.LeaderboardLoad
import dev.quinton.goa.feature.board.play.online.leaderboard.LeaderboardOrigin
import dev.quinton.goa.feature.board.play.online.leaderboard.LeaderboardPresenter
import dev.quinton.goa.feature.board.play.online.leaderboard.LeaderboardTab
import dev.quinton.goa.feature.board.play.online.leaderboard.LeaderboardUi
import dev.quinton.goa.feature.board.play.table.TableUi
import dev.quinton.goa.feature.board.play.table.logLines
import dev.quinton.goa.feature.board.play.table.turnLog
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Online games on screen (ADR-056), with the bundled map, cards and portraits: the home, the recovery-code gate, the
 * lobby, a game with its players and clock, the connection lost, another person's turn, the result, a voided game and
 * the leaderboards. Light and dark; font scale 2 at 384 dp for the screens with the most to read.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class OnlineScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val heroes = bundledCatalog.boardHeroes()
    private val cards = bundledCatalog.heroes.flatMap { it.allCards }.associateBy { it.id }
    private val mapNames = bundledMaps.associate { it.id to it.name }

    @Before fun images() = BundledImages.install(ApplicationProvider.getApplicationContext(), memoryCache = false)

    private fun capture(
        mode: ThemeMode,
        fontScale: Float = 1f,
        screen: Boolean = false,
        content: @Composable () -> Unit,
    ) {
        compose.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale)) {
                GoaTheme(themeMode = mode) { content() }
            }
        }
        compose.waitForIdle()
        compose.waitUntil(IMAGE_TIMEOUT_MS) { BundledImages.idle }
        compose.waitForIdle()
        if (screen) captureScreenRoboImage() else compose.onRoot().captureRoboImage()
    }

    // ---- Home ------------------------------------------------------------------------------------

    private val pickName = OnlineHomeUi(stage = AccountStage.PICK_NAME, name = "Ann")
    private val gate = OnlineHomeUi(stage = AccountStage.SAVE_CODE, name = "Ann", recoveryCode = RECOVERY)
    private val ready = OnlineHomeUi(
        stage = AccountStage.READY,
        name = "Ann",
        // As the presenter sorts them: your turn, waiting, finished.
        games = listOf(
            MyGame(
                GAME,
                CODE,
                "forgotten-island",
                listOf(SeatPlayer("arien", "acc", "Ann", Team.RED), SeatPlayer("wasp", "acc-bob", "Bob", Team.BLUE)),
                asked = listOf("arien"),
            ),
            MyGame(
                "game-2",
                "EFGH5678",
                "forgotten-island",
                listOf(
                    SeatPlayer("brogan", "acc", "Ann", Team.RED),
                    SeatPlayer("sabina", "acc-cid", "Cid", Team.RED),
                    SeatPlayer("tigerclaw", "acc-bob", "Bob", Team.BLUE),
                    SeatPlayer("xargatha", "acc-dee", "Dee", Team.BLUE),
                ),
                asked = listOf("tigerclaw"),
            ),
            MyGame(
                "game-3",
                "JKLM9012",
                "forgotten-island",
                listOf(SeatPlayer("wasp", "acc", "Ann", Team.RED), SeatPlayer("arien", "acc-bob", "Bob", Team.BLUE)),
                over = true,
            ),
        ),
        you = "acc",
    )

    @Composable
    private fun Home(state: OnlineHomeUi) = OnlineHomeScreen(state, mapNames, OnlineHomeActions(), heroes = heroes)

    @Test fun onlineHomePickNameDark() = capture(ThemeMode.DARK) { Home(pickName) }

    @Test fun onlineHomePickNameLight() = capture(ThemeMode.LIGHT) { Home(pickName) }

    @Test fun onlineHomeDark() = capture(ThemeMode.DARK) { Home(ready) }

    @Test fun onlineHomeLight() = capture(ThemeMode.LIGHT) { Home(ready) }

    @Test
    fun onlineHomeServerAwayDark() = capture(ThemeMode.DARK) { Home(ready.copy(message = OnlineWords.UNREACHABLE)) }

    /** Too short a code sent with the keyboard's Go: Join stays off, and the field says what a code is. */
    @Test
    fun onlineHomeJoinInvalidDark() {
        compose.setContent { GoaTheme(themeMode = ThemeMode.DARK) { Home(ready.copy(joinText = "7KQ2")) } }
        compose.onNode(hasSetTextAction() and hasText("7KQ2")).performImeAction()
        compose.waitForIdle()
        compose.onNodeWithText(OnlineWords.NOT_A_CODE).assertIsDisplayed()
        compose.waitUntil(IMAGE_TIMEOUT_MS) { BundledImages.idle }
        compose.onRoot().captureRoboImage()
    }

    @Test fun onlineCodeGateDark() = capture(ThemeMode.DARK) { Home(gate) }

    @Test fun onlineCodeGateLight() = capture(ThemeMode.LIGHT) { Home(gate) }

    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    @Test
    fun onlineCodeGateW384DarkFontScale2() = capture(ThemeMode.DARK, 2f) { Home(gate) }

    // ---- Lobby -----------------------------------------------------------------------------------

    private val lobby = LobbyUi(
        code = CODE,
        room = room(
            size = 2,
            filled = 3,
            hero = { listOf("arien", "brogan", null)[it] },
            ready = { it == 1 },
            ranked = true,
        ),
        you = "acc",
        playable = listOf("arien", "brogan", "wasp", "tigerclaw", "xargatha", "sabina"),
        connected = true,
    )

    @Composable
    private fun Lobby(state: LobbyUi, picking: Boolean = false, qr: Boolean = false) = OnlineLobbyScreen(
        state,
        heroes,
        mapNames,
        OnlineLobbyActions(),
        initialPicking = picking,
        initialQr = qr,
    )

    @Test fun onlineLobbyDark() = capture(ThemeMode.DARK) { Lobby(lobby) }

    @Test fun onlineLobbyLight() = capture(ThemeMode.LIGHT) { Lobby(lobby) }

    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    @Test
    fun onlineLobbyW384DarkFontScale2() = capture(ThemeMode.DARK, 2f) { Lobby(lobby) }

    @Test
    fun onlineLobbyHeroPickerDark() = capture(ThemeMode.DARK, screen = true) { Lobby(lobby, picking = true) }

    @Test fun onlineLobbyQrDark() = capture(ThemeMode.DARK, screen = true) { Lobby(lobby, qr = true) }

    /** Every seat taken, every hero chosen, everyone ready: the host's one bronze step is Start the game. */
    @Test
    fun onlineLobbyAllReadyDark() = capture(ThemeMode.DARK) {
        Lobby(
            lobby.copy(
                room = room(
                    size = 2,
                    filled = 4,
                    hero = { listOf("arien", "brogan", "wasp", "tigerclaw")[it] },
                    ready = { true },
                    ranked = true,
                ),
            ),
        )
    }

    /** A guest who hasn't chosen a hero yet: their one bronze step is Choose your hero. */
    @Test
    fun onlineLobbyGuestNoHeroLight() = capture(ThemeMode.LIGHT) { Lobby(lobby.copy(you = "acc-cid")) }

    // ---- The table -------------------------------------------------------------------------------

    private val seats = setOf("arien", "brogan", "wasp", "tigerclaw")

    private fun session(until: (Decision) -> Boolean): GameSession {
        val session = fakeFactory().newGame(fakeSetup(humans = seats))
        var guard = 0
        while (guard++ < MAX_ANSWERS) {
            val pending = checkNotNull(session.pending)
            if (until(pending)) return session
            session.answer(pending.id, pending.options.first())
        }
        error("never got there")
    }

    private fun stub(decision: Decision) = decision.copy(
        id = 0,
        prompt = "",
        sourceCardId = null,
        options = emptyList(),
        context = DecisionContext(),
    )

    private val players = mapOf("arien" to "Ann", "brogan" to "Bob", "wasp" to "Cid", "tigerclaw" to "Dee")

    private fun online(
        clockSeat: String? = "arien",
        seconds: Int? = 42,
        rated: Boolean? = null,
        closed: String? = null,
        ranked: Boolean = true,
    ) = OnlineUi(
        players = players,
        presence = mapOf(
            "arien" to Presence.HERE,
            "brogan" to Presence.AWAY,
            "wasp" to Presence.HERE,
            "tigerclaw" to Presence.COMPUTER,
        ),
        clockSeat = clockSeat,
        clockSeconds = seconds,
        ranked = ranked,
        rated = rated,
        closed = closed,
    )

    private fun table(session: GameSession, mine: String, decision: Decision?, asking: Boolean) = TableUi(
        map = bundledMaps.first { it.id == "forgotten-island" },
        view = session.view(mine),
        heroes = heroes,
        cards = cards,
        mine = setOf(mine),
        decision = decision,
        asking = asking,
        selected = null,
        bottomTeam = Team.RED,
        turnLog = turnLog(session.events),
        players = players,
    )

    private fun play(
        session: GameSession,
        mine: String,
        decision: Decision?,
        status: TurnStatus,
        asking: Boolean,
        online: OnlineUi = online(),
        over: GameOverUi? = null,
    ) = PlayUi(
        table = table(session, mine, decision, asking),
        status = status,
        recent = emptyList(),
        log = logLines(session.events),
        coach = false,
        pace = AiPace.NORMAL,
        takeBacks = 0,
        canTakeBack = false,
        over = over,
        online = online,
    )

    private fun yourCard() = session { it.kind == DecisionKind.PLAY_CARD && it.seat == "arien" }.let { session ->
        play(session, "arien", session.pending, TurnStatus.YourTurn, asking = true)
    }

    private fun someoneElse(): PlayUi {
        val session = session { it.seat == "wasp" && it.kind != DecisionKind.PLAY_CARD }
        return play(
            session,
            "arien",
            stub(session.pending!!),
            TurnStatus.OtherPlayer("wasp", "Cid"),
            asking = false,
            online = online(clockSeat = "wasp", seconds = 8),
        )
    }

    private val overUi = GameOverUi(
        winner = Team.RED,
        reason = WinReason.THRONE,
        youWon = true,
        rounds = 6,
        heroes = listOf(
            HeroResult("arien", Team.RED, Controller.Human, 5, 3, 7, 1, 12),
            HeroResult("brogan", Team.RED, Controller.Human, 4, 1, 5, 2, 9),
            HeroResult("wasp", Team.BLUE, Controller.Human, 4, 2, 4, 3, 8),
            HeroResult("tigerclaw", Team.BLUE, Controller.Human, 3, 0, 6, 2, 7),
        ),
        takeBacks = 0,
        nudges = 0,
        rethinks = 0,
        coach = false,
    )

    @Composable
    private fun Game(state: OnlineState) = OnlineGameScreen(state, {}, { false }, {}, {}, {})

    private fun inGame(play: PlayUi) = OnlineState.InGame(GameUiState.Playing(play))

    @Test fun onlineGameClockDark() = capture(ThemeMode.DARK) { Game(inGame(remember { yourCard() })) }

    @Test fun onlineGameClockLight() = capture(ThemeMode.LIGHT) { Game(inGame(remember { yourCard() })) }

    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    @Test
    fun onlineGameW384DarkFontScale2() = capture(ThemeMode.DARK, 2f) { Game(inGame(remember { yourCard() })) }

    @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    @Test
    fun onlineGameLandscapeDark() = capture(ThemeMode.DARK) { Game(inGame(remember { yourCard() })) }

    @Test
    fun onlineGameReconnectingDark() = capture(ThemeMode.DARK) {
        Game(OnlineState.Reconnecting(GameUiState.Playing(remember { yourCard() })))
    }

    @Test
    fun onlineGameReconnectingLight() = capture(ThemeMode.LIGHT) {
        Game(OnlineState.Reconnecting(GameUiState.Playing(remember { yourCard() })))
    }

    @Test fun onlineGameOtherPlayerDark() = capture(ThemeMode.DARK) { Game(inGame(remember { someoneElse() })) }

    @Test fun onlineGameOtherPlayerLight() = capture(ThemeMode.LIGHT) { Game(inGame(remember { someoneElse() })) }

    @Test fun onlineConnectingDark() = capture(ThemeMode.DARK) { Game(OnlineState.Connecting()) }

    @Test
    fun onlineServerAwayDark() = capture(ThemeMode.DARK) { Game(OnlineState.Connecting(OnlineWords.UNREACHABLE)) }

    @Test
    fun onlineRefusedDark() = capture(ThemeMode.DARK) { Game(OnlineState.Refused(Refusal.UPDATE_APP)) }

    private fun finished(online: OnlineUi, over: GameOverUi? = overUi): PlayUi {
        val session = session { true }
        return play(session, "arien", null, TurnStatus.Over, asking = false, online = online, over = over)
    }

    @Test
    fun onlineGameOverRatedDark() = capture(ThemeMode.DARK) {
        Game(inGame(remember { finished(online(null, null, rated = true)) }))
    }

    @Test
    fun onlineGameOverLostLight() = capture(ThemeMode.LIGHT) {
        val lost = remember {
            finished(online(null, null, rated = false, ranked = false), overUi.copy(youWon = false))
        }
        Game(inGame(lost))
    }

    @Test
    fun onlineGameVoidedDark() = capture(ThemeMode.DARK) {
        val words = OnlineWords.voided(VoidReason.RULES_CHANGED)
        Game(inGame(remember { finished(online(null, null, rated = false, closed = words), null) }))
    }

    // ---- Leaderboard -----------------------------------------------------------------------------

    private val snapshot = LeaderboardSnapshot(
        schema = LeaderboardSnapshot.SCHEMA,
        generatedAt = 0,
        logSha256 = "x",
        matches = 40,
        eloConfig = 1,
        players = listOf(
            LeaderRow("acc-bob", "Bob", 1_620, 20, 14),
            LeaderRow("acc", "Ann", 1_540, 9, 5),
            LeaderRow("acc-cid", "Former player", 1_490, 12, 5),
            LeaderRow("acc-dee", "Dee", 1_472, 15, 6),
            LeaderRow("acc-eve", "Eve", 1_455, 8, 3),
        ),
        heroes = listOf(
            LeaderRow("arien", "Arien", 1_580, 30, 18),
            LeaderRow("brogan", "Brogan", 1_515, 26, 14),
            LeaderRow("wasp", "Wasp", 1_470, 22, 9),
            LeaderRow("tigerclaw", "Tigerclaw", 1_462, 19, 8),
        ),
    )

    private fun board(tab: LeaderboardTab, origin: LeaderboardOrigin, age: Long = 12 * MINUTE): LeaderboardUi =
        LeaderboardPresenter.present(LeaderboardLoad(snapshot, origin, age), tab, "acc")

    @Composable
    private fun Board(state: LeaderboardUi) = OnlineLeaderboardScreen(
        state,
        {},
        {},
        {},
        portraits = bundledCatalog.heroes.associate { it.id to it.portrait },
    )

    @Test
    fun onlineLeaderboardPlayersDark() = capture(ThemeMode.DARK) {
        Board(board(LeaderboardTab.PLAYERS, LeaderboardOrigin.PAGES))
    }

    @Test
    fun onlineLeaderboardPlayersLight() = capture(ThemeMode.LIGHT) {
        Board(board(LeaderboardTab.PLAYERS, LeaderboardOrigin.PAGES))
    }

    @Test
    fun onlineLeaderboardHeroesDark() = capture(ThemeMode.DARK) {
        Board(board(LeaderboardTab.HEROES, LeaderboardOrigin.PAGES))
    }

    @Test
    fun onlineLeaderboardStaleDark() = capture(ThemeMode.DARK) {
        Board(board(LeaderboardTab.PLAYERS, LeaderboardOrigin.LAST_COPY, 3 * DAY))
    }

    @Test
    fun onlineLeaderboardEmptyLight() = capture(ThemeMode.LIGHT) { Board(LeaderboardPresenter.present(null)) }

    // ---- What TalkBack hears, and what isn't there ---------------------------------------------------

    @Test
    fun `the clock and presence have words`() {
        compose.setContent { GoaTheme(themeMode = ThemeMode.DARK) { Game(inGame(remember { yourCard() })) } }
        compose.onNode(hasContentDescription("Your clock: 42 seconds left")).assertExists()
        compose.onNode(hasContentDescription("Ann (you), playing Arien: here, on the clock")).assertExists()
        compose.onNode(hasContentDescription("Bob, playing Brogan: away", substring = true)).assertExists()
        compose.onNode(hasContentDescription("the computer answers", substring = true)).assertExists()
    }

    @Test
    fun `take back, coach and pace aren't offered online, and leaving asks first`() {
        var left = 0
        compose.setContent {
            GoaTheme(themeMode = ThemeMode.DARK) {
                OnlineGameScreen(inGame(remember { yourCard() }), {}, { false }, { left++ }, {}, {})
            }
        }
        compose.onNodeWithContentDescription("Take back").assertDoesNotExist()
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Coach").assertDoesNotExist()
        compose.onNodeWithText("COMPUTER SPEED").assertDoesNotExist()
        compose.onNodeWithText("Flip the board").assertIsDisplayed()
        compose.onNodeWithText("Leave this game").performClick()
        compose.onNodeWithText("your team still wins or loses", substring = true).assertIsDisplayed()
        assertThat(left).isEqualTo(0)
        compose.onNodeWithText("Leave game").performClick()
        assertThat(left).isEqualTo(1)
    }

    @Test
    fun `the result says whether it counted, with no report, rematch or new game`() {
        compose.setContent {
            GoaTheme(themeMode = ThemeMode.DARK) {
                Game(inGame(remember { finished(online(null, null, rated = true)) }))
            }
        }
        compose.onNodeWithText("Your team won").assertIsDisplayed()
        compose.onNodeWithText("Rated", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Rematch").assertDoesNotExist()
        compose.onNodeWithText("New game").assertDoesNotExist()
        compose.onNodeWithText("Coach off", substring = true).assertDoesNotExist()
        // Pinned under the page, in view without scrolling.
        compose.onNodeWithText("Done").assertIsDisplayed()
    }

    @Test
    fun `the code gate has no way off it but saying it is saved`() {
        var saved = 0
        compose.setContent {
            GoaTheme(themeMode = ThemeMode.DARK) {
                OnlineHomeScreen(gate, mapNames, OnlineHomeActions(onCodeSaved = { saved++ }))
            }
        }
        compose.onNodeWithText(RECOVERY).assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").assertDoesNotExist()
        compose.onNodeWithText("Create room").assertDoesNotExist()
        compose.onNodeWithText("I've saved it").performClick()
        assertThat(saved).isEqualTo(1)
    }

    private companion object {
        const val IMAGE_TIMEOUT_MS = 20_000L
        const val MAX_ANSWERS = 200
        const val MINUTE = 60_000L
        const val DAY = 24 * 60 * MINUTE
        const val RECOVERY = "7KQ2-M4XN-P9RD-3VTF"
    }
}
