package dev.quinton.goa.core.game.ai

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.game.pos
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

/**
 * The coach looks ahead (`prepare` while the person thinks, then `nudge` with the analysis when they
 * answer) and says exactly what it says when it looks only at the answer. The wall-clock cap is lifted
 * so a busy test machine can't cut one search shorter than the other.
 */
class CoachAheadTest {
    private val unhurried = 1_000_000L

    /** The old way and the new way, each on its own coach (nothing shared between them). */
    private var extended = 0

    private fun both(s: EngineSession, d: Decision, choice: Choice): Pair<Any?, Any?> {
        val before = s.save()
        val old = AiCoach(TestData.engine, 0x0C0AC4L, unhurried).nudge(s, d, choice)
        val ahead = AiCoach(TestData.engine, 0x0C0AC4L, unhurried)
        val prepared = ahead.prepare(s.fork(), d)
        // The pruning left the answer out: the analysis is widened at answer time.
        if ((prepared as? AiCoach.Prepared)?.analysis?.options?.none { it.choice == choice } == true) extended++
        val new = if (prepared == null) ahead.nudge(s, d, choice) else ahead.nudge(prepared, s, d, choice)
        // Looking never changes the game.
        assertThat(s.save()).isEqualTo(before)
        assertThat(s.pending).isEqualTo(d)
        return old to new
    }

    @Test
    fun `prepare then nudge says what nudge says, on the coach's blunder positions`() {
        val attacked = pos(engine = TestData.engine) {
            clearMinions()
            hero("wasp", Team.RED, Hex(0, -2)) { hand("wasp-blue-1", "wasp-gold", "wasp-red-1", exactly = true) }
            hero("brogan", Team.BLUE, Hex(1, -2)) { played("brogan-gold") }
            minion(Team.RED, MinionType.MELEE, Hex(-4, -1))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -5))
            turn = 2
            phase = PositionPhase.Resolving(next = "brogan")
        }.also { it.action("brogan", ActionType.PRIMARY) }
        val defend = attacked.expect(DecisionKind.DEFEND, "wasp")
        val moving = pos(engine = TestData.engine) {
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
        val move = moving.expect(DecisionKind.CHOOSE_SPACE, "wasp")
        var spoke = 0
        for ((s, d) in listOf(attacked.s to defend, moving.s to move)) {
            for (c in d.options) {
                val (old, new) = both(s, d, c)
                assertThat(new).isEqualTo(old)
                if (old != null) spoke++
            }
        }
        // The blunders of CoachTest are among them: the comparison covers notes, not only silence.
        assertThat(spoke).isAtLeast(3)
    }

    @Test
    fun `over a whole game the looked-ahead nudge is the same for every answer, and quick`() {
        val (red, blue) = AiHarness.mixedLineups(1, seed = 77L)[0]
        val s = TestData.engine.newGame(AiHarness.setup(red, blue, AiLevel.EASY, AiLevel.HARD, Team.RED))
            as EngineSession
        val players = mapOf(
            Team.RED to AiPlayers.deterministic(AiLevel.EASY, 70L),
            Team.BLUE to AiPlayers.deterministic(AiLevel.HARD, 80L),
        )
        // The app's coach, wall-clock cap included, for the times.
        val timed = AiCoach(TestData.engine)
        val prepareMs = ArrayList<Double>()
        val answerMs = ArrayList<Double>()
        var compared = 0
        var spoke = 0
        var widened = 0
        var bigLists = 0
        var n = 0
        while (n < 6000) {
            val d = s.pending ?: break
            val c = players.getValue(d.team).choose(s, d)
            if (d.team == Team.RED && d.options.size > 1) {
                // Every option of the first long lists (move destinations: the pruning leaves some out).
                val choices = if (d.options.size > 8 && bigLists++ < 4) d.options else listOf(c)
                for (o in choices) {
                    val (old, new) = both(s, d, o)
                    assertThat(new).isEqualTo(old)
                    compared++
                    if (old != null) spoke++
                    if (o != c) widened++
                }
                val t0 = System.nanoTime()
                val prepared = timed.prepare(s.fork(), d)
                val t1 = System.nanoTime()
                if (prepared != null) timed.nudge(prepared, s, d, c)
                val t2 = System.nanoTime()
                prepareMs += (t1 - t0) / 1e6
                answerMs += (t2 - t1) / 1e6
            }
            s.answer(d.id, c)
            n++
        }
        println(
            "COACH AHEAD: $compared answers compared ($widened beyond the chosen one, $extended widened), " +
                "$spoke nudges; " +
                "prepare mean ${"%.0f".format(prepareMs.average())} ms, max ${"%.0f".format(prepareMs.max())} ms; " +
                "at the answer mean ${"%.1f".format(answerMs.average())} ms, " +
                "p95 ${"%.1f".format(answerMs.sorted()[answerMs.size * 95 / 100])} ms, " +
                "max ${"%.1f".format(answerMs.max())} ms",
        )
        assertThat(compared).isGreaterThan(50)
        assertThat(widened).isGreaterThan(0)
        assertThat(extended).isGreaterThan(0)
    }
}
