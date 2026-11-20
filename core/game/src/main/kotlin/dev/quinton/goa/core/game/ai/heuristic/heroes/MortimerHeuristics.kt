package dev.quinton.goa.core.game.ai.heuristic.heroes

import dev.quinton.goa.core.game.ai.heuristic.HeroHeuristics
import dev.quinton.goa.core.game.ai.heuristic.HeroRole
import dev.quinton.goa.core.game.ai.heuristic.HeroStyle

/**
 * Mortimer: Raises pieces and farms from the minion line.
 * The general book plays the hero until this file grows a real note.
 */
object MortimerHeuristics : HeroHeuristics {
    override val heroId: String = "mortimer"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.SETUP,
        idealDistance = 1,
        aggression = 0.42,
        laneWeight = 1.15,
        upgradeOrder = listOf("green", "red"),
        keepDefender = true,
    )
}
