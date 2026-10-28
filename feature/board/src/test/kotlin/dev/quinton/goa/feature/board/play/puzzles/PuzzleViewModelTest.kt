package dev.quinton.goa.feature.board.play.puzzles

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.data.EditClock
import dev.quinton.goa.core.data.puzzle.PuzzleProgress
import dev.quinton.goa.core.game.puzzles.Move
import dev.quinton.goa.core.model.settings.AppSettings
import dev.quinton.goa.core.testing.FakeBoardMapRepository
import dev.quinton.goa.core.testing.FakeCatalogRepository
import dev.quinton.goa.core.testing.FakePuzzleProgressRepository
import dev.quinton.goa.core.testing.FakeSettingsRepository
import dev.quinton.goa.feature.board.bundledCatalog
import dev.quinton.goa.feature.board.bundledMaps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * The puzzle screen's ViewModel on the real puzzle book (core:game's EnginePuzzleBook on the real
 * engine and bundled catalog): every listed puzzle solved with its stored line through the table's
 * model, a wrong try missed and tried again, nudge timing on virtual time, nudges off, the replay,
 * and the progress written.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PuzzleViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val progress = FakePuzzleProgressRepository()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private val book = realPuzzleBook

    private fun TestScope.viewModel(id: String, nudges: Boolean = false, settings: FakeSettingsRepository? = null) =
        PuzzleViewModel(
            puzzleId = id,
            catalogRepository = FakeCatalogRepository(bundledCatalog),
            mapRepository = FakeBoardMapRepository(bundledMaps),
            settingsRepository = settings ?: FakeSettingsRepository(AppSettings(puzzleNudges = nudges)),
            progressRepository = progress,
            book = { book },
            clock = EditClock { NOW },
            engine = dispatcher,
        ).also { settle() }

    /** Lets an answer and the narrated replies play out (well under the idle nudge's 90 s). */
    private fun TestScope.settle() {
        advanceTimeBy(SETTLE_MS)
        runCurrent()
    }

    private fun PuzzleViewModel.ui(): PuzzleUi = (uiState.value as PuzzleUiState.Ready).puzzle

    private fun first(): String {
        assumeTrue("Needs a playable puzzle", book.puzzles.isNotEmpty())
        return book.puzzles.first().id
    }

    /** Answers the stored moves through the table's model, checking every option is shown somewhere. */
    private fun TestScope.play(vm: PuzzleViewModel, moves: List<Move>) {
        moves.forEach { move ->
            val ui = vm.ui()
            assertThat(ui.phase).isEqualTo(PuzzlePhase.SOLVING)
            val table = ui.table
            assertThat(table.asking).isTrue()
            val decision = table.decision!!
            assertThat(decision.seat).isIn(table.mine)
            val shown = table.spaceOptions.values + table.pieceOptions.values + table.cardOptions + table.buttonOptions
            assertThat(shown.toSet()).containsExactlyElementsIn(decision.options)
            vm.onAction(PuzzleAction.Answer(table.choiceOf(move)))
            settle()
        }
    }

    @Test
    fun `only the heroes you play are called You, the rest are the other side`() = runTest(dispatcher) {
        // Hands-on 2026-10-25: puzzle 1 called Xargatha "Red · You" (the engine seats both sides as human).
        val table = viewModel(first()).ui().table
        table.view.heroes.forEach { hero ->
            val expected = if (hero.heroId in table.mine) "You" else "The other side"
            assertThat(table.controllerLabel(hero)).isEqualTo(expected)
        }
        assertThat(table.view.heroes.map { it.heroId }.toSet()).isNotEqualTo(table.mine)
    }

    @Test
    fun `every listed puzzle is solved with its stored line, and the solve is saved`() = runTest(dispatcher) {
        assumeTrue(book.puzzles.isNotEmpty())
        book.puzzles.forEach { info ->
            val vm = viewModel(info.id)
            assertThat(vm.ui().info.id).isEqualTo(info.id)
            assertThat(vm.ui().total).isEqualTo(book.puzzles.size)
            assertThat(vm.ui().introOpen).isTrue()
            play(vm, puzzleFile(info.id).solution)
            assertThat(vm.ui().phase).isEqualTo(PuzzlePhase.SOLVED)
            assertThat(vm.ui().table.asking).isFalse()
            val saved = progress.state.value.getValue(info.id)
            assertThat(saved.solved).isTrue()
            assertThat(saved.solvedAt).isEqualTo(NOW)
            assertThat(saved.attempts).isEqualTo(1)
            assertThat(saved.solutionShown).isFalse()
        }
    }

    @Test
    fun `a wrong try is missed gently, tried again from before it, then solved`() = runTest(dispatcher) {
        val info = book.puzzles.firstOrNull { puzzleFile(it.id).wrongTries.isNotEmpty() }
        assumeTrue(info != null)
        val file = puzzleFile(info!!.id)
        val wrong = file.wrongTries.first()
        val vm = viewModel(info.id)
        play(vm, wrong.moves)
        val missed = vm.ui()
        assertThat(missed.phase).isEqualTo(PuzzlePhase.MISSED)
        assertThat(missed.miss).startsWith("Not quite")
        assertThat(missed.table.asking).isFalse()
        // Nudges off: no nudge comes by itself, even after a miss.
        assertThat(missed.nudge).isNull()

        vm.onAction(PuzzleAction.TryAgain)
        settle()
        assertThat(vm.ui().phase).isEqualTo(PuzzlePhase.SOLVING)
        assertThat(vm.ui().miss).isNull()
        assertThat(vm.ui().flow.fresh).isFalse() // taken back: nothing new to tell
        // Back to just before the move that missed: the moves before it stand; play the rest of the solution.
        val kept = wrong.moves.size - 1
        assertThat(wrong.moves.take(kept)).isEqualTo(file.solution.take(kept))
        play(vm, file.solution.drop(kept))
        assertThat(vm.ui().phase).isEqualTo(PuzzlePhase.SOLVED)
        // Two tries: the one that missed and the one after Try again.
        assertThat(progress.state.value.getValue(info.id).attempts).isEqualTo(2)
    }

    @Test
    fun `restart goes back to the starting position and counts a new try`() = runTest(dispatcher) {
        val id = first()
        val vm = viewModel(id)
        val start = vm.ui().table.decision
        play(vm, puzzleFile(id).solution.take(1))
        vm.onAction(PuzzleAction.Restart)
        settle()
        assertThat(vm.ui().phase).isEqualTo(PuzzlePhase.SOLVING)
        assertThat(vm.ui().table.decision?.prompt).isEqualTo(start?.prompt)
        assertThat(vm.ui().table.decision?.options).isEqualTo(start?.options)
        play(vm, puzzleFile(id).solution)
        assertThat(vm.ui().phase).isEqualTo(PuzzlePhase.SOLVED)
        assertThat(progress.state.value.getValue(id).attempts).isEqualTo(2)
    }

    @Test
    fun `with nudges on, a nudge comes after 90 s without progress and a correct move clears it`() =
        runTest(dispatcher) {
            val id = first()
            val vm = viewModel(id, nudges = true)
            advanceTimeBy(PuzzleTiming().idleNudgeMs - SETTLE_MS - 1)
            runCurrent()
            assertThat(vm.ui().nudge).isNull()
            advanceTimeBy(2)
            runCurrent()
            val nudge = vm.ui().nudge!!
            assertThat(nudge.level).isEqualTo(1)
            assertThat(nudge.text).isEqualTo(vm.ui().info.nudges.first())
            assertThat(progress.state.value.getValue(id).nudgesUsed).isEqualTo(1)
            // A correct move is progress: the nudge goes, and the clock starts again.
            val solution = puzzleFile(id).solution
            if (solution.size > 1) {
                play(vm, solution.take(1))
                assertThat(vm.ui().nudge).isNull()
                advanceTimeBy(PuzzleTiming().idleNudgeMs - SETTLE_MS - 1)
                runCurrent()
                assertThat(vm.ui().nudge).isNull()
                advanceTimeBy(SETTLE_MS)
                runCurrent()
                assertThat(vm.ui().nudge?.level).isEqualTo(2)
            }
        }

    @Test
    fun `with nudges on, a miss brings the next nudge`() = runTest(dispatcher) {
        val info = book.puzzles.firstOrNull { puzzleFile(it.id).wrongTries.isNotEmpty() }
        assumeTrue(info != null)
        val vm = viewModel(info!!.id, nudges = true)
        play(vm, puzzleFile(info.id).wrongTries.first().moves)
        assertThat(vm.ui().phase).isEqualTo(PuzzlePhase.MISSED)
        assertThat(vm.ui().nudge?.level).isEqualTo(1)
    }

    @Test
    fun `nudges get stronger when asked, and the last one points at the move`() = runTest(dispatcher) {
        val id = first()
        val vm = viewModel(id)
        val count = vm.ui().info.nudges.size
        repeat(count) { i ->
            vm.onAction(PuzzleAction.Nudge)
            settle()
            assertThat(vm.ui().nudge?.level).isEqualTo(i + 1)
        }
        val last = vm.ui()
        assertThat(last.nudge!!.more).isFalse()
        assertThat(last.nudge!!.text).isEqualTo(last.info.nudges.last())
        // The hint is a winning first move (the stored line's, or another that wins too).
        val hint = last.table.hint
        assertThat(hint).isNotNull()
        assertThat(hint).isIn(last.table.decision!!.options)
        assertThat(last.nudge!!.hint).isEqualTo(last.table.label(hint!!))
        assertThat(progress.state.value.getValue(id).nudgesUsed).isEqualTo(count)
        // Answering with it keeps the puzzle on the winning line.
        vm.onAction(PuzzleAction.Answer(hint))
        settle()
        assertThat(vm.ui().phase).isAnyOf(PuzzlePhase.SOLVING, PuzzlePhase.SOLVED)
        assertThat(vm.ui().table.hint).isNull()
    }

    @Test
    fun `with nudges off none comes by itself, and the switch turns them on`() = runTest(dispatcher) {
        val id = first()
        val settings = FakeSettingsRepository(AppSettings(puzzleNudges = false))
        val vm = viewModel(id, settings = settings)
        advanceTimeBy(PuzzleTiming().idleNudgeMs * 3)
        runCurrent()
        assertThat(vm.ui().nudge).isNull()
        assertThat(vm.ui().nudgesOn).isFalse()
        vm.onAction(PuzzleAction.SetNudges(true))
        runCurrent()
        assertThat(settings.state.value.puzzleNudges).isTrue()
        assertThat(vm.ui().nudgesOn).isTrue()
        advanceTimeBy(PuzzleTiming().idleNudgeMs + 1)
        runCurrent()
        assertThat(vm.ui().nudge?.level).isEqualTo(1)
        vm.onAction(PuzzleAction.DismissNudge)
        runCurrent()
        assertThat(vm.ui().nudge).isNull()
    }

    @Test
    fun `show solution replays the line step by step and is remembered`() = runTest(dispatcher) {
        val id = first()
        val vm = viewModel(id)
        vm.onAction(PuzzleAction.ShowSolution)
        settle()
        val replay = vm.ui().replay!!
        val moves = puzzleFile(id).solution
        assertThat(replay.index).isEqualTo(0)
        assertThat(replay.afterSolve).isFalse()
        // One step per player move, then the goal met. (Another winning line may be shorter or longer.)
        assertThat(replay.count).isAtLeast(2)
        assertThat(vm.ui().table.asking).isFalse()
        assertThat(vm.ui().table.selected).isNotNull()
        repeat(replay.count - 1) {
            vm.onAction(PuzzleAction.ReplayNext)
            runCurrent()
        }
        val end = vm.ui().replay!!
        assertThat(end.last).isTrue()
        assertThat(end.solved).isTrue()
        assertThat(end.title).contains(vm.ui().info.goal.text)
        vm.onAction(PuzzleAction.ReplayBack)
        runCurrent()
        assertThat(vm.ui().replay!!.index).isEqualTo(replay.count - 2)
        assertThat(progress.state.value.getValue(id).solutionShown).isTrue()
        vm.onAction(PuzzleAction.CloseReplay)
        runCurrent()
        assertThat(vm.ui().replay).isNull()
        // The live puzzle is untouched: still at its first question.
        assertThat(vm.ui().phase).isEqualTo(PuzzlePhase.SOLVING)
        play(vm, moves)
        assertThat(vm.ui().phase).isEqualTo(PuzzlePhase.SOLVED)
    }

    @Test
    fun `the other side's replies are narrated before the board catches up`() = runTest(dispatcher) {
        assumeTrue(book.puzzles.isNotEmpty())
        var narrated = 0
        // Every wrong try and every solution: the other side's replies show line by line, then the board.
        book.puzzles.forEach { info ->
            val file = puzzleFile(info.id)
            (file.wrongTries.map { it.moves } + listOf(file.solution)).forEach { moves ->
                val vm = viewModel(info.id)
                moves.forEach { move ->
                    vm.onAction(PuzzleAction.Answer(vm.ui().table.choiceOf(move)))
                    runCurrent()
                    advanceTimeBy(PuzzleTiming().replyLineMs + 1)
                    runCurrent()
                    if (vm.ui().phase == PuzzlePhase.REPLYING) {
                        narrated++
                        assertThat(vm.ui().replying!!.lines).isNotEmpty()
                        assertThat(vm.ui().table.asking).isFalse()
                        assertThat(vm.ui().table.decision).isNull()
                    }
                    settle()
                    assertThat(vm.ui().phase).isNotEqualTo(PuzzlePhase.REPLYING)
                }
            }
        }
        assertThat(narrated).isGreaterThan(0)
    }

    @Test
    fun `an unknown puzzle id says so`() = runTest(dispatcher) {
        val vm = viewModel("p99-unknown")
        assertThat(vm.uiState.value).isEqualTo(PuzzleUiState.Unavailable(NoPuzzle.MISSING))
    }

    @Test
    fun `the list shows progress, Continue on the first unsolved, filters, and reset`() = runTest(dispatcher) {
        assumeTrue(book.puzzles.isNotEmpty())
        val first = book.puzzles.first().id
        progress.state.value = mapOf(first to PuzzleProgress(solved = true, solvedAt = 1, attempts = 2, nudgesUsed = 1))
        val vm = PuzzleListViewModel(FakeCatalogRepository(bundledCatalog), progress) { book }
        backgroundScope.launch(dispatcher) { vm.uiState.collect {} }
        runCurrent()
        val ready = vm.uiState.value as PuzzleListUiState.Ready
        assertThat(ready.total).isEqualTo(book.puzzles.size)
        assertThat(ready.solved).isEqualTo(1)
        assertThat(ready.rows.map { it.info.number }).isEqualTo((1..book.puzzles.size).toList())
        assertThat(ready.rows.first().progress.attempts).isEqualTo(2)
        assertThat(ready.next?.info?.id).isEqualTo(book.puzzles.getOrNull(1)?.id)
        val difficulty = ready.difficulties.first()
        vm.onAction(PuzzleListAction.ToggleDifficulty(difficulty))
        runCurrent()
        val filtered = vm.uiState.value as PuzzleListUiState.Ready
        assertThat(filtered.rows.all { it.info.difficulty == difficulty }).isTrue()
        assertThat(filtered.total).isEqualTo(book.puzzles.size)
        vm.onAction(PuzzleListAction.ResetProgress)
        runCurrent()
        val reset = vm.uiState.value as PuzzleListUiState.Ready
        assertThat(reset.solved).isEqualTo(0)
        assertThat(reset.next?.info?.id).isEqualTo(first)
    }

    private companion object {
        const val NOW = 1_000L
        const val SETTLE_MS = 5_000L
    }
}
