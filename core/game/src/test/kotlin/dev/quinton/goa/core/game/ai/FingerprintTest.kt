package dev.quinton.goa.core.game.ai

import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

/**
 * Opt-in: `AI_FINGERPRINT=<variant>:<games>` (variant as in [ExperimentTest], `AI_FORMAT` for the
 * team size) plays self-play games with deterministic players and prints a hash of every game's
 * answers: a change meant only to be faster (cheaper copies, no explanations in search copies) must
 * print the same hashes before and after.
 */
class FingerprintTest {
    @Test
    fun `answers of deterministic games`() {
        val spec = System.getenv("AI_FINGERPRINT")?.split(":") ?: return
        val side = ExperimentTest.side(spec[0])
        val games = spec.getOrNull(1)?.toInt() ?: 2
        val format = AiHarness.Format.parse(System.getenv("AI_FORMAT"))
        val lineups = AiHarness.mixedLineups(games, seed = 31L, size = format.size)
        for ((i, lineup) in lineups.withIndex()) {
            val (red, blue) = lineup
            val t0 = System.nanoTime()
            val r = AiHarness.play(
                AiHarness.setup(red, blue, AiLevel.HARD, AiLevel.HARD, Team.RED, format.length, format.mapId),
                mapOf(Team.RED to side.factory(100L + i), Team.BLUE to side.factory(200L + i)),
            )
            val hash = r.session.save().payload.hashCode()
            val secs = (System.nanoTime() - t0) / 1e9
            println(
                "FINGERPRINT [$format] ${spec[0]} game $i: ${r.decisions} decisions, hash $hash, ${"%.1f".format(
                    secs,
                )} s",
            )
        }
    }
}
