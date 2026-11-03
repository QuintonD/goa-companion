package dev.quinton.goa.core.game.ai.learned

import dev.quinton.goa.core.game.AnswerResult
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.ai.AiPlayers
import dev.quinton.goa.core.game.ai.CardPlayModel
import dev.quinton.goa.core.game.ai.Evaluator
import dev.quinton.goa.core.game.ai.Searcher
import dev.quinton.goa.core.game.ai.StaticPolicy
import dev.quinton.goa.core.game.engine.EngineSession
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.Base64
import java.util.concurrent.Executors
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random
import org.junit.Test

/**
 * Opt-in: the learned card-play model (ADR-062 part 2, [CardPlayModel]). Gated offline first.
 *
 * - `AI_PLAYS=<games.jsonl>,…` replays stored games and records every card play of the sides whose
 *   variant matches `AI_PLAYS_SIDES` (a regex, default the plain searching sides with a net:
 *   `s[78]\+net=[^+]+`) into `AI_PLAYS_CACHE` (a file; read instead when it exists): the hero, its hand
 *   pool, the card played, [CardPlayModel.features] and today's static scores.
 * - Trains on line-ups whose number % 10 is 0–7, stops early on 8, reports on 9: log-loss and top-1 of
 *   today's opponent model (softmax of the static scores, τ 1.5, 15% uniform), the same form refitted,
 *   and the learned model, overall and per hero. `AI_PLAYS_WRITE=1` writes the checked-in weights.
 * - `AI_PLAYS_EVAL=<games.jsonl>,…` (cache `AI_PLAYS_EVAL_CACHE`) reports on every play of those games' sides
 *   matching `AI_PLAYS_EVAL_SIDES` (default `s10.*`) as well.
 * - Knobs: `AI_PLAYS_L2` (shared, default 1e-4), `AI_PLAYS_TABLE_L2` (hero and card tables, 1e-3),
 *   `AI_PLAYS_TABLES=0` (shared weights only), `AI_SEED` (1), `AI_THREADS` (replay threads, 1).
 */
class CardPlayTest {
    /** One card play. */
    internal class Play(
        val lineup: Int,
        val hero: String,
        val pool: List<String>,
        val chosen: Int,
        val turn: Float,
        /** pool.size × F. */
        val phi: FloatArray,
        /** Today's static scores ([StaticPolicy.playScore]), for the baseline. */
        val scores: FloatArray,
    )

    @Test
    fun `learned card-play model`() {
        val spec = System.getenv("AI_PLAYS") ?: return
        val cache = File(System.getenv("AI_PLAYS_CACHE") ?: "card-plays.bin")
        val plays = if (cache.exists()) read(cache) else extract(spec).also { write(cache, it) }
        println("PLAYS ${plays.size} card plays, ${plays.map { it.hero }.toSet().size} heroes")
        val train = plays.filter { it.lineup % 10 < 8 }
        val valid = plays.filter { it.lineup % 10 == 8 }
        val test = plays.filter { it.lineup % 10 == 9 }
        val tables = System.getenv("AI_PLAYS_TABLES") != "0"
        val model = fit(train, valid, tables)
        val (tau, mix) = refitStatic(train)
        println("PLAYS refitted static model: tau %.2f, uniform %.2f".format(tau, mix))
        report(test, model, tau, mix)
        System.getenv("AI_PLAYS_EVAL")?.let { eval ->
            val evalCache = File(System.getenv("AI_PLAYS_EVAL_CACHE") ?: "card-plays-eval.bin")
            val sides = System.getenv("AI_PLAYS_EVAL_SIDES") ?: "s10.*"
            val more = if (evalCache.exists()) read(evalCache) else extract(eval, sides).also { write(evalCache, it) }
            println("PLAYS other games ($sides): ${more.size} card plays")
            report(more, model, tau, mix)
        }
        if (System.getenv("AI_PLAYS_WRITE") != null) {
            writeWeights(model, File(OUT), "${spec.split(",").joinToString { File(it).name }}; ${train.size} plays")
            println("PLAYS wrote $OUT")
        }
    }

