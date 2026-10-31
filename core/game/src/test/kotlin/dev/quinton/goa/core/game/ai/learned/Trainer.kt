package dev.quinton.goa.core.game.ai.learned

import dev.quinton.goa.core.game.ai.Features
import dev.quinton.goa.core.game.ai.LearnedNet
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Trains the learned evaluation's net (AI.md "Learned evaluation"): an MLP N, inputs → [h1] → [h2] →
 * 1 with ReLU, applied to both teams' features, P(Red wins) = σ(N(red) − N(blue)); cross-entropy
 * against the targets, AdamW, mini-batches, early stopping on the validation set's log-loss against
 * the real results. Seeded and reproducible (the batch is split over a fixed number of threads and
 * the gradients summed in order). Inputs are standardized for training and the scaling folded into
 * the first layer at the end, so the exported net reads raw features. On top of the MLP, N has the
 * per-hero corrections and the card table ([dev.quinton.goa.core.game.ai.LearnedNet], ADR-049): they
 * start at zero and decay towards it, so a rare situation does not overfit.
 */
internal class Trainer(
    private val inputs: Int,
    private val h1: Int,
    private val h2: Int,
    private val seed: Long,
    private val threads: Int = 4,
    /** Starting weights of the linear skip on raw features (the hand evaluation's slope on its score). */
    private val skipInit: Map<Int, Double> = emptyMap(),
    /** Heroes and cards of the correction tables (0 = the plain net). */
    private val heroes: Int = 0,
    private val cards: Int = 0,
    /** Inputs from this index up are mostly zero (the pieces of one hero): a floor under their scale. */
    private val sparseFrom: Int = inputs,
) {
    /** One position for the tables: the heroes' table rows (Red's first, [red] of them), their numbers, the cards. */
    class Extras(
        val heroes: ByteArray,
        val red: Int,
        val v: FloatArray,
        val redCards: ShortArray,
        val blueCards: ShortArray,
    )

    /** Rows of Red's then Blue's features ([inputs] each), training targets and real results (1 = Red won). */
    class Data(val x: FloatArray, val target: FloatArray, val outcome: FloatArray, val extras: List<Extras>) {
        val n: Int get() = target.size
    }

    class Config(
        val lr: Double = 1e-3,
        val weightDecay: Double = 1e-4,
        /** AdamW's decay on the correction tables, towards zero. */
        val tableDecay: Double = 1.0,
        val batch: Int = 512,
        val maxEpochs: Int = 40,
        val patience: Int = 5,
        val log: (String) -> Unit = ::println,
    )

    private val w1 = DoubleArray(inputs * h1)
    private val b1 = DoubleArray(h1)
    private val w2 = DoubleArray(h2 * h1)
    private val b2 = DoubleArray(h2)
    private val w3 = DoubleArray(h2)

    /** A linear skip from the inputs to the output: N(x) = MLP(x) + wl · x (wide and deep). */
    private val wl = DoubleArray(inputs)
    private val wh = DoubleArray(heroes * LearnedNet.HERO_COLS)
    private val wc = DoubleArray(cards * LearnedNet.CARD_PLACES)
    private var mean = DoubleArray(inputs)
    private var sd = DoubleArray(inputs) { 1.0 }

    private val params: List<DoubleArray> get() = listOf(w1, b1, w2, b2, w3, wl, wh, wc)

    private fun init(constant: BooleanArray) {
        val rng = Random(seed)
        fun gauss() =
            sqrt(-2 * ln(rng.nextDouble().coerceAtLeast(1e-12))) * kotlin.math.cos(2 * Math.PI * rng.nextDouble())
        val s1 = sqrt(2.0 / inputs)
        for (i in 0 until inputs) for (j in 0 until h1) w1[i * h1 + j] = if (constant[i]) 0.0 else gauss() * s1
        val s2 = sqrt(2.0 / h1)
        for (k in w2.indices) w2[k] = gauss() * s2
        val s3 = sqrt(1.0 / h2)
        for (k in w3.indices) w3[k] = gauss() * s3 * 0.5
        for ((i, c) in skipInit) wl[i] = c * sd[i]
        b1.fill(0.01)
        b2.fill(0.01)
    }

    /** Standardizes [x] in place with the training statistics. */
    private fun standardize(x: FloatArray) {
        for (r in 0 until x.size / inputs) {
            val o = r * inputs
            for (i in 0 until inputs) x[o + i] = ((x[o + i] - mean[i]) / sd[i]).toFloat()
        }
    }

    /** Trains on [train] and [valid], whose inputs it overwrites. */
    fun train(train: Data, valid: Data, cfg: Config = Config()): Learned.NetData {
        // Statistics over both points of view of every training row.
        val rows = train.x.size / inputs
        for (i in 0 until inputs) {
            var s = 0.0
            var ss = 0.0
            for (r in 0 until rows) {
                val v = train.x[r * inputs + i].toDouble()
                s += v
                ss += v * v
            }
            mean[i] = s / rows
            val variance = ss / rows - mean[i] * mean[i]
            sd[i] = if (variance < 1e-10) 1.0 else sqrt(variance)
            if (i >= sparseFrom) sd[i] = max(sd[i], SPARSE_SCALE)
        }
        val constant = BooleanArray(inputs) { i ->
            sd[i] == 1.0 && run {
                var v = 0.0
                for (r in 0 until rows) v = max(v, kotlin.math.abs(train.x[r * inputs + i] - mean[i]))
                v < 1e-6
            }
        }
        init(constant)
        standardize(train.x)
        standardize(valid.x)
        val m = params.map { DoubleArray(it.size) }
        val v = params.map { DoubleArray(it.size) }
        var step = 0
        var best = Double.MAX_VALUE
        var bestParams = params.map { it.copyOf() }
        var bestEpoch = 0
        val order = IntArray(train.n) { it }
        val rng = Random(seed + 1)
        val pool = Executors.newFixedThreadPool(threads)
        try {
            for (epoch in 1..cfg.maxEpochs) {
                for (i in order.indices.reversed()) {
                    val j = rng.nextInt(i + 1)
                    val t = order[i]
                    order[i] = order[j]
                    order[j] = t
                }
                var trainLoss = 0.0
                var b = 0
                while (b < train.n) {
                    val end = minOf(train.n, b + cfg.batch)
                    val (grads, loss) = gradients(train, order, b, end, pool)
                    trainLoss += loss
                    step++
                    adam(grads, m, v, step, (end - b).toDouble(), cfg)
                    b = end
                }
                val vl = logLoss(valid)
                cfg.log("TRAIN epoch $epoch train ${"%.4f".format(trainLoss / train.n)} valid ${"%.4f".format(vl)}")
                if (vl < best - 1e-5) {
                    best = vl
                    bestParams = params.map { it.copyOf() }
                    bestEpoch = epoch
                } else if (epoch - bestEpoch >= cfg.patience) {
                    break
                }
            }
        } finally {
            pool.shutdown()
        }
        for ((p, q) in params.zip(bestParams)) q.copyInto(p)
        cfg.log("TRAIN best epoch $bestEpoch valid ${"%.4f".format(best)}")
        return export()
    }

    /** One AdamW step with the summed gradients of [count] rows (weight decay on the weights, not the biases). */
    private fun adam(
        grads: List<DoubleArray>,
        m: List<DoubleArray>,
        v: List<DoubleArray>,
        step: Int,
        count: Double,
        cfg: Config,
    ) {
        val bc1 = 1 - Math.pow(0.9, step.toDouble())
        val bc2 = 1 - Math.pow(0.999, step.toDouble())
        for ((pi, p) in params.withIndex()) {
            val g = grads[pi]
            val mm = m[pi]
            val vv = v[pi]
            val decay = when (pi) {
                0, 2, 4 -> cfg.weightDecay
                6, 7 -> cfg.tableDecay
                else -> 0.0
            }
            for (k in p.indices) {
                val gk = g[k] / count
                mm[k] = 0.9 * mm[k] + 0.1 * gk
                vv[k] = 0.999 * vv[k] + 0.001 * gk * gk
                p[k] -= cfg.lr * (mm[k] / bc1) / (sqrt(vv[k] / bc2) + 1e-8)
                p[k] -= cfg.lr * decay * p[k]
            }
        }
    }

    /** Summed gradients and loss over `order[from until to]`, split over the threads in a fixed way. */
    private fun gradients(
        data: Data,
        order: IntArray,
        from: Int,
        to: Int,
        pool: java.util.concurrent.ExecutorService,
    ): Pair<List<DoubleArray>, Double> {
        val chunk = (to - from + threads - 1) / threads
        val jobs = (0 until threads).map { t ->
            Callable {
                val g = params.map { DoubleArray(it.size) }
                var loss = 0.0
                val ws = Work()
                val a = from + t * chunk
                val e = minOf(to, a + chunk)
                for (q in a until e) {
                    val r = order[q]
                    val e = data.extras[r]
                    val x = data.x
                    val zr = forward(x, 2 * r * inputs, ws.red) + tables(e, true)
                    val zb = forward(x, (2 * r + 1) * inputs, ws.blue) + tables(e, false)
                    val p = sigmoid(zr - zb)
                    val target = data.target[r].toDouble()
                    loss += -(target * ln(p.coerceAtLeast(1e-9)) + (1 - target) * ln((1 - p).coerceAtLeast(1e-9)))
                    val d = p - target
                    backward(x, 2 * r * inputs, ws.red, d, g)
                    backward(x, (2 * r + 1) * inputs, ws.blue, -d, g)
                    tablesBack(e, true, d, g)
                    tablesBack(e, false, -d, g)
                }
                g to loss
            }
        }
        val results = pool.invokeAll(jobs).map { it.get() }
        val sum = params.map { DoubleArray(it.size) }
        var loss = 0.0
        for ((g, l) in results) {
            for ((s, gg) in sum.zip(g)) for (k in s.indices) s[k] += gg[k]
            loss += l
        }
        return sum to loss
    }

    private inner class Act {
        val z1 = DoubleArray(h1)
        val z2 = DoubleArray(h2)
    }

    private inner class Work {
        val red = Act()
        val blue = Act()
    }

    /** What one team's heroes and cards add ([dev.quinton.goa.core.game.ai.LearnedNet.corrections]). */
    private fun tables(e: Extras, red: Boolean): Double {
        if (heroes == 0) return 0.0
        var out = 0.0
        val size = Features.HERO.size
        val cols = LearnedNet.HERO_COLS
        for (h in if (red) 0 until e.red else e.red until e.heroes.size) {
            val row = e.heroes[h] * cols
            out += wh[row + size]
            for (k in 0 until size) out += wh[row + k] * e.v[h * size + k]
        }
        for (c in if (red) e.redCards else e.blueCards) out += wc[c.toInt()]
        return out
    }

    private fun tablesBack(e: Extras, red: Boolean, d: Double, g: List<DoubleArray>) {
        if (heroes == 0) return
        val gh = g[6]
        val gc = g[7]
        val size = Features.HERO.size
        val cols = LearnedNet.HERO_COLS
        for (h in if (red) 0 until e.red else e.red until e.heroes.size) {
            val row = e.heroes[h] * cols
            gh[row + size] += d
            for (k in 0 until size) gh[row + k] += d * e.v[h * size + k]
        }
        for (c in if (red) e.redCards else e.blueCards) gc[c.toInt()] += d
    }

    private fun forward(x: FloatArray, off: Int, a: Act): Double {
        b1.copyInto(a.z1)
        for (i in 0 until inputs) {
            val xi = x[off + i].toDouble()
            if (xi == 0.0) continue
            val row = i * h1
            for (j in 0 until h1) a.z1[j] += xi * w1[row + j]
        }
        var out = 0.0
        for (k in 0 until h2) {
            var s = b2[k]
            val row = k * h1
            for (j in 0 until h1) if (a.z1[j] > 0) s += w2[row + j] * a.z1[j]
            a.z2[k] = s
            if (s > 0) out += w3[k] * s
        }
        for (i in 0 until inputs) out += wl[i] * x[off + i]
        return out
    }

    private fun backward(x: FloatArray, off: Int, a: Act, d: Double, g: List<DoubleArray>) {
        val gw1 = g[0]
        val gb1 = g[1]
        val gw2 = g[2]
        val gb2 = g[3]
        val gw3 = g[4]
        val gwl = g[5]
        for (i in 0 until inputs) gwl[i] += d * x[off + i]
        val d1 = DoubleArray(h1)
        for (k in 0 until h2) {
            if (a.z2[k] <= 0) continue
            gw3[k] += d * a.z2[k]
            val d2 = d * w3[k]
            gb2[k] += d2
            val row = k * h1
            for (j in 0 until h1) {
                if (a.z1[j] <= 0) continue
                gw2[row + j] += d2 * a.z1[j]
                d1[j] += d2 * w2[row + j]
            }
        }
        for (j in 0 until h1) gb1[j] += d1[j]
        for (i in 0 until inputs) {
            val xi = x[off + i].toDouble()
            if (xi == 0.0) continue
            val row = i * h1
            for (j in 0 until h1) gw1[row + j] += d1[j] * xi
        }
    }

    /** Log-loss of the standardized rows of [d] against the real results. */
    private fun logLoss(d: Data): Double {
        val a = Work()
        var l = 0.0
        val x = d.x
        val y = d.outcome
        for (r in y.indices) {
            val e = d.extras[r]
            val p = sigmoid(
                forward(x, 2 * r * inputs, a.red) + tables(e, true) -
                    forward(x, (2 * r + 1) * inputs, a.blue) - tables(e, false),
            )
            l += -(y[r] * ln(p.coerceAtLeast(1e-9)) + (1 - y[r]) * ln((1 - p).coerceAtLeast(1e-9)))
        }
        return l / y.size
    }

    /** The trained net with the standardization folded into the first layer (reads raw features). */
    private fun export(): Learned.NetData {
        val fw1 = FloatArray(inputs * h1)
        val fb1 = FloatArray(h1)
        for (j in 0 until h1) {
            var b = b1[j]
            for (i in 0 until inputs) {
                val w = w1[i * h1 + j] / sd[i]
                fw1[i * h1 + j] = w.toFloat()
                b -= w * mean[i]
            }
            fb1[j] = b.toFloat()
        }
        return Learned.NetData(
            inputs, h1, h2, fw1, fb1,
            FloatArray(w2.size) { w2[it].toFloat() },
            FloatArray(h2) { b2[it].toFloat() },
            FloatArray(h2) { w3[it].toFloat() },
            FloatArray(inputs) { (wl[it] / sd[it]).toFloat() },
            0f, 1.0, 0L,
            Learned.HEROES.takeIf { heroes > 0 }.orEmpty(),
            FloatArray(wh.size) { wh[it].toFloat() },
            Learned.CARDS.takeIf { cards > 0 }.orEmpty(),
            FloatArray(wc.size) { wc[it].toFloat() },
        )
    }

    companion object {
        /** The floor under the scale of the mostly-zero inputs: a rare piece must not shout. */
        private const val SPARSE_SCALE = 0.25

        fun sigmoid(z: Double): Double = 1 / (1 + exp(-z))
    }
}
