package dev.quinton.goa.core.game.ai.bench

import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.AiPlayer
import dev.quinton.goa.core.game.AnswerResult
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.ai.AiHarness
import dev.quinton.goa.core.game.ai.AiPlayers
import dev.quinton.goa.core.game.ai.Evaluator
import dev.quinton.goa.core.game.ai.SampleRunner
import dev.quinton.goa.core.game.ai.Searcher
import dev.quinton.goa.core.game.ai.WinChance
import dev.quinton.goa.core.game.ai.heuristic.HeuristicAi
import dev.quinton.goa.core.game.ai.hybrid.HybridPlayers
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.game.engine.GameEngine
import dev.quinton.goa.core.game.puzzles.AnswerKey
import dev.quinton.goa.core.game.puzzles.Node
import dev.quinton.goa.core.game.puzzles.PreparedPuzzle
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.Value
import dev.quinton.goa.core.game.puzzles.book.Puzzles
import dev.quinton.goa.core.model.match.Team
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import org.junit.Test

/**
 * Opt-in position bench (wave 3, Track A): decision quality on fixed positions, thousands of answers in
 * minutes, before any game is played. `AI_BENCH=h10,h10+rest=0,x10+pol=1+scope=3+leaf=0` asks every spec
 * the same questions and prints, per set and per decision kind, how many it answered, the share it got
 * right and its mean regret. `AI_BENCH_SETS` picks sets (default all), `AI_THREADS` (default 2) positions
 * in parallel. Specs are those of `HeuristicTournamentTest` (h, s, x, y with `+knob=value`).
 *
 * The sets (version [VERSION]; a change to a set bumps it, so numbers are compared within one version):
 * - `puzzles`: every player question with a choice on every winning line of the 50+ turn puzzles' answer
 *   keys. Ground truth: the PUZZLE SOLVER (exact, independent of every AI). Right = the answer keeps the
 *   goal forced; regret = 1 for a miss.
 * - `rounds`: the player questions on the solution lines of the full-round puzzles (ADR-052, enemy plays
 *   known). The key holds the solution's answer; another answer is proved or refuted by the solver with
 *   [AI_BENCH_BUDGET] expansions (unknown answers are counted apart, never as right).
 * - `probe`: positions from h10's own games against s8 (line-ups from 1000, stored as replayable answers in
 *   `src/test/resources/bench/`). Ground truth: the ORACLE of the regret probe (REVIEW-2-losses.md: the old
 *   strength-10 search with the learned net, 3× card-play samples, no clock, opponent card model, kill
 *   extension) valuing EVERY option. It is a strong opinion, not the truth: right = its best option, regret
 *   in its coin scale and in win chance. `AI_BENCH_MAKE=<line-ups>` (with `AI_H_FIRST`, `AI_FORMAT`,
 *   `AI_BENCH_KEEP`) regenerates a probe file; never on the sealed line-ups 5000+.
 */
class AiPositionBenchTest {
    private val threads = System.getenv("AI_THREADS")?.toIntOrNull() ?: 2
    private val budget = System.getenv("AI_BENCH_BUDGET")?.toLongOrNull() ?: ROUND_BUDGET

    /**
     * `AI_BENCH_LASTWAVE=1`: puzzles and rounds keep only the questions asked on turn 3 while a lane is on its last
     * wave, the only ones the search's last-push extension (`lpx`, RESULTS.md "Wave 4 — Track P") can change.
     */
    private val lastWaveOnly = System.getenv("AI_BENCH_LASTWAVE") == "1"

    @Test
    fun `position bench`() {
        System.getenv("AI_BENCH_MAKE")?.toIntOrNull()?.let { return make(it) }
        val specs = System.getenv("AI_BENCH")?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: return
        val sets = (System.getenv("AI_BENCH_SETS") ?: "puzzles,rounds,probe").split(",").map { it.trim() }.toSet()
        loadVerdicts()
        for (spec in specs) {
            val t0 = System.nanoTime()
            val rows = ArrayList<Row>()
            if ("puzzles" in sets) rows += puzzleRows(spec, Puzzles.all, "puzzles")
            if ("rounds" in sets) rows += puzzleRows(spec, Puzzles.fullRounds, "rounds")
            if ("probe" in sets) rows += probeRows(spec)
            report(spec, rows, (System.nanoTime() - t0) / 1e9)
            saveVerdicts()
        }
    }

