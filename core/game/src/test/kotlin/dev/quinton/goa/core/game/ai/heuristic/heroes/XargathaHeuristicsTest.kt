package dev.quinton.goa.core.game.ai.heuristic.heroes

import dev.quinton.goa.core.model.board.Hex
import org.junit.Test

class XargathaHeuristicsTest {
    @Test fun finishes() = HeroBookSupport.finishes("xargatha")

    @Test fun signature() {
        val session = HeroBookSupport.playing(
            hero = "xargatha",
            at = Hex(-1, -2),
            hand = listOf("xargatha-silver", "xargatha-blue-1", "xargatha-red-1"),
            enemyAt = Hex(3, -3),
        )
        // H2: Siren's Call has no unit in range that is not already adjacent.
        HeroBookSupport.assertFlips(session, XargathaHeuristics, "xargatha-silver")
    }
}
