package dev.quinton.goa.web.engine

import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.Controller
import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.core.game.GameLength
import dev.quinton.goa.core.game.GameSetup
import dev.quinton.goa.core.game.PuzzleStatus
import dev.quinton.goa.core.game.SeatSetup
import dev.quinton.goa.core.model.match.Team
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/** The site serves the Android app's assets (`app/src/main/assets`) under the same paths. */
private fun shipped(path: String): String {
    val root = js("process.env.GOA_REPO_ROOT") as String? ?: error("GOA_REPO_ROOT is not set")
    val fs = js("require('fs')")
    return fs.readFileSync("$root/app/src/main/assets/$path", "utf8") as String
}

/**
 * The worker's side of play, driven exactly as the page drives it: every request and reply goes
 * through the JSON the worker posts ([EngineJson]), so what passes here passes the wire.
 */
class EngineHostTest {
    private val host = EngineHost { BundledData.load { shipped(it) } }
    private var ids = 0

    private suspend fun call(request: EngineRequest): EngineResponse {
        val sent = EngineJson.encodeToString(EngineMessage.serializer(), EngineMessage(++ids, request))
        val received = EngineJson.decodeFromString(EngineMessage.serializer(), sent)
        val reply = EngineReply(received.id, host.handle(received.request))
        val back = EngineJson.decodeFromString(
            EngineReply.serializer(),
            EngineJson.encodeToString(EngineReply.serializer(), reply),
        )
        assertEquals(received.id, back.id)
        return back.response
    }

    private suspend fun game(request: EngineRequest): GameSnapshot {
        val response = call(request)
        assertIs<EngineResponse.Game>(response, "$request answered $response")
        return response.game
    }

    private fun setup(human: String, level: AiLevel = AiLevel.EASY) = GameSetup(
        mapId = "forgotten-island",
        seats = listOf(
            SeatSetup("brogan", Team.RED, if (human == "brogan") Controller.Human else Controller.Ai(level)),
            SeatSetup("arien", Team.RED, if (human == "arien") Controller.Human else Controller.Ai(level)),
            SeatSetup("wasp", Team.BLUE, Controller.Ai(level)),
            SeatSetup("tigerclaw", Team.BLUE, Controller.Ai(level)),
        ),
        length = GameLength.QUICK,
        tieBreaker = Team.RED,
    )

    @Test
    fun infoSaysWhatTheEngineCanPlay() = runTest {
        val info = call(EngineRequest.Info)
        assertIs<EngineResponse.Info>(info)
        assertTrue("forgotten-island" in info.playableMaps)
        assertTrue(setOf("brogan", "arien", "wasp", "tigerclaw").all { it in info.playableHeroes })
    }

    @Test
    fun aGameIsPlayedThroughTheMessagesAndItsEventsAddUp() = runTest {
        var snapshot = game(EngineRequest.GameStart(setup("brogan")))
        assertEquals(0, snapshot.eventsFrom)
        assertEquals(false, snapshot.canUndo)
        val events = snapshot.events.toMutableList()
        var steps = 0
        while (steps < STEPS) {
            val decision = snapshot.pending ?: break
            snapshot = if (decision.seat == "brogan") {
                game(EngineRequest.GameAnswer(decision.id, decision.options.first()))
            } else {
                game(EngineRequest.GameComputer(decision.id))
            }
            assertTrue(snapshot.accepted)
            assertEquals(events.size, snapshot.eventsFrom, "each reply sends the events the page doesn't have")
            events += snapshot.events
            assertEquals(snapshot.eventCount, events.size)
            steps++
        }
        assertTrue(events.any { it is GameEvent.CardsRevealed }, "the game got past the first reveal")
        // The person's view: Brogan's hand is open, the others' are counts only (p.4).
        assertNotNull(snapshot.view.heroes.first { it.heroId == "brogan" }.hand)
        assertEquals(null, snapshot.view.heroes.first { it.heroId == "wasp" }.hand)

        // The save rebuilds the same game (a reload, or a fresh worker after cancelling the computer).
        val restored = game(EngineRequest.GameRestore(snapshot.saved))
        assertEquals(0, restored.eventsFrom)
        assertEquals(events, restored.events)
        assertEquals(snapshot.view, restored.view)
        assertEquals(snapshot.pending, restored.pending)
    }

