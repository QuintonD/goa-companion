package dev.quinton.goa.core.game.ai.heuristic

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.ai.AiHarness
import dev.quinton.goa.core.game.ai.Tournament
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

/**
 * Opt-in. `AI_H_ABLATION=<heroId>` (a comma list, or `all` for every playbook) plays the same strength
 * with that hero's playbook and without it (the general book and [HeroStyle.DEFAULT]). The hero sits on
 * both sides: every line-up that seats it, mirrored. `AI_H_GAMES` is the number of line-ups (games are
 * twice that), `AI_H_STRENGTH` the strength (default 8), `AI_THREADS` the games run at once,
 * `AI_H_TUNE` measurement knobs both sides carry (`laneFloor=0.85`). A playbook should not lose; the
 * aim is over 55%.
 */
class HeroAblationTest {
    @Test
    fun `with the playbook against without it`() {
        val spec = System.getenv("AI_H_ABLATION") ?: return
        val strength = System.getenv("AI_H_STRENGTH")?.toIntOrNull() ?: 8
        val lineups = System.getenv("AI_H_GAMES")?.toIntOrNull() ?: 24
        val threads = System.getenv("AI_THREADS")?.toIntOrNull() ?: 2
        val format = AiHarness.Format.parse(System.getenv("AI_FORMAT"))
        val tune = System.getenv("AI_H_TUNE")?.split("+")?.filter { it.contains("=") }
            ?.associate { kv -> kv.substringBefore("=") to kv.substringAfter("=").toDouble() }.orEmpty()
        val heroes = if (spec == "all") HeroPlaybooks.ids().sorted() else spec.split(",").map { it.trim() }
        // Each shuffle of the roster seats a hero once in (roster / table) line-ups.
        val pool = AiHarness.mixedLineups(lineups * 32 / (2 * format.size) + 32, size = format.size)
        for (hero in heroes) {
            val all = pool.filter { hero in it.first || hero in it.second }.take(lineups)
            assertThat(all).isNotEmpty()
            val withBook = Tournament.Side("with") { seed -> HeuristicAi(strength, seed, tune) }
            val without = Tournament.Side("without") { seed ->
                HeuristicAi(strength, seed, tune) { id ->
                    if (id == hero) DefaultHeroHeuristics else HeroPlaybooks.forHero(id)
                }
            }
            val specs = all.mapIndexed { i, lineup ->
                Tournament.Spec(lineup, if (i % 2 == 0) Team.RED else Team.BLUE, 50_000L + i)
            }
            val score = Tournament.play(withBook, without, specs, threads, format)
            val (lo, hi) = score.interval
            println(
                "H_ABLATION $hero strength $strength: $score Wilson ${"%.1f".format(lo * 100)}-" +
                    "${"%.1f".format(hi * 100)}%",
            )
            assertThat(score.games).isGreaterThan(0)
        }
    }
}
