package dev.quinton.goa.core.game.ai.heuristic.heroes

import dev.quinton.goa.core.game.ai.heuristic.HeroHeuristics
import dev.quinton.goa.core.game.ai.heuristic.HeroRole
import dev.quinton.goa.core.game.ai.heuristic.HeroStyle

/**
 * Mrak: A melee wall who keeps a defender and stands in the zone.
 * The general book plays the hero until this file grows a real note.
 */
object MrakHeuristics : HeroHeuristics {
    override val heroId: String = "mrak"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.TANK,
        idealDistance = 1,
        aggression = 0.36,
        laneWeight = 1.0,
        upgradeOrder = listOf("green", "red"),
        keepDefender = true,
    )
}
