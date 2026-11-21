package dev.quinton.goa.core.game.ai.heuristic.heroes

import dev.quinton.goa.core.model.board.Hex
import org.junit.Test

class UrsafarHeuristicsTest {
    @Test
    fun `finishes 2v2 and 3v3 at strengths 5 and 10`() = BatchDTestSupport.finishes("ursafar")

    @Test
    fun `a calm bear does not lunge, and steps with Blue to get angry`() {
        // H4/H2: Gold's step toward a hero exists only once he is enraged; calm, Blue is the enrage.
        val session = BatchDTestSupport.playing(
            hero = "ursafar",
            at = Hex(1, -5),
            hand = listOf("ursafar-gold", "ursafar-green-1", "ursafar-blue-1"),
            enemyAt = Hex(3, -6),
        )
        BatchDTestSupport.assertFlips(session, UrsafarHeuristics, from = "ursafar-gold", to = "ursafar-blue-1")
    }
}
