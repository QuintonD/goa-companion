package dev.quinton.goa.core.game.ai.heuristic

import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.AiPlayer
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.ai.AiHarness
import dev.quinton.goa.core.game.ai.AiPlayers
import dev.quinton.goa.core.game.ai.LearnedNet
import dev.quinton.goa.core.game.ai.NetEvaluator
import dev.quinton.goa.core.game.ai.SampleRunner
import dev.quinton.goa.core.game.ai.Searcher
import dev.quinton.goa.core.game.ai.hybrid.HybridAi
import dev.quinton.goa.core.game.ai.hybrid.HybridPlayers
import dev.quinton.goa.core.game.engine.Determinizer
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.model.match.Team
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt
import org.junit.Test

/**
 * Opt-in calibration of [HEval] (HEVAL.md "Calibration"), in two steps.
 *
 * **Record.** `AI_HEVAL_DATA=h10:h10,x10+pol=1+scope=3:s10` plays mirrored games on mixed line-ups
 * (`AI_H_GAMES` line-ups from `AI_H_FIRST`, like `HeuristicTournamentTest`; `AI_THREADS`, `AI_FORMAT`)
 * and appends to `AI_HEVAL_OUT` one row per team at the first decision of every turn (where a turn-horizon
 * search scores its leaves): the [HTerm] features from that team's side, computed on a copy determinized
 * from that team's view exactly as the hybrid's leaf is (strength 10's view, ADR-062), the old learned
 * net's logit on the same copy, and whether that team won.
 *
 * **Fit.** `AI_HEVAL_FIT=a.csv,b.csv` fits P(win) = σ(Σ vᵢ · fᵢ) by logistic regression, ridge toward the
 * hand-set weights scaled by the slope k of the current evaluation, on three quarters of the games, and
 * reports the held-out quarter: log-loss and calibration of the current HEval, the fit and the net.
 * The fitted weights in coin units are vᵢ / k. `AI_HEVAL_TERMS=lane,life,...` fits a subset (the rest keep
 * their hand weight inside a fixed offset), `AI_HEVAL_L2` the ridge strength (default 0.002).
 */
class HEvalCalibrationTest {
    @Test
    fun `record positions`() {
        val spec = System.getenv("AI_HEVAL_DATA") ?: return
        val out = File(System.getenv("AI_HEVAL_OUT") ?: error("AI_HEVAL_OUT"))
        val lineups = System.getenv("AI_H_GAMES")?.toIntOrNull() ?: 16
        val first = System.getenv("AI_H_FIRST")?.toIntOrNull() ?: 2000
        val threads = System.getenv("AI_THREADS")?.toIntOrNull() ?: 2
        val format = AiHarness.Format.parse(System.getenv("AI_FORMAT"))
        if (!out.exists()) out.writeText(header() + "\n")
        val all = AiHarness.mixedLineups(first + lineups, size = format.size)
        for (pair in spec.split(",").map { it.trim() }.filter { it.isNotEmpty() }) {
            val (aName, bName) = pair.split(":")
            val jobs = ArrayList<Callable<List<String>>>()
            for (i in first until first + lineups) {
                val (red, blue) = all[i]
                val coin = if (i % 2 == 0) Team.RED else Team.BLUE
                for (aIsRed in listOf(true, false)) {
                    jobs += Callable {
                        val seed = (50_000L + i) * 2
                        val setup = AiHarness.setup(red, blue, AiLevel.NORMAL, AiLevel.NORMAL, coin, format.length, format.mapId)
                        val players = mapOf(
                            Team.RED to player(if (aIsRed) aName else bName, seed),
                            Team.BLUE to player(if (aIsRed) bName else aName, seed + 1),
                        )
                        val game = "$pair|$i|${if (aIsRed) "a" else "b"}"
                        val labels = mapOf(Team.RED to if (aIsRed) aName else bName, Team.BLUE to if (aIsRed) bName else aName)
                        play(game, setup, players, labels)
                    }
                }
            }
            val pool = Executors.newFixedThreadPool(threads)
            try {
                var n = 0
                for (f in pool.invokeAll(jobs)) {
                    val rows = try {
                        f.get()
                    } catch (e: java.util.concurrent.ExecutionException) {
                        println("HEVAL_DATA game failed: ${e.cause}")
                        emptyList()
                    }
                    synchronized(this) { out.appendText(rows.joinToString("") { "$it\n" }) }
                    n += rows.size
                }
                println("HEVAL_DATA $pair: ${jobs.size} games, $n rows -> $out")
            } finally {
                pool.shutdown()
            }
        }
    }

