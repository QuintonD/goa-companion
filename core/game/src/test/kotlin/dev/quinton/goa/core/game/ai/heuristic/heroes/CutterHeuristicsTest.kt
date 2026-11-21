package dev.quinton.goa.core.game.ai.heuristic.heroes

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.position.position
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

class CutterHeuristicsTest {
    @Test
    fun `cutter finishes 2v2 and 3v3 at strengths 5 and 10`() {
        HeroBookCases.finishes("cutter")
    }

    @Test
    fun `the dash is played when a straight 3 lands beside a hero`() {
        // H6: (-2,-2) is 3 straight from (1,-2) and beside Arien at (-3,-2). Bombardment has no target (H8).
        val session = position(TestData.engine, TestData.FOREST) {
            turn = 4
            clearMinions()
            hero("cutter", Team.BLUE, Hex(1, -2)) {
                hand("cutter-green-1", "cutter-blue-1")
            }
            hero("arien", Team.RED, Hex(-3, -2)) {
                hand("arien-gold", "arien-red-1")
            }
            phase = PositionPhase.PlayCards(0)
        }
        HeroBookCases.flips("cutter", CutterHeuristics, session)
    }

    @Test
    fun `a Fistful of Coins in hand counts for her own team only`() {
        // H16 / review H1: which tier-III blue she kept is hidden from the other team (sampled below 10).
        val session = position(TestData.engine, TestData.FOREST) {
            clearMinions()
            hero("cutter", Team.BLUE, Hex(1, -2)) {
                coins = 11
                hand("cutter-blue-3b", "cutter-gold")
            }
            hero("arien", Team.RED, Hex(-3, -2))
            phase = PositionPhase.PlayCards(0)
        }
        val state = session.state
        assertThat(CutterHeuristics.evalTerms(state, Team.BLUE, "cutter")).isGreaterThan(0.0)
        assertThat(CutterHeuristics.evalTerms(state, Team.RED, "cutter")).isEqualTo(0.0)
    }
}
