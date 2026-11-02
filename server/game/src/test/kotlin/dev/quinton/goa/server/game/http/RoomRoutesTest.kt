package dev.quinton.goa.server.game.http

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.GameLength
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.online.ApiError
import dev.quinton.goa.core.online.ClientMessage
import dev.quinton.goa.core.online.CreateRoom
import dev.quinton.goa.core.online.HealthInfo
import dev.quinton.goa.core.online.LeaderRow
import dev.quinton.goa.core.online.LeaderboardSnapshot
import dev.quinton.goa.core.online.RoomCreated
import dev.quinton.goa.core.online.Resume
import dev.quinton.goa.core.online.RoomStatus
import dev.quinton.goa.core.online.RoomView
import dev.quinton.goa.core.online.ServerMessage
import dev.quinton.goa.server.game.GameServerConfig
import dev.quinton.goa.server.game.TestServer
import dev.quinton.goa.server.game.board
import dev.quinton.goa.server.game.jsonBody
import dev.quinton.goa.server.game.lobby.Lobby
import dev.quinton.goa.server.game.nextOf
import dev.quinton.goa.server.game.parse
import dev.quinton.goa.server.game.play
import dev.quinton.goa.server.game.room.LeaderboardSource
import dev.quinton.goa.server.game.sendMessage
import dev.quinton.goa.server.game.serverTest
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import java.nio.file.Files
import org.junit.Test

class RoomRoutesTest {
    private suspend fun HttpResponse.error() = ApiError.serializer().parse(bodyAsText()).error

    private suspend fun TestServer.leave(token: String, code: String): HttpResponse =
        http.post("/v1/rooms/$code/leave") { bearerAuth(token) }

    @Test
    fun aRoomGetsACodeAndItsHostASeat() = serverTest {
        val host = account()
        val response = createRoom(host.token, teamSize = 2, length = GameLength.LONG, ranked = true)
        assertThat(response.status).isEqualTo(HttpStatusCode.Created)
        val code = RoomCreated.serializer().parse(response.bodyAsText()).code
        assertThat(code).matches("[0-9A-HJKMNP-TV-Z]{8}")

        val room = join(host.token, code)
        assertThat(room.hostAccountId).isEqualTo(host.accountId)
        assertThat(room.teamSize).isEqualTo(2)
        assertThat(room.length).isEqualTo(GameLength.LONG)
        assertThat(room.ranked).isTrue()
        assertThat(room.status).isEqualTo(RoomStatus.OPEN)
        assertThat(room.seats).hasSize(4)
        assertThat(room.seats.mapNotNull { it.accountId }).containsExactly(host.accountId)
        assertThat(room.expiresAt).isEqualTo(clock.now + Lobby.ALONE_MILLIS)
    }

    @Test
    fun onlyTheSizesAndMapsOfTheModeAreOffered() = serverTest {
        val host = account().token
        val bad = listOf(
            0 to TestServer.FOREST,
            6 to TestServer.FOREST,
            -1 to TestServer.FOREST,
            4 to TestServer.FOREST,
            3 to TestServer.RIVER,
            1 to "magma-and-ice",
            1 to "no-such-map",
        )
        for ((size, map) in bad) {
            val response = createRoom(host, size, map)
            assertThat(response.status).isEqualTo(HttpStatusCode.BadRequest)
            assertThat(response.error()).isEqualTo("bad_room")
        }
    }

    @Test
    fun theOpenRoomCapReturns503WithRetryAfter() = serverTest(configure = { it.copy(maxOpenRooms = 2) }) {
        repeat(2) { room(account().token) }
        val full = createRoom(account().token)
        assertThat(full.status).isEqualTo(HttpStatusCode.ServiceUnavailable)
        assertThat(full.bodyAsText()).isEqualTo("""{"error":"rooms_full"}""")
        assertThat(full.headers[HttpHeaders.RetryAfter]).isNotNull()
    }

    @Test
    fun runningGamesAndOpenRoomsHaveSeparateBudgets() = serverTest(configure = { it.copy(maxRooms = 1) }) {
        rooms.extraRunning = 1
        // The games' budget is spent: a room can still open and wait for one to finish.
        assertThat(createRoom(account().token).status).isEqualTo(HttpStatusCode.Created)
        assertThat(createRoom(account().token).status).isEqualTo(HttpStatusCode.Created)
    }

