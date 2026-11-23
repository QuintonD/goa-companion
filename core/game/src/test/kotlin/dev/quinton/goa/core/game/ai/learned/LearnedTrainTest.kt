package dev.quinton.goa.core.game.ai.learned

import dev.quinton.goa.core.game.ai.Features
import dev.quinton.goa.core.game.ai.LearnedNet
import dev.quinton.goa.core.game.ai.Term
import dev.quinton.goa.core.model.match.Team
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
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
 * `LearnedWeights.kt`. Targets: λ-returns over the positions of each game (`AI_LAMBDA`, default 0.7;
 * 1 = the result alone) bootstrapped from a teacher (`AI_TEACHER=hand`, the default, or a net name).
 * `AI_TABLES=0` trains without the per-hero corrections and the card table, `AI_BOARD=0` with the board
 * inputs zeroed, `AI_TABLE_DECAY` sets how hard the tables decay; `AI_CACHE` is where features are cached.
 */
class LearnedTrainTest {
    /** `AI_WRITE_NET=<name>[:<target>]` writes a saved net as the checked-in weights, without training. */
    @Test
    fun `write a saved net as the weights`() {
        val spec = System.getenv("AI_WRITE_NET")?.split(":", limit = 2) ?: return
        val target = File(spec.getOrNull(1) ?: WEIGHTS)
        val net = Learned.loadNetData(File(Learned.NETS, "${spec[0]}.net"))
        WeightsWriter.write(net, target, System.getenv("AI_WRITE_NOTE") ?: spec[0])
        println("TRAIN wrote $target")
    }

