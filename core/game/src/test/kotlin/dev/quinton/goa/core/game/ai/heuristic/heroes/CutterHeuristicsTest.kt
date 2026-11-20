package dev.quinton.goa.core.game.ai.heuristic.heroes

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
    fun `the plank is not the turn-1 card`() {
        // Two minions, so the adjacent one is not the last in the lane. H2 holds the plank; H4 plays the grapple.
        val session = position(TestData.engine, TestData.FOREST) {
            turn = 1
            clearMinions()
            hero("cutter", Team.BLUE, Hex(1, -2)) {
                hand("cutter-gold", "cutter-silver")
            }
            hero("arien", Team.RED, Hex(4, -2))
            minion(Team.RED, MinionType.MELEE, Hex(2, -2))
            minion(Team.RED, MinionType.MELEE, Hex(3, -2))
            phase = PositionPhase.PlayCards(0)
        }
        HeroBookCases.flips("cutter", CutterHeuristics, session)
    }
}