    /** `AI_PLAYS_TIME=<games.jsonl>:<games>`: µs per guess of a hand, today's model against the learned one. */
    @Test
    fun `the cost of a guess`() {
        val spec = System.getenv("AI_PLAYS_TIME") ?: return
        val games = Learned.readGames(listOf(File(spec.substringBeforeLast(":")))).take(spec.substringAfterLast(":").toInt())
        val ev = Evaluator(TestData.engine.data, AiPlayers.TUNED_WITH_NET)
        val static = StaticPolicy(ev)
        val model = CardPlayModel.CURRENT
        val states = ArrayList<Pair<dev.quinton.goa.core.game.engine.GameState, Pair<String, List<String>>>>()
        for (r in games) {
            val s = TestData.engine.newGame(r.setup) as EngineSession
            for (c in r.answers) {
                val d = s.pending ?: break
                if (d.kind == DecisionKind.PLAY_CARD) {
                    states += s.state.copy() to (d.seat to d.options.filterIsInstance<Choice.Card>().map { it.cardId })
                }
                if (s.answer(d.id, c) !is AnswerResult.Accepted) break
            }
        }
        fun today(st: dev.quinton.goa.core.game.engine.GameState, h: String, pool: List<String>): Double {
            val ctx = ev.context(st)
            return pool.sumOf { static.playScore(ctx, h, Choice.Card(it)) }
        }
        fun learned(st: dev.quinton.goa.core.game.engine.GameState, h: String, pool: List<String>): Double =
            model.weights(static, ev.context(st), h, pool, 0.15).sum()
        repeat(3) { round ->
            for ((name, f) in listOf("today" to ::today, "learned" to ::learned)) {
                val t0 = System.nanoTime()
                var sink = 0.0
                for ((st, q) in states) sink += f(st, q.first, q.second)
                val us = (System.nanoTime() - t0) / 1e3 / states.size
                println("PLAYS time round $round $name: %.1f µs a guess over ${states.size} hands (%.0f)".format(us, sink))
            }
        }
    }

    // ---- data ---------------------------------------------------------------------------------

    private fun extract(
        spec: String,
        sidesSpec: String = System.getenv("AI_PLAYS_SIDES") ?: """s[78]\+net=[^+]+""",
    ): List<Play> {
        val sides = Regex(sidesSpec)
        val games = Learned.readGames(spec.split(",").map { File(it) })
        val threads = System.getenv("AI_THREADS")?.toIntOrNull() ?: 1
        val pool = Executors.newFixedThreadPool(threads)
        val data = TestData.engine.data
        val futures = games.map { r ->
            pool.submit<List<Play>> {
                val ev = Evaluator(data, AiPlayers.TUNED_WITH_NET)
                val static = StaticPolicy(ev)
                val s = TestData.engine.newGame(r.setup) as EngineSession
                val out = ArrayList<Play>()
                for (c in r.answers) {
                    val d = s.pending ?: break
                    val variant = if (d.team.name == "RED") r.red else r.blue
                    if (d.kind == DecisionKind.PLAY_CARD && c is Choice.Card && sides.matches(variant)) {
                        val ids = d.options.filterIsInstance<Choice.Card>().map { it.cardId }.sorted()
                        val chosen = ids.indexOf(c.cardId)
                        if (ids.size > 1 && chosen >= 0) {
                            val ctx = ev.context(s.state)
                            val phi = CardPlayModel.features(static, ctx, d.seat, ids)
                            val scores = ids.map { static.playScore(ctx, d.seat, Choice.Card(it)).toFloat() }
                            out += Play(
                                r.lineup, d.seat, ids, chosen, CardPlayModel.turnCentred(ctx).toFloat(),
                                FloatArray(ids.size * F) { phi[it / F][it % F] }, scores.toFloatArray(),
                            )
                        }
                    }
                    if (s.answer(d.id, c) !is AnswerResult.Accepted) break
                }
                out
            }
        }
        val all = futures.flatMap { it.get() }
        pool.shutdown()
        return all
    }

    private fun write(f: File, plays: List<Play>) = DataOutputStream(f.outputStream().buffered(1 shl 20)).use { o ->
        o.writeInt(F)
        o.writeInt(plays.size)
        for (p in plays) {
            o.writeInt(p.lineup)
            o.writeUTF(p.hero)
            o.writeInt(p.pool.size)
            p.pool.forEach { o.writeUTF(it) }
            o.writeInt(p.chosen)
            o.writeFloat(p.turn)
            p.phi.forEach { o.writeFloat(it) }
            p.scores.forEach { o.writeFloat(it) }
        }
    }

    private fun read(f: File): List<Play> = DataInputStream(f.inputStream().buffered(1 shl 20)).use { i ->
        require(i.readInt() == F) { "Cached card plays have another feature count" }
        List(i.readInt()) {
            val lineup = i.readInt()
            val hero = i.readUTF()
            val n = i.readInt()
            val pool = List(n) { i.readUTF() }
            Play(
                lineup, hero, pool, i.readInt(), i.readFloat(),
                FloatArray(n * F) { i.readFloat() }, FloatArray(n) { i.readFloat() },
            )
        }
    }

    // ---- the model ----------------------------------------------------------------------------

