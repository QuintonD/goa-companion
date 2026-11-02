package dev.quinton.goa.server.game.room

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.position.position
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.server.game.store.AnswerOrigin
import dev.quinton.goa.server.game.store.GameStore
import dev.quinton.goa.server.game.store.InMemoryGameStore
import dev.quinton.goa.server.game.store.StoredAnswer
import kotlinx.coroutines.runBlocking
import org.junit.Test

/** p.7: the team decides. A connected teammate is asked, whoever the engine names for the team. */
class TeamQuestionTest {
    private class Recorder(private val inner: GameStore = InMemoryGameStore()) : GameStore by inner {
        val answers = ArrayList<StoredAnswer>()

        override suspend fun appendAnswer(answer: StoredAnswer) {
            answers += answer
            inner.appendAnswer(answer)
        }
    }

    private val short = RoomRules(
        playCardMillis = 1_000,
        otherMillis = 1_000,
        disconnectedMillis = 100,
        allAwayPauseMillis = 60_000,
        abandonMillis = 120_000,
    )

    /** Three heroes tied at 8 across teams; the coin shows Blue, so Blue decides: arien and brogan. */
    private fun tied(): EngineSession = position(TestRules.engine, FOREST) {
        clearMinions()
        tieBreaker = Team.BLUE
        hero("wasp", Team.RED, Hex(-1, -2)) { played("wasp-red-1") }
        hero("arien", Team.BLUE, Hex(3, -2)) { played("arien-red-1") }
        hero("brogan", Team.BLUE, Hex(4, -5)) { played("brogan-blue-1") }
        hero("xargatha", Team.RED, Hex(1, -5)) { played("xargatha-red-1") }
        phase = PositionPhase.Resolving()
    }

    private fun game(store: GameStore = InMemoryGameStore(), rules: RoomRules = RoomRules()): TestGame {
        val session = tied()
        assertThat(session.pending!!.kind).isEqualTo(DecisionKind.ACT_FIRST)
        assertThat(session.pending!!.teamDecision).isTrue()
        return TestGame(store = store, rules = rules).also { it.seed(session) }
    }

    private fun engineSeat() = tied().pending!!.seat

    private fun teammate(): String = listOf("arien", "brogan").first { it != engineSeat() }

    private fun TestGame.close(seat: String) {
        inbox.getValue(seat).link?.close()
    }

    @Test
    fun `the first teammate who is connected is asked and the frame's seat says so`() {
        val game = game()
        val other = teammate()
        val box = game.connect(other)
        val q = box.question(other)!!
        assertThat(q.seat).isEqualTo(other)
        assertThat(q.kind).isEqualTo(DecisionKind.ACT_FIRST)
        assertThat(q.teamDecision).isTrue()
        assertThat(box.latest(other).clock!!.seat).isEqualTo(other)

        val wasp = game.connect("wasp").latest("wasp").decision!!
        assertThat(wasp.seat).isEqualTo(other)
        assertThat(wasp.options).isEmpty()
        assertThat(wasp.prompt).isEmpty()
    }

    @Test
    fun `with everyone there the engine's seat is asked, and only that person`() {
        val game = game()
        listOf("wasp", "xargatha", "arien", "brogan").forEach { game.connect(it) }
        // The first of the team to connect was asked: the engine's seat came in behind it.
        assertThat(game.asked()).hasSize(1)
        assertThat(game.asked().single()).isIn(listOf("arien", "brogan"))
        val asked = game.asked().single()
        for (seat in game.inbox.keys - asked) {
            val stub = game.inbox.getValue(seat).latest(seat).decision!!
            assertThat(stub.seat).isEqualTo(asked)
            assertThat(stub.id).isEqualTo(0)
            assertThat(stub.options).isEmpty()
            assertThat(stub.teamDecision).isFalse()
        }
    }

