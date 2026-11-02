package dev.quinton.goa.server.game.room

import dev.quinton.goa.core.game.AiPlayer
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.GameSession
import dev.quinton.goa.core.game.ai.AiPlayers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * The computer answering for a person (docs/ONLINE.md "Clocks"): strength 4 plays from static rules with
 * no search, so one answer stays bounded. It keeps state, so every room's runs on one [dispatcher].
 */
internal class Takeover(seed: Long, private val dispatcher: CoroutineDispatcher) {
    private val player: AiPlayer = AiPlayers.forStrength(STRENGTH, seed)

    /** [session] is a fork the caller keeps: the player needs an engine session and may read its hidden cards. */
    suspend fun answer(session: GameSession, question: Decision): Choice {
        val choice = withContext(dispatcher) { player.choose(session.fork(), question) }
        return if (choice in question.options) choice else question.options.first()
    }

    private companion object {
        const val STRENGTH = 4
    }
}