    @Test
    fun aComputerAnswerIsOnlyForTheComputersQuestionAndAStaleOneChangesNothing() = runTest {
        val start = game(EngineRequest.GameStart(setup("brogan")))
        val first = assertNotNull(start.pending)
        val stale = game(EngineRequest.GameAnswer(first.id + 1000, first.options.first()))
        assertFalse(stale.accepted)
        assertEquals(start.pending, stale.pending)
        if (first.seat == "brogan") {
            assertFalse(
                game(EngineRequest.GameComputer(first.id)).accepted,
                "the computer never answers for the person",
            )
        }
    }

    @Test
    fun takeBackUndoesThePersonsLastAnswer() = runTest {
        var snapshot = game(EngineRequest.GameStart(setup("arien")))
        val nothing = game(EngineRequest.GameTakeBack)
        assertEquals(false, nothing.undone)
        var answered: Long? = null
        while (answered == null) {
            val decision = assertNotNull(snapshot.pending)
            snapshot = if (decision.seat == "arien") {
                answered = decision.id
                game(EngineRequest.GameAnswer(decision.id, decision.options.last()))
            } else {
                game(EngineRequest.GameComputer(decision.id))
            }
        }
        val back = game(EngineRequest.GameTakeBack)
        assertEquals(true, back.undone)
        assertEquals(0, back.eventsFrom, "after a take back the page starts its log over")
        assertEquals(answered, back.pending?.id)
    }

    @Test
    fun theReviewAndAnEarlierBoardComeBackOverTheWire() = runTest {
        var snapshot = game(EngineRequest.GameStart(setup("brogan")))
        repeat(REVIEW_STEPS) {
            val decision = snapshot.pending ?: return@repeat
            snapshot = if (decision.seat == "brogan") {
                game(EngineRequest.GameAnswer(decision.id, decision.options.first()))
            } else {
                game(EngineRequest.GameComputer(decision.id))
            }
        }
        val review = call(EngineRequest.GameReview(setOf("brogan"), Turn(1, 1)))
        assertIs<EngineResponse.Review>(review)
        assertTrue(review.reviews.isNotEmpty())
        assertEquals(1, review.reviews.first().round)
        assertNotNull(review.rewind, "the start of round 1 turn 1 is kept")
    }

    @Test
    fun aPuzzleIsSolvedWithTheSolversMovesAndItsSolutionReplays() = runTest {
        val list = call(EngineRequest.PuzzleList)
        assertIs<EngineResponse.Puzzles>(list)
        assertTrue(list.puzzles.size >= 40, "the book lists the playable puzzles")
        val first = list.puzzles.first()
        val started = call(EngineRequest.PuzzleStart(first.id))
        assertIs<EngineResponse.Puzzle>(started)
        var puzzle = started.puzzle
        assertEquals(PuzzleStatus.PLAYING, puzzle.status)
        // Every hand is open in a puzzle.
        assertTrue(puzzle.view.heroes.all { it.hand != null })
        var moves = 0
        while (puzzle.status == PuzzleStatus.PLAYING && moves < MAX_PUZZLE_MOVES) {
            val best = call(EngineRequest.PuzzleBest)
            assertIs<EngineResponse.Best>(best)
            val choice = assertNotNull(best.choice)
            val decision = assertNotNull(puzzle.pending)
            val played = call(EngineRequest.PuzzleAnswer(decision.id, choice))
            assertIs<EngineResponse.PuzzlePlayed>(played)
            assertNotNull(played.move)
            puzzle = played.puzzle
            moves++
        }
        assertEquals(PuzzleStatus.SOLVED, puzzle.status)

        val solution = call(EngineRequest.PuzzleSolution(first.id))
        assertIs<EngineResponse.Solution>(solution)
        assertEquals(moves + 1, solution.steps.size, "a step per move, then the goal met")
        assertEquals(null, solution.steps.last().decision)

        val restarted = call(EngineRequest.PuzzleRestart)
        assertIs<EngineResponse.Puzzle>(restarted)
        assertEquals(started.puzzle.view, restarted.puzzle.view)
    }

    @Test
    fun aRequestWithoutAGameFailsWithoutThrowing() = runTest {
        assertIs<EngineResponse.Failed>(call(EngineRequest.GameTakeBack))
        assertIs<EngineResponse.Failed>(call(EngineRequest.PuzzleUndo))
    }

    private companion object {
        const val STEPS = 120
        const val REVIEW_STEPS = 40
        const val MAX_PUZZLE_MOVES = 20
    }
}