    @Test
    fun `the answer goes on record under the engine's seat, as a person's, and replays`() = runBlocking<Unit> {
        val store = Recorder()
        val game = game(store)
        val other = teammate()
        game.connect(other)
        game.answer(other, Choice.Unit(other))

        val answer = store.answers.single()
        assertThat(answer.record.seat).isEqualTo(engineSeat())
        assertThat(answer.record.choice).isEqualTo(Choice.Unit(other))
        assertThat(answer.origin).isEqualTo(AnswerOrigin.HUMAN)
        // The same answer given directly to the engine makes the same game.
        val direct = tied()
        direct.answer(direct.pending!!.id, Choice.Unit(other))
        assertThat(answer.eventsHash).isEqualTo(GameHashes.events(Replay.begin(tied()), direct.events.drop(tied().events.size)))

        val again = TestGame(store = store)
        again.core.restore()
        assertThat(again.core.runningCount).isEqualTo(1)
        assertThat(store.answers).hasSize(1)
    }

    @Test
    fun `a dropped person's question moves to a teammate who is there, and stays when they return`() {
        val game = game(rules = short)
        val first = engineSeat()
        val other = teammate()
        val firstBox = game.connect(first)
        val otherBox = game.connect(other)
        val ticket = firstBox.question(first)!!.id
        assertThat(otherBox.question(other)).isNull()

        game.close(first)
        val moved = otherBox.question(other)!!
        assertThat(moved.seat).isEqualTo(other)
        assertThat(moved.id).isNotEqualTo(ticket)
        // A whole clock for the one now asked: they have only just been given it.
        assertThat(otherBox.latest(other).clock!!.deadlineAt).isEqualTo(game.clock.now + short.otherMillis)

        game.connect(first)
        assertThat(otherBox.question(other)!!.id).isEqualTo(moved.id)
        assertThat(game.inbox.getValue(first).question(first)).isNull()
        // The old ticket is no good now, and the person is told, not left waiting.
        runBlocking { game.core.answer(TestGame.GAME_ID, TestGame.account(first), ticket, Choice.Unit(first)) }
        assertThat(game.core.runningCount).isEqualTo(1)
        game.answer(other, Choice.Unit(other))
        assertThat(otherBox.question(other)?.kind).isNotEqualTo(DecisionKind.ACT_FIRST)
    }

    @Test
    fun `the disconnected clock follows the person asked, and the computer waits for it`() = runBlocking<Unit> {
        val store = Recorder()
        val game = game(store, short)
        val other = teammate()
        val first = engineSeat()
        game.connect(other)
        game.close(other)
        // Nobody on the team is here: the question stays with them, on the short clock.
        game.connect("wasp")
        game.clock.advance(short.disconnectedMillis - 1)
        game.tick()
        assertThat(store.answers).isEmpty()

        // A teammate arrives before it runs out: theirs now, with a whole clock.
        val box = game.connect(first)
        assertThat(box.question(first)).isNotNull()
        game.clock.advance(short.otherMillis - 1)
        game.tick()
        assertThat(store.answers).isEmpty()
        game.clock.advance(1)
        game.tick()
        assertThat(store.answers.first().origin).isEqualTo(AnswerOrigin.CLOCK)
        assertThat(store.answers.first().record.seat).isEqualTo(first)
    }

    @Test
    fun `the engine's seat leaving doesn't make the computer decide while a teammate is there`() = runBlocking<Unit> {
        val store = Recorder()
        val game = game(store)
        val first = engineSeat()
        val other = teammate()
        game.connect(first)
        game.connect(other)
        game.core.leave(TestGame.GAME_ID, TestGame.account(first))
        assertThat(store.answers).isEmpty()
        assertThat(game.inbox.getValue(other).question(other)!!.kind).isEqualTo(DecisionKind.ACT_FIRST)
    }

    @Test
    fun `the computer answers at once when the whole team has left`() = runBlocking<Unit> {
        val store = Recorder()
        val game = game(store)
        val first = engineSeat()
        val other = teammate()
        game.connect("wasp")
        game.connect(first)
        game.connect(other)
        game.core.leave(TestGame.GAME_ID, TestGame.account(first))
        assertThat(store.answers).isEmpty()
        game.core.leave(TestGame.GAME_ID, TestGame.account(other))
        assertThat(store.answers.first().record.seat).isEqualTo(first)
        assertThat(store.answers.first().origin).isEqualTo(AnswerOrigin.CLOCK)
    }

