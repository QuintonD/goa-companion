package dev.quinton.goa.core.game.ai.heuristic.heroes

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.DecisionContext
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.position.position
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

class TaliHeuristicsTest {
    @Test
    fun `finishes 2v2 and 3v3 at strengths 5 and 10`() {
        for (size in listOf(2, 3)) {
            for (strength in listOf(5, 10)) BatchCBook.finish("tali", size, strength)
        }
    }

    @Test
    fun `places ice on the enemy rather than an empty hex`() {
        val beside = Hex(2, -3)
        val empty = Hex(4, -7)
        val session = position(TestData.engine, TestData.FOREST) {
            clearMinions()
            hero("tali", Team.RED, Hex(1, -2))
            hero("arien", Team.BLUE, Hex(2, -2))
            phase = PositionPhase.PlayCards()
        }
        val pending = session.pending!!
        val decision = pending.copy(
            kind = DecisionKind.CHOOSE_SPACE,
            prompt = "Place an Ice token",
            sourceCardId = "tali-green-1",
            options = listOf(Choice.Space(empty), Choice.Space(beside)),
            context = DecisionContext(subjectId = "ice-spot"),
        )
        val withBook = BatchCBook.chosen(session, decision, TaliHeuristics)
        val general = BatchCBook.chosen(session, decision, null)
        assertThat(general).isEqualTo(Choice.Space(empty))
        assertThat(withBook).isEqualTo(Choice.Space(beside))
    }
}
