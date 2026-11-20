package dev.quinton.goa.core.game.ai.heuristic.heroes

import dev.quinton.goa.core.game.ai.heuristic.HeroHeuristics
import dev.quinton.goa.core.game.ai.heuristic.HeroRole
import dev.quinton.goa.core.game.ai.heuristic.HeroStyle

/**
 * Arien: A durable pusher who holds the lane and keeps a Blue spare.
 * The general book plays the hero until this file grows a real note.
 */
object ArienHeuristics : HeroHeuristics {
    override val heroId: String = "arien"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.PUSHER,
        idealDistance = 1,
        aggression = 0.48,
        laneWeight = 1.35,
        upgradeOrder = listOf("red", "blue"),
        keepDefender = true,
    )
}
