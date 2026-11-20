package dev.quinton.goa.core.game.ai.heuristic.heroes

import dev.quinton.goa.core.game.ai.heuristic.HeroHeuristics
import dev.quinton.goa.core.game.ai.heuristic.HeroRole
import dev.quinton.goa.core.game.ai.heuristic.HeroStyle

/**
 * Widget: A pusher who wants the lane and a primary that moves it.
 * The general book plays the hero until this file grows a real note.
 */
object WidgetHeuristics : HeroHeuristics {
    override val heroId: String = "widget"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.PUSHER,
        idealDistance = 1,
        aggression = 0.55,
        laneWeight = 1.2,
        upgradeOrder = listOf("red", "blue"),
        keepDefender = true,
    )
}
