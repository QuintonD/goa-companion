package dev.quinton.goa.core.game.ai.heuristic.heroes

import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

class TigerclawHeuristicsTest {
    @Test fun finishes() = HeroBookSupport.finishes("tigerclaw")

    @Test fun signature() {
        val session = HeroBookSupport.playing(
            hero = "tigerclaw",
            at = Hex(-1, -2),
            hand = listOf("tigerclaw-gold", "tigerclaw-green-1", "tigerclaw-blue-1"),
            enemyAt = Hex(3, -3),
        ) {
            minion(Team.BLUE, MinionType.MELEE, Hex(2, -3))
        }
        // H2: Gold's printed reach is 3, and the engine only attacks a unit he can dash through.
        HeroBookSupport.assertFlips(session, TigerclawHeuristics, "tigerclaw-gold")
    }
}
