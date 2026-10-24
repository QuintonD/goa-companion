package dev.quinton.goa.core.game.puzzles

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import dev.quinton.goa.core.game.AnswerResult
import dev.quinton.goa.core.game.PuzzleStatus
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.puzzles.book.Puzzles
import java.io.File
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * The checks every bundled puzzle must pass (ENCODING.md "Tests"), run once per puzzle in
 * [Puzzles.all] whose heroes are playable. A puzzle file needs no test code of its own.
 */
@RunWith(Parameterized::class)
class PuzzleBookTest(private val puzzle: Puzzle) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun puzzles(): List<Array<Any>> = Puzzles.all
            .filter { p -> p.info.heroes.all { it in TestData.engine.playableHeroes } }
            .map { arrayOf(it) }

        /**
         * Desktop budget for one player answer. Typical answers take a few ms (key) or < 50 ms (budgeted
         * search after a miss); the headroom keeps the check from failing while other builds load the
         * machine. The phone target (~3-4x slower) is < 1 s.
         */
        const val ANSWER_MS = 500L
    }

    private val book = EnginePuzzleBook(TestData.engine)
    private val id = puzzle.info.id

    private fun play(session: EnginePuzzleSession, moves: List<Move>, label: String, check: (Int) -> Unit = {}) {
        val t = System.nanoTime()
        moves.forEachIndexed { i, m ->
            val d = session.game.pending ?: error("$id $label: no decision for move ${i + 1} ($m)")
            val c = m.resolve(d, session.position.session)
                ?: error("$id $label: move ${i + 1} ($m) is not offered at ${d.kind} ${d.prompt}: ${d.options}")
            val t0 = System.nanoTime()
            val r = session.answer(d.id, c)
            val ms = (System.nanoTime() - t0) / 1_000_000
            assertWithMessage("$id $label move ${i + 1} $m: $r").that(r).isInstanceOf(AnswerResult.Accepted::class.java)
            assertWithMessage("$id $label move ${i + 1} took $ms ms").that(ms).isLessThan(ANSWER_MS)
            check(i)
        }
        println("$id $label: ${moves.size} answers in ${(System.nanoTime() - t) / 1_000_000} ms")
    }

    private fun session() = book.start(id) as EnginePuzzleSession

    @Test
    fun `the info is complete`() {
        val info = puzzle.info
        assertThat(info.id).matches("p\\d\\d")
        assertThat(info.id.drop(1)).isEqualTo(puzzle.concept.drop(1))
        assertThat(info.difficulty).isIn(1..3)
        assertThat(info.nudges.size).isAtLeast(3)
        assertThat(info.explanation).isNotEmpty()
        assertThat(info.intro).isNotEmpty()
        assertThat(info.rules).isNotEmpty()
        assertThat(info.heroes.first()).isIn(puzzle.playerSeats)
        assertThat(puzzle.solution).isNotEmpty()
        val seated = puzzle.build(TestData.engine).setup.seats.map { it.heroId }
        assertThat(seated).containsExactlyElementsIn(info.heroes)
    }

    @Test
    fun `the answer key is up to date`() {
        val prep = EnginePuzzleBook(TestData.engine, useKeys = false).prepared(id)
        val t0 = System.nanoTime()
        val fresh = AnswerKey.generate(prep)
        val ms = (System.nanoTime() - t0) / 1_000_000
        println("$id answer key: ${fresh.entries.size} decisions, ${prep.solver.nodesExpanded} nodes, $ms ms")
        val stored = AnswerKey.load(id)
        if (stored != fresh) {
            val f = File("src/main/resources/${AnswerKey.RESOURCE_DIR}/$id.key")
            f.parentFile.mkdirs()
            f.writeText(fresh.format())
            val was = if (stored == null) "missing" else "stale"
            fail("$id: the answer key was $was; rewrote ${f.path}: run the tests again")
        }
    }

    @Test
    fun `the solver proves the goal is forced within the budget`() {
        val prep = EnginePuzzleBook(TestData.engine, useKeys = false).prepared(id)
        val t0 = System.nanoTime()
        val s = prep.solver.solve(prep.rootNode)
        val ms = (System.nanoTime() - t0) / 1_000_000
        println("$id ${puzzle.info.title}: ${s.value} in ${prep.solver.nodesExpanded} nodes, $ms ms")
        assertThat(s.value).isEqualTo(Value.WIN)
        assertThat(prep.rootNode.progress.state).isEqualTo(GoalState.OPEN)
        // The main line, with every player option that also wins (alternative solutions show here).
        var n = prep.rootNode
        for ((d, c) in prep.solver.mainLine(prep.rootNode)) {
            val side = prep.solver.sideOf(d)
            val good = if (side == Side.PLAYER) prep.solver.goodOptions(n).map { d.options[it] } else emptyList()
            println(
                "  $side ${d.seat} ${d.kind} '${d.prompt}': $c" +
                    if (good.isEmpty()) "" else "   winning: $good of ${d.options.size}",
            )
            n = prep.solver.child(n, c)!!
        }
    }

    @Test
    fun `the stored solution wins and stays on the forced line`() {
        val s = session()
        assertThat(s.status).isEqualTo(PuzzleStatus.PLAYING)
        play(s, puzzle.solution, "solution") { i ->
            if (i < puzzle.solution.size - 1) assertThat(s.status).isEqualTo(PuzzleStatus.PLAYING)
        }
        assertThat(s.status).isEqualTo(PuzzleStatus.SOLVED)
    }

    @Test
    fun `the last nudge names a winning first move`() {
        val s = session()
        play(s, puzzle.nudgedMoves, "nudged move")
        assertThat(s.status).isAnyOf(PuzzleStatus.PLAYING, PuzzleStatus.SOLVED)
        if (s.status == PuzzleStatus.PLAYING) assertThat(s.bestChoice()).isNotNull()
    }

    @Test
    fun `every wrong try is refuted`() {
        for (w in puzzle.wrongTries) {
            val s = session()
            play(s, w.moves, w.label) { i ->
                if (i < w.moves.size - 1) {
                    assertWithMessage("$id ${w.label}: missed too early").that(s.status).isEqualTo(PuzzleStatus.PLAYING)
                }
            }
            assertWithMessage("$id ${w.label}").that(s.status).isEqualTo(PuzzleStatus.MISSED)
            assertThat(s.bestChoice()).isNull()
            // Play on to the end off the key: every reply is a budgeted search.
            var more = 0
            var worst = 0L
            while (more < 200) {
                val d = s.game.pending ?: break
                val t0 = System.nanoTime()
                s.answer(d.id, d.options.first())
                val ms = (System.nanoTime() - t0) / 1_000_000
                assertWithMessage("$id ${w.label}: answer off the key took $ms ms").that(ms).isLessThan(ANSWER_MS)
                worst = maxOf(worst, ms)
                more++
            }
            println("$id ${w.label}: $more answers off the key, slowest $worst ms")
            assertThat(s.status).isEqualTo(PuzzleStatus.MISSED)
            // Undo back to just after the wrong answer (still missed), then that answer too.
            repeat(more) { assertThat(s.undo()).isTrue() }
            assertWithMessage("$id ${w.label} undone to the miss").that(s.status).isEqualTo(PuzzleStatus.MISSED)
            assertThat(s.undo()).isTrue()
            assertWithMessage("$id ${w.label} after undo").that(s.status).isEqualTo(PuzzleStatus.PLAYING)
        }
    }

    @Test
    fun `the first option at every decision does not solve it`() {
        val s = session()
        var n = 0
        while (s.status == PuzzleStatus.PLAYING && n++ < 200) {
            val d = s.game.pending ?: break
            s.answer(d.id, d.options.first())
        }
        assertThat(s.status).isNotEqualTo(PuzzleStatus.SOLVED)
    }

    @Test
    fun `show me always solves it`() {
        val s = session()
        var n = 0
        while (s.status == PuzzleStatus.PLAYING && n++ < 200) {
            val d = s.game.pending ?: break
            val c = s.bestChoice() ?: error("$id: no best choice at ${d.prompt}")
            s.answer(d.id, c)
        }
        assertThat(s.status).isEqualTo(PuzzleStatus.SOLVED)
    }

    @Test
    fun `the solver alone gives the answer key's verdicts`() {
        val keyed = book.prepared(id)
        assertThat(keyed.key).isNotNull()
        val bare = EnginePuzzleBook(TestData.engine, useKeys = false)
        for ((label, moves) in listOf("solution" to puzzle.solution) + puzzle.wrongTries.map { it.label to it.moves }) {
            val a = book.start(id) as EnginePuzzleSession
            val b = bare.start(id) as EnginePuzzleSession
            for (m in moves) {
                val da = a.game.pending!!
                val db = b.game.pending!!
                assertWithMessage("$id $label: same decision").that(db.options).isEqualTo(da.options)
                a.answer(da.id, m.resolve(da, a.position.session)!!)
                b.answer(db.id, m.resolve(db, b.position.session)!!)
                assertWithMessage("$id $label after $m").that(b.status).isEqualTo(a.status)
            }
        }
    }

    @Test
    fun `restart gives the starting position again`() {
        val s = session()
        play(s, puzzle.solution.take(1), "first move")
        val again = s.restart()
        assertThat(again.status).isEqualTo(PuzzleStatus.PLAYING)
        assertThat(again.game.pending!!.id).isEqualTo(session().game.pending!!.id)
    }
}
