package dev.quinton.goa.core.game.ai.heuristic.heroes

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.ai.AiHarness
import dev.quinton.goa.core.game.ai.Tournament
import dev.quinton.goa.core.game.ai.heuristic.DefaultHeroHeuristics
import dev.quinton.goa.core.game.ai.heuristic.HeroPlaybooks
import dev.quinton.goa.core.game.ai.heuristic.HeuristicAi
import dev.quinton.goa.core.model.match.Team
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import org.junit.Test

/**
 * Opt-in. `AI_H_ABLATION_BATCH=razzle,rowenna,…` runs [dev.quinton.goa.core.game.ai.heuristic.HeroAblationTest]'s
 * games (same line-ups, seeds and sides) for several heroes in one Gradle run, `AI_H_THREADS` games at a time.
 */
class BatchCAblationTest {
    @Test
    fun `with the playbook against without it, several heroes`() {
        val heroes =
            System.getenv("AI_H_ABLATION_BATCH")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: return
        val strength = System.getenv("AI_H_STRENGTH")?.toIntOrNull() ?: 8
        val lineups = System.getenv("AI_H_GAMES")?.toIntOrNull() ?: 24
        val threads = System.getenv("AI_H_THREADS")?.toIntOrNull() ?: 2
        val format = AiHarness.Format.parse(System.getenv("AI_FORMAT"))
        val pool = Executors.newFixedThreadPool(threads)
        try {
            for (hero in heroes) {
                val all = AiHarness.mixedLineups(lineups * 4, size = format.size)
                    .filter { hero in it.first || hero in it.second }
                    .take(lineups)
                assertThat(all).isNotEmpty()
                val withBook = Tournament.Side("with") { seed -> HeuristicAi(strength, seed) }
                val without = Tournament.Side("without") { seed ->
                    HeuristicAi(strength, seed) { id ->
                        if (id ==
                            hero
                        ) {
                            DefaultHeroHeuristics
                        } else {
                            HeroPlaybooks.forHero(id)
                        }
                    }
                }
                val jobs = all.withIndex().flatMap { (i, lineup) ->
                    val coin = if (i % 2 == 0) Team.RED else Team.BLUE
                    listOf(true, false).map { withRed ->
                        Callable { runCatching { game(lineup, withRed, coin, i, withBook, without, strength, format) } }
                    }
                }
                val score = Tournament.Score(withBook, without)
                val results = pool.invokeAll(jobs).map { it.get() }
                var failed = 0
                for ((i, pair) in results.chunked(2).withIndex()) {
                    // A crashed game drops its mirrored pair (as AI_KEEP_GOING does in Tournament).
                    if (pair.any { it.isFailure }) {
                        failed++
                        println(
                            "H_ABLATION   $hero pair $i failed: ${pair.firstNotNullOf {
                                it.exceptionOrNull()
                            }.message?.take(300)}",
                        )
                        continue
                    }
                    for ((j, res) in pair.withIndex()) {
                        val (r, ms) = res.getOrThrow()
                        val withRed = j == 0
                        val won = r.winner?.let { (it == Team.RED) == withRed }
                        score.record(all[i], withRed, won, r, ms)
                    }
                }
                if (failed > 0) println("H_ABLATION   $hero: $failed pairs failed and were left out")
                val (lo, hi) = score.interval
                println(
                    "H_ABLATION $hero strength $strength: $score Wilson ${"%.1f".format(
                        lo * 100,
                    )}-${"%.1f".format(hi * 100)}%",
                )
                println("H_ABLATION   ${score.details()}")
            }
        } finally {
            pool.shutdown()
        }
    }

    @Suppress("LongParameterList")
    private fun game(
        lineup: Pair<List<String>, List<String>>,
        withRed: Boolean,
        coin: Team,
        i: Int,
        withBook: Tournament.Side,
        without: Tournament.Side,
        strength: Int,
        format: AiHarness.Format,
    ): Pair<AiHarness.Result, Long> {
        val (red, blue) = lineup
        val t0 = System.nanoTime()
        val setup = AiHarness.setup(
            red,
            blue,
            AiLevel.of(strength),
            AiLevel.of(strength),
            coin,
            format.length,
            format.mapId,
        )
        val r = AiHarness.play(
            setup,
            mapOf(
                Team.RED to (if (withRed) withBook else without).factory(50_000L + i),
                Team.BLUE to (if (withRed) without else withBook).factory(50_000L + i + 1),
            ),
        )
        return r to (System.nanoTime() - t0) / 1_000_000
    }
}
