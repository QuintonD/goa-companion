package dev.quinton.goa.core.game.ai

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

class TournamentStatsTest {
    @Test
    fun `the test decides on a clear result and waits on an even one`() {
        val sprt = Tournament.Sprt(0.55)
        assertThat(sprt.verdict(80, 40)).isEqualTo(Tournament.Sprt.Verdict.H1)
        assertThat(sprt.verdict(30, 30)).isEqualTo(Tournament.Sprt.Verdict.GOING_ON)
        assertThat(sprt.verdict(400, 400)).isEqualTo(Tournament.Sprt.Verdict.H0)
    }

    @Test
    fun `the uplift cancels how strong a hero is`() {
        val score = Tournament.Score(Tournament.strength(1), Tournament.strength(2))
        // Brogan wins 3 of 4 for whoever holds it: no gain. Wasp wins 3 of 4 for a, 1 of 4 for b.
        repeat(3) { score.countHeroes(listOf("brogan"), listOf("wasp"), aRed = true, aWon = true) }
        score.countHeroes(listOf("brogan"), listOf("wasp"), aRed = true, aWon = false)
        repeat(3) { score.countHeroes(listOf("brogan"), listOf("wasp"), aRed = false, aWon = false) }
        score.countHeroes(listOf("brogan"), listOf("wasp"), aRed = false, aWon = true)
        val byHero = Tournament.uplifts(score).associateBy { it.hero }
        assertThat(byHero.getValue("brogan").gain).isWithin(1e-9).of(0.0)
        assertThat(byHero.getValue("wasp").gain).isWithin(1e-9).of(0.0)

        val hers = Tournament.Score(Tournament.strength(1), Tournament.strength(2))
        repeat(3) { hers.countHeroes(listOf("wasp"), listOf("min"), aRed = true, aWon = true) }
        hers.countHeroes(listOf("wasp"), listOf("min"), aRed = true, aWon = false)
        repeat(3) { hers.countHeroes(listOf("wasp"), listOf("min"), aRed = false, aWon = true) }
        hers.countHeroes(listOf("wasp"), listOf("min"), aRed = false, aWon = false)
        assertThat(Tournament.uplifts(hers).associateBy { it.hero }.getValue("wasp").gain).isWithin(1e-9).of(0.5)
    }

    @Test
    fun `pairs that split cost no evidence and narrow the interval`() {
        val r = AiHarness.play(
            AiHarness.setup(listOf("brogan"), listOf("wasp"), AiLevel.NORMAL, AiLevel.NORMAL, Team.RED),
            emptyMap(),
            maxDecisions = 0,
        )
        val lineup = listOf("brogan") to listOf("wasp")

        fun score(sweeps: Int, splits: Int, losses: Int) =
            Tournament.Score(Tournament.strength(1), Tournament.strength(2)).apply {
                repeat(sweeps) {
                    record(lineup, aRed = true, aWon = true, r = r, ms = 0)
                    record(lineup, aRed = false, aWon = true, r = r, ms = 0)
                }
                repeat(splits) {
                    record(lineup, aRed = true, aWon = true, r = r, ms = 0)
                    record(lineup, aRed = false, aWon = false, r = r, ms = 0)
                }
                repeat(losses) {
                    record(lineup, aRed = true, aWon = false, r = r, ms = 0)
                    record(lineup, aRed = false, aWon = false, r = r, ms = 0)
                }
            }
        val s = score(sweeps = 20, splits = 40, losses = 6)
        assertThat(s.pairs.toList()).isEqualTo(listOf(6, 0, 40, 0, 20))
        val (plo, phi) = s.pairedInterval
        val (lo, hi) = s.interval
        assertThat(phi - plo).isLessThan(hi - lo)
        // 80-52 by games is not yet decided; by pairs, where 40 split 1-1, it is.
        val sprt = Tournament.Sprt(0.55)
        assertThat(sprt.verdict(s.aWins, s.bWins)).isEqualTo(Tournament.Sprt.Verdict.GOING_ON)
        assertThat(sprt.verdict(s)).isEqualTo(Tournament.Sprt.Verdict.H1)
    }
}
