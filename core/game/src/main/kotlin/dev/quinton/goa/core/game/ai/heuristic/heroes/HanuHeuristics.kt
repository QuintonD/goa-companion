package dev.quinton.goa.core.game.ai.heuristic.heroes

import dev.quinton.goa.core.game.ai.heuristic.HeroHeuristics
import dev.quinton.goa.core.game.ai.heuristic.HeroRole
import dev.quinton.goa.core.game.ai.heuristic.HeroStyle

/**
 * Hanu: A sniper who wants a long lane and a clean shot.
 * The general book plays the hero until this file grows a real note.
 */
object HanuHeuristics : HeroHeuristics {
    override val heroId: String = "hanu"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.RANGED,
        idealDistance = 3,
        aggression = 0.52,
        laneWeight = 1.1,
        upgradeOrder = listOf("red", "blue"),
        keepDefender = true,
    )
}
