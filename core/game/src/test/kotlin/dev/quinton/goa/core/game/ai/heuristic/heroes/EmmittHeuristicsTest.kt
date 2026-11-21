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
    fun `the punch is not the kill against a hand of fast cards`() {
        // H3: Arien's cards are Defense 2-5 but Initiative 9-13. The punch (Attack 9) is defended with
        // Initiative, so it only costs a card; gold is the cheaper hit.
        val session = position(TestData.engine, TestData.FOREST) {
            turn = 2
            clearMinions()
            hero("emmitt", Team.BLUE, Hex(1, -2)) {
                hand("emmitt-red-1", "emmitt-gold")
            }
            hero("arien", Team.RED, Hex(2, -2)) {
                hand("arien-silver", "arien-gold", "arien-blue-1")
            }
            phase = PositionPhase.PlayCards(0)
        }
        HeroBookCases.noteAvoids(EmmittHeuristics, session, "emmitt-red-1")
    }
}
