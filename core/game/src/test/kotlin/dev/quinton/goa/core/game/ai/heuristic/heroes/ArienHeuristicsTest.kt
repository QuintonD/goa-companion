package dev.quinton.goa.core.game.ai.heuristic.heroes

import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.position.position
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

class ArienHeuristicsTest {
    @Test
    fun `arien finishes 2v2 and 3v3 at strengths 5 and 10`() {
        HeroBookCases.finishes("arien")
    }

    @Test
    fun `the red is played when a hero with no cards stands behind the adjacent minion`() {
        // H1: Arien (1,-2), a red minion at (2,-2), Brogan straight behind it at (3,-2) with an empty hand.
        // The general book sees only the minion and prefers gold's tempo; the line makes the red a kill.
        val session = position(TestData.engine, TestData.FOREST) {
            turn = 2
            clearMinions()
            hero("arien", Team.BLUE, Hex(1, -2)) {
                hand("arien-gold", "arien-red-1")
            }
            hero("brogan", Team.RED, Hex(3, -2)) {
                hand()
            }
            minion(Team.RED, MinionType.MELEE, Hex(2, -2))
            phase = PositionPhase.PlayCards(0)
        }
        HeroBookCases.flips("arien", ArienHeuristics, session)
    }

    @Test
    fun `spell break is not spent when the radius holds only attacks`() {
        // H3: Brogan (3 away) holds no non-gold skill, so Spell Break has nothing to stop.
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
