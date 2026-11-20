package dev.quinton.goa.core.game.ai.heuristic.heroes

import dev.quinton.goa.core.game.ai.heuristic.HeroHeuristics
import dev.quinton.goa.core.game.ai.heuristic.HeroRole
import dev.quinton.goa.core.game.ai.heuristic.HeroStyle

/**
 * Xargatha: A durable disabler who stands among enemy minions.
 * The general book plays the hero until this file grows a real note.
 */
object XargathaHeuristics : HeroHeuristics {
    override val heroId: String = "xargatha"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.TANK,
        idealDistance = 1,
        aggression = 0.46,
        laneWeight = 1.2,
        upgradeOrder = listOf("green", "red"),
        keepDefender = true,
    )
}
