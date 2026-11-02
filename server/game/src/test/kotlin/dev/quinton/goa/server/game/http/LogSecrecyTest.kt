package dev.quinton.goa.server.game.http

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.online.ClientMessage
import dev.quinton.goa.core.online.LinkAccount
import dev.quinton.goa.core.online.ServerMessage
import dev.quinton.goa.server.game.hello
import dev.quinton.goa.server.game.jsonBody
import dev.quinton.goa.server.game.nextOf
import dev.quinton.goa.server.game.play
import dev.quinton.goa.server.game.sendMessage
import dev.quinton.goa.server.game.serverTest
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.websocket.Frame
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import org.junit.Test
import org.slf4j.LoggerFactory

/**
 * A whole game on the fake with every logger at debug: nothing a person must keep to themselves reaches
 * the log. The logger is checked to be capturing first, or the test would pass by hearing nothing.
 */
class LogSecrecyTest {
    @Test
    fun eachRequestIsLoggedWithTheStatusItReallyGot() {
        val logs = capturing {
            serverTest(configure = { it.copy(maxOpenRooms = 1) }) {
                val token = account().token
                createRoom(token, teamSize = 9)
                room(token)
                val full = createRoom(token)
                assertThat(full.status.value).isEqualTo(503)
                http.post("/v1/rooms/ZZZZZZZZ/join") { bearerAuth(token) }
                http.get("/v1/me")
                http.post("/v1/accounts") { setBody("{not json") }
                http.get("/game/v1/nothing/ABCDEFGH")
            }
        }
        for (line in listOf(
            "POST /v1/accounts 201",
            "POST /v1/rooms 400",
            "POST /v1/rooms 201",
            "POST /v1/rooms 503",
            "POST /v1/rooms/{code}/join 404",
            "GET /v1/me 401",
            "POST /v1/accounts 400",
            "GET (other) 404",
        )) {
            assertThat(logs).contains(line)
        }
        // No request above failed for real, whatever Ktor's pipeline made of it.
        assertThat(logs).doesNotContain(" 500 ")
        assertThat(logs).doesNotContain("ZZZZZZZZ")
    }

    @Test
    fun aFullGameLeavesNoTokenCodeChoiceOrHandInTheLogs() {
        val secrets = HashSet<String>()
        var decision: Decision? = null
        val logs = capturing {
            LoggerFactory.getLogger("goa-game").info("capture-check")
            serverTest {
                http.get("/v1/health")
                val started = startedGame()
                secrets += listOf(started.red.token, started.red.recoveryCode)
                secrets += listOf(started.blue.token, started.blue.recoveryCode)

                val game = rooms.games.getValue(started.gameId)
                val pending = requireNotNull(game.session.pending)
                val seat = pending.seat
                val account = game.players.first { it.seat == seat }.accountId
                val token = if (account == started.red.accountId) started.red.token else started.blue.token
                ws.play(token) {
                    val table = nextOf<ServerMessage.Table> { it.decision != null }
                    decision = table.decision
                    secrets += table.view.heroes.first { it.heroId == seat }.hand.orEmpty()
                    val choice = requireNotNull(table.decision).options.first()
                    secrets += choice.toString()
                    sendMessage(ClientMessage.Answer(started.gameId, 1, choice))
                    nextOf<ServerMessage.Ack>()
                    // A marked choice the room rejects, and frames the socket can't read at all.
                    sendMessage(ClientMessage.Answer(started.gameId, 2, Choice.Unit(MARKED_CHOICE)))
                    nextOf<ServerMessage.Ack>()
                    sendMessage(ClientMessage.AskReview(started.gameId))
                    nextOf<ServerMessage.Reviewed>()
                    rooms.sendAll(game) { ServerMessage.End(started.gameId, null, null, rated = false) }
                    nextOf<ServerMessage.End>()
                }
                ws.play(token) {
                    send(Frame.Text("""{"type":"game.answer","gameId":"x","ticket":"$MARKED_FRAME"}"""))
                }
                http.post("/v1/accounts/link") {
                    jsonBody(LinkAccount.serializer(), LinkAccount(MARKED_CODE))
                }
                http.get("/v1/me?token=$MARKED_URL_TOKEN")
                ws.webSocket("/v1/play") {
                    sendMessage(hello(MARKED_TOKEN))
                    nextOf<ServerMessage.Refused>()
                }
                http.delete("/v1/me") { bearerAuth(token) }
            }
        }

        assertThat(logs).contains("capture-check")
        assertThat(logs).contains("/v1/play")
        assertThat(logs).contains("GET /v1/health 200") // Only logged at debug.
        assertThat(decision).isNotNull()
        assertThat(secrets).isNotEmpty()
        for (secret in secrets + setOf(MARKED_CHOICE, MARKED_FRAME, MARKED_CODE, MARKED_URL_TOKEN, MARKED_TOKEN)) {
            assertThat(logs).doesNotContain(secret)
        }
    }

    private companion object {
        const val MARKED_CHOICE = "marked-choice-3f9a"
        const val MARKED_FRAME = "marked-frame-3f9a"
        const val MARKED_CODE = "MARKEDCODE3F9A"
        const val MARKED_URL_TOKEN = "markedurltoken3f9a0000"
        const val MARKED_TOKEN = "markedhellotoken3f9a0000"
    }
}

private inline fun capturing(block: () -> Unit): String {
    val original = System.out
    val captured = ByteArrayOutputStream()
    val tee = PrintStream(captured, true, Charsets.UTF_8)
    System.setOut(tee)
    try {
        block()
    } finally {
        System.setOut(original)
    }
    tee.flush()
    return captured.toString(Charsets.UTF_8)
}
