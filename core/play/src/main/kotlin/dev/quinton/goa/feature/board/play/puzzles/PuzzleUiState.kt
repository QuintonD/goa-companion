package dev.quinton.goa.feature.board.play.puzzles

import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.PuzzleInfo
import dev.quinton.goa.feature.board.play.game.PlayAnnouncement
import dev.quinton.goa.feature.board.play.review.ReviewAction
import dev.quinton.goa.feature.board.play.review.ReviewUi
import dev.quinton.goa.feature.board.play.table.LogLine
import dev.quinton.goa.feature.board.play.table.TableTap
import dev.quinton.goa.feature.board.play.table.TableUi

/** Where a puzzle stands, as the panel shows it. */
enum class PuzzlePhase {
    /** The player's question is up (or an answer is on its way). */
    SOLVING,

    /** The other side's replies are being shown, briefly paced. */
    REPLYING,

    /** The last move lost the forced line: Try again from before it, Restart, Show solution. */
    MISSED,

    /** The goal is met. */
    SOLVED,
}

/** A nudge on show: [level] of [total] (1-based), stronger each time; [hint] names the option it points at. */
data class NudgeUi(val text: String, val level: Int, val total: Int, val hint: String? = null) {
    val more: Boolean get() = level < total
}

/** The other side's replies after a move, one line at a time. */
data class ReplyingUi(val title: String, val lines: List<String>)

/**
 * The solution replayed on the board (PRODUCT §10 "Explanation at the end"): step [index] of
 * [count]. A step shows the position before a move with the move outlined ([title] names it) and
 * what happened since the move before ([lines]); the last step is the position once the goal is met.
 */
data class ReplayUi(
    val index: Int,
    val count: Int,
    val title: String,
    val lines: List<String>,
    /** The last step: the goal met (or, if the line could not be rebuilt, where it stopped). */
    val last: Boolean,
    val solved: Boolean,
    /** Opened after solving (back = the celebration) rather than from Show solution (back = the puzzle). */
    val afterSolve: Boolean,
) {
    val first: Boolean get() = index == 0
}

data class PuzzleUi(
    val info: PuzzleInfo,
    /** Puzzles in the book; [info] has its number among them. */
    val total: Int,
    /** The table: the live puzzle, or the replay's step while [replay] is open. */
    val table: TableUi,
    val phase: PuzzlePhase,
    /** The heroes the player controls, by name. */
    val playing: List<String>,
    /** MISSED: why, gently. */
    val miss: String? = null,
    val replying: ReplyingUi? = null,
    val nudge: NudgeUi? = null,
    /** Nudges shown so far in this visit (they get stronger); the puzzle has [nudgeCount]. */
    val nudgeLevel: Int = 0,
    val nudgeCount: Int = 0,
    /** Settings → Puzzle nudges: on = they come by themselves when stuck; off = only when asked. */
    val nudgesOn: Boolean = true,
    /** "What you know" is open (it folds once the first move is made). */
    val introOpen: Boolean = true,
    /** Tries on this phone so far (this one included once a move is made), for the summary. */
    val attempts: Int = 0,
    /** The most nudges shown in a try, for the summary. */
    val nudgesUsed: Int = 0,
    val replay: ReplayUi? = null,
    /** The latest events, oldest first. */
    val recent: List<String> = emptyList(),
    val log: List<LogLine> = emptyList(),
    /** The next puzzle in the book, if any ("Next puzzle"). */
    val next: String? = null,
    /** An answer is on its way, or the solution is being prepared: buttons wait. */
    val busy: Boolean = false,
    val announcement: PlayAnnouncement? = null,
    /** The game-state review, while its sheet is open (ADR-037). */
    val review: ReviewUi? = null,
) {
    val canNudge: Boolean get() = nudgeCount > 0 && (nudgeLevel < nudgeCount || nudge == null)
}

/** Why there is no puzzle to show. */
enum class NoPuzzle {
    /** Not in this build's book (its heroes are not playable yet, or the id is unknown). */
    MISSING,

    /** The engine couldn't build it. */
    BROKEN,
}

sealed interface PuzzleUiState {
    data object Loading : PuzzleUiState

    data class Unavailable(val why: NoPuzzle) : PuzzleUiState

    data class Ready(val puzzle: PuzzleUi) : PuzzleUiState
}

sealed interface PuzzleAction {
    /** A tap on the board: selects the option it stands for, if any. */
    data class Tap(val tap: TableTap) : PuzzleAction

    data class Select(val choice: Choice) : PuzzleAction

    data class Answer(val choice: Choice) : PuzzleAction

    /** After a miss: undo the move that missed and try again from just before it. */
    data object TryAgain : PuzzleAction

    data object Restart : PuzzleAction

    /** The next nudge (or the last one again); works with nudges off too. */
    data object Nudge : PuzzleAction

    data object DismissNudge : PuzzleAction

    data class SetNudges(val on: Boolean) : PuzzleAction

    data class SetIntroOpen(val open: Boolean) : PuzzleAction

    /** The explanation and the solution replayed step by step. */
    data object ShowSolution : PuzzleAction

    data object ReplayNext : PuzzleAction

    data object ReplayBack : PuzzleAction

    /** Leaves the replay: back to the puzzle (or the celebration). */
    data object CloseReplay : PuzzleAction

    data object Flip : PuzzleAction

    /** The game-state review: opened, closed, a board rewound. */
    data class Review(val action: ReviewAction) : PuzzleAction
}
