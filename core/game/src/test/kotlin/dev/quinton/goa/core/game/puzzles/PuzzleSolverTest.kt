package dev.quinton.goa.core.game.puzzles

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.AnswerResult
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Deadline
import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.core.game.PuzzleGoal
import dev.quinton.goa.core.game.PuzzleInfo
import dev.quinton.goa.core.game.PuzzleStatus
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.WinReason
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.puzzles.Moves.action
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

/** The solver, goals, deadlines and runtime on small test positions (the book's puzzles: PuzzleBookTest). */
class PuzzleSolverTest {
    /** Turn 2: Arien (Blue) acts with a played card; Wasp is [waspAt] with Shock played. */
    private class TestPuzzle(
        goal: PuzzleGoal,
        private val waspAt: Hex,
        private val arienHand: List<String>,
        /** Start at the play step instead: Wasp (seat 0) and Arien both choose between two cards. */
        private val playStep: Boolean = false,
    ) : Puzzle() {
        override val concept = "C00"
        override val info = PuzzleInfo(
            id = "p00",
            number = 0,
            title = "Test",
            difficulty = 1,
            theme = "Test",
            heroes = listOf("arien", "wasp", "xargatha", "brogan"),
            goal = goal,
            nudges = listOf("a", "b", "c"),
            explanation = "Test",
        )
        override val playerSeats = setOf("arien")
        override val solution = listOf(action(ActionType.HOLD))

        override fun PositionBuilder.setUp() {
            round = 2
            turn = 2
            clearMinions()
            hero("wasp", Team.RED, waspAt) {
                if (playStep) {
                    hand("wasp-red-1", "wasp-blue-1")
                } else {
                    played("wasp-red-1")
                    hand("wasp-blue-1")
                }
                resolved(1, "wasp-gold")
            }
            hero("xargatha", Team.RED, Hex(-1, -5)) {
                resolved(1, "xargatha-gold")
                resolved(2, "xargatha-red-1")
                hand("xargatha-blue-1")
            }
            hero("arien", Team.BLUE, Hex(1, -2)) {
                if (playStep) {
                    hand("arien-green-1", *arienHand.toTypedArray())
                } else {
                    played("arien-green-1")
                    hand(*arienHand.toTypedArray())
                }
                resolved(1, "arien-gold")
            }
            hero("brogan", Team.BLUE, Hex(4, -5)) {
                resolved(1, "brogan-gold")
                resolved(2, "brogan-red-1")
                hand("brogan-blue-1")
            }
            phase = if (playStep) PositionPhase.PlayCards() else PositionPhase.Resolving()
        }
    }

    @Test
    fun `an answer key refuses a player card choice made after an opponent's facedown choice`() {
        val p = prepared(
            TestPuzzle(
                PuzzleGoal.Survive(Deadline.END_OF_TURN, "Survive"),
                Hex(3, -5),
                listOf("arien-blue-1"),
                playStep = true,
            ),
        )
        assertThat(p.solver.sideOf(p.rootNode.decision!!)).isEqualTo(Side.OPPONENT)
        val e = runCatching { AnswerKey.generate(p) }.exceptionOrNull()
        assertThat(e).isInstanceOf(IllegalStateException::class.java)
        assertThat(e!!.message).contains("facedown")
    }

    private fun prepared(p: Puzzle) = PreparedPuzzle(p, TestData.engine, useKey = false)

    @Test
    fun `the game halts at the deadline - no event of the next turn is seen`() {
        val p = prepared(
            TestPuzzle(PuzzleGoal.Survive(Deadline.END_OF_TURN, "Survive"), Hex(3, -5), listOf("arien-blue-1")),
        )
        assertThat(p.solver.solve(p.rootNode).value).isEqualTo(Value.WIN)
        val s = p.newSession()
        // Wasp (8) acts first and cannot reach; Arien's turn is the player's.
        assertThat(s.game.pending!!.seat).isEqualTo("arien")
        val d = s.game.pending!!
        assertThat(s.answer(d.id, Choice.Action(ActionType.HOLD))).isInstanceOf(AnswerResult.Accepted::class.java)
        assertThat(s.status).isEqualTo(PuzzleStatus.SOLVED)
        assertThat(s.game.pending).isNull()
        assertThat(s.game.events.filterIsInstance<GameEvent.TurnStarted>().map { it.turn }).doesNotContain(3)
    }

