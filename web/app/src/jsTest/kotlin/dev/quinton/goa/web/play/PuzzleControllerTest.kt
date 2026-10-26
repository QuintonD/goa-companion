package dev.quinton.goa.web.play

import dev.quinton.goa.core.data.EditClock
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.model.catalog.CatalogFiles
import dev.quinton.goa.core.testing.FakePuzzleProgressRepository
import dev.quinton.goa.core.testing.FakeSettingsRepository
import dev.quinton.goa.feature.board.play.puzzles.PuzzleAction
import dev.quinton.goa.feature.board.play.puzzles.PuzzlePhase
import dev.quinton.goa.feature.board.play.puzzles.PuzzleUi
import dev.quinton.goa.feature.board.play.puzzles.PuzzleUiState
import dev.quinton.goa.web.engine.EngineRequest
import dev.quinton.goa.web.engine.EngineResponse
import dev.quinton.goa.web.play.data.EngineClient
import dev.quinton.goa.web.play.puzzles.PuzzleController
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * The web's puzzle against the real engine host (in process, through the worker's JSON): solving
 * with the solver's moves records the progress, a miss offers Try again from before the move, a
 * fresh worker gets the puzzle back, and Show solution replays it.
 */
class PuzzleControllerTest {
    private class Rig(val scope: TestScope) {
        val channels = HostChannels(scope.backgroundScope)
        val client = EngineClient { channels.open() }
        val progress = FakePuzzleProgressRepository()
        val settings = FakeSettingsRepository()
        val controller = PuzzleController(
            puzzleId = PUZZLE,
            scope = scope.backgroundScope,
            engine = client,
            catalog = { CatalogFiles.load(::shipped) },
            maps = {
                CatalogFiles.parseMapIndex(shipped(CatalogFiles.MAPS_INDEX_PATH))
                    .map { CatalogFiles.parseMap(it, shipped(CatalogFiles.mapPath(it))) }
            },
            settings = settings,
            progressRepository = progress,
            clock = EditClock { NOW },
        )

        val puzzle: PuzzleUi get() = assertIs<PuzzleUiState.Ready>(controller.state).puzzle

        suspend fun best(): Choice = assertNotNull(
            assertIs<EngineResponse.Best>(client.call(EngineRequest.PuzzleBest)).choice,
        )

        /** Lets the answer, the narrated replies and the hold run (not the 90 s idle nudge). */
        fun settle() {
            scope.runCurrent()
            scope.advanceTimeBy(SETTLE_MS)
            scope.runCurrent()
        }
    }

    @Test
    fun solvingWithTheSolversMovesIsRecordedAsAFirstTryWithNoNudges() = runTest {
        val rig = Rig(this)
        rig.controller.start()
        rig.settle()
        assertEquals(PuzzlePhase.SOLVING, rig.puzzle.phase)
        assertTrue(rig.puzzle.introOpen)
        var moves = 0
        while (rig.puzzle.phase != PuzzlePhase.SOLVED && moves < MAX_MOVES) {
            rig.controller.onAction(PuzzleAction.Answer(rig.best()))
            rig.settle()
            moves++
        }
        assertEquals(PuzzlePhase.SOLVED, rig.puzzle.phase)
        assertTrue(!rig.puzzle.introOpen, "What you know folds after the first move")
        val stored = assertNotNull(rig.progress.progress.value()[PUZZLE])
        assertTrue(stored.solved)
        assertEquals(NOW, stored.solvedAt)
        assertEquals(1, stored.attempts)
        assertEquals(0, stored.nudgesUsed)
        assertEquals(false, stored.solutionShown)
    }

