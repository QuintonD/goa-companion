package dev.quinton.goa.core.game.ai

import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.game.engine.Step
import dev.quinton.goa.core.model.match.Team
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import org.junit.Test

/**
 * Opt-in: `AI_FIT=<games>[:<strength>[:<first line-up>]]` plays self-play games on mixed line-ups,
 * records the evaluation's parts ([Term], Red minus Blue) at the start of every turn and who won,
 * and fits P(Red wins) = σ(Σ mᵢ · termᵢ) by logistic regression (AI.md "Evaluation"). Equal mᵢ mean
 * the parts are weighed right against each other; the fit's mᵢ / m(LANE) are the scales to try in
 * [EvalKnobs.scales]. Half the games fit, the other half measure: the log-loss of the fit against
 * σ(k · score) with the current weights.
 */
class FitTest {
    private class Sample(val game: Int, val terms: DoubleArray, val score: Double, val redWon: Boolean)

    @Test
    fun `fit the evaluation's parts to self-play outcomes`() {
        val spec = System.getenv("AI_FIT")?.split(":") ?: return
        val games = spec[0].toInt()
        val strength = spec.getOrNull(1)?.toInt() ?: 6
        val first = spec.getOrNull(2)?.toInt() ?: 1000
        val ev = Evaluator(TestData.engine.data, AiPlayers.profile(strength).knobs)
        val lineups = AiHarness.mixedLineups(first + games).drop(first)
        val jobs = lineups.mapIndexed { i, (red, blue) ->
            Callable { play(i, red, blue, strength, ev) }
        }
        val pool = Executors.newFixedThreadPool(System.getenv("AI_THREADS")?.toIntOrNull() ?: 4)
        val all = try {
            pool.invokeAll(jobs).flatMap { it.get() }
        } finally {
            pool.shutdown()
        }
        val train = all.filter { it.game % 2 == 0 }
        val test = all.filter { it.game % 2 == 1 }
        println("FIT ${all.size} positions from $games games (strength $strength)")
        // The current evaluation: one slope on the score.
        val k = fit(train.map { doubleArrayOf(it.score) }, train.map { it.redWon }, l2 = 0.0)[0]
        val baseLoss = loss(test.map { doubleArrayOf(it.score) }, test.map { it.redWon }, doubleArrayOf(k))
        // Each part its own weight.
        val m = fit(train.map { it.terms }, train.map { it.redWon }, l2 = 1e-4)
        val fitLoss = loss(test.map { it.terms }, test.map { it.redWon }, m)
        println("FIT current score: k = ${"%.4f".format(k)}, held-out log-loss ${"%.4f".format(baseLoss)}")
        println("FIT per part: held-out log-loss ${"%.4f".format(fitLoss)}")
        val lane = m[Term.LANE.ordinal]
        for (t in Term.entries) {
            val sd = kotlin.math.sqrt(train.map { it.terms[t.ordinal] }.let { v -> v.sumOf { it * it } / v.size })
            println(
                "FIT   ${t.name.padEnd(14)} m = ${"%8.4f".format(m[t.ordinal])}  m/k = ${"%6.2f".format(m[t.ordinal] / k)}" +
                    "  m/m(LANE) = ${"%6.2f".format(m[t.ordinal] / lane)}  rms ${"%6.2f".format(sd)}",
            )
        }
    }

    private fun play(i: Int, red: List<String>, blue: List<String>, strength: Int, ev: Evaluator): List<Sample> {
        val level = AiLevel.of(strength)
        val s = TestData.engine.newGame(
            AiHarness.setup(red, blue, level, level, if (i % 2 == 0) Team.RED else Team.BLUE),
        ) as EngineSession
        val players = mapOf(
            Team.RED to AiPlayers.deterministic(strength, 7_000L + 2 * i),
            Team.BLUE to AiPlayers.deterministic(strength, 7_001L + 2 * i),
        )
        val out = ArrayList<DoubleArray>()
        val scores = ArrayList<Double>()
        var lastTurn = -1
        var n = 0
        while (n < 8000) {
            val d = s.pending ?: break
            val st = s.state
            val turn = st.round * 8 + st.turn
            if (d.kind == DecisionKind.PLAY_CARD && st.step == Step.PLAY && turn != lastTurn) {
                lastTurn = turn
                val ctx = ev.context(st)
                out += ctx.termsRed()
                scores += ctx.scoreRed()
            }
            s.answer(d.id, players.getValue(d.team).choose(s, d))
            n++
        }
        val winner = s.state.winner ?: return emptyList()
        return out.mapIndexed { j, t -> Sample(i, t, scores[j], winner == Team.RED) }
    }

    /** Logistic regression without intercept (the evaluation is zero-sum), Newton's method. */
    private fun fit(x: List<DoubleArray>, y: List<Boolean>, l2: Double): DoubleArray {
        val p = x.first().size
        val w = DoubleArray(p)
        repeat(30) {
            val g = DoubleArray(p)
            val h = Array(p) { DoubleArray(p) }
            for ((r, xi) in x.withIndex()) {
                val z = xi.indices.sumOf { w[it] * xi[it] }
                val q = 1 / (1 + exp(-z))
                val e = (if (y[r]) 1.0 else 0.0) - q
                for (a in 0 until p) {
                    g[a] += e * xi[a]
                    for (b in 0 until p) h[a][b] += q * (1 - q) * xi[a] * xi[b]
                }
            }
            for (a in 0 until p) {
                g[a] -= l2 * x.size * w[a]
                h[a][a] += l2 * x.size + 1e-9
            }
            val step = solve(h, g)
            for (a in 0 until p) w[a] += step[a]
            if (step.all { abs(it) < 1e-7 }) return w
        }
        return w
    }

    private fun loss(x: List<DoubleArray>, y: List<Boolean>, w: DoubleArray): Double = x.indices.sumOf { r ->
        val z = x[r].indices.sumOf { w[it] * x[r][it] }
        val q = (1 / (1 + exp(-z))).coerceIn(1e-9, 1 - 1e-9)
        -(if (y[r]) ln(q) else ln(1 - q))
    } / x.size

    private fun solve(a: Array<DoubleArray>, b: DoubleArray): DoubleArray {
        val n = b.size
        val m = Array(n) { i -> a[i].copyOf() + b[i] }
        for (c in 0 until n) {
            val piv = (c until n).maxBy { abs(m[it][c]) }
            val t = m[c]
            m[c] = m[piv]
            m[piv] = t
            if (abs(m[c][c]) < 1e-12) continue
            for (r in 0 until n) {
                if (r == c) continue
                val f = m[r][c] / m[c][c]
                for (k in c..n) m[r][k] -= f * m[c][k]
            }
        }
        return DoubleArray(n) { i -> if (abs(m[i][i]) < 1e-12) 0.0 else m[i][n] / m[i][i] }
    }
}
