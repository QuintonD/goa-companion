package dev.quinton.goa.core.game.puzzles.rounds

import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.PuzzleGoal
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.game.engine.Step
import dev.quinton.goa.core.game.puzzles.GoalTracker
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.PuzzleSolver
import dev.quinton.goa.core.game.puzzles.Side
import dev.quinton.goa.core.game.puzzles.Value
import dev.quinton.goa.core.game.puzzles.book.Puzzles
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Test

/**
 * Opt-in: `ROUND_DEEP=<out file>[:<seconds>]`, `AI_THREADS`. The rejection rules again on the book's
 * full rounds with longer budgets than mining could afford, and what each turn's choices are worth:
 * how many options at the solution line's decisions are proven to lose, proven to win, or neither. Its
 * table is what STATUS.md's "Why it is four turns" column says; UNKNOWN is never reported as a loss.
 */
class FullRoundDeepTest {
    @Test
    fun `check the book's rounds harder`() {
        val spec = System.getenv("ROUND_DEEP")?.split("|") ?: return
        val out = File(spec[0])
        val seconds = spec.getOrNull(1)?.toLong() ?: DEFAULT_SECONDS
        val threads = System.getenv("AI_THREADS")?.toInt() ?: 4
        val only = System.getenv("ROUND_ONLY")?.split(",")?.toSet()
        val pool = Executors.newFixedThreadPool(threads) { r -> Thread(null, r, "deep", STACK) }
        out.parentFile.mkdirs()
        out.writeText("")
        for (p in Puzzles.fullRounds) {
            if (only != null && p.info.id !in only) continue
            pool.submit {
                val line = runCatching { deep(p, seconds) }.getOrElse { "${p.info.id} FAILED $it" }
                println("DEEP $line")
                synchronized(out) { out.appendText(line + "\n") }
            }
        }
        pool.shutdown()
        pool.awaitTermination(LONG_WAIT, TimeUnit.HOURS)
    }

    private fun value(solver: PuzzleSolver, n: dev.quinton.goa.core.game.puzzles.Node, seconds: Long): Value =
        PlanProver(solver, MAX_RUNS, System.nanoTime() + seconds * NANOS).solve(n)

    private fun forcedBy(p: Puzzle, tracker: GoalTracker, last: Int, seconds: Long): Value {
        if (p.info.goal is PuzzleGoal.Survive) return Value.LOSS
        val s: EngineSession = p.build(TestData.engine)
        s.quiet = true
        val round = s.state.round
        s.haltWhen = { st -> st.round != round || st.turn > last || st.step.ordinal >= Step.EOR_EFFECTS.ordinal }
        val solver = PuzzleSolver(tracker, known = p.known)
        return value(solver, solver.root(s), seconds)
    }

    private fun deep(p: Puzzle, seconds: Long): String {
        val prep = RoundCertificate.prepared(p)
        val cert = checkNotNull(RoundCertificate.load(p.info.id))
        val line = cert.line(prep)
        val solver = prep.solver
        val by2 = forcedBy(p, solver.tracker, 2, seconds)
        val by3 = forcedBy(p, solver.tracker, 3, seconds)
        val turns = sortedMapOf<Int, IntArray>()
        val cards = ArrayList<String>()
        for (step in line) {
            val d = step.decision
            if (solver.sideOf(d) != Side.PLAYER || d.options.size < 2) continue
            val t = PlanProver.turnOf(step.at)
            val tally = turns.getOrPut(t) { IntArray(TALLY) }
            val verdicts = d.options.indices.filter { it != step.option }.map { i ->
                value(solver, solver.child(step.at, d.options[i])!!, OPTION_SECONDS)
            }
            tally[LOST] += verdicts.count { it == Value.LOSS }
            tally[WON] += verdicts.count { it == Value.WIN }
            tally[OPEN] += verdicts.count { it == Value.UNKNOWN }
            if (d.kind == DecisionKind.PLAY_CARD) {
                val name = (step.choice as? Choice.Card)?.cardId
                val wins = verdicts.count { it == Value.WIN } + 1
                val lost = verdicts.count { it == Value.LOSS }
                cards += "T$t ${d.seat}=$name $wins win $lost lose of ${d.options.size}"
            }
        }
        val each = turns.entries.joinToString(" ") { (t, a) -> "T$t:${a[LOST]}L/${a[WON]}W/${a[OPEN]}?" }
        return "${p.info.id} by2=$by2 by3=$by3 $each | ${cards.joinToString("; ")}"
    }

    private companion object {
        const val DEFAULT_SECONDS = 60L
        const val OPTION_SECONDS = 10L
        const val MAX_RUNS = 40_000_000L
        const val NANOS = 1_000_000_000L
        const val STACK = 512L shl 20
        const val LONG_WAIT = 3L
        const val TALLY = 3
        const val LOST = 0
        const val WON = 1
        const val OPEN = 2
    }
}
