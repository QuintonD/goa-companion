package dev.quinton.goa.core.game.ai

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

class AiSmokeTest {
    @Test
    fun `every level finishes a full game against itself`() {
        for (level in AiLevel.entries) {
            val (red, blue) = AiHarness.LINEUPS[0]
            val t0 = System.nanoTime()
            val r = AiHarness.play(
                AiHarness.setup(red, blue, level, level, Team.RED),
                mapOf(Team.RED to AiPlayers.deterministic(level, 1), Team.BLUE to AiPlayers.deterministic(level, 2)),
                timed = true,
            )
            val secs = (System.nanoTime() - t0) / 1e9
            println("SMOKE $level: winner ${r.winner} after ${r.rounds} rounds, ${r.decisions} decisions, ${"%.1f".format(secs)} s")
            for ((k, v) in r.timings) {
                val ms = v.map { it / 1e6 }
                println("   $k n=${ms.size} mean=${"%.1f".format(ms.average())} max=${"%.1f".format(ms.max())} ms")
            }
            assertThat(r.session.pending).isNull()
        }
    }
}
