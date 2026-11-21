package dev.quinton.goa.core.game.ai.heuristic.heroes

import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.position.position
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

class BainHeuristicsTest {
    @Test
    fun `bain finishes 2v2 and 3v3 at strengths 5 and 10`() {
        HeroBookCases.finishes("bain")
    }

    @Test
    fun `the crossbow is not played through a unit`() {
        // H7: the enemy minion at (3,-2) is in range 2, but Bain's own minion at (2,-2) stands on the line.
        val session = position(TestData.engine, TestData.FOREST) {
            turn = 3
            clearMinions()
            hero("bain", Team.BLUE, Hex(1, -2)) {
                hand("bain-red-1", "bain-green-1")
            }
            hero("brogan", Team.RED, Hex(-5, -4))
            minion(Team.BLUE, MinionType.MELEE, Hex(2, -2))
            minion(Team.RED, MinionType.MELEE, Hex(3, -2))
            phase = PositionPhase.PlayCards(0)
        }
        HeroBookCases.flips("bain", BainHeuristics, session)
    }
}
