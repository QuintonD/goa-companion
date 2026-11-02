package dev.quinton.goa.feature.board.play.online

import dev.quinton.goa.core.online.ClientMessage
import dev.quinton.goa.core.online.DecisionClock
import dev.quinton.goa.core.online.Refusal
import dev.quinton.goa.core.online.Resume
import dev.quinton.goa.core.online.ServerMessage
import dev.quinton.goa.feature.board.play.game.GameAction
import dev.quinton.goa.feature.board.play.game.GameUiState
import dev.quinton.goa.feature.board.play.game.NoGame
import dev.quinton.goa.feature.board.play.game.TurnStatus
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException

/** The driver and its socket: dropping, coming back, being refused, the clock. */
@OptIn(ExperimentalCoroutinesApi::class)
class OnlineConnectionTest {
    // ---- Reconnecting --------------------------------------------------------------------------------

    @Test
    fun theFirstHelloCarriesTheBuildAndTheCatalog() = runTest {
        val rig = rig()
        val hello = rig.connector.hellos.single()
        assertEquals("secret-token", hello.token)
        assertEquals("test/1", hello.client)
        assertEquals(OnlineFixture.catalog.dataVersion, hello.catalogVersion)
        assertEquals(listOf(Resume(FakeServer.GAME, 0)), hello.resume)
    }

    @Test
    fun aDroppedConnectionKeepsTheTableAndReconnectsWithHaveEvents() = runTest {
        val rig = rig()
        val server = rig.server
        val held = rig.play.log.size
        val hand = rig.play.table.heroView("wasp")?.hand
        rig.connector.last.close()
        runCurrent()

        val state = assertIs<OnlineState.Reconnecting>(rig.driver.state.value)
        val play = assertIs<GameUiState.Playing>(state.game).play
        assertEquals(hand, play.table.heroView("wasp")?.hand)
        assertFalse(play.table.asking, "no answering a table that may be out of date")
        assertTrue(play.busy)
        rig.driver.onAction(GameAction.Answer(checkNotNull(play.table.decision).firstCard()))
        runCurrent()
        assertTrue(rig.answers.isEmpty())

        server.answerFirst()
        advanceTimeBy(BACKOFF_FIRST)
        runCurrent()
        assertEquals(listOf(Resume(FakeServer.GAME, held)), rig.connector.hellos.last().resume)
        assertIs<OnlineState.InGame>(rig.driver.state.value)
        assertEquals(server.session.events.map { it.text }, rig.play.log.map { it.text })
    }

    @Test
    fun theBackoffDoublesToThirtySecondsWithJitter() {
        val random = Random(3)
        repeat(50) {
            assertTrue(reconnectDelay(0, random) in 750L..1_000L)
            assertTrue(reconnectDelay(1, random) in 1_500L..2_000L)
            assertTrue(reconnectDelay(4, random) in 12_000L..16_000L)
            assertTrue(reconnectDelay(20, random) in 22_500L..30_000L)
        }
        val many = List(50) { reconnectDelay(6, random) }.toSet()
        assertTrue(many.size > 1, "everyone dropped together must not come back together")
    }

    @Test
    fun anUnreachableServerSaysSoAndKeepsTrying() = runTest {
        val rig = DriverRig(this, "wasp", FakeServer())
        rig.connector.unreachable = true
        rig.open()
        val state = assertIs<OnlineState.Connecting>(rig.driver.state.value)
        assertEquals(OnlineWords.UNREACHABLE, state.note)
        assertEquals(1, rig.connector.hellos.size)
        advanceTimeBy(BACKOFF_FIRST)
        runCurrent()
        assertEquals(2, rig.connector.hellos.size)
        rig.connector.unreachable = false
        advanceTimeBy(BACKOFF_SECOND)
        runCurrent()
        assertIs<OnlineState.InGame>(rig.driver.state.value)
    }

    @Test
    fun resumeNowDoesNotWaitOutTheBackoff() = runTest {
        val rig = DriverRig(this, "wasp", FakeServer())
        rig.connector.unreachable = true
        rig.open()
        rig.connector.unreachable = false
        rig.driver.resumeNow()
        runCurrent()
        assertEquals(2, rig.connector.hellos.size)
        assertIs<OnlineState.InGame>(rig.driver.state.value)
    }

