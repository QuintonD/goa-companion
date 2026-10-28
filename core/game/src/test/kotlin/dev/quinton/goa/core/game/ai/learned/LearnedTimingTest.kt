package dev.quinton.goa.core.game.ai.learned

import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.ai.AiHarness
import dev.quinton.goa.core.model.match.Team
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import org.junit.Test

/**
 * Opt-in: decision times of variants at any team size, desktop JVM, no wall-clock cap (what the
 * search really costs). `AI_LTIMING=s10,s10+learned=LEAVES` plays each variant against itself on
 * `AI_N` (4) line-ups of `AI_SIZE` (3) against `AI_SIZE` from the test family, `AI_THREADS` games at once.
 */
class LearnedTimingTest {
    @Test
    fun `decision times at any team size`() {
        val specs = System.getenv("AI_LTIMING")?.split(",") ?: return
        val size = System.getenv("AI_SIZE")?.toIntOrNull() ?: 3
        val n = System.getenv("AI_N")?.toIntOrNull() ?: 4
        val threads = System.getenv("AI_THREADS")?.toIntOrNull() ?: 4
        val games = Teams.specs(n, size, Teams.TEST)
        for (spec in specs) {
            val side = Learned.side(spec)
            val pool = Executors.newFixedThreadPool(threads)
            val results = try {
                pool.invokeAll(
                    games.map { g ->
                        Callable {
                            val (red, blue) = g.lineup
                            AiHarness.play(
                                AiHarness.setup(red, blue, AiLevel.NORMAL, AiLevel.NORMAL, g.coin),
                                mapOf(Team.RED to side.factory(g.seed * 2), Team.BLUE to side.factory(g.seed * 2 + 1)),
                                maxDecisions = 20_000,
                                timed = true,
                            )
                        }
                    },
                ).map { it.get() }
            } finally {
                pool.shutdown()
            }
            val all = LinkedHashMap<DecisionKind, MutableList<Long>>()
            for (r in results) for ((k, v) in r.timings) all.getOrPut(k) { ArrayList() } += v
            println("LTIMING $spec ${size}v$size, $n games ($threads at once):")
            for ((k, v) in all.entries.sortedByDescending { it.value.size }) {
                val ms = v.map { it / 1e6 }.sorted()
                val p95 = ms[(ms.size * 0.95).toInt().coerceAtMost(ms.size - 1)]
                println(
                    "LTIMING   $k n=${ms.size} mean=${"%.1f".format(ms.average())} p95=${"%.1f".format(p95)} " +
                        "max=${"%.1f".format(ms.last())} ms",
                )
            }
        }
    }
}
