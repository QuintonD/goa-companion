package dev.quinton.goa.core.game.ai.heuristic.heroes

import dev.quinton.goa.core.game.ai.heuristic.HeroHeuristics
import dev.quinton.goa.core.game.ai.heuristic.HeroRole
import dev.quinton.goa.core.game.ai.heuristic.HeroStyle

/**
 * Dodger: A ranged damager who farms and looks for a shot.
 * The general book plays the hero until this file grows a real note.
 */
object DodgerHeuristics : HeroHeuristics {
    override val heroId: String = "dodger"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.RANGED,
        idealDistance = 2,
        aggression = 0.66,
        laneWeight = 1.15,
        upgradeOrder = listOf("red", "blue"),
        keepDefender = true,
    )
}
