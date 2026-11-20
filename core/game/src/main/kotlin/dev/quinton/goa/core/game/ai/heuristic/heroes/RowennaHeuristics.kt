package dev.quinton.goa.core.game.ai.heuristic.heroes

import dev.quinton.goa.core.game.ai.heuristic.HeroHeuristics
import dev.quinton.goa.core.game.ai.heuristic.HeroRole
import dev.quinton.goa.core.game.ai.heuristic.HeroStyle

/**
 * Rowenna: A front-line healer who holds the minion line.
 * The general book plays the hero until this file grows a real note.
 */
object RowennaHeuristics : HeroHeuristics {
    override val heroId: String = "rowenna"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.TANK,
        idealDistance = 1,
        aggression = 0.4,
        laneWeight = 1.25,
        upgradeOrder = listOf("red", "blue"),
        keepDefender = true,
    )
}
