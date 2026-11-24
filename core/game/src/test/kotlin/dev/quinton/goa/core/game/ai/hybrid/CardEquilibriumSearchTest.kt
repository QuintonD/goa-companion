package dev.quinton.goa.core.game.ai.hybrid

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.AnswerResult
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.ai.AiHarness
import dev.quinton.goa.core.game.ai.AiPlayers
import dev.quinton.goa.core.game.ai.ThreadRunner
import dev.quinton.goa.core.game.ai.heuristic.HeuristicAi
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

/** Wave 4 Track G: strength 10 with `eq=1` in real positions (the solver itself is in `CardEquilibriumTest`). */
class CardEquilibriumSearchTest {
    private val knobs = AiPlayers.SEARCH_10 + ("leaf" to AiPlayers.SEARCH_10_LEAF.ordinal.toDouble()) + ("eq" to 1.0)

    /** Legal answers, built from a real matrix, the same on three threads as on one and on a fresh copy. */
    @Test
    fun `eq card plays are legal and deterministic on any runner`() {
        ThreadRunner(3).use { threads ->
            val (red, blue) = AiHarness.mixedLineups(2, seed = 4242L)[1]
            val s = TestData.engine.newGame(AiHarness.setup(red, blue, AiLevel.EXPERT, AiLevel.EXPERT, Team.RED))
                as EngineSession
            val book = HeuristicAi(10, 3L)
            val one = HybridAi(7L, knobs)
            val again = HybridAi(7L, knobs)
            val many = HybridAi(7L, knobs, threads)
            var built = 0
            one.equilibriumProbe = { _, m, p ->
                built++
                assertThat(m.payoff.all { row -> row.all { it in 0.0..1.0 } }).isTrue()
                assertThat(p.played.sum()).isWithin(1e-9).of(1.0)
            }
            var compared = 0
            while (compared < PLAYS) {
                val d = s.pending ?: break
                val card = d.kind == DecisionKind.PLAY_CARD && d.options.size > 1 && d.options.all { it is Choice.Card }
                val c = if (card) {
                    val a = one.choose(s, d)
                    assertThat(d.options).contains(a)
                    assertThat(many.choose(s, d)).isEqualTo(a)
                    assertThat(again.choose(s, d)).isEqualTo(a)
                    compared++
                    a
                } else {
                    book.choose(s, d)
                }
                assertThat(s.answer(d.id, c)).isInstanceOf(AnswerResult.Accepted::class.java)
            }
            assertThat(compared).isEqualTo(PLAYS)
            assertThat(built).isEqualTo(PLAYS)
        }
    }

    /**
     * No peeking: the enemy's facedown card is the only hidden thing, so a Blue card play must build the same
     * matrix and play the same card whichever card Red really put down.
     */
    @Test
    fun `the matrix does not see the enemy's facedown card`() {
        val (red, blue) = AiHarness.mixedLineups(3, seed = 99L)[2]
        val setup = AiHarness.setup(red, blue, AiLevel.EXPERT, AiLevel.EXPERT, Team.RED)
        val a = TestData.engine.newGame(setup) as EngineSession
        val b = TestData.engine.newGame(setup) as EngineSession
        val book = HeuristicAi(10, 5L)
        var diverged = false
        while (true) {
            val d = a.pending ?: error("game ended before a Blue card play")
            val db = b.pending ?: error("game ended before a Blue card play")
            assertThat(db.id).isEqualTo(d.id)
            if (diverged && d.team == Team.BLUE && d.kind == DecisionKind.PLAY_CARD) break
            val c = book.choose(a, d)
            val cb = if (!diverged && d.team == Team.RED && d.kind == DecisionKind.PLAY_CARD && d.options.size > 1) {
                diverged = true
                d.options.first { it != c }
            } else {
                c
            }
            assertThat(a.answer(d.id, c)).isInstanceOf(AnswerResult.Accepted::class.java)
            assertThat(b.answer(db.id, cb)).isInstanceOf(AnswerResult.Accepted::class.java)
        }
        val seen = ArrayList<String>()
        for (s in listOf(a, b)) {
            val ai = HybridAi(11L, knobs)
            ai.equilibriumProbe = { _, m, p ->
                seen += m.rows.toString() + m.columns + m.payoff.joinToString { it.contentToString() } + p.row
            }
            val d = s.pending!!
            seen += ai.choose(s, d).toString()
        }
        assertThat(seen).hasSize(4)
        assertThat(seen[2]).isEqualTo(seen[0])
        assertThat(seen[3]).isEqualTo(seen[1])
    }

    private companion object {
        const val PLAYS = 6
    }
}
