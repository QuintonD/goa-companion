package dev.quinton.goa.core.game.ai

import dev.quinton.goa.core.game.AiLevel
import org.junit.Test

/**
 * Opt-in long tournaments (not part of the normal test run): `AI_TOURNAMENT=<seeds>` plays
 * 12 × seeds mirrored games per pair; `AI_PAIRS=HARD:NORMAL,…` picks the pairs, `AI_THREADS` the threads.
 */
class TournamentTest {
    @Test
    fun `long tournaments between neighbouring levels`() {
        val seeds = System.getenv("AI_TOURNAMENT")?.toIntOrNull() ?: return
        val pairs = (System.getenv("AI_PAIRS") ?: "NORMAL:EASY,HARD:NORMAL,EXPERT:HARD").split(",").map {
            val (a, b) = it.split(":")
            AiLevel.valueOf(a) to AiLevel.valueOf(b)
        }
        val first = System.getenv("AI_FIRST_SEED")?.toIntOrNull() ?: 0
        for ((a, b) in pairs) {
            val t0 = System.nanoTime()
            val score = Tournament.run(a, b, seeds, threads = System.getenv("AI_THREADS")?.toIntOrNull() ?: 4, firstSeed = first)
            println("TOURNAMENT $score in ${"%.0f".format((System.nanoTime() - t0) / 1e9)} s")
        }
    }
}
