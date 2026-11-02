package dev.quinton.goa.server.game.room

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.online.ServerMessage
import dev.quinton.goa.server.game.store.ClosedGame
import dev.quinton.goa.server.game.store.GameStore
import dev.quinton.goa.server.game.store.InMemoryGameStore
import kotlin.random.Random
import kotlinx.coroutines.runBlocking
import org.junit.Test

/** Retention deletes a finished game's answers after 30 days, and they must not replay as a fresh game. */
class PrunedGameTest {
    private class Pruned(private val inner: GameStore) : GameStore by inner {
        override suspend fun closedGame(gameId: String): ClosedGame? = inner.closedGame(gameId)?.let {
            it.copy(game = it.game.copy(answers = emptyList()))
        }
    }

    @Test
    fun `a finished game whose answers have gone has no table to show`() = runBlocking<Unit> {
        val store = InMemoryGameStore()
        val game = TestGame(store = store)
        val setup = duel()
        game.start(setup, ranked = true)
        game.connectAll(setup)
        game.core.leave(TestGame.GAME_ID, TestGame.account("arien"))
        val wasp = game.inbox.getValue("wasp")
        val rng = Random(8)
        while (game.core.runningCount > 0) {
            val question = wasp.question("wasp")
            if (question != null) game.answer("wasp", RandomGames.choose(question, rng)) else game.tick()
        }
        assertThat(store.closedGame(TestGame.GAME_ID)?.game?.answers).isNotEmpty()

        val later = TestGame(store = Pruned(store), clock = game.clock)
        val frames = ArrayList<ServerMessage>()
        val link = later.core.connect(TestGame.GAME_ID, TestGame.account("wasp"), 0, SeatSink { frames += it })
        assertThat(link).isNull()
        assertThat(frames).isEmpty()
    }
}
