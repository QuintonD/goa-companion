package dev.quinton.goa.core.game.ai.heuristic

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.ai.AiHarness
import dev.quinton.goa.core.game.ai.Tournament
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

/**
 * Opt-in. `AI_H_ABLATION=<heroId>` plays the same strength with that hero's playbook and without it
 * (the general book and [HeroStyle.DEFAULT]). The hero sits on both sides. A playbook should not
 * lose; the aim, once it has a real note, is over 55%. Stubs differ only by [HeroStyle], so a
 * result near 50% means the style is not yet a plan.
 */
class HeroAblationTest {
    @Test
    fun `with the playbook against without it`() {
        val hero = System.getenv("AI_H_ABLATION") ?: return
        val strength = System.getenv("AI_H_STRENGTH")?.toIntOrNull() ?: 8
        val lineups = System.getenv("AI_H_GAMES")?.toIntOrNull() ?: 24
        val format = AiHarness.Format.parse(System.getenv("AI_FORMAT"))
        val all = AiHarness.mixedLineups(lineups * 4, size = format.size)
            .filter { hero in it.first || hero in it.second }
            .take(lineups)
        assertThat(all).isNotEmpty()
        val withBook = Tournament.Side("with") { seed -> HeuristicAi(strength, seed) }
        val without = Tournament.Side("without") { seed ->
            HeuristicAi(strength, seed) { id ->
                if (id == hero) DefaultHeroHeuristics else HeroPlaybooks.forHero(id)
            }
        }
        val score = Tournament.Score(withBook, without)
        for ((i, lineup) in all.withIndex()) {
            val (red, blue) = lineup
            val coin = if (i % 2 == 0) Team.RED else Team.BLUE
            for (withRed in listOf(true, false)) {
                val t0 = System.nanoTime()
                val r = AiHarness.play(
                    AiHarness.setup(
                        red,
                        blue,
                        AiLevel.of(strength),
                        AiLevel.of(strength),
                        coin,
                        format.length,
                        format.mapId,
                    ),
                    mapOf(
                        Team.RED to (if (withRed) withBook else without).factory(50_000L + i),
                        Team.BLUE to (if (withRed) without else withBook).factory(50_000L + i + 1),
                    ),
                )
                val ms = (System.nanoTime() - t0) / 1_000_000
                val won = r.winner?.let { (it == Team.RED) == withRed }
                score.record(lineup, withRed, won, r, ms)
            }
        }
        val (lo, hi) = score.interval
        println(
            "H_ABLATION $hero strength $strength: $score Wilson ${"%.1f".format(lo * 100)}-" +
                "${"%.1f".format(hi * 100)}%",
        )
        println("H_ABLATION   ${score.details()}")
        assertThat(score.games).isGreaterThan(0)
    }
}
