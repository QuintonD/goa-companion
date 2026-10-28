package dev.quinton.goa.core.game.ai.learned

import dev.quinton.goa.core.game.ai.ExperimentTest
import org.junit.Test

/**
 * Opt-in: `AI_TEAMS=a:b,c:d` plays variant a against b (ExperimentTest's variant syntax plus
 * [Learned.side]'s keys), mirrored, on `AI_N` line-ups of `AI_SIZE` against `AI_SIZE` (default 3)
 * from the line-up family `AI_FAMILY` (train / tune / test, default test) starting at `AI_FIRST`.
 */
class TeamTournamentTest {
    @Test
    fun `variants against each other at any team size`() {
        val pairs = System.getenv("AI_TEAMS")?.split(",") ?: return
        val size = System.getenv("AI_SIZE")?.toIntOrNull() ?: 3
        val n = System.getenv("AI_N")?.toIntOrNull() ?: 8
        val first = System.getenv("AI_FIRST")?.toIntOrNull() ?: 0
        val threads = System.getenv("AI_THREADS")?.toIntOrNull() ?: 4
        val family = when (System.getenv("AI_FAMILY") ?: "test") {
            "train" -> Teams.TRAIN
            "tune" -> Teams.TUNE
            else -> Teams.TEST
        }
        for (pair in pairs) {
            val (a, b) = pair.split(":")
            val t0 = System.nanoTime()
            val score = Teams.mirrored(Learned.side(a), Learned.side(b), Teams.specs(n, size, family, first), threads)
            println("TEAMS ${size}v$size $score in ${"%.0f".format((System.nanoTime() - t0) / 1e9)} s")
            println("TEAMS by hero ($a): ${score.byHero()}")
            if (System.getenv("AI_LINES") != null) score.lines.forEach { println("TEAMS   $it") }
        }
    }
}
