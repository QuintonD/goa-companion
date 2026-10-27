package dev.quinton.goa.core.game.ai.learned

import dev.quinton.goa.core.game.ai.Features
import dev.quinton.goa.core.game.ai.LearnedNet
import dev.quinton.goa.core.game.ai.Term
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import org.junit.Test

/**
 * Opt-in: trains the learned evaluation (AI.md "How to retrain").
 *
 * `AI_TRAIN=<games.jsonl or directory>,…` replays the self-play games ([LearnedSelfPlayTest]),
 * records positions ([Learned.positions]; features cached next to the data per schema), splits them
 * by line-up (line-up % 10: 8 = validation for early stopping, 9 = test, the rest training), trains
 * [Trainer] and reports the test log-loss against the real results next to the hand evaluation's
 * (one slope on the tuned score) and a logistic fit of its twelve terms (as `FitTest`). The net is
 * saved as `<AI_NETS>/<AI_TRAIN_NAME>.net`; `AI_TRAIN_WRITE=1` also writes the checked-in
 * `LearnedWeights.kt`. Targets: the result, or with `AI_LAMBDA` < 1 a λ-return bootstrapped from a
 * teacher (`AI_TEACHER=hand` or a net name) over the positions of each game.
 */
class LearnedTrainTest {
    @Test
    fun `train the learned evaluation`() {
        val spec = System.getenv("AI_TRAIN") ?: return
        val name = System.getenv("AI_TRAIN_NAME") ?: "net"
        val rate = System.getenv("AI_RESOLVED")?.toDouble() ?: 0.35
        val lambda = System.getenv("AI_LAMBDA")?.toDouble() ?: 1.0
        val teacher = System.getenv("AI_TEACHER") ?: "hand"
        val h1 = System.getenv("AI_H1")?.toInt() ?: 64
        val h2 = System.getenv("AI_H2")?.toInt() ?: 32
        val seed = System.getenv("AI_SEED")?.toLong() ?: 1L
        val threads = System.getenv("AI_THREADS")?.toInt() ?: 4
        val files = spec.split(",").map(::File).flatMap { f ->
            if (f.isDirectory) f.listFiles { x -> x.name.endsWith(".jsonl") }!!.sorted() else listOf(f)
        }
        val all = files.flatMap { load(it, rate, threads) }
        val train = all.filter { it.lineup % 10 < 8 }
        val valid = all.filter { it.lineup % 10 == 8 }
        val test = all.filter { it.lineup % 10 == 9 }
        println(
            "TRAIN ${all.size} positions from ${all.map { it.game to it.family }.distinct().size} games " +
                "(train ${train.size}, valid ${valid.size}, test ${test.size}), ${Features.SIZE} inputs",
        )
        // The hand evaluation: one slope on the tuned score; and a logistic fit of its twelve terms.
        val k = Logistic.fit(train.map { doubleArrayOf(it.hand.toDouble()) }, train.map { it.redWon }, 0.0)[0]
        val m = Logistic.fit(train.map { p -> DoubleArray(p.terms.size) { p.terms[it].toDouble() } }, train.map { it.redWon }, 1e-4)
        println("TRAIN hand slope k = ${"%.4f".format(k)} (coin scale ${"%.2f".format(1 / k)})")
        val targets = targets(all, lambda, teacher, k)
        val cfg = Trainer.Config(
            lr = System.getenv("AI_LR")?.toDouble() ?: 1e-3,
            weightDecay = System.getenv("AI_WD")?.toDouble() ?: 1e-4,
            batch = System.getenv("AI_BATCH")?.toInt() ?: 512,
            maxEpochs = System.getenv("AI_EPOCHS")?.toInt() ?: 40,
            patience = System.getenv("AI_PATIENCE")?.toInt() ?: 5,
        )
        val t0 = System.nanoTime()
        val raw = Trainer(Features.SIZE, h1, h2, seed, threads).train(data(train, targets), data(valid, targets), cfg)
        println("TRAIN trained in ${"%.0f".format((System.nanoTime() - t0) / 1e9)} s")
        val net = Learned.NetData(
            raw.inputs, raw.h1, raw.h2, raw.w1, raw.b1, raw.w2, raw.b2, raw.w3, raw.b3, 1 / k, Features.SCHEMA_HASH,
        )
        val out = File(Learned.NETS, "$name.net")
        Learned.saveNet(net, out)
        println("TRAIN saved $out")
        report(test, k, m, net.toNet(), "test")
        report(valid, k, m, net.toNet(), "valid")
        val write = System.getenv("AI_TRAIN_WRITE")
        if (write != null) {
            val target = File(if (write == "1") WEIGHTS else write)
            WeightsWriter.write(net, target, "${files.joinToString { it.name }}, λ $lambda, teacher $teacher, seed $seed")
            println("TRAIN wrote $target")
        }
    }

