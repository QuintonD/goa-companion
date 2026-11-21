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

class SwiftHeuristicsTest {
    @Test
    fun `finishes 2v2 and 3v3 at strengths 5 and 10`() {
        for (size in listOf(2, 3)) {
            for (strength in listOf(5, 10)) BatchCBook.finish("swift", size, strength)
        }
    }

    @Test
    fun `reloads to defeat the adjacent minion`() {
        val session = position(TestData.engine, TestData.FOREST) {
            clearMinions()
            hero("swift", Team.RED, Hex(1, -2))
            hero("arien", Team.BLUE, Hex(-5, -4))
            minion(Team.BLUE, MinionType.MELEE, Hex(2, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -5))
            phase = PositionPhase.PlayCards()
        }
        val decision = session.pending!!
        val withBook = BatchCBook.chosen(session, decision, SwiftHeuristics)
        val general = BatchCBook.chosen(session, decision, null)
        assertThat(withBook).isEqualTo(Choice.Card("swift-gold"))
        assertThat(withBook).isNotEqualTo(general)
    }
}
