package dev.quinton.goa.core.game.ai.heuristic.heroes

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.engine.PieceState
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.position.position
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

class RazzleHeuristicsTest {
    @Test
    fun `finishes 2v2 and 3v3 at strengths 5 and 10`() {
        for (size in listOf(2, 3)) {
            for (strength in listOf(5, 10)) BatchCBook.finish("razzle", size, strength)
        }
    }

    @Test
    fun `acts with the figure that stands next to the target`() {
        val session = position(TestData.engine, TestData.FOREST) {
            clearMinions()
            hero("razzle", Team.RED, Hex(1, -2))
            hero("xargatha", Team.BLUE, Hex(3, -4))
            piece(
                PieceState(
                    "razzle-b",
                    "razzle_double",
                    "Razzle",
                    Hex(4, -5),
                    owner = "razzle",
                    team = Team.RED,
                    token = false,
                    unit = true,
                    removeAtEndOfRound = false,
                    heroBody = "razzle",
                ),
            )
            phase = PositionPhase.PlayCards()
        }
        val pending = session.pending!!
        val decision = pending.copy(
            kind = DecisionKind.CHOOSE_UNIT,
            prompt = "Which one of you performs the action?",
            sourceCardId = "razzle-gold",
            options = listOf(Choice.Unit("razzle"), Choice.Unit("razzle-b")),
        )
        val withBook = BatchCBook.chosen(session, decision, RazzleHeuristics)
        val general = BatchCBook.chosen(session, decision, null)
        assertThat(withBook).isEqualTo(Choice.Unit("razzle-b"))
        assertThat(withBook).isNotEqualTo(general)
    }
}