    @Test
    fun resumeNowTrustsARecentSocketAndNotAnOldOne() = runTest {
        val rig = rig()
        rig.driver.resumeNow()
        runCurrent()
        assertEquals(1, rig.connector.hellos.size, "just heard from: the socket is fine")

        rig.connector.last.answersPings = false
        advanceTimeBy(RESUME_SILENT)
        runCurrent()
        rig.driver.resumeNow()
        runCurrent()
        advanceTimeBy(BACKOFF_FIRST)
        runCurrent()
        assertEquals(2, rig.connector.hellos.size, "quiet too long: back on a new socket")
    }

    @Test
    fun aSilentSocketIsGivenUpOn() = runTest {
        val rig = rig()
        rig.connector.last.answersPings = false
        advanceTimeBy(40_000)
        runCurrent()
        assertEquals(1, rig.connector.hellos.size)
        advanceTimeBy(20_000 + BACKOFF_FIRST)
        runCurrent()
        assertEquals(2, rig.connector.hellos.size)
        assertTrue(rig.connector.connections.first().closed)
    }

    @Test
    fun anAnswerLostWithTheConnectionIsAskedAgain() = runTest {
        val rig = rig()
        val question = checkNotNull(rig.play.table.decision)
        rig.driver.onAction(GameAction.Answer(question.firstCard()))
        runCurrent()
        rig.connector.last.close()
        advanceTimeBy(BACKOFF_FIRST)
        runCurrent()
        // The same ticket, and this seat isn't locked: the server never got it.
        assertEquals(TurnStatus.YourTurn, rig.play.status)
        assertTrue(rig.play.table.asking)
    }

    @Test
    fun anAnswerTheServerDidGetStaysLockedAcrossAReconnect() = runTest {
        val rig = rig()
        val server = rig.server
        val question = checkNotNull(rig.play.table.decision)
        rig.driver.onAction(GameAction.Answer(question.firstCard()))
        runCurrent()
        rig.connector.onOpen = { connection ->
            val held = rig.play.log.size
            connection.feed(server.start("wasp"))
            connection.feed(server.table("wasp", have = held, decision = question, locked = listOf("wasp")))
        }
        rig.connector.last.close()
        advanceTimeBy(BACKOFF_FIRST)
        runCurrent()
        assertIs<TurnStatus.Waiting>(rig.play.status)
        assertFalse(rig.play.table.asking)
    }

    @Test
    fun leavingSendsItOnceAndDoesNotComeBack() = runTest {
        val rig = rig()
        rig.driver.leave()
        runCurrent()
        assertEquals(listOf<ClientMessage>(ClientMessage.Leave(FakeServer.GAME)), rig.connector.connections[0].said)
        assertTrue(rig.connector.last.closed)
        advanceTimeBy(MINUTE)
        runCurrent()
        assertEquals(1, rig.connector.hellos.size)
    }

    // ---- Refusals and new servers --------------------------------------------------------------------

    @Test
    fun aFrameThisBuildCantReadAsksForAnUpdate() = runTest {
        val rig = rig()
        rig.connector.last.failWith(SerializationException("Serializer for class 'game.new' is not found"))
        runCurrent()
        assertEquals(OnlineState.Refused(Refusal.UPDATE_APP), rig.driver.state.value)
        advanceTimeBy(MINUTE)
        runCurrent()
        assertEquals(1, rig.connector.hellos.size, "reconnecting won't make this build read it")
        assertEquals(OnlineState.Refused(Refusal.UPDATE_APP), rig.driver.state.value)
    }

    @Test
    fun aRealServerFrameOfAnUnknownTypeFailsWithTheExceptionTheDriverExpects() {
        val failure = runCatching { decodeServerFrame("""{"type":"game.new","x":1}""") }.exceptionOrNull()
        assertIs<SerializationException>(failure)
    }

