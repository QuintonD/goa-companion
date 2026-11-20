package dev.quinton.goa.core.game.ai.heuristic.heroes

import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.position.position
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

class EmmittHeuristicsTest {
    @Test
    fun `emmitt finishes 2v2 and 3v3 at strengths 5 and 10`() {
        HeroBookCases.finishes("emmitt")
    }

    @Test
    fun `reverse time is not the opening card`() {
        // Turn 1 wastes the flip (H2). Two minions keep the adjacent one from being the whole lane.
        val session = position(TestData.engine, TestData.FOREST) {
            turn = 1
            clearMinions()
            hero("emmitt", Team.BLUE, Hex(1, -2)) {
                hand("emmitt-gold", "emmitt-green-1")
            }
            hero("arien", Team.RED, Hex(4, -2))
            minion(Team.RED, MinionType.MELEE, Hex(2, -2))
            minion(Team.RED, MinionType.MELEE, Hex(3, -2))
            phase = PositionPhase.PlayCards(0)
        }
        HeroBookCases.flips("emmitt", EmmittHeuristics, session)
    }
}
