package dev.quinton.goa.core.game.puzzles

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import dev.quinton.goa.core.game.Deadline
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.ai.AiPlayers
import dev.quinton.goa.core.game.ai.Evaluator
import dev.quinton.goa.core.game.ai.Features
import dev.quinton.goa.core.game.ai.StaticPolicy
import dev.quinton.goa.core.game.puzzles.book.Puzzles
import dev.quinton.goa.core.game.puzzles.rounds.RoundCertificate
import dev.quinton.goa.core.game.puzzles.rounds.RoundGate
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Test

/**
 * What a full round's words may claim (ADR-052, after the audit). Each check here would have caught a false
 * sentence in the first book. `ROUND_GATE=<seconds>` measures the book into `rounds/gate.txt` (opt-in, minutes a
 * puzzle); the rest hold the book to that table and to its own position, without searching.
 */
class FullRoundGateTest {
    private val table = RoundGate.table()
    private val ideas = RoundGate.ideas()

    private val texts = Puzzles.fullRounds.map { p ->
        p to (
            listOf(p.info.title, p.info.theme, p.info.intro, p.info.explanation) + p.info.nudgesByTurn.flatten() +
                p.info.known.mapNotNull { it.plan }
            )
    }

    @Test
    fun `measure the book`() {
        val seconds = System.getenv("ROUND_GATE")?.toLong() ?: return
        val only = System.getenv("ROUND_ONLY")?.split(",")?.toSet()
        val threads = System.getenv("AI_THREADS")?.toInt() ?: DEFAULT_THREADS
        val pool = Executors.newFixedThreadPool(threads) { r -> Thread(null, r, "gate", STACK) }
        val rows = HashMap(table.mapValues { it.value.format() })
        for (p in Puzzles.fullRounds.filter { only == null || it.info.id in only }) {
            pool.submit {
                runCatching {
                    val row = RoundGate.measure(p, ideas[p.info.id], seconds, OPTION_SECONDS, ::aiSolves)
                    println("GATE ${row.format()} grade ${row.grade} passes ${row.passes}")
                    synchronized(rows) {
                        rows[p.info.id] = row.format()
                        RoundGate.tableFile().writeText(rows.toSortedMap().values.joinToString("\n", postfix = "\n"))
                    }
                }.onFailure {
                    println("GATE ${p.info.id} failed: $it")
                    it.printStackTrace(System.out)
                }
            }
        }
        pool.shutdown()
        pool.awaitTermination(LONG_WAIT, TimeUnit.HOURS)
    }

    private fun aiSolves(p: Puzzle, strength: Int): Boolean {
        val prep = RoundCertificate.prepared(p)
        val solver = PuzzleSolver(prep.solver.tracker, known = p.known)
        val session = p.build(TestData.engine)
        session.quiet = true
        session.haltWhen = GoalTracker.haltAt(Deadline.END_OF_ROUND, session.state.round, 1, beforeRetrieve = true)
        val static = StaticPolicy(Evaluator(TestData.engine.data, Features.KNOBS))
        var n = solver.root(session)
        val ai = AiPlayers.deterministic(strength, AI_SEED + strength)
        var guard = 0
        while (n.progress.state == GoalState.OPEN && n.decision != null && guard++ < MAX_DECISIONS) {
            val d = n.decision!!
            val c = if (solver.sideOf(d) == Side.OPPONENT) {
                val ch = solver.choices(n)
                val s = if (ch.size == 1) null else solver.solve(n, budget = REPLY_BUDGET)
                when {
                    ch.size == 1 -> d.options[ch.single()]

                    s != null && s.value != Value.UNKNOWN -> d.options[s.best]

                    else -> {
                        val scores = static.scores(n.session.state, d)
                        d.options[ch.maxByOrNull { scores[it] }!!]
                    }
                }
            } else {
                ai.choose(n.session, d)
            }
            n = solver.child(n, c)!!
        }
        return n.progress.state == GoalState.MET
    }