    @Test
    fun onePersonWithManyAccountsCantBlockNewRooms() = serverTest {
        // What the review did: seven accounts with three rooms each filled the cap in under a minute.
        val refusals = (1..7).map { account("Griefer $it") }.flatMap { griefer ->
            List(3) { createRoom(griefer.token, from = "203.0.113.9").status }
        }
        assertThat(refusals.count { it == HttpStatusCode.Created }).isEqualTo(Lobby.MAX_OPEN_ROOMS_PER_HOST)
        assertThat(refusals.toSet()).contains(HttpStatusCode.Conflict)

        val honest = createRoom(account("Honest").token, from = "198.51.100.1")
        assertThat(honest.status).isEqualTo(HttpStatusCode.Created)
    }

    @Test
    fun manyAddressesFillOnlyTheOpenRoomBudgetAndARoomAloneGoesInTenMinutes() = serverTest {
        repeat(GameServerConfig.DEFAULT_MAX_OPEN_ROOMS) { i ->
            assertThat(createRoom(account("A$i").token, from = "203.0.113.$i").status)
                .isEqualTo(HttpStatusCode.Created)
        }
        val honest = account("Honest").token
        assertThat(createRoom(honest, from = "198.51.100.1").status).isEqualTo(HttpStatusCode.ServiceUnavailable)
        clock.now += Lobby.ALONE_MILLIS
        assertThat(createRoom(honest, from = "198.51.100.1").status).isEqualTo(HttpStatusCode.Created)
    }

    @Test
    fun anExpiredRoomStopsCountingAndCannotBeJoined() = serverTest(configure = { it.copy(maxOpenRooms = 1) }) {
        val code = room(account().token)
        val other = account()
        clock.now += Lobby.ALONE_MILLIS
        val late = http.post("/v1/rooms/$code/join") { bearerAuth(other.token) }
        assertThat(late.status).isEqualTo(HttpStatusCode.NotFound)
        assertThat(late.error()).isEqualTo("no_such_room")
        assertThat(createRoom(other.token).status).isEqualTo(HttpStatusCode.Created)
    }

    @Test
    fun aPersonHostsAtMostThreeOpenRooms() = serverTest {
        val host = account().token
        repeat(Lobby.MAX_OPEN_ROOMS_PER_HOST) { assertThat(createRoom(host).status).isEqualTo(HttpStatusCode.Created) }
        val refused = createRoom(host)
        assertThat(refused.status).isEqualTo(HttpStatusCode.Conflict)
        assertThat(refused.error()).isEqualTo("too_many_rooms")
    }

    @Test
    fun theDrainFileRefusesNewRoomsAndShowsInHealthWhileGamesGoOn() = serverTest {
        val started = startedGame()
        val waiting = room(account().token)
        Files.createFile(config.drainFile)

        val refused = createRoom(account().token)
        assertThat(refused.status).isEqualTo(HttpStatusCode.ServiceUnavailable)
        assertThat(refused.bodyAsText()).isEqualTo("""{"error":"draining"}""")
        assertThat(refused.headers[HttpHeaders.RetryAfter]).isNotNull()
        assertThat(HealthInfo.serializer().parse(http.get("/v1/health").bodyAsText()).status).isEqualTo("draining")

        // A person can still go back to a running game and answer in it.
        ws.play(started.red.token, listOf(Resume(started.gameId, 0))) {
            nextOf<ServerMessage.GameStart>()
            sendMessage(ClientMessage.Answer(started.gameId, 1, Choice.Pass))
            assertThat(nextOf<ServerMessage.Ack>().accepted).isTrue()
        }
        // A room already open can still fill up; its start is refused (LobbyTest).
        assertThat(http.post("/v1/rooms/$waiting/join") { bearerAuth(account().token) }.status)
            .isEqualTo(HttpStatusCode.OK)

        Files.delete(config.drainFile)
        assertThat(createRoom(account().token).status).isEqualTo(HttpStatusCode.Created)
    }

