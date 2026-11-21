package dev.quinton.goa.core.game.ai.heuristic.heroes

import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.position.position
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

class DodgerHeuristicsTest {
    @Test
    fun `dodger finishes 2v2 and 3v3 at strengths 5 and 10`() {
        HeroBookCases.finishes("dodger")
    }

    @Test
    fun `death trap is not the card when no hero stands on an empty spawn`() {
        // H2: Arien at (-5,-4) is outside radius 4, so Death Trap has nobody to hit.
        val session = position(TestData.engine, TestData.FOREST) {
            turn = 3
            clearMinions()
            hero("dodger", Team.BLUE, Hex(1, -2)) {
                hand("dodger-silver", "dodger-red-1")
            }
            hero("arien", Team.RED, Hex(-5, -4)) {
                hand("arien-gold", "arien-red-1")
            }
            phase = PositionPhase.PlayCards(0)
        }
        HeroBookCases.flips("dodger", DodgerHeuristics, session)
    }
}
