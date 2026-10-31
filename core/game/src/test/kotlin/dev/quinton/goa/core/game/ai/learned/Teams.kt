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
    ): Tournament.Score {
        val score = Tournament.Score(a, b)
        val pool = Executors.newFixedThreadPool(threads)
        try {
            // AI_BATCH: a running score after every so many line-ups (a long run that is cut short still says something).
            val every = System.getenv("AI_BATCH")?.toIntOrNull() ?: if (sprt == null) specs.size else SPRT_BATCH
            for (batch in specs.chunked(every.coerceAtLeast(1))) {
                playBatch(a, b, batch, pool, score)
                if (every < specs.size) println("TEAMS so far $score")
                if (sprt != null && sprt.verdict(score.aWins, score.bWins) != Tournament.Sprt.Verdict.GOING_ON) break
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
    ) {
        val jobs = ArrayList<Callable<Pair<Boolean?, AiHarness.Result>>>()
        val aRed = ArrayList<Boolean>()
        for (spec in specs) {
            val (red, blue) = spec.lineup
            for (aIsRed in listOf(true, false)) {
                aRed += aIsRed
                jobs += Callable {
                    val setup = AiHarness.setup(red, blue, AiLevel.NORMAL, AiLevel.NORMAL, spec.coin)
                    val r = AiHarness.play(
                        setup,
                        mapOf(
                            Team.RED to (if (aIsRed) a else b).factory(spec.seed * 2),
                            Team.BLUE to (if (aIsRed) b else a).factory(spec.seed * 2 + 1),
                        ),
                        maxDecisions = 20_000,
                    )
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
            when (aWon) {
                null -> score.draws++
                true -> score.aWins++
                false -> score.bWins++
            }
            val (red, blue) = specs[i / 2].lineup
            score.countHeroes(red, blue, aRed[i], aWon)
            val reason = r.session.view(null).winReason?.name ?: "UNFINISHED"
            score.reasons[reason] = (score.reasons[reason] ?: 0) + 1
            score.rounds += r.rounds
            score.lines +=
                "${red.joinToString("+")} vs ${blue.joinToString("+")}: $a ${if (aRed[i]) "Red" else "Blue"}, " +
                "${aWon?.let { if (it) "$a won" else "$b won" } ?: "draw"} by $reason in round ${r.rounds}"
        }
    }
}
