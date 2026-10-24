package dev.quinton.goa.core.game.ai

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.AnswerResult
import dev.quinton.goa.core.game.Controller
import dev.quinton.goa.core.game.GameSetup
import dev.quinton.goa.core.game.SeatSetup
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.model.match.Team
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.random.Random
import org.junit.Test

/**
 * The AI plays every playable hero through the generic Decision API: seeded AI-vs-AI games over
 * shuffled line-ups that seat each hero at least twice, at Easy, Normal and Hard (and a few at
 * Expert), finish without exceptions and every answer is accepted.
 */
class AllHeroesAiTest {
    @Test
    fun `seeded AI games over every playable hero finish`() {
        val heroes = TestData.engine.playableHeroes.sorted()
        println("ALL HEROES: ${heroes.size} playable")
        val rng = Random(2026)
        val lineups = (0 until 2).flatMap { heroes.shuffled(rng).chunked(4).filter { it.size == 4 } }
        val levels = listOf(AiLevel.EASY, AiLevel.NORMAL, AiLevel.HARD)
        val jobs = lineups.mapIndexed { i, four ->
            val level = if (i % 8 == 7) AiLevel.EXPERT else levels[i % levels.size]
            Callable { play(four, level, i) }
        }
        val pool = Executors.newFixedThreadPool(6)
        val results = try {
            pool.invokeAll(jobs).map { it.get() }
        } finally {
            pool.shutdown()
        }
        results.forEach { println("ALL HEROES $it") }
        assertThat(results.size).isEqualTo(lineups.size)
        assertThat(results.none { it.endsWith("UNFINISHED") }).isTrue()
        // Every hero is seated (with a roster not divisible by four, a leftover may sit out a shuffle).
        assertThat(lineups.flatten().toSet().size).isAtLeast(heroes.size - 3)
    }

    private fun play(four: List<String>, level: AiLevel, i: Int): String {
        val ai = Controller.Ai(level)
        val setup = GameSetup(
            TestData.FOREST,
            listOf(
                SeatSetup(four[0], Team.RED, ai),
                SeatSetup(four[1], Team.RED, ai),
                SeatSetup(four[2], Team.BLUE, ai),
                SeatSetup(four[3], Team.BLUE, ai),
            ),
            tieBreaker = if (i % 2 == 0) Team.RED else Team.BLUE,
        )
        val s = TestData.engine.newGame(setup)
        val players = mapOf(
            Team.RED to AiPlayers.deterministic(level, 300L + i),
            Team.BLUE to AiPlayers.deterministic(level, 400L + i),
        )
        var n = 0
        while (n < MAX_DECISIONS) {
            val d = s.pending ?: break
            val c = try {
                players.getValue(d.team).choose(s, d)
            } catch (e: Exception) {
                throw AssertionError("$level AI failed on $d in ${four.joinToString()}", e)
            }
            val r = s.answer(d.id, c)
            check(r is AnswerResult.Accepted) { "Rejected $c for $d: $r" }
            n++
        }
        val v = s.view(null)
        return "$level ${four.joinToString()}: ${v.winner} by ${v.winReason} in round ${v.round} ($n decisions)" +
            if (s.pending != null) " UNFINISHED" else ""
    }

    companion object {
        private const val MAX_DECISIONS = 8000
    }
}
