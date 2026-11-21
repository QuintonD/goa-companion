package dev.quinton.goa.core.game.ai.heuristic.heroes

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.position.position
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

class SilverarrowHeuristicsTest {
    @Test
    fun `finishes 2v2 and 3v3 at strengths 5 and 10`() {
        for (size in listOf(2, 3)) {
            for (strength in listOf(5, 10)) BatchCBook.finish("silverarrow", size, strength)
        }
    }

    @Test
    fun `does not spend gold on a unit inside the exact ring`() {
        val session = position(TestData.engine, TestData.FOREST) {
            clearMinions()
            hero("silverarrow", Team.RED, Hex(1, -2))
            hero("arien", Team.BLUE, Hex(-5, -4))
            minion(Team.BLUE, MinionType.MELEE, Hex(2, -2))
            phase = PositionPhase.PlayCards()
        }
        HeroBookCases.noteAvoids(SilverarrowHeuristics, session, "silverarrow-gold")
    }
}
