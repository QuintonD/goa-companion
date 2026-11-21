package dev.quinton.goa.core.game.ai.heuristic.heroes

import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

class TrinketsHeuristicsTest {
    @Test
    fun `finishes 2v2 and 3v3 at strengths 5 and 10`() = BatchDTestSupport.finishes("trinkets")

    @Test
    fun `with no Turret the gun has no target and Gold rebuilds it`() {
        // H1/H3: Makeshift Minigun targets units in range of him and of the Turret; there is no Turret.
        val session = BatchDTestSupport.playing(
            hero = "trinkets",
            at = Hex(1, -5),
            hand = listOf("trinkets-red-1", "trinkets-gold", "trinkets-green-1"),
            enemyAt = Hex(4, -6),
        ) {
            minion(Team.BLUE, MinionType.MELEE, Hex(2, -6))
        }
        BatchDTestSupport.assertFlips(session, TrinketsHeuristics, from = "trinkets-red-1", to = "trinkets-gold")
    }
}
