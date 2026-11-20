package dev.quinton.goa.core.game.ai.heuristic.heroes

import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.position.position
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

class ArienHeuristicsTest {
    @Test
    fun `arien finishes 2v2 and 3v3 at strengths 5 and 10`() {
        HeroBookCases.finishes("arien")
    }

    @Test
    fun `spell break is not spent when the radius holds only attacks`() {
        // (1,-2) to (4,-2) is 3, inside Spell Break. Brogan's hand is attacks, so H3 refuses the silver.
        val session = position(TestData.engine, TestData.FOREST) {
            turn = 2
            clearMinions()
            hero("arien", Team.BLUE, Hex(1, -2)) {
                hand("arien-silver", "arien-green-1", "arien-red-1")
            }
            hero("brogan", Team.RED, Hex(4, -2)) {
                hand("brogan-gold", "brogan-red-1")
            }
            phase = PositionPhase.PlayCards(0)
        }
        HeroBookCases.flips("arien", ArienHeuristics, session)
    }
}