    internal class Params(val heroes: List<String>, val cards: List<String>) {
        val shared = DoubleArray(F)
        val hero = DoubleArray(heroes.size * F)
        val card = DoubleArray(cards.size * CardPlayModel.CARD_PARAMS)
        val heroIndex = heroes.withIndex().associate { it.value to it.index }
        val cardIndex = cards.withIndex().associate { it.value to it.index }

        fun toModel() = CardPlayModel(
            FloatArray(F) { shared[it].toFloat() },
            heroes,
            FloatArray(hero.size) { hero[it].toFloat() },
            cards,
            FloatArray(card.size) { card[it].toFloat() },
        )
    }

    private fun logits(m: Params, p: Play): DoubleArray {
        val h = m.heroIndex[p.hero]
        return DoubleArray(p.pool.size) { c ->
            var z = 0.0
            for (j in 0 until F) {
                val w = m.shared[j] + (if (h != null) m.hero[h * F + j] else 0.0)
                z += w * p.phi[c * F + j]
            }
            m.cardIndex[p.pool[c]]?.let { k -> z += m.card[k * 2] + m.card[k * 2 + 1] * p.turn }
            z
        }
    }

    private fun softmax(z: DoubleArray): DoubleArray {
        val best = z.max()
        val e = z.map { exp(it - best) }
        val s = e.sum()
        return DoubleArray(z.size) { e[it] / s }
    }

    private fun loss(m: Params, ps: List<Play>): Double = ps.sumOf { -ln(softmax(logits(m, it))[it.chosen]) } / ps.size

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    private fun fit(train: List<Play>, valid: List<Play>, tables: Boolean): Params {
        val heroes = if (tables) train.map { it.hero }.toSortedSet().toList() else emptyList()
        val cards = if (tables) train.flatMap { it.pool }.toSortedSet().toList() else emptyList()
        val m = Params(heroes, cards)
        val l2 = System.getenv("AI_PLAYS_L2")?.toDouble() ?: 1e-4
        val tl2 = System.getenv("AI_PLAYS_TABLE_L2")?.toDouble() ?: 1e-3
        val lr = System.getenv("AI_PLAYS_LR")?.toDouble() ?: 3e-3
        val rng = Random(System.getenv("AI_SEED")?.toLongOrNull() ?: 1L)
        val all = listOf(m.shared, m.hero, m.card)
        val mom = all.map { DoubleArray(it.size) }
        val vel = all.map { DoubleArray(it.size) }
        val grads = all.map { DoubleArray(it.size) }
        val decay = listOf(l2, tl2, tl2)
        var step = 0
        var best = Double.MAX_VALUE
        var bestParams = all.map { it.copyOf() }
        var bad = 0
        val order = train.indices.toMutableList()
        for (epoch in 1..MAX_EPOCHS) {
            order.shuffle(rng)
            for (batch in order.chunked(BATCH)) {
                grads.forEach { it.fill(0.0) }
                for (idx in batch) {
                    val p = train[idx]
                    val prob = softmax(logits(m, p))
                    val h = m.heroIndex[p.hero]
                    for (c in p.pool.indices) {
                        val g = (prob[c] - if (c == p.chosen) 1.0 else 0.0) / batch.size
                        for (j in 0 until F) {
                            val x = p.phi[c * F + j]
                            if (x == 0f) continue
                            grads[0][j] += g * x
                            if (h != null) grads[1][h * F + j] += g * x
                        }
                        m.cardIndex[p.pool[c]]?.let { k ->
                            grads[2][k * 2] += g
                            grads[2][k * 2 + 1] += g * p.turn
                        }
                    }
                }
                step++
                for ((a, w) in all.withIndex()) {
                    val g = grads[a]
                    for (i in w.indices) {
                        val gi = g[i] + 2 * decay[a] * w[i]
                        mom[a][i] = B1 * mom[a][i] + (1 - B1) * gi
                        vel[a][i] = B2 * vel[a][i] + (1 - B2) * gi * gi
                        val mh = mom[a][i] / (1 - Math.pow(B1, step.toDouble()))
                        val vh = vel[a][i] / (1 - Math.pow(B2, step.toDouble()))
                        w[i] -= lr * mh / (sqrt(vh) + 1e-8)
                    }
                }
            }
            val v = loss(m, valid)
            println("PLAYS epoch $epoch train %.4f valid %.4f".format(loss(m, train.take(VALID_SAMPLE)), v))
            if (v < best - 1e-5) {
                best = v
                bestParams = all.map { it.copyOf() }
                bad = 0
            } else if (++bad >= PATIENCE) {
                break
            }
        }
        for ((a, w) in all.withIndex()) bestParams[a].copyInto(w)
        return m
    }

