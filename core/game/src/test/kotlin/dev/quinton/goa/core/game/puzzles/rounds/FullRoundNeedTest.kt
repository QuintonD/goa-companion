package dev.quinton.goa.core.game.puzzles.rounds

import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.puzzles.GoalState
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.Side
import dev.quinton.goa.core.game.puzzles.Value
import dev.quinton.goa.core.game.puzzles.book.Puzzles
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Test

/**
 * What a full round actually needs (ADR-052, after the audit): `ROUND_NEED=<seconds>` takes away each of the
 * player's cards, each enemy minion and each enemy hero as a target, in turn, and asks the prover again.
 * WIN means the puzzle doesn't need that; its text may not claim it. Opt-in, written to `ROUND_NEED_OUT`.
 */
class FullRoundNeedTest {
    @Test
    fun `what each puzzle needs`() {
        if (System.getenv("ROUND_CLAIMS") != null) return
        val seconds = System.getenv("ROUND_NEED")?.toLong() ?: return
        val out = File(System.getenv("ROUND_NEED_OUT"))
        val only = System.getenv("ROUND_ONLY")?.split(",")?.toSet()
        val threads = System.getenv("AI_THREADS")?.toInt() ?: DEFAULT_THREADS
        val done = if (out.exists()) out.readLines().map { it.split(" ")[1] }.toSet() else emptySet()
        val pool = Executors.newFixedThreadPool(threads) { r -> Thread(null, r, "need", STACK) }
        for (p in Puzzles.fullRounds.filter { (only == null || it.info.id in only) && it.info.id !in done }) {
            pool.submit {
                val line = runCatching { need(p, seconds) }.getOrElse { "NEED ${p.info.id} FAILED $it" }
                println(line)
                synchronized(out) { out.appendText(line + "\n") }
            }
        }
        pool.shutdown()
        pool.awaitTermination(LONG_WAIT, TimeUnit.HOURS)
    }

    /** `ROUND_OPTIONS=<seconds>`: the verdict on every option at the solution line's decisions, the text's facts. */
    @Test
    fun `options of the solution line`() {
        val seconds = System.getenv("ROUND_OPTIONS")?.toLong() ?: return
        val out = File(System.getenv("ROUND_NEED_OUT"))
        val only = System.getenv("ROUND_ONLY")?.split(",")?.toSet()
        val threads = System.getenv("AI_THREADS")?.toInt() ?: DEFAULT_THREADS
        val pool = Executors.newFixedThreadPool(threads) { r -> Thread(null, r, "options", STACK) }
        for (p in Puzzles.fullRounds.filter { only == null || it.info.id in only }) {
            pool.submit {
                val text = runCatching {
                    options(p, seconds)
                }.getOrElse { "${p.info.id} FAILED $it${System.lineSeparator()}" }
                synchronized(out) { out.appendText(text) }
            }
        }
        pool.shutdown()
        pool.awaitTermination(LONG_WAIT, TimeUnit.HOURS)
    }

    /** `ROUND_CLAIMS=<file>`, lines `rNN T1-4 card:Name`: what the text says isn't needed, asked directly. */
    @Test
    fun `claims of the text`() {
        val file = System.getenv("ROUND_CLAIMS") ?: return
        val seconds = System.getenv("ROUND_NEED")?.toLong() ?: DEFAULT_CLAIM_SECONDS
        val out = File(System.getenv("ROUND_NEED_OUT"))
        val threads = System.getenv("AI_THREADS")?.toInt() ?: DEFAULT_THREADS
        val pool = Executors.newFixedThreadPool(threads) { r -> Thread(null, r, "claims", STACK) }
        for (line in File(file).readLines().filter { it.isNotBlank() }) {
            val id = line.substringBefore(' ')
            val probe = line.substringAfter(' ')
            pool.submit {
                val p = Puzzles.fullRounds.first { it.info.id == id }
                val prep = RoundCertificate.prepared(p)
                val ban = RoundGate.Ban.parse(probe)
                val v = RoundGate.without(prep.solver, prep.rootNode, listOf(ban), seconds)
                synchronized(out) { out.appendText("$id $probe: $v${System.lineSeparator()}") }
            }
        }
        pool.shutdown()
        pool.awaitTermination(LONG_WAIT, TimeUnit.HOURS)
    }

