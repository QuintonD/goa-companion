package dev.quinton.goa.feature.board.play

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.data.EditClock
import dev.quinton.goa.core.data.game.StoredGame
import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.AiStrength
import dev.quinton.goa.core.game.Controller
import dev.quinton.goa.core.game.GameFactory
import dev.quinton.goa.core.game.GameLength
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.settings.AppSettings
import dev.quinton.goa.core.navigation.NewGameRoute
import dev.quinton.goa.core.testing.FakeBoardMapRepository
import dev.quinton.goa.core.testing.FakeCatalogRepository
import dev.quinton.goa.core.testing.FakeDraftRecordRepository
import dev.quinton.goa.core.testing.FakeGameSaveRepository
import dev.quinton.goa.core.testing.FakeMatchRepository
import dev.quinton.goa.core.testing.FakePuzzleProgressRepository
import dev.quinton.goa.core.testing.FakeSettingsRepository
import dev.quinton.goa.feature.board.bundledCatalog
import dev.quinton.goa.feature.board.bundledMaps
import dev.quinton.goa.feature.board.list.PlayHomeUiState
import dev.quinton.goa.feature.board.list.PlayHomeViewModel
import dev.quinton.goa.feature.board.play.puzzles.PuzzleFilters
import dev.quinton.goa.feature.board.play.puzzles.PuzzleListUiState
import dev.quinton.goa.feature.board.play.puzzles.puzzleList
import dev.quinton.goa.feature.board.play.puzzles.realPuzzleBook
import dev.quinton.goa.feature.board.play.puzzles.screenshotPuzzleProgress
import dev.quinton.goa.feature.board.play.setup.Control
import dev.quinton.goa.feature.board.play.setup.NewGameAction
import dev.quinton.goa.feature.board.play.setup.NewGameStep
import dev.quinton.goa.feature.board.play.setup.NewGameUiState
import dev.quinton.goa.feature.board.play.setup.NewGameViewModel
import dev.quinton.goa.feature.board.play.setup.lineupNote
import dev.quinton.goa.feature.board.setup.NewBoardAction
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NewGameViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val factory = fakeFactory()
    private val saves = FakeGameSaveRepository()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(
        engine: GameFactory = factory,
        route: NewGameRoute = NewGameRoute(),
        settings: FakeSettingsRepository = FakeSettingsRepository(),
    ) = NewGameViewModel(
        route,
        SavedStateHandle(),
        FakeCatalogRepository(bundledCatalog),
        FakeBoardMapRepository(bundledMaps),
        FakeDraftRecordRepository(),
        FakeMatchRepository(),
        settings,
        saves,
        { engine },
        EditClock { 7L },
        Random(3),
    ).also { vm -> backgroundScope.launch { vm.uiState.collect {} } }

    private val NewGameViewModel.ready: NewGameUiState.Ready get() = uiState.value as NewGameUiState.Ready

    @Test
    fun `with no playable hero the screen says play is coming`() = runTest(dispatcher) {
        val vm = viewModel(FakeGameFactory(bundledMaps, playableHeroes = emptySet()))
        assertThat(vm.uiState.value).isEqualTo(NewGameUiState.ComingSoon)
    }

    @Test
    fun `heroes not yet playable are shown as later and can't be picked, picks alternate`() = runTest(dispatcher) {
        val vm = viewModel()
        assertThat(vm.ready.picker.unavailable).contains("xargatha")
        assertThat(vm.ready.picker.unavailable).doesNotContain("arien")
        // Playable heroes first in the grid.
        assertThat(vm.ready.picker.heroes.take(4).map { it.id }).containsExactly("arien", "brogan", "tigerclaw", "wasp")
        vm.onAction(NewGameAction.Lineup(NewBoardAction.Tap("xargatha")))
        assertThat(vm.ready.red).isEmpty()
        listOf("arien", "wasp", "brogan", "tigerclaw").forEach {
            vm.onAction(NewGameAction.Lineup(NewBoardAction.Tap(it)))
        }
        assertThat(vm.ready.red).containsExactly("arien", "brogan").inOrder()
        assertThat(vm.ready.blue).containsExactly("wasp", "tigerclaw").inOrder()
        assertThat(vm.ready.canContinue).isTrue()
    }

    @Test
    fun `you play your whole team or one hero, the rest are the computer at the chosen strengths`() = runTest(
        dispatcher,
    ) {
        val vm = viewModel(route = NewGameRoute(red = listOf("arien", "brogan"), blue = listOf("wasp", "tigerclaw")))
        val ready = vm.ready
        assertThat(ready.controls.first()).isEqualTo(Control.WholeTeam(Team.RED))
        assertThat(ready.controls).contains(Control.Hero("brogan"))
        assertThat(ready.aiSeats.map { it.hero.id }).containsExactly("wasp", "tigerclaw")

        vm.onAction(NewGameAction.Step(NewGameStep.OPTIONS))
        vm.onAction(NewGameAction.SetControl(Control.Hero("arien")))
        assertThat(vm.ready.form.strength).isEqualTo(AiStrength.DEFAULT)
        vm.onAction(NewGameAction.SetStrength(9))
        vm.onAction(NewGameAction.SetSeatStrength("brogan", 1))
        vm.onAction(NewGameAction.SetLength(GameLength.LONG))
        assertThat(vm.setup()).isNull() // the coin isn't flipped yet
        vm.onAction(NewGameAction.FlipCoin)
        val setup = vm.setup()!!
        assertThat(setup.tieBreaker).isEqualTo(vm.ready.form.coin)
        assertThat(setup.mapId).isEqualTo("forgotten-island")
        assertThat(setup.length).isEqualTo(GameLength.LONG)
        assertThat(setup.seats.map { it.heroId to it.controller }).containsExactly(
            "arien" to Controller.Human,
            "brogan" to Controller.Ai(AiLevel.EASY, 1),
            "wasp" to Controller.Ai(AiLevel.EXPERT, 9),
            "tigerclaw" to Controller.Ai(AiLevel.EXPERT, 9),
        ).inOrder()
    }

    @Test
    fun `a new game starts at the strength of the last game started`() = runTest(dispatcher) {
        val settings = FakeSettingsRepository(AppSettings(computerStrength = 7))
        val route = NewGameRoute(red = listOf("arien", "brogan"), blue = listOf("wasp", "tigerclaw"))
        val vm = viewModel(route = route, settings = settings)
        assertThat(vm.ready.form.strength).isEqualTo(7)
        vm.onAction(NewGameAction.SetStrength(3))
        // Moving the slider alone doesn't change the remembered strength; starting the game does.
        assertThat(settings.state.value.computerStrength).isEqualTo(7)
        vm.onAction(NewGameAction.FlipCoin)
        assertThat(vm.start()).isTrue()
        assertThat(settings.state.value.computerStrength).isEqualTo(3)
        val next = viewModel(route = route, settings = settings).uiState.first { it is NewGameUiState.Ready }
        assertThat((next as NewGameUiState.Ready).form.strength).isEqualTo(3)
    }

    @Test
    fun `start creates the game and saves it as the game in progress`() = runTest(dispatcher) {
        val vm = viewModel(route = NewGameRoute(red = listOf("arien", "brogan"), blue = listOf("wasp", "tigerclaw")))
        vm.onAction(NewGameAction.SetCoach(false))
        vm.onAction(NewGameAction.FlipCoin)
        assertThat(vm.start()).isTrue()
        val stored = saves.state.value as StoredGame.Saved
        assertThat(stored.game.coach).isFalse()
        assertThat(stored.game.startedAt).isEqualTo(7L)
        assertThat(stored.game.game.setup.seats.count { it.controller == Controller.Human }).isEqualTo(2)
        // Starting again would replace it: the screen says so.
        vm.uiState.first { (it as? NewGameUiState.Ready)?.replacesGame == true }
    }

    @Test
    fun `teams of different sizes can't continue, 1v1 is labelled as not the rulebook's way`() = runTest(dispatcher) {
        val vm = viewModel(route = NewGameRoute(red = listOf("arien", "brogan"), blue = listOf("wasp")))
        assertThat(vm.ready.canContinue).isFalse()
        assertThat(vm.ready.controls).isEmpty()
        assertThat(lineupNote(2, 1)).contains("same number")
        assertThat(lineupNote(1, 1)).contains("not the rulebook's way")
        assertThat(lineupNote(2, 2)).contains("standard")
    }

    @Test
    fun `Play offers a game once heroes are playable, and the game in progress to resume`() = runTest(dispatcher) {
        val games = FakeGameSaveRepository(StoredGame.Saved(fakeInProgress(factory)))
        val home = PlayHomeViewModel(
            FakeCatalogRepository(bundledCatalog),
            games,
            EditClock { 1L },
            { factory },
            { null },
            FakePuzzleProgressRepository(),
        )
        val ready = home.uiState.first { it is PlayHomeUiState.Ready } as PlayHomeUiState.Ready
        assertThat(ready.play.available).isTrue()
        assertThat(ready.play.resume?.summary).isEqualTo("You play Arien · Computer: Hard 8")
        assertThat(ready.play.resume?.red?.map { it.id }).containsExactly("arien", "brogan").inOrder()
        // No playable puzzle: the entry says so instead of a count.
        assertThat(ready.play.puzzles).isNull()

        val unreadable = PlayHomeViewModel.resumeOf(StoredGame.Unreadable, emptyMap())
        assertThat(unreadable?.unreadable).isTrue()
    }

    @Test
    fun `Play counts the puzzles part by part, as the puzzle list does`() = runTest(dispatcher) {
        val book = realPuzzleBook
        val progress = screenshotPuzzleProgress()
        val home = PlayHomeViewModel(
            FakeCatalogRepository(bundledCatalog),
            FakeGameSaveRepository(),
            EditClock { 1L },
            { factory },
            { book },
            FakePuzzleProgressRepository(progress),
        )
        val ready = home.uiState.first { it is PlayHomeUiState.Ready } as PlayHomeUiState.Ready
        val hub = requireNotNull(ready.play.puzzles)
        val list = puzzleList(
            book.puzzles,
            emptyMap(),
            progress,
            PuzzleFilters(),
        ) as PuzzleListUiState.Ready
        assertThat(hub.positionsSolved to hub.positionsTotal).isEqualTo(list.positionsSolved to list.positionsTotal)
        assertThat(hub.roundsSolved to hub.roundsTotal).isEqualTo(list.roundsSolved to list.roundsTotal)
        assertThat(hub.positionsSolved).isEqualTo(1)
        assertThat(hub.roundsSolved).isEqualTo(if (hub.roundsTotal > 0) 1 else 0)
    }

    @Test
    fun `Play says nothing can be played when the engine can't be built`() = runTest(dispatcher) {
        val home = PlayHomeViewModel(
            FakeCatalogRepository(bundledCatalog),
            FakeGameSaveRepository(),
            EditClock { 1L },
            { error("no engine") },
            { null },
            FakePuzzleProgressRepository(),
        )
        val ready = home.uiState.first { it is PlayHomeUiState.Ready } as PlayHomeUiState.Ready
        assertThat(ready.play.available).isFalse()
        assertThat(ready.play.resume).isNull()
    }
}
