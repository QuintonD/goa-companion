package dev.quinton.goa.core.game.ai.heuristic.heroes

import dev.quinton.goa.core.game.ai.heuristic.HeroHeuristics
import dev.quinton.goa.core.game.ai.heuristic.HeroRole
import dev.quinton.goa.core.game.ai.heuristic.HeroStyle

/**
 * Tigerclaw: A melee assassin who looks for the kill, not the farm.
 * The general book plays the hero until this file grows a real note.
 */
object TigerclawHeuristics : HeroHeuristics {
    override val heroId: String = "tigerclaw"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.ASSASSIN,
        idealDistance = 1,
        aggression = 0.78,
        laneWeight = 1.0,
        upgradeOrder = listOf("red", "gold"),
        keepDefender = false,
    )
}
