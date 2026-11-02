package dev.quinton.goa.server.game.http

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.data.EditClock
import dev.quinton.goa.core.game.Controller
import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.core.game.GameSetup
import dev.quinton.goa.core.game.SeatSetup
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.online.AccountCreated
import dev.quinton.goa.core.online.ClientMessage
import dev.quinton.goa.core.online.CreateAccount
import dev.quinton.goa.core.online.SeatPlayer
import dev.quinton.goa.core.online.ServerMessage
import dev.quinton.goa.feature.board.play.game.GameUiState
import dev.quinton.goa.feature.board.play.online.GameConnection
import dev.quinton.goa.feature.board.play.online.GameConnector
import dev.quinton.goa.feature.board.play.online.OnlineGameDriver
import dev.quinton.goa.feature.board.play.online.OnlineIdentity
import dev.quinton.goa.feature.board.play.online.OnlineIdentityStore
import dev.quinton.goa.feature.board.play.online.OnlineState
import dev.quinton.goa.feature.board.play.online.decodeServerFrame
import dev.quinton.goa.feature.board.play.online.encodeClientFrame
import dev.quinton.goa.server.game.FakeRooms
import dev.quinton.goa.server.game.GameServerConfig
import dev.quinton.goa.server.game.board
import dev.quinton.goa.server.game.json
import dev.quinton.goa.server.game.parse
import dev.quinton.goa.server.game.room.GameRooms
import dev.quinton.goa.server.game.room.LeaderboardSource
import dev.quinton.goa.server.game.room.SeatLink
import dev.quinton.goa.server.game.room.SeatSink
import dev.quinton.goa.server.game.store.SqliteGameStore
import dev.quinton.goa.server.game.testEngine
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.WebSocket
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.CompletionStage
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test

/** A game whose log is very long: the client lacks all of it, and neither a real socket nor the driver may mind. */
class LongLogTest {
    private val setup = GameSetup(
        mapId = "forgotten-island",
        seats = listOf(SeatSetup("wasp", Team.RED, Controller.Human), SeatSetup("arien", Team.BLUE, Controller.Human)),
        tieBreaker = Team.RED,
    )
    private val session = testEngine.newGame(setup)

    private inner class LongGame(private val events: Int, private val rooms: FakeRooms = FakeRooms()) :
        GameRooms by rooms {
        override suspend fun gamesOf(accountId: String): List<String> = listOf(GAME)

        override suspend fun connect(gameId: String, accountId: String, haveEvents: Int, sink: SeatSink): SeatLink? {
            val players = setup.seats.map { SeatPlayer(it.heroId, accountId, it.heroId, it.team) }
            sink.send(ServerMessage.GameStart(GAME, setup, players, setOf("wasp"), ranked = false))
            val log = List(events) { GameEvent.Note("Line $it of the log") }
            sink.send(
                ServerMessage.Table(
                    gameId = GAME,
                    seq = 1,
                    view = session.view("wasp"),
                    decision = session.pending,
                    eventsFrom = haveEvents,
                    events = log.drop(haveEvents),
                ),
            )
            return SeatLink {}
        }
    }

    /** The JDK's client under the real driver, as a phone would be. */
    private class RealConnection(private val socket: WebSocket, frames: Channel<ServerMessage>) : GameConnection {
        override val incoming: Flow<ServerMessage> = frames.receiveAsFlow()

        override suspend fun send(message: ClientMessage) {
            socket.sendText(encodeClientFrame(message), true).join()
        }

        override fun close() {
            socket.abort()
        }
    }

    private class Listener(private val frames: Channel<ServerMessage>, val seen: MutableList<ServerMessage>) :
        WebSocket.Listener {
        private val text = StringBuilder()

        override fun onText(socket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
            text.append(data)
            if (last) {
                val frame = decodeServerFrame(text.toString())
                text.clear()
                seen += frame
                frames.trySend(frame)
            }
            socket.request(1)
            return null
        }

        override fun onClose(socket: WebSocket, statusCode: Int, reason: String): CompletionStage<*>? {
            frames.close()
            return null
        }

        override fun onError(socket: WebSocket, error: Throwable) {
            frames.close(error)
        }
    }

    @Test
    fun `nine thousand events reach the real driver over a real socket, in pages`() = runBlocking {
        val dir = Files.createTempDirectory("goa-long-log")
        val store = SqliteGameStore.open(dir)
        val config = GameServerConfig(dir, Paths.get("unused"))
        val server = embeddedServer(CIO, port = 0, host = "127.0.0.1") {
            gameServerModule(config, store, testEngine, LongGame(EVENTS), LeaderboardSource { board() })
        }.start(wait = false)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
        try {
            val port = server.engine.resolvedConnectors().first().port
            val http = HttpClient.newHttpClient()
            val created = http.send(
                HttpRequest.newBuilder(URI("http://127.0.0.1:$port/v1/accounts"))
                    .POST(HttpRequest.BodyPublishers.ofString(CreateAccount.serializer().json(CreateAccount("Someone"))))
                    .build(),
                HttpResponse.BodyHandlers.ofString(),
            )
            val account = AccountCreated.serializer().parse(created.body())
            val seen = CopyOnWriteArrayList<ServerMessage>()
            val connector = GameConnector { hello ->
                val frames = Channel<ServerMessage>(Channel.UNLIMITED)
                val socket = http.newWebSocketBuilder()
                    .buildAsync(URI("ws://127.0.0.1:$port/v1/play"), Listener(frames, seen)).join()
                RealConnection(socket, frames).also { it.send(hello) }
            }
            val driver = OnlineGameDriver(
                gameId = GAME,
                connector = connector,
                identities = object : OnlineIdentityStore {
                    override suspend fun load() = OnlineIdentity(account.accountId, account.token, "Someone")

                    override suspend fun save(identity: OnlineIdentity) = Unit

                    override suspend fun clear() = Unit
                },
                catalog = { testEngine.data.catalog },
                maps = { testEngine.data.maps.values.toList() },
                clock = EditClock { System.currentTimeMillis() },
                scope = scope,
                client = "test/1",
            )
            driver.start()
            val shown = withTimeout(WAIT_MILLIS) {
                driver.state.first { it is OnlineState.InGame && ((it.game as? GameUiState.Playing) != null) }
            } as OnlineState.InGame
            val play = (shown.game as GameUiState.Playing).play

            val tables = seen.filterIsInstance<ServerMessage.Table>()
            assertThat(tables.size).isAtLeast(EVENTS / PAGE)
            assertThat(tables.maxOf { it.events.size }).isAtMost(PAGE)
            assertThat(tables.dropLast(1).all { it.more }).isTrue()
            assertThat(tables.last().more).isFalse()
            assertThat(play.log).hasSize(EVENTS)
            assertThat(play.log.first().text).isEqualTo("Line 0 of the log")
            assertThat(play.log.last().text).isEqualTo("Line ${EVENTS - 1} of the log")
            // It is a look at a game under way, not a reading of the whole log.
            assertThat(play.announcement).isNull()
            driver.state.value.let { assertThat(it).isInstanceOf(OnlineState.InGame::class.java) }
        } finally {
            scope.cancel()
            server.stop(0, 0)
            store.close()
            dir.toFile().deleteRecursively()
        }
    }

    private companion object {
        const val GAME = "long-game"
        const val EVENTS = 9_000
        const val PAGE = 1_000
        const val WAIT_MILLIS = 30_000L
    }
}