    @Test
    fun aBadTokenStopsAndARateLimitWaitsAndTriesAgain() = runTest {
        val bad = rig()
        bad.feed(ServerMessage.Refused(Refusal.BAD_TOKEN))
        assertEquals(OnlineState.Refused(Refusal.BAD_TOKEN), bad.driver.state.value)
        advanceTimeBy(MINUTE)
        runCurrent()
        assertEquals(1, bad.connector.hellos.size)

        val limited = DriverRig(this, null, FakeServer())
        limited.open()
        limited.feed(ServerMessage.Refused(Refusal.RATE_LIMITED, retryAfterSeconds = 10))
        assertEquals(OnlineState.Refused(Refusal.RATE_LIMITED, 10), limited.driver.state.value)
        advanceTimeBy(9_000)
        runCurrent()
        assertEquals(1, limited.connector.hellos.size)
        advanceTimeBy(1_001)
        runCurrent()
        assertEquals(2, limited.connector.hellos.size)
    }

    @Test
    fun aRateLimitWithATableKeepsTheTableUpWhileItWaits() = runTest {
        val rig = rig()
        rig.feed(ServerMessage.Refused(Refusal.RATE_LIMITED, retryAfterSeconds = 10))
        assertIs<OnlineState.Reconnecting>(rig.driver.state.value)
        advanceTimeBy(10_001)
        runCurrent()
        assertEquals(2, rig.connector.hellos.size)
        assertIs<OnlineState.InGame>(rig.driver.state.value)
    }

    @Test
    fun aRefusalIsNotPaintedOverByTheClock() = runTest {
        val rig = rig()
        val clock = DecisionClock("wasp", currentTime + MINUTE)
        rig.feed(rig.server.table("wasp", have = rig.play.log.size, clock = clock))
        rig.feed(ServerMessage.Refused(Refusal.BAD_TOKEN))
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(OnlineState.Refused(Refusal.BAD_TOKEN), rig.driver.state.value)
    }

    @Test
    fun noAccountOnThisDeviceIsABadToken() = runTest {
        val rig = DriverRig(this, "wasp", FakeServer())
        rig.identities.identity = null
        rig.open()
        assertEquals(OnlineState.Refused(Refusal.BAD_TOKEN), rig.driver.state.value)
        assertTrue(rig.connector.hellos.isEmpty())
    }

    @Test
    fun aMapThisBuildLacksIsSaid() = runTest {
        val server = FakeServer()
        val rig = DriverRig(this, null, server)
        rig.open()
        val elsewhere = server.session.setup.copy(mapId = "no-map")
        rig.feed(ServerMessage.GameStart(FakeServer.GAME, elsewhere, server.players, setOf("wasp"), false))
        rig.feed(server.table("wasp"))
        val state = assertIs<OnlineState.InGame>(rig.driver.state.value)
        assertEquals(GameUiState.Unavailable(NoGame.MAP_MISSING), state.game)
    }

    // ---- The clock -----------------------------------------------------------------------------------

    @Test
    fun theClockTicksAndIsCorrectedByPong() = runTest {
        // This phone's clock is 40 s behind the server's.
        val skew = 40_000L
        val rig = rig(skew = skew)
        val server = rig.server
        val deadline = currentTime + skew + 30_000
        rig.feed(
            server.table(
                "wasp",
                have = rig.play.log.size,
                clock = DecisionClock("wasp", deadline),
            ),
        )
        assertEquals("wasp", rig.play.online?.clockSeat)
        assertEquals(30, rig.play.online?.clockSeconds, "read on the server's clock, not the phone's")
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(25, rig.play.online?.clockSeconds)
        advanceTimeBy(MINUTE)
        runCurrent()
        assertEquals(0, rig.play.online?.clockSeconds, "never below zero")
    }

    @Test
    fun secondsLeftRoundsUp() {
        assertEquals(1, secondsLeft(1_001, 1_000))
        assertEquals(1, secondsLeft(2_000, 1_000))
        assertEquals(2, secondsLeft(2_001, 1_000))
        assertEquals(0, secondsLeft(1_000, 2_000))
    }
}
