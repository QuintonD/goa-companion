package dev.quinton.goa.core.game.ai.heuristic

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.ai.AiHarness
import dev.quinton.goa.core.game.ai.Tournament
import dev.quinton.goa.core.game.ai.heuristic.heroes.LegacyPlaybooks
import dev.quinton.goa.core.game.engine.GameState
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

/**
 * Opt-in. `AI_H_ABLATION=<heroId>` (a comma list, or `all` for every playbook) plays the same strength
 * with that hero's playbook and without it (the general book and [HeroStyle.DEFAULT]). The hero sits on
 * both sides: every line-up that seats it, mirrored. `AI_H_GAMES` is the number of line-ups (games are
 * twice that), `AI_H_STRENGTH` the strength (default 8), `AI_THREADS` the games run at once,
 * `AI_H_TUNE` measurement knobs both sides carry (`laneFloor=0.85`). A playbook should not lose; the
 * aim is over 55%. `AI_H_FIRST` is the first mixed line-up index the pool starts at (development line-ups
 * from 1000; the held-out sets are 300–395 and 600–695). `AI_H_VS=legacy` seats the hero's previous
 * playbook ([LegacyPlaybooks]) on the other side instead of the general book: the new notes against the
 * old, head to head. `AI_H_OUT` appends each result line to that file.
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
        // AI_H_ABLATION_MODE=full,rules,style: the whole book, its notes with the default style, its style alone;
        // noeval, nothreat, noplay, nochoose: the whole book less one hook (see [variant]).
        val modes = System.getenv("AI_H_ABLATION_MODE")?.split(",")?.map { it.trim() } ?: listOf("full")
        val first = System.getenv("AI_H_FIRST")?.toIntOrNull() ?: 0
        val legacy = System.getenv("AI_H_VS") == "legacy"
        // AI_H_SKIP: leave out the first line-ups that seat the hero (a fresh set for a re-measure).
        val skip = System.getenv("AI_H_SKIP")?.toIntOrNull() ?: 0
        // Each shuffle of the roster seats a hero once in (roster / table) line-ups.
        val pool = AiHarness.mixedLineups(
            first + (lineups + maxOf(96, skip)) * 32 / (2 * format.size) + 32,
            size = format.size,
        )
            .drop(first)
        for (mode in modes) {
            for (hero in heroes) {
                val all = pool.filter { hero in it.first || hero in it.second }.drop(skip).take(lineups)
                assertThat(all).isNotEmpty()
                val book = variant(HeroPlaybooks.forHero(hero), mode)
                val withBook = Tournament.Side("with") { seed ->
                    HeuristicAi(strength, seed, tune) { id -> if (id == hero) book else HeroPlaybooks.forHero(id) }
                }
                val old = if (legacy) LegacyPlaybooks.forHero(hero) else DefaultHeroHeuristics
                val without = Tournament.Side(if (legacy) "legacy" else "without") { seed ->
                    HeuristicAi(strength, seed, tune) { id ->
                        if (id == hero) old else HeroPlaybooks.forHero(id)
                    }
                }
                val specs = all.mapIndexed { i, lineup ->
                    Tournament.Spec(lineup, if (i % 2 == 0) Team.RED else Team.BLUE, 50_000L + i)
                }
                val score = Tournament.play(withBook, without, specs, threads, format)
                val (lo, hi) = score.interval
                val line = "H_ABLATION $mode $hero strength $strength${if (legacy) " vs legacy" else ""} " +
                    "lineups $first+: $score Wilson ${"%.1f".format(lo * 100)}-${"%.1f".format(hi * 100)}%"
                println(line)
                println("H_ABLATION   ${score.details()}")
                System.getenv("AI_H_OUT")?.let { java.io.File(it).appendText(line + "\n") }
                assertThat(score.games).isGreaterThan(0)
            }
        }
    }

    /**
     * Opt-in. `AI_H_LEGACY_BOOK=1`: the whole current book against the whole legacy book ([LegacyPlaybooks]: the
     * playbooks wave 3 Track D changed, as they were) in mixed line-ups from `AI_H_FIRST`, `AI_H_GAMES` line-ups
     * mirrored, at `AI_H_STRENGTH` (default 10).
     */
    @Test
    fun `the current book against the legacy book`() {
        if (System.getenv("AI_H_LEGACY_BOOK") == null) return
        val strength = System.getenv("AI_H_STRENGTH")?.toIntOrNull() ?: 10
        val lineups = System.getenv("AI_H_GAMES")?.toIntOrNull() ?: 24
        val first = System.getenv("AI_H_FIRST")?.toIntOrNull() ?: 0
        val threads = System.getenv("AI_THREADS")?.toIntOrNull() ?: 2
        val format = AiHarness.Format.parse(System.getenv("AI_FORMAT"))
        val current = Tournament.Side("current") { seed -> HeuristicAi(strength, seed) }
        val legacy = Tournament.Side("legacy") { seed -> HeuristicAi(strength, seed) { LegacyPlaybooks.forHero(it) } }
        val score = Tournament.runMixed(current, legacy, lineups, threads, firstLineup = first, format = format)
        val (lo, hi) = score.interval
        val line = "H_BOOK strength $strength lineups $first+$lineups: $score Wilson " +
            "${"%.1f".format(lo * 100)}-${"%.1f".format(hi * 100)}%"
        println(line)
        println("H_BOOK   ${score.details()}")
        System.getenv("AI_H_OUT")?.let { java.io.File(it).appendText(line + "\n") }
        assertThat(score.games).isGreaterThan(0)
    }

    /**
     * `rules`: the playbook's notes with the default style; `style`: its style with no notes; `noeval`: no
     * [HeroHeuristics.evalTerms]; `nothreat`: no [HeroHeuristics.threatAdjust]; `noplay`: no card-play notes
     * (PLAY_CARD adjust and override); `nochoose`: no notes on any other question; else the whole book.
     */
    private fun variant(book: HeroHeuristics, mode: String): HeroHeuristics = when (mode) {
        "noeval" -> object : HeroHeuristics by book {
            override fun evalTerms(state: GameState, team: Team, heroId: String): Double = 0.0
        }

        "nothreat" -> object : HeroHeuristics by book {
            override fun threatAdjust(ctx: HContext, enemyHeroId: String, target: Hex): Double = 0.0
            override fun threatAdjust(ctx: HContext, enemyHeroId: String, target: Hex, victimId: String): Double =
                0.0
        }

        "noplay" -> object : HeroHeuristics by book {
            override fun override(ctx: HContext, d: Decision): Choice? =
                if (d.kind == DecisionKind.PLAY_CARD) null else book.override(ctx, d)
            override fun adjust(ctx: HContext, d: Decision, choice: Choice): Double =
                if (d.kind == DecisionKind.PLAY_CARD) 0.0 else book.adjust(ctx, d, choice)
        }

        "nochoose" -> object : HeroHeuristics by book {
            override fun override(ctx: HContext, d: Decision): Choice? =
                if (d.kind != DecisionKind.PLAY_CARD) null else book.override(ctx, d)
            override fun adjust(ctx: HContext, d: Decision, choice: Choice): Double =
                if (d.kind != DecisionKind.PLAY_CARD) 0.0 else book.adjust(ctx, d, choice)
        }

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
