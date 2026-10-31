package dev.quinton.goa.core.game.puzzles.rounds

import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.PuzzleStatus
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.puzzles.AnswerKey
import dev.quinton.goa.core.game.puzzles.EnginePuzzleBook
import dev.quinton.goa.core.game.puzzles.EnginePuzzleSession
import dev.quinton.goa.core.game.puzzles.GoalState
import dev.quinton.goa.core.game.puzzles.Node
import dev.quinton.goa.core.game.puzzles.PreparedPuzzle
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.PuzzleSolver
import dev.quinton.goa.core.game.puzzles.Side
import dev.quinton.goa.core.game.puzzles.Value
import java.io.File
import java.util.Random

/**
 * The stricter gate (ADR-052, after the audit): what a book file's words may claim is measured here and
 * recorded in `rounds/gate.txt`, which `FullRoundGateTest` holds the book to.
 *
 * - A proven loss in turn 3 on the solution line: an option at a player decision that no plan can recover from.
 * - The puzzle's idea, removed: `rounds/ideas.txt` says which of the player's choices *are* the idea. With them
 *   forbidden the prover must not win; if it does, the title and text are claiming something the puzzle doesn't need.
 * - Random clicks don't solve it.
 * - The enemy's free actions on the proof's lines, so "turn 4 isn't known" can't be said where nobody ever acts freely.
 */
object RoundGate {
    private val data get() = TestData.engine.data

    /**
     * One thing taken away from the player. A choice in some turns: `T1-4 card:Hurry up!`, `T4 defend:X`,
     * `T3 retrieve:X`, `T2 unit:brogan` where the card asks, `T1-4 bullet:X` a "Choose one" bullet. Or something
     * that must not happen on the board, however
     * it comes about, because an attack with a single target is never asked: `T1-4 kill:RED` no Red minion falls,
     * `T1-4 defeat:misa` Misa isn't defeated, `T1-4 spare:widget` Widget's hand stays what its card plays leave.
     */
    class Ban(private val turns: IntRange, private val kind: String, private val value: String) {
        fun hits(n: Node, c: Choice): Boolean {
            val d = n.decision ?: return false
            if (PlanProver.turnOf(n) !in turns) return false
            return when (kind) {
                "card" -> d.kind == DecisionKind.PLAY_CARD && c is Choice.Card && data.card(c.cardId).name == value
                "defend" -> d.kind == DecisionKind.DEFEND && c is Choice.Card && data.card(c.cardId).name == value
                "retrieve" -> "retrieve" in d.prompt && c is Choice.Card && data.card(c.cardId).name == value
                "unit" -> c is Choice.Unit && c.unitId == value
                "bullet" -> c is Choice.Option && value in c.label
                else -> false
            }
        }

        /** Has it happened? [start] is what [start] returned at the root. */
        fun dead(n: Node, start: Int): Boolean {
            val turn = PlanProver.turnOf(n)
            if (turn > turns.last || turn == PlanProver.END_OF_ROUND) return false
            val st = n.session.state
            return when (kind) {
                "kill" -> st.minions.count { it.team.name == value } < start
                "defeat" -> start == ON_BOARD && st.heroes.first { it.id == value }.at == null
                "spare" -> st.heroes.first { it.id == value }.let { it.at != null && it.hand.size < HAND - turn }
                else -> false
            }
        }

        /** The count [dead] compares with: how many minions of that team, or whether the hero is on the board. */
        fun start(n: Node): Int {
            val st = n.session.state
            return when (kind) {
                "kill" -> st.minions.count { it.team.name == value }
                "defeat" -> if (st.heroes.first { it.id == value }.at != null) ON_BOARD else 0
                else -> 0
            }
        }

        override fun toString(): String = "T$turns $kind:$value"

        companion object {
            private const val ON_BOARD = 1
            private const val HAND = 5
            private const val FROM = 1
            private const val TO = 2
            private const val KIND = 3
            private const val VALUE = 4
            private val FORM = Regex("""T(\d)(?:-(\d))? (\w+):(.+)""")

            fun parse(s: String): Ban {
                val m = requireNotNull(FORM.matchEntire(s.trim())) { "Bad ban: $s" }
                val g = m.groupValues
                val first = g[FROM].toInt()
                return Ban(first..(g[TO].toIntOrNull() ?: first), g[KIND], g[VALUE])
            }
        }
    }

    /** The prover with [probe] taken away. */
    fun without(solver: PuzzleSolver, root: Node, probe: List<Ban>, seconds: Long): Value {
        val starts = probe.map { it.start(root) }
        val prover = PlanProver(
            solver,
            MAX_RUNS,
            System.nanoTime() + seconds * NANOS,
            { n, c -> probe.any { it.hits(n, c) } },
            { n -> probe.indices.any { probe[it].dead(n, starts[it]) } },
        )
        return prover.solve(root)
    }

