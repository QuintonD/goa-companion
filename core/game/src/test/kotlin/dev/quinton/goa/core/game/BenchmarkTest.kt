package dev.quinton.goa.core.game

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Random-legal full games per second on the desktop JVM (ENGINE.md "Performance"; target ≥ 20 for a
 * 2v2 Quick game). Prints the number; asserts only a floor so a slow CI machine doesn't fail it.
 */
class BenchmarkTest {
    @Test
    fun `random 2v2 quick games per second`() {
        repeat(5) { playOut(TestData.newGame(), RandomPlayer(1000L + it)) } // warm-up
        val games = 60
        var decisions = 0L
        val start = System.nanoTime()
        for (i in 0 until games) {
            val s = TestData.newGame()
            decisions += playOut(s, RandomPlayer(i.toLong()))
            assertThat(s.pending).isNull()
        }
        val seconds = (System.nanoTime() - start) / 1e9
        val rate = games / seconds
        println("BENCHMARK: $games random 2v2 Quick games in ${"%.2f".format(seconds)} s = ${"%.1f".format(rate)} games/s, ${"%.0f".format(decisions / seconds)} decisions/s")
        assertThat(rate).isGreaterThan(2.0)
    }
}
