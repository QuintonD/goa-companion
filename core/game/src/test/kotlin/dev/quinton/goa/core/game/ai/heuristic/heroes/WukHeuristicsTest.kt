package dev.quinton.goa.core.game.ai.heuristic.heroes

import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

class WukHeuristicsTest {
    @Test fun finishes() = HeroBookSupport.finishes("wuk")

    @Test fun signature() {
        val session = HeroBookSupport.playing(
            hero = "wuk",
            at = Hex(-1, -2),
            hand = listOf("wuk-gold", "wuk-silver", "wuk-blue-1"),
            enemyAt = Hex(3, -3),
        ) {
            minion(Team.BLUE, MinionType.MELEE, Hex(2, -3))
        }
        // Tree Slam has no adjacent minion and no tree, so neither bullet starts.
        HeroBookSupport.assertFlips(session, WukHeuristics, "wuk-gold")
    }
}
