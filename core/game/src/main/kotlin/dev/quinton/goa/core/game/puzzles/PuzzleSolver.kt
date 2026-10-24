package dev.quinton.goa.core.game.puzzles

import dev.quinton.goa.core.game.AnswerResult
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.game.engine.GameState
import dev.quinton.goa.core.model.match.Team

/** A position's value for the player: the goal is forced ([WIN]), it is not ([LOSS]), or the budget ran out. */
enum class Value { WIN, LOSS, UNKNOWN }

/** Who answers a decision in a puzzle. */
enum class Side {
    /** One of the player's seats: the player chooses (an OR node). */
    PLAYER,

    /** A hero of the player's team the player does not control: answered for them, in their favour (OR). */
    FRIEND,

    /** Everyone else: the best reply against the player (an AND node). */
    OPPONENT,
}

/**
 * A solved position. [best] is the option index to play: for the player and friends the first
 * winning option (or, when lost, the one that resists longest); for an opponent the first refutation
 * (or, when every reply loses, the one that holds out longest). [depth] counts decisions to the end
 * of that line.
 */
data class Solved(val value: Value, val best: Int, val depth: Int)

/** One position in the search: a game paused at a decision (or halted) and the goal's progress on the way there. */
class Node internal constructor(
    val session: EngineSession,
    val progress: Progress,
    internal val checkpointRef: GameState,
    internal val checkpointHash: Long,
    internal val checkpointHash2: Long,
) {
    val decision: Decision? get() = session.pending
}

/**
 * AND-OR proof search over the engine's decisions (ENCODING.md "Solver"). The player's seats (and
 * friendly seats) choose; every other decision is the opponent's best reply. A position is a WIN when
 * the goal is reached against every reply before the deadline (the engine is halted there).
 *
 * - Exact: no evaluation function; positions are expanded until the goal is met or fails.
 * - Transpositions: positions are keyed by a 128-bit hash of the engine checkpoint (decision counter
 *   dropped, card stamps ranked) plus the answers inside the current step and the goal progress.
 * - Short-circuit: an OR node stops at its first winning option, an AND node at its first refutation;
 *   options are tried in the engine's canonical order, a known best first. Deterministic.
 * - Budget: [nodeBudget] expansions per [solve] call; past it (or when cancelled) results are UNKNOWN
 *   and not memoised.
 *
 * Not thread-safe. The memo lives as long as the solver: a puzzle keeps one solver for all its sessions.
 */
