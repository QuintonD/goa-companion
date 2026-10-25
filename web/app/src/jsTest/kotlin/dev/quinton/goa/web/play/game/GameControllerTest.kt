package dev.quinton.goa.web.play.game

import dev.quinton.goa.core.data.EditClock
import dev.quinton.goa.core.data.game.AiPace
import dev.quinton.goa.core.data.game.GameInProgress
import dev.quinton.goa.core.data.game.StoredGame
import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.Controller
import dev.quinton.goa.core.game.GameLength
import dev.quinton.goa.core.game.GameSetup
import dev.quinton.goa.core.game.SeatSetup
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.testing.FakeGameSaveRepository
import dev.quinton.goa.feature.board.play.game.GameAction
import dev.quinton.goa.feature.board.play.game.GameUiState
import dev.quinton.goa.feature.board.play.game.NoGame
import dev.quinton.goa.feature.board.play.game.PlayUi
import dev.quinton.goa.feature.board.play.game.TurnStatus
import dev.quinton.goa.web.engine.BundledData
import dev.quinton.goa.web.engine.EngineHost
import dev.quinton.goa.web.engine.EngineJson
import dev.quinton.goa.web.engine.EngineMessage
import dev.quinton.goa.web.engine.EngineReply
import dev.quinton.goa.web.engine.EngineRequest
import dev.quinton.goa.web.engine.EngineResponse
import dev.quinton.goa.web.play.data.EngineChannel
import dev.quinton.goa.web.play.data.EngineClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

private fun shipped(path: String): String {
    val root = js("process.env.GOA_REPO_ROOT") as String? ?: error("GOA_REPO_ROOT is not set")
    val fs = js("require('fs')")
    return fs.readFileSync("$root/app/src/main/assets/$path", "utf8") as String
}

/** An engine "worker" in the test: the real host, answering through the same JSON, asynchronously. */
private class HostChannel(private val scope: CoroutineScope, private val silent: Boolean = false) : EngineChannel {
    val host = EngineHost { BundledData.load { shipped(it) } }
    var requests = 0
    override var onMessage: (String) -> Unit = {}
    override var onFailure: (String) -> Unit = {}
    var terminated = false

    override fun post(message: String) {
        requests++
        if (silent) return
        scope.launch {
            val m = EngineJson.decodeFromString(EngineMessage.serializer(), message)
            val reply = EngineReply(m.id, host.handle(m.request))
            if (!terminated) onMessage(EngineJson.encodeToString(EngineReply.serializer(), reply))
        }
    }

    override fun terminate() {
        terminated = true
    }
}

/**
 * The web game's controller against the real engine host (in-process, through the worker's JSON):
 * the save on every answer, the computer driven between the person's questions, take back, a fresh
 * worker rebuilt from the save, the coach's 300 ms wait.
 */
class GameControllerTest {
    private val setup = GameSetup(
        mapId = "forgotten-island",
        seats = listOf(
            SeatSetup("brogan", Team.RED, Controller.Human),
            SeatSetup("arien", Team.RED, Controller.Ai(AiLevel.EASY)),
            SeatSetup("wasp", Team.BLUE, Controller.Ai(AiLevel.EASY)),
            SeatSetup("tigerclaw", Team.BLUE, Controller.Ai(AiLevel.EASY)),
        ),
        length = GameLength.QUICK,
        tieBreaker = Team.RED,
    )

    private class Rig(
        val controller: GameController,
        val saves: FakeGameSaveRepository,
        val engine: EngineClient,
        val coach: EngineClient,
        val channels: MutableList<HostChannel>,
        val coachChannels: MutableList<HostChannel>,
    )