    /**
     * `AI_HEVAL_LEAVES=x10+pol=1+scope=3+leaf=2:s10` plays mirrored games and appends to `AI_HEVAL_OUT` one
     * row per leaf the hybrid side scores: the decision, the sample and the candidate, the learned net's
     * logit and the [HTerm] features from the deciding team's side (HEVAL.md "Leaf ranking").
     */
    @Test
    fun `record leaves`() {
        val spec = System.getenv("AI_HEVAL_LEAVES") ?: return
        val out = File(System.getenv("AI_HEVAL_OUT") ?: error("AI_HEVAL_OUT"))
        val lineups = System.getenv("AI_H_GAMES")?.toIntOrNull() ?: 4
        val first = System.getenv("AI_H_FIRST")?.toIntOrNull() ?: 2500
        val threads = System.getenv("AI_THREADS")?.toIntOrNull() ?: 2
        val format = AiHarness.Format.parse(System.getenv("AI_FORMAT"))
        if (!out.exists()) {
            out.writeText((listOf("game", "decision", "kind", "sample", "cand", "round", "turn", "net") + HTerm.entries.map { it.label }).joinToString(",") + "\n")
        }
        val all = AiHarness.mixedLineups(first + lineups, size = format.size)
        val (aName, bName) = spec.split(":")
        val profile = HeuristicProfile.of(10)
        val jobs = ArrayList<Callable<Int>>()
        for (i in first until first + lineups) {
            val (red, blue) = all[i]
            val coin = if (i % 2 == 0) Team.RED else Team.BLUE
            for (aIsRed in listOf(true, false)) {
                jobs += Callable {
                    val seed = (60_000L + i) * 2
                    val game = "$spec|$i|${if (aIsRed) "a" else "b"}"
                    val net = NetEvaluator(TestData.engine.data, LearnedNet.ENSEMBLE)
                    val rows = StringBuilder()
                    val hybrid = HybridAi(seed, tuneOf(aName))
                    hybrid.leafObserver = { d, sample, cand, f, team ->
                        val ctx = HContext(f, f.pending, team, profile, finishing = true)
                        val red = if (f.state.winner == null) net.redLogit(f.state) else if (f.state.winner == Team.RED) 20.0 else -20.0
                        val feats = HEval.features(ctx, team)
                        val win = f.state.winner?.let { if (it == team) 1.0 else -1.0 }
                        rows.append(game).append(',').append(d.id).append(',').append(d.kind.name).append(',')
                            .append(sample).append(',').append(cand).append(',').append(f.state.round).append(',')
                            .append(f.state.turn).append(',').append("%.4f".format(if (team == Team.RED) red else -red))
                        for (t in HTerm.entries) {
                            val v = feats?.get(t.ordinal) ?: if (t == HTerm.LIFE) 1000.0 * win!! else 0.0
                            rows.append(',').append("%.4f".format(v))
                        }
                        rows.append('\n')
                    }
                    val setup = AiHarness.setup(red, blue, AiLevel.NORMAL, AiLevel.NORMAL, coin, format.length, format.mapId)
                    val other = player(bName, seed + 1)
                    AiHarness.play(setup, mapOf(Team.RED to if (aIsRed) hybrid else other, Team.BLUE to if (aIsRed) other else hybrid))
                    synchronized(this) { out.appendText(rows.toString()) }
                    rows.count { it == '\n' }
                }
            }
        }
        val pool = Executors.newFixedThreadPool(threads)
        try {
            val n = pool.invokeAll(jobs).sumOf { f ->
                try {
                    f.get()
                } catch (e: java.util.concurrent.ExecutionException) {
                    println("HEVAL_LEAVES game failed: ${e.cause}")
                    0
                }
            }
            println("HEVAL_LEAVES $spec: ${jobs.size} games, $n leaves -> $out")
        } finally {
            pool.shutdown()
        }
    }

