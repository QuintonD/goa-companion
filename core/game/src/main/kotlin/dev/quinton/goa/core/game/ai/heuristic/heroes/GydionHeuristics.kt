package dev.quinton.goa.core.game.ai.heuristic.heroes

import dev.quinton.goa.core.game.ai.heuristic.HeroHeuristics
import dev.quinton.goa.core.game.ai.heuristic.HeroRole
import dev.quinton.goa.core.game.ai.heuristic.HeroStyle

/**
 * Gydion: A spellcaster who sets the book up before he commits.
 * The general book plays the hero until this file grows a real note.
 */
object GydionHeuristics : HeroHeuristics {
    override val heroId: String = "gydion"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.SETUP,
        idealDistance = 2,
        aggression = 0.45,
        laneWeight = 1.0,
        upgradeOrder = listOf("blue", "green"),
        keepDefender = true,
    )
}
