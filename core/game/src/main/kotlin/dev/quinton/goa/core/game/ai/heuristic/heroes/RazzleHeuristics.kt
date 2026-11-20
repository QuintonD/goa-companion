package dev.quinton.goa.core.game.ai.heuristic.heroes

import dev.quinton.goa.core.game.ai.heuristic.HeroHeuristics
import dev.quinton.goa.core.game.ai.heuristic.HeroRole
import dev.quinton.goa.core.game.ai.heuristic.HeroStyle

/**
 * Razzle: A melee trickster who wants to be in the enemy's face.
 * The general book plays the hero until this file grows a real note.
 */
object RazzleHeuristics : HeroHeuristics {
    override val heroId: String = "razzle"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.ASSASSIN,
        idealDistance = 1,
        aggression = 0.7,
        laneWeight = 0.95,
        upgradeOrder = listOf("red", "gold"),
        keepDefender = false,
    )
}
