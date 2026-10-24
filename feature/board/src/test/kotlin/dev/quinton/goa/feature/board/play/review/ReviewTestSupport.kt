package dev.quinton.goa.feature.board.play.review

import dev.quinton.goa.core.data.EditClock
import dev.quinton.goa.core.data.game.AiPace
import dev.quinton.goa.core.data.game.AiPlayers
import dev.quinton.goa.core.data.game.GameInProgress
import dev.quinton.goa.core.data.game.StoredGame
import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.Controller
import dev.quinton.goa.core.game.GamePhase
import dev.quinton.goa.core.game.GameSession
import dev.quinton.goa.core.game.GameSetup
import dev.quinton.goa.core.game.SeatSetup
import dev.quinton.goa.core.game.engine.GameEngine
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.testing.FakeBoardMapRepository
import dev.quinton.goa.core.testing.FakeCatalogRepository
import dev.quinton.goa.core.testing.FakeGameSaveRepository
import dev.quinton.goa.feature.board.bundledCatalog
import dev.quinton.goa.feature.board.bundledMaps
import dev.quinton.goa.feature.board.play.FakeCoach
import dev.quinton.goa.feature.board.play.game.GameUiState
import dev.quinton.goa.feature.board.play.game.GameViewModel
import dev.quinton.goa.feature.board.play.game.PlayUi
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/** The real engine on the bundled catalog and maps, built once. */
val reviewEngine: GameEngine by lazy { GameEngine(bundledCatalog, bundledMaps) }

/** The rulebook's first game (p.5): Wasp and Xargatha (Red) against Arien and Brogan (Blue); the person plays Wasp. */
fun reviewSetup(humans: Set<String> = setOf("wasp")) = GameSetup(
    mapId = "forgotten-island",
    seats = listOf(
        SeatSetup("wasp", Team.RED, controller("wasp", humans)),
        SeatSetup("xargatha", Team.RED, controller("xargatha", humans)),
        SeatSetup("arien", Team.BLUE, controller("arien", humans)),
        SeatSetup("brogan", Team.BLUE, controller("brogan", humans)),
    ),
    tieBreaker = Team.RED,
)

private fun controller(id: String, humans: Set<String>): Controller =
    if (id in humans) Controller.Human else Controller.Ai(AiLevel.EASY)

/**
 * A real game played by the Easy computer for every seat (seeded, so always the same game) until
 * [round]'s [turn], mid-resolution with someone already acted and the person's question up.
 */
fun reviewGameAt(round: Int, turn: Int, humans: Set<String> = setOf("wasp"), seed: Long = 1): GameSession {
    val game = reviewEngine.newGame(reviewSetup(humans))
    val ai = dev.quinton.goa.core.game.ai.AiPlayers.deterministic(AiLevel.EASY, seed)
    repeat(MAX_DECISIONS) {
        val d = game.pending ?: return game
        val v = game.view(humans.first())
        val there = v.round > round || (v.round == round && v.turn >= turn)
        val midTurn = v.phase == GamePhase.RESOLVE && v.actedThisTurn.isNotEmpty()
        if (there && midTurn && d.seat in humans) return game
        game.answer(d.id, ai.choose(game, d))
    }
    return game
}

/**
 * The real [GameViewModel] resumed from [game]'s save, on virtual time (the main dispatcher swapped
 * only while it runs); [steps] act on it (call the second argument to let it settle) before the
 * state is read.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun reviewViewModel(game: GameSession, steps: (GameViewModel, () -> Unit) -> Unit = { _, _ -> }): PlayUi {
    val scheduler = TestCoroutineScheduler()
    val dispatcher = StandardTestDispatcher(scheduler)
    Dispatchers.setMain(dispatcher)
    try {
        val saves = FakeGameSaveRepository(
            StoredGame.Saved(GameInProgress(game.save(), coach = false, pace = AiPace.FAST)),
        )
        val vm = GameViewModel(
            FakeCatalogRepository(bundledCatalog),
            FakeBoardMapRepository(bundledMaps),
            saves,
            { reviewEngine },
            AiPlayers { dev.quinton.goa.core.game.ai.AiPlayers.deterministic(it, 1) },
            FakeCoach(silent = true),
            EditClock { 0L },
            dispatcher,
            Random(0),
        )
        val settle = {
            scheduler.advanceTimeBy(SETTLE_MS)
            scheduler.runCurrent()
        }
        settle()
        steps(vm, settle)
        return (vm.uiState.value as GameUiState.Playing).play
    } finally {
        Dispatchers.resetMain()
    }
}

private const val MAX_DECISIONS = 4_000
private const val SETTLE_MS = 2_000L
