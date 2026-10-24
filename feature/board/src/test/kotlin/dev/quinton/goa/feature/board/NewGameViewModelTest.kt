package dev.quinton.goa.feature.board

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.data.EditClock
import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.Controller
import dev.quinton.goa.core.game.GameFactoryProvider
import dev.quinton.goa.core.game.GameLength
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.navigation.NewGameRoute
import dev.quinton.goa.core.testing.FakeBoardMapRepository
import dev.quinton.goa.core.testing.FakeCatalogRepository
import dev.quinton.goa.core.testing.FakeDraftRecordRepository
import dev.quinton.goa.core.testing.FakeMatchRepository
import dev.quinton.goa.core.testing.FakeSavedGameRepository
import dev.quinton.goa.core.testing.FakeSettingsRepository
import dev.quinton.goa.feature.board.play.newgame.NewGameAction
import dev.quinton.goa.feature.board.play.newgame.NewGameStep
import dev.quinton.goa.feature.board.play.newgame.NewGameUiState
import dev.quinton.goa.feature.board.play.newgame.NewGameViewModel
import dev.quinton.goa.feature.board.setup.NewBoardAction
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NewGameViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private val games = FakeSavedGameRepository()

    private fun TestScope.viewModel(
        route: NewGameRoute = NewGameRoute(),
        saved: SavedStateHandle = SavedStateHandle(),
    ) = NewGameViewModel(
        route,
        saved,
        FakeCatalogRepository(bundledCatalog),
        FakeBoardMapRepository(bundledMaps),
        FakeDraftRecordRepository(),
        FakeMatchRepository(),
        FakeSettingsRepository(),
        GameFactoryProvider { PlayTestData.engine },
        games,
        EditClock { 5_000L },
        Random(1),
        dispatcher,
        newId = { "new-game" },
    ).also { it.uiState.launchIn(backgroundScope) }

    private fun TestScope.ready(vm: NewGameViewModel): NewGameUiState.Ready {
        advanceUntilIdle()
        return vm.uiState.value as NewGameUiState.Ready
    }

    private fun NewGameViewModel.tap(vararg heroes: String) =
        heroes.forEach { onAction(NewGameAction.Lineup(NewBoardAction.Tap(it))) }

    @Test
    fun `only heroes the engine plays can be seated, the rest are coming later and listed after them`() =
        runTest(dispatcher) {
            val vm = viewModel()
            val state = ready(vm)
            val playable = PlayTestData.engine.playableHeroes
            assertThat(state.playable).isEqualTo(playable)
            assertThat(state.picker.heroes.take(playable.size).map { it.id }).containsExactlyElementsIn(playable)
            val later = state.picker.heroes.drop(playable.size)
            assertThat(later).isNotEmpty()
            later.forEach { assertThat(state.picker.unavailable[it.id]).isEqualTo(NewGameViewModel.COMING_LATER) }
            assertThat(state.canContinue).isFalse()
            assertThat(state.problem).isNotNull()
        }

    @Test
    fun `picks alternate, 1 v 1 carries the p20 note, unequal teams wait, 2 v 2 needs no note`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.tap("wasp", "arien")
            var state = ready(vm)
            assertThat(state.form.red).containsExactly("wasp")
            assertThat(state.form.blue).containsExactly("arien")
            assertThat(state.canContinue).isTrue()
            assertThat(state.note).isEqualTo(NewGameViewModel.ONE_V_ONE)
            vm.tap("xargatha")
            state = ready(vm)
            assertThat(state.canContinue).isFalse()
            vm.tap("brogan")
            state = ready(vm)
            assertThat(state.canContinue).isTrue()
            assertThat(state.note).isNull()
        }

    @Test
    fun `a draft's line-up with heroes not playable yet is named and can't start`() = runTest(dispatcher) {
        val vm = viewModel(NewGameRoute(red = listOf("wasp", "snorri"), blue = listOf("arien", "brogan")))
        val state = ready(vm)
        assertThat(state.canContinue).isFalse()
        assertThat(state.problem).contains("Snorri")
    }

    @Test
    fun `by default you control Red's first pick with computer teammates at Normal and the coin is flipped`() =
        runTest(dispatcher) {
            val vm = viewModel(NewGameRoute(red = listOf("wasp", "xargatha"), blue = listOf("arien", "brogan")))
            vm.onAction(NewGameAction.Next)
            val state = ready(vm)
            assertThat(state.form.step).isEqualTo(NewGameStep.OPTIONS)
            assertThat(state.form.coin).isNotNull()
            assertThat(state.map?.id).isEqualTo(NewGameViewModel.DEFAULT_MAP)
            assertThat(state.seats.filter { it.human }.map { it.hero.id }).containsExactly("wasp")
            assertThat(state.seats.filterNot { it.human }.map { it.level }.toSet()).containsExactly(AiLevel.NORMAL)
            val route = vm.start()!!
            assertThat(route.gameId).isEqualTo("new-game")
            val game = games.stored("new-game")!!
            val setup = game.game.setup
            assertThat(setup.seats.map { it.heroId }).containsExactly("wasp", "xargatha", "arien", "brogan").inOrder()
            assertThat(setup.seats.first().controller).isEqualTo(Controller.Human)
            assertThat(setup.seats.drop(1).map { it.controller }.toSet()).containsExactly(Controller.Ai(AiLevel.NORMAL))
            assertThat(setup.tieBreaker).isEqualTo(state.form.coin)
            assertThat(setup.length).isEqualTo(GameLength.QUICK)
            assertThat(game.options.coach).isTrue()
            assertThat(game.game.engineVersion).isEqualTo(PlayTestData.engine.engineVersion)
            // The saved game opens on a question.
            assertThat(PlayTestData.engine.restore(game.game).pending).isNotNull()
        }

    @Test
    fun `whole team, a level per seat, Long and no coach end up in the game`() = runTest(dispatcher) {
        val vm = viewModel(NewGameRoute(red = listOf("wasp", "xargatha"), blue = listOf("arien", "brogan")))
        vm.onAction(NewGameAction.Next)
        vm.onAction(NewGameAction.You("arien"))
        vm.onAction(NewGameAction.WholeTeam(true))
        vm.onAction(NewGameAction.Level(AiLevel.HARD))
        vm.onAction(NewGameAction.Advanced(true))
        vm.onAction(NewGameAction.SeatLevel("wasp", AiLevel.EASY))
        vm.onAction(NewGameAction.Length(GameLength.LONG))
        vm.onAction(NewGameAction.Coach(false))
        ready(vm)
        vm.start()
        val game = games.stored("new-game")!!
        val controllers = game.game.setup.seats.associate { it.heroId to it.controller }
        assertThat(controllers).containsExactly(
            "wasp", Controller.Ai(AiLevel.EASY),
            "xargatha", Controller.Ai(AiLevel.HARD),
            "arien", Controller.Human,
            "brogan", Controller.Human,
        )
        assertThat(game.game.setup.length).isEqualTo(GameLength.LONG)
        assertThat(game.options.coach).isFalse()
    }

    @Test
    fun `the form survives process death`() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val vm = viewModel(saved = saved)
        vm.tap("wasp", "arien")
        vm.onAction(NewGameAction.Next)
        vm.onAction(NewGameAction.Coach(false))
        val before = ready(vm).form
        val restored = viewModel(saved = SavedStateHandle(saved.keys().associateWith { saved.get<Any>(it) }))
        assertThat(ready(restored).form).isEqualTo(before)
    }
}
