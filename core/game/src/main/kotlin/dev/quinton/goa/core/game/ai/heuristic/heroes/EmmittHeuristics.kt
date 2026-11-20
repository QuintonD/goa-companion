package dev.quinton.goa.core.game.ai.heuristic.heroes

import dev.quinton.goa.core.game.ai.heuristic.HeroHeuristics
import dev.quinton.goa.core.game.ai.heuristic.HeroRole
import dev.quinton.goa.core.game.ai.heuristic.HeroStyle

/**
 * Emmitt: A melee disabler who sets up and then stands in the fight.
 * The general book plays the hero until this file grows a real note.
 */
object EmmittHeuristics : HeroHeuristics {
    override val heroId: String = "emmitt"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.TANK,
        idealDistance = 1,
        aggression = 0.4,
        laneWeight = 1.0,
        upgradeOrder = listOf("green", "red"),
        keepDefender = true,
    )
}
