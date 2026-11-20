package dev.quinton.goa.core.game.ai.heuristic.heroes

import dev.quinton.goa.core.game.ai.heuristic.HeroHeuristics
import dev.quinton.goa.core.game.ai.heuristic.HeroRole
import dev.quinton.goa.core.game.ai.heuristic.HeroStyle

/**
 * Min: Sets mines and traps, then hits from the next space.
 * The general book plays the hero until this file grows a real note.
 */
object MinHeuristics : HeroHeuristics {
    override val heroId: String = "min"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.SETUP,
        idealDistance = 1,
        aggression = 0.5,
        laneWeight = 1.0,
        upgradeOrder = listOf("green", "red"),
        keepDefender = true,
    )
}
