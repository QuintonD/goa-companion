package dev.quinton.goa.core.game.ai.heuristic.heroes

import dev.quinton.goa.core.game.ai.heuristic.HeroHeuristics
import dev.quinton.goa.core.game.ai.heuristic.HeroRole
import dev.quinton.goa.core.game.ai.heuristic.HeroStyle

/**
 * Bain: A ranged healer who farms and shoots from a few spaces out.
 * The general book plays the hero until this file grows a real note.
 */
object BainHeuristics : HeroHeuristics {
    override val heroId: String = "bain"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.RANGED,
        idealDistance = 3,
        aggression = 0.42,
        laneWeight = 1.05,
        upgradeOrder = listOf("red", "blue"),
        keepDefender = true,
    )
}
