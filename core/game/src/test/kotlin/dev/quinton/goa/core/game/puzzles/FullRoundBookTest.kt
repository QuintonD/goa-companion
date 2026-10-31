package dev.quinton.goa.core.game.puzzles

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import dev.quinton.goa.core.game.AnswerResult
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.PuzzleStatus
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.engine.GameEngine
import dev.quinton.goa.core.game.puzzles.book.Puzzles
import dev.quinton.goa.core.game.puzzles.rounds.PlanProver
import dev.quinton.goa.core.game.puzzles.rounds.RoundCertificate
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * The checks every full round must pass (ENCODING.md "Full rounds"), once per puzzle in
 * [Puzzles.fullRounds]. The proof itself is opt-in (`FullRoundProveTest`); this replays its certificate
 * against every enemy answer, so a rules change that breaks a puzzle fails the build without searching.
 */
@RunWith(Parameterized::class)
class FullRoundBookTest(private val puzzle: Puzzle) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun puzzles(): List<Array<Any>> = Puzzles.fullRounds.map { arrayOf(it) }

        /** Desktop budget for one answer off the key (a phone is ~4× slower: 2 s in the worst case, usually far less). */
        const val ANSWER_MS = PuzzleBookTest.ANSWER_MS

        const val TURNS = 4
        const val END_OF_THE_LINE = 300
    }

    private val book = EnginePuzzleBook(TestData.engine, listOf(puzzle))
    private val id = puzzle.info.id
    private val known get() = checkNotNull(puzzle.known)

    private fun session() = book.start(id) as EnginePuzzleSession

    private fun certificate() = checkNotNull(RoundCertificate.load(id)) { "$id has no certificate" }

    private fun play(s: EnginePuzzleSession, moves: List<Move>, label: String, check: (Int) -> Unit = {}) {
        moves.forEachIndexed { i, m ->
            val d = s.game.pending ?: error("$id $label: no decision for move ${i + 1} ($m)")
            val c = m.resolve(d, s.position.session)
                ?: error("$id $label: move ${i + 1} ($m) is not offered at ${d.kind} ${d.prompt}: ${d.options}")
            val t0 = System.nanoTime()
            val r = s.answer(d.id, c)
            val ms = (System.nanoTime() - t0) / 1_000_000
            assertWithMessage("$id $label move ${i + 1} $m: $r").that(r).isInstanceOf(AnswerResult.Accepted::class.java)
            assertWithMessage("$id $label move ${i + 1} took $ms ms").that(ms).isLessThan(ANSWER_MS)
            check(i)
        }
    }

    @Test
    fun `the info is complete and every enemy card is listed`() {
        val info = puzzle.info
        assertThat(info.id).matches("r\\d\\d")
        assertThat(info.id.drop(1)).isEqualTo(puzzle.concept.drop(1))
        assertThat(info.difficulty).isIn(1..3)
        assertThat(info.fullRound).isTrue()
        assertThat(info.intro).isNotEmpty()
        assertThat(info.rules).isNotEmpty()
        assertThat(info.heroes.first()).isIn(puzzle.playerSeats)
        assertThat(info.nudgesByTurn).hasSize(TURNS)
        for (n in info.nudgesByTurn) assertThat(n.size).isAtLeast(3)
        for (t in 1..TURNS) assertThat(info.explanation).contains("Turn $t")
        val seats = puzzle.build(TestData.engine).setup.seats
        assertThat(seats.map { it.heroId }).containsExactlyElementsIn(info.heroes)
        val mine = seats.first { it.heroId in puzzle.playerSeats }.team
        val enemies = seats.filter { it.team != mine }.map { it.heroId }
        assertThat(known.cards.keys).containsExactlyElementsIn(enemies)
        for ((hero, cards) in known.cards) {
            assertWithMessage("$id $hero's cards").that(cards).hasSize(TURNS)
            assertWithMessage("$id $hero's cards").that(cards).containsNoDuplicates()
        }
        // What the player reads is what the solver plays: every known turn has its plan, no other does.
        for (play in info.known) {
            val isKnown = known.actions[play.heroId]?.containsKey(play.turn) == true
            assertWithMessage("$id ${play.heroId} turn ${play.turn}: a plan where the action is known")
                .that(play.plan != null).isEqualTo(isKnown)
        }
    }

    @Test
    fun `the certificate holds against every enemy answer`() {
        val checked = certificate().check(RoundCertificate.prepared(puzzle))
        println("$id certificate: ${checked.positions} positions, ${checked.edges} edges")
        assertWithMessage("$id: ${checked.error} (run FullRoundProveTest if the rules changed)")
            .that(checked.error).isNull()
    }

    @Test
    fun `the answer key is the certificate's solution line`() {
        val prep = RoundCertificate.prepared(puzzle)
        val fresh = certificate().key(prep)
        val stored = AnswerKey.load(id)
        if (stored != fresh) {
            val f = java.io.File("src/main/resources/${AnswerKey.RESOURCE_DIR}/$id.key")
            f.parentFile.mkdirs()
            f.writeText(fresh.format())
            fail("$id: the answer key was ${if (stored == null) "missing" else "stale"}; rewrote ${f.path}: run again")
        }
        assertThat(stored!!.engineVersion).isEqualTo(GameEngine.ENGINE_VERSION)
    }

    @Test
    fun `it really takes four turns`() {
        val line = certificate().line(RoundCertificate.prepared(puzzle))
        assertWithMessage("$id: the turn its solution ends in").that(PlanProver.turnOf(line.last().next)).isAtLeast(TURNS)
    }

    @Test
    fun `the stored solution wins and stays on the line`() {
        val s = session()
        assertThat(s.status).isEqualTo(PuzzleStatus.PLAYING)
        play(s, puzzle.solution, "solution") { i ->
            if (i < puzzle.solution.size - 1) assertThat(s.status).isEqualTo(PuzzleStatus.PLAYING)
        }
        assertThat(s.status).isEqualTo(PuzzleStatus.SOLVED)
    }

    @Test
    fun `the last nudge of each turn names that turn's card`() {
        val data = TestData.engine.data
        val s = session()
        val named = HashMap<Int, MutableList<String>>()
        var moves = 0
        while (s.status == PuzzleStatus.PLAYING && moves++ < END_OF_THE_LINE) {
            val d = s.game.pending ?: break
            val c = s.bestChoice() ?: error("$id: no best choice at ${d.prompt}")
            if (d.kind.name == "PLAY_CARD" && c is Choice.Card) {
                named.getOrPut(s.position.session.state.turn) { ArrayList() } += data.card(c.cardId).name
            }
            s.answer(d.id, c)
        }
        for ((turn, cards) in named) {
            val last = puzzle.info.nudgesByTurn[turn - 1].last()
            for (card in cards) assertWithMessage("$id turn $turn's last nudge").that(last).contains(card)
        }
    }

    @Test
    fun `a tempting wrong line fails and can be taken back`() {
        for (w in puzzle.wrongTries) {
            val s = session()
            // The line was played against the search's answers; off the key they depend on its budget, so
            // a move no longer offered gives way to the first option, and the line goes on.
            val left = ArrayDeque(w.moves)
            var more = 0
            var worst = 0L
            while (s.status == PuzzleStatus.PLAYING && more++ < END_OF_THE_LINE) {
                val d = s.game.pending ?: break
                val c = left.removeFirstOrNull()?.resolve(d, s.position.session) ?: d.options.first()
                val t0 = System.nanoTime()
                s.answer(d.id, c)
                worst = maxOf(worst, (System.nanoTime() - t0) / 1_000_000)
            }
            println("$id ${w.label}: $more answers off the key, slowest $worst ms, ${s.status}")
            assertWithMessage("$id ${w.label}: slowest answer off the key").that(worst).isLessThan(ANSWER_MS)
            assertWithMessage("$id ${w.label}").that(s.status).isEqualTo(PuzzleStatus.MISSED)
            assertThat(s.undo()).isTrue()
        }
    }

    @Test
    fun `the first option at every decision does not solve it`() {
        val s = session()
        var n = 0
        while (s.status == PuzzleStatus.PLAYING && n++ < END_OF_THE_LINE) {
            val d = s.game.pending ?: break
            s.answer(d.id, d.options.first())
        }
        assertThat(s.status).isNotEqualTo(PuzzleStatus.SOLVED)
    }

    @Test
    fun `show me always solves it`() {
        val s = session()
        var n = 0
        while (s.status == PuzzleStatus.PLAYING && n++ < END_OF_THE_LINE) {
            val d = s.game.pending ?: break
            s.answer(d.id, s.bestChoice() ?: error("$id: no best choice at ${d.prompt}"))
        }
        assertThat(s.status).isEqualTo(PuzzleStatus.SOLVED)
    }

    @Test
    fun `the enemy plays its known cards while it can`() {
        val s = session()
        var n = 0
        while (s.status == PuzzleStatus.PLAYING && n++ < END_OF_THE_LINE) {
            val d = s.game.pending ?: break
            s.answer(d.id, s.bestChoice() ?: error("$id: no best choice"))
        }
        val played = s.game.events.filterIsInstance<dev.quinton.goa.core.game.GameEvent.CardsRevealed>()
        played.forEachIndexed { i, e ->
            for ((hero, cards) in known.cards) {
                val listed = cards[i]
                val card = e.played[hero]
                // A listed card can only be missing if it has left the hand first (a defense, a discard).
                if (card != listed) {
                    val at = s.position.session.state.hero(hero)?.card(listed)?.zone
                    assertWithMessage("$id turn ${i + 1}: $hero played $card, not $listed ($at)")
                        .that(at).isEqualTo(dev.quinton.goa.core.game.engine.Zone.DISCARD)
                }
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
