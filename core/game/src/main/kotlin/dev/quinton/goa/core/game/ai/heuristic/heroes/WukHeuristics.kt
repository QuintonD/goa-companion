package dev.quinton.goa.core.game.ai.heuristic.heroes

import dev.quinton.goa.core.game.ai.heuristic.HeroHeuristics
import dev.quinton.goa.core.game.ai.heuristic.HeroRole
import dev.quinton.goa.core.game.ai.heuristic.HeroStyle

/**
 * Wuk: A support pusher who stays with the wave.
 * The general book plays the hero until this file grows a real note.
 */
object WukHeuristics : HeroHeuristics {
    override val heroId: String = "wuk"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.SUPPORT,
        idealDistance = 2,
        aggression = 0.42,
        laneWeight = 1.2,
        upgradeOrder = listOf("blue", "green"),
        keepDefender = true,
    )
}
