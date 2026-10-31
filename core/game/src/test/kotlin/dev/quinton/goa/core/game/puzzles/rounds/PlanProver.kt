package dev.quinton.goa.core.game.puzzles.rounds

import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.engine.Step
import dev.quinton.goa.core.game.puzzles.GoalState
import dev.quinton.goa.core.game.puzzles.Node
import dev.quinton.goa.core.game.puzzles.PuzzleSolver
import dev.quinton.goa.core.game.puzzles.Side
import dev.quinton.goa.core.game.puzzles.Value

/**
 * Proves full rounds by trying a plan first (ADR-052). Most enemy answers don't change what the player
 * should do, so a plan that worked in a sibling branch is tried first ([history], the killer heuristic
 * of game-tree search), which makes the cost about the number of enemy positions under one plan rather
 * than plans × positions.
 *
 * Budgeted dovetailing: at a player node every option gets a small budget, then four times as much,
 * until one wins or all are refuted; at an enemy node the first answer that isn't proven ends the
 * attempt. Only proven values are stored for good; a position that ran out of budget remembers how
 * much it was given so a retry with no more is free. Deterministic for a given position.
 *
 * A proof is only trusted after [verify] has replayed it through the engine.
 */
class PlanProver(
    private val solver: PuzzleSolver,
    private val maxRuns: Long,
    private val deadlineNanos: Long = Long.MAX_VALUE,
) {
    private val proven = HashMap<RoundKey, Boolean>()
    private val tried = HashMap<RoundKey, Long>()
    private val best = HashMap<RoundKey, Int>()
    private val history = HashMap<Long, Int>()

    var engineRuns = 0L
        private set
    private var spent = false

    val positions: Int get() = proven.size + tried.size

    private fun terminal(n: Node): Value? = when {
        n.progress.state == GoalState.MET -> Value.WIN
        n.progress.state == GoalState.FAILED -> Value.LOSS
        n.decision == null -> Value.LOSS
        else -> null
    }

    /** WIN, LOSS, or UNKNOWN when [maxRuns] or the deadline ran out. */
    fun solve(root: Node): Value {
        var budget = FIRST_BUDGET
        while (!spent) {
            val v = prove(root, budget)
            if (v != Value.UNKNOWN) return v
            budget *= GROWTH
        }
        return Value.UNKNOWN
    }

    private fun child(n: Node, option: Int): Node {
        engineRuns++
        if (engineRuns >= maxRuns || (engineRuns and CLOCK_MASK == 0L && System.nanoTime() > deadlineNanos)) {
            spent = true
        }
        return checkNotNull(solver.child(n, n.decision!!.options[option]))
    }

    private fun historyKey(n: Node, c: Choice): Long =
        (n.session.state.turn.toLong() shl HISTORY_SHIFT) + n.decision!!.seat.hashCode() * HISTORY_MUL + stableHash(c)

    private fun ordered(n: Node, choices: List<Int>): List<Int> {
        if (choices.size < 2) return choices
        val d = n.decision!!
        return choices.sortedByDescending { history[historyKey(n, d.options[it])] ?: 0 }
    }

    private fun credit(n: Node, option: Int) {
        val k = historyKey(n, n.decision!!.options[option])
        history[k] = (history[k] ?: 0) + 1
    }

    private fun prove(n: Node, budget: Long): Value {
        terminal(n)?.let { return it }
        val k = RoundKey.of(n)
        proven[k]?.let { return if (it) Value.WIN else Value.LOSS }
        if ((tried[k] ?: 0L) >= budget || spent) return Value.UNKNOWN
        val start = engineRuns
        val choices = ordered(n, solver.choices(n))
        val v = if (solver.sideOf(n.decision!!) == Side.OPPONENT) {
            enemy(n, choices, budget, start)
        } else {
            player(n, k, choices, budget, start)
        }
        if (v == Value.UNKNOWN) {
            tried[k] = budget
        } else {
            tried.remove(k)
            proven[k] = v == Value.WIN
        }
        return v
    }

    /** Every answer must be proven; the first that isn't ends this attempt. */
    private fun enemy(n: Node, choices: List<Int>, budget: Long, start: Long): Value {
        for (i in choices) {
            val left = budget - (engineRuns - start)
            if (left <= 0) return Value.UNKNOWN
            when (prove(child(n, i), left)) {
                Value.WIN -> Unit

                Value.LOSS -> {
                    credit(n, i)
                    return Value.LOSS
                }

                Value.UNKNOWN -> return Value.UNKNOWN
            }
        }
        return Value.WIN
    }

    /** Small budgets for every option, growing, until one wins or all are refuted. */
    private fun player(n: Node, k: RoundKey, choices: List<Int>, budget: Long, start: Long): Value {
        var open = choices
        best[k]?.let { b -> if (b in open) open = listOf(b) + (open - b) }
        var each = minOf(budget, maxOf(FIRST_BUDGET, budget / open.size.coerceAtLeast(1)))
        while (true) {
            val still = ArrayList<Int>(open.size)
            for (i in open) {
                val left = budget - (engineRuns - start)
                if (left <= 0) {
                    still += i
                    continue
                }
                when (prove(child(n, i), minOf(each, left))) {
                    Value.WIN -> {
                        credit(n, i)
                        best[k] = i
                        return Value.WIN
                    }

                    Value.LOSS -> Unit

                    Value.UNKNOWN -> still += i
                }
            }
            if (still.isEmpty()) return Value.LOSS
            if (engineRuns - start >= budget || spent) return Value.UNKNOWN
            open = still
            each *= GROWTH
        }
    }

    /** What a proof looks like once it has been replayed through the engine. */
    class Proof(
        val positions: Int,
        val playerPositions: Int,
        val enemyPositions: Int,
        val edges: Long,
        /** The earliest and latest turn in which the goal was met, over all the proof's lines (5 = end of round). */
        val earliest: Int,
        val latest: Int,
        /** The plan: the option played at each player position. */
        val moves: Map<RoundKey, Int>,
    )

    /** Walks the proof from [root] with the real engine. Null when a claimed proof doesn't hold. */
    fun verify(root: Node): Proof? {
        val done = HashSet<RoundKey>()
        val moves = LinkedHashMap<RoundKey, Int>()
        var edges = 0L
        var enemy = 0
        var player = 0
        var earliest = Int.MAX_VALUE
        var latest = 0
        var ok = true

        fun go(n: Node) {
            when (terminal(n)) {
                Value.WIN -> {
                    val t = turnOf(n)
                    earliest = minOf(earliest, t)
                    latest = maxOf(latest, t)
                    return
                }

                Value.LOSS, Value.UNKNOWN -> {
                    ok = false
                    return
                }

                null -> Unit
            }
            val k = RoundKey.of(n)
            if (!done.add(k)) return
            val d = n.decision!!
            if (solver.sideOf(d) == Side.OPPONENT) {
                enemy++
                for (i in solver.choices(n)) {
                    edges++
                    go(checkNotNull(solver.child(n, d.options[i])))
                    if (!ok) return
                }
            } else {
                player++
                val i = best[k] ?: solver.choices(n).firstOrNull { provenChild(n, it) }
                if (i == null) {
                    ok = false
                    return
                }
                edges++
                moves[k] = i
                go(checkNotNull(solver.child(n, d.options[i])))
            }
        }
        go(root)
        return if (ok) Proof(done.size, player, enemy, edges, earliest, latest, moves) else null
    }

    private fun provenChild(n: Node, option: Int): Boolean {
        val c = checkNotNull(solver.child(n, n.decision!!.options[option]))
        return when (terminal(c)) {
            Value.WIN -> true
            Value.LOSS, Value.UNKNOWN -> false
            null -> proven[RoundKey.of(c)] == true
        }
    }

    companion object {
        const val END_OF_ROUND: Int = 5
        private const val FIRST_BUDGET = 256L
        private const val GROWTH = 4L
        private const val CLOCK_MASK = 0x3FFL
        private const val HISTORY_SHIFT = 40
        private const val HISTORY_MUL = 1_000_003L

        /** The turn a position stands in, [END_OF_ROUND] from the end-of-round steps on. */
        fun turnOf(n: Node): Int {
            val st = n.session.state
            return if (st.step.ordinal >= Step.EOR_EFFECTS.ordinal) END_OF_ROUND else st.turn
        }

        /** A hash that is the same in every JVM (an enum's own hashCode is not). */
        fun stableHash(c: Choice): Int = when (c) {
            is Choice.Action -> c.action.ordinal
            is Choice.Card -> c.cardId.hashCode()
            is Choice.Unit -> c.unitId.hashCode()
            is Choice.Token -> c.tokenId.hashCode()
            is Choice.Space -> c.hex.q * SPACE_MUL + c.hex.r
            is Choice.Option -> c.index
            is Choice.Number -> c.value
            else -> c.toString().hashCode()
        }

        private const val SPACE_MUL = 1009

        /**
         * The line a proof's plan leads to against the answer that holds out longest (most decisions):
         * the solution as the book shows it. [moves] is the plan, [solver] answers what it can't see.
         */
        fun mainLine(solver: PuzzleSolver, root: Node, moves: Map<RoundKey, Int>): List<LineStep> {
            val depth = HashMap<RoundKey, Int>()

            fun depthOf(n: Node): Int {
                if (n.progress.state != GoalState.OPEN || n.decision == null) return 0
                val k = RoundKey.of(n)
                depth[k]?.let { return it }
                val d = n.decision!!
                val value = if (solver.sideOf(d) == Side.OPPONENT) {
                    solver.choices(n).maxOf { depthOf(solver.child(n, d.options[it])!!) } + 1
                } else {
                    val i = moves[k] ?: error(
                        "No plan at ${solver.sideOf(d)} ${d.seat} ${d.kind} '${d.prompt}' turn ${turnOf(n)} " +
                            "step ${n.session.state.step} options ${d.options.size} progress ${n.progress} " +
                            "choices ${solver.choices(n)}",
                    )
                    depthOf(solver.child(n, d.options[i])!!) + 1
                }
                depth[k] = value
                return value
            }
            val out = ArrayList<LineStep>()
            var at = root
            while (at.progress.state == GoalState.OPEN && at.decision != null) {
                val d = at.decision!!
                val pick = if (solver.sideOf(d) == Side.OPPONENT) {
                    solver.choices(at).maxByOrNull { depthOf(solver.child(at, d.options[it])!!) }!!
                } else {
                    moves.getValue(RoundKey.of(at))
                }
                val next = solver.child(at, d.options[pick])!!
                out += LineStep(at, d, pick, next)
                at = next
            }
            return out
        }
    }
}

/** One decision of a line of play and what was answered. */
class LineStep(val at: Node, val decision: Decision, val option: Int, val next: Node) {
    val choice: Choice get() = decision.options[option]
}

/** A position's identity across JVMs: the checkpoint's fingerprint, this step's answers, the coins so far. */
data class RoundKey(val a: Long, val b: Long, val seg: Int, val coins: Int) {
    fun format(): String = "%016x%016x%08x%d".format(a, b, seg, coins)

    companion object {
        fun of(n: Node) = RoundKey(
            n.checkpointHash,
            n.checkpointHash2,
            // Starts at 1 and every answer adds its kind: no answer may leave the hash where it was.
            n.session.stepAnswers.fold(1) { h, c -> h * SEG_MUL + PlanProver.stableHash(c) * KIND_MUL + c::class.simpleName.hashCode() },
            n.progress.coins,
        )

        private const val SEG_MUL = 1_000_003
        private const val KIND_MUL = 31
    }
}
