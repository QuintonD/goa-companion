package dev.quinton.goa.core.game.ai.hybrid

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.AnswerResult
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.ai.AiHarness
import dev.quinton.goa.core.game.ai.AiPlayers
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

/**
 * Strength 10 as the apps seat it (wave 4 track A): one player per team for the whole game, so the memo
 * carries from one answer to the next within a turn, with the same answers as a fresh player per answer;
 * and the app's think time ([HybridConfig.budgetMs]) changes no answer it does not cut short.
 */
class HybridAppTest {
    /** A 2v2 game from its first question, both teams strength 10. */
    private fun game(lineup: Int, seed: Long, size: Int = 2): EngineSession {
        val (red, blue) = AiHarness.mixedLineups(lineup + 1, seed = seed, size = size)[lineup]
        return TestData.engine.newGame(AiHarness.setup(red, blue, AiLevel.EXPERT, AiLevel.EXPERT, Team.RED))
            as EngineSession
    }

    @Test
    fun `one player for the game keeps its memo between answers and answers as a fresh player per answer`() {
        val s = game(0, seed = 31L)
        val kept = mapOf(
            Team.RED to AiPlayers.deterministic(10, 7L) as HybridAi,
            Team.BLUE to AiPlayers.deterministic(10, 8L) as HybridAi,
        )
        var compared = 0
        var carried = 0
        var freshHits = 0L
        while (compared < DECISIONS) {
            val d = s.pending ?: break
            if (d.options.size < 2) {
                assertThat(s.answer(d.id, d.options.single())).isInstanceOf(AnswerResult.Accepted::class.java)
                continue
            }
            val player = kept.getValue(d.team)
            if (player.memoSize > 0) carried++
            val a = player.choose(s, d)
            val fresh = AiPlayers.deterministic(10, if (d.team == Team.RED) 7L else 8L) as HybridAi
            assertThat(fresh.choose(s, d)).isEqualTo(a)
            freshHits += fresh.stats[1]
            compared++
            assertThat(s.answer(d.id, a)).isInstanceOf(AnswerResult.Accepted::class.java)
        }
        assertThat(compared).isEqualTo(DECISIONS)
        // The memo was there when later answers of the same turn started, and it answered more playouts.
        assertThat(carried).isGreaterThan(0)
        assertThat(kept.values.sumOf { it.stats[1] }).isGreaterThan(freshHits)
        assertThat(kept.values.maxOf { it.memoSize }).isAtMost(HybridAi.MEMO_CAP)
    }

    @Test
    fun `a think time the answer fits in changes nothing`() {
        val s = game(1, seed = 32L)
        val plain = AiPlayers.deterministic(10, 7L)
        // An hour: never reached, so the same answers, from the same player the apps build.
        val timed = AiPlayers.forApp(10, budgetMs = HOUR_MS) as HybridAi
        val same = HybridAi(AiPlayers.APP_SEED + 10, AiPlayers.SEARCH_10 + ("leaf" to AiPlayers.SEARCH_10_LEAF.ordinal.toDouble()))
        var compared = 0
        while (compared < BUDGET_DECISIONS) {
            val d = s.pending ?: break
            val a = plain.choose(s, d)
            if (d.options.size > 1) {
                assertThat(timed.choose(s, d)).isEqualTo(same.choose(s, d))
                compared++
            }
            assertThat(s.answer(d.id, a)).isInstanceOf(AnswerResult.Accepted::class.java)
        }
        assertThat(compared).isEqualTo(BUDGET_DECISIONS)
        assertThat(timed.stats[4]).isEqualTo(0L)
        assertThat(timed.config.budgetMs).isEqualTo(HOUR_MS)
    }

    @Test
    fun `a short think time cuts the search and still answers a real option in time`() {
        val s = game(2, seed = 33L)
        val tight = HybridAi(7L, AiPlayers.SEARCH_10 + ("leaf" to AiPlayers.SEARCH_10_LEAF.ordinal.toDouble()) + ("ms" to TIGHT_MS.toDouble()))
        val mover = AiPlayers.deterministic(4, 9L)
        var plays = 0
        var worstOverMs = 0L
        while (plays < TIGHT_PLAYS) {
            val d = s.pending ?: break
            val c = if (d.kind == DecisionKind.PLAY_CARD && d.options.size > 1 && d.team == Team.RED) {
                val t0 = System.nanoTime()
                val a = tight.choose(s, d)
                val ms = (System.nanoTime() - t0) / 1_000_000
                worstOverMs = maxOf(worstOverMs, ms - TIGHT_MS)
                plays++
                assertThat(d.options).contains(a)
                a
            } else {
                mover.choose(s, d)
            }
            assertThat(s.answer(d.id, c)).isInstanceOf(AnswerResult.Accepted::class.java)
        }
        assertThat(plays).isEqualTo(TIGHT_PLAYS)
        // Card plays this short are cut (the search wants several times longer) ...
        assertThat(tight.stats[4]).isGreaterThan(0L)
        // ... and stop near the deadline: what runs past it is the book's own answer and the samples, not playouts.
        println("HYBRID_APP tight budget $TIGHT_MS ms: worst overrun $worstOverMs ms, cut ${tight.stats[4]} of $plays")
        assertThat(worstOverMs).isLessThan(OVERRUN_LIMIT_MS)
    }

    private companion object {
        const val DECISIONS = 24
        const val BUDGET_DECISIONS = 12
        const val HOUR_MS = 3_600_000L
        const val TIGHT_MS = 60L
        const val TIGHT_PLAYS = 6

        /** Generous for a loaded test machine; the measured overrun is printed. */
        const val OVERRUN_LIMIT_MS = 2_000L
    }
}
