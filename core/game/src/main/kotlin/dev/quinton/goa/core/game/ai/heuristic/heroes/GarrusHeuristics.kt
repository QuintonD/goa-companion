package dev.quinton.goa.core.game.ai.heuristic.heroes

import dev.quinton.goa.core.game.ai.heuristic.HeroHeuristics
import dev.quinton.goa.core.game.ai.heuristic.HeroRole
import dev.quinton.goa.core.game.ai.heuristic.HeroStyle

/**
 * Garrus: A durable disabler who wants a solid defense in the melee.
 * The general book plays the hero until this file grows a real note.
 */
object GarrusHeuristics : HeroHeuristics {
    override val heroId: String = "garrus"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.TANK,
        idealDistance = 1,
        aggression = 0.42,
        laneWeight = 1.05,
        upgradeOrder = listOf("blue", "red"),
        keepDefender = true,
    )
}
