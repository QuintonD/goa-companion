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
        // H1: Brynn plus two red minions are three obstacles around Arien, so the red is Attack 6.
        val session = position(TestData.engine, TestData.FOREST) {
            turn = 3
            clearMinions()
            hero("brynn", Team.BLUE, Hex(0, -2)) {
                hand("brynn-red-1", "brynn-gold")
            }
            hero("arien", Team.RED, Hex(1, -2)) {
                hand("arien-green-1", "arien-gold", "arien-silver")
            }
            minion(Team.RED, MinionType.MELEE, Hex(2, -2))
            minion(Team.RED, MinionType.MELEE, Hex(1, -1))
            phase = PositionPhase.PlayCards(0)
        }
        HeroBookCases.noteFavours(BrynnHeuristics, session, "brynn-red-1", "brynn-gold")
    }
}
