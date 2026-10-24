package dev.quinton.goa.core.game.ai

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.pos
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

/** The coach speaks up on clear blunders with a concrete reason, and stays quiet on fine choices. */
class CoachTest {
    private val coach = AiCoach(TestData.engine)

    /** Brogan's Onslaught (Attack 4) hits Wasp, who holds Blue (Defense 5) and Gold (Defense 2). */
    private fun attacked() = pos(engine = TestData.engine) {
        clearMinions()
        hero("wasp", Team.RED, Hex(0, -2)) { hand("wasp-blue-1", "wasp-gold", "wasp-red-1", exactly = true) }
        hero("brogan", Team.BLUE, Hex(1, -2)) { played("brogan-gold") }
        minion(Team.RED, MinionType.MELEE, Hex(-4, -1))
        minion(Team.BLUE, MinionType.MELEE, Hex(4, -5))
        turn = 2
        phase = PositionPhase.Resolving(next = "brogan")
    }.also { it.action("brogan", ActionType.PRIMARY) }

    @Test
    fun `not defending when a card would stop the attack is flagged`() {
        val d = attacked()
        val q = d.expect(DecisionKind.DEFEND, "wasp")
        val note = coach.nudge(d.s, q, Choice.Decline("Don't defend"))
        println("COACH defend-decline: $note")
        assertThat(note).isNotNull()
        assertThat(note!!.text).contains("Lift Up")
        assertThat(note.detail).contains("p.14")
    }

    @Test
    fun `defending with a card that is too weak is flagged`() {
        val d = attacked()
        val q = d.expect(DecisionKind.DEFEND, "wasp")
        val note = coach.nudge(d.s, q, Choice.Card("wasp-gold"))
        println("COACH defend-weak: $note")
        assertThat(note).isNotNull()
        assertThat(note!!.text).contains("isn't enough")
    }

    @Test
    fun `defending with the card that works is fine`() {
        val d = attacked()
        val q = d.expect(DecisionKind.DEFEND, "wasp")
        assertThat(coach.nudge(d.s, q, Choice.Card("wasp-blue-1"))).isNull()
    }

    /** Wasp moves 1 with her Gold before Brogan's revealed Onslaught (Attack 4); she holds no defense. */
    private fun moving() = pos(engine = TestData.engine) {
        clearMinions()
        hero("wasp", Team.RED, Hex(0, -2)) {
            played("wasp-gold")
            hand("wasp-green-1", exactly = true)
        }
        hero("brogan", Team.BLUE, Hex(2, -2)) { played("brogan-gold") }
        minion(Team.RED, MinionType.MELEE, Hex(-4, -1))
        minion(Team.BLUE, MinionType.MELEE, Hex(4, -5))
        turn = 3
        phase = PositionPhase.Resolving(next = "wasp")
    }.also { it.action("wasp", ActionType.MOVEMENT) }

    @Test
    fun `moving next to an enemy whose revealed attack defeats you is flagged`() {
        val d = moving()
        val q = d.expect(DecisionKind.CHOOSE_SPACE, "wasp")
        assertThat(q.options).contains(Choice.Space(Hex(1, -2)))
        val note = coach.nudge(d.s, q, Choice.Space(Hex(1, -2)))
        println("COACH move-into-danger: $note")
        assertThat(note).isNotNull()
        assertThat(note!!.text).contains("Brogan")
    }

    @Test
    fun `moving away is fine`() {
        val d = moving()
        val q = d.expect(DecisionKind.CHOOSE_SPACE, "wasp")
        assertThat(coach.nudge(d.s, q, Choice.Space(Hex(-1, -2)))).isNull()
    }

    @Test
    fun `turning points of a finished game are 3 to 6 explained moments, oldest first`() {
        val (red, blue) = AiHarness.LINEUPS[0]
        val r = AiHarness.play(
            AiHarness.setup(red, blue, AiLevel.NORMAL, AiLevel.HARD, Team.RED),
            mapOf(Team.RED to AiPlayers.deterministic(AiLevel.NORMAL, 3), Team.BLUE to AiPlayers.deterministic(AiLevel.HARD, 4)),
        )
        val notes = coach.turningPoints(r.session.save(), "wasp")
        notes.forEach { println("TURNING POINT: ${it.text} | ${it.detail}") }
        assertThat(notes.size).isAtLeast(3)
        assertThat(notes.size).isAtMost(6)
        val order = notes.map { it.round!! * 10 + it.turn!! }
        assertThat(order).isInOrder()
        assertThat(notes.all { it.detail.contains("p.") }).isTrue()
    }
}
