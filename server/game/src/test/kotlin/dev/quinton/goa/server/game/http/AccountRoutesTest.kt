package dev.quinton.goa.server.game.http

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.online.AccountCreated
import dev.quinton.goa.core.online.ApiError
import dev.quinton.goa.core.online.CreateAccount
import dev.quinton.goa.core.online.LinkAccount
import dev.quinton.goa.core.online.Me
import dev.quinton.goa.core.online.MyGames
import dev.quinton.goa.core.online.Rename
import dev.quinton.goa.core.online.ServerMessage
import dev.quinton.goa.server.game.hello
import dev.quinton.goa.server.game.jsonBody
import dev.quinton.goa.server.game.lobby.Accounts
import dev.quinton.goa.server.game.next
import dev.quinton.goa.server.game.parse
import dev.quinton.goa.server.game.sendMessage
import dev.quinton.goa.server.game.serverTest
import dev.quinton.goa.server.game.store.GameResult
import dev.quinton.goa.server.game.store.GameSeatRow
import dev.quinton.goa.server.game.store.StoreFixtures
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import java.nio.file.Files
import org.junit.Test

class AccountRoutesTest {
    private suspend fun HttpResponse.error() = ApiError.serializer().parse(bodyAsText()).error

    @Test
    fun anAccountIsCreatedAndItsTokenWorks() = serverTest {
        val response = http.post("/v1/accounts") { jsonBody(CreateAccount.serializer(), CreateAccount("  Quinton ")) }
        assertThat(response.status).isEqualTo(HttpStatusCode.Created)
        val created = AccountCreated.serializer().parse(response.bodyAsText())
        assertThat(created.token).isNotEqualTo(created.recoveryCode)

        val me = Me.serializer().parse(http.get("/v1/me") { bearerAuth(created.token) }.bodyAsText())
        assertThat(me).isEqualTo(Me(created.accountId, "Quinton"))
    }

    @Test
    fun onlyHashesOfSecretsAreStored() = serverTest {
        val created = account()
        val database = Files.readAllBytes(config.dataDir.resolve("games.sqlite")).decodeToString() +
            Files.readAllBytes(config.dataDir.resolve("games.sqlite-wal")).decodeToString()
        assertThat(database).doesNotContain(created.token)
        assertThat(database).doesNotContain(created.recoveryCode)
    }

    @Test
    fun aBadNameIsRefused() = serverTest {
        val refused = listOf(
            "x",
            " y ",
            "a".repeat(25),
            "<script>",
            "a>b",
            "tab\there",
            "line\nbreak",
            "bi‮di",
            "former PLAYER",
        )
        for (name in refused) {
            val response = http.post("/v1/accounts") { jsonBody(CreateAccount.serializer(), CreateAccount(name)) }
            assertThat(response.status).isEqualTo(HttpStatusCode.BadRequest)
            assertThat(response.error()).isEqualTo("bad_name")
        }
    }

    @Test
    fun namesOfEveryAlphabetAreFine() {
        for (name in listOf("Ann", "Ана", "田中", "Zoë-2", "a b", "😀😀")) {
            assertThat(Accounts.cleanName(name)).isEqualTo(name)
        }
    }

    @Test
    fun aMalformedOrHugeBodyIsRefused() = serverTest {
        val bad = http.post("/v1/accounts") { setBody("{not json") }
        assertThat(bad.status).isEqualTo(HttpStatusCode.BadRequest)
        assertThat(bad.error()).isEqualTo("bad_request")
        val huge = http.post("/v1/accounts") { setBody("""{"name":"${"a".repeat(5000)}"}""") }
        assertThat(huge.status).isEqualTo(HttpStatusCode.PayloadTooLarge)
    }

