package dev.quinton.goa.core.game.ai

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.AiLevel
import org.junit.Test

/**
 * PRODUCT §9 "each level beats the one below it over a seeded tournament": mirrored games (every
 * game is played twice with the levels swapping teams; same seed, same coin) over line-ups drawn
 * from all playable heroes, plus the first-game heroes. Deterministic, so the numbers below are
 * stable until the AI or the rules change. Longer runs: `TournamentTest` (AI.md "Measured strength").
 */
class LevelLadderTest {
    private fun ladder(a: AiLevel, b: AiLevel, atLeast: Double) {
        val mixed = Tournament.runMixed(a, b, lineups = 12, threads = 6, firstLineup = FIRST_LINEUP)
        println("LADDER mixed $mixed")
        val first = Tournament.run(a, b, seeds = 1, threads = 6)
        println("LADDER first-game heroes $first")
        val rate = (mixed.aWins + first.aWins + 0.5 * (mixed.draws + first.draws)) / (mixed.games + first.games)
        assertThat(rate).isAtLeast(atLeast)
    }

    @Test
    fun `Normal beats Easy`() = ladder(AiLevel.NORMAL, AiLevel.EASY, atLeast = 0.75)

    @Test
    fun `Hard beats Normal`() = ladder(AiLevel.HARD, AiLevel.NORMAL, atLeast = 0.7)

    @Test
    fun `Expert beats Hard`() = ladder(AiLevel.EXPERT, AiLevel.HARD, atLeast = 0.55)

    companion object {
        /** Line-ups not used for tuning (those were 0–191). */
        private const val FIRST_LINEUP = 200
    }
}
