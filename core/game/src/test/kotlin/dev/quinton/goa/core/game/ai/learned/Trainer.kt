package dev.quinton.goa.core.game.ai.learned

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
 * the first layer at the end, so the exported net reads raw features.
 */
internal class Trainer(
    private val inputs: Int,
    private val h1: Int,
    private val h2: Int,
    private val seed: Long,
    private val threads: Int = 4,
    /** Starting weights of the linear skip on raw features (the hand evaluation's slope on its score). */
    private val skipInit: Map<Int, Double> = emptyMap(),
) {
    /** Rows of Red's then Blue's features ([inputs] each), training targets and real results (1 = Red won). */
    class Data(val x: FloatArray, val target: FloatArray, val outcome: FloatArray) {
        val n: Int get() = target.size
    }

    class Config(
        val lr: Double = 1e-3,
        val weightDecay: Double = 1e-4,
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
    private var mean = DoubleArray(inputs)
    private var sd = DoubleArray(inputs) { 1.0 }

    private val params: List<DoubleArray> get() = listOf(w1, b1, w2, b2, w3, wl)

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

    /** Standardizes a copy of [x] with the training statistics. */
    private fun standardize(x: FloatArray): FloatArray {
        val out = FloatArray(x.size)
        for (r in 0 until x.size / inputs) {
            val o = r * inputs
            for (i in 0 until inputs) out[o + i] = ((x[o + i] - mean[i]) / sd[i]).toFloat()
        }
        return out
    }

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
        }
        val constant = BooleanArray(inputs) { i ->
            sd[i] == 1.0 && run {
                var v = 0.0
                for (r in 0 until rows) v = max(v, kotlin.math.abs(train.x[r * inputs + i] - mean[i]))
                v < 1e-6
            }
        }
        init(constant)
        val tx = standardize(train.x)
        val vx = standardize(valid.x)
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
                    val (grads, loss) = gradients(tx, train.target, order, b, end, pool)
                    trainLoss += loss
                    step++
                    adam(grads, m, v, step, (end - b).toDouble(), cfg)
                    b = end
                }
                val vl = logLoss(vx, valid.outcome)
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
            val decay = pi == 0 || pi == 2 || pi == 4
            for (k in p.indices) {
                val gk = g[k] / count
                mm[k] = 0.9 * mm[k] + 0.1 * gk
                vv[k] = 0.999 * vv[k] + 0.001 * gk * gk
                p[k] -= cfg.lr * (mm[k] / bc1) / (sqrt(vv[k] / bc2) + 1e-8)
                if (decay) p[k] -= cfg.lr * cfg.weightDecay * p[k]
            }
        }
    }

    /** Summed gradients and loss over `order[from until to]`, split over the threads in a fixed way. */
    private fun gradients(
        x: FloatArray,
        y: FloatArray,
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
                    val zr = forward(x, 2 * r * inputs, ws.red)
                    val zb = forward(x, (2 * r + 1) * inputs, ws.blue)
                    val p = sigmoid(zr - zb)
                    val target = y[r].toDouble()
                    loss += -(target * ln(p.coerceAtLeast(1e-9)) + (1 - target) * ln((1 - p).coerceAtLeast(1e-9)))
                    val d = p - target
                    backward(x, 2 * r * inputs, ws.red, d, g)
                    backward(x, (2 * r + 1) * inputs, ws.blue, -d, g)
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

    /** Log-loss against [y] of the standardized rows [x]. */
    private fun logLoss(x: FloatArray, y: FloatArray): Double {
        val a = Work()
        var l = 0.0
        for (r in y.indices) {
            val p = sigmoid(forward(x, 2 * r * inputs, a.red) - forward(x, (2 * r + 1) * inputs, a.blue))
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
        )
    }

    companion object {
        fun sigmoid(z: Double): Double = 1 / (1 + exp(-z))
    }
}