    /**
     * One line of `ideas.txt`: what the puzzle claims to be about, and how to take it away. Each probe is one
     * claim, tested alone: everything in it forbidden at once.
     */
    class Idea(val id: String, val category: String, val text: String, val probes: List<List<Ban>>)

    private val RESOURCES =
        System.getenv("ROUND_RES") ?: "src/test/resources/dev/quinton/goa/core/game/puzzles/rounds"

    fun ideas(): Map<String, Idea> {
        val f = File("$RESOURCES/ideas.txt")
        if (!f.exists()) return emptyMap()
        return f.readLines().filter { it.isNotBlank() }.map { line ->
            val f = line.split("|", limit = IDEA_FIELDS)
            val probes = f[IDEA_FIELDS - 1].split(";").filter { it.isNotBlank() }
            Idea(f[0], f[1], f[2], probes.map { it.split("+").map(Ban::parse) })
        }.associateBy { it.id }
    }

    /** One row of `gate.txt`. */
    class Row(
        val id: String,
        val fingerprint: String,
        /** Option indices from the root along the solution line, then the option proven to lose. */
        val witness: List<Int>,
        val witnessRuns: Long,
        val lost3: Int,
        val won3: Int,
        val open3: Int,
        val lost4: Int,
        val won4: Int,
        val open4: Int,
        val idea: Value,
        val free3: Int,
        val free4: Int,
        val s7: Boolean,
        val s10: Boolean,
        val randomSolved: Int,
        val firstNotWon: List<Int>,
    ) {
        fun format(): String = listOf(
            id, fingerprint, witness.joinToString("."), witnessRuns, lost3, won3, open3, lost4, won4, open4, idea,
            free3, free4, s7, s10, randomSolved, firstNotWon.joinToString(","),
        ).joinToString(" ")

        /**
         * Hard to hardest, by what is proven and who solves it: one point for a free enemy action in turns 3–4
         * on the proof's lines, one for strength 10 failing, one for most options in turns 3–4 being proven to lose.
         */
        val grade: Int
            get() {
                val points = listOf(free3 + free4 > 0, !s10, lost3 + lost4 >= won3 + won4).count { it }
                return points.coerceIn(1, 3)
            }

        /** Passes the stricter gate. */
        val passes: Boolean get() = witness.isNotEmpty() && idea != Value.WIN && randomSolved == 0

        companion object {
            fun parse(line: String): Row {
                val p = line.split(" ")
                var i = 0
                fun next() = p[i++]
                return Row(
                    next(), next(), next().split(".").filter { it.isNotBlank() }.map { it.toInt() }, next().toLong(),
                    next().toInt(), next().toInt(), next().toInt(), next().toInt(), next().toInt(), next().toInt(),
                    Value.valueOf(next()), next().toInt(), next().toInt(), next().toBoolean(), next().toBoolean(),
                    next().toInt(), next().split(",").filter { it.isNotBlank() }.map { it.toInt() },
                )
            }
        }
    }

    fun table(): Map<String, Row> {
        val f = File("$RESOURCES/gate.txt")
        if (!f.exists()) return emptyMap()
        return f.readLines().filter { it.isNotBlank() }.map(Row::parse).associateBy { it.id }
    }

    fun tableFile(): File = File("$RESOURCES/gate.txt")

    private fun value(solver: PuzzleSolver, n: Node, seconds: Long): Pair<Value, Long> {
        val p = PlanProver(solver, MAX_RUNS, System.nanoTime() + seconds * NANOS)
        return p.solve(n) to p.engineRuns
    }

    /** The enemy's free acting decisions on the proof's lines, by turn: a defense or a known play is not one. */
    fun freeActions(prep: PreparedPuzzle, cert: RoundCertificate): Map<Int, Int> {
        val solver = prep.solver
        val done = HashSet<String>()
        val out = HashMap<Int, Int>()

        fun go(n: Node) {
            val d = n.decision
            if (n.progress.state != GoalState.OPEN || d == null) return
            val k = RoundKey.of(n).format()
            if (!done.add(k)) return
            if (solver.sideOf(d) == Side.OPPONENT) {
                val ch = solver.choices(n)
                val st = n.session.state
                if (ch.size > 1 && st.acting == d.seat && d.kind != DecisionKind.DEFEND) {
                    out.merge(PlanProver.turnOf(n), 1, Int::plus)
                }
                for (i in ch) go(solver.child(n, d.options[i])!!)
            } else {
                go(solver.child(n, d.options[checkNotNull(cert.moves[k])])!!)
            }
        }
        go(prep.rootNode)
        return out
    }

