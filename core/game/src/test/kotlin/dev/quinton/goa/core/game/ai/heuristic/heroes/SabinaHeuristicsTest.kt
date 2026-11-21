package dev.quinton.goa.core.game.ai.heuristic.heroes

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.position.position
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

class SabinaHeuristicsTest {
    @Test
    fun `finishes 2v2 and 3v3 at strengths 5 and 10`() {
        for (size in listOf(2, 3)) {
            for (strength in listOf(5, 10)) BatchCBook.finish("sabina", size, strength)
        }
    }

    @Test
    fun `does not open silver with no friendly minion nearby`() {
        val session = position(TestData.engine, TestData.FOREST) {
            clearMinions()
            hero("sabina", Team.RED, Hex(1, -2))
            hero("arien", Team.BLUE, Hex(-5, -4))
            phase = PositionPhase.PlayCards()
        }
        HeroBookCases.noteAvoids(SabinaHeuristics, session, "sabina-silver")
    }
}
