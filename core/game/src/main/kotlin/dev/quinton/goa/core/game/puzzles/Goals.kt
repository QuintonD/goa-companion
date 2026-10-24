package dev.quinton.goa.core.game.puzzles

import dev.quinton.goa.core.game.Deadline
import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.core.game.PuzzleGoal
import dev.quinton.goa.core.game.WinReason
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.game.engine.GameState
import dev.quinton.goa.core.game.engine.Step
import dev.quinton.goa.core.model.match.Team

/** Where a puzzle's goal stands on one line of play. */
enum class GoalState { OPEN, MET, FAILED }

/**
 * What a line of play has achieved so far: the goal's state and, for [PuzzleGoal.GainCoins], the
 * coins the player's heroes gained. Part of the solver's transposition key.
 */
data class Progress(val state: GoalState = GoalState.OPEN, val coins: Int = 0)

/**
 * Reads a puzzle's goal from the events of a line of play (ENCODING.md "Goals"). The game itself is
 * stopped at the goal's [Deadline] ([haltAt]), so every event the tracker sees happened in time.
 */
class GoalTracker(
    val goal: PuzzleGoal,
    val playerTeam: Team,
    val playerSeats: Set<String>,
) {
    /** The progress after [events] (oldest first) on top of [before]; [session] is the game after them. */
    fun after(before: Progress, events: List<GameEvent>, session: EngineSession): Progress {
        var p = before
        for (e in events) {
            if (p.state != GoalState.OPEN) return p
            p = step(p, e)
        }
        if (p.state == GoalState.OPEN && session.pending == null) {
            // The deadline passed (or the game ended without a deciding event).
            p = p.copy(state = if (goal is PuzzleGoal.Survive) GoalState.MET else GoalState.FAILED)
        }
        return p
    }

    private fun step(p: Progress, e: GameEvent): Progress {
        val state = when (val g = goal) {
            is PuzzleGoal.WinGame -> when (e) {
                is GameEvent.GameOver -> if (e.winner == playerTeam) GoalState.MET else GoalState.FAILED
                else -> GoalState.OPEN
            }

            is PuzzleGoal.DefeatHero -> when {
                e is GameEvent.Defeated && e.unitId == g.heroId -> GoalState.MET
                e is GameEvent.GameOver -> GoalState.FAILED
                else -> GoalState.OPEN
            }

            is PuzzleGoal.PushLane -> pushState(e)

            is PuzzleGoal.Survive -> surviveState(e)

            is PuzzleGoal.GainCoins -> return coinsStep(p, e, g.atLeast)
        }
        return if (state == GoalState.OPEN) p else p.copy(state = state)
    }

    private fun pushState(e: GameEvent): GoalState = when {
        e is GameEvent.LanePushed && e.winner == playerTeam -> GoalState.MET

        e is GameEvent.GameOver && e.winner == playerTeam &&
            (e.reason == WinReason.THRONE || e.reason == WinReason.LAST_PUSH) -> GoalState.MET

        e is GameEvent.GameOver -> GoalState.FAILED

        else -> GoalState.OPEN
    }

    private fun surviveState(e: GameEvent): GoalState = when {
        e is GameEvent.Defeated && e.unitId in playerSeats -> GoalState.FAILED
        e is GameEvent.LanePushed && e.winner != playerTeam -> GoalState.FAILED
        e is GameEvent.GameOver -> if (e.winner == playerTeam) GoalState.MET else GoalState.FAILED
        else -> GoalState.OPEN
    }

    private fun coinsStep(p: Progress, e: GameEvent, atLeast: Int): Progress = when {
        e is GameEvent.CoinsGained && e.heroId in playerSeats -> {
            val coins = p.coins + e.amount
            Progress(if (coins >= atLeast) GoalState.MET else GoalState.OPEN, coins)
        }

        e is GameEvent.GameOver -> p.copy(state = GoalState.FAILED)

        else -> p
    }

    companion object {
        fun deadlineOf(goal: PuzzleGoal): Deadline = when (goal) {
            is PuzzleGoal.WinGame -> Deadline.END_OF_TURN
            is PuzzleGoal.DefeatHero -> goal.by
            is PuzzleGoal.PushLane -> goal.by
            is PuzzleGoal.Survive -> goal.by
            is PuzzleGoal.GainCoins -> goal.by
        }

        /**
         * The halt condition for [deadline] from a start in [round]/[turn]: true for the first step
         * after it. End of turn = once the turn's End of turn step is done (the next turn starts, or
         * turn 4's end of round begins); end of round = once the next round starts.
         */
        fun haltAt(deadline: Deadline, round: Int, turn: Int): (GameState) -> Boolean = when (deadline) {
            Deadline.END_OF_TURN -> { s ->
                s.round != round || s.turn != turn || s.step.ordinal >= Step.EOR_EFFECTS.ordinal
            }

            Deadline.END_OF_ROUND -> { s -> s.round != round }
        }
    }
}
