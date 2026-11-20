package dev.quinton.goa.core.game.ai.heuristic.heroes

import dev.quinton.goa.core.game.ai.heuristic.HeroHeuristics
import dev.quinton.goa.core.game.ai.heuristic.HeroRole
import dev.quinton.goa.core.game.ai.heuristic.HeroStyle

/**
 * Swift: A long-range farmer who shoots and repositions.
 * The general book plays the hero until this file grows a real note.
 */
object SwiftHeuristics : HeroHeuristics {
    override val heroId: String = "swift"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.RANGED,
        idealDistance = 3,
        aggression = 0.55,
        laneWeight = 1.1,
        upgradeOrder = listOf("red", "blue"),
        keepDefender = true,
    )
}
