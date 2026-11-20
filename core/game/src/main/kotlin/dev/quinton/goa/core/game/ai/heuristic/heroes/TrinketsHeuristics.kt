package dev.quinton.goa.core.game.ai.heuristic.heroes

import dev.quinton.goa.core.game.ai.heuristic.HeroHeuristics
import dev.quinton.goa.core.game.ai.heuristic.HeroRole
import dev.quinton.goa.core.game.ai.heuristic.HeroStyle

/**
 * Trinkets: A ranged damager who sets tokens and shoots.
 * The general book plays the hero until this file grows a real note.
 */
object TrinketsHeuristics : HeroHeuristics {
    override val heroId: String = "trinkets"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.RANGED,
        idealDistance = 3,
        aggression = 0.6,
        laneWeight = 1.0,
        upgradeOrder = listOf("red", "blue"),
        keepDefender = true,
    )
}
