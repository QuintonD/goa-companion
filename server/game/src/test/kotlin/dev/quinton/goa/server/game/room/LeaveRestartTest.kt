package dev.quinton.goa.server.game.room

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test

class LeaveRestartTest {
    @Test
    fun `leaving survives a restart, so it can't dodge the result`() = runBlocking<Unit> {
        val game = TestGame()
        val setup = duel()
        game.start(setup, ranked = true)
        game.connectAll(setup)
        game.core.leave(TestGame.GAME_ID, TestGame.account("arien"))

        val again = TestGame(store = game.store, clock = game.clock)
        again.core.restore()
        assertThat(again.core.gamesOf(TestGame.account("arien"))).isEmpty()
        assertThat(again.core.connect(TestGame.GAME_ID, TestGame.account("arien"), 0, Inbox())).isNull()
        assertThat(again.core.gamesOf(TestGame.account("wasp"))).containsExactly(TestGame.GAME_ID)
    }
}
