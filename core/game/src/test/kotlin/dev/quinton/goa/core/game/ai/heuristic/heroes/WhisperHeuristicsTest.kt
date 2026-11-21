package dev.quinton.goa.core.game.ai.heuristic.heroes

import dev.quinton.goa.core.model.board.Hex
import org.junit.Test

class WhisperHeuristicsTest {
    @Test
    fun `finishes 2v2 and 3v3 at strengths 5 and 10`() = BatchDTestSupport.finishes("whisper")

    @Test
    fun `Swift Justice does not snipe a hero who has already discarded`() {
        // H1/H3: the snipe needs an empty discard; Arien has thrown a card and is not adjacent.
        val session = BatchDTestSupport.playing(
            hero = "whisper",
            at = Hex(1, -5),
            hand = listOf("whisper-gold", "whisper-green-1", "whisper-blue-1"),
            enemyAt = Hex(4, -6),
            enemy = { discard("arien-red-1") },
        )
        BatchDTestSupport.assertFlips(session, WhisperHeuristics, from = "whisper-gold", to = null)
    }
}
