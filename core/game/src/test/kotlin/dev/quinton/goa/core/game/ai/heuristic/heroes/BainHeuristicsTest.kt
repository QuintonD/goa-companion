package dev.quinton.goa.core.game.ai.heuristic.heroes

import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.position.position
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

class BainHeuristicsTest {
    @Test
    fun `bain finishes 2v2 and 3v3 at strengths 5 and 10`() {
        HeroBookCases.finishes("bain")
    }

    @Test
    fun `get over here is not the card when nobody is on a line`() {
        // (2,-1) is not in a straight line from (1,-2), so the pull has no target (H5).
        val session = position(TestData.engine, TestData.FOREST) {
            turn = 2
            clearMinions()
            hero("bain", Team.BLUE, Hex(1, -2)) {
                hand("bain-silver", "bain-blue-1")
            }
            hero("brogan", Team.RED, Hex(2, -1)) {
                hand("brogan-gold", "brogan-red-1")
            }
            phase = PositionPhase.PlayCards(0)
        }
        HeroBookCases.flips("bain", BainHeuristics, session)
    }
}
