package dev.quinton.goa.feature.board.play.review

import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.core.game.GameSession
import dev.quinton.goa.core.game.GameView
import dev.quinton.goa.core.game.TurnPlay
import dev.quinton.goa.core.game.TurnReview
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.board.play.game.FACEDOWN
import dev.quinton.goa.feature.board.play.game.important
import dev.quinton.goa.feature.board.play.game.merged

/** A log line of a turn in the review; [strong] = one not to miss (a defeat, a push, a level up…). */
data class ReviewLine(val text: String, val strong: Boolean = false)

/** One turn in the review's history: the cards revealed and what happened (ADR-037). */
data class TurnUi(
    val round: Int,
    val turn: Int,
    /** In the order the heroes acted; empty before the reveal. */
    val plays: List<TurnPlay>,
    /** What happened after the reveal (round and turn headings and "plays a card facedown" left out). */
    val lines: List<ReviewLine>,
    /** After a round's 4th turn: the end of the round. */
    val endOfRound: List<ReviewLine>,
    val finished: Boolean,
    /** The board at the turn's start can be shown ([ReviewAction.Rewind]). */
    val hasStart: Boolean,
    /** A puzzle began inside this turn: its "start" is the puzzle's starting position. */
    val fromPosition: Boolean,
)

/** The board at the start of an earlier turn, read only. */
data class RewindUi(
    val round: Int,
    val turn: Int,
    val view: GameView,
    val fromPosition: Boolean,
    /** Another kept turn before / after this one. */
    val hasEarlier: Boolean,
    val hasLater: Boolean,
)

/**
 * The game-state review (PRODUCT §9–10): the game so far, turn by turn, and the pushes each team
 * won, as the person may see them; the board of an earlier turn while [rewind] is open.
 */
data class ReviewUi(
    /** Oldest first; the turn being played last. */
    val turns: List<TurnUi>,
    /** Lane pushes won per team so far (from the log). */
    val pushes: Map<Team, Int> = emptyMap(),
    val rewind: RewindUi? = null,
)

/** What the review asks of its screen's ViewModel. */
sealed interface ReviewAction {
    /** The review sheet opened (true) or closed: the history is only built while it is open. */
    data class Open(val open: Boolean) : ReviewAction

    /** Show the board at the start of [round]'s [turn]. */
    data class Rewind(val round: Int, val turn: Int) : ReviewAction

    /** The kept turn before (-1) or after (+1) the one shown. */
    data class Step(val by: Int) : ReviewAction

    data object CloseRewind : ReviewAction
}

/**
 * The review's state in a screen's ViewModel: open or not, which board is rewound. [build] runs on
 * the engine thread (the session is not thread-safe).
 */
class ReviewKeeper {
    var open = false
        private set
    private var rewind: Pair<Int, Int>? = null

    /** Applies [action]; true when the screen should be rebuilt. */
    fun handle(action: ReviewAction, turns: List<TurnUi>?): Boolean = when (action) {
        is ReviewAction.Open -> {
            val changed = open != action.open
            open = action.open
            if (!open) rewind = null
            changed
        }

        is ReviewAction.Rewind -> {
            rewind = action.round to action.turn
            true
        }

        is ReviewAction.Step -> {
            val kept = turns.orEmpty().filter { it.hasStart }.map { it.round to it.turn }
            val at = kept.indexOf(rewind)
            val next = kept.getOrNull(at + action.by)
            if (at >= 0 && next != null) rewind = next
            next != null
        }

        ReviewAction.CloseRewind -> {
            rewind = null
            true
        }
    }

    /** Forget the rewind (a take back or a restart may have dropped that turn). */
    fun reset() {
        rewind = null
    }

    /** The turn whose starting board is shown (round to turn), if any. */
    val rewindAt: Pair<Int, Int>? get() = rewind

    /** The review for [viewers], or null while closed. On the engine thread. */
    fun build(game: GameSession, viewers: Set<String>): ReviewUi? {
        if (!open) return null
        return reviewUi(
            heroIds = game.setup.seats.map { it.heroId }.toSet(),
            reviews = game.review(viewers),
            pushes = pushesWon(game.events),
            rewind = rewind,
        ) { round, turn -> game.positionAt(round, turn, viewers) }
    }
}

/** Lane pushes won per team so far, from the log. */
fun pushesWon(events: List<GameEvent>): Map<Team, Int> =
    events.filterIsInstance<GameEvent.LanePushed>().groupingBy { it.winner }.eachCount()

/**
 * The review from the engine's [reviews] (oldest first) and [pushes], with the board at the start
 * of [rewind] (round to turn) when that turn's start is kept ([positionAt]). The phone builds it on
 * the engine thread; the web app from what its engine worker sent.
 */
fun reviewUi(
    heroIds: Set<String>,
    reviews: List<TurnReview>,
    pushes: Map<Team, Int>,
    rewind: Pair<Int, Int>?,
    positionAt: (round: Int, turn: Int) -> GameView?,
): ReviewUi {
    val turns = reviews.map { it.toUi(heroIds) }
    val kept = turns.filter { it.hasStart }
    val index = kept.indexOfFirst { (it.round to it.turn) == rewind }
    val shown = kept.getOrNull(index)
    val view = shown?.let { positionAt(it.round, it.turn) }
    return ReviewUi(
        turns = turns,
        pushes = pushes,
        rewind = if (shown != null && view != null) {
            RewindUi(shown.round, shown.turn, view, shown.fromPosition, index > 0, index < kept.lastIndex)
        } else {
            null
        },
    )
}

fun TurnReview.toUi(heroIds: Set<String>): TurnUi = TurnUi(
    round = round,
    turn = turn,
    plays = plays,
    lines = lines(events, heroIds),
    endOfRound = lines(endOfRound, heroIds),
    finished = finished,
    hasStart = hasStart,
    fromPosition = fromPosition,
)

private fun lines(events: List<GameEvent>, heroIds: Set<String>): List<ReviewLine> = merged(
    events.filterNot {
        it is GameEvent.RoundStarted || it is GameEvent.TurnStarted ||
            (it is GameEvent.Note && it.text.endsWith(FACEDOWN))
    },
).map { ReviewLine(it.text, important(it, heroIds)) }
