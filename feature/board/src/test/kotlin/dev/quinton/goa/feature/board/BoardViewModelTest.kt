package dev.quinton.goa.feature.board

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.data.EditClock
import dev.quinton.goa.core.domain.board.BoardGeometry
import dev.quinton.goa.core.domain.board.BoardRules
import dev.quinton.goa.core.model.board.HeroPiece
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionPiece
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.board.TokenPiece
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.navigation.BoardRoute
import dev.quinton.goa.core.testing.FakeBoardMapRepository
import dev.quinton.goa.core.testing.FakeBoardRepository
import dev.quinton.goa.core.testing.FakeCatalogRepository
import dev.quinton.goa.core.testing.TestCatalog
import dev.quinton.goa.feature.board.board.BoardAction
import dev.quinton.goa.feature.board.board.BoardNotice
import dev.quinton.goa.feature.board.board.BoardTool
import dev.quinton.goa.feature.board.board.BoardUiState
import dev.quinton.goa.feature.board.board.BoardViewModel
import dev.quinton.goa.feature.board.board.MapTap
import dev.quinton.goa.feature.board.board.NewPiece
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BoardViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val boards = FakeBoardRepository()
    private val maps = FakeBoardMapRepository(listOf(TestMap.map))
    private val catalog = FakeCatalogRepository(TestCatalog.catalog)
    private var now = 1_000L
    private var ids = 0
    private val geometry = BoardGeometry.of(TestMap.map)

    private val red = listOf("test-hero-1", "test-hero-2")
    private val blue = listOf("test-hero-3", "test-hero-4")

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(
        route: BoardRoute = BoardRoute(
            red = red,
            blue = blue,
        ),
        saved: SavedStateHandle = SavedStateHandle(),
    ) =
        BoardViewModel(
            route = route,
            savedState = saved,
            catalogRepository = catalog,
            mapRepository = maps,
            boardRepository = boards,
            clock = EditClock { now },
            newBoardId = { "board-${++ids}" },
            flushScope = CoroutineScope(dispatcher),
        )

    private fun BoardViewModel.ready(): BoardUiState.Ready = uiState.value as BoardUiState.Ready

    private fun BoardUiState.Ready.hero(heroId: String) = pieces.filterIsInstance<HeroPiece>().first {
        it.heroId ==
            heroId
    }

    private fun BoardUiState.Ready.emptySpace(near: Hex): Hex {
        val occupied = BoardRules.occupancy(board.state)
        return TestMap.map.spaces.map {
            it.hex
        }.filter { it !in occupied }.minBy { dev.quinton.goa.core.domain.board.HexGrid.distance(it, near) }
    }

    @Test
    fun `a new board from a line-up opens on the opening and is saved once`() = runTest(dispatcher) {
        val vm = viewModel()
        val ready = vm.ready()
        assertThat(ready.board.lineup.red).containsExactlyElementsIn(red).inOrder()
        assertThat(ready.hero("test-hero-1").at).isEqualTo(geometry.heroSpawns(Team.RED)[0])
        assertThat(ready.hero("test-hero-3").at).isEqualTo(geometry.heroSpawns(Team.BLUE)[0])
        // Minions on every Spawn point of the starting Battle Zone (p.6).
        assertThat(ready.pieces.filterIsInstance<MinionPiece>().map { it.at }.toSet())
            .isEqualTo(geometry.minionSpawns("mid").map { it.hex }.toSet())
        assertThat(ready.board.name).isEqualTo("Test Hero 1, Test Hero 2 vs Test Hero 3, Test Hero 4")
        assertThat(boards.state.value.map { it.id }).containsExactly(ready.board.id)
    }

    @Test
    fun `process death reopens the same new board instead of creating another`() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val first = viewModel(saved = saved)
        val id = first.ready().board.id
        first.onAction(BoardAction.Select(first.ready().hero("test-hero-2").id))
        // A new ViewModel restored from the same saved state (process death, then the entry returns).
        val restored = viewModel(saved = SavedStateHandle(saved.keys().associateWith { saved.get<Any>(it) }))
        assertThat(restored.ready().board.id).isEqualTo(id)
        assertThat(restored.ready().selected).isEqualTo(restored.ready().hero("test-hero-2"))
        assertThat(boards.state.value).hasSize(1)
    }

    @Test
    fun `tap selects, tap an empty space moves, drop on a piece swaps, undo and redo`() = runTest(dispatcher) {
        val vm = viewModel()
        val arien = vm.ready().hero("test-hero-1")
        val other = vm.ready().hero("test-hero-2")
        assertThat(vm.tap(MapTap.Piece(arien.id))).isFalse()
        assertThat(vm.ready().selectedId).isEqualTo(arien.id)

        val target = vm.ready().emptySpace(Hex(2, 1))
        assertThat(vm.tap(MapTap.Space(target))).isTrue()
        assertThat(vm.ready().hero("test-hero-1").at).isEqualTo(target)
        assertThat(vm.ready().canUndo).isTrue()

        // Tapping a taken space selects its piece; the board doesn't change.
        assertThat(vm.tap(MapTap.Piece(other.id))).isFalse()
        assertThat(vm.ready().selectedId).isEqualTo(other.id)

        vm.onAction(BoardAction.Drop(other.id, target))
        assertThat(vm.ready().hero("test-hero-2").at).isEqualTo(target)
        assertThat(vm.ready().hero("test-hero-1").at).isEqualTo(other.at)
        // One piece per space, always.
        val placed = vm.ready().pieces.mapNotNull { it.at }
        assertThat(placed.toSet()).hasSize(placed.size)

        vm.onAction(BoardAction.Undo)
        assertThat(vm.ready().hero("test-hero-1").at).isEqualTo(target)
        assertThat(vm.ready().hero("test-hero-2").at).isEqualTo(other.at)
        vm.onAction(BoardAction.Undo)
        assertThat(vm.ready().hero("test-hero-1").at).isEqualTo(arien.at)
        assertThat(vm.ready().canUndo).isFalse()
        vm.onAction(BoardAction.Redo)
        assertThat(vm.ready().hero("test-hero-1").at).isEqualTo(target)
        assertThat(vm.ready().canRedo).isTrue()
    }

    @Test
    fun `a tap off the board or on terrain never moves a piece`() = runTest(dispatcher) {
        val vm = viewModel()
        val hero = vm.ready().hero("test-hero-1")
        vm.onAction(BoardAction.Select(hero.id))
        assertThat(vm.tap(MapTap.Space(TestMap.terrain))).isFalse()
        assertThat(vm.tap(MapTap.Space(Hex(40, 40)))).isFalse()
        assertThat(vm.ready().hero("test-hero-1").at).isEqualTo(hero.at)
        assertThat(vm.ready().selectedId).isNull()
    }

    @Test
    fun `every change is autosaved after a short delay, and at once on flush`() = runTest(dispatcher) {
        val vm = viewModel()
        val hero = vm.ready().hero("test-hero-1")
        vm.onAction(BoardAction.Select(hero.id))
        val target = vm.ready().emptySpace(Hex(2, 1))
        now = 5_000L
        vm.tap(MapTap.Space(target))
        fun stored() = boards.state.value.single()
        assertThat(stored().state.pieces.first { it.id == hero.id }.at).isEqualTo(hero.at)
        advanceTimeBy(1_000)
        runCurrent()
        assertThat(stored().state.pieces.first { it.id == hero.id }.at).isEqualTo(target)
        assertThat(stored().updatedAt).isEqualTo(5_000L)

        vm.onAction(BoardAction.Remove)
        vm.flush()
        assertThat(stored().state.pieces.none { it.id == hero.id }).isTrue()
    }

    @Test
    fun `overlays are measured with BoardRules from the chosen card`() = runTest(dispatcher) {
        val vm = viewModel()
        val hero = vm.ready().hero("test-hero-1")
        vm.onAction(BoardAction.Select(hero.id))
        assertThat(vm.ready().overlay?.movement).isEmpty()
        // Tier II cards of the test catalog have Movement 2 and Range 2.
        val card = TestCatalog.catalog.hero("test-hero-1")!!.cards.first { it.tier == 2 }
        vm.onAction(BoardAction.ChooseCard(card.id))
        val overlay = vm.ready().overlay!!
        val state = vm.ready().board.state
        assertThat(overlay.movement).isEqualTo(BoardRules.movementReach(geometry, state, hero.at!!, 2))
        assertThat(overlay.range).isEqualTo(BoardRules.within(geometry, hero.at!!, 2))
        assertThat(overlay.radius).isEmpty()

        vm.onAction(BoardAction.RangeBonus(1))
        assertThat(vm.ready().overlay!!.range).isEqualTo(BoardRules.within(geometry, hero.at!!, 3))
        vm.onAction(BoardAction.ToggleIgnoreObstacles)
        assertThat(
            vm.ready().overlay!!.movement,
        ).isEqualTo(BoardRules.movementReach(geometry, state, hero.at!!, 2, BoardRules.MovePath.IGNORE_OBSTACLES))
        vm.onAction(BoardAction.ToggleFastTravel)
        assertThat(vm.ready().overlay!!.fastTravel).isEqualTo(BoardRules.fastTravel(geometry, state, hero))
    }

    @Test
    fun `a minion's movement comes from the stepper`() = runTest(dispatcher) {
        val vm = viewModel()
        val minion = vm.ready().pieces.filterIsInstance<MinionPiece>().first()
        vm.onAction(BoardAction.Select(minion.id))
        vm.onAction(BoardAction.Steps(2))
        val state = vm.ready().board.state
        assertThat(vm.ready().overlay!!.movementValue).isEqualTo(3)
        assertThat(vm.ready().overlay!!.movement).isEqualTo(BoardRules.movementReach(geometry, state, minion.at!!, 3))
    }

    @Test
    fun `measure reads distance and straight line from the selected piece`() = runTest(dispatcher) {
        val vm = viewModel()
        val hero = vm.ready().hero("test-hero-1")
        vm.onAction(BoardAction.Select(hero.id))
        vm.onAction(BoardAction.StartMeasure)
        assertThat(vm.ready().tool).isEqualTo(BoardTool.Measure)
        val target = Hex(hero.at!!.q + 4, hero.at!!.r)
        assertThat(vm.tap(MapTap.Space(target))).isFalse()
        assertThat(vm.ready().measure?.distance).isEqualTo(4)
        assertThat(vm.ready().measure?.straight).isTrue()
        vm.tap(MapTap.Space(Hex(hero.at!!.q + 3, hero.at!!.r + 2)))
        assertThat(vm.ready().measure?.straight).isFalse()
        // Measuring never moves anything.
        assertThat(vm.ready().hero("test-hero-1").at).isEqualTo(hero.at)
    }

    @Test
    fun `add a piece then tap where, one hero once per board`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onAction(BoardAction.Add(NewPiece.Token("Mine", null)))
        val target = vm.ready().emptySpace(Hex(10, 1))
        assertThat(vm.tap(MapTap.Space(target))).isTrue()
        val token = vm.ready().pieces.filterIsInstance<TokenPiece>().single()
        assertThat(token.at).isEqualTo(target)
        assertThat(vm.ready().selectedId).isEqualTo(token.id)
        assertThat(vm.ready().tool).isEqualTo(BoardTool.Move)

        vm.onAction(BoardAction.Add(NewPiece.Hero("test-hero-1", Team.BLUE)))
        assertThat(vm.tap(MapTap.Space(vm.ready().emptySpace(Hex(10, 2))))).isFalse()
        assertThat(vm.ready().notice).isInstanceOf(BoardNotice.Hint::class.java)
        assertThat(vm.ready().pieces.filterIsInstance<HeroPiece>().count { it.heroId == "test-hero-1" }).isEqualTo(1)
    }

    @Test
    fun `push the lane respawns minions, a taken Spawn point puts its minion in the tray`() = runTest(dispatcher) {
        val vm = viewModel()
        // A red hero stands on lane-3's red melee Spawn point (9,0).
        val hero = vm.ready().hero("test-hero-1")
        vm.onAction(BoardAction.Drop(hero.id, Hex(9, 0)))
        vm.onAction(BoardAction.Push("lane", Team.RED))
        val ready = vm.ready()
        assertThat(ready.board.state.battleZones).containsEntry("lane", "lane-3")
        // The previous Battle Zone's minions are gone.
        assertThat(ready.pieces.filterIsInstance<MinionPiece>().none { it.at?.let(geometry::zoneOf) == "mid" }).isTrue()
        val waiting = ready.waiting.single()
        assertThat((waiting.piece as MinionPiece).type).isEqualTo(MinionType.MELEE)
        assertThat(ready.activeWaitingId).isEqualTo(waiting.piece.id)
        assertThat(waiting.targets).isNotEmpty()
        assertThat(waiting.targets.all { geometry.zoneOf(it) == "lane-3" }).isTrue()

        // A space that isn't one of the nearest empty ones is refused; a highlighted one places it.
        val wrong = TestMap.map.spaces.map { it.hex }.first {
            geometry.zoneOf(it) == "lane-1" &&
                it !in BoardRules.occupancy(ready.board.state)
        }
        assertThat(vm.tap(MapTap.Space(wrong))).isFalse()
        val target = waiting.targets.first()
        assertThat(vm.tap(MapTap.Space(target))).isTrue()
        assertThat(vm.ready().waiting).isEmpty()
        assertThat(vm.ready().pieces.first { it.id == waiting.piece.id }.at).isEqualTo(target)

        // Undo goes back through the placement and the push.
        vm.onAction(BoardAction.Undo)
        vm.onAction(BoardAction.Undo)
        assertThat(vm.ready().board.state.battleZones).containsEntry("lane", "mid")
    }

    @Test
    fun `pushing into a Throne Zone says that team wins and changes nothing`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onAction(BoardAction.Push("lane", Team.RED))
        val before = vm.ready().board.state
        vm.onAction(BoardAction.Push("lane", Team.RED))
        assertThat(vm.ready().notice).isEqualTo(BoardNotice.ThroneCaptured(Team.RED))
        assertThat(vm.ready().board.state).isEqualTo(before)
        vm.onAction(BoardAction.DismissNotice)
        assertThat(vm.ready().notice).isNull()
    }

    @Test
    fun `reset to opening puts every piece back, undoably`() = runTest(dispatcher) {
        val vm = viewModel()
        val opening = vm.ready().board.state
        vm.onAction(BoardAction.Select(vm.ready().hero("test-hero-1").id))
        vm.onAction(BoardAction.Remove)
        vm.onAction(BoardAction.ResetToOpening)
        assertThat(vm.ready().pieces.map { it.at to it::class }).containsExactlyElementsIn(
            opening.pieces.map {
                it.at to
                    it::class
            },
        )
        vm.onAction(BoardAction.Undo)
        assertThat(vm.ready().pieces).hasSize(opening.pieces.size - 1)
    }

    @Test
    fun `an existing board opens, a deleted one says so`() = runTest(dispatcher) {
        val created = viewModel().ready().board
        viewModel(BoardRoute(boardId = created.id)).uiState.test {
            assertThat((awaitItem() as BoardUiState.Ready).board).isEqualTo(created)
        }
        boards.delete(created.id)
        assertThat(
            viewModel(BoardRoute(boardId = created.id)).uiState.value,
        ).isInstanceOf(BoardUiState.Missing::class.java)
    }

    @Test
    fun `flip swaps which Throne Zone is at the bottom and survives process death`() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val vm = viewModel(saved = saved)
        assertThat(vm.ready().bottomTeam).isEqualTo(Team.RED)
        vm.onAction(BoardAction.Flip)
        assertThat(vm.ready().bottomTeam).isEqualTo(Team.BLUE)
        val restored = viewModel(saved = SavedStateHandle(saved.keys().associateWith { saved.get<Any>(it) }))
        assertThat(restored.ready().bottomTeam).isEqualTo(Team.BLUE)
    }
}
