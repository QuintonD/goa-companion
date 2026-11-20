package dev.quinton.goa.core.game.ai.heuristic.heroes

import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

class TrinketsHeuristicsTest {
    @Test fun finishes() = HeroBookSupport.finishes("trinkets")

    @Test fun signature() {
        val session = HeroBookSupport.playing(
            hero = "trinkets",
            at = Hex(-1, -2),
            hand = listOf("trinkets-red-1", "trinkets-gold", "trinkets-blue-1"),
            enemyAt = Hex(3, -3),
        ) {
            minion(Team.BLUE, MinionType.MELEE, Hex(-1, -1))
        }
        // H3: the gun fizzles until the Turret is on the board.
        HeroBookSupport.assertFlips(session, TrinketsHeuristics, "trinkets-red-1")
    }
}
