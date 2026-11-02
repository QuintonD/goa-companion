package dev.quinton.goa.server.game.http

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Controller
import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.online.ClientMessage
import dev.quinton.goa.core.online.OnlineProtocol
import dev.quinton.goa.core.online.Refusal
import dev.quinton.goa.core.online.Rename
import dev.quinton.goa.core.online.Resume
import dev.quinton.goa.core.online.RoomStatus
import dev.quinton.goa.core.online.ServerMessage
import dev.quinton.goa.core.online.TurnRef
import dev.quinton.goa.server.game.WAIT_MILLIS
import dev.quinton.goa.server.game.hello
import dev.quinton.goa.server.game.json
import dev.quinton.goa.server.game.jsonBody
import dev.quinton.goa.server.game.next
import dev.quinton.goa.server.game.nextOf
import dev.quinton.goa.server.game.play
import dev.quinton.goa.server.game.sendMessage
import dev.quinton.goa.server.game.serverTest
import dev.quinton.goa.server.game.testEngine
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.junit.Test

class SocketTest {
    private val catalog = testEngine.data.catalog.dataVersion

    private suspend fun DefaultClientWebSocketSession.closeCode(): Short? = withTimeout(WAIT_MILLIS) {
        closeReason.await()?.code
    }

    private suspend fun DefaultClientWebSocketSession.expectClosed() {
        try {
            withTimeout(WAIT_MILLIS) {
                while (true) {
                    val frame = incoming.receive()
                    assertThat(frame).isNotInstanceOf(Frame.Text::class.java)
                }
            }
        } catch (_: ClosedReceiveChannelException) {
            return
        }
    }

    // Admission ------------------------------------------------------------------------------------

    @Test
    fun aGoodHelloIsWelcomedOnBothPaths() = serverTest {
        val account = account("Quinton")
        for (path in listOf("/v1/play", "/game/v1/play")) {
            ws.webSocket(path) {
                sendMessage(hello(account.token))
                val welcome = next() as ServerMessage.Welcome
                assertThat(welcome.accountId).isEqualTo(account.accountId)
                assertThat(welcome.name).isEqualTo("Quinton")
                assertThat(welcome.protocol).isEqualTo(OnlineProtocol.VERSION)
                assertThat(welcome.serverTime).isEqualTo(clock.now)
                assertThat(welcome.activeGames).isEmpty()
            }
        }
    }

    @Test
    fun noHelloInTimeIsClosedWithNoWelcome() = serverTest(configure = { it.copy(helloTimeoutMillis = 100) }) {
        ws.webSocket("/v1/play") { expectClosed() }
    }

    @Test
    fun aTokenInTheUrlOrAHeaderOpensNothing() = serverTest(configure = { it.copy(helloTimeoutMillis = 100) }) {
        val account = account()
        ws.webSocket("/v1/play?token=${account.token}") { expectClosed() }
        ws.webSocket("/v1/play", request = { bearerAuth(account.token) }) { expectClosed() }
        // Nor does it rescue a Hello with another token.
        ws.webSocket("/v1/play?token=${account.token}", request = { bearerAuth(account.token) }) {
            sendMessage(hello("not-the-token-of-anyone"))
            assertThat((next() as ServerMessage.Refused).reason).isEqualTo(Refusal.BAD_TOKEN)
        }
    }

    @Test
    fun theFirstFrameMustBeAHello() = serverTest {
        ws.webSocket("/v1/play") {
            sendMessage(ClientMessage.Ping(1))
            assertThat(closeCode()).isEqualTo(CloseReason.Codes.VIOLATED_POLICY.code)
        }
        ws.webSocket("/v1/play") {
            send(Frame.Text("{not json"))
            assertThat(closeCode()).isEqualTo(CloseReason.Codes.VIOLATED_POLICY.code)
        }
        ws.webSocket("/v1/play") {
            send(Frame.Binary(true, byteArrayOf(1, 2, 3)))
            assertThat(closeCode()).isEqualTo(CloseReason.Codes.VIOLATED_POLICY.code)
        }
    }

