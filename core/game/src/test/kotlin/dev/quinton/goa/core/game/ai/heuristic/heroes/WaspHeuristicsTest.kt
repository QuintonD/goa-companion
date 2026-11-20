package dev.quinton.goa.core.game.ai.heuristic.heroes

import dev.quinton.goa.core.model.board.Hex
import org.junit.Test

class WaspHeuristicsTest {
    @Test fun finishes() = HeroBookSupport.finishes("wasp")

    @Test fun signature() {
        val session = HeroBookSupport.playing(
            hero = "wasp",
            at = Hex(-1, -2),
            hand = listOf("wasp-red-2b", "wasp-gold", "wasp-blue-1"),
            enemyAt = Hex(-1, -1),
            turnNumber = 3,
        )
        // Charged Boomerang cannot target a unit on a straight line, and adjacent is one.
        HeroBookSupport.assertFlips(session, WaspHeuristics, "wasp-red-2b")
    }
}
