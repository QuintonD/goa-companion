package dev.quinton.goa.core.game.ai

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.AiLevel
import org.junit.Test

/**
 * PRODUCT §9 "each level beats the one below it over a seeded tournament": mirrored line-ups (every
 * game is played twice with the levels swapping teams; same seed, same coin), all three ways to split
 * the four playable heroes, both coin sides. Deterministic, so the numbers below are stable until the
 * AI or the rules change. Longer runs: `TournamentTest` (AI.md "Measured strength").
 */
class LevelLadderTest {
    private fun ladder(a: AiLevel, b: AiLevel, seeds: Int, atLeast: Double) {
        val score = Tournament.run(a, b, seeds, threads = 6)
        println("LADDER $score")
        assertThat(score.rate).isAtLeast(atLeast)
    }

    @Test
    fun `Normal beats Easy`() = ladder(AiLevel.NORMAL, AiLevel.EASY, seeds = 2, atLeast = 0.75)

    @Test
    fun `Hard beats Normal`() = ladder(AiLevel.HARD, AiLevel.NORMAL, seeds = 2, atLeast = 0.7)

    @Test
    fun `Expert beats Hard`() = ladder(AiLevel.EXPERT, AiLevel.HARD, seeds = 2, atLeast = 0.55)
}