    @Test
    fun joiningSeatsYouOnTheEmptierTeamAndARepeatChangesNothing() = serverTest {
        val host = account()
        val code = room(host.token, teamSize = 2)
        val second = account()
        val room = join(second.token, code)
        val seat = room.seats.single { it.accountId == second.accountId }
        assertThat(seat.team).isNotEqualTo(room.seats.single { it.accountId == host.accountId }.team)
        assertThat(join(second.token, code)).isEqualTo(room)
        assertThat(join(host.token, code)).isEqualTo(room)
    }

    @Test
    fun aRoomCodeIsReadHoweverItIsTyped() = serverTest {
        val code = room(account().token)
        val typed = code.lowercase().replace('0', 'o').replace('1', 'l')
        assertThat(join(account().token, typed).code).isEqualTo(code)
    }

    @Test
    fun aFullRoomOrAnUnknownCodeIsRefused() = serverTest {
        val code = room(account().token)
        join(account().token, code)
        val full = http.post("/v1/rooms/$code/join") { bearerAuth(account().token) }
        assertThat(full.status).isEqualTo(HttpStatusCode.Conflict)
        assertThat(full.error()).isEqualTo("room_taken")
        for (unknown in listOf("ZZZZZZZZ", "short", "not-a-room-code-at-all")) {
            val response = http.post("/v1/rooms/$unknown/join") { bearerAuth(account().token) }
            assertThat(response.status).isEqualTo(HttpStatusCode.NotFound)
            assertThat(response.error()).isEqualTo("no_such_room")
        }
    }

    @Test
    fun leavingFreesTheSeatAndPassesTheHostAndTheLastOneOutClosesTheRoom() = serverTest {
        val host = account()
        val guest = account()
        val code = room(host.token)
        join(guest.token, code)

        val after = RoomView.serializer().parse(leave(host.token, code).bodyAsText())
        assertThat(after.hostAccountId).isEqualTo(guest.accountId)
        assertThat(after.seats.mapNotNull { it.accountId }).containsExactly(guest.accountId)
        // Leaving again changes nothing.
        assertThat(RoomView.serializer().parse(leave(host.token, code).bodyAsText())).isEqualTo(after)

        val closed = RoomView.serializer().parse(leave(guest.token, code).bodyAsText())
        assertThat(closed.status).isEqualTo(RoomStatus.CLOSED)
        val gone = http.post("/v1/rooms/$code/join") { bearerAuth(host.token) }
        assertThat(gone.status).isEqualTo(HttpStatusCode.NotFound)
        assertThat(leave(host.token, "ZZZZZZZZ").status).isEqualTo(HttpStatusCode.NotFound)
    }

    @Test
    fun aStartedRoomOpensOnlyToItsPlayers() = serverTest {
        val started = startedGame()
        val code = rooms.games.getValue(started.gameId).roomCode
        val back = join(started.blue.token, code)
        assertThat(back.status).isEqualTo(RoomStatus.STARTED)
        assertThat(back.gameId).isEqualTo(started.gameId)

        val stranger = http.post("/v1/rooms/$code/join") { bearerAuth(account().token) }
        assertThat(stranger.status).isEqualTo(HttpStatusCode.Conflict)
        val leaving = leave(started.blue.token, code)
        assertThat(leaving.status).isEqualTo(HttpStatusCode.Conflict)
    }

    @Test
    fun aPersonPlaysAtMostThreeGames() = serverTest {
        val player = account()
        repeat(Lobby.MAX_RUNNING_GAMES) { i ->
            val other = account()
            val code = room(player.token)
            join(other.token, code)
            ws.play(other.token) {
                sendMessage(ClientMessage.PickSeat(code, Team.BLUE, 0, "arien"))
                sendMessage(ClientMessage.Ready(code, true))
                nextOf<ServerMessage.Lobby> { lobby -> lobby.room.seats.any { it.ready } }
            }
            ws.play(player.token) {
                sendMessage(ClientMessage.PickSeat(code, Team.RED, 0, "wasp"))
                sendMessage(ClientMessage.Ready(code, true))
                nextOf<ServerMessage.Lobby> { lobby -> lobby.room.seats.all { it.ready } }
                sendMessage(ClientMessage.Start(code))
                nextOf<ServerMessage.Lobby> { it.room.gameId != null }
            }
            assertThat(rooms.games.size).isEqualTo(i + 1)
        }
        val tooMany = createRoom(player.token)
        assertThat(tooMany.status).isEqualTo(HttpStatusCode.Conflict)
        assertThat(tooMany.error()).isEqualTo("too_many_games")

        val waiting = room(account().token)
        val joining = http.post("/v1/rooms/$waiting/join") { bearerAuth(player.token) }
        assertThat(joining.status).isEqualTo(HttpStatusCode.Conflict)
        assertThat(joining.error()).isEqualTo("too_many_games")
    }

