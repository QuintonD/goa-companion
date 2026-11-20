package dev.quinton.goa.core.game.ai.heuristic.heroes

import dev.quinton.goa.core.game.ai.heuristic.HeroHeuristics
import dev.quinton.goa.core.game.ai.heuristic.HeroRole
import dev.quinton.goa.core.game.ai.heuristic.HeroStyle

/**
 * Tali: Support whose basic attack likes attack items.
 * The general book plays the hero until this file grows a real note.
 */
object TaliHeuristics : HeroHeuristics {
    override val heroId: String = "tali"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.SUPPORT,
        idealDistance = 2,
        aggression = 0.48,
        laneWeight = 1.15,
        upgradeOrder = listOf("red", "gold"),
        keepDefender = true,
    )
}
