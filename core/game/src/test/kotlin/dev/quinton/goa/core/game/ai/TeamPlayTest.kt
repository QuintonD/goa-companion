package dev.quinton.goa.core.game.ai

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.game.position.position
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

/** ADR-061: the parts of team play — the kills of [TeamKills], [RegretMatching], and the joint search's answers. */
class TeamPlayTest {
    /** Wasp holds [waspHand]; Brogan and Arien stand next to her with their Gold attacks (Attack 4, adjacent). */
    private fun surrounded(vararg waspHand: String, arienAt: Hex = Hex(0, -3)): EngineSession =
        position(TestData.engine, TestData.FOREST) {
            hero("wasp", Team.RED, Hex(0, -2)) { hand(*waspHand, exactly = true) }
            hero("xargatha", Team.RED, Hex(-4, -1))
            hero("brogan", Team.BLUE, Hex(1, -2)) { hand("brogan-gold", "brogan-silver", exactly = true) }
            hero("arien", Team.BLUE, arienAt) { hand("arien-gold", "arien-silver", exactly = true) }
        }

    @Test
    fun `two heroes in reach of a hero holding two cards make a two-hero kill`() {
        val s = surrounded("wasp-gold", "wasp-blue-1")
        val chances = TeamKills.chances(Evaluator(s.engineData), s.state, Team.BLUE)
        assertThat(chances.map { it.target }).containsExactly("wasp")
        assertThat(chances.single().attackers).containsExactly("brogan", "brogan-gold", "arien", "arien-gold")
        assertThat(chances.single().needsTwo).isTrue()
    }

    @Test
    fun `no kill when the target holds a card for every attacker and one to play`() {
        val s = surrounded("wasp-gold", "wasp-blue-1", "wasp-silver")
        assertThat(TeamKills.chances(Evaluator(s.engineData), s.state, Team.BLUE)).isEmpty()
        // One attacker out of reach: two cards in hand are enough.
        val far = surrounded("wasp-gold", "wasp-blue-1", arienAt = Hex(-3, 2))
        assertThat(TeamKills.chances(Evaluator(far.engineData), far.state, Team.BLUE)).isEmpty()
    }

    @Test
    fun `regret matching mixes evenly at matching pennies`() {
        // Two samples, one per enemy card: heads wins against heads, tails against tails.
        val values = listOf(doubleArrayOf(1.0, -1.0), doubleArrayOf(-1.0, 1.0))
        val x = RegretMatching.solve(values, listOf("h", "t"), free = 1.0)
        assertThat(x[0]).isWithin(0.05).of(0.5)
        assertThat(x[1]).isWithin(0.05).of(0.5)
    }

    @Test
    fun `regret matching plays a dominant card alone and best-replies to the model when the enemy is not free`() {
        val dominant = listOf(doubleArrayOf(2.0, 3.0), doubleArrayOf(1.0, 0.0))
        assertThat(RegretMatching.solve(dominant, listOf("a", "b"), free = 1.0).toList()).containsExactly(1.0, 0.0)
        // Against the model alone (three samples drew heads, one tails) heads is the best reply.
        val pennies = listOf(doubleArrayOf(1.0, 1.0, 1.0, -1.0), doubleArrayOf(-1.0, -1.0, -1.0, 1.0))
        val x = RegretMatching.solve(pennies, listOf("h", "h", "h", "t"), free = 0.0)
        assertThat(x[0]).isGreaterThan(0.95)
    }

    @Test
    fun `team card play answers legally and the same for the same seed`() {
        val profile = AiPlayers.profile(10).copy(joint = 2, killExt = 1, rm = 0.5, workBudget = 4000)
        val s = TestData.engine.newGame(
            AiHarness.setup(
                listOf("wasp", "arien", "brogan"),
                listOf("xargatha", "tigerclaw", "sabina"),
                AiLevel.EXPERT,
                AiLevel.EXPERT,
                Team.RED,
            ),
        ) as EngineSession
        val d = s.pending!!
        assertThat(d.kind).isEqualTo(DecisionKind.PLAY_CARD)
        val a = SearchAi(AiLevel.EXPERT, profile, 7L, timeCap = false).choose(s, d)
        val b = SearchAi(AiLevel.EXPERT, profile, 7L, timeCap = false).choose(s, d)
        assertThat(a).isInstanceOf(Choice.Card::class.java)
        assertThat(a).isIn(d.options)
        assertThat(b).isEqualTo(a)
    }
}