    /** Today's form, softmax(score / τ) mixed with a uniform share, its τ and share fitted on [train]. */
    private fun refitStatic(train: List<Play>): Pair<Double, Double> {
        var best = Triple(Double.MAX_VALUE, 1.5, 0.15)
        for (tau in listOf(0.5, 0.75, 1.0, 1.5, 2.0, 3.0, 4.0, 6.0)) {
            for (mix in listOf(0.0, 0.05, 0.1, 0.15, 0.25, 0.35, 0.5)) {
                val l = train.sumOf { -ln(staticProb(it, tau, mix)[it.chosen]) } / train.size
                if (l < best.first) best = Triple(l, tau, mix)
            }
        }
        return best.second to best.third
    }

    private fun staticProb(p: Play, tau: Double, mix: Double): DoubleArray {
        val s = softmax(DoubleArray(p.pool.size) { p.scores[it] / tau })
        return DoubleArray(s.size) { (1 - mix) * s[it] + mix / s.size }
    }

    private fun report(test: List<Play>, m: Params, tau: Double, mix: Double) {
        val models = listOf<Pair<String, (Play) -> DoubleArray>>(
            "uniform" to { p -> DoubleArray(p.pool.size) { 1.0 / p.pool.size } },
            "today (tau 1.5, 15%)" to { p -> staticProb(p, Searcher.OPPONENT_TAU, 0.15) },
            "today refitted" to { p -> staticProb(p, tau, mix) },
            "learned" to { p -> softmax(logits(m, p)) },
            "learned + 15% uniform" to { p -> softmax(logits(m, p)).let { s -> DoubleArray(s.size) { 0.85 * s[it] + 0.15 / s.size } } },
        )
        fun line(label: String, ps: List<Play>) = models.joinToString("  |  ") { (name, f) ->
            var ll = 0.0
            var top = 0
            for (p in ps) {
                val pr = f(p)
                ll -= ln(pr[p.chosen].coerceAtLeast(1e-12))
                if (pr.indices.maxBy { pr[it] } == p.chosen) top++
            }
            "%s %.4f / %.1f%%".format(name, ll / ps.size, 100.0 * top / ps.size)
        }.let { "$label (${ps.size}): $it" }
        println("PLAYS test, log-loss / top-1: " + line("all", test))
        for ((hero, ps) in test.groupBy { it.hero }.toSortedMap()) println("PLAYS   " + line(hero, ps))
    }

    // ---- weights --------------------------------------------------------------------------------

    private fun writeWeights(m: Params, f: File, trainedOn: String) {
        val model = m.toModel()
        val sb = StringBuilder()
        sb.append("// Generated by CardPlayTest (core/game/ai/AI.md \"v0.14: hand knowledge and card inference\"). ")
        sb.append("Do not edit by hand.\n")
        sb.append("// Trained on: ").append(trainedOn.take(100)).append('\n')
        sb.append("package dev.quinton.goa.core.game.ai\n\n")
        sb.append("@Suppress(\"LargeClass\", \"MaxLineLength\")\n")
        sb.append("internal object CardPlayWeights {\n")
        ids(sb, "HEROES", model.heroes)
        ids(sb, "CARDS", model.cards)
        floats(sb, "shared", model.shared)
        floats(sb, "heroes", model.heroWeights)
        floats(sb, "cards", model.cardWeights)
        sb.append("}\n")
        f.writeText(sb.toString())
    }

    private fun ids(sb: StringBuilder, name: String, ids: List<String>) {
        sb.append("    val $name: List<String> by lazy {\n        listOf(\n")
        for (line in ids.joinToString(" ").chunked(PER_LINE)) sb.append("            \"$line\",\n")
        sb.append("        ).joinToString(\"\").split(\" \").filter { it.isNotEmpty() }\n    }\n\n")
    }

    private fun floats(sb: StringBuilder, name: String, a: FloatArray) {
        val bytes = ByteArrayOutputStream(a.size * 4)
        for (x in a) {
            val v = x.toRawBits()
            for (s in 0..3) bytes.write(v shr (8 * s))
        }
        val lines = Base64.getEncoder().encodeToString(bytes.toByteArray()).chunked(PER_LINE)
        sb.append("    fun $name(): FloatArray = LearnedCodec.floats(\n")
        sb.append("        \"${lines.first()}\"")
        for (line in lines.drop(1)) sb.append(" +\n            \"$line\"")
        sb.append(",\n        ${a.size},\n    )\n")
        if (name != "cards") sb.append('\n')
    }

    private companion object {
        const val F = CardPlayModel.F
        const val BATCH = 256
        const val MAX_EPOCHS = 60
        const val PATIENCE = 4
        const val VALID_SAMPLE = 20_000
        const val B1 = 0.9
        const val B2 = 0.999
        const val PER_LINE = 100
        const val OUT = "src/main/kotlin/dev/quinton/goa/core/game/ai/CardPlayWeights.kt"
    }
}