    @Test
    fun everyRouteAnswersUnderThePublicMountToo() = serverTest {
        val host = account()
        val made = http.post("/game/v1/rooms") {
            bearerAuth(host.token)
            jsonBody(CreateRoom.serializer(), CreateRoom(TestServer.FOREST, GameLength.QUICK, 1, false))
        }
        assertThat(made.status).isEqualTo(HttpStatusCode.Created)
        val code = RoomCreated.serializer().parse(made.bodyAsText()).code
        assertThat(
            http.post("/game/v1/rooms/$code/join") {
                bearerAuth(host.token)
            }.status,
        ).isEqualTo(HttpStatusCode.OK)
        assertThat(http.get("/game/v1/me") { bearerAuth(host.token) }.status).isEqualTo(HttpStatusCode.OK)
        assertThat(http.get("/game/v1/me/games") { bearerAuth(host.token) }.status).isEqualTo(HttpStatusCode.OK)
        assertThat(http.get("/game/v1/leaderboard").status).isEqualTo(HttpStatusCode.OK)
        assertThat(
            http.post("/game/v1/rooms/$code/leave") {
                bearerAuth(host.token)
            }.status,
        ).isEqualTo(HttpStatusCode.OK)
    }

    @Test
    fun unknownPathsAndMethodsAnswerInJson() = serverTest {
        assertThat(http.get("/v1/nothing").status).isEqualTo(HttpStatusCode.NotFound)
        assertThat(http.get("/v1/nothing").error()).isEqualTo("not_found")
    }

    @Test
    fun theLeaderboardIsOpenAndAnswersItsETag() = serverTest(leaderboard = LeaderboardSource { board("sha-1") }) {
        val first = http.get("/v1/leaderboard")
        assertThat(first.status).isEqualTo(HttpStatusCode.OK)
        val etag = first.headers[HttpHeaders.ETag]!!
        assertThat(first.headers[HttpHeaders.CacheControl]).isEqualTo("no-cache")
        assertThat(LeaderboardSnapshot.serializer().parse(first.bodyAsText()).logSha256).isEqualTo("sha-1")

        for (asked in listOf(etag, "W/$etag", "\"other\", $etag", "*")) {
            val same = http.get("/v1/leaderboard") { header(HttpHeaders.IfNoneMatch, asked) }
            assertThat(same.status).isEqualTo(HttpStatusCode.NotModified)
            assertThat(same.headers[HttpHeaders.ETag]).isEqualTo(etag)
            assertThat(same.bodyAsText()).isEmpty()
        }
        val other = http.get("/v1/leaderboard") { header(HttpHeaders.IfNoneMatch, "\"sha-0\"") }
        assertThat(other.status).isEqualTo(HttpStatusCode.OK)
    }

    @Test
    fun theETagFollowsWhatTheBoardSaysAndNotWhenItWasMade() {
        var now = board("sha-1").copy(generatedAt = 1, players = listOf(LeaderRow("a1", "Sam", 1000, 5, 3)))
        serverTest(leaderboard = LeaderboardSource { now }) {
            val first = http.get("/v1/leaderboard").headers[HttpHeaders.ETag]
            now = now.copy(generatedAt = 2)
            assertThat(http.get("/v1/leaderboard").headers[HttpHeaders.ETag]).isEqualTo(first)
            // A deleted account leaves the log as it is: the name must still leave every copy.
            now = now.copy(players = listOf(LeaderRow("a1", "Former player", 1000, 5, 3)))
            assertThat(http.get("/v1/leaderboard").headers[HttpHeaders.ETag]).isNotEqualTo(first)
        }
    }

    @Test
    fun answersAreNotKeptByCaches() = serverTest {
        assertThat(http.get("/v1/health").headers[HttpHeaders.CacheControl]).isEqualTo("no-store")
        assertThat(http.get("/v1/health").headers["X-Content-Type-Options"]).isEqualTo("nosniff")
    }
}
