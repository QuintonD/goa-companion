package dev.quinton.goa.core.game.ai.learned

import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.ai.AiHarness
import dev.quinton.goa.core.game.ai.Tournament
import dev.quinton.goa.core.model.match.Team
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.random.Random

/**
 * Line-ups and mirrored tournaments for any team size (AI.md "Learned evaluation"). The line-up
 * families are disjoint by seed: [TRAIN] seeds the self-play data, [TUNE] the generation and
 * setting choices, [TEST] (and the 2v2 harness's line-ups 900+) the final measurements.
 */
internal object Teams {
    const val TRAIN: Long = 31_000L
    const val TUNE: Long = 32_000L
    const val TEST: Long = 33_000L

    /**
     * [n] line-ups of [size] against [size] from every playable hero: consecutive seeded shuffles of
     * the roster cut into groups of 2 × [size], so every hero is seated about equally often.
     */
    fun lineups(n: Int, size: Int, seed: Long): List<Pair<List<String>, List<String>>> {
        val heroes = TestData.engine.playableHeroes.sorted()
        val rng = Random(seed * 31 + size)
        val out = ArrayList<Pair<List<String>, List<String>>>()
        while (out.size < n) {
            for (group in heroes.shuffled(rng).chunked(2 * size)) {
                if (group.size == 2 * size && out.size < n) out += group.take(size) to group.drop(size)
            }
        }
        return out
    }

    /**
     * [n] line-ups with some heroes seated more often: each group draws 2 × [size] different heroes, a
     * hero's chance in proportion to its weight in [weights] (missing: 1).
     */
    fun weightedLineups(
        n: Int,
        size: Int,
        seed: Long,
        weights: Map<String, Double>,
    ): List<Pair<List<String>, List<String>>> {
        val heroes = TestData.engine.playableHeroes.sorted()
        val rng = Random(seed * 37 + size)
        return List(n) {
            val left = heroes.toMutableList()
            val group = List(2 * size) {
                var r = rng.nextDouble() * left.sumOf { weights[it] ?: 1.0 }
                val i = left.indices.firstOrNull { k ->
                    r -= weights[left[k]] ?: 1.0
                    r < 0
                } ?: left.lastIndex
                left.removeAt(i)
            }
            group.take(size) to group.drop(size)
        }
    }

    /**
     * The per-hero bench's line-ups (ADR-063): line-up i seats `heroes[i % heroes.size]` and 2 × [size] − 1
     * other heroes drawn from the rest of the roster, on a seat and side drawn too, so a chosen hero is
     * seated in every line-up it is due (a regular family seats a hero in 3 of 16).
     */
    fun seating(n: Int, size: Int, family: Long, heroes: List<String>, first: Int = 0): List<Spec> {
        val roster = TestData.engine.playableHeroes.sorted()
        return (first until first + n).map { i ->
            val hero = heroes[i % heroes.size]
            val rng = Random(family * 1_000_003L + size * 7919L + i)
            val group = (roster - hero).shuffled(rng).take(2 * size - 1).toMutableList()
            group.add(rng.nextInt(2 * size), hero)
            Spec(
                group.take(size) to group.drop(size),
                if (i % 2 == 0) Team.RED else Team.BLUE,
                family * 1000 + size * 100_000L + BENCH_SEEDS + i,
            )
        }
    }

    /** Bench seeds start here, apart from [specs]' of the same family. */
    private const val BENCH_SEEDS = 50_000_000L

    /** One finished game of [mirrored]: its spec, whether side a played Red, and the game. */
    class Played(val spec: Spec, val aRed: Boolean, val result: AiHarness.Result)

    /** One game to play: a line-up, the coin and the seed. */
    class Spec(val lineup: Pair<List<String>, List<String>>, val coin: Team, val seed: Long)

    fun specs(n: Int, size: Int, family: Long, first: Int = 0): List<Spec> {
        val all = lineups(first + n, size, family)
        return (first until first + n).map { i ->
            Spec(all[i], if (i % 2 == 0) Team.RED else Team.BLUE, family * 1000 + size * 100_000L + i)
        }
    }

