package dev.quinton.goa.feature.board.play.puzzles

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import dev.quinton.goa.core.data.EditClock
import dev.quinton.goa.core.data.puzzle.PuzzleProgress
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
 * The puzzles with the real puzzle book, bundled map, catalog cards and portraits: the list, a
 * puzzle at its first question, the other side's replies, a miss with a nudge, the last nudge
 * pointing at the move, solved with the explanation, and the solution replayed. The puzzle screen's
 * states come from the real [PuzzleViewModel], driven on virtual time before the screen is drawn.
 * Light/dark, font scale 2 at 384 dp, landscape, as the play screenshots.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class PuzzleScreenshotTest {
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

    private fun need(vararg ids: String) =
        assumeTrue("Needs puzzles ${ids.toList()}", ids.all { id -> realPuzzleBook.puzzles.any { it.id == id } })

    // ---- The puzzle screen, from the real ViewModel ----------------------------------------

    /** A ViewModel driven on a test scheduler (the main dispatcher swapped only while it runs). */
    private class Driver(val vm: PuzzleViewModel, private val scheduler: TestCoroutineScheduler) {
        fun settle(ms: Long = 5_000L) {
            scheduler.advanceTimeBy(ms)
            scheduler.runCurrent()
        }

        fun ui(): PuzzleUi = (vm.uiState.value as PuzzleUiState.Ready).puzzle

        fun play(vararg moves: dev.quinton.goa.core.game.puzzles.Move) = moves.forEach { move ->
            vm.onAction(PuzzleAction.Answer(ui().table.choiceOf(move)))
            settle()
        }

        fun act(action: PuzzleAction) {
            vm.onAction(action)
            settle()
        }
    }

    private fun drive(
        id: String,
        nudges: Boolean = false,
        progress: Map<String, PuzzleProgress> = emptyMap(),
        steps: Driver.() -> Unit = {},
    ): PuzzleUi {
        val scheduler = TestCoroutineScheduler()
        val dispatcher = StandardTestDispatcher(scheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val vm = PuzzleViewModel(
                puzzleId = id,
                catalogRepository = FakeCatalogRepository(bundledCatalog),
                mapRepository = FakeBoardMapRepository(bundledMaps),
                settingsRepository = FakeSettingsRepository(AppSettings(puzzleNudges = nudges)),
                progressRepository = FakePuzzleProgressRepository(progress),
                book = { realPuzzleBook },
                clock = EditClock { 0L },
                engine = dispatcher,
            )
            return Driver(vm, scheduler).apply {
                settle()
                steps()
            }.ui()
        } finally {
            Dispatchers.resetMain()
        }
    }

    /** Builds the state first (the ViewModel on virtual time), then draws it. */
    private fun shot(mode: ThemeMode, fontScale: Float = 1f, needs: List<String>, state: () -> PuzzleUi) {
        need(*needs.toTypedArray())
        val puzzle = state()
        capture(mode, fontScale) { Puzzle(puzzle) }
    }

    @Composable
    private fun Puzzle(puzzle: PuzzleUi) {
        PuzzleScreen(
            state = PuzzleUiState.Ready(puzzle),
            onAction = {},
            onTap = { false },
            onNext = {},
            onList = {},
            onBack = {},
        )
    }

    /** Puzzle 1 at its first question: the goal, "What you know", the actions. */
    private fun start() = drive("p01")

    @Test fun puzzleStartDark() = shot(ThemeMode.DARK, needs = listOf("p01")) { start() }

    @Test fun puzzleStartLight() = shot(ThemeMode.LIGHT, needs = listOf("p01")) { start() }

    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    @Test
    fun puzzleStartW384DarkFontScale2() = shot(ThemeMode.DARK, 2f, needs = listOf("p01")) { start() }

    @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    @Test
    fun puzzleStartLandscapeDark() = shot(ThemeMode.DARK, needs = listOf("p01")) { start() }

    /** Two heroes to play at once: Wasp's card is down, Xargatha's hand is up (the intro folded). */
    @Test
    fun puzzleTwoHeroesDark() = shot(ThemeMode.DARK, needs = listOf("p17")) {
        drive("p17") { play(puzzleFile("p17").solution.first()) }
    }

    /** A tempting thrust at Arien: he defends, the reply is narrated line by line. */
    private fun missing(nudges: Boolean = true, narrating: Boolean = false) = drive("p14", nudges = nudges) {
        val wrong = puzzleFile("p14").wrongTries.first().moves
        wrong.dropLast(1).forEach { play(it) }
        vm.onAction(PuzzleAction.Answer(ui().table.choiceOf(wrong.last())))
        if (narrating) settle(REPLY_MS) else settle()
    }

    @Test fun puzzleReplyingDark() = shot(ThemeMode.DARK, needs = listOf("p14")) { missing(narrating = true) }

    /** The miss, gently, with Try again from before that move, and the first nudge. */
    @Test fun puzzleMissDark() = shot(ThemeMode.DARK, needs = listOf("p14")) { missing() }

    @Test fun puzzleMissLight() = shot(ThemeMode.LIGHT, needs = listOf("p14")) { missing() }

    /** Every nudge asked: the last names the move and outlines the space on the board. */
    private fun lastNudge() = drive("p34") {
        repeat(ui().info.nudges.size) { act(PuzzleAction.Nudge) }
    }

    @Test fun puzzleNudgeHintDark() = shot(ThemeMode.DARK, needs = listOf("p34")) { lastNudge() }

    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    @Test
    fun puzzleNudgeHintW384LightFontScale2() = shot(ThemeMode.LIGHT, 2f, needs = listOf("p34")) { lastNudge() }

    /** The first nudge, gentle, with Another nudge. */
    @Test
    fun puzzleNudgeLight() = shot(ThemeMode.LIGHT, needs = listOf("p17")) { drive("p17") { act(PuzzleAction.Nudge) } }

    /** Solved: the seal, the record, why it works with the rules. */
    private fun solved() = drive("p01", progress = mapOf("p01" to PuzzleProgress(attempts = 1, nudgesUsed = 1))) {
        puzzleFile("p01").solution.forEach { play(it) }
    }

    @Test fun puzzleSolvedDark() = shot(ThemeMode.DARK, needs = listOf("p01")) { solved() }

    @Test fun puzzleSolvedLight() = shot(ThemeMode.LIGHT, needs = listOf("p01")) { solved() }

    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    @Test
    fun puzzleSolvedW384DarkFontScale2() = shot(ThemeMode.DARK, 2f, needs = listOf("p01")) { solved() }

    @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    @Test
    fun puzzleSolvedLandscapeDark() = shot(ThemeMode.DARK, needs = listOf("p01")) { solved() }

    /** Show solution: step 4, Wasp's push on Arien outlined on the board. */
    private fun replay() = drive("p17") {
        act(PuzzleAction.ShowSolution)
        repeat(REPLAY_STEP) { act(PuzzleAction.ReplayNext) }
    }

    @Test fun puzzleReplayDark() = shot(ThemeMode.DARK, needs = listOf("p17")) { replay() }

    @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    @Test
    fun puzzleReplayLandscapeLight() = shot(ThemeMode.LIGHT, needs = listOf("p17")) { replay() }

    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    @Test
    fun puzzleReplayW384DarkFontScale2() = shot(ThemeMode.DARK, 2f, needs = listOf("p17")) { replay() }

    // ---- The list --------------------------------------------------------------------------

    private fun listProgress(): Map<String, PuzzleProgress> {
        val ids = realPuzzleBook.puzzles.map { it.id }
        return buildMap {
            ids.getOrNull(0)?.let { put(it, PuzzleProgress(solved = true, solvedAt = 1, attempts = 1)) }
            ids.getOrNull(1)?.let { put(it, PuzzleProgress(attempts = 3, nudgesUsed = 2, solutionShown = true)) }
        }
    }

    @Composable
    private fun ListScreen(progress: Map<String, PuzzleProgress> = listProgress()) {
        val vm = remember {
            PuzzleListViewModel(FakeCatalogRepository(bundledCatalog), FakePuzzleProgressRepository(progress)) {
                realPuzzleBook
            }
        }
        val state by vm.uiState.collectAsState()
        PuzzleListScreen(state, onAction = vm::onAction, onOpen = {}, onBack = {})
    }

    @Test fun puzzleListDark() = capture(ThemeMode.DARK) { ListScreen() }

    @Test fun puzzleListLight() = capture(ThemeMode.LIGHT) { ListScreen() }

    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    @Test
    fun puzzleListW384DarkFontScale2() = capture(ThemeMode.DARK, 2f) { ListScreen() }

    @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    @Test
    fun puzzleListLandscapeDark() = capture(ThemeMode.DARK) { ListScreen() }

    /** Every puzzle solved: the seal instead of Continue. */
    @Test
    fun puzzleListAllSolvedLight() = capture(ThemeMode.LIGHT) {
        ListScreen(
            realPuzzleBook.puzzles.associate {
                it.id to PuzzleProgress(solved = true, solvedAt = 1, attempts = 1)
            },
        )
    }

    @Test
    fun puzzleListEmptyDark() = capture(ThemeMode.DARK) {
        PuzzleListScreen(PuzzleListUiState.Empty, onAction = {}, onOpen = {}, onBack = {})
    }

    private companion object {
        const val IMAGE_TIMEOUT_MS = 10_000L

        /** Two reply lines in (each shows 0.7 s). */
        const val REPLY_MS = 1_500L

        const val REPLAY_STEP = 3
    }
}
