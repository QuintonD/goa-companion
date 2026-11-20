package dev.quinton.goa.core.game.ai.heuristic.heroes

import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.position.position
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

class BroganHeuristicsTest {
    @Test
    fun `brogan finishes 2v2 and 3v3 at strengths 5 and 10`() {
        HeroBookCases.finishes("brogan")
    }

    @Test
    fun `the charge is not played when the only enemy is already adjacent`() {
        // The minion occupies the only straight step of 2 that would land beside it, so the red fizzles (H1).
        val session = position(TestData.engine, TestData.FOREST) {
            turn = 2
            clearMinions()
            hero("brogan", Team.BLUE, Hex(1, -2)) {
                hand("brogan-gold", "brogan-red-1")
            }
            hero("arien", Team.RED, Hex(-5, -4))
            minion(Team.RED, MinionType.MELEE, Hex(2, -2))
            minion(Team.RED, MinionType.MELEE, Hex(4, -4))
            phase = PositionPhase.PlayCards(0)
        }
        HeroBookCases.flips("brogan", BroganHeuristics, session)
    }
}
