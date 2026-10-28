package dev.quinton.goa.core.game.ai

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * PRODUCT §9 "each step beats the one below it over a seeded tournament" (ADR-039): mirrored games
 * (every game is played twice with the strengths swapping teams; same seed, same coin) over
 * line-ups drawn from all playable heroes, plus the first-game heroes. Deterministic, so the numbers
 * are stable until the AI or the rules change. A run this short cannot separate neighbouring steps
 * (AI.md "Measured strength"), so it plays the named levels' strengths against each other (and the
 * gentlest step against Easy), where the gaps are wide enough to guard against a regression;
 * `TournamentTest` measures every step.
 */
class LevelLadderTest {
    private fun ladder(a: Int, b: Int, atLeast: Double) {
        val mixed = Tournament.runMixed(Tournament.strength(a), Tournament.strength(b), 12, 4, FIRST_LINEUP)
        println("LADDER mixed $mixed")
        val first = Tournament.run(Tournament.strength(a), Tournament.strength(b), seeds = 1, threads = 4)
        println("LADDER first-game heroes $first")
        val rate = (mixed.aWins + first.aWins + 0.5 * (mixed.draws + first.draws)) / (mixed.games + first.games)
        assertThat(rate).isAtLeast(atLeast)
    }

    @Test
    fun `Easy (2) beats the gentlest (1)`() = ladder(2, 1, atLeast = 0.6)

    @Test
    fun `Normal (6) beats Easy (2)`() = ladder(6, 2, atLeast = 0.75)

    @Test
    fun `Hard (8) beats Normal (6)`() = ladder(8, 6, atLeast = 0.65)

    /** Expert over Hard (about 63%) is too close for 36 games to guard; over Normal it is not. */
    @Test
    fun `Expert (10) beats Normal (6)`() = ladder(10, 6, atLeast = 0.75)

    companion object {
        /** Line-ups not used for tuning (those were 0–191). */
        private const val FIRST_LINEUP = 200
    }
}
