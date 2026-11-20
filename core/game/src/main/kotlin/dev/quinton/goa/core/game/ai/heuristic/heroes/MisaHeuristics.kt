package dev.quinton.goa.core.game.ai.heuristic.heroes

import dev.quinton.goa.core.game.ai.heuristic.HeroHeuristics
import dev.quinton.goa.core.game.ai.heuristic.HeroRole
import dev.quinton.goa.core.game.ai.heuristic.HeroStyle

/**
 * Misa: A durable fighter who walks in and trades.
 * The general book plays the hero until this file grows a real note.
 */
object MisaHeuristics : HeroHeuristics {
    override val heroId: String = "misa"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.FIGHTER,
        idealDistance = 1,
        aggression = 0.58,
        laneWeight = 1.05,
        upgradeOrder = listOf("red", "blue"),
        keepDefender = true,
    )
}
