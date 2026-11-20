package dev.quinton.goa.core.game.ai.heuristic.heroes

import dev.quinton.goa.core.game.ai.heuristic.HeroHeuristics
import dev.quinton.goa.core.game.ai.heuristic.HeroRole
import dev.quinton.goa.core.game.ai.heuristic.HeroStyle

/**
 * Ignatia: A long-range damager who stays outside the melee.
 * The general book plays the hero until this file grows a real note.
 */
object IgnatiaHeuristics : HeroHeuristics {
    override val heroId: String = "ignatia"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.RANGED,
        idealDistance = 3,
        aggression = 0.6,
        laneWeight = 1.0,
        upgradeOrder = listOf("red", "blue"),
        keepDefender = true,
    )
}