    /**
     * The solver's verdicts on full-round answers off the key, by `puzzle:path:option` (WIN, LOSS or UNKNOWN at
     * [ROUND_BUDGET]). Committed beside the probe files: every spec, run and track reuses them. A verdict depends
     * only on the engine and the position, so the file is cleared when [VERSION] or the engine version changes.
     */
    private val verdicts = ConcurrentHashMap<String, String>()
    private val verdictFile = File(PROBE_DIR, "rounds-verdicts-v$VERSION-e${GameEngine.ENGINE_VERSION}.txt")

    private fun loadVerdicts() {
        if (!verdictFile.exists() || budget != ROUND_BUDGET) return
        verdictFile.readLines().filter { it.isNotBlank() }.forEach { line ->
            verdicts[line.substringBefore(' ')] = line.substringAfter(' ')
        }
    }

    private fun saveVerdicts() {
        if (budget != ROUND_BUDGET) return
        verdictFile.parentFile.mkdirs()
        verdictFile.writeText(verdicts.entries.sortedBy { it.key }.joinToString("") { "${it.key} ${it.value}\n" })
    }

    /** One answered question: right or not, its regret (puzzles 0/1; probe coins and win chance). */
    private class Row(
        val set: String,
        val kind: DecisionKind,
        val right: Boolean?,
        val regret: Double,
        val wpRegret: Double = Double.NaN,
        val ms: Double,
    )

    private fun report(spec: String, rows: List<Row>, secs: Double) {
        println("BENCH v$VERSION $spec: ${rows.size} questions in ${"%.0f".format(secs)} s")
        for ((set, inSet) in rows.groupBy { it.set }.toSortedMap()) {
            println("BENCH   $spec $set ${line(inSet)}")
            for ((kind, k) in inSet.groupBy { it.kind }.toList().sortedByDescending { it.second.size }) {
                println("BENCH     $spec $set ${kind.name.lowercase()} ${line(k)}")
            }
        }
    }

