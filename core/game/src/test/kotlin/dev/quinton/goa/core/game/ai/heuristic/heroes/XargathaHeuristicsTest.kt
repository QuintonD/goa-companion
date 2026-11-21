package dev.quinton.goa.core.game.ai.heuristic.heroes

import dev.quinton.goa.core.model.board.Hex
import org.junit.Test

class XargathaHeuristicsTest {
    @Test
    fun `finishes 2v2 and 3v3 at strengths 5 and 10`() = BatchDTestSupport.finishes("xargatha")

    @Test
    fun `Siren's Call is not played with nobody to pull`() {
        // H2: the only enemy is already adjacent; the pull targets units that are not.
        val session = BatchDTestSupport.playing(
            hero = "xargatha",
            at = Hex(1, -5),
            hand = listOf("xargatha-silver", "xargatha-blue-1", "xargatha-green-1"),
            enemyAt = Hex(2, -6),
        )
        BatchDTestSupport.assertFlips(session, XargathaHeuristics, from = "xargatha-silver", to = null)
    }
}
