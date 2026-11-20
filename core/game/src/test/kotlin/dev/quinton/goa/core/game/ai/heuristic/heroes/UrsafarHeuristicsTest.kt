package dev.quinton.goa.core.game.ai.heuristic.heroes

import dev.quinton.goa.core.model.board.Hex
import org.junit.Test

class UrsafarHeuristicsTest {
    @Test fun finishes() = HeroBookSupport.finishes("ursafar")

    @Test fun signature() {
        val session = HeroBookSupport.playing(
            hero = "ursafar",
            at = Hex(-1, -2),
            hand = listOf("ursafar-green-1", "ursafar-blue-1", "ursafar-red-1"),
            enemyAt = Hex(3, -3),
        )
        // Green does nothing until he is enraged, and it does not enrage him.
        HeroBookSupport.assertFlips(session, UrsafarHeuristics, "ursafar-green-1")
    }
}