    /**
     * Every spec played twice, the sides swapping teams (same seed and coin). With [sprt] the specs go
     * in batches of [SPRT_BATCH] and the tournament stops as soon as the test decides.
     */
    fun mirrored(
        a: Tournament.Side,
        b: Tournament.Side,
        specs: List<Spec>,
        threads: Int,
        sprt: Tournament.Sprt? = null,
        onGame: ((Played) -> Unit)? = null,
        batch: Int? = null,
        kills: List<KillCount>? = null,
    ): Tournament.Score {
        val score = Tournament.Score(a, b)
        val pool = Executors.newFixedThreadPool(threads)
        try {
            // AI_BATCH: a running score every so many line-ups (a run cut short still says something).
            val every =
                batch ?: System.getenv("AI_BATCH")?.toIntOrNull() ?: if (sprt == null) specs.size else SPRT_BATCH
            for (batch in specs.chunked(every.coerceAtLeast(1))) {
                playBatch(a, b, batch, pool, score, kills, onGame)
                if (every < specs.size) {
                    println("TEAMS so far $score")
                    // The details too, so a run stopped by hand keeps them.
                    println("TEAMS so far   ${score.details()}")
                    if (Oracle.predicted.get() > 0) {
                        println("TEAMS so far   oracle right ${Oracle.right.get()} of ${Oracle.predicted.get()}")
                    }
                    kills?.let { (ka, kb) -> println("TEAMS so far   kills ${ka.line(a.label)} | ${kb.line(b.label)}") }
                    // AI_PROGRESS_FILE: the running score to watch a long run (Gradle prints it at the end).
                    System.getenv("AI_PROGRESS_FILE")?.let { java.io.File(it).appendText("TEAMS so far $score\n") }
                }
                if (sprt != null && sprt.verdict(score) != Tournament.Sprt.Verdict.GOING_ON) break
            }
        } finally {
            pool.shutdown()
        }
        return score
    }

    private const val SPRT_BATCH = 16

    private fun playBatch(
        a: Tournament.Side,
        b: Tournament.Side,
        specs: List<Spec>,
        pool: ExecutorService,
        score: Tournament.Score,
        kills: List<KillCount>?,
        onGame: ((Played) -> Unit)? = null,
    ) {
        val jobs = ArrayList<Callable<Pair<Boolean?, AiHarness.Result>>>()
        val aRed = ArrayList<Boolean>()
        for (spec in specs) {
            val (red, blue) = spec.lineup
            for (aIsRed in listOf(true, false)) {
                aRed += aIsRed
                jobs += Callable {
                    val setup = AiHarness.setup(red, blue, AiLevel.NORMAL, AiLevel.NORMAL, spec.coin)
                    val players = mapOf(
                        Team.RED to (if (aIsRed) a else b).factory(spec.seed * 2),
                        Team.BLUE to (if (aIsRed) b else a).factory(spec.seed * 2 + 1),
                    )
                    val watch = kills?.let { KillCount.Game(players) }
                    val r = AiHarness.play(setup, players, maxDecisions = 20_000, observe = watch?.let { it::observe })
                    if (kills != null && watch != null) {
                        kills[0].add(watch.of(r, if (aIsRed) Team.RED else Team.BLUE))
                        kills[1].add(watch.of(r, if (aIsRed) Team.BLUE else Team.RED))
                    }
                    r.winner?.let { (it == Team.RED) == aIsRed } to r
                }
            }
        }
        for ((i, f) in pool.invokeAll(jobs).withIndex()) {
            val (aWon, r) = try {
                f.get()
            } catch (e: ExecutionException) {
                val spec = specs[i / 2]
                throw IllegalStateException("Game failed: ${spec.lineup}, coin ${spec.coin}, seed ${spec.seed}", e)
            }
            score.record(specs[i / 2].lineup, aRed[i], aWon, r, 0)
            onGame?.invoke(Played(specs[i / 2], aRed[i], r))
        }
    }
}