    private fun options(p: Puzzle, seconds: Long): String = buildString {
        val prep = RoundCertificate.prepared(p)
        val solver = prep.solver
        val data = TestData.engine.data
        val line = checkNotNull(RoundCertificate.load(p.info.id)).line(prep)
        appendLine("=== ${p.info.id} ${p.info.title}")
        for (step in line) {
            val d = step.decision
            val turn = PlanProver.turnOf(step.at)
            if (solver.sideOf(d) != Side.PLAYER || d.options.size < 2) continue
            if (turn < THREE && d.kind != DecisionKind.PLAY_CARD) continue
            val verdicts = d.options.indices.map { i ->
                val c = d.options[i]
                val name = (c as? Choice.Card)?.let { data.card(it.cardId).name } ?: c.toString()
                val child = solver.child(step.at, c)!!
                val v = when {
                    i == step.option -> "*"
                    child.progress.state == GoalState.MET -> "W"
                    child.progress.state != GoalState.OPEN || child.decision == null -> "L"
                    else -> PlanProver(solver, MAX_RUNS, System.nanoTime() + seconds * NANOS).solve(child).name.take(1)
                }
                "$name=$v"
            }
            val shown = if (verdicts.size > MANY) {
                verdicts.groupBy { it.last() }.entries.joinToString(" ") { (k, l) -> "$k:${l.size}" } +
                    " L: " + verdicts.filter { it.endsWith("=L") }.take(MANY).joinToString(", ") +
                    " *: " + verdicts.first { it.endsWith("=*") }
            } else {
                verdicts.joinToString(", ")
            }
            appendLine("T$turn ${d.seat} ${d.kind} '${d.prompt}': $shown")
        }
    }

    private fun need(p: Puzzle, seconds: Long): String {
        val prep = RoundCertificate.prepared(p)
        val solver = prep.solver
        val cert = checkNotNull(RoundCertificate.load(p.info.id))
        val line = cert.line(prep)
        // The first proven loss in turn 3, or nothing here is worth more of the machine's time.
        var witness = ""
        steps@ for (step in line) {
            val d = step.decision
            if (PlanProver.turnOf(step.at) != THREE || solver.sideOf(d) != Side.PLAYER || d.options.size < 2) continue
            for (i in d.options.indices) {
                if (i == step.option) continue
                val child = solver.child(step.at, d.options[i])!!
                val v = when {
                    child.progress.state == GoalState.MET -> Value.WIN
                    child.progress.state != GoalState.OPEN || child.decision == null -> Value.LOSS
                    else -> PlanProver(solver, MAX_RUNS, System.nanoTime() + WITNESS_SECONDS * NANOS).solve(child)
                }
                if (v == Value.LOSS) {
                    witness = "${d.seat} ${d.kind} ${d.options[i]}".replace(' ', '_')
                    break@steps
                }
            }
        }
        if (witness.isEmpty()) return "NEED ${p.info.id} no-T3-loss"
        val data = TestData.engine.data
        val root = prep.rootNode
        val st = root.session.state
        val cards = line.filter {
            PlanProver.turnOf(it.at) == 1 && it.decision.kind == DecisionKind.PLAY_CARD &&
                solver.sideOf(it.decision) == Side.PLAYER
        }.flatMap { s -> s.decision.options.filterIsInstance<Choice.Card>().map { data.card(it.cardId).name } }
        val goldOnLine = line.filter {
            it.decision.kind == DecisionKind.PLAY_CARD &&
                solver.sideOf(it.decision) == Side.PLAYER
        }
            .mapNotNull { (it.choice as? Choice.Card)?.let { c -> data.card(c.cardId).name } }
        val mine = p.playerSeats
        val heroes = p.info.heroes.filter { it !in mine }
        val teamOfMine = st.heroes.first { it.id in mine }.team
        val enemyTeam = st.minions.first { it.team != teamOfMine }.team.name
        val probes = heroes.flatMap { h -> listOf("T1-4 defeat:$h", "T1-4 spare:$h") } + "T1-4 kill:$enemyTeam" +
            (cards + goldOnLine).distinct().map { "T1-4 card:$it" }
        val results = probes.map { probe ->
            val v = RoundGate.without(solver, root, listOf(RoundGate.Ban.parse(probe)), seconds)
            "$probe=${v.name[0]}"
        }
        return "NEED ${p.info.id} t3[$witness] " + results.joinToString(" | ")
    }

    private companion object {
        const val DEFAULT_THREADS = 4
        const val STACK = 512L shl 20
        const val LONG_WAIT = 2L
        const val THREE = 3
        const val MANY = 12
        const val DEFAULT_CLAIM_SECONDS = 120L
        const val WITNESS_SECONDS = 10L
        const val NANOS = 1_000_000_000L
        const val MAX_RUNS = 40_000_000L
    }
}
