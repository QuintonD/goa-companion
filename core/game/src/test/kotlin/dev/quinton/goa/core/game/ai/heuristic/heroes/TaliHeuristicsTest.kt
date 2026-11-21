package dev.quinton.goa.core.game.ai.heuristic.heroes

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.DecisionContext
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.ai.heuristic.HContext
import dev.quinton.goa.core.game.ai.heuristic.HeroPlaybooks
import dev.quinton.goa.core.game.ai.heuristic.HeuristicProfile
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

    @Test
    fun `ice blast splashes only after a target with a card to defend`() {
        // Review H2-10: the splash follows a defense discard. A target with an empty hand is defeated instead.
        fun splash(targetHand: Array<String>): Double {
            val session = position(TestData.engine, TestData.FOREST) {
                turn = 2
                clearMinions()
                hero("tali", Team.RED, Hex(1, -2)) { hand("tali-gold", "tali-green-1") }
                hero("arien", Team.BLUE, Hex(2, -2)) { hand(*targetHand) }
                hero("brogan", Team.BLUE, Hex(3, -4)) { hand("brogan-gold") }
                phase = PositionPhase.PlayCards(0)
            }
            val d = session.pending!!
            assertThat(d.seat).isEqualTo("tali")
            val ctx = HContext(session, d, d.team, HeuristicProfile.of(8), playbooks = HeroPlaybooks::forHero)
            val gold = d.options.filterIsInstance<Choice.Card>().first { it.cardId == "tali-gold" }
            return TaliHeuristics.adjust(ctx, d, gold)
        }
        assertThat(splash(arrayOf("arien-gold"))).isEqualTo(3.0)
        assertThat(splash(emptyArray())).isEqualTo(0.0)
    }
}
