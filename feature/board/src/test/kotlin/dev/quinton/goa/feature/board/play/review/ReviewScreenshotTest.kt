package dev.quinton.goa.feature.board.play.review

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.quinton.goa.core.data.EditClock
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.model.settings.AppSettings
import dev.quinton.goa.core.model.settings.ThemeMode
import dev.quinton.goa.core.testing.FakeBoardMapRepository
import dev.quinton.goa.core.testing.FakeCatalogRepository
import dev.quinton.goa.core.testing.FakePuzzleProgressRepository
import dev.quinton.goa.core.testing.FakeSettingsRepository
import dev.quinton.goa.feature.board.BundledImages
import dev.quinton.goa.feature.board.bundledCatalog
import dev.quinton.goa.feature.board.bundledMaps
import dev.quinton.goa.feature.board.play.game.GameAction
import dev.quinton.goa.feature.board.play.game.GameScreen
import dev.quinton.goa.feature.board.play.game.GameUiState
import dev.quinton.goa.feature.board.play.game.PlaySheet
import dev.quinton.goa.feature.board.play.game.PlayUi
import dev.quinton.goa.feature.board.play.puzzles.PuzzleAction
import dev.quinton.goa.feature.board.play.puzzles.PuzzleScreen
import dev.quinton.goa.feature.board.play.puzzles.PuzzleUi
import dev.quinton.goa.feature.board.play.puzzles.PuzzleUiState
import dev.quinton.goa.feature.board.play.puzzles.PuzzleViewModel
import dev.quinton.goa.feature.board.play.puzzles.choiceOf
import dev.quinton.goa.feature.board.play.puzzles.puzzleFile
import dev.quinton.goa.feature.board.play.puzzles.realPuzzleBook
import dev.quinton.goa.feature.board.ui.cards.CARD_TAG
import dev.quinton.goa.feature.board.ui.cards.CONFIRM_TAG
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The game-state review (ADR-037) on a real game (the rulebook's first line-up played by the Easy
 * computer to round 2, the person playing Wasp) and on a puzzle: Now, Turns, the board of an
 * earlier turn. States come from the real ViewModels on virtual time, then the screen is drawn.
 * Light/dark, font scale 2 at 384 dp, landscape.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class ReviewScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Before fun images() = BundledImages.install(ApplicationProvider.getApplicationContext(), memoryCache = false)

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

    // ---- The game ------------------------------------------------------------------------

    /** Round 2, turn 2: two turns of round 2 and all of round 1 behind, someone has acted. */
    private fun midGame(
        rewind: Pair<Int, Int>? = null,
    ): PlayUi = reviewViewModel(reviewGameAt(round = 2, turn = 2)) { vm, settle ->
        vm.onAction(GameAction.Review(ReviewAction.Open(true)))
        settle()
        if (rewind != null) {
            vm.onAction(GameAction.Review(ReviewAction.Rewind(rewind.first, rewind.second)))
            settle()
        }
    }

    @Composable
    private fun Game(play: PlayUi, tab: ReviewTab) {
        GameScreen(
            state = GameUiState.Playing(play),
            onAction = {},
            onTap = { false },
            onDelete = {},
            onNewGame = {},
            onDone = {},
            onBack = {},
            initialSheet = PlaySheet.Review(tab),
        )
    }

    private fun game(mode: ThemeMode, tab: ReviewTab, fontScale: Float = 1f, rewind: Pair<Int, Int>? = null) {
        val play = midGame(rewind)
        capture(mode, fontScale) { Game(play, tab) }
    }

    /**
     * A card tapped in the review opens the card focus viewer over the sheet, view only; back returns
     * to the review.
     */
    @Test
    fun reviewCardFocusDark() {
        val play = midGame()
        compose.setContent { GoaTheme(themeMode = ThemeMode.DARK) { Game(play, ReviewTab.NOW) } }
        compose.waitForIdle()
        val look = SemanticsMatcher("opens a card") {
            it.config.getOrNull(SemanticsActions.OnClick)?.label == "Look at the card"
        }
        // The first card shown in the review sheet (the panel's hand, in the screen's window, is under it).
        val nodes = compose.onAllNodes(look).fetchSemanticsNodes()
        val inSheet = nodes.indexOfFirst { it.root != nodes.first().root && !it.boundsInRoot.isEmpty }
        compose.onAllNodes(look)[inSheet].performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(CARD_TAG).assertExists()
        compose.onNodeWithTag(CONFIRM_TAG).assertDoesNotExist()
        compose.onNodeWithText("GAME STATE").assertExists()
        compose.waitUntil(IMAGE_TIMEOUT_MS) { BundledImages.idle }
        compose.waitForIdle()
        captureScreenRoboImage()
        Espresso.pressBack()
        compose.waitForIdle()
        compose.onNodeWithTag(CARD_TAG).assertDoesNotExist()
        compose.onAllNodes(look)[inSheet].assertExists()
    }

    @Test fun reviewNowDark() = game(ThemeMode.DARK, ReviewTab.NOW)

    @Test fun reviewNowLight() = game(ThemeMode.LIGHT, ReviewTab.NOW)

    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    @Test
    fun reviewNowW384DarkFontScale2() = game(ThemeMode.DARK, ReviewTab.NOW, fontScale = 2f)

    @Test fun reviewTurnsDark() = game(ThemeMode.DARK, ReviewTab.TURNS)

    @Test fun reviewTurnsLight() = game(ThemeMode.LIGHT, ReviewTab.TURNS)

    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    @Test
    fun reviewTurnsW384DarkFontScale2() = game(ThemeMode.DARK, ReviewTab.TURNS, fontScale = 2f)

    @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    @Test
    fun reviewTurnsLandscapeDark() = game(ThemeMode.DARK, ReviewTab.TURNS)

    /** The board at the start of round 1's turn 3, read only. */
    @Test fun reviewRewindDark() = game(ThemeMode.DARK, ReviewTab.TURNS, rewind = 1 to 3)

    @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    @Test
    fun reviewRewindLandscapeLight() = game(ThemeMode.LIGHT, ReviewTab.TURNS, rewind = 1 to 3)

    // ---- A puzzle ------------------------------------------------------------------------

    /** "Into the Wall" played to its end with the stored solution: the turn's cards and lines. */
    private fun puzzle(): PuzzleUi {
        val id = "p17"
        assumeTrue("Needs $id", realPuzzleBook.puzzles.any { it.id == id })
        val scheduler = TestCoroutineScheduler()
        val dispatcher = StandardTestDispatcher(scheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val vm = PuzzleViewModel(
                puzzleId = id,
                catalogRepository = FakeCatalogRepository(bundledCatalog),
                mapRepository = FakeBoardMapRepository(bundledMaps),
                settingsRepository = FakeSettingsRepository(AppSettings(puzzleNudges = false)),
                progressRepository = FakePuzzleProgressRepository(),
                book = { realPuzzleBook },
                clock = EditClock { 0L },
                engine = dispatcher,
            )
            fun settle() {
                scheduler.advanceTimeBy(SETTLE_MS)
                scheduler.runCurrent()
            }
            fun ui() = (vm.uiState.value as PuzzleUiState.Ready).puzzle
            settle()
            puzzleFile(id).solution.forEach { move ->
                vm.onAction(PuzzleAction.Answer(ui().table.choiceOf(move)))
                settle()
            }
            vm.onAction(PuzzleAction.Review(ReviewAction.Open(true)))
            settle()
            return ui()
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun puzzleShot(mode: ThemeMode, tab: ReviewTab, fontScale: Float = 1f) {
        val p = puzzle()
        capture(mode, fontScale) {
            PuzzleScreen(
                state = PuzzleUiState.Ready(p),
                onAction = {},
                onTap = { false },
                onNext = {},
                onList = {},
                onBack = {},
                initialSheet = PlaySheet.Review(tab),
            )
        }
    }

    @Test fun reviewPuzzleTurnsDark() = puzzleShot(ThemeMode.DARK, ReviewTab.TURNS)

    @Test fun reviewPuzzleNowLight() = puzzleShot(ThemeMode.LIGHT, ReviewTab.NOW)

    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    @Test
    fun reviewPuzzleTurnsW384LightFontScale2() = puzzleShot(ThemeMode.LIGHT, ReviewTab.TURNS, fontScale = 2f)

    private companion object {
        const val IMAGE_TIMEOUT_MS = 10_000L
        const val SETTLE_MS = 5_000L
    }
}
