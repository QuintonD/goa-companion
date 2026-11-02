package dev.quinton.goa.server.game.room

import com.google.common.truth.Truth.assertThat
import kotlin.random.Random
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

    @Test
    fun `leaving before answering anything leaves a game that isn't rated`() = runBlocking<Unit> {
        val game = TestGame()
        val setup = duel()
        game.start(setup, ranked = true)
        game.connectAll(setup)
        game.core.leave(TestGame.GAME_ID, TestGame.account("arien"))
        val wasp = game.inbox.getValue("wasp")
        val rng = Random(8)
        var answered = 0
        while (game.core.runningCount > 0) {
            val q = wasp.question("wasp")
            if (q != null) game.answer("wasp", RandomGames.choose(q, rng)) else game.tick()
            assertThat(++answered).isLessThan(TestGame.MAX_ANSWERS)
        }
        assertThat(wasp.ended!!.winner).isNotNull()
        assertThat(wasp.ended!!.rated).isFalse()
        assertThat(game.store.matches()).isEmpty()
    }

    @Test
    fun `who has answered survives a restart, and the end says what was decided`() = runBlocking<Unit> {
        val game = TestGame()
        val setup = duel()
        game.start(setup, ranked = true)
        game.connectAll(setup)
        game.answer("wasp", game.inbox.getValue("wasp").question("wasp")!!.options.first())
        game.answer("arien", game.inbox.getValue("arien").question("arien")!!.options.first())

        val again = TestGame(store = game.store, clock = game.clock)
        again.core.restore()
        again.connectAll(setup)
        val rng = Random(3)
        again.drive(choose = { RandomGames.choose(it, rng) })
        assertThat(again.inbox.getValue("wasp").ended!!.rated).isTrue()
    }

    @Test
    fun `a game that ended unheard is one to go back to but doesn't count as one being played`() = runBlocking<Unit> {
        val rules = RoomRules()
        val game = TestGame(rules = rules)
        game.start(duel())
        game.clock.advance(rules.abandonMillis + 1)
        game.tick()
        val wasp = TestGame.account("wasp")
        assertThat(game.core.gamesOf(wasp)).containsExactly(TestGame.GAME_ID)
        assertThat(game.core.runningOf(wasp)).isEmpty()
    }
}