class PuzzleSolver(
    val tracker: GoalTracker,
    val nodeBudget: Long = DEFAULT_NODE_BUDGET,
    private val maxMemo: Int = DEFAULT_MAX_MEMO,
) {
    private val memo = HashMap<Key, Solved>()

    /** Positions expanded over the solver's life (for budgets and measurements). */
    var nodesExpanded: Long = 0
        private set

    private var callStart = 0L
    private var cancelled: () -> Boolean = { false }
    private var outOfBudget = false
    private var budget = 0L

    val playerTeam: Team get() = tracker.playerTeam

    fun sideOf(d: Decision): Side = when {
        d.seat in tracker.playerSeats -> Side.PLAYER
        d.team == tracker.playerTeam -> Side.FRIEND
        else -> Side.OPPONENT
    }

    /** The root node of a puzzle game (its progress from the events before the first decision). */
    fun root(session: EngineSession): Node {
        val progress = tracker.after(Progress(), session.events, session)
        return node(session, progress, null)
    }

    /** The node after answering [choice] at [parent]; null when the engine rejects it. */
    fun child(parent: Node, choice: Choice): Node? {
        val d = parent.session.pending ?: return null
        val s = parent.session.forkForAnswer()
        val r = s.answer(d.id, choice)
        if (r !is AnswerResult.Accepted) return null
        return node(s, tracker.after(parent.progress, r.events, s), parent)
    }

    /** Wraps a session that was advanced outside the solver (the runtime's own game). */
    fun wrap(session: EngineSession, progress: Progress, previous: Node?): Node = node(session, progress, previous)

    /** Proves or refutes [node]; [Solved.best] is meaningful when the node has a pending decision. */
    fun solve(node: Node, cancelled: () -> Boolean = { false }, budget: Long = nodeBudget): Solved {
        this.cancelled = cancelled
        callStart = nodesExpanded
        this.budget = budget
        outOfBudget = false
        return eval(node)
    }

    /**
     * Every option at [node] whose answer keeps the goal forced (for the player) or refutes it (for
     * an opponent). Slower than [solve] (no short-circuit): for tests and authoring.
     */
    fun goodOptions(node: Node, cancelled: () -> Boolean = { false }): List<Int> {
        val d = node.decision ?: return emptyList()
        val want = if (sideOf(d) == Side.OPPONENT) Value.LOSS else Value.WIN
        return d.options.indices.filter { i -> child(node, d.options[i])?.let { solve(it, cancelled).value } == want }
    }

    /** The main line from [node]: every decision answered with [Solved.best], until the end. */
    fun mainLine(node: Node, maxLength: Int = MAX_LINE): List<Pair<Decision, Choice>> {
        val out = ArrayList<Pair<Decision, Choice>>()
        var at = node
        while (out.size < maxLength) {
            val d = at.decision ?: break
            if (at.progress.state != GoalState.OPEN) break
            val s = solve(at)
            val c = d.options[s.best]
            out += d to c
            at = child(at, c) ?: break
        }
        return out
    }

    fun clearMemo() = memo.clear()

    val memoSize: Int get() = memo.size

    private fun eval(n: Node): Solved {
        when (n.progress.state) {
            GoalState.MET -> return Solved(Value.WIN, 0, 0)
            GoalState.FAILED -> return Solved(Value.LOSS, 0, 0)
            GoalState.OPEN -> Unit
        }
        val d = n.session.pending ?: return Solved(Value.LOSS, 0, 0)
        val key = keyOf(n)
        memo[key]?.let { return it }
        if (outOfBudget || nodesExpanded - callStart >= budget || cancelled()) {
            outOfBudget = true
            return Solved(Value.UNKNOWN, 0, 0)
        }
        nodesExpanded++
        val result = if (sideOf(d) == Side.OPPONENT) evalAnd(n, d) else evalOr(n, d)
        if (result.value != Value.UNKNOWN) {
            if (memo.size >= maxMemo) memo.clear()
            memo[key] = result
        }
        return result
    }

    /** The player (or a friend): the first winning option wins; else the longest resistance. */
    private fun evalOr(n: Node, d: Decision): Solved {
        var unknown = false
        var longest = -1
        var longestAt = 0
        for (i in d.options.indices) {
            val c = child(n, d.options[i]) ?: continue
            val v = eval(c)
            when (v.value) {
                Value.WIN -> return Solved(Value.WIN, i, v.depth + 1)

                Value.UNKNOWN -> unknown = true

                Value.LOSS -> if (v.depth > longest) {
                    longest = v.depth
                    longestAt = i
                }
            }
        }
        return if (unknown) Solved(Value.UNKNOWN, 0, 0) else Solved(Value.LOSS, longestAt, longest + 1)
    }

    /** The opponent: the first refutation refutes; else the reply that holds out longest. */
    private fun evalAnd(n: Node, d: Decision): Solved {
        var unknown = false
        var longest = -1
        var longestAt = 0
        for (i in d.options.indices) {
            val c = child(n, d.options[i]) ?: continue
            val v = eval(c)
            when (v.value) {
                Value.LOSS -> return Solved(Value.LOSS, i, v.depth + 1)

                Value.UNKNOWN -> unknown = true

                Value.WIN -> if (v.depth > longest) {
                    longest = v.depth
                    longestAt = i
                }
            }
        }
        return if (unknown) Solved(Value.UNKNOWN, 0, 0) else Solved(Value.WIN, longestAt, longest + 1)
    }

    private data class Key(val h1: Long, val h2: Long, val segment: List<Choice>, val coins: Int)

    private fun keyOf(n: Node): Key =
        Key(n.checkpointHash, n.checkpointHash2, n.session.stepAnswers.toList(), n.progress.coins)

    private fun node(s: EngineSession, progress: Progress, parent: Node?): Node {
        val ck = s.checkpointState
        if (parent != null && parent.checkpointRef === ck) {
            return Node(s, progress, ck, parent.checkpointHash, parent.checkpointHash2)
        }
        val (h1, h2) = StateHash.of(ck)
        return Node(s, progress, ck, h1, h2)
    }

    companion object {
        const val DEFAULT_NODE_BUDGET: Long = 200_000
        const val DEFAULT_MAX_MEMO: Int = 400_000
        const val MAX_LINE: Int = 400
    }
}
