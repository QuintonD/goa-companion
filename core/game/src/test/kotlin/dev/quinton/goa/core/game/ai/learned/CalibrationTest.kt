package dev.quinton.goa.core.game.ai.learned

import dev.quinton.goa.core.game.AnswerResult
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.ai.AiPlayers
import dev.quinton.goa.core.game.ai.Evaluator
import dev.quinton.goa.core.game.ai.Searcher
import dev.quinton.goa.core.game.engine.EngineSession
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.ln
import org.junit.Test

/**
 * Opt-in: `AI_CALIBRATE=<games.jsonl>,…` fits the coach's win chance (AI.md "The coach", ADR-051):
 * Platt scaling, P(team wins) = σ(k · score), of the tuned hand evaluation at every turn boundary of the
 * stored self-play games (what a playout to the end of the turn ends on), on line-ups % 10 < 8, and
 * measures it on the held-out line-ups (8, 9): log-loss against the old σ(score / 15), a reliability table
 * per team size and per stage of the game. The result goes into [dev.quinton.goa.core.game.ai.WinChance].
 */
class CalibrationTest {
    private class P(val lineup: Int, val size: Int, val round: Int, val score: Double, val redWon: Boolean)

    @Test
    fun `fit the win chance on self-play`() {
        val spec = System.getenv("AI_CALIBRATE") ?: return
        val threads = System.getenv("AI_THREADS")?.toInt() ?: 3
        val games = Learned.readGames(spec.split(",").map(::File)).filter { it.winner != null }
        val pool = Executors.newFixedThreadPool(threads)
        val all = try {
            pool.invokeAll(games.map { r -> Callable { positions(r) } }).flatMap { it.get() }
        } finally {
            pool.shutdown()
        }
        println("CALIBRATE ${all.size} positions from ${games.size} games")
        val train = all.filter { it.lineup % 10 < 8 }
        val held = all.filter { it.lineup % 10 >= 8 }
        val kAll = fit(train)
        println("CALIBRATE all sizes: k = ${f(kAll)} (1/k = ${f(1 / kAll)}), held-out ${f(loss(held, kAll))}, old ${f(loss(held, 1 / 15.0))}")
        for (size in all.map { it.size }.distinct().sorted()) {
            val tr = train.filter { it.size == size }
            val he = held.filter { it.size == size }
            val k = fit(tr)
            println(
                "CALIBRATE ${size}v$size: n ${tr.size}/${he.size} k = ${f(k)} (1/k = ${f(1 / k)}), held-out ${f(loss(he, k))}, " +
                    "one slope ${f(loss(he, kAll))}, old ${f(loss(he, 1 / 15.0))}, coin flip ${f(ln(2.0))}",
            )
            reliability("${size}v$size k", he, k)
            reliability("${size}v$size old", he, 1 / 15.0)
            for ((name, stage) in STAGES) {
                val ts = tr.filter { it.round in stage }
                val hs = he.filter { it.round in stage }
                if (ts.size < MIN_STAGE || hs.isEmpty()) continue
                val kStage = fit(ts)
                println(
                    "CALIBRATE ${size}v$size $name: n ${ts.size}/${hs.size} k = ${f(kStage)}, held-out own ${f(loss(hs, kStage))}, " +
                        "size slope ${f(loss(hs, k))}",
                )
            }
        }
    }

    private fun positions(r: Learned.GameRecord): List<P> {
        val data = TestData.engine.data
        val ev = Evaluator(data, AiPlayers.TUNED)
        val s = TestData.engine.newGame(r.setup) as EngineSession
        val out = ArrayList<P>()
        var last = -1
        for (c in r.answers) {
            val d = s.pending ?: break
            val st = s.state
            val t = Searcher.turnIndex(st)
            if (t != last && st.winner == null) {
                out += P(r.lineup, r.size, st.round, ev.context(st).scoreRed(), r.winner == "RED")
            }
            last = t
            if (s.answer(d.id, c) !is AnswerResult.Accepted) break
        }
        return out
    }

    private fun fit(ps: List<P>): Double {
        var k = 0.03
        repeat(40) {
            var g = 0.0
            var h = 0.0
            for (p in ps) {
                val q = Evaluator.sigmoid(k * p.score)
                g += (if (p.redWon) 1.0 else 0.0).minus(q) * p.score
                h += q * (1 - q) * p.score * p.score
            }
            val step = g / h
            k += step
            if (abs(step) < 1e-7) return k
        }
        return k
    }

    private fun loss(ps: List<P>, k: Double): Double = ps.sumOf { p ->
        val q = Evaluator.sigmoid(k * p.score).coerceIn(1e-9, 1 - 1e-9)
        -(if (p.redWon) ln(q) else ln(1 - q))
    } / ps.size

    /** Seen from the side the chance favours: of the positions given 60–70%, how many were won? */
    private fun reliability(label: String, ps: List<P>, k: Double) {
        val bins = (0 until 5).map { ArrayList<Pair<Double, Boolean>>() }
        for (p in ps) {
            val q = Evaluator.sigmoid(k * p.score)
            val fav = if (q >= 0.5) q else 1 - q
            val won = if (q >= 0.5) p.redWon else !p.redWon
            bins[((fav - 0.5) / 0.1).toInt().coerceIn(0, 4)] += fav to won
        }
        val text = bins.mapIndexed { i, b ->
            val lo = 50 + i * 10
            if (b.isEmpty()) {
                "$lo-${lo + 10}: -"
            } else {
                "$lo-${lo + 10}: said ${pc(b.map { it.first }.average())} won ${pc(b.count { it.second }.toDouble() / b.size)} (n ${b.size})"
            }
        }
        println("CALIBRATE   $label | ${text.joinToString(" | ")}")
    }

    private fun f(x: Double) = "%.4f".format(x)

    private fun pc(x: Double) = "%.1f%%".format(100 * x)

    private companion object {
        const val MIN_STAGE = 500
        val STAGES = listOf("round 1" to 1..1, "round 2" to 2..2, "round 3" to 3..3, "round 4+" to 4..99)
    }
}
