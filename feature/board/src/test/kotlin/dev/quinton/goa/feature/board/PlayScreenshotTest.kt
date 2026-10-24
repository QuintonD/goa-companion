package dev.quinton.goa.feature.board

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import dev.quinton.goa.core.data.EditClock
import dev.quinton.goa.core.data.game.PlayOptions
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.AnswerResult
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.CoachNote
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.core.game.GameFactoryProvider
import dev.quinton.goa.core.game.GameSession
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.position.position
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.settings.ThemeMode
import dev.quinton.goa.core.navigation.NewGameRoute
import dev.quinton.goa.core.testing.FakeBoardMapRepository
import dev.quinton.goa.core.testing.FakeCatalogRepository
import dev.quinton.goa.core.testing.FakeDraftRecordRepository
import dev.quinton.goa.core.testing.FakeMatchRepository
import dev.quinton.goa.core.testing.FakeSavedGameRepository
import dev.quinton.goa.core.testing.FakeSettingsRepository
import dev.quinton.goa.feature.board.play.game.Activity
import dev.quinton.goa.feature.board.play.game.CoachPrompt
import dev.quinton.goa.feature.board.play.game.GameOverUi
import dev.quinton.goa.feature.board.play.game.Interlude
import dev.quinton.goa.feature.board.play.game.LogContent
import dev.quinton.goa.feature.board.play.game.PlayCallbacks
import dev.quinton.goa.feature.board.play.game.PlayScreen
import dev.quinton.goa.feature.board.play.game.PlayUiState
import dev.quinton.goa.feature.board.play.game.summarize
import dev.quinton.goa.feature.board.play.newgame.NewGameAction
import dev.quinton.goa.feature.board.play.newgame.NewGameScreen
import dev.quinton.goa.feature.board.play.newgame.NewGameViewModel
import dev.quinton.goa.feature.board.play.table.CardReader
import dev.quinton.goa.feature.board.play.table.HeroDetails
import dev.quinton.goa.feature.board.play.table.TableCatalog
import dev.quinton.goa.feature.board.play.table.TableModel
import dev.quinton.goa.feature.board.play.table.TableState
import dev.quinton.goa.feature.board.setup.NewBoardAction
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Play against the computer with the real engine, catalog, cards and map: the game table for every
 * decision kind (positions from seeded games, or built with the engine's PositionBuilder), the
 * computer at work, the reveal, the end of a round, the coach, the log and the dashboards, the
 * result, and New game. Light/dark, font scale 2 at 384 dp and landscape, like the sandbox's.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class PlayScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val catalog = TableCatalog.of(bundledCatalog)
    private val map = bundledMaps.first { it.id == PlayTestData.MAP }

    @Before fun images() = BundledImages.install(ApplicationProvider.getApplicationContext())

    private fun capture(mode: ThemeMode, fontScale: Float = 1f, content: @Composable () -> Unit) {
        compose.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale)) {
                GoaTheme(themeMode = mode) { content() }
            }
        }
        compose.waitForIdle()
        compose.waitUntil(IMAGE_TIMEOUT_MS) { BundledImages.idle }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage()
    }

    // ---- Positions -----------------------------------------------------------------------

    /** The table as the deciding seat sees it, the player answering for [humans] (default: that seat). */
    private fun table(session: GameSession, humans: Set<String>? = null): TableState {
        val pending = session.pending
        val seat = pending?.seat
        return TableModel.build(map, session.view(seat), pending, catalog, humans ?: setOfNotNull(seat))
    }

    private fun kind(kind: DecisionKind): GameSession {
        val session = DecisionKindScan.find(kind, humans = setOf("wasp", "xargatha", "arien", "brogan"))
        assumeTrue("No $kind decision with the playable heroes", session != null)
        return session!!
    }

    private fun playing(
        table: TableState,
        activity: Activity? = null,
        interlude: Interlude? = null,
        coach: CoachPrompt? = null,
        over: GameOverUi? = null,
        log: List<String> = emptyList(),
    ) = PlayUiState.Playing(
        table = table,
        options = PlayOptions(),
        activity = activity,
        interlude = interlude,
        coach = coach,
        canTakeBack = true,
        takeBacks = 0,
        log = log,
        announcement = null,
        over = over,
    )

    @Composable
    private fun Play(state: PlayUiState) = PlayScreen(state, PlayCallbacks())

    private fun decision(kind: DecisionKind, mode: ThemeMode = ThemeMode.DARK, fontScale: Float = 1f) {
        val table = table(kind(kind))
        capture(mode, fontScale) { Play(playing(table)) }
    }

    // ---- Every decision kind -------------------------------------------------------------

    @Test fun tablePlayCardDark() = decision(DecisionKind.PLAY_CARD)

    @Test fun tablePlayCardLight() = decision(DecisionKind.PLAY_CARD, ThemeMode.LIGHT)

    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    @Test
    fun tablePlayCardW384DarkFontScale2() = decision(DecisionKind.PLAY_CARD, fontScale = 2f)

    @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    @Test
    fun tablePlayCardLandscapeDark() = decision(DecisionKind.PLAY_CARD)

    @Test fun tableActFirstDark() = decision(DecisionKind.ACT_FIRST)

    @Test fun tableRespawnDark() = decision(DecisionKind.RESPAWN)

    @Test fun tableChooseActionDark() = decision(DecisionKind.CHOOSE_ACTION)

    @Test fun tableChooseActionLight() = decision(DecisionKind.CHOOSE_ACTION, ThemeMode.LIGHT)

    @Test fun tableChooseSpaceDark() = decision(DecisionKind.CHOOSE_SPACE)

    @Test fun tableChooseSpaceLight() = decision(DecisionKind.CHOOSE_SPACE, ThemeMode.LIGHT)

    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    @Test
    fun tableChooseSpaceW384DarkFontScale2() = decision(DecisionKind.CHOOSE_SPACE, fontScale = 2f)

    @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    @Test
    fun tableChooseSpaceLandscapeDark() = decision(DecisionKind.CHOOSE_SPACE)

    @Test fun tableChooseUnitDark() = decision(DecisionKind.CHOOSE_UNIT)

    @Test fun tableChooseCardDark() = decision(DecisionKind.CHOOSE_CARD)

    @Test fun tableDefendDark() = decision(DecisionKind.DEFEND)

    @Test fun tableDefendLight() = decision(DecisionKind.DEFEND, ThemeMode.LIGHT)

    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    @Test
    fun tableDefendW384DarkFontScale2() = decision(DecisionKind.DEFEND, fontScale = 2f)

    @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    @Test
    fun tableDefendLandscapeDark() = decision(DecisionKind.DEFEND)

    @Test fun tableChooseOptionDark() = decision(DecisionKind.CHOOSE_OPTION)

    @Test fun tableConfirmDark() = decision(DecisionKind.CONFIRM)

    @Test fun tableChooseNumberDark() = decision(DecisionKind.CHOOSE_NUMBER)

    @Test fun tableRemoveMinionDark() = decision(DecisionKind.REMOVE_MINION)

    @Test fun tableMinionReturnDark() = decision(DecisionKind.MINION_RETURN)

    @Test fun tableUpgradeCardDark() = decision(DecisionKind.UPGRADE_CARD)

    @Test fun tableKeepUpgradeDark() = decision(DecisionKind.KEEP_UPGRADE)

    /**
     * Clear (p.13): no playable hero makes tokens yet, so the position is built: Arien resolving an
     * attack card next to a rock token chooses Clear, then which token to remove.
     */
    @Test
    fun tableChooseTokenDark() {
        val session = position(PlayTestData.engine, PlayTestData.MAP) {
            clearMinions()
            hero("arien", Team.BLUE, Hex(3, -8)) { played("arien-red-1") }
            hero("wasp", Team.RED, Hex(5, -9))
            token("token_rock", Hex(4, -8))
            phase = PositionPhase.Resolving(next = "arien")
        }
        val action = session.pending
        assumeTrue("Clear offered", action?.options?.contains(Choice.Action(ActionType.CLEAR)) == true)
        check(session.answer(action!!.id, Choice.Action(ActionType.CLEAR)) is AnswerResult.Accepted)
        check(session.pending?.kind == DecisionKind.CHOOSE_TOKEN)
        val table = table(session)
        capture(ThemeMode.DARK) { Play(playing(table)) }
    }

    // ---- The computer, the reveal, the end of a round, the coach ---------------------------

    /** The computer is choosing (and taking a while); what it did last is still shown. */
    @Test
    fun tableComputerThinkingDark() {
        val session = kind(DecisionKind.CHOOSE_SPACE)
        val seat = session.pending!!.seat
        val table = TableModel.build(map, session.view("brogan"), session.pending, catalog, setOf("brogan"))
        val activity = Activity(
            hero = table.hero(seat),
            prompt = session.pending!!.prompt,
            thinking = true,
            waitingForOthers = false,
            last = session.events.takeLast(2).map { it.text },
        )
        capture(ThemeMode.DARK) { Play(playing(table, activity = activity)) }
    }

    /** The player has played; the others play facedown (p.7). */
    @Test
    fun tableWaitingForOthersLight() {
        val session = PlayTestData.engine.newGame(
            PlayTestData.firstGame(human = setOf("wasp", "xargatha", "arien", "brogan")),
        )
        val first = session.pending!!
        session.answer(first.id, first.options.first())
        val table = TableModel.build(map, session.view("wasp"), session.pending, catalog, setOf("wasp"))
        val activity =
            Activity(
                table.hero(session.pending!!.seat),
                null,
                thinking = false,
                waitingForOthers = true,
                last = emptyList(),
            )
        capture(ThemeMode.LIGHT) { Play(playing(table, activity = activity)) }
    }

    /** The reveal: every card in initiative order, waiting for Continue. */
    @Test
    fun tableRevealDark() {
        val table = table(kind(DecisionKind.CHOOSE_ACTION), setOf("wasp"))
        val reveal = Interlude.Reveal(table.initiative, emptyList())
        capture(ThemeMode.DARK) { Play(playing(table, interlude = reveal)) }
    }

    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    @Test
    fun tableRevealW384LightFontScale2() {
        val table = table(kind(DecisionKind.CHOOSE_ACTION), setOf("wasp"))
        val reveal = Interlude.Reveal(table.initiative, emptyList())
        capture(ThemeMode.LIGHT, 2f) { Play(playing(table, interlude = reveal)) }
    }

    /** The end of round 1: its steps as they happened, and the level ups with their items. */
    @Test
    fun tableRoundEndDark() {
        val session = kind(DecisionKind.KEEP_UPGRADE)
        val events = session.events
        val from = events.indexOfLast { it is GameEvent.Note && it.text.startsWith("End of round") }
        val lines = events.drop(from.coerceAtLeast(0)).map { it.text }
        val table = table(session, setOf("wasp"))
        val levelled = events.drop(from.coerceAtLeast(0)).filterIsInstance<GameEvent.LevelledUp>().map { it.heroId }
        val end = Interlude.RoundEnd(table.view.round, lines, table.heroes.filter { it.id in levelled })
        capture(ThemeMode.DARK) { Play(playing(table, interlude = end)) }
    }

    /** The coach's nudge before a choice: Rethink or Choose anyway. */
    @Test
    fun tableCoachDark() {
        val session = kind(DecisionKind.CHOOSE_ACTION)
        val table = table(session)
        val decision = session.pending!!
        val prompt = CoachPrompt(
            decision.id,
            decision.options.last(),
            "Hold",
            CoachNote(
                "Brogan can still reach you this turn.",
                "Holding leaves you next to him with no defense card to spare (p.14).",
            ),
        )
        capture(ThemeMode.DARK) { Play(playing(table, coach = prompt)) }
    }

    @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    @Test
    fun tableCoachLandscapeLight() {
        val session = kind(DecisionKind.CHOOSE_ACTION)
        val table = table(session)
        val decision = session.pending!!
        val prompt = CoachPrompt(
            decision.id,
            decision.options.last(),
            "Hold",
            CoachNote("Brogan can still reach you this turn.", "Holding leaves you next to him (p.14)."),
        )
        capture(ThemeMode.LIGHT) { Play(playing(table, coach = prompt)) }
    }

    // ---- Sheets ----------------------------------------------------------------------------

    @Test
    fun tableLogDark() {
        val session = kind(DecisionKind.UPGRADE_CARD)
        capture(ThemeMode.DARK) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                LogContent(session.events.map { it.text }, onDone = {})
            }
        }
    }

    /** A hero's dashboard: played, resolved by Turn slot, discard, items (open information, p.4). */
    @Test
    fun tableHeroSheetDark() {
        val session = kind(DecisionKind.REMOVE_MINION)
        val table = table(session, setOf("wasp"))
        capture(ThemeMode.DARK) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                HeroDetails(table.hero("wasp")!!, onRead = {})
            }
        }
    }

    @Test
    fun tableCardReaderLight() {
        val card = catalog.card("arien-red-2b")!!
        capture(ThemeMode.LIGHT) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) { CardReader(card) }
        }
    }

    // ---- Game over -------------------------------------------------------------------------

    private fun finished(): PlayUiState.Playing {
        val session = PlayTestData.engine.newGame(
            PlayTestData.firstGame(human = setOf("wasp", "xargatha", "arien", "brogan")),
        )
        val player = PlayTestData.Busy(5)
        while (true) {
            val d = session.pending ?: break
            session.answer(d.id, player.choose(d))
        }
        val table = TableModel.build(map, session.view("wasp"), null, catalog, setOf("wasp"))
        val view = session.view("wasp")
        val (heroes, pushes) = summarize(session.events, table.heroes)
        val over = GameOverUi(
            winner = view.winner,
            reason = view.winReason,
            story = session.events.filterIsInstance<GameEvent.GameOver>().lastOrNull()?.text,
            humanTeam = Team.RED,
            rounds = view.round,
            heroes = heroes,
            pushes = pushes,
            takeBacks = 2,
            coachOn = true,
            turningPoints = listOf(
                CoachNote(
                    "Arien reached the Battle Zone first and won the Minion Battle.",
                    "Minions decide the push at the end of the round (p.8).",
                    round = 2,
                    turn = 4,
                ),
            ),
        )
        return playing(table, over = over)
    }

    @Test fun gameOverDark() = finished().let { capture(ThemeMode.DARK) { Play(it) } }

    @Test fun gameOverLight() = finished().let { capture(ThemeMode.LIGHT) { Play(it) } }

    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    @Test
    fun gameOverW384DarkFontScale2() = finished().let { capture(ThemeMode.DARK, 2f) { Play(it) } }

    @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    @Test
    fun gameOverLandscapeDark() = finished().let { capture(ThemeMode.DARK) { Play(it) } }

    @Test
    fun gameUnplayableLight() = capture(ThemeMode.LIGHT) {
        Play(PlayUiState.Unplayable("g", "This game was saved with another version of the rules.", otherRules = true))
    }

    // ---- New game --------------------------------------------------------------------------

    private fun newGame(red: List<String> = emptyList(), blue: List<String> = emptyList()) = NewGameViewModel(
        NewGameRoute(red, blue),
        SavedStateHandle(),
        FakeCatalogRepository(bundledCatalog),
        FakeBoardMapRepository(bundledMaps),
        FakeDraftRecordRepository(),
        FakeMatchRepository(),
        FakeSettingsRepository(),
        GameFactoryProvider { PlayTestData.engine },
        FakeSavedGameRepository(),
        EditClock { 0L },
        Random(4),
        Dispatchers.Unconfined,
    )

    @Composable
    private fun NewGame(vm: NewGameViewModel) {
        val state by vm.uiState.collectAsState()
        NewGameScreen(state, onAction = vm::onAction, onStart = {}, onBack = {})
    }

    private fun lineup() = newGame().also { vm ->
        listOf("wasp", "arien", "xargatha").forEach { vm.onAction(NewGameAction.Lineup(NewBoardAction.Tap(it))) }
    }

    private fun options() = newGame(listOf("wasp", "xargatha"), listOf("arien", "brogan")).also {
        it.onAction(NewGameAction.Next)
    }

    /** Nothing picked: the four heroes ready to play first, the rest "coming later". */
    @Test fun newGameEmptyDark() = capture(ThemeMode.DARK) { NewGame(remember { newGame() }) }

    @Test fun newGameLineupDark() = capture(ThemeMode.DARK) { NewGame(remember { lineup() }) }

    @Test fun newGameLineupLight() = capture(ThemeMode.LIGHT) { NewGame(remember { lineup() }) }

    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    @Test
    fun newGameLineupW384DarkFontScale2() = capture(ThemeMode.DARK, 2f) { NewGame(remember { lineup() }) }

    @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    @Test
    fun newGameLineupLandscapeDark() = capture(ThemeMode.DARK) { NewGame(remember { lineup() }) }

    @Test fun newGameOptionsDark() = capture(ThemeMode.DARK) { NewGame(remember { options() }) }

    @Test fun newGameOptionsLight() = capture(ThemeMode.LIGHT) { NewGame(remember { options() }) }

    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    @Test
    fun newGameOptionsW384DarkFontScale2() = capture(ThemeMode.DARK, 2f) { NewGame(remember { options() }) }

    @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    @Test
    fun newGameOptionsLandscapeDark() = capture(ThemeMode.DARK) { NewGame(remember { options() }) }

    /** Advanced: a level per computer seat; 1 v 1 carries the p.20 note. */
    @Test
    fun newGameAdvancedLight() = capture(ThemeMode.LIGHT) {
        NewGame(
            remember {
                newGame(listOf("wasp"), listOf("brogan")).also {
                    it.onAction(NewGameAction.Next)
                    it.onAction(NewGameAction.Advanced(true))
                }
            },
        )
    }

    private companion object {
        const val IMAGE_TIMEOUT_MS = 10_000L
    }
}
