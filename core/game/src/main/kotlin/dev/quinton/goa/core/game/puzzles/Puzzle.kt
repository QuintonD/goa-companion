package dev.quinton.goa.core.game.puzzles

import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Deadline
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.PuzzleInfo
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.game.engine.GameEngine
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.position.position
import dev.quinton.goa.core.model.board.Hex

/**
 * One bundled puzzle: one file per puzzle in `puzzles/book/`, listed in [Puzzles.all]
 * (core/game/puzzles/ENCODING.md). The position is code (PositionBuilder); the solver proves it in
 * the puzzle's test (`PuzzleBookTest`), so a rules change that breaks a puzzle fails the build.
 */
abstract class Puzzle {
    abstract val info: PuzzleInfo

    /** The concept it encodes in core/game/puzzles/CONCEPTS.md, e.g. `C01`. */
    abstract val concept: String

    /** The heroes the player controls; every other seat is answered by the solver. */
    abstract val playerSeats: Set<String>

    open val mapId: String = FORGOTTEN_ISLAND

    /** When the goal must be met; defaults to the goal's own (`WinGame`: this turn). */
    open val deadline: Deadline get() = GoalTracker.deadlineOf(info.goal)

    /** The position. Seat every hero listed in [PuzzleInfo.heroes] and no other. */
    abstract fun PositionBuilder.setUp()

    /** The intended line: the player's answers, in order (opponents reply as the solver does). */
    abstract val solution: List<Move>

    /** The move(s) the last nudge names ("Attack the minion east of Arien" = action + target). */
    open val nudgedMoves: List<Move> get() = solution.take(1)

    /** The concept's tempting wrong tries: each must turn the status to MISSED exactly at its last move. */
    open val wrongTries: List<WrongTry> = emptyList()

    /** Solver expansions allowed for the full proof in tests (raise for big searches, say why). */
    open val nodeBudget: Long = PuzzleSolver.DEFAULT_NODE_BUDGET

    /** The starting position as a fresh engine session (not yet halted at the deadline). */
    fun build(engine: GameEngine): EngineSession = position(engine, mapId) { setUp() }

    override fun toString(): String = "${info.id} ${info.title}"

    companion object {
        const val FORGOTTEN_ISLAND: String = "forgotten-island"
    }
}

/** A tempting wrong line from the concept and why it fails. */
data class WrongTry(val label: String, val moves: List<Move>)

/** A player answer in a stored line, resolved against the pending decision when it is played. */
sealed interface Move {
    /** The option this move means at [d], or null when it is not offered. */
    fun resolve(d: Decision, s: EngineSession): Choice?

    /** Exactly this choice. */
    data class Pick(val choice: Choice) : Move {
        override fun resolve(d: Decision, s: EngineSession): Choice? = choice.takeIf { it in d.options }

        override fun toString(): String = choice.toString()
    }

    /** The unit (hero, minion or unit piece) standing on [hex] when the move is played. */
    data class UnitAt(val hex: Hex) : Move {
        override fun resolve(d: Decision, s: EngineSession): Choice? {
            val st = s.state
            val id = st.heroes.firstOrNull { it.at == hex }?.id
                ?: st.minions.firstOrNull { it.at == hex }?.id
                ?: st.pieces.firstOrNull { it.at == hex && it.unit }?.id
                ?: return null
            return Choice.Unit(id).takeIf { it in d.options }
        }

        override fun toString(): String = "unit at (${hex.q},${hex.r})"
    }

    /** The token on [hex]. */
    data class TokenAt(val hex: Hex) : Move {
        override fun resolve(d: Decision, s: EngineSession): Choice? {
            val id = s.state.pieces.firstOrNull { it.at == hex && it.token }?.id ?: return null
            return Choice.Token(id).takeIf { it in d.options }
        }
    }

    /** The decision's Decline option, whatever its label. */
    data object DeclineMove : Move {
        override fun resolve(d: Decision, s: EngineSession): Choice? = d.options.firstOrNull { it is Choice.Decline }
    }

    /** The "Choose one" bullet [index] (0 = top). */
    data class OptionAt(val index: Int) : Move {
        override fun resolve(d: Decision, s: EngineSession): Choice? =
            d.options.firstOrNull { it is Choice.Option && it.index == index }
    }
}

/** Shorthands for stored lines: `action(PRIMARY), unitAt(1, -5), space(2, -3), card("…"), decline()`. */
object Moves {
    fun action(a: ActionType): Move = Move.Pick(Choice.Action(a))

    fun unitAt(q: Int, r: Int): Move = Move.UnitAt(Hex(q, r))

    fun hero(heroId: String): Move = Move.Pick(Choice.Unit(heroId))

    fun tokenAt(q: Int, r: Int): Move = Move.TokenAt(Hex(q, r))

    fun space(q: Int, r: Int): Move = Move.Pick(Choice.Space(Hex(q, r)))

    fun card(cardId: String): Move = Move.Pick(Choice.Card(cardId))

    fun number(n: Int): Move = Move.Pick(Choice.Number(n))

    fun option(index: Int): Move = Move.OptionAt(index)

    fun yes(): Move = Move.Pick(Choice.Yes)

    fun decline(): Move = Move.DeclineMove

    fun pass(): Move = Move.Pick(Choice.Pass)
}