    @Test
    fun accountCreationIsLimitedPerClientByTheLastForwardedHop() = serverTest(
        configure = { it.copy(accountsPerClientPerHour = 2) },
    ) {
        suspend fun create(forwarded: String) = http.post("/v1/accounts") {
            header(HttpHeaders.XForwardedFor, forwarded)
            jsonBody(CreateAccount.serializer(), CreateAccount("Somebody"))
        }
        assertThat(create("1.1.1.1").status).isEqualTo(HttpStatusCode.Created)
        // The earlier hops are the client's to forge; only the last was added by our proxy.
        assertThat(create("9.9.9.9, 1.1.1.1").status).isEqualTo(HttpStatusCode.Created)
        val limited = create("8.8.8.8, 1.1.1.1")
        assertThat(limited.status).isEqualTo(HttpStatusCode.TooManyRequests)
        assertThat(limited.headers[HttpHeaders.RetryAfter]).isNotNull()
        assertThat(create("2.2.2.2").status).isEqualTo(HttpStatusCode.Created)
    }

    @Test
    fun requestsAreLimitedPerClientIncludingTheBoard() = serverTest(configure = { it.copy(perClientPerMinute = 2) }) {
        repeat(2) { assertThat(http.get("/v1/leaderboard").status).isEqualTo(HttpStatusCode.OK) }
        val limited = http.get("/v1/leaderboard")
        assertThat(limited.status).isEqualTo(HttpStatusCode.TooManyRequests)
        assertThat(limited.error()).isEqualTo("rate_limited")
    }

    @Test
    fun healthIsNeverLimitedOnEitherPath() = serverTest(
        configure = { it.copy(perClientPerMinute = 1, globalPerMinute = 1) },
    ) {
        repeat(5) {
            assertThat(http.get("/v1/health").status).isEqualTo(HttpStatusCode.OK)
            assertThat(http.get("/game/v1/health").status).isEqualTo(HttpStatusCode.OK)
        }
    }

    @Test
    fun aFloodOfRestCallsDoesntStopSocketsAndSocketsHaveTheirOwnLimit() = serverTest(
        configure = { it.copy(perClientPerMinute = 3, globalPerMinute = 3, upgradesPerClientPerMinute = 2) },
    ) {
        val token = account().token
        repeat(5) { http.get("/v1/leaderboard") }
        assertThat(http.get("/v1/leaderboard").status).isEqualTo(HttpStatusCode.TooManyRequests)
        repeat(2) {
            ws.webSocket("/v1/play") {
                sendMessage(hello(token))
                assertThat(next()).isInstanceOf(ServerMessage.Welcome::class.java)
            }
        }
        val limited = http.get("/v1/play") { header(HttpHeaders.XForwardedFor, "not-the-flooded-one") }
        assertThat(limited.status).isNotEqualTo(HttpStatusCode.TooManyRequests)
        val refused = http.get("/v1/play")
        assertThat(refused.status).isEqualTo(HttpStatusCode.TooManyRequests)
        assertThat(refused.headers[HttpHeaders.RetryAfter]).isNotNull()
    }

    @Test
    fun ipv6ClientsCountBySlash64() {
        val one = clientGroup("2001:db8:1:2:aaaa::1")
        assertThat(clientGroup("2001:db8:1:2:bbbb:cccc:dddd:eeee")).isEqualTo(one)
        assertThat(clientGroup("[2001:db8:1:2::9]")).isEqualTo(one)
        assertThat(clientGroup("2001:DB8:1:2::9%eth0")).isEqualTo(one)
        assertThat(clientGroup("2001:db8:1:3::1")).isNotEqualTo(one)
        assertThat(clientGroup("::ffff:192.0.2.7")).isEqualTo("192.0.2.7")
        assertThat(clientGroup("192.0.2.7")).isEqualTo("192.0.2.7")
        // A header may say anything: it stays a plain key, and no name is ever looked up.
        assertThat(clientGroup("some.host.example")).isEqualTo("some.host.example")
        assertThat(clientGroup("not:an:address")).isEqualTo("not:an:address")
    }