    @Test
    fun aBadTokenIsRefusedThenClosed() = serverTest {
        ws.webSocket("/v1/play") {
            sendMessage(hello("not-the-token-of-anyone"))
            assertThat((next() as ServerMessage.Refused).reason).isEqualTo(Refusal.BAD_TOKEN)
            expectClosed()
        }
        val deleted = account()
        http.delete("/v1/me") { bearerAuth(deleted.token) }
        ws.webSocket("/v1/play") {
            sendMessage(hello(deleted.token))
            assertThat((next() as ServerMessage.Refused).reason).isEqualTo(Refusal.BAD_TOKEN)
        }
    }

    @Test
    fun anOldProtocolOrCatalogIsToldToUpdateThenClosed() = serverTest {
        val token = account().token
        val old = listOf(
            hello(token, protocol = OnlineProtocol.VERSION - 1),
            hello(token, catalogVersion = catalog - 1),
        )
        for (message in old) {
            ws.webSocket("/v1/play") {
                sendMessage(message)
                assertThat((next() as ServerMessage.Refused).reason).isEqualTo(Refusal.UPDATE_APP)
                expectClosed()
            }
        }
    }

    @Test
    fun anOriginMustBeOnTheListWhenPresentAndNativeClientsSendNone() = serverTest {
        val token = account().token
        val refused = http.get("/v1/play") { header(HttpHeaders.Origin, "https://evil.example") }
        assertThat(refused.status).isEqualTo(HttpStatusCode.Forbidden)
        assertThat(http.get("/game/v1/play") { header(HttpHeaders.Origin, "null") }.status)
            .isEqualTo(HttpStatusCode.Forbidden)

        ws.webSocket("/v1/play", request = { header(HttpHeaders.Origin, "https://quintond.github.io") }) {
            sendMessage(hello(token))
            assertThat(next()).isInstanceOf(ServerMessage.Welcome::class.java)
        }
        ws.webSocket("/v1/play") {
            sendMessage(hello(token))
            assertThat(next()).isInstanceOf(ServerMessage.Welcome::class.java)
        }
    }

    @Test
    fun aFrameOver4KibIsClosedAsTooBig() = serverTest {
        val token = account().token
        ws.play(token) {
            send(Frame.Text("x".repeat(MAX_FRAME_BYTES + 1)))
            assertThat(closeCode()).isEqualTo(CloseReason.Codes.TOO_BIG.code)
        }
    }

    @Test
    fun aHelloOver4KibGetsNoWelcome() = serverTest {
        val token = account().token
        ws.webSocket("/v1/play") {
            val resume = List(500) { Resume("game-$it", it) }
            sendMessage(hello(token, resume))
            expectClosed()
        }
    }

    @Test
    fun anUnreadableFrameClosesTheSocket() = serverTest {
        ws.play(account().token) {
            send(Frame.Text("""{"type":"game.answer","gameId":"x","ticket":"not a number"}"""))
            assertThat(closeCode()).isEqualTo(CloseReason.Codes.CANNOT_ACCEPT.code)
        }
    }

    @Test
    fun aPingIsAnsweredWithTheServersClock() = serverTest {
        ws.play(account().token) {
            sendMessage(ClientMessage.Ping(42))
            assertThat(next()).isEqualTo(ServerMessage.Pong(42, clock.now))
        }
    }

    @Test
    fun tooManyFramesAreRefusedNotServed() = serverTest(configure = { it.copy(socketFramesPerMinute = 2) }) {
        ws.play(account().token) {
            repeat(2) { sendMessage(ClientMessage.Ping(it.toLong())) }
            repeat(2) { assertThat(next()).isInstanceOf(ServerMessage.Pong::class.java) }
            sendMessage(ClientMessage.Ping(9))
            val refused = next() as ServerMessage.Refused
            assertThat(refused.reason).isEqualTo(Refusal.RATE_LIMITED)
            assertThat(refused.retryAfterSeconds).isNotNull()
        }
    }

