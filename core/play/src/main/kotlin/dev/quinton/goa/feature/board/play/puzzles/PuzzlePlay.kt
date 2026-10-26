package dev.quinton.goa.feature.board.play.puzzles

import dev.quinton.goa.core.game.AnswerResult
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.core.game.GameSession
import dev.quinton.goa.core.game.GameView
import dev.quinton.goa.core.game.PuzzleBook
import dev.quinton.goa.core.game.PuzzleInfo
import dev.quinton.goa.core.game.PuzzleSession
import dev.quinton.goa.core.game.PuzzleStatus
import dev.quinton.goa.core.model.match.Team
import kotlinx.serialization.Serializable

/*
 * What the puzzle screen does with the puzzle session, shared by the phone's PuzzleViewModel and the
 * web app (whose session lives in its engine Web Worker, ADR-043). Every function here runs where the
 * session is (the phone's engine thread, the worker).
 */

/** What one answer did: every event, how many of them were the player's own move, the board after it. */
@Serializable
data class PuzzleMove(
    val events: List<GameEvent>,
    val own: Int,
    val ownView: GameView,
    val status: PuzzleStatus,
)

/** One step of the solution replayed: the position before a move, the move, what happened since the last one. */
@Serializable
data class ReplayStep(val view: GameView, val decision: Decision?, val choice: Choice?, val lines: List<String>)

/** Every hero as it sees itself: a puzzle's hands (and cards already played) are open. */
fun GameSession.openView(mine: Set<String>): GameView {
    val first = mine.firstOrNull() ?: return view(null)
    val base = view(first)
    val own = setup.seats.associate { seat ->
        seat.heroId to
            view(seat.heroId).heroes.firstOrNull { it.heroId == seat.heroId }
    }
    return base.copy(heroes = base.heroes.map { hero -> own[hero.heroId] ?: hero })
}

/**
 * The player's answer [choice], with the board just after their own move (a fork answered alone);
 * null when the question isn't theirs or [choice] isn't an option. A game over at the deadline is a
 * miss when the goal wasn't met: the status says.
 */
fun PuzzleSession.play(choice: Choice, mine: Set<String> = playerSeats): PuzzleMove? {
    val decision = game.pending ?: return null
    if (decision.seat !in mine || choice !in decision.options) return null
    val before = game.events.size
    val fork = game.fork()
    val own = runCatching { fork.answer(decision.id, choice) }.getOrNull()
    val result = answer(decision.id, choice) as? AnswerResult.Accepted ?: return null
    val ownCount = if (own is AnswerResult.Accepted) fork.events.size - before else result.events.size
    val ownView = if (own is AnswerResult.Accepted) fork.openView(mine) else game.openView(mine)
    return PuzzleMove(result.events, ownCount.coerceIn(0, result.events.size), ownView, status)
}

/** The other side's replies in [move], as the screen narrates them (round and turn headings left out). */
fun replies(move: PuzzleMove): List<GameEvent> = move.events.drop(move.own).filterNot {
    it is GameEvent.RoundStarted || it is GameEvent.TurnStarted
}

/** "Brogan answers" when an enemy hero acted, else "What happens". */
fun replyTitle(replies: List<GameEvent>, teams: Map<String, Team>, team: Team?, name: (String) -> String): String {
    val enemy = replies.firstNotNullOfOrNull { event ->
        when (event) {
            is GameEvent.ActionChosen -> event.heroId
            is GameEvent.Defended -> event.heroId
            is GameEvent.Moved -> event.unitId
            is GameEvent.Attacked -> event.attackerId
            else -> null
        }?.takeIf { id -> id in teams && teams[id] != team }
    }
    return enemy?.let { "${name(it)} answers" } ?: "What happens"
}

/** The player's question and the solver's answer to it, while the puzzle is still being played. */
fun PuzzleSession.bestMove(): Pair<Decision, Choice>? {
    if (status != PuzzleStatus.PLAYING) return null
    val decision = game.pending ?: return null
    return bestChoice()?.let { decision to it }
}

/** A fresh session of [puzzleId] played with the solver's choice at every player question, step by step. */
fun PuzzleBook.solution(puzzleId: String, maxSteps: Int = MAX_SOLUTION_STEPS): List<ReplayStep> {
    val s = start(puzzleId)
    val mine = s.playerSeats
    val steps = ArrayList<ReplayStep>()
    var seen = s.game.events.size
    fun since(): List<String> = s.game.events.drop(seen).also { seen = s.game.events.size }
        .filterNot { it is GameEvent.RoundStarted || it is GameEvent.TurnStarted }
        .map { it.text }
    var accepted = true
    while (accepted && steps.size < maxSteps) {
        val (decision, choice) = s.bestMove() ?: break
        steps += ReplayStep(s.game.openView(mine), decision, choice, since())
        accepted = s.answer(decision.id, choice) is AnswerResult.Accepted
    }
    steps += ReplayStep(s.game.openView(mine), null, null, since())
    return steps
}

/** A solution longer than this is not replayed (no puzzle comes close). */
const val MAX_SOLUTION_STEPS = 60

/**
 * A replay step's title: the move ("Wasp · Choose a space to move to: Lane Zone 2"), the goal met, or
 * why it can't be shown. [label] names a choice as the table does.
 */
fun replayTitle(
    info: PuzzleInfo,
    step: ReplayStep,
    steps: Int,
    name: (String) -> String,
    label: (Choice) -> String,
): String =
    when {
        step.decision != null && step.choice != null -> "${name(
            step.decision.seat,
        )} · ${step.decision.prompt}: ${label(step.choice)}"

        step.decision == null && steps > 1 -> "Goal met: ${info.goal.text}"

        else -> "The solution can't be shown for this position."
    }
