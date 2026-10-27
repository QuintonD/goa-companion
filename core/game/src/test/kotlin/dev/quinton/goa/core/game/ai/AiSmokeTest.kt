package dev.quinton.goa.core.game.ai

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.Controller
import dev.quinton.goa.core.game.GameLength
import dev.quinton.goa.core.game.GameSetup
import dev.quinton.goa.core.game.SeatSetup
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

/** Full AI-vs-AI games finish: every level, 2v2 Quick and Long, 1v1, 2v1 with a Handicap, 3v3 to 5v5, double lane. */
class AiSmokeTest {
    @Test
    fun `every level finishes a full game against itself`() {
        for (level in AiLevel.entries) {
            val (red, blue) = AiHarness.LINEUPS[0]
            val t0 = System.nanoTime()
            val r = AiHarness.play(
                AiHarness.setup(red, blue, level, level, Team.RED),
                mapOf(Team.RED to AiPlayers.deterministic(level, 1), Team.BLUE to AiPlayers.deterministic(level, 2)),
            )
            val secs = "%.1f".format((System.nanoTime() - t0) / 1e9)
            println("SMOKE $level: winner ${r.winner} after ${r.rounds} rounds, ${r.decisions} decisions, $secs s")
            assertThat(r.session.pending).isNull()
            assertThat(r.winner).isNotNull()
        }
    }

    private fun finishes(setup: GameSetup, level: AiLevel = AiLevel.NORMAL) {
        val r = AiHarness.play(
            setup,
            mapOf(
                Team.RED to AiPlayers.deterministic(level, 5),
                Team.BLUE to AiPlayers.deterministic(level, 6),
            ),
        )
        println("SMOKE ${setup.mapId} ${setup.length} ${setup.seats.size} seats: ${r.winner} after ${r.rounds} rounds")
        assertThat(r.session.pending).isNull()
    }

    @Test
    fun `a Long game finishes`() {
        val (red, blue) = AiHarness.LINEUPS[1]
        finishes(AiHarness.setup(red, blue, AiLevel.NORMAL, AiLevel.NORMAL, Team.BLUE, GameLength.LONG))
    }

    @Test
    fun `1v1 and 2v1 with a Handicap card finish`() {
        val ai = Controller.Ai(AiLevel.NORMAL)
        finishes(
            GameSetup(
                TestData.FOREST,
                listOf(SeatSetup("wasp", Team.RED, ai), SeatSetup("brogan", Team.BLUE, ai)),
                tieBreaker = Team.RED,
            ),
        )
        finishes(
            GameSetup(
                TestData.FOREST,
                listOf(
                    SeatSetup("arien", Team.RED, ai),
                    SeatSetup("xargatha", Team.RED, ai, handicap = true),
                    SeatSetup("brogan", Team.BLUE, ai),
                ),
                tieBreaker = Team.BLUE,
            ),
            AiLevel.HARD,
        )
    }

    /** The owner's games (ADR-047): 3v3 on the single lane, 4v4 and 5v5 on the double lane, every hero seated. */
    @Test
    fun `3v3, 4v4 and 5v5 games finish`() {
        for ((i, format) in listOf(AiHarness.Format.THREE, AiHarness.Format.FOUR_RIVER, AiHarness.Format.FIVE_RIVER).withIndex()) {
            val (red, blue) = AiHarness.mixedLineups(1, seed = 900L + i, size = format.size)[0]
            finishes(AiHarness.setup(red, blue, AiLevel.NORMAL, AiLevel.NORMAL, Team.RED, format.length, format.mapId))
        }
        val (red, blue) = AiHarness.mixedLineups(1, seed = 903L, size = 4)[0]
        finishes(AiHarness.setup(red, blue, AiLevel.HARD, AiLevel.HARD, Team.BLUE), AiLevel.HARD)
    }

    @Test
    fun `a double-lane game finishes`() {
        val ai = Controller.Ai(AiLevel.NORMAL)
        finishes(
            GameSetup(
                TestData.RIVER,
                listOf(
                    SeatSetup("wasp", Team.RED, ai),
                    SeatSetup("xargatha", Team.RED, ai),
                    SeatSetup("arien", Team.BLUE, ai),
                    SeatSetup("brogan", Team.BLUE, ai),
                ),
                tieBreaker = Team.RED,
            ),
        )
    }
}
