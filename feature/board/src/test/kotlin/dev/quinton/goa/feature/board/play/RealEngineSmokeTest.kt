package dev.quinton.goa.feature.board.play

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.data.EditClock
import dev.quinton.goa.core.data.game.AiPace
import dev.quinton.goa.core.data.game.AiPlayers
import dev.quinton.goa.core.data.game.GameInProgress
import dev.quinton.goa.core.data.game.StoredGame
import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.AiPlayer
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Controller
import dev.quinton.goa.core.game.Decision
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
import dev.quinton.goa.feature.board.play.game.GameAction
import dev.quinton.goa.feature.board.play.game.GameUiState
import dev.quinton.goa.feature.board.play.game.GameViewModel
import dev.quinton.goa.feature.board.play.game.TurnStatus
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * The play screen's ViewModel on the real rules engine (core:game), not the fake: the person
 * answers with the first option shown, the computer seats too. It checks that every question the
 * engine asks can be shown and answered through the UI's model, and that saves resume. Skipped
 * while fewer than four heroes are playable.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RealEngineSmokeTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private object FirstOption : AiPlayer {
        override fun choose(session: GameSession, decision: Decision, cancelled: () -> Boolean): Choice =
            decision.options.first()
    }

    @Test
    fun `a real game is played through the table's model and resumes from its save`() = runTest(dispatcher) {
        val engine = GameEngine(bundledCatalog, bundledMaps)
        val heroes = engine.playableHeroes.toList().take(4)
        assumeTrue("Needs four playable heroes", heroes.size == 4)
        val map = engine.playableMaps.first()
        val setup = GameSetup(
            mapId = map,
            seats = listOf(
                SeatSetup(heroes[0], Team.RED, Controller.Human),
                SeatSetup(heroes[1], Team.RED, Controller.Ai(AiLevel.EASY)),
                SeatSetup(heroes[2], Team.BLUE, Controller.Ai(AiLevel.EASY)),
                SeatSetup(heroes[3], Team.BLUE, Controller.Ai(AiLevel.EASY)),
            ),
            tieBreaker = Team.RED,
        )
        val saves = FakeGameSaveRepository(
            StoredGame.Saved(GameInProgress(engine.newGame(setup).save(), coach = false, pace = AiPace.FAST)),
        )
        fun viewModel() = GameViewModel(
            FakeCatalogRepository(bundledCatalog),
            FakeBoardMapRepository(bundledMaps),
            saves,
            { engine },
            AiPlayers { FirstOption },
            FakeCoach(silent = true),
            EditClock { 0L },
            dispatcher,
            Random(0),
        )
        val vm = viewModel()
        advanceUntilIdle()
        var answers = 0
        while (answers < MAX_ANSWERS) {
            val play = (vm.uiState.value as GameUiState.Playing).play
            if (play.status == TurnStatus.Over) break
            assertThat(play.status).isEqualTo(TurnStatus.YourTurn)
            val decision = play.table.decision!!
            // Everything the engine offers is shown somewhere: on the board, as a card, or a button.
            val shown = play.table.spaceOptions.values + play.table.pieceOptions.values +
                play.table.cardOptions + play.table.buttonOptions
            assertThat(shown.toSet()).containsExactlyElementsIn(decision.options)
            decision.options.forEach { assertThat(play.table.label(it)).isNotEmpty() }
            vm.onAction(GameAction.Answer(decision.options.first()))
            advanceUntilIdle()
            answers++
        }
        assertThat(answers).isGreaterThan(0)
        // The save resumes at the same question.
        val before = (vm.uiState.value as GameUiState.Playing).play.table
        val again = viewModel()
        advanceUntilIdle()
        val after = (again.uiState.value as GameUiState.Playing).play.table
        assertThat(after.decision).isEqualTo(before.decision)
        assertThat(after.view.round).isEqualTo(before.view.round)
    }

    private companion object {
        const val MAX_ANSWERS = 60
    }
}
