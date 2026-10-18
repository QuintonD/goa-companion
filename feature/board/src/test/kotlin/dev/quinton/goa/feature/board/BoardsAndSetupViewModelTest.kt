package dev.quinton.goa.feature.board

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.data.EditClock
import dev.quinton.goa.core.model.board.BoardLineup
import dev.quinton.goa.core.model.board.BoardState
import dev.quinton.goa.core.model.board.SavedBoard
import dev.quinton.goa.core.model.draft.DraftAction
import dev.quinton.goa.core.model.draft.DraftMethodKind
import dev.quinton.goa.core.model.draft.DraftRecord
import dev.quinton.goa.core.model.draft.DraftStepRecord
import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.core.model.match.Participant
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.navigation.NewBoardRoute
import dev.quinton.goa.core.testing.FakeBoardMapRepository
import dev.quinton.goa.core.testing.FakeBoardRepository
import dev.quinton.goa.core.testing.FakeCatalogRepository
import dev.quinton.goa.core.testing.FakeDraftRecordRepository
import dev.quinton.goa.core.testing.FakeMatchRepository
import dev.quinton.goa.core.testing.TestCatalog
import dev.quinton.goa.feature.board.list.BoardsUiState
import dev.quinton.goa.feature.board.list.BoardsViewModel
import dev.quinton.goa.feature.board.setup.LineupSourceKind
import dev.quinton.goa.feature.board.setup.NewBoardAction
import dev.quinton.goa.feature.board.setup.NewBoardUiState
import dev.quinton.goa.feature.board.setup.NewBoardViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BoardsAndSetupViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val maps = FakeBoardMapRepository(listOf(TestMap.map))
    private val catalog = FakeCatalogRepository(TestCatalog.catalog)

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private suspend fun <T> ReceiveTurbine<T>.ready(type: Class<*>): T {
        while (true) {
            val item = awaitItem()
            if (type.isInstance(item)) return item
        }
    }

    // ---- Boards --------------------------------------------------------------------------

    private fun board(id: String, updatedAt: Long) = SavedBoard(
        id = id,
        name = "Board $id",
        lineup = BoardLineup(red = listOf("test-hero-1"), blue = listOf("test-hero-2", "unknown-hero")),
        state = BoardState(TestMap.map.id),
        createdAt = 1L,
        updatedAt = updatedAt,
    )

    @Test
    fun `boards list newest first with map and line-up, rename, duplicate, delete and undo`() = runTest(dispatcher) {
        val repo = FakeBoardRepository(listOf(board("a", 10), board("b", 20)))
        val vm = BoardsViewModel(catalog, maps, repo, EditClock { 99L })
        vm.uiState.test {
            var ready = ready(BoardsUiState.Ready::class.java) as BoardsUiState.Ready
            assertThat(ready.boards.map { it.id }).containsExactly("b", "a").inOrder()
            val row = ready.boards.first()
            assertThat(row.mapName).isEqualTo("Test map")
            assertThat(row.mapVerified).isFalse()
            assertThat(row.blue.map { it.name }).containsExactly("Test Hero 2", "Unknown hero").inOrder()

            vm.rename("a", "  Opening study  ")
            ready = awaitItem() as BoardsUiState.Ready
            assertThat(ready.boards.first().name).isEqualTo("Opening study")
            assertThat(ready.boards.first().updatedAt).isEqualTo(99L)

            vm.duplicate("b")
            ready = awaitItem() as BoardsUiState.Ready
            assertThat(ready.boards.map { it.name }).contains("Board b (copy)")
            assertThat(ready.boards).hasSize(3)

            val deleted = vm.delete("b")!!
            ready = awaitItem() as BoardsUiState.Ready
            assertThat(ready.boards.map { it.id }).doesNotContain("b")
            vm.restore(deleted)
            ready = awaitItem() as BoardsUiState.Ready
            assertThat(ready.boards.map { it.id }).contains("b")
        }
    }

    // ---- New board -----------------------------------------------------------------------

    private val drafts = FakeDraftRecordRepository(
        listOf(
            DraftRecord(
                id = "d1",
                createdAt = 100L,
                methodId = "house",
                methodKind = DraftMethodKind.CUSTOM,
                pool = emptyList(),
                teamA = Team.RED,
                steps = listOf(
                    DraftStepRecord(Team.RED, DraftAction.BAN, "test-hero-9"),
                    DraftStepRecord(Team.BLUE, DraftAction.PICK, "test-hero-4"),
                    DraftStepRecord(Team.RED, DraftAction.PICK, "test-hero-2"),
                    DraftStepRecord(Team.RED, DraftAction.PICK, "test-hero-1"),
                    DraftStepRecord(Team.BLUE, DraftAction.PICK, "test-hero-3"),
                ),
            ),
        ),
    )

    private fun match(id: String, status: MatchStatus, playedAt: Long, vararg heroes: Pair<Team, String>) = Match(
        id = id,
        createdAt = playedAt,
        playedAt = playedAt,
        status = status,
        mode = GameMode.QUICK,
        participants = heroes.mapIndexed { i, (team, hero) -> Participant("p$i", hero, team) },
    )

    private val matches = FakeMatchRepository(
        listOf(
            match("m1", MatchStatus.COMPLETED, 50L, Team.RED to "test-hero-5", Team.BLUE to "test-hero-6"),
            match("m2", MatchStatus.PENDING, 200L, Team.RED to "test-hero-7", Team.BLUE to "test-hero-8"),
            match("m3", MatchStatus.ABANDONED, 300L, Team.RED to "test-hero-10", Team.BLUE to "test-hero-11"),
        ),
    )

    private fun newBoard(route: NewBoardRoute = NewBoardRoute(), saved: SavedStateHandle = SavedStateHandle()) =
        NewBoardViewModel(route, saved, catalog, maps, drafts, matches)

    @Test
    fun `heroes join the team being filled in pick order, once per board, five a side`() = runTest(dispatcher) {
        val vm = newBoard()
        vm.uiState.test {
            ready(NewBoardUiState.Ready::class.java)
            vm.onAction(NewBoardAction.Tap("test-hero-1"))
            vm.onAction(NewBoardAction.Tap("test-hero-2"))
            vm.onAction(NewBoardAction.Fill(Team.BLUE))
            vm.onAction(NewBoardAction.Tap("test-hero-3"))
            var ready = expectMostRecentItem() as NewBoardUiState.Ready
            assertThat(ready.form.red).containsExactly("test-hero-1", "test-hero-2").inOrder()
            assertThat(ready.form.blue).containsExactly("test-hero-3")
            // Tapping a hero already on the board takes it off.
            vm.onAction(NewBoardAction.Tap("test-hero-1"))
            vm.onAction(NewBoardAction.Reorder("test-hero-3", -1))
            vm.onAction(NewBoardAction.SwitchTeam("test-hero-2"))
            ready = expectMostRecentItem() as NewBoardUiState.Ready
            assertThat(ready.form.red).isEmpty()
            assertThat(ready.form.blue).containsExactly("test-hero-3", "test-hero-2").inOrder()
            (4..12).forEach { vm.onAction(NewBoardAction.Tap("test-hero-$it")) }
            ready = expectMostRecentItem() as NewBoardUiState.Ready
            assertThat(ready.form.blue).hasSize(NewBoardUiState.MAX_PER_TEAM)
            assertThat(ready.form.red).hasSize(NewBoardUiState.MAX_PER_TEAM)
            assertThat(ready.map?.id).isEqualTo(TestMap.map.id)
            assertThat(vm.setUp()?.blue).isEqualTo(ready.form.blue)
        }
    }

    @Test
    fun `a draft or a match fills both teams in one tap, newest first`() = runTest(dispatcher) {
        val vm = newBoard()
        vm.uiState.test {
            val ready = ready(NewBoardUiState.Ready::class.java) as NewBoardUiState.Ready
            // Abandoned matches are left out.
            assertThat(ready.sources.map { it.kind }).containsExactly(
                LineupSourceKind.PENDING,
                LineupSourceKind.DRAFT,
                LineupSourceKind.LOGGED,
            ).inOrder()
            val draft = ready.sources.first { it.kind == LineupSourceKind.DRAFT }
            // Picks per team in pick order; bans are not heroes on the board.
            assertThat(draft.lineup).isEqualTo(BoardLineup(listOf("test-hero-2", "test-hero-1"), listOf("test-hero-4", "test-hero-3")))
            vm.onAction(NewBoardAction.Use(draft))
            val filled = expectMostRecentItem() as NewBoardUiState.Ready
            assertThat(filled.form.lineup).isEqualTo(draft.lineup)
        }
    }

    @Test
    fun `route pre-fills and the form survives process death`() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val vm = newBoard(NewBoardRoute(red = listOf("test-hero-1"), blue = listOf("test-hero-2", "test-hero-1")), saved)
        vm.uiState.test {
            val ready = ready(NewBoardUiState.Ready::class.java) as NewBoardUiState.Ready
            assertThat(ready.form.red).containsExactly("test-hero-1")
            assertThat(ready.form.blue).containsExactly("test-hero-2")
        }
        vm.onAction(NewBoardAction.Tap("test-hero-5"))
        val restored = newBoard(NewBoardRoute(), SavedStateHandle(saved.keys().associateWith { saved.get<Any>(it) }))
        restored.uiState.test {
            val ready = ready(NewBoardUiState.Ready::class.java) as NewBoardUiState.Ready
            assertThat(ready.form.red).containsExactly("test-hero-1", "test-hero-5").inOrder()
        }
    }
}
