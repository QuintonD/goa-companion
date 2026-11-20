package dev.quinton.goa.core.game.ai.heuristic.heroes

import dev.quinton.goa.core.game.ai.heuristic.HeroHeuristics
import dev.quinton.goa.core.game.ai.heuristic.HeroRole
import dev.quinton.goa.core.game.ai.heuristic.HeroStyle

/**
 * Sabina: A lane pusher who weights the Battle Zone above a chase.
 * The general book plays the hero until this file grows a real note.
 */
object SabinaHeuristics : HeroHeuristics {
    override val heroId: String = "sabina"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.PUSHER,
        idealDistance = 1,
        aggression = 0.5,
        laneWeight = 1.4,
        upgradeOrder = listOf("blue", "red"),
        keepDefender = true,
    )
}
