package dev.quinton.goa.server.game.room

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.online.VoidReason
import dev.quinton.goa.server.game.store.InMemoryGameStore
import kotlinx.coroutines.runBlocking
import org.junit.Test

/** Being away is on disk: a restart neither wakes a paused game nor gives a forgotten one more time. */
class AwayTest {
    private val rules = RoomRules()
    private val store = InMemoryGameStore()
    private val clock = FakeClock()

    private fun game() = TestGame(store = store, clock = clock, rules = rules)

    /** A game that has been played a little, with everyone gone at this moment. */
    private fun everyoneGone(): TestGame {
        val game = game()
        game.start(duel())
        game.connectAll(duel())
        game.inbox.values.forEach { it.link?.close() }
        return game
    }

    private fun restart(): TestGame = game().also { runBlocking { it.core.restore() } }

    private suspend fun answers() = store.runningGames().single().answers

    @Test
    fun `a paused game comes back paused`() = runBlocking<Unit> {
        everyoneGone()
        clock.advance(rules.allAwayPauseMillis + 1)
        val again = restart()
        assertThat(again.core.runningCount).isEqualTo(1)
        clock.advance(rules.playCardMillis + 1)
        again.tick()
        assertThat(answers()).isEmpty()
        // Nor does a second restart wake it.
        val third = restart()
        clock.advance(rules.playCardMillis + 1)
        third.tick()
        assertThat(answers()).isEmpty()
    }

    @Test
    fun `coming back to a restored paused game starts the clocks from there`() = runBlocking<Unit> {
        everyoneGone()
        clock.advance(rules.allAwayPauseMillis + 1)
        val again = restart()
        clock.advance(rules.playCardMillis + 1)
        again.tick()
        val box = again.connect("wasp")
        assertThat(box.latest("wasp").clock!!.deadlineAt).isEqualTo(clock.now + rules.playCardMillis)
        assertThat(store.runningGames().single().game.awaySince).isNull()
        clock.advance(rules.playCardMillis - 1)
        again.tick()
        assertThat(answers().filter { it.record.seat == "wasp" }).isEmpty()
    }

    @Test
    fun `the 24 hours run on whatever restarts there are`() = runBlocking<Unit> {
        everyoneGone()
        clock.advance(rules.abandonMillis / 2)
        restart()
        clock.advance(rules.abandonMillis / 2 - 1)
        val before = restart()
        assertThat(before.core.runningCount).isEqualTo(1)
        clock.advance(1)
        val again = restart()
        assertThat(again.core.runningCount).isEqualTo(0)
        assertThat(store.runningGames()).isEmpty()
        val box = Inbox()
        again.core.connect(TestGame.GAME_ID, TestGame.account("wasp"), 0, box)
        assertThat(box.ended!!.voided).isEqualTo(VoidReason.ABANDONED)
        assertThat(box.ended!!.rated).isFalse()
    }

    @Test
    fun `a game nobody ever connected to keeps its first moment too`() = runBlocking<Unit> {
        val game = game()
        game.start(duel())
        val started = store.runningGames().single().game.awaySince
        assertThat(started).isEqualTo(clock.now)
        clock.advance(rules.abandonMillis)
        restart()
        assertThat(store.runningGames()).isEmpty()
    }

    @Test
    fun `away for less than the pause still pauses on time after a restart`() = runBlocking<Unit> {
        everyoneGone()
        clock.advance(rules.allAwayPauseMillis - 1_000)
        val again = restart()
        // The whole clock, as it wasn't paused; and past its pause by the time that runs out.
        clock.advance(rules.playCardMillis + 1)
        again.tick()
        assertThat(answers()).isEmpty()
    }

    @Test
    fun `not paused and with its clock whole, a restart lets the computer answer on expiry`() = runBlocking<Unit> {
        val game = game()
        game.start(duel())
        game.connectAll(duel())
        clock.advance(1_000)
        assertThat(store.runningGames().single().game.awaySince).isNull()
        val again = restart()
        clock.advance(rules.playCardMillis + 1)
        again.tick()
        assertThat(answers()).isNotEmpty()
    }

    @Test
    fun `with someone there when the server stopped, its restart starts the away clock, once`() = runBlocking<Unit> {
        val game = game()
        game.start(duel())
        game.connectAll(duel())
        clock.advance(1_000)
        restart()
        val since = store.runningGames().single().game.awaySince
        assertThat(since).isEqualTo(clock.now)
        clock.advance(rules.abandonMillis / 2)
        restart()
        assertThat(store.runningGames().single().game.awaySince).isEqualTo(since)
    }

    @Test
    fun `the last to go sets it and the first to come clears it`() = runBlocking<Unit> {
        val game = game()
        game.start(duel())
        game.connect("wasp")
        assertThat(store.runningGames().single().game.awaySince).isNull()
        val arien = game.connect("arien")
        arien.link?.close()
        assertThat(store.runningGames().single().game.awaySince).isNull()
        clock.advance(5)
        game.inbox.getValue("wasp").link?.close()
        assertThat(store.runningGames().single().game.awaySince).isEqualTo(clock.now)
        game.connect("arien")
        assertThat(store.runningGames().single().game.awaySince).isNull()
    }

    @Test
    fun `leaving as the last one there sets it too`() = runBlocking<Unit> {
        val game = game()
        game.start(firstGame())
        game.connect("wasp")
        game.core.leave(TestGame.GAME_ID, TestGame.account("wasp"))
        assertThat(store.runningGames().single().game.awaySince).isEqualTo(clock.now)
    }
}
