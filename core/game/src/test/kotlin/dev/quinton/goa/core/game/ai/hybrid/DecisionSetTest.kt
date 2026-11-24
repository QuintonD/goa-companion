package dev.quinton.goa.core.game.ai.hybrid

import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.AnswerResult
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.ai.AiHarness
import dev.quinton.goa.core.game.ai.AiPlayers
import dev.quinton.goa.core.game.ai.heuristic.HeuristicAi
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.model.match.Team
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Test

/**
 * The decision set (Wave 4 Track F): fixed card-play positions on which strength 10's search is compared
 * offline, without playing games. Opt-in.
 *
 * Positions (version [VERSION]): the card plays with a choice (both seat colours) of stored games, kept by a hash
 * of the game and the question (deterministic):
 * - 2v2: the 24 games of the probe bench (`bench/probe-v1-2v2-1000-1011.jsonl.gz`, h10 against s8, line-ups
 *   1000–1011), kept with chance [KEEP_2V2];
 * - 3v3: `bench/decisions-v1-3v3.jsonl.gz`, h10 against h10 on the 3v3 line-ups 1000–1001 (both colours), kept
 *   with chance [KEEP_3V3]. `AI_DECISIONS_MAKE=1` writes it again (never on the line-ups 4000+).
 *
 * Run: `AI_DECISIONS="old:lp=0+hide=0+deal=0+rep=4;old1:lp=0+hide=0+deal=0+rep=4+seed=1;new:;new1:seed=1"`,
 * `goa-run DecisionSetTest`. A spec is `name:` and knobs over the shipped `AiPlayers.SEARCH_10` (and its leaf);
 * `seed=n` offsets the seed (same search, other samples: the noise floor), `ms=n` gives a budget. Each position
 * is asked of a fresh player per spec (as the apps do). `AI_THREADS` (default 2) positions in parallel, each
 * search on one thread; `AI_DECISIONS_MAX` caps the positions (a smoke run). Prints one `DEC` line per position
 * (each spec's answer, ms, columns and best two scores), then per spec the time, columns and silent fallbacks,
 * then for every pair of specs how many answers differ, by seat colour.
 */
class DecisionSetTest {
    private val threads = System.getenv("AI_THREADS")?.toIntOrNull() ?: 2

    @Test
    fun `decision set`() {
        if (System.getenv("AI_DECISIONS_MAKE") != null) return make()
        val specs = System.getenv("AI_DECISIONS")?.split(";")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: return
        val max = System.getenv("AI_DECISIONS_MAX")?.toIntOrNull() ?: Int.MAX_VALUE
        val parsed = specs.map { it.substringBefore(":") to knobsOf(it.substringAfter(":", "")) }
        val games = load()
        val positions = games.flatMap { g -> g.keep.map { g to it } }.take(max)
        println("DECSET v$VERSION: ${positions.size} card plays from ${games.size} games, ${parsed.size} specs")
        val byGame = positions.groupBy({ it.first }, { it.second })
        val jobs = byGame.map { (g, ns) -> Callable { askGame(g, ns, parsed) } }
        val pool = Executors.newFixedThreadPool(threads.coerceAtLeast(1))
        val rows = try {
            pool.invokeAll(jobs).flatMap { it.get() }
        } finally {
            pool.shutdown()
        }
        report(parsed.map { it.first }, rows)
    }

    /** One stored game: its setup and every answer as an option index. */
    class Stored(
        val label: String,
        val format: AiHarness.Format,
        val red: List<String>,
        val blue: List<String>,
        val coin: Team,
        val answers: List<Int>,
        /** The answer counts at which a kept card play is pending. */
        val keep: List<Int>,
    )

    private class Asked(val choice: Choice, val ms: Double, val cols: Int, val scores: Map<Choice, Double>)

    private class Row(val label: String, val team: Team, val seat: String, val asked: List<Asked>)

