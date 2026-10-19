package dev.quinton.goa.feature.board

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.data.BoardRepository
import dev.quinton.goa.core.data.EditClock
import dev.quinton.goa.core.domain.board.BoardGeometry
import dev.quinton.goa.core.domain.board.BoardRules
import dev.quinton.goa.core.domain.board.BoardRules.MovePath
import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.model.board.BoardPiece
import dev.quinton.goa.core.model.board.HeroPiece
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionPiece
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.board.SavedBoard
import dev.quinton.goa.core.model.board.TokenPiece
import dev.quinton.goa.core.model.draft.DraftAction
import dev.quinton.goa.core.model.draft.DraftMethodKind
import dev.quinton.goa.core.model.draft.DraftRecord
import dev.quinton.goa.core.model.draft.DraftStepRecord
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.settings.AppSettings
import dev.quinton.goa.core.navigation.BoardRoute
import dev.quinton.goa.core.testing.FakeBoardMapRepository
import dev.quinton.goa.core.testing.FakeBoardRepository
import dev.quinton.goa.core.testing.FakeCatalogRepository
import dev.quinton.goa.core.testing.FakeDraftRecordRepository
import dev.quinton.goa.core.testing.FakeSettingsRepository
import dev.quinton.goa.core.testing.TestCatalog
import dev.quinton.goa.feature.board.board.BoardAction
import dev.quinton.goa.feature.board.board.BoardTool
import dev.quinton.goa.feature.board.board.BoardUiState
import dev.quinton.goa.feature.board.board.BoardViewModel
import dev.quinton.goa.feature.board.board.MapTap
import dev.quinton.goa.feature.board.board.NewPiece
import dev.quinton.goa.feature.board.board.ThroneCaptured
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
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
    private val drafts = FakeDraftRecordRepository()
    private val settings = FakeSettingsRepository()
    private var now = 1_000L
    private var ids = 0
    private var pieces = 0
    private val geometry = BoardGeometry.of(TestMap.map)

    private val red = listOf("test-hero-1", "test-hero-2")
    private val blue = listOf("test-hero-3", "test-hero-4")

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(
        route: BoardRoute = BoardRoute(red = red, blue = blue),
        saved: SavedStateHandle = SavedStateHandle(),
        repository: BoardRepository = boards,
        pieceIds: (() -> String)? = { "piece-${++pieces}" },
    ) = if (pieceIds == null) {
        BoardViewModel(
            route = route,
            savedState = saved,
            catalogRepository = catalog,
            mapRepository = maps,
            boardRepository = repository,
            draftRepository = drafts,
            settingsRepository = settings,
            clock = EditClock { now },
            newBoardId = { "board-${++ids}" },
            flushScope = CoroutineScope(dispatcher),
        )
    } else {
        BoardViewModel(
            route = route,
            savedState = saved,
            catalogRepository = catalog,
            mapRepository = maps,
            boardRepository = repository,
            draftRepository = drafts,
            settingsRepository = settings,
            clock = EditClock { now },
            newBoardId = { "board-${++ids}" },
            newPieceId = pieceIds,
            flushScope = CoroutineScope(dispatcher),
        )
    }

    private fun BoardViewModel.ready(): BoardUiState.Ready = uiState.value as BoardUiState.Ready

    private fun BoardUiState.Ready.hero(heroId: String) = pieces.filterIsInstance<HeroPiece>().first {
        it.heroId == heroId
    }

    private fun BoardUiState.Ready.emptySpace(near: Hex): Hex {
        val occupied = BoardRules.occupancy(board.state)
        return TestMap.map.spaces.map { it.hex }.filter { it !in occupied }.minBy { HexGrid.distance(it, near) }
    }

    @Test
    fun `a new board from a line-up opens on the opening and is saved once`() = runTest(dispatcher) {
        val vm = viewModel()
        val ready = vm.ready()
        assertThat(ready.board.lineup.red).containsExactlyElementsIn(red).inOrder()
        assertThat(ready.hero("test-hero-1").at).isEqualTo(geometry.heroSpawns(Team.RED)[0])
        assertThat(ready.hero("test-hero-3").at).isEqualTo(geometry.heroSpawns(Team.BLUE)[0])
        // Minions on every Spawn point of the starting Battle Zone (p.6), bound to the Lane (p.20).
        val minions = ready.pieces.filterIsInstance<MinionPiece>()
        assertThat(minions.map { it.at }.toSet()).isEqualTo(geometry.minionSpawns("mid").map { it.hex }.toSet())
        assertThat(minions.map { it.lane }.toSet()).containsExactly("lane")
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
    fun `an untouched board of the same line-up is reopened, not duplicated`() = runTest(dispatcher) {
        val first = viewModel().ready().board
        // "Try on the board" again (a new entry, fresh saved state): the same untouched board.
        val again = viewModel()
        assertThat(again.ready().board.id).isEqualTo(first.id)
        assertThat(again.ready().hint?.text).contains("untouched")
        assertThat(boards.state.value).hasSize(1)
        // Once a piece has moved, the board is the user's study: a new entry sets up a new one.
        val hero = again.ready().hero("test-hero-1")
        again.onAction(BoardAction.Select(hero.id))
        again.tap(MapTap.Space(again.ready().emptySpace(Hex(2, 1))))
        again.flush()
        val third = viewModel()
        assertThat(third.ready().board.id).isNotEqualTo(first.id)
        assertThat(boards.state.value).hasSize(2)
    }

    @Test
    fun `from a drafted match the heroes stand in the draft's pick order`() = runTest(dispatcher) {
        drafts.state.value = listOf(
            DraftRecord(
                id = "d1",
                createdAt = 1L,
                methodId = "house",
                methodKind = DraftMethodKind.CUSTOM,
                pool = emptyList(),
                teamA = Team.RED,
                steps = listOf(
                    DraftStepRecord(Team.RED, DraftAction.PICK, "test-hero-2"),
                    DraftStepRecord(Team.BLUE, DraftAction.PICK, "test-hero-4"),
                    DraftStepRecord(Team.BLUE, DraftAction.PICK, "test-hero-3"),
                    DraftStepRecord(Team.RED, DraftAction.PICK, "test-hero-1"),
                ),
                matchId = "m1",
            ),
        )
        // The match lists its seats in seat order; the draft picked test-hero-2 first.
        val vm = viewModel(BoardRoute(red = red, blue = blue, matchId = "m1"))
        assertThat(vm.ready().board.lineup.red).containsExactly("test-hero-2", "test-hero-1").inOrder()
        assertThat(vm.ready().board.lineup.blue).containsExactly("test-hero-4", "test-hero-3").inOrder()
        assertThat(vm.ready().hero("test-hero-2").at).isEqualTo(geometry.heroSpawns(Team.RED)[0])
    }

    @Test
    fun `tap selects, tap an empty space moves, drop on a piece swaps, undo and redo`() = runTest(dispatcher) {
        val vm = viewModel()
        val arien = vm.ready().hero("test-hero-1")
        val other = vm.ready().hero("test-hero-2")
        assertThat(vm.tap(MapTap.Piece(arien.id))).isTrue()
        assertThat(vm.ready().selectedId).isEqualTo(arien.id)

        val target = vm.ready().emptySpace(Hex(2, 1))
        assertThat(vm.tap(MapTap.Space(target))).isTrue()
        assertThat(vm.ready().hero("test-hero-1").at).isEqualTo(target)
        assertThat(vm.ready().canUndo).isTrue()
        assertThat(vm.ready().announcement?.text).startsWith("Test Hero 1 moved")

        // Tapping a taken space selects its piece; the board doesn't change.
        assertThat(vm.tap(MapTap.Piece(other.id))).isTrue()
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
    fun `a tap reports whether it did anything, so only an idle tap arms double tap zoom`() = runTest(dispatcher) {
        val vm = viewModel()
        val hero = vm.ready().hero("test-hero-1")
        // Nothing selected, an empty space: nothing happens (a second tap there zooms).
        assertThat(vm.tap(MapTap.Space(vm.ready().emptySpace(Hex(7, 2))))).isFalse()
        // Selecting changes the selection: the quick tap after it must move, not zoom.
        assertThat(vm.tap(MapTap.Piece(hero.id))).isTrue()
        // The selected piece again: nothing changes.
        assertThat(vm.tap(MapTap.Piece(hero.id))).isFalse()
        assertThat(vm.tap(MapTap.Space(vm.ready().emptySpace(Hex(2, 1))))).isTrue()
    }

    @Test
    fun `dropping a piece where it started just selects it (a long press released)`() = runTest(dispatcher) {
        val vm = viewModel()
        val hero = vm.ready().hero("test-hero-2")
        vm.onAction(BoardAction.Drop(hero.id, hero.at!!))
        assertThat(vm.ready().selectedId).isEqualTo(hero.id)
        assertThat(vm.ready().canUndo).isFalse()
    }

    @Test
    fun `a tap off the board or on terrain never moves a piece`() = runTest(dispatcher) {
        val vm = viewModel()
        val hero = vm.ready().hero("test-hero-1")
        vm.onAction(BoardAction.Select(hero.id))
        assertThat(vm.tap(MapTap.Space(TestMap.terrain))).isTrue()
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
        // Its own write coming back from the store changes nothing.
        assertThat(vm.ready().canUndo).isTrue()

        vm.onAction(BoardAction.Remove)
        vm.flush()
        assertThat(stored().state.pieces.none { it.id == hero.id }).isTrue()
    }

    @Test
    fun `a board changed elsewhere is adopted, a deleted one is closed and never written back`() =
        runTest(dispatcher) {
            val vm = viewModel()
            val board = vm.ready().board
            // A backup Replace import brings an older version with a token on the board.
            val imported = board.copy(
                name = "From the backup",
                state = board.state.copy(pieces = board.state.pieces + TokenPiece("tok", Hex(7, 0), "Mine")),
                updatedAt = 500L,
            )
            boards.import(listOf(imported), replace = true)
            assertThat(vm.ready().board).isEqualTo(imported)
            assertThat(vm.ready().canUndo).isFalse()
            assertThat(vm.ready().hint?.text).contains("changed elsewhere")

            // A pending change, then the board is deleted in Boards: closed, and the change is dropped.
            vm.onAction(BoardAction.Select(vm.ready().hero("test-hero-1").id))
            vm.tap(MapTap.Space(vm.ready().emptySpace(Hex(2, 1))))
            boards.delete(board.id)
            assertThat(vm.uiState.value).isInstanceOf(BoardUiState.Missing::class.java)
            vm.flush()
            advanceTimeBy(1_000)
            runCurrent()
            assertThat(boards.state.value).isEmpty()
        }

    @Test
    fun `a board that can't be read says so instead of crashing`() = runTest(dispatcher) {
        val broken = object : BoardRepository by FakeBoardRepository() {
            override val boards: Flow<List<SavedBoard>> = flow { throw IOException("corrupt") }

            override fun observe(id: String): Flow<SavedBoard?> = flow { throw IOException("corrupt") }
        }
        val vm = viewModel(BoardRoute(boardId = "anything"), repository = broken)
        assertThat((vm.uiState.value as BoardUiState.Missing).message).contains("couldn't be read")
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
        vm.onAction(BoardAction.SetPath(MovePath.IGNORE_OBSTACLES))
        assertThat(vm.ready().overlay!!.movement)
            .isEqualTo(BoardRules.movementReach(geometry, state, hero.at!!, 2, MovePath.IGNORE_OBSTACLES))
        vm.onAction(BoardAction.SetPath(MovePath.THROUGH_TOKENS))
        vm.onAction(BoardAction.ToggleStraightLine)
        assertThat(vm.ready().overlay!!.movement)
            .isEqualTo(BoardRules.movementReach(geometry, state, hero.at!!, 2, MovePath.THROUGH_TOKENS, true))
        vm.onAction(BoardAction.ToggleFastTravel)
        assertThat(vm.ready().overlay!!.fastTravel).isEqualTo(BoardRules.fastTravel(geometry, state, hero))
    }

    @Test
    fun `a minion's movement comes from the stepper, and outside its Battle Zone it shows the way back`() =
        runTest(dispatcher) {
            val vm = viewModel()
            val minion = vm.ready().pieces.filterIsInstance<MinionPiece>().first()
            vm.onAction(BoardAction.Select(minion.id))
            vm.onAction(BoardAction.Steps(2))
            val state = vm.ready().board.state
            assertThat(vm.ready().overlay!!.movementValue).isEqualTo(3)
            assertThat(
                vm.ready().overlay!!.movement,
            ).isEqualTo(BoardRules.movementReach(geometry, state, minion.at!!, 3))
            assertThat(vm.ready().overlay!!.returnTo).isEmpty()
            // Moved out of the Battle Zone ("mid", q 6..8): the overlay shows where it goes back to (p.18).
            vm.tap(MapTap.Space(vm.ready().emptySpace(Hex(4, 1))))
            val back = vm.ready().overlay!!.returnTo
            assertThat(back).isNotEmpty()
            assertThat(back.all { geometry.zoneOf(it) == "mid" }).isTrue()
        }

    @Test
    fun `measure reads distance and straight line from the selected piece, only on the board`() =
        runTest(dispatcher) {
            val vm = viewModel()
            val hero = vm.ready().hero("test-hero-1")
            vm.onAction(BoardAction.Select(hero.id))
            vm.onAction(BoardAction.StartMeasure)
            assertThat(vm.ready().tool).isEqualTo(BoardTool.Measure)
            val target = Hex(hero.at!!.q + 4, hero.at!!.r)
            assertThat(vm.tap(MapTap.Space(target))).isTrue()
            assertThat(vm.ready().measure?.distance).isEqualTo(4)
            assertThat(vm.ready().measure?.straight).isTrue()
            vm.tap(MapTap.Space(Hex(hero.at!!.q + 3, hero.at!!.r + 2)))
            assertThat(vm.ready().measure?.straight).isFalse()
            // Far off the map: ignored, the last measure stays.
            assertThat(vm.tap(MapTap.Space(Hex(40, 40)))).isFalse()
            assertThat(vm.ready().measure?.target).isEqualTo(Hex(hero.at!!.q + 3, hero.at!!.r + 2))
            // Measuring never moves anything.
            assertThat(vm.ready().hero("test-hero-1").at).isEqualTo(hero.at)
        }

    @Test
    fun `add a piece then tap where, one hero once per board, new ids never reused`() = runTest(dispatcher) {
        val vm = viewModel(pieceIds = null)
        vm.onAction(BoardAction.Add(NewPiece.Token("Mine", null)))
        val target = vm.ready().emptySpace(Hex(10, 1))
        assertThat(vm.tap(MapTap.Space(target))).isTrue()
        val token = vm.ready().pieces.filterIsInstance<TokenPiece>().single()
        assertThat(token.at).isEqualTo(target)
        assertThat(vm.ready().selectedId).isEqualTo(token.id)
        assertThat(vm.ready().tool).isEqualTo(BoardTool.Move)

        // Remove it and add another: a fresh id, so Undo and overlays can't mix the two up.
        vm.onAction(BoardAction.Remove)
        vm.onAction(BoardAction.Add(NewPiece.Token("Mine", null)))
        vm.tap(MapTap.Space(target))
        val second = vm.ready().pieces.filterIsInstance<TokenPiece>().single()
        assertThat(second.id).isNotEqualTo(token.id)
        assertThat(second.id).matches("[0-9a-f-]{36}")

        vm.onAction(BoardAction.Add(NewPiece.Hero("test-hero-1", Team.BLUE)))
        assertThat(vm.tap(MapTap.Space(vm.ready().emptySpace(Hex(10, 2))))).isTrue()
        assertThat(vm.ready().hint?.text).contains("already on this board")
        assertThat(vm.ready().pieces.filterIsInstance<HeroPiece>().count { it.heroId == "test-hero-1" }).isEqualTo(1)
    }

    @Test
    fun `hints show once each and only the hint shown is cleared`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onAction(BoardAction.Add(NewPiece.Token("Mine", null)))
        vm.tap(MapTap.Space(Hex(40, 40)))
        val first = vm.ready().hint!!
        vm.tap(MapTap.Space(Hex(41, 41)))
        val second = vm.ready().hint!!
        assertThat(second.id).isNotEqualTo(first.id)
        // The snackbar reports the old one late: the new hint stays.
        vm.onAction(BoardAction.HintShown(first.id))
        assertThat(vm.ready().hint).isEqualTo(second)
        vm.onAction(BoardAction.HintShown(second.id))
        assertThat(vm.ready().hint).isNull()
    }

    /** Red pushes while a red hero stands on lane-3's red melee Spawn point (9,0). */
    private fun BoardViewModel.pushOntoHero(): BoardPiece {
        val hero = ready().hero("test-hero-1")
        onAction(BoardAction.Drop(hero.id, Hex(9, 0)))
        onAction(BoardAction.Deselect)
        onAction(BoardAction.Push("lane", Team.RED))
        return ready().waiting.single().piece
    }

    @Test
    fun `push the lane respawns minions, a taken Spawn point puts its minion in the tray`() = runTest(dispatcher) {
        val vm = viewModel()
        val waitingPiece = vm.pushOntoHero()
        val ready = vm.ready()
        assertThat(ready.board.state.battleZones).containsEntry("lane", "lane-3")
        // The previous Battle Zone's minions are gone.
        assertThat(ready.pieces.filterIsInstance<MinionPiece>().none { it.at?.let(geometry::zoneOf) == "mid" }).isTrue()
        assertThat((waitingPiece as MinionPiece).type).isEqualTo(MinionType.MELEE)
        assertThat(waitingPiece.lane).isEqualTo("lane")
        val waiting = ready.waiting.single()
        assertThat(ready.activeWaitingId).isEqualTo(waitingPiece.id)
        assertThat(ready.trayActive).isTrue()
        assertThat(waiting.targets).isNotEmpty()
        assertThat(waiting.targets.all { geometry.zoneOf(it) == "lane-3" }).isTrue()

        // A space that isn't one of the nearest empty ones is refused; a highlighted one places it.
        val wrong = TestMap.map.spaces.map { it.hex }.first {
            geometry.zoneOf(it) == "lane-1" && it !in BoardRules.occupancy(ready.board.state)
        }
        vm.tap(MapTap.Space(wrong))
        assertThat(vm.ready().hint?.text).contains("highlighted")
        val target = waiting.targets.first()
        assertThat(vm.tap(MapTap.Space(target))).isTrue()
        assertThat(vm.ready().waiting).isEmpty()
        assertThat(vm.ready().pieces.first { it.id == waitingPiece.id }.at).isEqualTo(target)

        // Undo goes back through the placement and the push.
        vm.onAction(BoardAction.Undo)
        vm.onAction(BoardAction.Undo)
        assertThat(vm.ready().board.state.battleZones).containsEntry("lane", "mid")
    }

    @Test
    fun `a waiting minion never takes a tap meant for the selected piece`() = runTest(dispatcher) {
        val vm = viewModel()
        val waiting = vm.pushOntoHero()
        val hero = vm.ready().hero("test-hero-2")
        vm.onAction(BoardAction.Select(hero.id))
        assertThat(vm.ready().trayActive).isFalse()
        assertThat(vm.ready().trayTargets).isEmpty()
        val target = vm.ready().emptySpace(Hex(2, 1))
        vm.tap(MapTap.Space(target))
        // The hero moved; the minion still waits.
        assertThat(vm.ready().hero("test-hero-2").at).isEqualTo(target)
        assertThat(vm.ready().pieces.first { it.id == waiting.id }.at).isNull()

        // Tapping it in the tray makes it the one taps place, whatever was selected.
        vm.onAction(BoardAction.ActivateWaiting(waiting.id))
        assertThat(vm.ready().selectedId).isNull()
        assertThat(vm.ready().trayActive).isTrue()
        // So does picking it from the Pieces list.
        vm.onAction(BoardAction.Select(hero.id))
        vm.onAction(BoardAction.Select(waiting.id))
        assertThat(vm.ready().trayActive).isTrue()
        assertThat(vm.ready().activeWaitingId).isEqualTo(waiting.id)
        vm.tap(MapTap.Space(vm.ready().trayTargets.first()))
        assertThat(vm.ready().pieces.first { it.id == waiting.id }.at).isNotNull()
    }

    @Test
    fun `a reopened board still aims its waiting minion at its own Battle Zone`() = runTest(dispatcher) {
        val vm = viewModel()
        val waiting = vm.pushOntoHero()
        val targets = vm.ready().waiting.single().targets
        vm.flush()
        val reopened = viewModel(BoardRoute(boardId = vm.ready().board.id))
        assertThat(reopened.ready().waiting.single().piece.id).isEqualTo(waiting.id)
        assertThat(reopened.ready().waiting.single().targets).isEqualTo(targets)
    }

    @Test
    fun `pushing into a Throne Zone says that team wins and changes nothing`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onAction(BoardAction.Push("lane", Team.RED))
        val before = vm.ready().board.state
        vm.onAction(BoardAction.Push("lane", Team.RED))
        assertThat(vm.ready().throneCaptured).isEqualTo(ThroneCaptured(Team.RED))
        assertThat(vm.ready().board.state).isEqualTo(before)
        vm.onAction(BoardAction.DismissThrone)
        assertThat(vm.ready().throneCaptured).isNull()
    }

    @Test
    fun `reset to opening puts every piece back, undoably`() = runTest(dispatcher) {
        val vm = viewModel()
        val opening = vm.ready().board.state
        vm.onAction(BoardAction.Select(vm.ready().hero("test-hero-1").id))
        vm.onAction(BoardAction.Remove)
        vm.onAction(BoardAction.ResetToOpening)
        assertThat(vm.ready().board.state.sameLayoutAs(opening)).isTrue()
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

    @Test
    fun `the owned products from Settings reach the Add sheet`() = runTest(dispatcher) {
        settings.state.value = AppSettings(ownedProducts = setOf("test-core"))
        assertThat(viewModel().ready().ownedProducts).containsExactly("test-core")
    }
}
