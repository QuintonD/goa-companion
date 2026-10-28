package dev.quinton.goa.core.game.ai

import dev.quinton.goa.core.game.SavedGame
import dev.quinton.goa.core.game.TestData
import java.io.File
import kotlinx.serialization.json.Json
import org.junit.Test

/**
 * Opt-in: `AI_REPRO=<dump file>|<variant>|<seed>` restores a game saved by `AI_DUMP_DIR` (AiHarness)
 * and asks the computer player that crashed for its answer again, to reproduce an engine crash met
 * inside a search.
 */
class CrashReproTest {
    @Test
    fun `reproduce a crash from a dump`() {
        val spec = System.getenv("AI_REPRO")?.split("|") ?: return
        val saved = Json.decodeFromString(SavedGame.serializer(), File(spec[0]).readText())
        val s = TestData.engine.restore(saved)
        val d = s.pending ?: error("No decision pending")
        println("REPRO decision ${d.id} ${d.seat} ${d.kind} ${d.prompt}")
        val player = ExperimentTest.side(spec[1]).factory(spec[2].toLong())
        println("REPRO answer ${player.choose(s, d)}")
    }
}
