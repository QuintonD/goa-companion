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

class TakahideHeuristicsTest {
    @Test
    fun `finishes 2v2 and 3v3 at strengths 5 and 10`() {
        for (size in listOf(2, 3)) {
            for (strength in listOf(5, 10)) HeroBookTest.finish("takahide", size, strength)
        }
    }

    @Test
    fun `never defends with shameful display`() {
        val session = position(TestData.engine, TestData.FOREST) {
            clearMinions()
            hero("takahide", Team.RED, Hex(1, -2)) {
                hand("takahide-gold", "takahide-silver", "takahide-red-1", "takahide-green-1", "takahide-blue-1")
            }
            hero("arien", Team.BLUE, Hex(2, -2))
            phase = PositionPhase.PlayCards()
        }
        val pending = session.pending!!
        val decision = pending.copy(
            kind = DecisionKind.DEFEND,
            prompt = "Discard a card to defend?",
            sourceCardId = "arien-red-1",
            options = listOf(
                Choice.Card("takahide-handicap"),
                Choice.Card("takahide-gold"),
                Choice.Decline("Don't defend"),
            ),
            context = DecisionContext(
                attackerId = "arien",
                attackTotal = 3,
                rangedAttack = false,
                defensePreview = mapOf("takahide-gold" to 8),
                blockPreview = mapOf("takahide-handicap" to true),
            ),
        )
        val withBook = HeroBookTest.chosen(session, decision, TakahideHeuristics)
        val general = HeroBookTest.chosen(session, decision, null)
        assertThat(general).isEqualTo(Choice.Card("takahide-handicap"))
        assertThat(withBook).isEqualTo(Choice.Card("takahide-gold"))
    }
}
