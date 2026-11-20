package dev.quinton.goa.core.game.ai.heuristic.heroes

import dev.quinton.goa.core.model.board.Hex
import org.junit.Test

class WidgetHeuristicsTest {
    @Test fun finishes() = HeroBookSupport.finishes("widget")

    @Test fun signature() {
        val session = HeroBookSupport.playing(
            hero = "widget",
            at = Hex(-1, -2),
            hand = listOf("widget-green-1", "widget-silver", "widget-blue-1"),
            enemyAt = Hex(-1, -1),
        )
        // Breath is measured from Pyro. With Pyro off the board the engine fizzles it.
        HeroBookSupport.assertFlips(session, WidgetHeuristics, "widget-green-1")
    }
}