    // Lobby over the socket ------------------------------------------------------------------------

    @Test
    fun aWatcherSeesTheRoomChangeAndAnUnknownRoomIsRefused() = serverTest {
        val host = account()
        val code = room(host.token)
        ws.play(account().token) {
            sendMessage(ClientMessage.WatchRoom("ZZZZZZZZ"))
            assertThat((next() as ServerMessage.Refused).reason).isEqualTo(Refusal.NO_SUCH_ROOM)
            sendMessage(ClientMessage.WatchRoom(code))
            assertThat((next() as ServerMessage.Lobby).room.code).isEqualTo(code)

            val guest = account("Guest")
            join(guest.token, code)
            val seen = nextOf<ServerMessage.Lobby>().room
            assertThat(seen.seats.first { it.accountId == guest.accountId }.name).isEqualTo("Guest")
        }
    }

    @Test
    fun seatsAndHeroesAreOneEach() = serverTest {
        val host = account()
        val guest = account()
        val code = room(host.token, teamSize = 2)
        join(guest.token, code)
        val guestSeat = store.room(code)!!.seats.first { it.accountId == guest.accountId }
        ws.play(host.token) {
            // Someone else's seat.
            sendMessage(ClientMessage.PickSeat(code, guestSeat.team, guestSeat.index, "wasp"))
            assertThat((next() as ServerMessage.Refused).reason).isEqualTo(Refusal.ROOM_TAKEN)
            assertThat((next() as ServerMessage.Lobby).room.seats.all { it.heroId == null }).isTrue()
            // A seat that doesn't exist, a hero that doesn't.
            sendMessage(ClientMessage.PickSeat(code, Team.RED, 9, "wasp"))
            assertThat(next()).isInstanceOf(ServerMessage.Refused::class.java)
            next()
            sendMessage(ClientMessage.PickSeat(code, Team.RED, 0, "nobody"))
            assertThat(next()).isInstanceOf(ServerMessage.Refused::class.java)
            next()

            sendMessage(ClientMessage.PickSeat(code, Team.RED, 0, "wasp"))
            nextOf<ServerMessage.Lobby> { lobby -> lobby.room.seats.any { it.heroId == "wasp" } }
            ws.play(guest.token) {
                sendMessage(ClientMessage.PickSeat(code, guestSeat.team, guestSeat.index, "wasp"))
                assertThat(nextOf<ServerMessage.Refused>().reason).isEqualTo(Refusal.ROOM_TAKEN)
                sendMessage(ClientMessage.PickSeat(code, guestSeat.team, guestSeat.index, "arien"))
                val picked = nextOf<ServerMessage.Lobby> { lobby -> lobby.room.seats.any { it.heroId == "arien" } }
                assertThat(picked.room.seats.filter { it.heroId != null }).hasSize(2)
            }
            // A null hero clears the pick, and moving takes you with you, not your old seat.
            sendMessage(ClientMessage.PickSeat(code, Team.RED, 1, null))
            val moved = nextOf<ServerMessage.Lobby> { lobby -> lobby.room.seats.none { it.heroId == "wasp" } }.room
            assertThat(moved.seats.first { it.team == Team.RED && it.index == 0 }.accountId).isNull()
            assertThat(moved.seats.first { it.team == Team.RED && it.index == 1 }.accountId)
                .isEqualTo(host.accountId)
        }
    }

    @Test
    fun readyNeedsAHeroAndChangingHeroTakesItBack() = serverTest {
        val host = account()
        val code = room(host.token)
        ws.play(host.token) {
            sendMessage(ClientMessage.Ready(code, true))
            assertThat((next() as ServerMessage.Refused).reason).isEqualTo(Refusal.ROOM_TAKEN)
            next()
            sendMessage(ClientMessage.PickSeat(code, Team.RED, 0, "wasp"))
            nextOf<ServerMessage.Lobby> { lobby -> lobby.room.seats.any { it.heroId == "wasp" } }
            sendMessage(ClientMessage.Ready(code, true))
            nextOf<ServerMessage.Lobby> { lobby -> lobby.room.seats.any { it.ready } }
            sendMessage(ClientMessage.PickSeat(code, Team.RED, 0, "arien"))
            val changed = nextOf<ServerMessage.Lobby> { lobby -> lobby.room.seats.any { it.heroId == "arien" } }
            assertThat(changed.room.seats.none { it.ready }).isTrue()
        }
    }

