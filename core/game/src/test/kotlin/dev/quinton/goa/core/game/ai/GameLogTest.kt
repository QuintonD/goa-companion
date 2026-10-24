package dev.quinton.goa.core.game.ai

import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

/** Opt-in: `AI_LOG=RED_LEVEL:BLUE_LEVEL[:seed[:lineup]]` prints one game's log, to read how a level plays. */
class GameLogTest {
    @Test
    fun `print one game`() {
        val spec = System.getenv("AI_LOG")?.split(":") ?: return
        val red = AiLevel.valueOf(spec[0])
        val blue = AiLevel.valueOf(spec[1])
        val seed = spec.getOrNull(2)?.toLong() ?: 1L
        val (r, b) = AiHarness.LINEUPS[spec.getOrNull(3)?.toInt() ?: 0]
        val s = TestData.engine.newGame(AiHarness.setup(r, b, red, blue, Team.RED))
        val players = mapOf(Team.RED to AiPlayers.deterministic(red, seed), Team.BLUE to AiPlayers.deterministic(blue, seed + 1))
        var printed = 0
        var n = 0
        while (n < 6000) {
            val d = s.pending ?: break
            val c = players.getValue(d.team).choose(s, d)
            s.answer(d.id, c)
            for (e in s.events.drop(printed)) println("LOG ${e.text}")
            printed = s.events.size
            n++
        }
        println("LOG winner ${s.view(null).winner} ${s.view(null).winReason}")
    }
}
