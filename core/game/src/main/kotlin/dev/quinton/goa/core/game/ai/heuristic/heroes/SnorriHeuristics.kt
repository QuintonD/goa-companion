package dev.quinton.goa.core.game.ai.heuristic.heroes

import dev.quinton.goa.core.game.ai.heuristic.HeroHeuristics
import dev.quinton.goa.core.game.ai.heuristic.HeroRole
import dev.quinton.goa.core.game.ai.heuristic.HeroStyle

/**
 * Snorri: A mid-range fighter who farms and keeps a defender.
 * The general book plays the hero until this file grows a real note.
 */
object SnorriHeuristics : HeroHeuristics {
    override val heroId: String = "snorri"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.RANGED,
        idealDistance = 2,
        aggression = 0.5,
        laneWeight = 1.15,
        upgradeOrder = listOf("blue", "red"),
        keepDefender = true,
    )
}