    private fun report(set: List<Pos>, k: Double, m: DoubleArray, net: LearnedNet, label: String) {
        fun line(title: String, ps: List<Pos>) {
            if (ps.isEmpty()) return
            val y = ps.map { it.redWon }
            val hand = Logistic.loss(ps.map { doubleArrayOf(it.hand.toDouble()) }, y, doubleArrayOf(k))
            val fit = Logistic.loss(ps.map { p -> DoubleArray(p.terms.size) { p.terms[it].toDouble() } }, y, m)
            val nn = ps.indices.sumOf { i ->
                val p = Trainer.sigmoid(logit(net, ps[i].x)).coerceIn(1e-9, 1 - 1e-9)
                -(if (y[i]) ln(p) else ln(1 - p))
            } / ps.size
            println(
                "TRAIN $label $title n=${ps.size}: hand ${"%.4f".format(hand)}, " +
                    "12-term fit ${"%.4f".format(fit)}, net ${"%.4f".format(nn)}",
            )
        }
        line("all", set)
        line("turn starts", set.filter { it.boundary })
        for (size in set.map { it.size }.distinct().sorted()) line("${size}v$size", set.filter { it.size == size })
    }

    /** One position with the result and what the targets need. */
    class Pos(
        val game: Int,
        val family: Long,
        val lineup: Int,
        val size: Int,
        val index: Int,
        val boundary: Boolean,
        val redWon: Boolean,
        val hand: Float,
        val terms: FloatArray,
        val x: FloatArray,
    )

    private fun data(ps: List<Pos>, targets: Map<Pos, Float>): Trainer.Data {
        val x = FloatArray(ps.size * 2 * Features.SIZE)
        for ((r, p) in ps.withIndex()) p.x.copyInto(x, r * 2 * Features.SIZE)
        return Trainer.Data(
            x,
            FloatArray(ps.size) { targets.getValue(ps[it]) },
            FloatArray(ps.size) { if (ps[it].redWon) 1f else 0f },
        )
    }

    /** λ-returns per game, bootstrapped from the teacher's P(Red wins) at the next positions; λ = 1 is the result. */
    private fun targets(all: List<Pos>, lambda: Double, teacher: String, k: Double): Map<Pos, Float> {
        val out = HashMap<Pos, Float>(all.size * 2)
        val net = if (teacher == "hand" || lambda >= 1.0) null else Learned.net(teacher)
        for ((_, ps) in all.groupBy { it.family to it.game }) {
            val sorted = ps.sortedBy { it.index }
            var g = if (sorted.first().redWon) 1.0 else 0.0
            val result = g
            for (i in sorted.indices.reversed()) {
                if (i < sorted.lastIndex) {
                    val next = sorted[i + 1]
                    val v = when {
                        lambda >= 1.0 -> result
                        net != null -> Trainer.sigmoid(logit(net, next.x))
                        else -> Trainer.sigmoid(k * next.hand)
                    }
                    g = (1 - lambda) * v + lambda * g
                }
                out[sorted[i]] = g.toFloat()
            }
        }
        return out
    }

    private fun logit(net: LearnedNet, x: FloatArray): Double = (net.forward(x, 0) - net.forward(x, Features.SIZE)).toDouble()

