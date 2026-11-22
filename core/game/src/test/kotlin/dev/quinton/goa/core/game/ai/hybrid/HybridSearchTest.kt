package dev.quinton.goa.core.game.ai.hybrid

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.AnswerResult
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.ai.AiHarness
import dev.quinton.goa.core.game.ai.AiPlayers
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

    /**
     * The shipped strength 10 (AiPlayers.SEARCH_10, the memo and merged samples on): three threads give the
     * sequential answers, every answer is a real option, and the memo changes no answer (it only saves work).
     */
    @Test
    fun `strength 10 answers alike on any runner and with or without its memo`() {
        ThreadRunner(3).use { threads ->
            val (red, blue) = AiHarness.mixedLineups(2, seed = 4242L)[1]
            val s = TestData.engine.newGame(AiHarness.setup(red, blue, AiLevel.EXPERT, AiLevel.EXPERT, Team.BLUE))
                as EngineSession
            val noMemo = AiPlayers.SEARCH_10 + ("tt" to 0.0) + ("leaf" to AiPlayers.SEARCH_10_LEAF.ordinal.toDouble())
            val one = mapOf(Team.RED to AiPlayers.deterministic(10, 7L), Team.BLUE to AiPlayers.deterministic(10, 8L))
            val many = mapOf(
                Team.RED to AiPlayers.forStrength(10, 7L, threads),
                Team.BLUE to AiPlayers.forStrength(10, 8L, threads),
            )
            val plain = mapOf(Team.RED to HybridAi(7L, noMemo), Team.BLUE to HybridAi(8L, noMemo))
            var compared = 0
            while (compared < SHIPPED_DECISIONS) {
                val d = s.pending ?: break
                val a = one.getValue(d.team).choose(s, d)
                assertThat(d.options).contains(a)
                if (d.options.size > 1) {
                    assertThat(many.getValue(d.team).choose(s, d)).isEqualTo(a)
                    assertThat(plain.getValue(d.team).choose(s, d)).isEqualTo(a)
                    compared++
                }
                assertThat(s.answer(d.id, a)).isInstanceOf(AnswerResult.Accepted::class.java)
            }
            assertThat(compared).isEqualTo(SHIPPED_DECISIONS)
        }
    }

    /**
     * The tree after the reveal (`td`): legal answers, the same on three threads as on one, and the same
     * with the memo off (the frontier playouts it remembers are the ones it would play).
     */
    @Test
    fun `the tree after the reveal answers alike on any runner and with or without the memo`() {
        ThreadRunner(3).use { threads ->
            val (red, blue) = AiHarness.mixedLineups(3, seed = 99L)[2]
            val s = TestData.engine.newGame(AiHarness.setup(red, blue, AiLevel.EXPERT, AiLevel.EXPERT, Team.RED))
                as EngineSession
            val knobs = mapOf("pol" to 1.0, "scope" to 3.0, "tt" to 1.0, "K" to 4.0, "Ko" to 2.0, "td" to 2.0, "tw" to 2.0)
            val one = mapOf(Team.RED to HybridAi(7L, knobs), Team.BLUE to HybridAi(8L, knobs))
            val many = mapOf(Team.RED to HybridAi(7L, knobs, threads), Team.BLUE to HybridAi(8L, knobs, threads))
            val noMemo = knobs + ("tt" to 0.0)
            val plain = mapOf(Team.RED to HybridAi(7L, noMemo), Team.BLUE to HybridAi(8L, noMemo))
            var compared = 0
            while (compared < DECISIONS) {
                val d = s.pending ?: break
                val a = one.getValue(d.team).choose(s, d)
                assertThat(d.options).contains(a)
                if (d.options.size > 1) {
                    assertThat(many.getValue(d.team).choose(s, d)).isEqualTo(a)
                    assertThat(plain.getValue(d.team).choose(s, d)).isEqualTo(a)
                    compared++
                }
                assertThat(s.answer(d.id, a)).isInstanceOf(AnswerResult.Accepted::class.java)
            }
            assertThat(compared).isEqualTo(DECISIONS)
        }
    }

    private companion object {
        const val DECISIONS = 30
        const val SHIPPED_DECISIONS = 40
    }
}
