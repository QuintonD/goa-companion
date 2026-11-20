package dev.quinton.goa.core.game.ai.heuristic.heroes

import dev.quinton.goa.core.game.ai.heuristic.HeroHeuristics
import dev.quinton.goa.core.game.ai.heuristic.HeroRole
import dev.quinton.goa.core.game.ai.heuristic.HeroStyle

/**
 * Nebkher: A ranged disabler who plays from outside the fight.
 * The general book plays the hero until this file grows a real note.
 */
object NebkherHeuristics : HeroHeuristics {
    override val heroId: String = "nebkher"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.RANGED,
        idealDistance = 3,
        aggression = 0.5,
        laneWeight = 1.0,
        upgradeOrder = listOf("blue", "red"),
        keepDefender = true,
    )
}