    /** Positions of the games in [file], featurized once per schema and rate (cached next to the file). */
    private fun load(file: File, rate: Double, threads: Int): List<Pos> {
        val cache = File(file.parentFile, "${file.nameWithoutExtension}.${java.lang.Long.toHexString(Features.SCHEMA_HASH)}-$rate.feat")
        if (cache.exists() && cache.lastModified() >= file.lastModified()) return readCache(cache)
        val games = Learned.readGames(listOf(file))
        val pool = Executors.newFixedThreadPool(threads)
        val t0 = System.nanoTime()
        val ps = try {
            pool.invokeAll(
                games.map { r ->
                    Callable {
                        Learned.positions(r, rate).map {
                            Pos(r.game, r.family, r.lineup, r.size, it.index, it.boundary, it.redWon, it.hand, it.terms, it.x)
                        }
                    }
                },
            ).flatMap { it.get() }
        } finally {
            pool.shutdown()
        }
        println("TRAIN featurized ${games.size} games of $file: ${ps.size} positions in ${"%.0f".format((System.nanoTime() - t0) / 1e9)} s")
        writeCache(cache, ps)
        return ps
    }

    private fun writeCache(f: File, ps: List<Pos>) {
        DataOutputStream(f.outputStream().buffered(1 shl 20)).use { o ->
            o.writeInt(ps.size)
            o.writeInt(Features.SIZE)
            for (p in ps) {
                o.writeInt(p.game)
                o.writeLong(p.family)
                o.writeInt(p.lineup)
                o.writeInt(p.size)
                o.writeInt(p.index)
                o.writeBoolean(p.boundary)
                o.writeBoolean(p.redWon)
                o.writeFloat(p.hand)
                for (t in p.terms) o.writeFloat(t)
                for (v in p.x) o.writeFloat(v)
            }
        }
    }

    private fun readCache(f: File): List<Pos> = DataInputStream(f.inputStream().buffered(1 shl 20)).use { i ->
        val n = i.readInt()
        check(i.readInt() == Features.SIZE)
        List(n) {
            Pos(
                i.readInt(), i.readLong(), i.readInt(), i.readInt(), i.readInt(), i.readBoolean(), i.readBoolean(),
                i.readFloat(), FloatArray(Term.entries.size) { i.readFloat() }, FloatArray(2 * Features.SIZE) { i.readFloat() },
            )
        }
    }

    companion object {
        const val WEIGHTS = "src/main/kotlin/dev/quinton/goa/core/game/ai/LearnedWeights.kt"
    }
}

/** Logistic regression without intercept (the evaluations are zero-sum), Newton's method (as `FitTest`). */
internal object Logistic {
    fun fit(x: List<DoubleArray>, y: List<Boolean>, l2: Double): DoubleArray {
        val p = x.first().size
        val w = DoubleArray(p)
        repeat(30) {
            val g = DoubleArray(p)
            val h = Array(p) { DoubleArray(p) }
            for ((r, xi) in x.withIndex()) {
                val z = xi.indices.sumOf { w[it] * xi[it] }
                val q = 1 / (1 + exp(-z))
                val e = (if (y[r]) 1.0 else 0.0) - q
                for (a in 0 until p) {
                    g[a] += e * xi[a]
                    for (b in 0 until p) h[a][b] += q * (1 - q) * xi[a] * xi[b]
                }
            }
            for (a in 0 until p) {
                g[a] -= l2 * x.size * w[a]
                h[a][a] += l2 * x.size + 1e-9
            }
            val step = solve(h, g)
            for (a in 0 until p) w[a] += step[a]
            if (step.all { abs(it) < 1e-7 }) return w
        }
        return w
    }

    fun loss(x: List<DoubleArray>, y: List<Boolean>, w: DoubleArray): Double = x.indices.sumOf { r ->
        val z = x[r].indices.sumOf { w[it] * x[r][it] }
        val q = (1 / (1 + exp(-z))).coerceIn(1e-9, 1 - 1e-9)
        -(if (y[r]) ln(q) else ln(1 - q))
    } / x.size

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
                for (k in c..n) m[r][k] -= f * m[c][k]
            }
        }
        return DoubleArray(n) { i -> if (abs(m[i][i]) < 1e-12) 0.0 else m[i][n] / m[i][i] }
    }
}
