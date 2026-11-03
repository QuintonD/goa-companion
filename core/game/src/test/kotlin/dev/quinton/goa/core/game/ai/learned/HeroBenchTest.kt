package dev.quinton.goa.core.game.ai.learned

import dev.quinton.goa.core.game.ai.Term
import dev.quinton.goa.core.game.ai.Tournament
import dev.quinton.goa.core.model.match.Team
import java.io.File
import kotlin.random.Random
import org.junit.Test

/**
 * Stream D of the v0.14 AI round (ADR-063, AI.md "Stream D"), all opt-in:
 *
 * - `AI_BENCH=a:b`: the per-hero bench. `AI_N` line-ups of the `AI_FAMILY` family (default tune), mirrored,
 *   every hero seated equally often, or with `AI_SEAT=<hero>,…` one of those heroes in every line-up;
 *   writes the games (`bench-<a>-<b>.games`) and the rating fit ([HeroRating]) under `AI_OUT`
 *   (default `D:/.goa-qa/v14/d`). `AI_BENCH_FIT=<games file>,…` fits stored games instead.
 * - `AI_TUNE=<hero>:<base>[:<opponent>]`: tunes the hero's row of the per-hero table ([HeroScales]) with
 *   [Ntbea] on the games that seat it: each evaluation is `AI_TUNE_PAIRS` mirrored pairs (3) of base
 *   plus the row against the opponent (default base), `AI_TUNE_ITER` evaluations (100); terms
 *   `AI_TUNE_TERMS`, values `AI_TUNE_VALUES`.
 * - `AI_EXPLOIT=<base>:<opponent>`: the learned exploiters, [Ntbea] over [EXPLOIT_DIMS] (the [Hunt]
 *   taste, focus fire, exposure and Life scales, own heroes' terms, temperature, kill tempo, leaves).
 *   Its games go to `AI_STORE` in [LearnedSelfPlayTest]'s format.
 */
class HeroBenchTest {
    private val out = File(System.getenv("AI_OUT") ?: "D:/.goa-qa/v14/d")
    private val threads = System.getenv("AI_THREADS")?.toIntOrNull() ?: 3
    private val family = when (System.getenv("AI_FAMILY") ?: "tune") {
        "train" -> Teams.TRAIN
        "test" -> Teams.TEST
        else -> Teams.TUNE
    }

    @Test
    fun `per-hero bench`() {
        System.getenv("AI_BENCH_FIT")?.let { files ->
            val games = HeroRating.read(files.split(",").map(::File))
            println("BENCH ${HeroRating.report(HeroRating.fit(games), "a", "b")}")
            return
        }
        val (a, b) = System.getenv("AI_BENCH")?.split(":") ?: return
        val size = System.getenv("AI_SIZE")?.toIntOrNull() ?: 3
        val n = System.getenv("AI_N")?.toIntOrNull() ?: 64
        val first = System.getenv("AI_FIRST")?.toIntOrNull() ?: 0
        val seat = System.getenv("AI_SEAT")?.split(",")
        val specs = if (seat == null) Teams.specs(n, size, family, first) else Teams.seating(n, size, family, seat, first)
        val label = "bench-${a.replace("=", "")}-${b.replace("=", "")}${if (seat == null) "" else "-" + seat.joinToString("-")}"
        out.mkdirs()
        val gamesFile = File(out, "$label.games")
        if (first == 0) gamesFile.writeText("")
        val t0 = System.nanoTime()
        val score = Teams.mirrored(Learned.side(a), Learned.side(b), specs, threads, onGame = { p ->
            gamesFile.appendText(HeroRating.Game.of(p).line() + "\n")
        }, batch = BENCH_BATCH)
        val fit = HeroRating.fit(HeroRating.read(listOf(gamesFile)))
        val report = buildString {
            appendLine("${size}v$size, family ${System.getenv("AI_FAMILY") ?: "tune"}, line-ups $first.. (${specs.size})")
            appendLine("$score in ${"%.0f".format((System.nanoTime() - t0) / 1e9)} s")
            appendLine(score.details())
            appendLine()
            append(HeroRating.report(fit, a, b))
            appendLine()
            appendLine("Raw uplift (a's rate with the hero less b's, this run only):")
            appendLine(Tournament.scorecard(score))
        }
        File(out, "$label.txt").writeText(report)
        println("BENCH $report")
    }

    @Test
    fun `tune a hero's row with NTBEA`() {
        val spec = System.getenv("AI_TUNE")?.split(":") ?: return
        val hero = spec[0]
        val base = spec[1]
        val opponent = spec.getOrElse(2) { base }
        val terms = (System.getenv("AI_TUNE_TERMS") ?: "EXPOSURE,PRESENCE,OPPORTUNITY,HAND_RATING,PIECES,LEVELS")
            .split(",").map { Term.valueOf(it) }
        val values = (System.getenv("AI_TUNE_VALUES") ?: "0.25,0.5,1.0,1.5,2.0,3.0").split(",").map { it.toDouble() }
        val dims = terms.map { t -> values.map { v -> if (v == 1.0) "" else "h.$hero.$t=$v" } }
        val start = IntArray(dims.size) { values.indexOf(1.0) }
        val log = File(out, "tune-$hero-${base.replace("=", "")}.log")
        val result = search(base, opponent, dims, start, log) { i, pairs ->
            Teams.seating(pairs, 3, family, listOf(hero), i * pairs)
        }
        println("TUNE $hero: $result")
    }

