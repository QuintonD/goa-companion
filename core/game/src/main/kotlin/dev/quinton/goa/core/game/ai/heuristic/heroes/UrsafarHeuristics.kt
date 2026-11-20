package dev.quinton.goa.core.game.ai.heuristic.heroes

import dev.quinton.goa.core.game.ai.heuristic.HeroHeuristics
import dev.quinton.goa.core.game.ai.heuristic.HeroRole
import dev.quinton.goa.core.game.ai.heuristic.HeroStyle

/**
 * Ursafar: A melee pusher who stands on the wave.
 * The general book plays the hero until this file grows a real note.
 */
object UrsafarHeuristics : HeroHeuristics {
    override val heroId: String = "ursafar"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.TANK,
        idealDistance = 1,
        aggression = 0.36,
        laneWeight = 1.3,
        upgradeOrder = listOf("red", "green"),
        keepDefender = true,
    )
}
