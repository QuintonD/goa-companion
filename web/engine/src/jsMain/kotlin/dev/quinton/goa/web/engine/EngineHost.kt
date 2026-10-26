package dev.quinton.goa.web.engine

import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.AiPlayer
import dev.quinton.goa.core.game.AnswerResult
import dev.quinton.goa.core.game.Coach
import dev.quinton.goa.core.game.CoachAnalysis
import dev.quinton.goa.core.game.Controller
import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.core.game.GameSession
import dev.quinton.goa.core.game.PuzzleSession
import dev.quinton.goa.core.game.ai.AiCoach
import dev.quinton.goa.core.game.ai.AiPlayers
import dev.quinton.goa.core.game.engine.GameEngine
import dev.quinton.goa.core.game.puzzles.EnginePuzzleBook
import dev.quinton.goa.core.model.board.BoardMap
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.feature.board.play.game.humans
import dev.quinton.goa.feature.board.play.game.mergedView
import dev.quinton.goa.feature.board.play.puzzles.openView
import dev.quinton.goa.feature.board.play.puzzles.play
import dev.quinton.goa.feature.board.play.puzzles.solution
import dev.quinton.goa.feature.board.play.review.pushesWon
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The engine side of the web app's play (ADR-043 "Addendum (play, W4)"): what the phone's
 * GameViewModel and PuzzleViewModel do on their engine thread, behind [EngineRequest]s. Runs in the
 * engine Web Worker (`:web:worker`) and, for tests, directly on Node. [load] reads the bundled
 * catalog and maps once, on the first request.
 *
 * The computer players are the phone's: the same strengths and seeds ([aiFor]), so a game replays the
 * same on both; the coach and the puzzle book run on the same engine as the game.
 */
class EngineHost(private val load: suspend () -> Pair<Catalog, List<BoardMap>>) {
    private val lock = Mutex()
    private var engine: GameEngine? = null
    private var coach: Coach? = null
    private var book: EnginePuzzleBook? = null

    private var game: GameSession? = null

    /** How many of the game's events the page has (it keeps them; later replies send the rest). */
    private var sent = 0

    private var puzzle: PuzzleSession? = null
    private var coachLook: Pair<Long, CoachAnalysis?>? = null

    /** Answers [request]; never throws (a failure is [EngineResponse.Failed]). One request at a time. */
    @Suppress("TooGenericExceptionCaught") // the engine's own failures (require, error) become Failed
    suspend fun handle(request: EngineRequest): EngineResponse = lock.withLock {
        try {
            answer(request)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            EngineResponse.Failed(e.message ?: e::class.simpleName ?: "engine error")
        }
    }

    private suspend fun engine(): GameEngine = engine ?: load().let { (catalog, maps) ->
        GameEngine(catalog, maps).also { engine = it }
    }

    @Suppress("CyclomaticComplexMethod")
    private suspend fun answer(request: EngineRequest): EngineResponse = when (request) {
        EngineRequest.Info -> engine().let {
            EngineResponse.Info(it.engineVersion, it.playableHeroes, it.playableMaps)
        }

        is EngineRequest.GameStart -> started(engine().newGame(request.setup))

        is EngineRequest.GameRestore -> started(engine().restore(request.saved))

        is EngineRequest.GameAnswer -> withGame { g ->
            val result = g.answer(request.decisionId, request.choice)
            snapshot(g, accepted = result is AnswerResult.Accepted)
        }

        is EngineRequest.GameComputer -> withGame { g -> computerMove(g, request.decisionId) }

        EngineRequest.GameTakeBack -> withGame { g ->
            val undone = g.undoToLastHumanDecision()
            if (undone) sent = 0
            snapshot(g, canUndo = undone && canUndo(g), undone = undone)
        }

        is EngineRequest.GameReview -> withGame { g ->
            EngineResponse.Review(
                reviews = g.review(request.viewers),
                pushes = pushesWon(g.events),
                rewind = request.rewind?.let { g.positionAt(it.round, it.turn, request.viewers) },
            )
        }

        is EngineRequest.CoachPrepare -> {
            val g = engine().restore(request.saved)
            val decision = g.pending
            coachLook = if (decision != null && decision.id == request.decisionId) {
                decision.id to coach().prepare(g, decision)
            } else {
                null
            }
            EngineResponse.Done
        }

        is EngineRequest.CoachNudge -> {
            val g = engine().restore(request.saved)
            val decision = g.pending
            val note = if (decision == null || decision.id != request.decisionId) {
                null
            } else {
                val look = coachLook?.takeIf { it.first == decision.id }?.second
                if (look != null) {
                    coach().nudge(look, g, decision, request.choice)
                } else {
                    coach().nudge(g, decision, request.choice)
                }
            }
            EngineResponse.Nudge(note)
        }

        is EngineRequest.CoachTurningPoints ->
            EngineResponse.TurningPoints(coach().turningPoints(request.saved, request.seat))

        EngineRequest.PuzzleList -> EngineResponse.Puzzles(book().puzzles)

        is EngineRequest.PuzzleStart -> {
            val started = book().start(request.puzzleId)
            puzzle = started
            EngineResponse.Puzzle(puzzleSnapshot(started))
        }

        is EngineRequest.PuzzleAnswer -> withPuzzle { p ->
            val decision = p.game.pending
            val move = if (decision?.id == request.decisionId) p.play(request.choice) else null
            EngineResponse.PuzzlePlayed(move, puzzleSnapshot(p))
        }

        EngineRequest.PuzzleUndo -> withPuzzle { p ->
            p.undo()
            EngineResponse.Puzzle(puzzleSnapshot(p))
        }

        EngineRequest.PuzzleRestart -> withPuzzle { p ->
            val fresh = p.restart()
            puzzle = fresh
            EngineResponse.Puzzle(puzzleSnapshot(fresh))
        }

        EngineRequest.PuzzleBest -> withPuzzle { p -> EngineResponse.Best(p.bestChoice()) }

        is EngineRequest.PuzzleSolution -> EngineResponse.Solution(book().solution(request.puzzleId))

        is EngineRequest.PuzzleReview -> withPuzzle { p ->
            // A puzzle's hands are open: the review sees every seat.
            val seats = p.game.setup.seats.map { it.heroId }.toSet()
            EngineResponse.Review(
                reviews = p.game.review(seats),
                pushes = pushesWon(p.game.events),
                rewind = request.rewind?.let { p.game.positionAt(it.round, it.turn, seats) },
            )
        }
    }

