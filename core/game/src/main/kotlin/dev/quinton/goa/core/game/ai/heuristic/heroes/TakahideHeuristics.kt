package dev.quinton.goa.core.game.ai.heuristic.heroes

import dev.quinton.goa.core.game.ai.heuristic.HeroHeuristics
import dev.quinton.goa.core.game.ai.heuristic.HeroRole
import dev.quinton.goa.core.game.ai.heuristic.HeroStyle

/**
 * Takahide: Ranged and durable: a shot, and a way out.
 * The general book plays the hero until this file grows a real note.
 */
object TakahideHeuristics : HeroHeuristics {
    override val heroId: String = "takahide"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.RANGED,
        idealDistance = 2,
        aggression = 0.52,
        laneWeight = 1.1,
        upgradeOrder = listOf("red", "blue"),
        keepDefender = true,
    )
}