    private fun askGame(g: Stored, upTos: List<Int>, specs: List<Pair<String, Map<String, Double>>>): List<Row> {
        val es = newGame(g)
        val out = ArrayList<Row>()
        var done = 0
        for (n in upTos.sorted()) {
            while (done < n) {
                val d = es.pending ?: error("decision set replay ended early")
                check(es.answer(d.id, d.options[g.answers[done]]) is AnswerResult.Accepted)
                done++
            }
            val d = es.pending ?: error("decision set replay: no question")
            check(d.kind == DecisionKind.PLAY_CARD) { "decision set replay drifted at ${g.label}:$n (engine changed?)" }
            val seed = HeuristicAi.mix(SEED_SALT, "${g.label}:$n".hashCode().toLong())
            val asked = specs.map { (_, knobs) ->
                val ai = HybridAi(seed + (knobs["seed"] ?: 0.0).toLong(), BASE + (knobs - "seed"))
                var scores: Map<Choice, Double> = emptyMap()
                var cols = 0
                ai.scored = { _, sc, c ->
                    scores = sc
                    cols = c
                }
                val t0 = System.nanoTime()
                val c = ai.choose(es.forkSession(), d)
                val ms = (System.nanoTime() - t0) / 1e6
                fallbacks.merge(ai.fallbacks)
                Asked(c, ms, cols, scores)
            }
            val row = Row("${g.label}:$n r${es.state.round}t${es.state.turn}", d.team, d.seat, asked)
            println(
                "DEC ${row.label} ${d.team} ${d.seat} " + specs.indices.joinToString(" ") { k ->
                    val a = asked[k]
                    val top = a.scores.entries.sortedByDescending { it.value }.take(2)
                        .joinToString(",") { "${(it.key as? Choice.Card)?.cardId ?: it.key}=${"%.4f".format(it.value)}" }
                    "${specs[k].first}=${(a.choice as? Choice.Card)?.cardId ?: a.choice}|${"%.0f".format(a.ms)}ms|" +
                        "c${a.cols}|$top"
                },
            )
            out += row
        }
        return out
    }

    /** Silent fallbacks over every player of a spec (by spec index, kind). */
    private val fallbacks = Fallbacks()

    private class Fallbacks {
        private val sums = HashMap<String, Long>()

        @Synchronized fun merge(f: Map<String, Long>) {
            for ((k, v) in f) sums[k] = (sums[k] ?: 0L) + v
        }

        @Synchronized fun text(): String = sums.entries.sortedBy { it.key }.joinToString(" ") { "${it.key}=${it.value}" }
    }

    private fun report(names: List<String>, rows: List<Row>) {
        for ((k, name) in names.withIndex()) {
            val ms = rows.map { it.asked[k].ms }.sorted()
            val cols = rows.map { it.asked[k].cols }
            fun pct(p: Int) = ms[((ms.size - 1) * p) / 100]
            for (team in listOf(null, Team.RED, Team.BLUE)) {
                val r = rows.filter { team == null || it.team == team }
                if (r.isEmpty()) continue
                val m = r.map { it.asked[k].ms }
                println(
                    "DECSET $name ${team ?: "all"} n=${r.size} ms mean ${"%.0f".format(m.average())} max " +
                        "${"%.0f".format(m.max())} cols mean ${"%.1f".format(r.map { it.asked[k].cols }.average())}",
                )
            }
            println(
                "DECSET $name ms p50 ${"%.0f".format(pct(50))} p95 ${"%.0f".format(pct(95))} " +
                    "max ${"%.0f".format(ms.last())}; cols max ${cols.max()}",
            )
        }
        println("DECSET fallbacks (all specs): ${fallbacks.text()}")
        for (a in names.indices) {
            for (b in a + 1 until names.size) {
                val parts = listOf(null, Team.RED, Team.BLUE).mapNotNull { team ->
                    val r = rows.filter { team == null || it.team == team }
                    if (r.isEmpty()) return@mapNotNull null
                    val diff = r.count { it.asked[a].choice != it.asked[b].choice }
                    "${team ?: "all"} ${diff}/${r.size} (${"%.1f".format(100.0 * diff / r.size)}%)"
                }
                println("DECSET differ ${names[a]} vs ${names[b]}: ${parts.joinToString(", ")}")
            }
        }
    }

    private fun knobsOf(spec: String): Map<String, Double> = spec.split("+").filter { it.contains("=") }.associate {
        it.substringBefore("=") to it.substringAfter("=").toDouble()
    }

    /** The stored games with their kept card plays. */
    fun load(): List<Stored> = read(File(DIR, PROBE_2V2), KEEP_2V2) + read(File(DIR, GAMES_3V3), KEEP_3V3)

