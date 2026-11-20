package dev.quinton.goa.core.game.ai.heuristic.heroes

import dev.quinton.goa.core.game.ai.heuristic.HeroHeuristics
import dev.quinton.goa.core.game.ai.heuristic.HeroRole
import dev.quinton.goa.core.game.ai.heuristic.HeroStyle

/**
 * Wasp: A disabler who plays from two spaces and keeps Blue.
 * The general book plays the hero until this file grows a real note.
 */
object WaspHeuristics : HeroHeuristics {
    override val heroId: String = "wasp"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.RANGED,
        idealDistance = 2,
        aggression = 0.55,
        laneWeight = 1.05,
        upgradeOrder = listOf("blue", "red"),
        keepDefender = true,
    )
}
