package dev.quinton.goa.core.game.ai.heuristic.heroes

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.position.position
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

class SwiftHeuristicsTest {
    @Test
    fun `finishes 2v2 and 3v3 at strengths 5 and 10`() {
        for (size in listOf(2, 3)) {
            for (strength in listOf(5, 10)) HeroBookTest.finish("swift", size, strength)
        }
    }

    @Test
    fun `reloads the red instead of covering it`() {
        val session = position(TestData.engine, TestData.FOREST) {
            clearMinions()
            turn = 2
            hero("swift", Team.RED, Hex(1, -2)) {
                hand("swift-gold", "swift-silver", "swift-green-1", "swift-blue-1")
                resolved(1, "swift-red-1")
            }
            hero("arien", Team.BLUE, Hex(4, -5))
            phase = PositionPhase.PlayCards()
        }
        val decision = session.pending!!
        val withBook = HeroBookTest.chosen(session, decision, SwiftHeuristics)
        val general = HeroBookTest.chosen(session, decision, null)
        assertThat(withBook).isEqualTo(Choice.Card("swift-gold"))
        assertThat(withBook).isNotEqualTo(general)
    }
}
