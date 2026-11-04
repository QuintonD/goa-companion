package dev.quinton.goa.core.game.ai

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.AnswerResult
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.game.engine.GameState
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

/**
 * The per-hero evaluation table (ADR-063): data keyed by hero id, applied only where the evaluation
 * scores that hero; a hero the table does not know, or a row of ones, changes nothing.
 */
class HeroScalesTest {
    private val red = listOf("trinkets", "brogan", "gydion")
    private val blue = listOf("nebkher", "razzle", "wasp")

    /** States along a 3v3 game between static players (cheap), a few dozen answers apart. */
    private fun states(): List<GameState> {
        val s = TestData.engine.newGame(
            AiHarness.setup(red, blue, AiLevel.NORMAL, AiLevel.NORMAL, Team.RED),
        ) as EngineSession
        val players = mapOf(Team.RED to AiPlayers.deterministic(4, 1), Team.BLUE to AiPlayers.deterministic(4, 2))
        val out = ArrayList<GameState>()
        var n = 0
        while (n < 600) {
            val d = s.pending ?: break
            check(s.answer(d.id, players.getValue(d.team).choose(s, d)) is AnswerResult.Accepted)
            if (++n % 25 == 0) out += s.state
        }
        return out
    }

    @Test
    fun `a hero the table does not know, or a row of ones, changes nothing`() {
        val data = TestData.engine.data
        val base = Evaluator(data, AiPlayers.TUNED)
        val unknown = Evaluator(data, AiPlayers.TUNED.copy(heroScales = mapOf("nobody" to mapOf(Term.EXPOSURE to 3.0))))
        val ones = Evaluator(
            data,
            AiPlayers.TUNED.copy(heroScales = (red + blue).associateWith { Term.entries.associateWith { 1.0 } }),
        )
        val states = states()
        assertThat(states.size).isAtLeast(10)
        for (st in states) {
            for (ev in listOf(unknown, ones)) {
                assertThat(ev.evaluate(st, Team.RED)).isEqualTo(base.evaluate(st, Team.RED))
                val a = base.context(st)
                val b = ev.context(st)
                for (h in st.heroes) {
                    val at = h.at ?: continue
                    assertThat(b.placement(h.id, at)).isEqualTo(a.placement(h.id, at))
                }
            }
        }
    }

    @Test
    fun `a row scales only its hero's parts, on either team`() {
        val data = TestData.engine.data
        val base = Evaluator(data, EvalKnobs())
        val table =
            Evaluator(
                data,
                EvalKnobs(heroScales = mapOf("trinkets" to mapOf(Term.EXPOSURE to 2.0, Term.PRESENCE to 0.0))),
            )
        val team = Evaluator(data, EvalKnobs(termsByTeam = mapOf(Team.BLUE to mapOf(Term.PRESENCE to 2.0))))
        var checked = 0
        for (st in states()) {
            val a = base.context(st)
            val b = table.context(st)
            val c = team.context(st)
            for (h in st.heroes) {
                val at = h.at ?: continue
                val hi = a.info(h.id)
                val placed = a.placement(h.id, at)
                when {
                    h.id == "trinkets" -> {
                        assertThat(b.placement(h.id, at))
                            .isWithin(1e-9).of(placed - a.presence(h.team, at) - a.exposure(hi, at))
                        checked++
                    }

                    else -> assertThat(b.placement(h.id, at)).isEqualTo(placed)
                }
                val expected = if (h.team == Team.BLUE) placed + a.presence(h.team, at) else placed
                assertThat(c.placement(h.id, at)).isWithin(1e-9).of(expected)
            }
        }
        assertThat(checked).isGreaterThan(0)
    }
}
