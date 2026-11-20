package dev.quinton.goa.core.game.ai.heuristic.heroes

import dev.quinton.goa.core.game.ai.heuristic.HeroHeuristics
import dev.quinton.goa.core.game.ai.heuristic.HeroRole
import dev.quinton.goa.core.game.ai.heuristic.HeroStyle

/**
 * Cutter: Aggressive melee who spends cards to finish heroes.
 * The general book plays the hero until this file grows a real note.
 */
object CutterHeuristics : HeroHeuristics {
    override val heroId: String = "cutter"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.ASSASSIN,
        idealDistance = 1,
        aggression = 0.72,
        laneWeight = 1.05,
        upgradeOrder = listOf("red", "gold"),
        keepDefender = false,
    )
}