    private fun tuneOf(spec: String): Map<String, Double> =
        spec.split("+").drop(1).associate { kv -> kv.substringBefore("=") to kv.substringAfter("=").toDouble() }

    private fun header(): String =
        (listOf("game", "players", "round", "turn", "team", "won", "net") + HTerm.entries.map { it.label }).joinToString(",")

    private fun play(
        game: String,
        setup: dev.quinton.goa.core.game.GameSetup,
        players: Map<Team, AiPlayer>,
        labels: Map<Team, String>,
    ): List<String> {
        val profile = HeuristicProfile.of(10)
        val net = NetEvaluator(TestData.engine.data, LearnedNet.ENSEMBLE)
        val pending = ArrayList<Pair<Team, String>>()
        var lastTurn = -1
        var k = 0L
        val r = AiHarness.play(setup, players) { s: EngineSession, d ->
            val st = s.state
            val turn = HybridAi.turnIndex(st)
            if (turn != lastTurn && st.winner == null) {
                lastTurn = turn
                for (team in Team.entries) {
                    val seen = Searcher.seenCards(s, team)
                    val det = s.determinizeWith(
                        team,
                        HeuristicAi.mix(game.hashCode().toLong(), k++),
                        Determinizer.Options(seen = seen, knowHands = profile.knowHands),
                        quiet = true,
                    )
                    val ctx = HContext(det, det.pending ?: d, team, profile, seen = seen, finishing = true)
                    val f = HEval.features(ctx, team) ?: continue
                    val red = net.redLogit(det.state)
                    val logit = if (team == Team.RED) red else -red
                    val row = listOf(game, labels.getValue(team), st.round, st.turn, team.name, "?", "%.4f".format(logit)) +
                        f.map { "%.4f".format(it) }
                    pending += team to row.joinToString(",")
                }
            }
        }
        val winner = r.winner ?: return emptyList()
        return pending.map { (team, row) -> row.replace(",?,", if (team == winner) ",1," else ",0,") }
    }

    private fun player(spec: String, seed: Long): AiPlayer {
        val parts = spec.split("+")
        val n = parts[0].drop(1).toInt()
        val tune = parts.drop(1).associate { kv -> kv.substringBefore("=") to kv.substringAfter("=").toDouble() }
        return when (spec[0]) {
            'h' -> HeuristicAi(n, seed, tune = tune)
            's' -> AiPlayers.searchDeterministic(n, seed)
            'x' -> HybridPlayers.hybrid(seed, tune, SampleRunner.SEQUENTIAL)
            else -> error("AI_HEVAL_DATA wants hN, sN or x10, not $spec")
        }
    }

    // ---- fit -------------------------------------------------------------------------------------

    private class Row(val game: String, val round: Int, val turn: Int, val won: Boolean, val net: Double, val f: DoubleArray)

