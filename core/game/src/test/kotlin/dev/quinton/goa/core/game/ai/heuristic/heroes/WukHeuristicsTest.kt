package dev.quinton.goa.core.game.ai.heuristic.heroes

import dev.quinton.goa.core.model.board.Hex
import org.junit.Test

class WukHeuristicsTest {
    @Test
    fun `finishes 2v2 and 3v3 at strengths 5 and 10`() = BatchDTestSupport.finishes("wuk")

    @Test
    fun `Tree Slam needs an adjacent minion or tree, so he plants first`() {
        // H3/H1: no minion beside him and no tree to spend; no trees on the board either.
        val session = BatchDTestSupport.playing(
            hero = "wuk",
            at = Hex(1, -5),
            hand = listOf("wuk-gold", "wuk-silver", "wuk-blue-1"),
            enemyAt = Hex(3, -6),
        )
        HeroBookCases.noteFavours(WukHeuristics, session, "wuk-silver", "wuk-gold")
    }
}
