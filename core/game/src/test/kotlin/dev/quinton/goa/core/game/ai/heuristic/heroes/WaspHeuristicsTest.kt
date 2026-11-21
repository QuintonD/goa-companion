package dev.quinton.goa.core.game.ai.heuristic.heroes

import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

class WaspHeuristicsTest {
    @Test
    fun `finishes 2v2 and 3v3 at strengths 5 and 10`() = BatchDTestSupport.finishes("wasp")

    @Test
    fun `the boomerang cannot target her straight lines, so she cages instead`() {
        // H4: Charged Boomerang targets units in range and not in a straight line from her. Arien is adjacent
        // (a line) and the minion is on a line; H1: Arien is inside the dome's radius and Brogan is outside.
        val session = BatchDTestSupport.playing(
            hero = "wasp",
            at = Hex(1, -5),
            hand = listOf("wasp-red-2b", "wasp-silver", "wasp-green-1"),
            enemyAt = Hex(2, -6),
            mine = { level = 2 },
        ) {
            minion(Team.BLUE, MinionType.MELEE, Hex(3, -5))
            hero("brogan", Team.BLUE, Hex(4, -4))
        }
        BatchDTestSupport.assertFlips(session, WaspHeuristics, from = "wasp-red-2b", to = "wasp-silver")
    }
}