    @Test
    fun onlyTheHostStartsAndOnlyWithEverySeatFilledAndReady() = serverTest {
        val host = account()
        val guest = account()
        val code = room(host.token)
        ws.play(host.token) {
            sendMessage(ClientMessage.Start(code))
            assertThat(nextOf<ServerMessage.Refused>().reason).isEqualTo(Refusal.ROOM_TAKEN)
            assertThat(rooms.games).isEmpty()

            join(guest.token, code)
            sendMessage(ClientMessage.PickSeat(code, Team.RED, 0, "wasp"))
            sendMessage(ClientMessage.Ready(code, true))
            nextOf<ServerMessage.Lobby> { lobby -> lobby.room.seats.any { it.ready } }

            ws.play(guest.token) {
                sendMessage(ClientMessage.PickSeat(code, Team.BLUE, 0, "arien"))
                nextOf<ServerMessage.Lobby> { lobby -> lobby.room.seats.any { it.heroId == "arien" } }
                sendMessage(ClientMessage.Start(code))
                assertThat(nextOf<ServerMessage.Refused>().reason).isEqualTo(Refusal.ROOM_TAKEN)
            }
            sendMessage(ClientMessage.Start(code))
            assertThat(nextOf<ServerMessage.Refused>().reason).isEqualTo(Refusal.ROOM_TAKEN)
            assertThat(rooms.games).isEmpty()
        }
    }

    @Test
    fun aStartCreatesOneGameAndLinksEveryoneSeated() = serverTest {
        val started = startedGame()
        val game = rooms.games.getValue(started.gameId)
        assertThat(rooms.games).hasSize(1)
        assertThat(game.setup.mapId).isEqualTo("forgotten-island")
        assertThat(game.setup.seats.map { it.heroId to it.team })
            .containsExactly("wasp" to Team.RED, "arien" to Team.BLUE).inOrder()
        assertThat(game.setup.seats.all { it.controller == Controller.Human }).isTrue()
        assertThat(game.players.map { it.accountId }).containsExactly(started.red.accountId, started.blue.accountId)

        // A repeat of the start changes nothing, and neither does anybody's second connection.
        ws.play(started.red.token) {
            sendMessage(ClientMessage.Start(game.roomCode))
            val view = nextOf<ServerMessage.Lobby>().room
            assertThat(view.status).isEqualTo(RoomStatus.STARTED)
            assertThat(view.gameId).isEqualTo(started.gameId)
        }
        assertThat(rooms.games).hasSize(1)
    }

    @Test
    fun aPersonSeatedElsewhereHearsOfTheStartWithoutWatching() = serverTest {
        val host = account()
        val guest = account()
        val code = room(host.token)
        val guestHeard = CompletableDeferred<ServerMessage.GameStart>()
        join(guest.token, code)
        ws.play(guest.token) {
            sendMessage(ClientMessage.PickSeat(code, Team.BLUE, 0, "arien"))
            sendMessage(ClientMessage.Ready(code, true))
            nextOf<ServerMessage.Lobby> { lobby -> lobby.room.seats.any { it.ready } }
            launch { guestHeard.complete(nextOf<ServerMessage.GameStart>()) }
            ws.play(host.token) {
                sendMessage(ClientMessage.PickSeat(code, Team.RED, 0, "wasp"))
                sendMessage(ClientMessage.Ready(code, true))
                nextOf<ServerMessage.Lobby> { lobby -> lobby.room.seats.all { it.ready } }
                sendMessage(ClientMessage.Start(code))
                nextOf<ServerMessage.GameStart>()
            }
            val start = withTimeout(WAIT_MILLIS) { guestHeard.await() }
            assertThat(start.you).containsExactly("arien")
        }
    }

