package dev.quinton.goa.core.game.ai.heuristic.heroes

import dev.quinton.goa.core.game.ai.heuristic.HeroHeuristics
import dev.quinton.goa.core.game.ai.heuristic.HeroRole
import dev.quinton.goa.core.game.ai.heuristic.HeroStyle

/**
 * Brynn: A fighter who mixes a mid-range attack with staying alive.
 * The general book plays the hero until this file grows a real note.
 */
object BrynnHeuristics : HeroHeuristics {
    override val heroId: String = "brynn"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.FIGHTER,
        idealDistance = 2,
        aggression = 0.62,
        laneWeight = 1.05,
        upgradeOrder = listOf("red", "blue"),
        keepDefender = true,
    )
}