    @Test
    fun `learned exploiters with NTBEA`() {
        val (base, opponent) = System.getenv("AI_EXPLOIT")?.split(":") ?: return
        val start = IntArray(EXPLOIT_DIMS.size) { d -> EXPLOIT_DIMS[d].indexOfFirst { it.isEmpty() }.coerceAtLeast(0) }
        val log = File(out, "exploit-${base.replace("=", "")}.log")
        val first = System.getenv("AI_FIRST")?.toIntOrNull() ?: 0
        val result = search(base, opponent, EXPLOIT_DIMS, start, log) { i, pairs ->
            Teams.specs(pairs, 3, family, first + i * pairs)
        }
        println("EXPLOIT $result")
    }

    /**
     * NTBEA over variant fragments: [dims] lists each dimension's fragments ("" = unchanged); a point is
     * [base] plus its fragments. Evaluation i plays the pairs [specs] gives against [opponent].
     */
    private fun search(
        base: String,
        opponent: String,
        dims: List<List<String>>,
        start: IntArray,
        log: File,
        specs: (Int, Int) -> List<Teams.Spec>,
    ): String {
        val iterations = System.getenv("AI_TUNE_ITER")?.toIntOrNull() ?: 100
        val pairs = System.getenv("AI_TUNE_PAIRS")?.toIntOrNull() ?: 3
        val explore = System.getenv("AI_TUNE_EXPLORE")?.toDoubleOrNull() ?: 1.0
        val store = System.getenv("AI_STORE")?.let(::File)
        val model = Ntbea(dims.map { it.size }.toIntArray(), Random(System.getenv("AI_SEED")?.toLong() ?: 1L), explore)
        val opp = Learned.side(opponent)
        fun variant(x: IntArray) = base + x.indices.map { dims[it][x[it]] }.filter { it.isNotEmpty() }.joinToString("") { "+$it" }
        out.mkdirs()
        log.writeText("NTBEA $base against $opponent, $iterations × $pairs pairs\n")
        var x = start
        var games = 0
        val t0 = System.nanoTime()
        for (i in 0 until iterations) {
            val v = variant(x)
            val points = LinkedHashMap<Long, Double>()
            val score = Teams.mirrored(Learned.side(v), opp, specs(i, pairs), threads, onGame = { p ->
                val a = when (p.result.winner) {
                    null -> 0.5
                    Team.RED -> if (p.aRed) 1.0 else 0.0
                    Team.BLUE -> if (p.aRed) 0.0 else 1.0
                }
                points[p.spec.seed] = (points[p.spec.seed] ?: 0.0) + a / 2
                store?.let { Learned.store(it, p, v, opponent, family) }
            })
            games += score.games
            for (r in points.values) model.add(x, r)
            log.appendText(
                "$i ${"%.2f".format(points.values.average())} defeats ${score.aDefeats}/${score.bDefeats} " +
                    "est ${"%.3f".format(model.estimate(x))} n ${model.count(x)} $v (${"%.0f".format((System.nanoTime() - t0) / 1e9)} s)\n",
            )
            x = model.next(x)
        }
        val ranked = model.evaluated.values.sortedByDescending { model.estimate(it) }
        val best = ranked.first()
        val report = buildString {
            appendLine("best ${variant(best)} est ${"%.3f".format(model.estimate(best))} n ${model.count(best)}")
            ranked.take(TOP).forEach { appendLine("  ${"%.3f".format(model.estimate(it))} n ${model.count(it)} ${variant(it)}") }
            appendLine("$games games in ${"%.0f".format((System.nanoTime() - t0) / 1e9)} s")
        }
        log.appendText(report)
        return report
    }

    companion object {
        private const val BENCH_BATCH = 48
        private const val TOP = 8

        /** The exploiters' knob space: each dimension's fragments of a variant ("" = as the base). */
        val EXPLOIT_DIMS: List<List<String>> = listOf(
            listOf("", "hunt=0.5/1.5/1.3", "hunt=1/2/1.3", "hunt=0.5/2/1.6", "hunt=1/3/1.6", "hunt=0/2/1.5"),
            listOf("", "focus=true"),
            listOf("xEXPOSURE=0.15", "", "xEXPOSURE=0.6"),
            listOf("", "xLIFE=1.5", "xLIFE=2"),
            listOf("temp=0.05", "", "temp=0.3"),
            listOf("", "learned=HAND"),
            listOf("own.PRESENCE=0.5", "", "own.PRESENCE=2"),
            listOf("own.OPPORTUNITY=0.5", "", "own.OPPORTUNITY=2"),
            listOf("", "tempo=10", "tempo=20"),
        )
    }
}
