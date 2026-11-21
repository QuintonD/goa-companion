package dev.quinton.goa.core.game.ai.heuristic.heroes

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.ai.AiHarness
import dev.quinton.goa.core.game.ai.Tournament
import dev.quinton.goa.core.game.ai.heuristic.DefaultHeroHeuristics
import dev.quinton.goa.core.game.ai.heuristic.HeroHeuristics
import dev.quinton.goa.core.game.ai.heuristic.HeroPlaybooks
import dev.quinton.goa.core.game.ai.heuristic.HeroStyle
import dev.quinton.goa.core.game.ai.heuristic.HeuristicAi
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

/**
 * Opt-in. `AI_H_ABLATION_BATCH=arien,bain,…` runs [dev.quinton.goa.core.game.ai.heuristic.HeroAblationTest]'s
 * measurement for several heroes in one build (one Gradle start on a shared machine). Same seeds, same
 * line-ups. `AI_H_ABLATION_MODE=full,rules,style` (comma list): `rules` seats the notes with the
 * default style, `style` the style alone.
 */
class BatchAAblationTest {
    @Test
    fun `with each playbook against without it`() {
        val heroes = System.getenv("AI_H_ABLATION_BATCH")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
            ?: return
        val strength = System.getenv("AI_H_STRENGTH")?.toIntOrNull() ?: 8
        val lineups = System.getenv("AI_H_GAMES")?.toIntOrNull() ?: 24
        val format = AiHarness.Format.parse(System.getenv("AI_FORMAT"))
        val modes = System.getenv("AI_H_ABLATION_MODE")?.split(',')?.map { it.trim() } ?: listOf("full")
        for (mode in modes) {
            for (hero in heroes) {
                val all = AiHarness.mixedLineups(lineups * 4, size = format.size)
                    .filter { hero in it.first || hero in it.second }
                    .take(lineups)
                assertThat(all).isNotEmpty()
                val book = variant(HeroPlaybooks.forHero(hero), mode)
                val withBook = Tournament.Side("with") { seed ->
                    HeuristicAi(strength, seed) { id -> if (id == hero) book else HeroPlaybooks.forHero(id) }
                }
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
                        score.record(lineup, withRed, r.winner?.let { (it == Team.RED) == withRed }, r, ms)
                    }
                }
                val (lo, hi) = score.interval
                println(
                    "H_ABLATION $mode $hero strength $strength: $score Wilson ${"%.1f".format(
                        lo * 100,
                    )}-${"%.1f".format(hi * 100)}%",
                )
                assertThat(score.games).isGreaterThan(0)
            }
        }
    }

    /** `rules`: the playbook's notes with the default style; `style`: its style with no notes; else the whole book. */
    private fun variant(book: HeroHeuristics, mode: String?): HeroHeuristics = when (mode) {
        "rules" -> object : HeroHeuristics by book {
            override val style: HeroStyle = HeroStyle.DEFAULT
        }

        "style" -> object : HeroHeuristics {
            override val heroId: String = book.heroId
            override val style: HeroStyle = book.style
        }

        else -> book
    }
}
