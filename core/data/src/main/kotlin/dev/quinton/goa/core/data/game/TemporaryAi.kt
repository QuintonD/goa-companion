package dev.quinton.goa.core.data.game

import dev.quinton.goa.core.game.AiPlayer
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Coach
import dev.quinton.goa.core.game.CoachNote
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.GameSession
import dev.quinton.goa.core.game.SavedGame
import kotlin.random.Random

/**
 * Stand-in until core:game's AI levels land ([GameAiModule]): any legal option, from a seeded
 * random source, so a game and its tests replay the same. It never reads hidden information (it
 * looks only at the decision's options).
 */
class RandomLegalAi(seed: Long) : AiPlayer {
    private val random = Random(seed)

    override fun choose(session: GameSession, decision: Decision, cancelled: () -> Boolean): Choice =
        decision.options[random.nextInt(decision.options.size)]
}

/** Stand-in until core:game's Coach lands ([GameAiModule]): never nudges, finds no turning points. */
object SilentCoach : Coach {
    override fun nudge(session: GameSession, decision: Decision, choice: Choice, cancelled: () -> Boolean): CoachNote? =
        null

    override fun turningPoints(saved: SavedGame, seat: String, cancelled: () -> Boolean): List<CoachNote> = emptyList()
}