    @Test
    fun `a goal that cannot be forced is missed from the start and has no best choice`() {
        // Wasp stands next to Arien, who has no card left: Shock defeats him before he acts.
        val p = prepared(TestPuzzle(PuzzleGoal.Survive(Deadline.END_OF_TURN, "Survive"), Hex(2, -2), emptyList()))
        assertThat(p.solver.solve(p.rootNode).value).isEqualTo(Value.LOSS)
        val s = p.newSession()
        assertThat(s.status).isEqualTo(PuzzleStatus.MISSED)
        assertThat(s.bestChoice()).isNull()
        assertThat(s.undo()).isFalse()
    }

    @Test
    fun `opponent decisions are answered at once and the player may not answer them`() {
        val p =
            prepared(
                TestPuzzle(
                    PuzzleGoal.DefeatHero("wasp", Deadline.END_OF_TURN, "x"),
                    Hex(3, -5),
                    listOf("arien-blue-1"),
                ),
            )
        val s = p.newSession()
        val d = s.game.pending!!
        assertThat(p.solver.sideOf(d)).isEqualTo(Side.PLAYER)
        // A stale id is rejected by the game itself.
        assertThat(s.answer(d.id + 5, d.options.first())).isInstanceOf(AnswerResult.Rejected::class.java)
    }

    @Test
    fun `goal tracker - defeat, survive, coins and pushes`() {
        val session = prepared(
            TestPuzzle(PuzzleGoal.Survive(Deadline.END_OF_TURN, "x"), Hex(3, -5), listOf("arien-blue-1")),
        ).root
        fun tracker(goal: PuzzleGoal) = GoalTracker(goal, Team.BLUE, setOf("arien"))
        val defeatedWasp = GameEvent.Defeated("wasp", "arien", "Wasp is defeated.")
        val defeatedArien = GameEvent.Defeated("arien", "wasp", "Arien is defeated.")
        val coins = GameEvent.CoinsGained("arien", 2, "Arien gains 2 coins.")
        val friendCoins = GameEvent.CoinsGained("brogan", 5, "Brogan gains 5 coins.")
        val blueWinsThrone = GameEvent.GameOver(Team.BLUE, WinReason.THRONE, "Blue wins.")
        val redPush = GameEvent.LanePushed("lane-1", Team.RED, "lane-1-3", "Red pushes.")

        val defeat = tracker(PuzzleGoal.DefeatHero("wasp", Deadline.END_OF_TURN, "x"))
        assertThat(defeat.after(Progress(), listOf(defeatedWasp), session).state).isEqualTo(GoalState.MET)
        assertThat(defeat.after(Progress(), listOf(defeatedArien), session).state).isEqualTo(GoalState.OPEN)

        val survive = tracker(PuzzleGoal.Survive(Deadline.END_OF_TURN, "x"))
        assertThat(survive.after(Progress(), listOf(defeatedWasp), session).state).isEqualTo(GoalState.OPEN)
        assertThat(survive.after(Progress(), listOf(defeatedArien), session).state).isEqualTo(GoalState.FAILED)
        assertThat(survive.after(Progress(), listOf(redPush), session).state).isEqualTo(GoalState.FAILED)

        val gain = tracker(PuzzleGoal.GainCoins(3, Deadline.END_OF_TURN, "x"))
        val once = gain.after(Progress(), listOf(coins, friendCoins), session)
        assertThat(once).isEqualTo(Progress(GoalState.OPEN, 2))
        assertThat(gain.after(once, listOf(coins), session)).isEqualTo(Progress(GoalState.MET, 4))

        val push = tracker(PuzzleGoal.PushLane(Deadline.END_OF_TURN, "x"))
        assertThat(push.after(Progress(), listOf(blueWinsThrone), session).state).isEqualTo(GoalState.MET)
        assertThat(push.after(Progress(), listOf(redPush), session).state).isEqualTo(GoalState.OPEN)

        val win = tracker(PuzzleGoal.WinGame("x"))
        assertThat(win.after(Progress(), listOf(blueWinsThrone), session).state).isEqualTo(GoalState.MET)
    }

    @Test
    fun `state hash - ignores the decision counter and stamps, not the position`() {
        val p = prepared(TestPuzzle(PuzzleGoal.Survive(Deadline.END_OF_TURN, "x"), Hex(3, -5), listOf("arien-blue-1")))
        val a = p.root.checkpointState.copy()
        val b = a.copy()
        b.decisions += 7
        b.stamp += 100
        for (h in b.heroes) for (c in h.cards) c.stamp += 50
        assertThat(StateHash.of(b)).isEqualTo(StateHash.of(a))
        val c = a.copy()
        c.hero("arien")!!.at = Hex(0, -2)
        assertThat(StateHash.of(c)).isNotEqualTo(StateHash.of(a))
    }
}