    private suspend fun coach(): Coach = coach ?: AiCoach(engine()).also { coach = it }

    private suspend fun book(): EnginePuzzleBook = book ?: EnginePuzzleBook(engine()).also { book = it }

    private inline fun withGame(block: (GameSession) -> EngineResponse): EngineResponse =
        game?.let(block) ?: EngineResponse.Failed("No game in progress")

    private inline fun withPuzzle(block: (PuzzleSession) -> EngineResponse): EngineResponse =
        puzzle?.let(block) ?: EngineResponse.Failed("No puzzle in progress")

    private fun started(session: GameSession): EngineResponse {
        game = session
        sent = 0
        return snapshot(session, canUndo = canUndo(session))
    }

    /** The computer answers [decisionId] for its seat; a choice the engine refuses falls back to the first option. */
    private fun computerMove(g: GameSession, decisionId: Long): EngineResponse {
        val decision = g.pending
        if (decision == null || decision.id != decisionId || decision.seat in g.setup.humans) {
            return snapshot(g, accepted = false)
        }
        val seat = g.setup.seats.firstOrNull { it.heroId == decision.seat }
        val choice = aiFor(seat?.controller as? Controller.Ai).choose(g, decision)
        val before = g.events.size
        if (g.answer(decision.id, choice) !is AnswerResult.Accepted) g.answer(decision.id, decision.options.first())
        val revealed = g.events.drop(before).any { it is GameEvent.CardsRevealed }
        return snapshot(g, revealed = revealed)
    }

    /** Whether the person has an answer to take back: tried on a copy, so the game is untouched. */
    private fun canUndo(
        g: GameSession,
    ): Boolean = runCatching { g.fork().undoToLastHumanDecision() }.getOrDefault(false)

    private fun snapshot(
        g: GameSession,
        accepted: Boolean = true,
        canUndo: Boolean? = null,
        undone: Boolean? = null,
        revealed: Boolean = false,
    ): EngineResponse {
        val events = g.events
        val from = sent.coerceAtMost(events.size)
        sent = events.size
        return EngineResponse.Game(
            GameSnapshot(
                setup = g.setup,
                pending = g.pending,
                view = g.mergedView(g.setup.humans),
                eventsFrom = from,
                events = events.subList(from, events.size).toList(),
                eventCount = events.size,
                saved = g.save(),
                canUndo = canUndo,
                accepted = accepted,
                undone = undone,
                revealed = revealed,
            ),
        )
    }

    private fun puzzleSnapshot(p: PuzzleSession) = PuzzleSnapshot(
        info = p.info,
        setup = p.game.setup,
        playerSeats = p.playerSeats,
        pending = p.game.pending,
        view = p.game.openView(p.playerSeats),
        events = p.game.events.toList(),
        status = p.status,
    )

    companion object {
        /**
         * The computer player of a seat: the phone's rule ([AiPlayers.forApp]: its strength, seeded per
         * strength, with the wall-clock cap, ADR-039), so a strength plays the same search as on the
         * phone; a browser that is slower ends a strong search sooner (the play addendum of ADR-043).
         * A seat without a computer (never asked) plays as Normal.
         */
        fun aiFor(controller: Controller.Ai?): AiPlayer =
            AiPlayers.forApp(controller?.strength ?: AiLevel.NORMAL.strength)
    }
}
