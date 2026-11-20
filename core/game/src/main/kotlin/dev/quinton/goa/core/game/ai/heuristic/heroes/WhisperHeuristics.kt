package dev.quinton.goa.core.game.ai.heuristic.heroes

import dev.quinton.goa.core.game.ai.heuristic.HeroHeuristics
import dev.quinton.goa.core.game.ai.heuristic.HeroRole
import dev.quinton.goa.core.game.ai.heuristic.HeroStyle

/**
 * Whisper: A damager who steps in at mid range.
 * The general book plays the hero until this file grows a real note.
 */
object WhisperHeuristics : HeroHeuristics {
    override val heroId: String = "whisper"

    override val style: HeroStyle = HeroStyle(
        role = HeroRole.FIGHTER,
        idealDistance = 2,
        aggression = 0.6,
        laneWeight = 1.1,
        upgradeOrder = listOf("red", "blue"),
        keepDefender = true,
    )
}