    @Test
    fun aRenameReachesTheRoomsYouSitIn() = serverTest {
        val guest = account()
        val host = account()
        val code = room(host.token)
        join(guest.token, code)
        ws.play(host.token) {
            sendMessage(ClientMessage.WatchRoom(code))
            nextOf<ServerMessage.Lobby>()
            http.patch("/v1/me") {
                bearerAuth(guest.token)
                jsonBody(Rename.serializer(), Rename("Someone new"))
            }
            nextOf<ServerMessage.Lobby> { lobby -> lobby.room.seats.any { it.name == "Someone new" } }
        }
    }

    @Test
    fun aSocketWatchesItsMostRecentRoomsOnly() = serverTest {
        val codes = List(MAX_WATCHED_ROOMS + 1) { room(account().token) }
        ws.play(account().token) {
            codes.forEach { code ->
                sendMessage(ClientMessage.WatchRoom(code))
                nextOf<ServerMessage.Lobby> { it.room.code == code }
            }
            // The first room fell off the list, the second didn't.
            join(account().token, codes[0])
            join(account().token, codes[1])
            assertThat(nextOf<ServerMessage.Lobby>().room.code).isEqualTo(codes[1])
        }
    }

    @Test
    fun theNewestSocketWinsPastTheLimitOfOnePerson() = serverTest {
        val token = account().token
        ws.webSocket("/v1/play") {
            sendMessage(hello(token))
            nextOf<ServerMessage.Welcome>()
            val others = List(MAX_SOCKETS_PER_ACCOUNT) { ws.webSocketSession("/v1/play") }
            others.forEach { other ->
                other.send(Frame.Text(ClientMessage.serializer().json(hello(token))))
                assertThat(other.next()).isInstanceOf(ServerMessage.Welcome::class.java)
            }
            expectClosed()
            others.forEach { it.close() }
        }
    }

    @Test
    fun deletingAnAccountEndsItsSockets() = serverTest {
        val token = account().token
        ws.play(token) {
            http.delete("/v1/me") { bearerAuth(token) }
            expectClosed()
        }
    }

    // Games over the socket ------------------------------------------------------------------------

    @Test
    fun afterHelloEachActiveOrResumedGameIsConnected() = serverTest {
        val started = startedGame()
        rooms.connects.clear()
        val other = startedGame()
        rooms.connects.clear()
        val resume = listOf(Resume(started.gameId, 17), Resume(other.gameId, -3), Resume("no-such-game", 1))
        ws.play(started.red.token, resume) {
            val first = nextOf<ServerMessage.GameStart>()
            assertThat(first.gameId).isEqualTo(started.gameId)
            assertThat(first.you).containsExactly("wasp")
            assertThat(nextOf<ServerMessage.Table>().eventsFrom).isEqualTo(17)
            sendMessage(ClientMessage.Ping(1))
            nextOf<ServerMessage.Pong>()
        }
        // A person can't resume somebody else's game: the room says no and nothing is linked.
        assertThat(rooms.connects.map { it.first }).containsExactly(started.gameId)
    }

    @Test
    fun theWelcomeListsActiveGames() = serverTest {
        val started = startedGame()
        ws.webSocket("/v1/play") {
            sendMessage(hello(started.blue.token))
            assertThat((next() as ServerMessage.Welcome).activeGames).containsExactly(started.gameId)
        }
    }

    @Test
    fun aSocketClosingClosesItsLinks() = serverTest {
        val started = startedGame()
        val before = rooms.closedLinks.get()
        ws.play(started.red.token) { nextOf<ServerMessage.GameStart>() }
        withTimeout(WAIT_MILLIS) { while (rooms.closedLinks.get() == before) delay(10) }
    }