    private fun line(rows: List<Row>): String {
        val known = rows.filter { it.right != null }
        val right = known.count { it.right == true }
        val pct = if (known.isEmpty()) 0.0 else 100.0 * right / known.size
        val unknown = rows.size - known.size
        val wp = rows.map { it.wpRegret }.filter { !it.isNaN() }
        val wpText = if (wp.isEmpty()) "" else " wpRegret ${"%.4f".format(wp.average())}"
        return "n=${rows.size} right ${"%.1f".format(pct)}% ($right/${known.size}" +
            (if (unknown > 0) ", $unknown unknown" else "") + ") regret ${"%.3f".format(
                known.map { it.regret }.average().takeIf { !it.isNaN() } ?: 0.0,
            )}$wpText ms ${"%.1f".format(rows.map { it.ms }.average())}"
    }

    // ---------------------------------------------------------------------------------------------
    // Puzzles and full rounds: the solver's answer keys.
    // ---------------------------------------------------------------------------------------------

    private fun puzzleRows(spec: String, puzzles: List<Puzzle>, set: String): List<Row> {
        val playable = puzzles.filter { p -> p.info.heroes.all { it in TestData.engine.playableHeroes } }
        val jobs = playable.map { p -> Callable { puzzle(spec, p, set) } }
        return runAll(jobs).flatten()
    }

    /** Every keyed player question of [p] with a real choice, answered by [spec] and judged. One solver: sequential. */
    private fun puzzle(spec: String, p: Puzzle, set: String): List<Row> {
        val pp = PreparedPuzzle(p, TestData.engine)
        val key = pp.key ?: run {
            println("BENCH   no current answer key for ${p.info.id}: skipped")
            return emptyList()
        }
        val out = ArrayList<Row>()
        fun visit(n: Node, path: List<Int>) {
            val d = n.decision ?: return
            when (val e = key.entries[AnswerKey.pathOf(path)] ?: return) {
                is AnswerKey.Wins -> {
                    val st = n.session.state
                    val asked = !lastWaveOnly || (st.turn == 3 && st.wavesLeft.values.any { it <= 1 })
                    if (asked && d.options.size > 1 && (pp.fullRound || e.options.size < d.options.size)) {
                        out += judgePuzzle(spec, pp, n, d, e.options, set, "${p.info.id}:${AnswerKey.pathOf(path)}")
                    }
                    for (i in e.options) visit(pp.solver.child(n, d.options[i]) ?: return, path + i)
                }

                is AnswerKey.Reply -> visit(pp.solver.child(n, d.options[e.option]) ?: return, path + e.option)
            }
        }
        visit(pp.rootNode, emptyList())
        return out
    }

    private fun judgePuzzle(
        spec: String,
        pp: PreparedPuzzle,
        n: Node,
        d: Decision,
        wins: List<Int>,
        set: String,
        id: String,
    ): Row {
        val player = BenchSpecs.player(spec, seedOf(id))
        val t0 = System.nanoTime()
        val c = player.choose(n.session.forkSession(), d)
        val ms = (System.nanoTime() - t0) / 1e6
        val i = d.options.indexOf(c)
        val right: Boolean? = when {
            i < 0 -> false

            i in wins -> true

            // A full round's key holds the solution only: another answer is proved or refuted (cached).
            pp.fullRound -> verdicts.getOrPut("$id:$i") {
                pp.solver.child(n, c)?.let { child ->
                    pp.solver.solve(child, budget = minOf(budget, pp.puzzle.nodeBudget)).value.name
                } ?: Value.LOSS.name
            }.let { if (it == Value.UNKNOWN.name) null else it == Value.WIN.name }

            else -> false
        }
        // AI_BENCH_MISSES=1: name every question answered wrong (to read it with the solver's key).
        if (right == false && System.getenv("AI_BENCH_MISSES") == "1") println("BENCH   miss $spec $id ${d.kind} $c")
        return Row(set, d.kind, right, if (right == true) 0.0 else 1.0, ms = ms)
    }

    // ---------------------------------------------------------------------------------------------
    // Probe positions: stored games, oracle values per option.
    // ---------------------------------------------------------------------------------------------

    private class Probe(
        val game: Int,
        val format: String,
        val upTo: Int,
        val id: Long,
        val values: List<Double>,
        val wp: List<Double>,
    )

    private class StoredGame(
        val format: AiHarness.Format,
        val red: List<String>,
        val blue: List<String>,
        val coin: Team,
        val answers: List<Int>,
    )

    private fun probeRows(spec: String): List<Row> {
        val dir = File(PROBE_DIR)
        val files = dir.listFiles { f -> f.name.endsWith(".jsonl.gz") }?.sortedBy { it.name }.orEmpty()
        if (files.isEmpty()) println("BENCH   no probe files in $PROBE_DIR")
        val jobs = ArrayList<Callable<List<Row>>>()
        for (f in files) {
            val games = HashMap<Int, StoredGame>()
            val probes = ArrayList<Probe>()
            GZIPInputStream(f.inputStream()).bufferedReader().useLines { lines ->
                for (raw in lines) {
                    val o = Json.parseToJsonElement(raw).jsonObject
                    if ("answers" in o) {
                        games[o["game"]!!.jsonPrimitive.int] = StoredGame(
                            AiHarness.Format.parse(o["format"]!!.jsonPrimitive.content),
                            o["red"]!!.jsonArray.map { it.jsonPrimitive.content },
                            o["blue"]!!.jsonArray.map { it.jsonPrimitive.content },
                            Team.valueOf(o["coin"]!!.jsonPrimitive.content),
                            o["answers"]!!.jsonPrimitive.content.split(",")
                                .filter { it.isNotEmpty() }.map { it.toInt() },
                        )
                    } else {
                        probes += Probe(
                            o["game"]!!.jsonPrimitive.int,
                            f.name,
                            o["n"]!!.jsonPrimitive.int,
                            o["id"]!!.jsonPrimitive.long,
                            o["v"]!!.jsonArray.map { it.jsonPrimitive.double },
                            o["wp"]!!.jsonArray.map { it.jsonPrimitive.double },
                        )
                    }
                }
            }
            // One job per game: the replay is shared by its positions, in order.
            for ((g, ps) in probes.groupBy { it.game }) {
                val game = games[g] ?: continue
                jobs += Callable { probeGame(spec, game, ps.sortedBy { it.upTo }) }
            }
        }
        return runAll(jobs).flatten()
    }

    private fun probeGame(spec: String, game: StoredGame, probes: List<Probe>): List<Row> {
        val es = newGame(game)
        val out = ArrayList<Row>()
        var done = 0
        for (p in probes) {
            while (done < p.upTo) {
                val d = es.pending ?: error("probe replay ended early")
                check(es.answer(d.id, d.options[game.answers[done]]) is AnswerResult.Accepted)
                done++
            }
            val d = es.pending ?: error("probe replay: no question")
            check(d.id == p.id) { "probe replay drifted: ${d.id} != ${p.id} (engine changed? regenerate)" }
            val player = BenchSpecs.player(spec, seedOf("${p.format}:${p.id}:${d.seat}"))
            val t0 = System.nanoTime()
            val c = player.choose(es.forkSession(), d)
            val ms = (System.nanoTime() - t0) / 1e6
            val i = d.options.indexOf(c)
            val best = p.values.max()
            val right = i >= 0 && p.values[i] >= best - 1e-9
            val regret = if (i >= 0) best - p.values[i] else best - p.values.min()
            val wpRegret = if (i >= 0) p.wp.max() - p.wp[i] else p.wp.max() - p.wp.min()
            out += Row("probe", d.kind, right, regret, wpRegret, ms)
        }
        return out
    }

    private fun newGame(game: StoredGame): EngineSession {
        val setup = AiHarness.setup(
            game.red,
            game.blue,
            AiLevel.NORMAL,
            AiLevel.NORMAL,
            game.coin,
            game.format.length,
            game.format.mapId,
        )
        return TestData.engine.newGame(setup) as EngineSession
    }

    /**
     * Writes a probe file: h10 against s8 on [lineups] mirrored line-ups from `AI_H_FIRST` (default 1000),
     * each h10 question with a choice kept with chance `AI_BENCH_KEEP` (default 0.06, by a hash of the game
     * and question: deterministic), and valued option by option by the oracle.
     */
    private fun make(lineups: Int) {
        val format = AiHarness.Format.parse(System.getenv("AI_FORMAT"))
        val first = System.getenv("AI_H_FIRST")?.toIntOrNull() ?: 1000
        require(first + lineups <= SEALED) { "line-ups $SEALED+ are sealed for the final table" }
        val keep = System.getenv("AI_BENCH_KEEP")?.toDoubleOrNull() ?: 0.06
        val all = AiHarness.mixedLineups(first + lineups, size = format.size)
        val jobs = ArrayList<Callable<List<String>>>()
        for (i in first until first + lineups) {
            for (hRed in listOf(true, false)) jobs += Callable { makeGame(format, all[i], i, hRed, keep) }
        }
        val lines = runAll(jobs).flatten()
        val name = "probe-v$VERSION-${format.label.replace(" ", "")}-$first-${first + lineups - 1}.jsonl.gz"
        val out = File(PROBE_DIR, name)
        out.parentFile.mkdirs()
        GZIPOutputStream(out.outputStream()).bufferedWriter().use { w -> lines.forEach { w.write(it + "\n") } }
        println("BENCH_MAKE wrote ${lines.count { !it.contains("\"answers\"") }} positions to $out")
    }

    @Suppress("LongMethod")
    private fun makeGame(
        format: AiHarness.Format,
        lineup: Pair<List<String>, List<String>>,
        index: Int,
        hRed: Boolean,
        keep: Double,
    ): List<String> {
        val (red, blue) = lineup
        val coin = if (index % 2 == 0) Team.RED else Team.BLUE
        val seed = 50_000L + index
        val hTeam = if (hRed) Team.RED else Team.BLUE
        val h = HeuristicAi(10, if (hRed) seed * 2 else seed * 2 + 1)
        val s = AiPlayers.searchDeterministic(8, if (hRed) seed * 2 + 1 else seed * 2)
        val game = StoredGame(format, red, blue, coin, emptyList())
        val es = newGame(game)
        val oracle = Searcher(Evaluator(es.engineData, ORACLE.knobs), ORACLE)
        val gameNo = index * 2 + if (hRed) 0 else 1
        val answers = ArrayList<Int>()
        val out = ArrayList<String>()
        var n = 0
        while (n < MAX_DECISIONS) {
            val d = es.pending ?: break
            val player: AiPlayer = if (d.team == hTeam) h else s
            val c = player.choose(es, d)
            if (d.team == hTeam && d.options.size > 1 && unit(gameNo, d.id) < keep) {
                val oseed = HeuristicAi.mix(ORACLE_SALT, d.id * 1_000_003L + d.seat.hashCode())
                val a = oracle.analyze(es, d, oseed, { false }, Long.MAX_VALUE, only = d.options)
                val byChoice = a?.options?.associate { it.choice to it.values }.orEmpty()
                if (d.options.all { it in byChoice }) {
                    val size = es.setup.seats.count { it.team == Team.RED }
                    val v = d.options.map { o -> mean(byChoice.getValue(o)) }
                    val chance = { x: Double -> if (x.isNaN()) x else WinChance.of(x, size) }
                    val wp = d.options.map { o -> mean(byChoice.getValue(o).map(chance).toDoubleArray()) }
                    out += buildJsonObject {
                        put("game", gameNo)
                        put("n", answers.size)
                        put("id", d.id)
                        put("kind", d.kind.name)
                        put("seat", d.seat)
                        put("v", buildJsonArray { v.forEach { add(JsonPrimitive(r3(it))) } })
                        put("wp", buildJsonArray { wp.forEach { add(JsonPrimitive(r3(it))) } })
                    }.toString()
                }
            }
            answers += d.options.indexOf(c)
            check(es.answer(d.id, c) is AnswerResult.Accepted)
            n++
        }
        val header: JsonObject = buildJsonObject {
            put("game", gameNo)
            put("format", format.label.replace(" ", ""))
            put("red", buildJsonArray { red.forEach { add(JsonPrimitive(it)) } })
            put("blue", buildJsonArray { blue.forEach { add(JsonPrimitive(it)) } })
            put("coin", coin.name)
            put("lineup", index)
            put("hSide", hTeam.name)
            put("answers", answers.joinToString(","))
        }
        println("BENCH_MAKE game $index ${if (hRed) "R" else "B"}: ${out.size} positions")
        return listOf(header.toString()) + out
    }

    private fun mean(v: DoubleArray): Double = v.filter { !it.isNaN() }.average().let { if (it.isNaN()) -1e9 else it }

    private fun r3(x: Double): Double = kotlin.math.round(x * 1000.0) / 1000.0

    private fun unit(game: Int, id: Long): Double =
        (HeuristicAi.mix(game.toLong() * 7_919L + KEEP_SALT, id) ushr 11).toDouble() / (1L shl 53).toDouble()

    private fun seedOf(id: String): Long = HeuristicAi.mix(BENCH_SALT, id.hashCode().toLong())

    private fun <T> runAll(jobs: List<Callable<T>>): List<T> {
        val pool = Executors.newFixedThreadPool(threads.coerceAtLeast(1))
        return try {
            pool.invokeAll(jobs).map { it.get() }
        } finally {
            pool.shutdown()
        }
    }

    companion object {
        /** Bump when a set changes (new positions, another oracle). */
        const val VERSION = 1
        const val PROBE_DIR = "src/test/resources/bench"
        private const val SEALED = 5000
        private const val ROUND_BUDGET = 200_000L
        private const val MAX_DECISIONS = 8000
        private const val ORACLE_SALT = 0x0EAC1EL
        private const val KEEP_SALT = 0xBE4C11L
        private const val BENCH_SALT = 0xBE4C40L

        /** The regret probe's oracle (HeuristicRegretTest): s10's search, 3× card samples, model, kill extension. */
        private val ORACLE = AiPlayers.profile(10).let {
            it.copy(playSamples = it.playSamples * 3, otherSamples = 4, rm = 1.0, killExt = 1)
        }
    }
}

/** The specs of `HeuristicTournamentTest`, for the bench (h, s, x, y with `+knob=value`). */
internal object BenchSpecs {
    fun player(spec: String, seed: Long): AiPlayer {
        // Track E: a hybrid search with a saved net at its leaves (`x10+…+net=avg(a,b,c)`, `W2+net=…`).
        if ("+net=" in spec || spec.startsWith("W2")) {
            return dev.quinton.goa.core.game.ai.learned.LeafNets.player(spec, seed)
        }
        val parts = spec.split("+")
        val n = parts[0].drop(1).toInt()
        val tune = parts.drop(1).associate { kv -> kv.substringBefore("=") to kv.substringAfter("=").toDouble() }
        return when (spec[0]) {
            'h', 'H' -> HeuristicAi(n, seed, tune = tune)
            's', 'S' -> AiPlayers.searchDeterministic(n, seed)
            'x', 'X' -> HybridPlayers.hybrid(seed, tune, SampleRunner.SEQUENTIAL)
            'y', 'Y' -> HybridPlayers.searchWithBook(seed, tune, SampleRunner.SEQUENTIAL)
            else -> error("AI_BENCH wants h10, s8 or x10+…, not $spec")
        }
    }
}
