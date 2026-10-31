package dev.quinton.goa.core.game.ai

import com.google.common.truth.Truth.assertThat
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
}