    @Test
    fun aWholeSlash64SharesOneLimit() = serverTest(configure = { it.copy(accountsPerClientPerHour = 1) }) {
        suspend fun create(from: String) = http.post("/v1/accounts") {
            header(HttpHeaders.XForwardedFor, from)
            jsonBody(CreateAccount.serializer(), CreateAccount("Somebody"))
        }
        assertThat(create("2001:db8:1:2::1").status).isEqualTo(HttpStatusCode.Created)
        assertThat(create("2001:db8:1:2::2").status).isEqualTo(HttpStatusCode.TooManyRequests)
        assertThat(create("2001:db8:1:3::1").status).isEqualTo(HttpStatusCode.Created)
    }

    @Test
    fun namesAreCheckedByCodePoint() {
        val refused = listOf(
            "a\u200Db",
            "a\uDB40\uDC01b",
            "a\u0378b",
            "a\uE000b",
            "a\uDBC0\uDC00b",
            "a\uD800b",
            "a\u0001b",
            "a\u2028b",
            "a<b",
            "a>b",
            "a\"b",
            "R&D",
            "a`b",
            "    ",
            "\u3000\u3000\u3000",
            "\u00A0\u00A0",
            "\u0301\u0301",
            "\u3164\u3164",
            "a\u2800",
        )
        for (name in refused) assertThat(Accounts.cleanName(name)).isNull()
    }

    @Test
    fun theNameOfADeletedAccountCantBeTaken() {
        val names = listOf(
            "Former player",
            "former player",
            "FORMER PLAYER",
            "  Former player  ",
            "Former   player",
            "Former\u00A0player",
            "Former\u3000\u3000Player",
        )
        for (name in names) assertThat(Accounts.cleanName(name)).isNull()
    }

    @Test
    fun spacesCollapseAndOrdinaryNamesPass() {
        assertThat(Accounts.cleanName("  Ada   Lovelace ")).isEqualTo("Ada Lovelace")
        assertThat(Accounts.cleanName("Ada\u00A0\u00A0L")).isEqualTo("Ada L")
        for (name in listOf("Zo\u00EB", "\u674E\u96F7", "\uD83D\uDC69 Ada", "Jos\u0065\u0301", "O'Neil")) {
            assertThat(Accounts.cleanName(name)).isEqualTo(name)
        }
    }

    @Test
    fun aRecoveryCodeLinksOnceAndIssuesANewOne() = serverTest {
        val created = account()
        val typed = "  " + created.recoveryCode.lowercase().replace("-", " ") + " "
        val response = http.post("/v1/accounts/link") { jsonBody(LinkAccount.serializer(), LinkAccount(typed)) }
        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        val linked = AccountCreated.serializer().parse(response.bodyAsText())
        assertThat(linked.accountId).isEqualTo(created.accountId)
        assertThat(linked.token).isNotEqualTo(created.token)
        assertThat(linked.recoveryCode).isNotEqualTo(created.recoveryCode)

        // Both devices work; the old code is burnt, the new one isn't.
        for (token in listOf(created.token, linked.token)) {
            assertThat(http.get("/v1/me") { bearerAuth(token) }.status).isEqualTo(HttpStatusCode.OK)
        }
        val burnt = http.post("/v1/accounts/link") {
            jsonBody(LinkAccount.serializer(), LinkAccount(created.recoveryCode))
        }
        assertThat(burnt.status).isEqualTo(HttpStatusCode.Unauthorized)
        assertThat(burnt.error()).isEqualTo("bad_recovery_code")
        val again = http.post("/v1/accounts/link") {
            jsonBody(LinkAccount.serializer(), LinkAccount(linked.recoveryCode))
        }
        assertThat(again.status).isEqualTo(HttpStatusCode.OK)
    }

    @Test
    fun aRouteWithoutAGoodBearerTokenIs401() = serverTest {
        val good = account()
        suspend fun check(response: HttpResponse) {
            assertThat(response.status).isEqualTo(HttpStatusCode.Unauthorized)
            assertThat(response.headers[HttpHeaders.WWWAuthenticate]).contains("Bearer")
            assertThat(response.error()).isEqualTo("unauthorized")
        }
        check(http.get("/v1/me"))
        check(http.get("/v1/me") { bearerAuth("not-a-real-token-at-all") })
        check(http.get("/v1/me") { header(HttpHeaders.Authorization, "Basic ${good.token}") })
        check(http.get("/v1/me?token=${good.token}"))
        check(http.get("/v1/me/games"))
        check(http.patch("/v1/me"))
        check(http.delete("/v1/me"))
        check(http.post("/v1/rooms"))
        check(http.post("/v1/rooms/ABCDEFGH/join"))
        check(http.post("/v1/rooms/ABCDEFGH/leave"))
    }