    @Test
    fun `every puzzle passes the stricter gate`() {
        for (p in Puzzles.fullRounds) {
            val id = p.info.id
            val row = table[id]
            assertWithMessage("$id has a row in gate.txt: run ROUND_GATE").that(row).isNotNull()
            row!!
            assertWithMessage("$id: a proven loss in turn 3").that(row.witness).isNotEmpty()
            assertWithMessage("$id: without its idea it must not prove").that(row.idea).isNotEqualTo(Value.WIN)
            assertWithMessage("$id: solved by random clicks").that(row.randomSolved).isEqualTo(0)
            assertWithMessage("$id: strength 7 solves it").that(row.s7).isFalse()
            assertWithMessage("$id: grade").that(p.info.difficulty).isEqualTo(row.grade)
            assertWithMessage("$id: every first card proven to win").that(row.firstNotWon.sum()).isGreaterThan(0)
            assertWithMessage("$id has an idea with something to remove").that(ideas[id]?.probes.orEmpty()).isNotEmpty()
        }
    }

    @Test
    fun `the recorded turn 3 losses still lose`() {
        for (p in Puzzles.fullRounds) {
            val id = p.info.id
            val row = table[id] ?: continue
            if (row.witnessRuns > WITNESS_LIMIT) continue
            val prep = RoundCertificate.prepared(p)
            assertWithMessage("$id: the gate was measured on this position (rerun ROUND_GATE)")
                .that(row.fingerprint).isEqualTo(AnswerKey.fingerprint(prep.rootNode))
            assertWithMessage("$id: the option recorded as losing").that(
                RoundGate.witnessHolds(prep, row.witness, row.witnessRuns),
            ).isTrue()
        }
    }

    @Test
    fun `a plan says outlined when and only when it has something to outline`() {
        for (p in Puzzles.fullRounds) {
            for (play in p.info.known) {
                val plan = play.plan ?: continue
                val has = play.spaces.isNotEmpty() || play.targets.any { it !in p.info.heroes }
                assertWithMessage("${p.info.id} ${play.heroId} turn ${play.turn}: '$plan', spaces ${play.spaces}")
                    .that("outlined" in plan).isEqualTo(has)
            }
        }
    }

    @Test
    fun `a card named in a plan is the card listed for that turn, and the plan does not repeat it`() {
        val data = TestData.engine.data
        for (p in Puzzles.fullRounds) {
            for (play in p.info.known) {
                val plan = play.plan ?: continue
                val listed = data.card(play.cardId).name
                assertWithMessage("${p.info.id} ${play.heroId} turn ${play.turn}: '$plan' repeats $listed")
                    .that(plan.startsWith(listed)).isFalse()
                val others = data.catalogHero(play.heroId).allCards.map {
                    it.name
                }.filter { it != listed && it in plan }
                assertWithMessage("${p.info.id} ${play.heroId} turn ${play.turn}: '$plan', listed $listed")
                    .that(others).isEmpty()
            }
        }
    }

    @Test
    fun `a plan does not depend on where the player stands`() {
        for (p in Puzzles.fullRounds) {
            val mine = p.playerSeats.map { TestData.engine.data.heroName(it) }
            for (play in p.info.known) {
                val plan = play.plan ?: continue
                for (hero in mine) {
                    assertWithMessage("${p.info.id} ${play.heroId} turn ${play.turn}: '$plan'")
                        .that(plan).doesNotContain("next to $hero")
                }
            }
        }
    }

    @Test
    fun `life numbers in the text match the position`() {
        for ((p, words) in texts) {
            val st = p.build(TestData.engine).state
            val text = words.joinToString(" ")
            for (m in LIFE_LEFT.findAll(text)) {
                val team = st.lifeCounters.entries.first { it.key.name.equals(m.groupValues[1], ignoreCase = true) }
                assertWithMessage("${p.info.id}: '${m.value}'").that(m.groupValues[2].toInt()).isEqualTo(team.value)
            }
            for (m in LIFE_LAST.findAll(text)) {
                val team = st.lifeCounters.entries.first { it.key.name.equals(m.groupValues[1], ignoreCase = true) }
                assertWithMessage("${p.info.id}: '${m.value}'").that(m.groupValues[2].toInt()).isEqualTo(team.value)
            }
        }
    }