    @Test
    fun aDuplicateAnswerReachesTheRoomOnceAndGetsTheSameAck() = serverTest {
        val started = startedGame()
        ws.play(started.red.token) {
            nextOf<ServerMessage.Table>()
            val answer = ClientMessage.Answer(started.gameId, 5, Choice.Pass)
            sendMessage(answer)
            val first = nextOf<ServerMessage.Ack>()
            assertThat(first.accepted).isTrue()
            sendMessage(answer)
            assertThat(nextOf<ServerMessage.Ack>()).isEqualTo(first)

            // Another choice or another question is a new answer.
            sendMessage(ClientMessage.Answer(started.gameId, 5, Choice.Unit("x")))
            nextOf<ServerMessage.Ack>()
            sendMessage(ClientMessage.Answer(started.gameId, 6, Choice.Pass))
            nextOf<ServerMessage.Ack>()
        }
        assertThat(rooms.answers.map { it.ticket to it.choice }).containsExactly(
            5L to Choice.Pass,
            5L to Choice.Unit("x"),
            6L to Choice.Pass,
        ).inOrder()
    }

    @Test
    fun anAnswerToAGameYouAreNotInIsRefusedAndNeverReachesTheRoom() = serverTest {
        val started = startedGame()
        ws.play(account().token) {
            sendMessage(ClientMessage.Answer(started.gameId, 1, Choice.Pass))
            val ack = nextOf<ServerMessage.Ack>()
            assertThat(ack.accepted).isFalse()
            assertThat(ack.reason).isEqualTo("not_in_game")
            sendMessage(ClientMessage.AskReview(started.gameId))
            sendMessage(ClientMessage.Leave(started.gameId))
            sendMessage(ClientMessage.Ping(1))
            nextOf<ServerMessage.Pong>()
        }
        assertThat(rooms.answers).isEmpty()
        assertThat(rooms.reviews).isEmpty()
        assertThat(rooms.left).isEmpty()
    }

    @Test
    fun reviewAndLeaveGoToTheRoom() = serverTest {
        val started = startedGame()
        ws.play(started.blue.token) {
            nextOf<ServerMessage.Table>()
            sendMessage(ClientMessage.AskReview(started.gameId, TurnRef(1, 2)))
            nextOf<ServerMessage.Reviewed>()
            sendMessage(ClientMessage.Leave(started.gameId))
            sendMessage(ClientMessage.Ping(1))
            nextOf<ServerMessage.Pong>()
        }
        assertThat(rooms.reviews).containsExactly(started.blue.accountId to TurnRef(1, 2))
        assertThat(rooms.left).containsExactly(started.gameId to started.blue.accountId)
    }

    /** One socket doesn't read at all; the room's frames to the other still arrive, and in the end to both. */
    @Test
    fun aClientThatDoesNotReadDoesNotHoldUpTheRoom() = serverTest {
        val started = startedGame()
        val game = rooms.games.getValue(started.gameId)
        val events = List(2_000) { GameEvent.RoundStarted(it, "event $it") }
        ws.play(started.red.token) {
            nextOf<ServerMessage.Table>()
            ws.play(started.blue.token) {
                nextOf<ServerMessage.Table>()
                events.forEachIndexed { i, event ->
                    rooms.sendAll(game) { accountId ->
                        val seat = game.players.first { it.accountId == accountId }.seat
                        rooms.table(game, seat, seq = i + 2L, from = i, events = listOf(event))
                    }
                }
                assertThat(eventsUpTo(events.size)).isEqualTo(events)
            }
            assertThat(eventsUpTo(events.size)).isEqualTo(events)
        }
    }

    /** Puts the tables back together, as a client does: a gap or a repeat would fail the comparison. */
    private suspend fun DefaultClientWebSocketSession.eventsUpTo(count: Int): List<GameEvent> {
        val seen = ArrayList<GameEvent>()
        while (seen.size < count) {
            val table = nextOf<ServerMessage.Table>()
            assertThat(table.eventsFrom).isAtMost(seen.size)
            seen += table.events.drop(seen.size - table.eventsFrom)
        }
        return seen
    }
}
