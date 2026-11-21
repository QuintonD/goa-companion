package dev.quinton.goa.core.game.ai.heuristic.heroes

import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

class TigerclawHeuristicsTest {
    @Test
    fun `finishes 2v2 and 3v3 at strengths 5 and 10`() = BatchDTestSupport.finishes("tigerclaw")

    @Test
    fun `Blink Strike is not played at a unit he cannot dash through`() {
        // H1/H2: the minion is two away, not beside him, so Gold has no line; printed reach says 3.
        val session = BatchDTestSupport.playing(
            hero = "tigerclaw",
            at = Hex(1, -5),
            hand = listOf("tigerclaw-gold", "tigerclaw-green-1", "tigerclaw-blue-1"),
            enemyAt = Hex(4, -6),
        ) {
            minion(Team.BLUE, MinionType.MELEE, Hex(3, -6))
        }
        BatchDTestSupport.assertFlips(session, TigerclawHeuristics, from = "tigerclaw-gold", to = null)
    }
}