    private fun read(f: File, keep: Double): List<Stored> {
        if (!f.exists()) {
            println("DECSET missing $f")
            return emptyList()
        }
        val out = ArrayList<Stored>()
        GZIPInputStream(f.inputStream()).bufferedReader().useLines { lines ->
            for (raw in lines) {
                val o = Json.parseToJsonElement(raw).jsonObject
                if ("answers" !in o) continue
                val game = o["game"]!!.jsonPrimitive.int
                val g = Stored(
                    "${f.name.substringBefore(".jsonl")}#$game",
                    AiHarness.Format.parse(o["format"]!!.jsonPrimitive.content),
                    o["red"]!!.jsonArray.map { it.jsonPrimitive.content },
                    o["blue"]!!.jsonArray.map { it.jsonPrimitive.content },
                    Team.valueOf(o["coin"]!!.jsonPrimitive.content),
                    o["answers"]!!.jsonPrimitive.content.split(",").filter { it.isNotEmpty() }.map { it.toInt() },
                    emptyList(),
                )
                out += Stored(g.label, g.format, g.red, g.blue, g.coin, g.answers, cardPlays(g, game, keep))
            }
        }
        return out
    }

    /** The kept card plays with a choice of [g] (replayed once). */
    private fun cardPlays(g: Stored, game: Int, keep: Double): List<Int> {
        val es = newGame(g)
        val out = ArrayList<Int>()
        for ((n, i) in g.answers.withIndex()) {
            val d = es.pending ?: break
            if (d.kind == DecisionKind.PLAY_CARD && d.options.count { it is Choice.Card } > 1 && unit(game, d.id) < keep) {
                out += n
            }
            check(es.answer(d.id, d.options[i]) is AnswerResult.Accepted)
        }
        return out
    }

    private fun newGame(g: Stored): EngineSession = TestData.engine.newGame(
        AiHarness.setup(g.red, g.blue, AiLevel.NORMAL, AiLevel.NORMAL, g.coin, g.format.length, g.format.mapId),
    ) as EngineSession

    /** Writes the 3v3 games: h10 against h10 on line-ups [MAKE_FIRST]…, both colours. */
    private fun make() {
        val format = AiHarness.Format.THREE
        val all = AiHarness.mixedLineups(MAKE_FIRST + MAKE_LINEUPS, size = format.size)
        val lines = ArrayList<String>()
        for (i in MAKE_FIRST until MAKE_FIRST + MAKE_LINEUPS) {
            val (red, blue) = all[i]
            val coin = if (i % 2 == 0) Team.RED else Team.BLUE
            val players = mapOf(Team.RED to HeuristicAi(10, 70_000L + 2 * i), Team.BLUE to HeuristicAi(10, 70_001L + 2 * i))
            val es = newGame(Stored("", format, red, blue, coin, emptyList(), emptyList()))
            val answers = ArrayList<Int>()
            while (answers.size < MAX_DECISIONS) {
                val d = es.pending ?: break
                val c = players.getValue(d.team).choose(es, d)
                answers += d.options.indexOf(c)
                check(es.answer(d.id, c) is AnswerResult.Accepted)
            }
            lines += buildJsonObject {
                put("game", i)
                put("format", format.label)
                put("red", buildJsonArray { red.forEach { add(JsonPrimitive(it)) } })
                put("blue", buildJsonArray { blue.forEach { add(JsonPrimitive(it)) } })
                put("coin", coin.name)
                put("lineup", i)
                put("answers", answers.joinToString(","))
            }.toString()
            println("DECSET_MAKE 3v3 line-up $i: ${answers.size} answers, winner ${es.state.winner}")
        }
        val out = File(DIR, GAMES_3V3)
        GZIPOutputStream(out.outputStream()).bufferedWriter().use { w -> lines.forEach { w.write(it + "\n") } }
        println("DECSET_MAKE wrote $out")
    }

    private fun unit(game: Int, id: Long): Double =
        (HeuristicAi.mix(game.toLong() * 7_919L + KEEP_SALT, id) ushr 11).toDouble() / (1L shl 53).toDouble()

    companion object {
        /** Bump when the positions change (other games, other keep rates). */
        const val VERSION = 1
        const val DIR = "src/test/resources/bench"
        const val PROBE_2V2 = "probe-v1-2v2-1000-1011.jsonl.gz"
        const val GAMES_3V3 = "decisions-v1-3v3.jsonl.gz"
        const val KEEP_2V2 = 0.055
        const val KEEP_3V3 = 0.2
        private const val MAKE_FIRST = 1000
        private const val MAKE_LINEUPS = 2
        private const val MAX_DECISIONS = 8000
        private const val KEEP_SALT = 0xDEC5E7L
        private const val SEED_SALT = 0xDEC5EEDL

        /** The shipped strength 10 (its knobs and leaf); a spec's knobs go on top. */
        val BASE: Map<String, Double> = AiPlayers.SEARCH_10 + ("leaf" to AiPlayers.SEARCH_10_LEAF.ordinal.toDouble())
    }
}