    @Test
    fun `a person's next team question has a new ticket, though the answer went on another seat`() {
        val session = position(TestRules.engine, FOREST) {
            clearMinions()
            tieBreaker = Team.RED
            hero("wasp", Team.RED, Hex(-1, -2)) { played("wasp-red-1") }
            hero("arien", Team.BLUE, Hex(3, -2)) { played("arien-red-1") }
            hero("brogan", Team.BLUE, Hex(4, -5)) { played("brogan-blue-1") }
            hero("xargatha", Team.RED, Hex(1, -5)) { played("xargatha-green-1") }
            phase = PositionPhase.Resolving()
        }
        val game = TestGame()
        game.seed(session)
        game.connect("wasp")
        val q1 = game.inbox.getValue("wasp").question("wasp")!!
        assertThat(q1.kind).isEqualTo(DecisionKind.CHOOSE_ACTION)
        game.answer("wasp", holdOf(q1))
        assertThat(game.inbox.getValue("wasp").question("wasp")).isNull()
        val team = session.setup.seats.first { it.team == Team.BLUE }.heroId
        val other = listOf("arien", "brogan").first { it != team }
        val box = game.connect(other)
        val first = box.question(other)!!
        assertThat(first.teamDecision).isTrue()
        game.answer(other, Choice.Unit(team))
        val after = box.tables(other).last().decision!!
        assertThat(after.id).isNotEqualTo(first.id)
    }

    private fun holdOf(d: Decision): Choice = RandomGames.hold(d)

    @Test
    fun `a team's question carries public choices and the asked person's own hand only`() {
        val session = position(TestRules.engine, FOREST) {
            clearMinions()
            tieBreaker = Team.BLUE
            hero("wasp", Team.RED, Hex(-1, -2)) { played("wasp-red-1") }
            hero("arien", Team.BLUE, Hex(3, -2)) {
                hand("arien-gold")
                played("arien-red-1")
            }
            hero("brogan", Team.BLUE, Hex(4, -5)) {
                hand("brogan-gold")
                played("brogan-blue-1")
            }
            hero("xargatha", Team.RED, Hex(1, -5)) { played("xargatha-red-1") }
            phase = PositionPhase.Resolving()
        }
        val other = session.setup.seats.first { it.team == Team.BLUE && it.heroId != session.pending!!.seat }.heroId
        val hidden = if (other == "arien") "brogan-gold" else "arien-gold"
        val game = TestGame()
        game.seed(session)
        val box = game.connect(other)
        val text = box.bytes().joinToString("\n")
        assertThat(box.question(other)!!.options.toSet())
            .isEqualTo(setOf<Choice>(Choice.Unit("arien"), Choice.Unit("brogan")))
        assertThat(text).doesNotContain(hidden)
        assertThat(box.question(other)!!.sourceCardId).isNull()
    }

    @Test
    fun `removing a minion in the battle goes to a teammate who is there`() {
        val session = position(TestRules.engine, FOREST) {
            clearMinions()
            hero("wasp", Team.RED, Hex(-5, -4))
            hero("xargatha", Team.RED, Hex(-5, -3))
            hero("arien", Team.BLUE, Hex(-5, -2))
            hero("brogan", Team.BLUE, Hex(-5, -1))
            minion(Team.RED, MinionType.MELEE, Hex(0, -6))
            minion(Team.RED, MinionType.MELEE, Hex(1, -6))
            minion(Team.RED, MinionType.MELEE, Hex(-1, -6))
            minion(Team.BLUE, MinionType.MELEE, Hex(5, -8))
            minion(Team.BLUE, MinionType.MELEE, Hex(1, -4))
            minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -4))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -3))
            minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
            phase = PositionPhase.EndOfRound
        }
        val pending = session.pending!!
        assertThat(pending.kind).isEqualTo(DecisionKind.REMOVE_MINION)
        assertThat(pending.teamDecision).isTrue()
        val other = session.setup.seats.first { it.team == pending.team && it.heroId != pending.seat }.heroId
        val game = TestGame()
        game.seed(session)
        val box = game.connect(other)
        assertThat(box.question(other)!!.kind).isEqualTo(DecisionKind.REMOVE_MINION)
    }
}
