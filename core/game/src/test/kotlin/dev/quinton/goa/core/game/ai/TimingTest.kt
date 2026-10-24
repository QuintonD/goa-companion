package dev.quinton.goa.core.game.ai

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

/**
 * Time per decision on this desktop JVM, with the production players (wall-clock cap on). A phone is
 * assumed 3–4× slower: device budgets per card play are Easy 50 ms, Normal 300 ms, Hard 1.5 s,
 * Expert 4 s (AI.md "Budgets"). Asserts only loose bounds: other builds may share the machine.
 */
class TimingTest {
    @Test
    fun `decision times per level`() {
        val budgets = AiLevel.entries.associateWith { AiPlayers.profile(it).budgetMs }
        for (level in AiLevel.entries) {
            val all = LinkedHashMap<DecisionKind, MutableList<Long>>()
            for (g in 0 until 2) {
                val (red, blue) = AiHarness.LINEUPS[g]
                val r = AiHarness.play(
                    AiHarness.setup(red, blue, level, level, Team.RED),
                    mapOf(
                        Team.RED to AiPlayers.forLevel(level, 40L + g),
                        Team.BLUE to AiPlayers.forLevel(level, 50L + g),
                    ),
                    timed = true,
                    timedTeam = Team.RED,
                )
                for ((k, v) in r.timings) all.getOrPut(k) { ArrayList() } += v
            }
            println("TIMING $level (device budget per card ${budgets[level]} ms):")
            for ((k, v) in all) {
                val ms = v.map { it / 1e6 }.sorted()
                val p95 = ms[(ms.size * 0.95).toInt().coerceAtMost(ms.size - 1)]
                println(
                    "TIMING   $k n=${ms.size} mean=${"%.1f".format(ms.average())} p95=${"%.1f".format(p95)} " +
                        "max=${"%.1f".format(ms.last())} ms",
                )
            }
            val play = all.getValue(DecisionKind.PLAY_CARD).map { it / 1e6 }
            // Desktop mean well under the phone budget (a phone is ~3–4× slower).
            assertThat(play.average()).isLessThan(budgets.getValue(level) / 2.0)
        }
    }
}
