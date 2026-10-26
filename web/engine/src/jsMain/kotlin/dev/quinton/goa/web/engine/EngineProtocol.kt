package dev.quinton.goa.web.engine

import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.CoachNote
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.core.game.GameSetup
import dev.quinton.goa.core.game.GameView
import dev.quinton.goa.core.game.PuzzleInfo
import dev.quinton.goa.core.game.PuzzleStatus
import dev.quinton.goa.core.game.SavedGame
import dev.quinton.goa.core.game.TurnReview
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.board.play.puzzles.PuzzleMove
import dev.quinton.goa.feature.board.play.puzzles.ReplayStep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/*
 * The messages between the web app and its engine Web Worker (ADR-043 "Addendum (play, W4)"). The
 * worker keeps the game session (and the puzzle session) and runs the rules, the computer players,
 * the coach and the puzzle solver; the page only ever sees what the engine's views and events say,
 * as the phone's screens do. Plain JSON both ways (the engine's API types are @Serializable).
 *
 * One game and one puzzle at a time per worker, as on the phone. A request that takes long (the
 * computer choosing, the coach looking) can't be interrupted inside a worker: the page terminates
 * the worker instead and starts a fresh one from the last save.
 */

@Serializable
sealed interface EngineRequest {
    /** What the engine can play: [EngineResponse.Info]. */
    @Serializable
    @SerialName("info")
    data object Info : EngineRequest

    /** A new game ([EngineResponse.Game]). */
    @Serializable
    @SerialName("game.start")
    data class GameStart(val setup: GameSetup) : EngineRequest

    /** The saved game rebuilt ([EngineResponse.Game]), or [EngineResponse.Failed] when the engine can't. */
    @Serializable
    @SerialName("game.restore")
    data class GameRestore(val saved: SavedGame) : EngineRequest

    /** The person's answer to [decisionId] ([EngineResponse.Game], `accepted` false when it wasn't an option). */
    @Serializable
    @SerialName("game.answer")
    data class GameAnswer(val decisionId: Long, val choice: Choice) : EngineRequest

    /** The computer answers the pending question of one of its seats ([EngineResponse.Game]). */
    @Serializable
    @SerialName("game.computer")
    data class GameComputer(val decisionId: Long) : EngineRequest

    /** Take back the person's last answer ([EngineResponse.Game], `undone` says whether there was one). */
    @Serializable
    @SerialName("game.takeBack")
    data object GameTakeBack : EngineRequest

    /** The game so far as [viewers] may see it, with the board at the start of [rewind] ([EngineResponse.Review]). */
    @Serializable
    @SerialName("game.review")
    data class GameReview(val viewers: Set<String>, val rewind: Turn? = null) : EngineRequest

    /** The coach looks at the person's question [decisionId] of [saved] (in the coach's own worker). */
    @Serializable
    @SerialName("coach.prepare")
    data class CoachPrepare(val saved: SavedGame, val decisionId: Long) : EngineRequest

    /** A nudge before [choice] is answered, or none ([EngineResponse.Nudge]). */
    @Serializable
    @SerialName("coach.nudge")
    data class CoachNudge(val saved: SavedGame, val decisionId: Long, val choice: Choice) : EngineRequest

    /** After the game: the moments that decided it for [seat] ([EngineResponse.TurningPoints]). */
    @Serializable
    @SerialName("coach.turningPoints")
    data class CoachTurningPoints(val saved: SavedGame, val seat: String) : EngineRequest

    /** The puzzle book: every puzzle whose heroes are all playable ([EngineResponse.Puzzles]). */
    @Serializable
    @SerialName("puzzle.list")
    data object PuzzleList : EngineRequest

    @Serializable
    @SerialName("puzzle.start")
    data class PuzzleStart(val puzzleId: String) : EngineRequest

    /** The player's answer; the other side replies at once ([EngineResponse.PuzzlePlayed]). */
    @Serializable
    @SerialName("puzzle.answer")
    data class PuzzleAnswer(val decisionId: Long, val choice: Choice) : EngineRequest

    /** Undo the player's last answer, after a miss ([EngineResponse.Puzzle]). */
    @Serializable
    @SerialName("puzzle.undo")
    data object PuzzleUndo : EngineRequest