    @Test
    fun `fit the terms to outcomes`() {
        val files = System.getenv("AI_HEVAL_FIT")?.split(",") ?: return
        val l2s = System.getenv("AI_HEVAL_L2")?.split(",")?.map { it.trim().toDouble() } ?: listOf(0.002)
        val chosen = System.getenv("AI_HEVAL_TERMS")?.split(",")?.map { HTerm.byLabel.getValue(it.trim()) } ?: HTerm.entries
        val filter = System.getenv("AI_HEVAL_PLAYERS")?.toRegex()
        val rows = files.flatMap { load(File(it.trim()), filter) }
        val hand = HEvalWeights.HAND
        val isTest = { r: Row -> (r.game.hashCode() and 3) == 0 }
        val train = rows.filter { !isTest(it) }
        val test = rows.filter(isTest)
        println("HEVAL_FIT ${rows.size} rows (${rows.map { it.game }.distinct().size} games): train ${train.size}, test ${test.size}")
        val score = { r: Row -> HTerm.entries.sumOf { hand[it] * r.f[it.ordinal] } }
        // The current evaluation: one slope.
        val k = fit(train.map { doubleArrayOf(score(it)) }, train.map { it.won }, DoubleArray(1), DoubleArray(1), 0.0)[0]
        report("current HEval (k = ${"%.5f".format(k)})", test.map { k * score(it) }, test)
        val c = fit(train.map { doubleArrayOf(it.net) }, train.map { it.won }, DoubleArray(1), DoubleArray(1), 0.0)[0]
        report("learned net (slope ${"%.3f".format(c)})", test.map { c * it.net }, test)
        // Each chosen term its own weight; the others stay at k × hand inside a fixed offset.
        val idx = chosen.map { it.ordinal }
        val rest = HTerm.entries.filter { it !in chosen }
        val offset = { r: Row -> rest.sumOf { k * hand[it] * r.f[it.ordinal] } }
        val x = { r: Row -> DoubleArray(idx.size) { r.f[idx[it]] } }
        val sd = DoubleArray(idx.size) { j -> sqrt(train.sumOf { it.f[idx[j]] * it.f[idx[j]] } / train.size).coerceAtLeast(1e-6) }
        val prior = DoubleArray(idx.size) { k * hand.w[idx[it]] }
        var best: Pair<Double, DoubleArray>? = null
        for (l2 in l2s) {
            val fv = fit(train.map(x), train.map { it.won }, prior, sd, l2, train.map(offset))
            val loss = report("fitted terms (l2 $l2)", test.map { r -> offset(r) + x(r).indices.sumOf { fv[it] * x(r)[it] } }, test)
            if (best == null || loss < best.first) best = loss to fv
        }
        val v = best!!.second
        println("HEVAL_FIT   term          hand      fit   fit/hand    rms(f)  rms(w·f)")
        for ((j, i) in idx.withIndex()) {
            val t = HTerm.entries[i]
            val w = v[j] / k
            val ratio = if (hand.w[i] != 0.0) "%8.2f".format(w / hand.w[i]) else "     new"
            println(
                "HEVAL_FIT   ${t.label.padEnd(12)} ${"%7.3f".format(hand.w[i])} ${"%8.3f".format(w)} $ratio" +
                    "  ${"%8.2f".format(sd[j])}  ${"%8.2f".format(abs(w) * sd[j])}",
            )
        }
        println("HEVAL_FIT   weights: " + idx.withIndex().joinToString(", ") { (j, i) -> "${HTerm.entries[i].name} ${"%.3f".format(v[j] / k)}" })
    }

    private fun load(file: File, filter: Regex?): List<Row> {
        val lines = file.readLines()
        val head = lines.first().split(",")
        val col = HTerm.entries.map { head.indexOf(it.label) }
        return lines.drop(1).mapNotNull { line ->
            val p = line.split(",")
            if (p.size < head.size) return@mapNotNull null
            if (filter != null && !filter.containsMatchIn(p[1])) return@mapNotNull null
            Row(
                p[0] + "@" + file.name,
                p[2].toInt(),
                p[3].toInt(),
                p[5] == "1",
                p[6].toDouble(),
                DoubleArray(HTerm.entries.size) { i -> if (col[i] < 0) 0.0 else p[col[i]].toDouble() },
            )
        }
    }

