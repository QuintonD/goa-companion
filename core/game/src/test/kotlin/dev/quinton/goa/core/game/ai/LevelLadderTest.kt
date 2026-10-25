package dev.quinton.goa.core.game.ai

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * PRODUCT §9 "each step beats the one below it over a seeded tournament" (ADR-039): mirrored games
 * (every game is played twice with the strengths swapping teams; same seed, same coin) over
 * line-ups drawn from all playable heroes, plus the first-game heroes. Deterministic, so the numbers
 * are stable until the AI or the rules change. A run this short cannot separate neighbouring steps
 * (their measured gaps are 55–90%, AI.md "Measured strength"), so it plays every second step, where
 * the gaps are wide enough to guard against a regression; `TournamentTest` measures every step.
 */
class LevelLadderTest {
    private fun ladder(a: Int, b: Int, atLeast: Double) {
        val mixed = Tournament.runMixed(Tournament.strength(a), Tournament.strength(b), 12, 6, FIRST_LINEUP)
        println("LADDER mixed $mixed")
        val first = Tournament.run(Tournament.strength(a), Tournament.strength(b), seeds = 1, threads = 6)
        println("LADDER first-game heroes $first")
        val rate = (mixed.aWins + first.aWins + 0.5 * (mixed.draws + first.draws)) / (mixed.games + first.games)
        assertThat(rate).isAtLeast(atLeast)
    }

    @Test
    fun `strength 3 beats strength 1`() = ladder(3, 1, atLeast = 0.6)

    @Test
    fun `Normal (4) beats Easy (2)`() = ladder(4, 2, atLeast = 0.75)

    @Test
    fun `Hard (6) beats Normal (4)`() = ladder(6, 4, atLeast = 0.7)

    @Test
    fun `Expert (8) beats Hard (6)`() = ladder(8, 6, atLeast = 0.55)

    @Test
    fun `strength 10 beats Expert (8)`() = ladder(10, 8, atLeast = 0.55)

    companion object {
        /** Line-ups not used for tuning (those were 0–191). */
        private const val FIRST_LINEUP = 200
    }
}
