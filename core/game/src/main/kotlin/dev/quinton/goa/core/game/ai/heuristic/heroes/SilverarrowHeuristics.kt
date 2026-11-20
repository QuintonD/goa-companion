package dev.quinton.goa.core.game.ai.heuristic.heroes

import dev.quinton.goa.core.game.ai.heuristic.HeroHeuristics
import dev.quinton.goa.core.game.ai.heuristic.HeroRole
import dev.quinton.goa.core.game.ai.heuristic.HeroStyle

/**
 * Silverarrow: A sniper who keeps distance and still farms the wave.
 * The general book plays the hero until this file grows a real note.
 */
object SilverarrowHeuristics : HeroHeuristics {
    override val heroId: String = "silverarrow"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.RANGED,
        idealDistance = 3,
        aggression = 0.62,
        laneWeight = 1.1,
        upgradeOrder = listOf("red", "blue"),
        keepDefender = true,
    )
}
