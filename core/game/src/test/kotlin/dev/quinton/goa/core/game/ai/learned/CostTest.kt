package dev.quinton.goa.core.game.ai.learned

import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.ai.AiBenchmark
import dev.quinton.goa.core.game.ai.SearchAi
import org.junit.Test

/**
 * Opt-in (ADR-060): what a variant's card play costs in time at the same work, where the work measure
 * counts engine answers only and not the evaluation. `AI_COST=s10+work=15500,s10+work=15500+net=avg(a,b,c)`
 * answers the speed measure's fixed 3v3 card plays ([AiBenchmark.positions]) with every variant on one
 * thread, the variants interleaved position by position so a busy machine slows all alike; `AI_COST_N`
 * card plays (default 120) after 20 for warming up. Prints time and work per card play and the answers
 * per second against the first variant's: what a phone would pay for the same work.
 */
class CostTest {
    @Test
    fun `time per card play at the same work`() {
        val specs = System.getenv("AI_COST")?.let { Learned.splitTop(it, ',') } ?: return
        val n = System.getenv("AI_COST_N")?.toIntOrNull() ?: 120
        val warm = 20
        val positions = AiBenchmark.positions(TestData.engine).take(n + warm)
        val players = specs.map { spec ->
            val strength = spec.substringBefore("+").removePrefix("s").toInt()
            SearchAi(AiLevel.of(strength), Learned.profile(spec), SEED, timeCap = false)
        }
        val times = specs.map { ArrayList<Long>() }
        val works = specs.map { ArrayList<Long>() }
        for ((i, s) in positions.withIndex()) {
            for (j in specs.indices) {
                // A rotating order: no variant always runs first on a position.
                val v = (i + j) % specs.size
                val p = players[v]
                val w0 = p.work
                val t0 = System.nanoTime()
                p.choose(s, s.pending!!) { false }
                val t = System.nanoTime() - t0
                if (i >= warm) {
                    times[v] += t
                    works[v] += p.work - w0
                }
            }
        }
        val base = rate(works[0], times[0])
        for ((v, spec) in specs.withIndex()) {
            val ms = times[v].map { it / 1e6 }.sorted()
            val r = rate(works[v], times[v])
            println(
                "COST $spec: card play mean ${"%.1f".format(ms.average())} median ${"%.1f".format(ms[ms.size / 2])} " +
                    "p95 ${"%.1f".format(ms[(ms.size * 0.95).toInt().coerceAtMost(ms.size - 1)])} ms, " +
                    "work ${works[v].average().toLong()}, ${"%.0f".format(r)} answers/s, " +
                    "time per answer ×${"%.3f".format(base / r)} the first's",
            )
        }
    }

    private fun rate(w: List<Long>, t: List<Long>): Double = w.sum() * 1e9 / t.sum()

    private companion object {
        const val SEED = 20_261_103L
    }
}
