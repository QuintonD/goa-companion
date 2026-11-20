package dev.quinton.goa.core.game.ai.heuristic.heroes

import dev.quinton.goa.core.game.ai.heuristic.HeroHeuristics
import dev.quinton.goa.core.game.ai.heuristic.HeroRole
import dev.quinton.goa.core.game.ai.heuristic.HeroStyle

/**
 * Brogan: A durable front line who stands with friendly minions.
 * The general book plays the hero until this file grows a real note.
 */
object BroganHeuristics : HeroHeuristics {
    override val heroId: String = "brogan"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.TANK,
        idealDistance = 1,
        aggression = 0.38,
        laneWeight = 1.3,
        upgradeOrder = listOf("red", "blue"),
        keepDefender = true,
    )
}