    /** Random answers through the runtime: how many of [games] end solved. */
    fun randomSolved(p: Puzzle, games: Int): Int {
        val book = EnginePuzzleBook(TestData.engine, listOf(p))
        return (0 until games).count { i ->
            val r = Random(SEED + i)
            val s = book.start(p.info.id) as EnginePuzzleSession
            var guard = 0
            while (s.status == PuzzleStatus.PLAYING && guard++ < MAX_ANSWERS) {
                val d = s.game.pending ?: break
                s.answer(d.id, d.options[r.nextInt(d.options.size)])
            }
            s.status == PuzzleStatus.SOLVED
        }
    }

    /** Re-proves a recorded witness: true when that option still loses. */
    fun witnessHolds(prep: PreparedPuzzle, witness: List<Int>, runs: Long): Boolean {
        val solver = prep.solver
        var n = prep.rootNode
        for (i in witness) n = solver.child(n, checkNotNull(n.decision).options[i]) ?: return false
        val budget = maxOf(runs * WITNESS_MARGIN, MIN_WITNESS_RUNS)
        return PlanProver(solver, budget).solve(n) == Value.LOSS
    }

    fun measure(p: Puzzle, idea: Idea?, seconds: Long, optionSeconds: Long, strength: (Puzzle, Int) -> Boolean): Row {
        val prep = RoundCertificate.prepared(p)
        val cert = checkNotNull(RoundCertificate.load(p.info.id))
        val solver = prep.solver
        val line = cert.line(prep)
        val tally = HashMap<Int, IntArray>()
        val path = ArrayList<Int>()
        var witness = emptyList<Int>()
        var witnessRuns = 0L
        val first = ArrayList<Int>()
        for (step in line) {
            val d = step.decision
            val turn = PlanProver.turnOf(step.at)
            val isPlayer = solver.sideOf(d) == Side.PLAYER && d.options.size > 1
            if (isPlayer && turn == 1 && d.kind == DecisionKind.PLAY_CARD) {
                first += d.options.indices.filter { it != step.option }
                    .count { value(solver, solver.child(step.at, d.options[it])!!, optionSeconds).first != Value.WIN }
            }
            if (isPlayer && turn >= THREE) {
                val t = tally.getOrPut(minOf(turn, FOUR)) { IntArray(TALLY) }
                for (i in d.options.indices) {
                    if (i == step.option) continue
                    val child = solver.child(step.at, d.options[i])!!
                    val (v, runs) = if (child.progress.state == GoalState.OPEN && child.decision != null) {
                        value(solver, child, optionSeconds)
                    } else if (child.progress.state == GoalState.MET) {
                        Value.WIN to 0L
                    } else {
                        Value.LOSS to 0L
                    }
                    when (v) {
                        Value.LOSS -> {
                            t[0]++
                            if (turn == THREE && (witness.isEmpty() || runs < witnessRuns)) {
                                witness = path + i
                                witnessRuns = runs
                            }
                        }

                        Value.WIN -> t[1]++

                        Value.UNKNOWN -> t[2]++
                    }
                }
            }
            path += step.option
        }
        val verdicts = idea?.probes.orEmpty().map { probe ->
            val v = without(solver, prep.rootNode, probe, seconds)
            println("GATE-IDEA ${p.info.id} $probe: $v")
            v
        }
        val ideaValue = when {
            Value.WIN in verdicts -> Value.WIN
            verdicts.isNotEmpty() && verdicts.all { it == Value.LOSS } -> Value.LOSS
            else -> Value.UNKNOWN
        }
        val free = freeActions(prep, cert)
        val t3 = tally[THREE] ?: IntArray(TALLY)
        val t4 = tally[FOUR] ?: IntArray(TALLY)
        return Row(
            p.info.id, AnswerKey.fingerprint(prep.rootNode), witness, witnessRuns,
            t3[0], t3[1], t3[2], t4[0], t4[1], t4[2], ideaValue,
            free[THREE] ?: 0, (free[FOUR] ?: 0) + (free[PlanProver.END_OF_ROUND] ?: 0),
            strength(p, S7), strength(p, S10), randomSolved(p, RANDOM_GAMES), first,
        )
    }

    private const val IDEA_FIELDS = 4
    const val S7 = 7
    const val S10 = 10
    private const val THREE = 3
    private const val FOUR = 4
    private const val TALLY = 3
    private const val NANOS = 1_000_000_000L
    private const val MAX_RUNS = 40_000_000L
    private const val MAX_ANSWERS = 300
    private const val SEED = 77L
    private const val RANDOM_GAMES = 20
    private const val WITNESS_MARGIN = 4L
    private const val MIN_WITNESS_RUNS = 20_000L
}
