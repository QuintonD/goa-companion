package dev.quinton.goa.core.game.ai.heuristic.heroes

import dev.quinton.goa.core.model.board.Hex
import org.junit.Test

class WidgetHeuristicsTest {
    @Test
    fun `finishes 2v2 and 3v3 at strengths 5 and 10`() = BatchDTestSupport.finishes("widget")

    @Test
    fun `with Pyro off the board Silver brings it back before Breath`() {
        // H1/H4: Breath is measured from Pyro, and Pyro is not on the board.
        val session = BatchDTestSupport.playing(
            hero = "widget",
            at = Hex(1, -5),
            hand = listOf("widget-green-1", "widget-silver", "widget-red-1"),
            enemyAt = Hex(3, -6),
        )
        BatchDTestSupport.assertFlips(session, WidgetHeuristics, from = null, to = "widget-silver")
    }
}
