package dev.quinton.goa.core.game.ai.hybrid

import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.AnswerResult
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.ai.AiHarness
import dev.quinton.goa.core.game.ai.ThreadRunner
import dev.quinton.goa.core.game.ai.heuristic.HeuristicAi
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

/**
 * Opt-in (Wave 3 Track C2): the same card plays asked of a reference search and of cheaper variants, on the
 * reference's own games, so a budget cut is judged on positions before games. `AI_SHADOW=ref;b1;b2` are
 * `x10+…` specs (`seed=n` offsets a variant's seed: the same search with other samples is the noise floor).
 * The reference plays h10 on line-ups `AI_H_FIRST`… (`AI_H_GAMES`, `AI_FORMAT`), its searches on `AI_THREADS`
 * threads. At each of its card plays every variant answers first (timed), then the reference; a variant's
 * regret is the reference's score of its own answer minus that of the variant's (the reference's leaf scale;
 * it favours the reference, so compare variants with the `seed=` control, not with zero).
 */
class HybridShadowTest {
    @Test
    fun `shadow card plays`() {
        val specs = System.getenv("AI_SHADOW")?.split(";")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: return
        val lineups = System.getenv("AI_H_GAMES")?.toIntOrNull() ?: 2
        val first = System.getenv("AI_H_FIRST")?.toIntOrNull() ?: 1000
        val threads = System.getenv("AI_THREADS")?.toIntOrNull() ?: 3
        val format = AiHarness.Format.parse(System.getenv("AI_FORMAT"))
        val rows = specs.indices.map { ArrayList<Row>() }
        val all = AiHarness.mixedLineups(first + lineups, size = format.size)
        ThreadRunner(threads).use { runner ->
            for (i in first until first + lineups) {
                val (red, blue) = all[i]
                val coin = if (i % 2 == 0) Team.RED else Team.BLUE
                val refTeam = if (i % 2 == 0) Team.RED else Team.BLUE
                val seed = (50_000L + i) * 2
                val players = specs.map { spec ->
                    val knobs = knobsOf(spec)
                    HybridAi(seed + (knobs["seed"] ?: 0.0).toLong(), knobs - "seed", runner)
                }
                val h10 = HeuristicAi(10, seed + 1)
                val setup = AiHarness.setup(red, blue, AiLevel.NORMAL, AiLevel.NORMAL, coin, format.length, format.mapId)
                val s = TestData.engine.newGame(setup) as EngineSession
                var n = 0
                while (n < MAX_DECISIONS) {
                    val d = s.pending ?: break
                    val c = if (d.team != refTeam) {
                        h10.choose(s, d)
                    } else if (d.kind != DecisionKind.PLAY_CARD || d.options.size < 2) {
                        players[0].choose(s, d)
                    } else {
                        val answers = specs.indices.reversed().map { k ->
                            var scores: Map<Choice, Double> = emptyMap()
                            var cols = 0
                            players[k].scored = { _, sc, g ->
                                scores = sc
                                cols = g
                            }
                            val t0 = System.nanoTime()
                            val a = players[k].choose(s, d)
                            val ms = (System.nanoTime() - t0) / 1e6
                            players[k].scored = null
                            k to Answer(a, scores, cols, ms)
                        }.toMap()
                        val ref = answers.getValue(0)
                        val refBest = ref.scores[ref.choice] ?: Double.NaN
                        for (k in specs.indices) {
                            val a = answers.getValue(k)
                            val regret = refBest - (ref.scores[a.choice] ?: Double.NaN)
                            rows[k] += Row(a.choice == ref.choice, regret, a.ms, a.cols, ref.scores.size)
                            println(
                                "SHADOW ${d.id} r${s.state.round}t${s.state.turn} ${d.seat} v$k cols=${a.cols} " +
                                    "cands=${ref.scores.size} same=${a.choice == ref.choice} " +
                                    "regret=${"%.4f".format(regret)} ms=${"%.0f".format(a.ms)}",
                            )
                        }
                        ref.choice
                    }
                    check(s.answer(d.id, c) is AnswerResult.Accepted) { "rejected $c" }
                    n++
                }
                println("SHADOW game $i ${red + blue} ref ${refTeam}: winner ${s.state.winner}, round ${s.state.round}")
                report(specs, rows)
            }
        }
    }

    private class Answer(val choice: Choice, val scores: Map<Choice, Double>, val cols: Int, val ms: Double)

    private class Row(val same: Boolean, val regret: Double, val ms: Double, val cols: Int, val cands: Int)

    private fun report(specs: List<String>, rows: List<List<Row>>) {
        for ((k, spec) in specs.withIndex()) {
            val r = rows[k]
            if (r.isEmpty()) continue
            val reg = r.map { it.regret }.filter { !it.isNaN() }.sorted()
            val ms = r.map { it.ms }.sorted()
            fun pct(xs: List<Double>, p: Int) = xs[((xs.size - 1) * p) / 100]
            println(
                "SHADOW v$k ${r.size} plays: same ${"%.1f".format(100.0 * r.count { it.same } / r.size)}%, " +
                    "regret mean ${"%.4f".format(reg.average())} p95 ${"%.4f".format(pct(reg, 95))} " +
                    "max ${"%.4f".format(reg.last())}; ms ${"%.0f".format(ms.average())} / " +
                    "${"%.0f".format(pct(ms, 95))} / ${"%.0f".format(ms.last())}; " +
                    "cols mean ${"%.1f".format(r.map { it.cols }.average())} max ${r.maxOf { it.cols }}; $spec",
            )
        }
    }

    private fun knobsOf(spec: String): Map<String, Double> = spec.split("+").drop(1).associate {
        it.substringBefore("=") to it.substringAfter("=").toDouble()
    }

    private companion object {
        const val MAX_DECISIONS = 8000
    }
}
