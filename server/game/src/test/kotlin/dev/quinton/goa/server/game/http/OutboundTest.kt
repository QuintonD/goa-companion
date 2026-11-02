package dev.quinton.goa.server.game.http

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.core.game.GameView
import dev.quinton.goa.core.online.ServerMessage
import dev.quinton.goa.server.game.store.StoreFixtures
import dev.quinton.goa.server.game.testEngine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Test

class OutboundTest {
    private val view: GameView by lazy { testEngine.newGame(StoreFixtures.setup).view(null) }

    private fun event(n: Int) = GameEvent.RoundStarted(n, "event $n")

    private fun table(game: String, seq: Long, from: Int, vararg events: Int) =
        ServerMessage.Table(game, seq, view, null, from, events.map(::event))

    private fun ack(ticket: Long) = ServerMessage.Ack("a", ticket, accepted = true)

    private suspend fun Outbound.drain(count: Int) = List(count) { requireNotNull(next()) }

    @Test
    fun onlyTheLatestTableOfEachGameIsKeptAndEveryEventWithIt(): Unit = runBlocking {
        val outbound = Outbound()
        outbound.offer(table("a", 1, 0, 0, 1))
        outbound.offer(table("b", 1, 0, 0))
        outbound.offer(table("a", 2, 2, 2))
        outbound.offer(table("a", 3, 3))
        outbound.offer(table("a", 4, 3, 3, 4))

        val sent = outbound.drain(2).map { it as ServerMessage.Table }
        assertThat(sent.map { it.gameId to it.seq }).containsExactly("b" to 1L, "a" to 4L).inOrder()
        assertThat(sent.last().eventsFrom).isEqualTo(0)
        assertThat(sent.last().events).isEqualTo((0..4).map(::event))
    }

    @Test
    fun otherMessagesStayInOrderAroundTheTable(): Unit = runBlocking {
        val outbound = Outbound()
        outbound.offer(ack(1))
        outbound.offer(table("a", 1, 0, 0))
        outbound.offer(ack(2))
        outbound.offer(table("a", 2, 1, 1))
        outbound.offer(ack(3))

        val sent = outbound.drain(4)
        assertThat(sent.map { (it as? ServerMessage.Ack)?.ticket ?: -1L })
            .containsExactly(1L, 2L, -1L, 3L).inOrder()
        assertThat((sent[2] as ServerMessage.Table).seq).isEqualTo(2)
    }

    @Test
    fun aNewTableOverlappingTheOldOneRepeatsNoEvent() {
        val merged = Outbound.merged(table("a", 1, 0, 0, 1, 2), table("a", 2, 1, 1, 2, 3))
        assertThat(merged.eventsFrom).isEqualTo(0)
        assertThat(merged.events).isEqualTo((0..3).map(::event))
    }

    @Test
    fun aNewTableFromTheBeginningReplacesTheOldEvents() {
        val merged = Outbound.merged(table("a", 1, 2, 2), table("a", 2, 0, 0, 1, 2))
        assertThat(merged.eventsFrom).isEqualTo(0)
        assertThat(merged.events).isEqualTo((0..2).map(::event))
    }

    @Test
    fun tooManyMessagesShutTheQueueRatherThanGrow(): Unit = runBlocking {
        val outbound = Outbound(maxMessages = 3)
        repeat(4) { outbound.offer(ack(it.toLong())) }
        assertThat(outbound.next()).isNull()
    }

    @Test
    fun fallingTooFarBehindShutsTheQueue(): Unit = runBlocking {
        val outbound = Outbound(maxEvents = 2)
        outbound.offer(table("a", 1, 0, 0, 1))
        outbound.offer(table("a", 2, 2, 2, 3))
        outbound.offer(table("a", 3, 4, 4))
        assertThat(outbound.next()).isNull()
    }

    @Test
    fun aCatchUpIsSentInPagesHoweverLongItIs(): Unit = runBlocking {
        val outbound = Outbound(maxEvents = 3, pageEvents = 2)
        outbound.offer(table("a", 1, 0, *(0 until 7).toList().toIntArray()))
        val sent = outbound.drain(4).map { it as ServerMessage.Table }
        assertThat(sent.map { it.eventsFrom }).containsExactly(0, 2, 4, 6).inOrder()
        assertThat(sent.map { it.more }).containsExactly(true, true, true, false).inOrder()
        assertThat(sent.flatMap { it.events }).isEqualTo((0 until 7).map(::event))
    }

    @Test
    fun whatArrivesDuringACatchUpJoinsItsEnd(): Unit = runBlocking {
        val outbound = Outbound(maxEvents = 3, pageEvents = 2)
        outbound.offer(table("a", 1, 0, 0, 1, 2, 3, 4))
        val first = outbound.next() as ServerMessage.Table
        assertThat(first.more).isTrue()
        outbound.offer(ack(9))
        outbound.offer(table("a", 2, 5, 5))
        val sent = outbound.drain(3)
        assertThat(sent.first()).isEqualTo(ack(9))
        val tables = sent.drop(1).map { it as ServerMessage.Table }
        assertThat(tables.map { it.more }).containsExactly(true, false).inOrder()
        assertThat(first.events + tables.flatMap { it.events }).isEqualTo((0..5).map(::event))
        // Every frame carries the table as it stands by then.
        assertThat(tables.map { it.seq }).containsExactly(2L, 2L)
    }

    @Test
    fun whatIsOfferedAfterShuttingIsDropped(): Unit = runBlocking {
        val outbound = Outbound()
        outbound.shut()
        outbound.offer(ack(1))
        assertThat(outbound.next()).isNull()
    }

    /** The socket is stuck on its first frame: the room keeps going, and what it missed arrives as one table. */
    @Test
    fun aStuckSocketDoesNotHoldUpOffersAndCatchesUpWithOneTable(): Unit = runBlocking {
        val outbound = Outbound()
        val gate = CompletableDeferred<Unit>()
        val sent = ArrayList<ServerMessage>()
        val pumping = async {
            pump(outbound) { message ->
                sent += message
                gate.await()
            }
        }
        outbound.offer(table("a", 1, 0, 0))
        while (sent.isEmpty()) yield()

        // Every one of these returns with the socket stuck.
        for (seq in 2L..500L) outbound.offer(table("a", seq, (seq - 1).toInt(), (seq - 1).toInt()))
        outbound.offer(ack(7))
        gate.complete(Unit)
        while (sent.size < 3) yield()
        outbound.shut()
        pumping.await()

        assertThat(sent).hasSize(3)
        val caughtUp = sent[1] as ServerMessage.Table
        assertThat(caughtUp.seq).isEqualTo(500)
        assertThat(caughtUp.eventsFrom).isEqualTo(1)
        assertThat(caughtUp.events).isEqualTo((1..499).map(::event))
        assertThat(sent[2]).isEqualTo(ack(7))
    }
}
