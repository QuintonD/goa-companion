package dev.quinton.goa.core.game.ai.heuristic.heroes

import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.position.position
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

class GarrusHeuristicsTest {
    @Test
    fun `garrus finishes 2v2 and 3v3 at strengths 5 and 10`() {
        HeroBookCases.finishes("garrus")
    }

    @Test
    fun `howl is not the card when nobody is in the cage`() {
        // H2: Arien at (-5,-4) is outside radius 3; a Howl would only buy a discard.
        val session = position(TestData.engine, TestData.FOREST) {
            turn = 3
            clearMinions()
            hero("garrus", Team.BLUE, Hex(1, -2)) {
                hand("garrus-silver", "garrus-red-1")
            }
            hero("arien", Team.RED, Hex(-5, -4)) {
                hand("arien-gold", "arien-red-1")
            }
            phase = PositionPhase.PlayCards(0)
        }
        HeroBookCases.flips("garrus", GarrusHeuristics, session)
    }
}