    /** Back to the starting position ([EngineResponse.Puzzle]). */
    @Serializable
    @SerialName("puzzle.restart")
    data object PuzzleRestart : EngineRequest

    /** The solver's choice for the pending question ([EngineResponse.Best]). */
    @Serializable
    @SerialName("puzzle.best")
    data object PuzzleBest : EngineRequest

    /** The solution replayed on a fresh copy of the puzzle ([EngineResponse.Solution]). */
    @Serializable
    @SerialName("puzzle.solution")
    data class PuzzleSolution(val puzzleId: String) : EngineRequest

    /** The puzzle so far, every hand open ([EngineResponse.Review]). */
    @Serializable
    @SerialName("puzzle.review")
    data class PuzzleReview(val rewind: Turn? = null) : EngineRequest
}

/** A turn of a round, e.g. the board at its start. */
@Serializable
data class Turn(val round: Int, val turn: Int)

@Serializable
sealed interface EngineResponse {
    @Serializable
    @SerialName("info")
    data class Info(val engineVersion: Int, val playableHeroes: Set<String>, val playableMaps: List<String>) :
        EngineResponse

    @Serializable
    @SerialName("game")
    data class Game(val game: GameSnapshot) : EngineResponse

    @Serializable
    @SerialName("review")
    data class Review(
        val reviews: List<TurnReview>,
        val pushes: Map<Team, Int>,
        /** The board at the start of the requested turn, when it is kept. */
        val rewind: GameView? = null,
    ) : EngineResponse

    @Serializable
    @SerialName("nudge")
    data class Nudge(val note: CoachNote? = null) : EngineResponse

    @Serializable
    @SerialName("turningPoints")
    data class TurningPoints(val notes: List<CoachNote>) : EngineResponse

    @Serializable
    @SerialName("puzzles")
    data class Puzzles(val puzzles: List<PuzzleInfo>) : EngineResponse

    @Serializable
    @SerialName("puzzle")
    data class Puzzle(val puzzle: PuzzleSnapshot) : EngineResponse

    @Serializable
    @SerialName("puzzlePlayed")
    data class PuzzlePlayed(val move: PuzzleMove?, val puzzle: PuzzleSnapshot) : EngineResponse

    @Serializable
    @SerialName("best")
    data class Best(val choice: Choice?) : EngineResponse

    @Serializable
    @SerialName("solution")
    data class Solution(val steps: List<ReplayStep>) : EngineResponse

    @Serializable
    @SerialName("done")
    data object Done : EngineResponse

    /** The request couldn't be carried out ([message] says why; never shown as is). */
    @Serializable
    @SerialName("failed")
    data class Failed(val message: String) : EngineResponse
}

/**
 * The game as the person may see it after a request. [events] are the log from [eventsFrom] on (the
 * page keeps the earlier ones; 0 after a start, a restore or a take back, so it starts over).
 */
@Serializable
data class GameSnapshot(
    val setup: GameSetup,
    val pending: Decision?,
    /** The person's view: their first hero's, with each other hero they control as it sees itself. */
    val view: GameView,
    val eventsFrom: Int,
    val events: List<GameEvent>,
    val eventCount: Int,
    /** What to store: the engine's save of the game as it is now. */
    val saved: SavedGame,
    /** The person has an answer to take back (only worked out on a start, restore or take back). */
    val canUndo: Boolean? = null,
    /** False when the answer wasn't one of the options (nothing changed). */
    val accepted: Boolean = true,
    /** A take back found an answer to undo. */
    val undone: Boolean? = null,
    /** The computer's answer revealed the cards (the reveal stays longer on screen). */
    val revealed: Boolean = false,
)

/** A puzzle as the player sees it (every hand open, the puzzle's convention). */
@Serializable
data class PuzzleSnapshot(
    val info: PuzzleInfo,
    val setup: GameSetup,
    val playerSeats: Set<String>,
    val pending: Decision?,
    val view: GameView,
    val events: List<GameEvent>,
    val status: PuzzleStatus,
)

/** How the page and the worker write the messages. */
val EngineJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
    allowStructuredMapKeys = true
}

/** One request with its id, as posted to the worker. */
@Serializable
data class EngineMessage(val id: Int, val request: EngineRequest)

/** The worker's reply to request [id]. */
@Serializable
data class EngineReply(val id: Int, val response: EngineResponse)