    private fun report(name: String, z: List<Double>, rows: List<Row>): Double {
        val q = z.map { 1 / (1 + exp(-it)) }
        val loss = q.indices.sumOf { val p = q[it].coerceIn(1e-9, 1 - 1e-9); if (rows[it].won) -ln(p) else -ln(1 - p) } / q.size
        val brier = q.indices.sumOf { val e = q[it] - (if (rows[it].won) 1.0 else 0.0); e * e } / q.size
        // Accuracy on positions whose outcome the value calls with any confidence.
        val acc = q.indices.count { (q[it] > 0.5) == rows[it].won }.toDouble() / q.size
        println("HEVAL_FIT $name: held-out log-loss ${"%.4f".format(loss)}, Brier ${"%.4f".format(brier)}, accuracy ${"%.1f".format(acc * 100)}%")
        // Calibration: predicted against observed, by tenths of the prediction.
        val bins = (0 until 10).map { b -> q.indices.filter { (q[it] * 10).toInt().coerceIn(0, 9) == b } }
        println(
            "HEVAL_FIT   calibration " + bins.withIndex().filter { it.value.size >= 30 }.joinToString(" ") { (b, ix) ->
                "${b * 10}-${b * 10 + 10}%:${"%.0f".format(ix.count { rows[it].won } * 100.0 / ix.size)}% (${ix.size})"
            },
        )
        // By phase: the opening, the middle and the late game.
        val phases = listOf("r1" to { r: Row -> r.round == 1 }, "r2-3" to { r: Row -> r.round in 2..3 }, "r4+" to { r: Row -> r.round >= 4 })
        println(
            "HEVAL_FIT   by round " + phases.joinToString(" ") { (label, pick) ->
                val ix = rows.indices.filter { pick(rows[it]) }
                val l = ix.sumOf { val p = q[it].coerceIn(1e-9, 1 - 1e-9); if (rows[it].won) -ln(p) else -ln(1 - p) } / ix.size.coerceAtLeast(1)
                "$label ${"%.4f".format(l)} (${ix.size})"
            },
        )
        return loss
    }

    /**
     * Logistic regression without intercept (the evaluation is zero-sum), Newton's method, with a ridge
     * of [l2] toward [prior] on the standardised coefficients ([sd]) and a fixed [offset] per row.
     */
    @Suppress("LongParameterList")
    private fun fit(
        x: List<DoubleArray>,
        y: List<Boolean>,
        prior: DoubleArray,
        sd: DoubleArray,
        l2: Double,
        offset: List<Double>? = null,
    ): DoubleArray {
        val p = x.first().size
        val w = prior.copyOf()
        repeat(40) {
            val g = DoubleArray(p)
            val h = Array(p) { DoubleArray(p) }
            for ((r, xi) in x.withIndex()) {
                var z = offset?.get(r) ?: 0.0
                for (a in 0 until p) z += w[a] * xi[a]
                val q = 1 / (1 + exp(-z))
                val e = (if (y[r]) 1.0 else 0.0) - q
                val s = q * (1 - q)
                for (a in 0 until p) {
                    g[a] += e * xi[a]
                    val sa = s * xi[a]
                    for (b in 0 until p) h[a][b] += sa * xi[b]
                }
            }
            for (a in 0 until p) {
                // Ridge on the standardised coefficient w·sd toward prior·sd.
                val lam = l2 * x.size * sd[a] * sd[a]
                g[a] -= lam * (w[a] - prior[a])
                h[a][a] += lam + 1e-9
            }
            val step = solve(h, g)
            for (a in 0 until p) w[a] += step[a]
            if (step.indices.all { abs(step[it]) * sd[it] < 1e-6 }) return w
        }
        return w
    }

    private fun solve(a: Array<DoubleArray>, b: DoubleArray): DoubleArray {
        val n = b.size
        val m = Array(n) { i -> a[i].copyOf() + b[i] }
        for (c in 0 until n) {
            val piv = (c until n).maxBy { abs(m[it][c]) }
            val t = m[c]
            m[c] = m[piv]
            m[piv] = t
            if (abs(m[c][c]) < 1e-12) continue
            for (r in 0 until n) {
                if (r == c) continue
                val f = m[r][c] / m[c][c]
                for (q in c..n) m[r][q] -= f * m[c][q]
            }
        }
        return DoubleArray(n) { i -> if (abs(m[i][i]) < 1e-12) 0.0 else m[i][n] / m[i][i] }
    }
}
