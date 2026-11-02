package dev.quinton.goa.web.play.online

import dev.quinton.goa.core.data.EditClock
import dev.quinton.goa.core.game.Controller
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionContext
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.core.game.GameSetup
import dev.quinton.goa.core.game.GameView
import dev.quinton.goa.core.game.SeatSetup
import dev.quinton.goa.core.game.WinReason
import dev.quinton.goa.core.game.engine.GameEngine
import dev.quinton.goa.core.model.board.BoardMap
import dev.quinton.goa.core.model.catalog.CatalogFiles
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.online.ClientMessage
import dev.quinton.goa.core.online.OnlineProtocol
import dev.quinton.goa.core.online.SeatPlayer
import dev.quinton.goa.core.online.ServerMessage
import dev.quinton.goa.feature.board.play.game.GameAction
import dev.quinton.goa.feature.board.play.game.GameUiState
import dev.quinton.goa.feature.board.play.game.PlayUi
import dev.quinton.goa.feature.board.play.game.TurnStatus
import dev.quinton.goa.feature.board.play.online.OnlineGameDriver
import dev.quinton.goa.feature.board.play.online.OnlineIdentity
import dev.quinton.goa.feature.board.play.online.OnlineState
import dev.quinton.goa.feature.board.play.online.decodeServerFrame
import dev.quinton.goa.web.play.shipped
import dev.quinton.goa.web.storage.MemoryKeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val FIXTURE = "web/e2e/tests/fixtures/online-game.json"
private const val GAME = "game-1"
private const val TICKETS = 1_000L
private const val STEPS = 10

/**
 * One step of the recorded game: for each seat its view and question, and the events that step adds. The fake
 * game server of the Playwright suite (`online-helpers.ts`) sends these, as `SeatProjector` must: each seat's own
 * view, its own question with a ticket for an id, a stub for anyone else's.
 */
@Serializable
private data class RecordedStep(
    val asked: String,
    val ticket: Long,
    val kind: String,
    val events: List<JsonElement>,
    val views: Map<String, JsonElement>,
    val decisions: Map<String, JsonElement>,
    val locked: List<String>,
)

@Serializable
private data class RecordedGame(val start: Map<String, JsonElement>, val steps: List<RecordedStep>, val end: JsonElement)

private fun readRepo(path: String): String {
    val root = js("process.env.GOA_REPO_ROOT") as String
    return js("process.getBuiltinModule('fs')").readFileSync("$root/$path", "utf8") as String
}

private fun catalog() = CatalogFiles.load(::shipped)

private fun maps(): List<BoardMap> = CatalogFiles.parseMapIndex(shipped(CatalogFiles.MAPS_INDEX_PATH)).map {
    CatalogFiles.parseMap(it, shipped(CatalogFiles.mapPath(it)))
}

private fun stub(decision: Decision) =
    decision.copy(id = 0, prompt = "", sourceCardId = null, options = emptyList(), context = DecisionContext())

/** Plays the real engine and records it; run with GOA_WRITE_ONLINE_FIXTURE=1 when the wire types change. */
private fun record(): RecordedGame {
    val setup = GameSetup(
        mapId = "forgotten-island",
        seats = listOf("wasp" to Team.RED, "arien" to Team.BLUE).map { (id, team) -> SeatSetup(id, team, Controller.Human) },
        tieBreaker = Team.RED,
    )
    val session = GameEngine(catalog(), maps()).newGame(setup)
    val json = OnlineProtocol.json
    val players = listOf(
        SeatPlayer("wasp", "acc-host", "HOSTNAME", Team.RED),
        SeatPlayer("arien", "acc-guest", "GUESTNAME", Team.BLUE),
    )
    val start = setup.seats.associate { seat ->
        seat.heroId to json.encodeToJsonElement(
            ServerMessage.serializer(),
            ServerMessage.GameStart(GAME, setup, players, setOf(seat.heroId), ranked = true),
        )
    }
    val steps = mutableListOf<RecordedStep>()
    var have = 0
    var locked = emptyList<String>()
    repeat(STEPS) {
        val pending = checkNotNull(session.pending) { "the recorded game ended early" }
        val ticket = pending.id + TICKETS
        steps += RecordedStep(
            asked = pending.seat,
            ticket = ticket,
            kind = pending.kind.name,
            events = session.events.drop(have).map { json.encodeToJsonElement(GameEvent.serializer(), it) },
            views = setup.seats.associate { it.heroId to json.encodeToJsonElement(GameView.serializer(), session.view(it.heroId)) },
            decisions = setup.seats.associate {
                val own = if (it.heroId == pending.seat) pending.copy(id = ticket) else stub(pending)
                it.heroId to json.encodeToJsonElement(Decision.serializer(), own)
            },
            locked = locked.takeIf { pending.kind == DecisionKind.PLAY_CARD }.orEmpty(),
        )
        have = session.events.size
        locked = if (pending.kind == DecisionKind.PLAY_CARD) locked + pending.seat else emptyList()
        session.answer(pending.id, pending.options.first())
    }
    val end = ServerMessage.End(GAME, Team.RED, WinReason.entries.first(), rated = true)
    return RecordedGame(start, steps, json.encodeToJsonElement(ServerMessage.serializer(), end))
}