    @Test
    fun aMissSaysWhyAndTryAgainGoesBackToBeforeTheMove() = runTest {
        val rig = Rig(this)
        rig.controller.start()
        rig.settle()
        val question = assertNotNull(rig.puzzle.table.decision)
        val best = rig.best()
        var missed = false
        for (option in question.options.filter { it != best }) {
            rig.controller.onAction(PuzzleAction.Answer(option))
            rig.settle()
            if (rig.puzzle.phase == PuzzlePhase.MISSED) {
                missed = true
                break
            }
            rig.controller.onAction(PuzzleAction.Restart)
            rig.settle()
        }
        assertTrue(missed, "some other first move loses the forced line")
        assertTrue(rig.puzzle.miss.orEmpty().startsWith("Not quite"))
        // Nudges are on by default: the first one comes with the miss.
        assertEquals(1, rig.puzzle.nudge?.level)
        rig.controller.onAction(PuzzleAction.TryAgain)
        rig.settle()
        assertEquals(PuzzlePhase.SOLVING, rig.puzzle.phase)
        assertEquals(question.id, rig.puzzle.table.decision?.id)
        assertEquals(1, rig.progress.progress.value()[PUZZLE]?.nudgesUsed)
    }

    @Test
    fun aFreshWorkerGetsThePuzzleAndTheMovesBack() = runTest {
        val rig = Rig(this)
        rig.controller.start()
        rig.settle()
        rig.controller.onAction(PuzzleAction.Answer(rig.best()))
        rig.settle()
        if (rig.puzzle.phase == PuzzlePhase.SOLVED) return@runTest // a one-move puzzle: nothing to restore
        val before = rig.puzzle.table.decision
        rig.client.reset()
        assertEquals(1, rig.channels.terminated)
        // The next answer finds a new worker: the puzzle is set up again with the first move replayed.
        val next = assertNotNull(before).options.first()
        rig.controller.onAction(PuzzleAction.Answer(next))
        rig.settle()
        assertEquals(2, rig.channels.opened)
        assertTrue(rig.puzzle.phase in setOf(PuzzlePhase.SOLVING, PuzzlePhase.MISSED, PuzzlePhase.SOLVED))
        assertTrue(rig.puzzle.log.isNotEmpty())
    }

    @Test
    fun showSolutionReplaysItStepByStepAndIsRemembered() = runTest {
        val rig = Rig(this)
        rig.controller.start()
        rig.settle()
        rig.controller.onAction(PuzzleAction.ShowSolution)
        rig.settle()
        val replay = assertNotNull(rig.puzzle.replay)
        assertTrue(replay.count > 1)
        assertTrue(replay.first)
        repeat(replay.count) { rig.controller.onAction(PuzzleAction.ReplayNext) }
        val last = assertNotNull(rig.puzzle.replay)
        assertTrue(last.last && last.solved, last.title)
        assertTrue(last.title.startsWith("Goal met"))
        assertEquals(true, rig.progress.progress.value()[PUZZLE]?.solutionShown)
        rig.controller.onAction(PuzzleAction.CloseReplay)
        assertEquals(null, rig.puzzle.replay)
    }

    /**
     * p01's first step: Brogan may move to one of three spaces, all in Lane Zone 2 one space from
     * him. VoiceOver read them (and a nudge's hint named them) as three identical "Lane Zone 2 ·
     * 1 space from Brogan"; each must now say what stands beside it.
     */
    @Test
    fun spacesThatReadAlikeSayWhatStandsBesideThem() = runTest {
        val rig = Rig(this)
        rig.controller.start()
        rig.settle()
        rig.controller.onAction(PuzzleAction.Answer(rig.best()))
        rig.settle()
        val table = rig.puzzle.table
        val spaces = assertNotNull(table.decision).options.filterIsInstance<Choice.Space>()
        assertTrue(spaces.size > 1, "a choice of spaces: ${table.decision?.options}")
        val labels = spaces.map(table::label)
        assertEquals(labels.size, labels.toSet().size, "labels must differ: $labels")
        assertTrue(labels.any { it.contains("next to Wasp") }, labels.toString())
    }

    private companion object {
        const val PUZZLE = "p01"
        const val NOW = 1_700_000_000_000L
        const val MAX_MOVES = 20
        const val SETTLE_MS = 5_000L
    }
}

/** The current value of a test fake's flow. */
private suspend fun <T> kotlinx.coroutines.flow.Flow<T>.value(): T = first()