    private suspend fun TestScope.rig(coachOn: Boolean = false, coachSilent: Boolean = false): Rig {
        val starter = EngineHost { BundledData.load { shipped(it) } }
        val started = (starter.handle(EngineRequest.GameStart(setup)) as EngineResponse.Game).game
        val saves = FakeGameSaveRepository(
            StoredGame.Saved(GameInProgress(started.saved, coach = coachOn, pace = AiPace.FAST)),
        )
        val channels = mutableListOf<HostChannel>()
        val coachChannels = mutableListOf<HostChannel>()
        val engine = EngineClient { HostChannel(backgroundScope).also { channels += it } }
        val coach = EngineClient { HostChannel(backgroundScope, silent = coachSilent).also { coachChannels += it } }
        val (catalog, maps) = BundledData.load { shipped(it) }
        val controller = GameController(
            engine = engine,
            coach = coach,
            saves = saves,
            catalog = { catalog },
            maps = { maps },
            clock = EditClock { 1_000L },
            scope = backgroundScope,
        )
        controller.start()
        return Rig(controller, saves, engine, coach, channels, coachChannels)
    }

    private suspend fun GameController.yourTurn(): PlayUi = (
        uiState.first { s ->
            val play = (s as? GameUiState.Playing)?.play
            play != null && play.status == TurnStatus.YourTurn && !play.busy
        } as GameUiState.Playing
        ).play

    @Test
    fun aGameResumesFromTheSaveAndEveryAnswerIsSaved() = runTest {
        val rig = rig()
        val first = rig.controller.yourTurn()
        val before = rig.saves.saves.size
        val decision = first.table.decision!!
        rig.controller.onAction(GameAction.Answer(decision.options.first()))
        val next = rig.controller.yourTurn()
        assertTrue(next.table.decision!!.id != decision.id, "the game moved on")
        assertTrue(rig.saves.saves.size > before, "the answer (and the computer's) were saved")
        assertEquals(1_000L, rig.saves.saves.last().game.savedAt)
        assertTrue(next.canTakeBack)
    }

    @Test
    fun takeBackReturnsToThePersonsLastQuestionAndCountsIt() = runTest {
        val rig = rig()
        val first = rig.controller.yourTurn()
        val decision = first.table.decision!!
        rig.controller.onAction(GameAction.Answer(decision.options.first()))
        rig.controller.yourTurn()
        rig.controller.onAction(GameAction.TakeBack)
        val back = (
            rig.controller.uiState.first { s ->
                val play = (s as? GameUiState.Playing)?.play
                play != null && play.takeBacks == 1 && play.status == TurnStatus.YourTurn
            } as GameUiState.Playing
            ).play
        assertEquals(decision.id, back.table.decision?.id)
        assertEquals(1, (rig.saves.state.value as StoredGame.Saved).game.takeBacks)
    }

    @Test
    fun aFreshWorkerIsRebuiltFromTheSave() = runTest {
        val rig = rig()
        val first = rig.controller.yourTurn()
        // The worker is stopped (a search cancelled): the next answer goes to a new one.
        rig.engine.reset()
        rig.controller.onAction(GameAction.Answer(first.table.decision!!.options.first()))
        val next = rig.controller.yourTurn()
        assertTrue(next.table.decision!!.id != first.table.decision!!.id)
        assertEquals(2, rig.channels.size, "a second worker was started")
    }

    @Test
    fun anAnswerWaitsForTheCoachAtMostAMomentThenGoesThrough() = runTest {
        val rig = rig(coachOn = true, coachSilent = true)
        val first = rig.controller.yourTurn()
        val start = testScheduler.currentTime
        rig.controller.onAction(GameAction.Answer(first.table.decision!!.options.first()))
        val next = rig.controller.yourTurn()
        assertTrue(next.table.decision!!.id != first.table.decision!!.id, "answered without a nudge")
        assertTrue(testScheduler.currentTime - start >= 300L, "it waited for the coach")
        assertTrue(rig.coachChannels.first().terminated, "the coach's searching worker was stopped")
    }

    @Test
    fun nothingSavedSaysSo() = runTest {
        val saves = FakeGameSaveRepository()
        val controller = GameController(
            engine = EngineClient { HostChannel(backgroundScope) },
            coach = EngineClient { HostChannel(backgroundScope) },
            saves = saves,
            catalog = { error("not needed") },
            maps = { error("not needed") },
            clock = EditClock { 0L },
            scope = backgroundScope,
        )
        controller.start()
        val state = controller.uiState.first { it !is GameUiState.Loading }
        assertIs<GameUiState.Unavailable>(state)
        assertEquals(NoGame.NONE, state.why)
    }
}