private fun write(game: RecordedGame) {
    val root = js("process.env.GOA_REPO_ROOT") as String
    js("process.getBuiltinModule('fs')").writeFileSync(
        "$root/$FIXTURE",
        OnlineProtocol.json.encodeToString(RecordedGame.serializer(), game) + "\n",
    )
}

private fun loadRecorded(): RecordedGame {
    if (js("process.env.GOA_WRITE_ONLINE_FIXTURE") as String? != null) write(record())
    return OnlineProtocol.json.decodeFromString(RecordedGame.serializer(), readRepo(FIXTURE))
}

/** The frame the fake game server sends for [seat] at [step], asking from [have] events on. */
private fun table(game: RecordedGame, seat: String, step: Int, have: Int): String {
    val all = game.steps.take(step + 1).flatMap { it.events }
    val at = game.steps[step]
    val fields = mapOf(
        "type" to JsonPrimitive("game.table"),
        "gameId" to JsonPrimitive(GAME),
        "seq" to JsonPrimitive(step + 1),
        "view" to at.views.getValue(seat),
        "decision" to at.decisions.getValue(seat),
        "eventsFrom" to JsonPrimitive(have),
        "events" to JsonArray(all.drop(have)),
        "locked" to JsonArray(at.locked.map(::JsonPrimitive)),
    )
    return JsonObject(fields).toString()
}

/**
 * The frames the Playwright suite's fake server sends, kept honest: they decode with this build's wire types, and
 * the real driver and connector turn them into the table, the question, the wait, the flow and the result.
 */
class OnlineRecordedGameTest {
    private class Sockets {
        val opened = mutableListOf<BrowserSocketDouble>()

        fun open(): BrowserSocket = BrowserSocketDouble().also { opened += it }
    }

    private class BrowserSocketDouble : BrowserSocket {
        override var onOpen: () -> Unit = {}
        override var onText: (String) -> Unit = {}
        override var onClosed: () -> Unit = {}
        val sent = mutableListOf<ClientMessage>()

        override fun send(text: String) {
            sent += OnlineProtocol.json.decodeFromString(ClientMessage.serializer(), text)
        }

        override fun close() = Unit
    }

    private suspend fun TestScope.driver(sockets: Sockets, identity: OnlineIdentity): OnlineGameDriver {
        val identities = WebOnlineIdentityStore(MemoryKeyValueStore())
        identities.save(identity)
        return OnlineGameDriver(
            gameId = GAME,
            connector = WebGameConnector({ "https://game.example/game" }) { sockets.open() },
            identities = identities,
            catalog = { catalog() },
            maps = { maps() },
            clock = EditClock { currentTime },
            scope = backgroundScope,
            client = "web/test",
        )
    }

    private fun OnlineGameDriver.play(): PlayUi =
        ((state.value as OnlineState.InGame).game as GameUiState.Playing).play

    @Test
    fun everyRecordedFrameDecodes() {
        val game = loadRecorded()
        assertEquals(STEPS, game.steps.size)
        game.start.values.forEach { assertIs<ServerMessage.GameStart>(decodeServerFrame(it.toString())) }
        game.steps.indices.forEach { step ->
            listOf("wasp", "arien").forEach { assertIs<ServerMessage.Table>(decodeServerFrame(table(game, it, step, 0))) }
        }
        assertIs<ServerMessage.End>(decodeServerFrame(game.end.toString()))
    }

    @Test
    fun theRecordedFramesDriveTheWholeScreen() = runTest {
        val game = loadRecorded()
        val sockets = Sockets()
        val driver = driver(sockets, OnlineIdentity("acc-host", "tok", "HOSTNAME"))
        driver.start()
        runCurrent()
        val socket = sockets.opened.single()
        socket.onOpen()
        runCurrent()
        val hello = assertIs<ClientMessage.Hello>(socket.sent.first())
        assertEquals("tok", hello.token)
        socket.onText(game.start.getValue("wasp").toString())
        socket.onText(table(game, "wasp", 0, 0))
        runCurrent()

        // The person's own question, with the names of the people playing.
        val asked = driver.play()
        val firstSeat = game.steps[0].asked
        if (firstSeat == "wasp") assertEquals(TurnStatus.YourTurn, asked.status)
        assertEquals("GUESTNAME", asked.table.players["arien"])
        assertNotNull(asked.online)

        // Answering goes out with the ticket, once.
        val own = game.steps.indexOfFirst { it.asked == "wasp" }
        socket.onText(table(game, "wasp", own, game.steps.take(1).flatMap { it.events }.size))
        runCurrent()
        val decision = assertNotNull(driver.play().table.decision)
        driver.onAction(GameAction.Answer(decision.options.first()))
        runCurrent()
        val answer = socket.sent.filterIsInstance<ClientMessage.Answer>().single()
        assertEquals(game.steps[own].ticket, answer.ticket)

        // The rest of the recording: the flow fills, and the result comes with whether it counted.
        (own + 1 until game.steps.size).forEach { step ->
            val have = game.steps.take(step).flatMap { it.events }.size
            socket.onText(table(game, "wasp", step, have))
            runCurrent()
        }
        assertTrue(driver.play().log.isNotEmpty())
        socket.onText(game.end.toString())
        runCurrent()
        val finished = driver.play()
        assertEquals(TurnStatus.Over, finished.status)
        assertEquals(Team.RED, finished.over?.winner)
        assertEquals(true, finished.over?.youWon)
        assertEquals(true, finished.online?.rated)
    }
}
