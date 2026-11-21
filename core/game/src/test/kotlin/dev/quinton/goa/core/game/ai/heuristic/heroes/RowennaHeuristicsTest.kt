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

class RowennaHeuristicsTest {
    @Test
    fun `finishes 2v2 and 3v3 at strengths 5 and 10`() {
        for (size in listOf(2, 3)) {
            for (strength in listOf(5, 10)) BatchCBook.finish("rowenna", size, strength)
        }
    }

    @Test
    fun `executes an adjacent hero who has no cards`() {
        val session = position(TestData.engine, TestData.FOREST) {
            clearMinions()
            hero("rowenna", Team.RED, Hex(1, -2)) {
                level = 4
                hand(
                    "rowenna-gold",
                    "rowenna-silver",
                    "rowenna-red-2a",
                    "rowenna-green-2a",
                    "rowenna-blue-2b",
                )
            }
            hero("arien", Team.BLUE, Hex(2, -2)) { hand(exactly = true) }
            minion(Team.BLUE, MinionType.MELEE, Hex(1, -1))
            phase = PositionPhase.PlayCards()
        }
        val decision = session.pending!!
        val withBook = BatchCBook.chosen(session, decision, RowennaHeuristics)
        val general = BatchCBook.chosen(session, decision, null)
        assertThat(withBook).isEqualTo(Choice.Card("rowenna-blue-2b"))
        assertThat(withBook).isNotEqualTo(general)
    }
}
