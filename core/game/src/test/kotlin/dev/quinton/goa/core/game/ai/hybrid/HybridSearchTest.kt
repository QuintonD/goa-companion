package dev.quinton.goa.core.game.ai.hybrid

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.AnswerResult
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.ai.AiHarness
import dev.quinton.goa.core.game.ai.ThreadRunner
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

/**
 * The hybrid search prototype (REVIEW-2-search.md): every answer is one of the real options, and an
 * answer depends only on the seed, the decision and the session, whatever runner runs its samples
 * (no peeking is in `NoPeekingTest`).
 */
class HybridSearchTest {
    @Test
    fun `a threaded runner answers like the sequential one, with legal answers`() {
        ThreadRunner(3).use { threads ->
            val (red, blue) = AiHarness.mixedLineups(1, seed = 777L)[0]
            val s = TestData.engine.newGame(AiHarness.setup(red, blue, AiLevel.HARD, AiLevel.HARD, Team.RED))
                as EngineSession
            val knobs = mapOf("K" to 4.0, "Ko" to 2.0, "scope" to 3.0, "hz" to 3.0)
            val one = mapOf(Team.RED to HybridAi(7L, knobs), Team.BLUE to HybridAi(8L, knobs))
            val many = mapOf(Team.RED to HybridAi(7L, knobs, threads), Team.BLUE to HybridAi(8L, knobs, threads))
            var compared = 0
            while (compared < DECISIONS) {
                val d = s.pending ?: break
                val a = one.getValue(d.team).choose(s, d)
                assertThat(d.options).contains(a)
                if (d.options.size > 1) {
                    assertThat(many.getValue(d.team).choose(s, d)).isEqualTo(a)
                    compared++
                }
                assertThat(s.answer(d.id, a)).isInstanceOf(AnswerResult.Accepted::class.java)
            }
            assertThat(compared).isEqualTo(DECISIONS)
        }
    }

    private companion object {
        const val DECISIONS = 30
    }
}