    @Test
    fun aRenameIsKeptAndABadOneRefused() = serverTest {
        val created = account()
        val renamed = http.patch("/v1/me") {
            bearerAuth(created.token)
            jsonBody(Rename.serializer(), Rename("New name"))
        }
        assertThat(Me.serializer().parse(renamed.bodyAsText()).name).isEqualTo("New name")
        assertThat(store.account(created.accountId)?.name).isEqualTo("New name")

        val refused = http.patch("/v1/me") {
            bearerAuth(created.token)
            jsonBody(Rename.serializer(), Rename("<b>"))
        }
        assertThat(refused.status).isEqualTo(HttpStatusCode.BadRequest)
        assertThat(store.account(created.accountId)?.name).isEqualTo("New name")
    }

    @Test
    fun deletingAnonymisesRevokesAndKeepsMatchRows() = serverTest {
        val created = account()
        val game = StoreFixtures.game("kept")
        store.createGame(game, StoreFixtures.seats("kept"))
        val match = StoreFixtures.match("m", 1, 1)
        store.finish("kept", GameResult(2, rated = true), match)

        val response = http.delete("/v1/me") { bearerAuth(created.token) }
        assertThat(response.status).isEqualTo(HttpStatusCode.NoContent)

        assertThat(store.account(created.accountId)?.name).isEqualTo("Former player")
        assertThat(store.account(created.accountId)?.deletedAt).isNotNull()
        assertThat(http.get("/v1/me") { bearerAuth(created.token) }.status).isEqualTo(HttpStatusCode.Unauthorized)
        val link = http.post("/v1/accounts/link") {
            jsonBody(LinkAccount.serializer(), LinkAccount(created.recoveryCode))
        }
        assertThat(link.status).isEqualTo(HttpStatusCode.Unauthorized)
        assertThat(store.matches()).containsExactly(match)
    }

    @Test
    fun deletingGivesUpSeatsInRoomsAndGames() = serverTest {
        val started = startedGame()
        val code = room(started.red.token, teamSize = 2)
        http.delete("/v1/me") { bearerAuth(started.red.token) }
        assertThat(rooms.left).containsExactly(started.gameId to started.red.accountId)
        assertThat(store.room(code)?.seats?.mapNotNull { it.accountId }).isEmpty()
    }

    @Test
    fun myGamesListsWhatToGoBackTo() = serverTest {
        val started = startedGame()
        rooms.games.getValue(started.gameId).let { game ->
            store.createGame(
                StoreFixtures.game(game.id).copy(roomCode = game.roomCode, setup = game.setup),
                game.players.mapIndexed { i, player ->
                    GameSeatRow(
                        game.id,
                        player.seat,
                        player.accountId,
                        player.team,
                        handicap = false,
                        seatOrder = i,
                    )
                },
            )
        }
        val games = MyGames.serializer()
            .parse(http.get("/v1/me/games") { bearerAuth(started.blue.token) }.bodyAsText()).games
        val only = games.single()
        assertThat(only.gameId).isEqualTo(started.gameId)
        assertThat(only.mapId).isEqualTo("forgotten-island")
        assertThat(only.seats.map { it.accountId })
            .containsExactly(started.red.accountId, started.blue.accountId).inOrder()
        assertThat(only.seats.all { it.name.isNotEmpty() }).isTrue()

        val other = account()
        val none = MyGames.serializer().parse(http.get("/v1/me/games") { bearerAuth(other.token) }.bodyAsText())
        assertThat(none.games).isEmpty()
    }
}