    @Test
    fun `nothing is said about turn 4 being free unless someone acts freely there`() {
        for ((p, words) in texts) {
            val row = table[p.info.id] ?: continue
            if (row.free4 == 0) {
                assertWithMessage("${p.info.id}: nobody acts freely in turn 4 on the proof's lines")
                    .that(words.joinToString(" ")).doesNotContain(TURN_FOUR_FREE)
            }
        }
    }

    @Test
    fun `puzzle text makes no claim about the computer's strength`() {
        for ((p, words) in texts) {
            for (w in words) assertWithMessage("${p.info.id}: '$w'").that(w).doesNotContain("strength")
        }
    }

    @Test
    fun `a text that says other plans win has the proof recorded`() {
        val claims = File("src/test/resources/dev/quinton/goa/core/game/puzzles/rounds/claims.txt")
            .readLines().filter { it.isNotBlank() }
        val ids = claims.map { it.substringBefore(' ') }.toSet()
        for ((p, words) in texts) {
            if (words.any { "other plans win" in it }) {
                assertWithMessage("${p.info.id} says other plans win").that(ids).contains(p.info.id)
            }
        }
        val book = Puzzles.fullRounds.map { it.info.id }
        for (line in claims) {
            assertThat(book).contains(line.substringBefore(' '))
            RoundGate.Ban.parse(line.substringAfter(' '))
        }
    }

    /** `ROUND_CLAIMS_CHECK=<seconds>`: proves every line of `claims.txt` again: each must still win without it. */
    @Test
    fun `the recorded claims still hold`() {
        val seconds = System.getenv("ROUND_CLAIMS_CHECK")?.toLong() ?: return
        val claims = File("src/test/resources/dev/quinton/goa/core/game/puzzles/rounds/claims.txt")
            .readLines().filter { it.isNotBlank() }
        val pool = Executors.newFixedThreadPool(DEFAULT_THREADS) { r -> Thread(null, r, "claims", STACK) }
        val out = java.util.concurrent.ConcurrentHashMap<String, Value>()
        for (line in claims) {
            pool.submit {
                val p = Puzzles.fullRounds.first { it.info.id == line.substringBefore(' ') }
                val prep = RoundCertificate.prepared(p)
                val ban = RoundGate.Ban.parse(line.substringAfter(' '))
                out[line] = RoundGate.without(prep.solver, prep.rootNode, listOf(ban), seconds)
                println("CLAIM $line: ${out[line]}")
            }
        }
        pool.shutdown()
        pool.awaitTermination(LONG_WAIT, TimeUnit.HOURS)
        for (line in claims) assertWithMessage(line).that(out[line]).isEqualTo(Value.WIN)
    }

    @Test
    fun `the book has at least twelve puzzles and no idea takes it over`() {
        assertThat(Puzzles.fullRounds.size).isAtLeast(FLOOR)
        val byIdea = Puzzles.fullRounds.groupBy { ideas[it.info.id]?.category }
        assertThat(byIdea.keys).containsAtLeast("battle", "strip", "last-card")
    }

    private companion object {
        const val FLOOR = 12
        const val DEFAULT_THREADS = 4
        const val STACK = 512L shl 20
        const val LONG_WAIT = 6L
        const val OPTION_SECONDS = 10L
        const val AI_SEED = 52L
        const val MAX_DECISIONS = 300
        const val REPLY_BUDGET = 1_500L
        const val WITNESS_LIMIT = 400_000L
        const val TURN_FOUR_FREE = "Turn 4 isn't known"
        val LIFE_LEFT = Regex("""(Red|Blue) has (\d) Life counters? left""")
        val LIFE_LAST = Regex("""(Red|Blue) spends its last (\d) Life counters?""")
    }
}
