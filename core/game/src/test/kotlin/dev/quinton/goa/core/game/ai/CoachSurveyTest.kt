package dev.quinton.goa.core.game.ai

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

/**
 * How often the coach speaks for a stand-in "human" (an Easy or Normal player) over whole games, and
 * what it says. The coach should be quiet most of the time: false positives annoy learners.
 */
class CoachSurveyTest {
    @Test
    fun `the coach speaks rarely and only with a reason`() {
        val coach = AiCoach(TestData.engine)
        val games = System.getenv("AI_COACH_GAMES")?.toIntOrNull() ?: 1
        // Line-ups from every playable hero: the coach must stay quiet about mechanics it does not read.
        val lineups = AiHarness.mixedLineups(games, seed = 77L)
        var asked = 0
        var spoke = 0
        var maxMs = 0.0
        var totalMs = 0.0
        for (g in 0 until games) {
            val human = if (g % 2 == 0) AiLevel.EASY else AiLevel.NORMAL
            val (red, blue) = lineups[g]
            val s = TestData.engine.newGame(AiHarness.setup(red, blue, human, AiLevel.HARD, Team.RED))
            val players = mapOf(
                Team.RED to AiPlayers.deterministic(human, 70L + g),
                Team.BLUE to AiPlayers.deterministic(AiLevel.HARD, 80L + g),
            )
            var n = 0
            while (n < 6000) {
                val d = s.pending ?: break
                val c = players.getValue(d.team).choose(s, d)
                if (d.team == Team.RED && d.options.size > 1) {
                    asked++
                    val t0 = System.nanoTime()
                    val note = coach.nudge(s, d, c)
                    val ms = (System.nanoTime() - t0) / 1e6
                    totalMs += ms
                    maxMs = maxOf(maxMs, ms)
                    if (note != null) {
                        spoke++
                        println("NUDGE [$human ${d.seat} ${d.kind} r${note.round}t${note.turn}] ${note.text} | ${note.detail}")
                    }
                }
                s.answer(d.id, c)
                n++
            }
        }
        println(
            "COACH SURVEY: spoke $spoke times in $asked decisions (${"%.1f".format(100.0 * spoke / asked)}%), " +
                "mean ${"%.0f".format(totalMs / asked)} ms, max ${"%.0f".format(maxMs)} ms",
        )
        assertThat(spoke.toDouble() / asked).isLessThan(0.15)
    }
}
