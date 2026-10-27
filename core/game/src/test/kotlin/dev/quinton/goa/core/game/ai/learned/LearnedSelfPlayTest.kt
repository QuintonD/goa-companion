package dev.quinton.goa.core.game.ai.learned

import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.ai.AiHarness
import dev.quinton.goa.core.game.ai.learned.Learned.answers
import dev.quinton.goa.core.model.match.Team
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.random.Random
import org.junit.Test

/**
 * Opt-in: the self-play data of the learned evaluation (AI.md "Learned evaluation").
 * `AI_SELFPLAY=<games>:<out.jsonl>` plays games on the training line-ups ([Teams.TRAIN]), each team
 * a variant drawn from `AI_VARIANTS` (`spec*weight,…`, default [GEN0]), team sizes drawn from
 * `AI_SIZES` (`size*weight,…`, default 2v2 ×1, 3v3 ×2, 4v4 ×1), and writes one replayable
 * [Learned.GameRecord] per line. `AI_FIRST` offsets the game numbers (and so the line-ups) so that
 * several runs never repeat a game. At most `AI_THREADS` (4) games at once.
 */
class LearnedSelfPlayTest {
    @Test
    fun `self-play games for the learned evaluation`() {
        val spec = System.getenv("AI_SELFPLAY")?.split(":", limit = 2) ?: return
        val games = spec[0].toInt()
        val out = File(spec[1])
        val first = System.getenv("AI_FIRST")?.toIntOrNull() ?: 0
        val threads = System.getenv("AI_THREADS")?.toIntOrNull() ?: 4
        val variants = weighted(System.getenv("AI_VARIANTS") ?: GEN0)
        val sizes = weighted(System.getenv("AI_SIZES") ?: "2*1,3*2,4*1").map { (s, w) -> s.toInt() to w }
        out.parentFile.mkdirs()
        val lineups = sizes.associate { (size, _) -> size to Teams.lineups(first + games, size, Teams.TRAIN) }
        val sides = variants.map { it.first }.distinct().associateWith { Learned.side(it) }
        val t0 = System.nanoTime()
        val pool = Executors.newFixedThreadPool(threads)
        var done = 0
        try {
            val jobs = (first until first + games).map { g ->
                Callable {
                    try {
                        game(g, sizes, lineups, variants, sides)
                    } catch (e: Exception) {
                        throw IllegalStateException("game $g", e)
                    }
                }
            }
            val cs = java.util.concurrent.ExecutorCompletionService<String>(pool)
            val futures = jobs.map { cs.submit(it) }
            out.bufferedWriter().use { w ->
                repeat(futures.size) {
                    val line = try {
                        cs.take().get()
                    } catch (e: java.util.concurrent.ExecutionException) {
                        println("SELFPLAY game failed: ${e.cause}")
                        e.cause?.printStackTrace(System.out)
                        null
                    }
                    if (line != null) {
                        w.write(line)
                        w.newLine()
                        w.flush()
                        done++
                    }
                    if ((it + 1) % 50 == 0) {
                        println("SELFPLAY ${it + 1} / $games in ${"%.0f".format((System.nanoTime() - t0) / 1e9)} s")
                    }
                }
            }
        } finally {
            pool.shutdown()
        }
        println("SELFPLAY wrote $done games to $out in ${"%.0f".format((System.nanoTime() - t0) / 1e9)} s")
    }

    private fun game(
        g: Int,
        sizes: List<Pair<Int, Double>>,
        lineups: Map<Int, List<Pair<List<String>, List<String>>>>,
        variants: List<Pair<String, Double>>,
        sides: Map<String, dev.quinton.goa.core.game.ai.Tournament.Side>,
    ): String {
        val rng = Random(Teams.TRAIN * 1_000_003L + g)
        val size = pick(sizes, rng)
        val (red, blue) = lineups.getValue(size)[g]
        val rv = pick(variants, rng)
        val bv = pick(variants, rng)
        val coin = if (g % 2 == 0) Team.RED else Team.BLUE
        val setup = AiHarness.setup(red, blue, AiLevel.NORMAL, AiLevel.NORMAL, coin)
        val r = AiHarness.play(
            setup,
            mapOf(
                Team.RED to sides.getValue(rv).factory(g * 2L + 1),
                Team.BLUE to sides.getValue(bv).factory(g * 2L + 2),
            ),
            maxDecisions = 20_000,
        )
        val rec = Learned.GameRecord(
            g, size, g, Teams.TRAIN, rv, bv, r.winner?.name, r.rounds,
            r.session.view(null).winReason?.name ?: "UNFINISHED", setup, r.session.answers(),
        )
        return Learned.JSON.encodeToString(Learned.GameRecord.serializer(), rec)
    }

    private fun weighted(s: String): List<Pair<String, Double>> = s.split(",").map {
        val (v, w) = if ("*" in it) it.substringBeforeLast("*") to it.substringAfterLast("*").toDouble() else it to 1.0
        v to w
    }

    private fun <T> pick(list: List<Pair<T, Double>>, rng: Random): T {
        var r = rng.nextDouble() * list.sumOf { it.second }
        for ((v, w) in list) {
            r -= w
            if (r < 0) return v
        }
        return list.last().first
    }

    companion object {
        /**
         * Generation 0: strength 7 (fast) against itself, a noisy 7, an aggressive kill-seeking 7
         * that ignores its own danger and values Life counters twice (so mistakes get punished),
         * strength 6 and a little strength 8.
         */
        const val GEN0: String = "s7*4,s7+temp=1.5+gap=4*2,s7+xEXPOSURE=0+xLIFE=2+tempo=10*2,s6*1,s8*1"
    }
}