    /**
     * `AI_SPREAD=<games>,…` with `AI_SPREAD_NETS=a,b,c` (ADR-060): on the test split, each net's log-loss
     * against the results, the mean of their logits' log-loss, and how far the nets' scores lie apart
     * (standard deviation across the nets, in coin-equivalents): the scale an `lcb` ensemble penalizes.
     */
    @Test
    fun `how far seed nets disagree`() {
        val spec = System.getenv("AI_SPREAD") ?: return
        val names = System.getenv("AI_SPREAD_NETS")!!.split(",")
        val nets = names.map { Learned.net(it) }
        val threads = System.getenv("AI_THREADS")?.toInt() ?: 3
        val test = spec.split(",").map(::File).flatMap { load(it, 0.35, threads) }.filter { it.lineup % 10 == 9 }
        val y = test.map { it.redWon }
        fun loss(z: (Pos) -> Double) = test.indices.sumOf { i ->
            val p = Trainer.sigmoid(z(test[i])).coerceIn(1e-9, 1 - 1e-9)
            -(if (y[i]) ln(p) else ln(1 - p))
        } / test.size
        for ((n, net) in names.zip(nets)) println("SPREAD $n log-loss ${"%.4f".format(loss { logit(net, it) })}")
        println(
            "SPREAD mean of logits log-loss ${"%.4f".format(
                loss { p ->
                    nets.sumOf { logit(it, p) } / nets.size
                },
            )}",
        )
        val sds = test.map { p ->
            val v = nets.map { logit(it, p) * it.coinScale }
            val m = v.average()
            kotlin.math.sqrt(v.sumOf { (it - m) * (it - m) } / v.size)
        }.sorted()
        println(
            "SPREAD sd across nets (coin-equivalents) over ${test.size}: mean ${"%.2f".format(sds.average())}, " +
                "median ${"%.2f".format(sds[sds.size / 2])}, p90 ${"%.2f".format(sds[sds.size * 9 / 10])}",
        )
    }

    /** One run's settings over the environment ([AI_TRAIN_RUNS]). */
    private var overrides: Map<String, String> = emptyMap()

    private fun env(k: String): String? = overrides[k] ?: System.getenv(k)

    /**
     * `AI_TRAIN_RUNS=name1:AI_SEED=2;AI_LAMBDA=0.8|name2:…` trains several nets in one JVM (one run slot on a
     * shared machine, Wave 3 Track E), each with its settings over the environment; the features are cached once.
     */
    @Test
    fun `train the learned evaluation`() {
        val runs = System.getenv("AI_TRAIN_RUNS") ?: return trainOnce()
        for (run in runs.split("|").filter { it.isNotBlank() }) {
            val (name, kv) = run.split(":", limit = 2)
            overrides = kv.split(";").filter { it.isNotBlank() }
                .associate { it.substringBefore("=") to it.substringAfter("=") } + ("AI_TRAIN_NAME" to name)
            println("TRAIN run $name: $overrides")
            trainOnce()
        }
        overrides = emptyMap()
    }

    private fun trainOnce() {
        val spec = env("AI_TRAIN") ?: return
        val name = env("AI_TRAIN_NAME") ?: "net"
        val rate = env("AI_RESOLVED")?.toDouble() ?: 0.35
        val lambda = env("AI_LAMBDA")?.toDouble() ?: 0.7
        val teacher = env("AI_TEACHER") ?: "hand"
        val h1 = env("AI_H1")?.toInt() ?: 64
        val h2 = env("AI_H2")?.toInt() ?: 32
        val seed = env("AI_SEED")?.toLong() ?: 1L
        val threads = env("AI_THREADS")?.toInt() ?: 4
        val files = spec.split(",").map(::File).flatMap { f ->
            if (f.isDirectory) f.listFiles { x -> x.name.endsWith(".jsonl") }!!.sorted() else listOf(f)
        }
        // `AI_AUX=kills:0.3,push:0.3,life:0.3`: auxiliary heads and their loss weights (ADR-060, [AuxTargets]).
        val auxWeight = DoubleArray(AuxTargets.COUNT)
        env("AI_AUX")?.split(",")?.filter { it.isNotBlank() }?.forEach {
            val (k, w) = it.split(":")
            auxWeight[AuxTargets.NAMES.indexOf(k).also { i -> require(i >= 0) { "Unknown aux target $k" } }] =
                w.toDouble()
        }
        val useAux = auxWeight.any { it != 0.0 }
        // `AI_SV=<values.tsv>,…` ([SearchValueTest]): the search's backed-up values of the positions it searched.
        val sv = env("AI_SV")?.split(",")?.filter { it.isNotBlank() }?.let { readSearchValues(it.map(::File)) }
        val all = files.withIndex().flatMap { (src, f) ->
            val ps = load(f, rate, threads)
            for (p in ps) p.src = src
            if (sv != null) {
                var n = 0
                for (p in ps) {
                    sv["${f.nameWithoutExtension}\t${p.game}\t${p.index}"]?.let {
                        p.sv = it
                        n++
                    }
                }
                println("TRAIN search values for $n of ${ps.size} positions of ${f.name}")
            }
            if (useAux) {
                val dir = File(env("AI_AUX_CACHE") ?: "D:/.goa-qa/v14/a/cache")
                val t = AuxTargets.load(f, rate, threads, dir)
                var missing = 0
                for (p in ps) {
                    p.aux = t[AuxTargets.key(p.family, p.game, p.index)] ?: run {
                        missing++
                        null
                    }
                }
                println("TRAIN aux targets for ${ps.size - missing} of ${ps.size} positions of ${f.name}")
            }
            ps
        }
        if (env("AI_BOARD") == "0") {
            for (p in all) {
                p.x?.fill(0f, Features.BOARD_AT, Features.SIZE)
                p.x?.fill(0f, Features.SIZE + Features.BOARD_AT, 2 * Features.SIZE)
            }
        }
        val svOnly = env("AI_SV_ONLY") == "1"
        val train = all.filter { it.lineup % 10 < 8 && (!svOnly || !it.sv.isNaN()) }
        val valid = all.filter { it.lineup % 10 == 8 }
        val test = all.filter { it.lineup % 10 == 9 }
        println(
            "TRAIN ${all.size} positions from ${all.map { it.game to it.family }.distinct().size} games " +
                "(train ${train.size}, valid ${valid.size}, test ${test.size}), ${Features.SIZE} inputs",
        )
        // The hand evaluation: one slope on the tuned score; and a logistic fit of its twelve terms.
        val k = Logistic.fit(train.map { doubleArrayOf(it.hand.toDouble()) }, train.map { it.redWon }, 0.0)[0]
        val fitOn = train.filterIndexed { i, _ -> i % max(1, train.size / FIT_ROWS) == 0 }
        val m = Logistic.fit(
            fitOn.map { p ->
                DoubleArray(p.terms.size) { p.terms[it].toDouble() }
            },
            fitOn.map { it.redWon },
            1e-4,
        )
        println("TRAIN hand slope k = ${"%.4f".format(k)} (coin scale ${"%.2f".format(1 / k)})")
        val targets = targets(all, lambda, teacher, k)
        // `AI_SV_LAMBDA=λ`: a position with a search value gets λ · result + (1 − λ) · the search's P(Red wins);
        // `AI_SV_ONLY=1` trains on those positions alone (the rest stay for validation and test).
        val svLambda = env("AI_SV_LAMBDA")?.toDouble()
        if (svLambda != null) {
            for (p in all) {
                if (p.sv.isNaN()) continue
                targets[p] = (svLambda * (if (p.redWon) 1.0 else 0.0) + (1 - svLambda) * p.sv).toFloat()
            }
        }
        val cfg = Trainer.Config(
            lr = env("AI_LR")?.toDouble() ?: 3e-4,
            weightDecay = env("AI_WD")?.toDouble() ?: 1e-3,
            batch = env("AI_BATCH")?.toInt() ?: 512,
            maxEpochs = env("AI_EPOCHS")?.toInt() ?: 40,
            patience = env("AI_PATIENCE")?.toInt() ?: 5,
            tableDecay = env("AI_TABLE_DECAY")?.toDouble() ?: 1.0,
        )
        val t0 = System.nanoTime()
        // The linear skip starts as the hand evaluation (its tuned score is a feature, ±score / 30 per side).
        val skip = if (env("AI_SKIP") ==
            "0"
        ) {
            emptyMap()
        } else {
            mapOf(Features.NAMES.indexOf("tunedScore") to k * 15)
        }
        val tabled = env("AI_TABLES") != "0"
        val raw = Trainer(
            Features.SIZE,
            h1,
            h2,
            seed,
            threads,
            skip,
            if (tabled) Learned.HEROES.size else 0,
            if (tabled) Learned.CARDS.size else 0,
            Features.BOARD_AT,
            if (useAux) auxWeight else DoubleArray(0),
            // AI_INIT=<net>: fine-tune a saved or checked-in net (`cur1`) instead of starting at random (Track E).
            initData(env("AI_INIT")),
        ).train(data(train, targets, release = true), data(valid, targets, release = false), cfg)
        println("TRAIN trained in ${"%.0f".format((System.nanoTime() - t0) / 1e9)} s")
        val net = Learned.NetData(
            raw.inputs, raw.h1, raw.h2, raw.w1, raw.b1, raw.w2, raw.b2, raw.w3, raw.wl, raw.b3, 1 / k,
            Features.SCHEMA_HASH, raw.heroes, raw.wh, raw.cards, raw.wc,
        )
        val out = File(Learned.NETS, "$name.net")
        Learned.saveNet(net, out)
        println("TRAIN saved $out")
        reports(test, valid, k, m, net.toNet())
        val write = env("AI_TRAIN_WRITE")
        if (write != null) {
            val target = File(if (write == "1") WEIGHTS else write)
            WeightsWriter.write(
                net,
                target,
                "${files.joinToString {
                    it.name
                }}, λ $lambda, teacher $teacher, seed $seed",
            )
            println("TRAIN wrote $target")
        }
    }

    /** A saved net's data, or a checked-in one's (`cur1`…) through its weights object; null: none. */
    private fun initData(name: String?): Learned.NetData? {
        if (name == null) return null
        LeafNets.CHECKED_IN_DATA[name]?.let { return it() }
        return Learned.loadNetData(File(Learned.NETS, "$name.net"))
    }

    /** The test and validation reports, and with `AI_COMPARE=cur1,cur2` other nets on the same test split (Track E). */
    private fun reports(test: List<Pos>, valid: List<Pos>, k: Double, m: DoubleArray, net: LearnedNet) {
        report(test, k, m, net, "test")
        report(valid, k, m, net, "valid")
        for (other in env("AI_COMPARE")?.split(",").orEmpty().filter { it.isNotBlank() }) {
            report(test, k, m, Learned.net(other), "test[$other]")
        }
    }

    private fun report(set: List<Pos>, k: Double, m: DoubleArray, net: LearnedNet, label: String) {
        fun line(title: String, ps: List<Pos>) {
            if (ps.isEmpty()) return
            val y = ps.map { it.redWon }
            val hand = Logistic.loss(ps.map { doubleArrayOf(it.hand.toDouble()) }, y, doubleArrayOf(k))
            val fit = Logistic.loss(ps.map { p -> DoubleArray(p.terms.size) { p.terms[it].toDouble() } }, y, m)
            val nn = ps.indices.sumOf { i ->
                val p = Trainer.sigmoid(logit(net, ps[i])).coerceIn(1e-9, 1 - 1e-9)
                -(if (y[i]) ln(p) else ln(1 - p))
            } / ps.size
            println(
                "TRAIN $label $title n=${ps.size}: hand ${"%.4f".format(hand)}, " +
                    "12-term fit ${"%.4f".format(fit)}, net ${"%.4f".format(nn)}",
            )
        }
        line("all", set)
        line("turn starts", set.filter { it.boundary })
        // Track E2: how far the net lies from the search's own values where they exist (RMS, in probability).
        val searched = set.filter { !it.sv.isNaN() }
        if (searched.isNotEmpty()) {
            val rms = kotlin.math.sqrt(
                searched.sumOf { p ->
                    val d = Trainer.sigmoid(logit(net, p)) - p.sv
                    d * d
                } / searched.size,
            )
            line("searched", searched)
            println("TRAIN $label searched n=${searched.size}: net vs search value RMS ${"%.4f".format(rms)}")
        }
        for (size in set.map { it.size }.distinct().sorted()) line("${size}v$size", set.filter { it.size == size })
    }

    /** One position with the result and what the targets need. */
    internal class Pos(
        val game: Int,
        val family: Long,
        val lineup: Int,
        val size: Int,
        val index: Int,
        val boundary: Boolean,
        val redWon: Boolean,
        val hand: Float,
        val terms: FloatArray,
        var x: FloatArray?,
        val extras: Trainer.Extras,
    ) {
        /** The auxiliary targets ([AuxTargets]), when asked for. */
        var aux: FloatArray? = null

        /** Which input file (games of two files can share a number). */
        var src: Int = 0

        /** The search's P(Red wins) at this position ([SearchValueTest]), NaN: not searched. */
        var sv: Float = Float.NaN
    }

    /**
     * The search values of [files] as P(Red wins) per `file\tgame\tindex`: each team's value of the card it
     * would play, in coin-equivalents from its view, through σ(v / `AI_SV_SCALE`) (default: the six-net leaf's
     * mean coin scale), Red's and Blue's averaged when both were searched.
     */
    private fun readSearchValues(files: List<File>): Map<String, Float> {
        val scale = env("AI_SV_SCALE")?.toDouble() ?: SV_SCALE
        val col = env("AI_SV_COL")?.toInt() ?: 4
        val sums = HashMap<String, DoubleArray>()
        for (f in files) {
            for (line in f.readLines()) {
                val t = line.split("\t")
                if (t.size < 8) continue
                val v = t[col].toDouble()
                if (v.isNaN()) continue
                val p = Trainer.sigmoid((v / scale).coerceIn(-SV_CLAMP, SV_CLAMP))
                val red = if (t[3] == Team.RED.name) p else 1 - p
                val a = sums.getOrPut("${t[0]}\t${t[1]}\t${t[2]}") { DoubleArray(2) }
                a[0] += red
                a[1] += 1.0
            }
        }
        println("TRAIN search values: ${sums.size} positions from ${files.joinToString { it.name }}")
        return sums.mapValues { (_, a) -> (a[0] / a[1]).toFloat() }
    }

    /** The rows of [ps]; with [release] the positions give up their inputs (the big training set). */
    private fun data(ps: List<Pos>, targets: Map<Pos, Float>, release: Boolean): Trainer.Data {
        // The trainer standardizes its rows in place: the training set hands its arrays over, the others copy.
        val x = Array(ps.size) { r ->
            val p = ps[r]
            if (release) p.x!!.also { p.x = null } else p.x!!.copyOf()
        }
        return Trainer.Data(
            x,
            FloatArray(ps.size) { targets.getValue(ps[it]) },
            FloatArray(ps.size) { if (ps[it].redWon) 1f else 0f },
            ps.map { it.extras },
            if (ps.any { it.aux != null }) {
                FloatArray(ps.size * AuxTargets.COUNT) { i ->
                    ps[i / AuxTargets.COUNT].aux?.get(i % AuxTargets.COUNT)
                        ?: Float.NaN
                }
            } else {
                null
            },
        )
    }

    /** λ-returns per game, bootstrapped from the teacher's P(Red wins) at the next positions; λ = 1 is the result. */
    private fun targets(all: List<Pos>, lambda: Double, teacher: String, k: Double): MutableMap<Pos, Float> {
        val out = HashMap<Pos, Float>(all.size * 2)
        // A net, or the mean logit of several (`avg(cur1,cur2,cur3)`: the checked-in ensemble, Wave 3 Track E).
        val nets = when {
            teacher == "hand" || lambda >= 1.0 -> null

            teacher.startsWith("avg(") -> teacher.removePrefix("avg(").removeSuffix(")").split(",").map {
                Learned.net(it.trim())
            }

            else -> listOf(Learned.net(teacher))
        }
        for ((_, ps) in all.groupBy { Triple(it.src, it.family, it.game) }) {
            val sorted = ps.sortedBy { it.index }
            var g = if (sorted.first().redWon) 1.0 else 0.0
            val result = g
            for (i in sorted.indices.reversed()) {
                if (i < sorted.lastIndex) {
                    val next = sorted[i + 1]
                    val v = when {
                        lambda >= 1.0 -> result
                        nets != null -> Trainer.sigmoid(nets.sumOf { logit(it, next) } / nets.size)
                        else -> Trainer.sigmoid(k * next.hand)
                    }
                    g = (1 - lambda) * v + lambda * g
                }
                out[sorted[i]] = g.toFloat()
            }
        }
        return out
    }

    /** The net's logit of a recorded position: the same sums as [LearnedNet.corrections], from what was recorded. */
    private fun logit(net: LearnedNet, p: Pos): Double = (
        net.forward(p.x!!, 0) - net.forward(p.x!!, Features.SIZE)
        ).toDouble() + net.recorded(p.extras, true) - net.recorded(p.extras, false)

    private fun LearnedNet.recorded(e: Trainer.Extras, red: Boolean): Double {
        var out = 0.0
        val size = Features.HERO.size
        for (h in if (red) 0 until e.red else e.red until e.heroes.size) {
            out += tableHero(e.heroes[h].toInt(), e.v, h * size)
        }
        for (c in if (red) e.redCards else e.blueCards) out += tableCard(c.toInt())
        return out
    }

    /** Positions of the games in [file], featurized once per schema and rate (cached next to the file). */
    private fun load(file: File, rate: Double, threads: Int): List<Pos> {
        val cache =
            File(
                env("AI_CACHE")?.let(::File) ?: file.parentFile,
                "${file.nameWithoutExtension}.${java.lang.Long.toHexString(Features.SCHEMA_HASH)}-$rate.feat",
            )
        if (cache.exists() && cache.lastModified() >= file.lastModified()) return readCache(cache)
        val games = Learned.readGames(listOf(file))
        val pool = Executors.newFixedThreadPool(threads)
        val t0 = System.nanoTime()
        val ps = try {
            pool.invokeAll(
                games.map { r ->
                    Callable {
                        Learned.positions(r, rate).map {
                            Pos(
                                r.game, r.family, r.lineup, r.size, it.index, it.boundary, it.redWon, it.hand,
                                it.terms, it.x, it.extras,
                            )
                        }
                    }
                },
            ).flatMap { it.get() }
        } finally {
            pool.shutdown()
        }
        println(
            "TRAIN featurized ${games.size} games of $file: ${ps.size} positions in ${"%.0f".format(
                (System.nanoTime() - t0) / 1e9,
            )} s",
        )
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
                for (v in p.x!!) o.writeFloat(v)
                val e = p.extras
                o.writeByte(e.heroes.size)
                o.writeByte(e.red)
                o.write(e.heroes)
                for (v in e.v) o.writeFloat(v)
                for (cards in listOf(e.redCards, e.blueCards)) {
                    o.writeShort(cards.size)
                    for (c in cards) o.writeShort(c.toInt())
                }
            }
        }
    }

    private fun readCache(f: File): List<Pos> = DataInputStream(f.inputStream().buffered(1 shl 20)).use { i ->
        val n = i.readInt()
        check(i.readInt() == Features.SIZE)
        List(n) {
            Pos(
                i.readInt(), i.readLong(), i.readInt(), i.readInt(), i.readInt(), i.readBoolean(), i.readBoolean(),
                i.readFloat(),
                FloatArray(Term.entries.size) {
                    i.readFloat()
                },
                FloatArray(2 * Features.SIZE) { i.readFloat() },
                run {
                    val n = i.readByte().toInt()
                    val red = i.readByte().toInt()
                    val heroes = ByteArray(n).also { i.readFully(it) }
                    val v = FloatArray(n * Features.HERO.size) { i.readFloat() }
                    val rc = ShortArray(i.readShort().toInt()) { i.readShort() }
                    val bc = ShortArray(i.readShort().toInt()) { i.readShort() }
                    Trainer.Extras(heroes, red, v, rc, bc)
                },
            )
        }
    }

    companion object {
        const val WEIGHTS = "src/main/kotlin/dev/quinton/goa/core/game/ai/LearnedWeights.kt"

        /** The six-net leaf's mean coin scale: its value / this is its mean logit (Track E2's search values). */
        private const val SV_SCALE = 37.852

        /** A won game's leaf value is huge: a logit past this is certain enough. */
        private const val SV_CLAMP = 12.0

        /** Rows the report's twelve-term fit uses. */
        private const val FIT_ROWS = 150_000
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
