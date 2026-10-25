package dev.quinton.goa.core.game.ai

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

/**
 * Time per decision on this desktop JVM, with the production players (wall-clock cap on), over
 * line-ups drawn from every playable hero (both teams timed). A phone is assumed 3–4× slower: device
 * budgets per card play run from 50 ms (strengths 1–3) to 4 s (8–10) (AI.md "Budgets"). Asserts only
 * loose bounds: other builds may share the machine. `AI_TIMING_GAMES=8` seats all 32 heroes once
 * per strength and prints the slowest heroes; `AI_TIMING_STRENGTHS=8,9,10` picks the strengths.
 */
class TimingTest {
    @Test
    fun `decision times per level`() {
        val games = System.getenv("AI_TIMING_GAMES")?.toIntOrNull() ?: 2
        val strengths = System.getenv("AI_TIMING_STRENGTHS")?.split(",")?.map { it.trim().toInt() }
            ?: listOf(2, 4, 7, 9, 10)
        val lineups = AiHarness.mixedLineups(games)
        for (strength in strengths) {
            val budget = AiPlayers.profile(strength).budgetMs
            val all = LinkedHashMap<DecisionKind, MutableList<Long>>()
            val byHero = LinkedHashMap<String, MutableList<Long>>()
            for ((g, lineup) in lineups.withIndex()) {
                val (red, blue) = lineup
                val r = AiHarness.play(
                    AiHarness.setup(red, blue, AiLevel.NORMAL, AiLevel.NORMAL, if (g % 2 == 0) Team.RED else Team.BLUE),
                    mapOf(
                        Team.RED to AiPlayers.forStrength(strength, 40L + g),
                        Team.BLUE to AiPlayers.forStrength(strength, 50L + g),
                    ),
                    timed = true,
                )
                for ((k, v) in r.timings) all.getOrPut(k) { ArrayList() } += v
                for ((k, v) in r.playBySeat) byHero.getOrPut(k) { ArrayList() } += v
            }
            println("TIMING strength $strength (device budget per card $budget ms):")
            for ((k, v) in all) {
                val ms = v.map { it / 1e6 }.sorted()
                println(
                    "TIMING   $k n=${ms.size} mean=${"%.1f".format(ms.average())} p95=${"%.1f".format(p95(ms))} " +
                        "max=${"%.1f".format(ms.last())} ms",
                )
            }
            val slow = byHero.mapValues { (_, v) -> v.map { it / 1e6 }.sorted() }.entries
                .sortedByDescending { p95(it.value) }.take(5)
            println(
                "TIMING   slowest heroes (card play p95/max ms): " +
                    slow.joinToString(", ") { (hero, t) -> "$hero ${ms(p95(t))}/${ms(t.last())}" },
            )
            val play = all.getValue(DecisionKind.PLAY_CARD).map { it / 1e6 }
            // Desktop mean well under the phone budget (a phone is ~3–4× slower).
            assertThat(play.average()).isLessThan(budget / 2.0)
        }
    }

    private fun ms(v: Double): String = "%.0f".format(v)

    private fun p95(sorted: List<Double>): Double = sorted[(sorted.size * 0.95).toInt().coerceAtMost(sorted.size - 1)]
}
