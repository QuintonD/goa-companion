package dev.quinton.goa.core.game.ai.heuristic.heroes

import dev.quinton.goa.core.model.board.Hex
import org.junit.Test

class WhisperHeuristicsTest {
    @Test fun finishes() = HeroBookSupport.finishes("whisper")

    @Test fun signature() {
        val session = HeroBookSupport.playing(
            hero = "whisper",
            at = Hex(-1, -2),
            hand = listOf("whisper-gold", "whisper-silver", "whisper-blue-1"),
            enemyAt = Hex(2, -3),
            thrown = listOf("arien-green-1"),
        )
        // H3: the snipe needs an empty discard, and the melee bullet needs a neighbour.
        HeroBookSupport.assertFlips(session, WhisperHeuristics, "whisper-gold")
    }
}
