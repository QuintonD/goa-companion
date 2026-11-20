package dev.quinton.goa.core.game.ai.heuristic.heroes

import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.position.position
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

class BrynnHeuristicsTest {
    @Test
    fun `brynn finishes 2v2 and 3v3 at strengths 5 and 10`() {
        HeroBookCases.finishes("brynn")
    }

    @Test
    fun `primary red is the card when the hero beside her is crowded`() {
        // Brynn plus three minions are four obstacles around Arien. H1 adds the +2 attack on top of a green.
        val session = position(TestData.engine, TestData.FOREST) {
            turn = 2
            clearMinions()
            hero("brynn", Team.BLUE, Hex(0, -2)) {
                hand("brynn-red-1", "brynn-green-1")
            }
            hero("arien", Team.RED, Hex(1, -2)) {
                hand("arien-blue-1", "arien-gold")
            }
            minion(Team.RED, MinionType.MELEE, Hex(2, -2))
            minion(Team.RED, MinionType.MELEE, Hex(1, -1))
            minion(Team.BLUE, MinionType.MELEE, Hex(0, -3))
            phase = PositionPhase.PlayCards(0)
        }
        HeroBookCases.flips("brynn", BrynnHeuristics, session)
    }
}
