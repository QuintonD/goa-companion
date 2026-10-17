package dev.quinton.goa.feature.draft

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.domain.draft.PoolSource
import dev.quinton.goa.core.domain.teams.OddSeatMode
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.core.model.rating.Rating
import dev.quinton.goa.core.testing.FakeCatalogRepository
import dev.quinton.goa.core.testing.FakePlayerRepository
import dev.quinton.goa.core.testing.FakeRatingsRepository
import dev.quinton.goa.core.testing.FakeSettingsRepository
import dev.quinton.goa.feature.draft.data.DraftProgress
import dev.quinton.goa.feature.draft.setup.DraftSetupViewModel
import dev.quinton.goa.feature.draft.setup.SetupEvent
import dev.quinton.goa.feature.draft.setup.SetupStage
import dev.quinton.goa.feature.draft.setup.TeamMode
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class DraftSetupViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val players = (0 until 6).map { Player("p$it", "Player $it", 0xFF3FC1B0, it, 0L) }
    private val playerRepo = FakePlayerRepository(players)
    private val ratings = FakeRatingsRepository().apply {
        playerRatings.value = mapOf(
            "p0" to Rating(1400.0, 20, 15, false),
            "p1" to Rating(1300.0, 15, 9, false),
            "p2" to Rating(1200.0, 10, 5, false),
            "p3" to Rating(1100.0, 3, 1, true),
        )
    }
    private val drafts = FakeDraftRepository()

    private fun TestScope.viewModel(): DraftSetupViewModel {
        val vm =
            DraftSetupViewModel(
                playerRepo,
                ratings,
                FakeCatalogRepository(),
                FakeSettingsRepository(),
                drafts,
                FakeDraftEnvironment(),
            )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        return vm
    }

    @Test
    fun `house draft is the default method and players are listed`() = runTest {
        val vm = viewModel()
        val state = vm.uiState.value
        assertThat(state.loading).isFalse()
        assertThat(state.form.methodId).isEqualTo("house")
        assertThat(state.players).hasSize(6)
        assertThat(state.blocker).isEqualTo("Choose at least 2 players")
    }

    @Test
    fun `selected players are split into teams and odd counts offer seat layouts`() = runTest {
        val vm = viewModel()
        listOf("p0", "p1", "p2", "p3", "p4").forEach(vm::togglePlayer)
        vm.next()
        val state = vm.uiState.value
        assertThat(state.stage).isEqualTo(SetupStage.TEAMS)
        assertThat(state.form.red).containsExactly("p0", "p2", "p4")
        assertThat(state.form.blue).containsExactly("p1", "p3")
        // Default for 5 players: double pilot on the smaller team → 3 v 3 seats.
        assertThat(state.seats.count { it.team == Team.BLUE }).isEqualTo(3)
        vm.setOddMode(OddSeatMode.HANDICAP)
        assertThat(vm.uiState.value.seats.filter { it.team == Team.RED }.all { it.handicap }).isTrue()
    }

    @Test
    fun `tapping a player moves them to the other team`() = runTest {
        val vm = viewModel()
        listOf("p0", "p1", "p2", "p3").forEach(vm::togglePlayer)
        vm.movePlayer("p0")
        assertThat(vm.uiState.value.form.blue).contains("p0")
        assertThat(vm.uiState.value.blocker).isNull() // 1 v 3 is invalid at the teams stage only
        vm.next()
        assertThat(vm.uiState.value.blocker).isEqualTo("Teams may differ by at most one player")
    }

    @Test
    fun `balance by Elo minimises the rating gap and respects locks`() = runTest {
        val vm = viewModel()
        listOf("p0", "p1", "p2", "p3").forEach(vm::togglePlayer)
        vm.setTeamMode(TeamMode.BALANCED)
        val form = vm.uiState.value.form
        // 1400+1100 vs 1300+1200 → both average 1250.
        assertThat(form.balanceDifference).isEqualTo(0.0)
        assertThat(setOf(form.red.toSet(), form.blue.toSet())).containsExactly(setOf("p0", "p3"), setOf("p1", "p2"))
    }

    @Test
    fun `quick add creates a player and selects them`() = runTest {
        val vm = viewModel()
        vm.quickAddPlayer("  Guest  ")
        val added = playerRepo.state.value.last()
        assertThat(added.name).isEqualTo("Guest")
        assertThat(vm.uiState.value.form.selected).contains(added.id)
    }

    @Test
    fun `start saves an in-progress board and opens it`() = runTest {
        val vm = viewModel()
        vm.setupEvents.test {
            listOf("p0", "p1", "p2", "p3").forEach(vm::togglePlayer)
            repeat(2) { vm.next() }
            assertThat(vm.uiState.value.stage).isEqualTo(SetupStage.HEROES)
            vm.setPoolSource(PoolSource.ALL)
            vm.next()
            assertThat(vm.uiState.value.blocker).isEqualTo("Flip the Tie Breaker coin")
            vm.setTeamA(Team.BLUE)
            vm.start()
            assertThat(awaitItem()).isEqualTo(SetupEvent.OpenBoard)
        }
        val board = (drafts.activeState.value as DraftProgress.Board).session
        assertThat(board.method.id).isEqualTo("house")
        assertThat(board.teamA).isEqualTo(Team.BLUE)
        assertThat(board.seats).hasSize(4)
        assertThat(board.pool).hasSize(12) // TestCatalog has 12 heroes < 15
        assertThat(vm.uiState.value.stage).isEqualTo(SetupStage.PLAYERS)
    }

    @Test
    fun `player draft hides team methods and starts the official flow`() = runTest {
        val vm = viewModel()
        listOf("p0", "p1", "p2", "p3").forEach(vm::togglePlayer)
        vm.next()
        vm.setTeamMode(TeamMode.PLAYER_DRAFT)
        val state = vm.uiState.value
        // Most experienced: p0 (20 games) and p1 (15).
        assertThat(state.form.redCaptain).isEqualTo("p0")
        assertThat(state.form.blueCaptain).isEqualTo("p1")
        assertThat(state.methods.first { it.method.id == "pick-and-ban" }.unavailable).isNotNull()
        assertThat(state.methods.first { it.method.id == "house" }.unavailable).isNotNull()
        vm.next()
        vm.selectMethod("all-pick")
        vm.next()
        vm.flipCoin()
        vm.start()
        val progress = drafts.activeState.value as DraftProgress.Players
        assertThat(progress.session.captains.redCaptain).isEqualTo("p0")
        assertThat(progress.session.selection.players).hasSize(4)
    }

    @Test
    fun `a filter that leaves too few heroes blocks the method`() = runTest {
        val vm = viewModel()
        listOf("p0", "p1", "p2", "p3", "p4", "p5").forEach(vm::togglePlayer)
        repeat(2) { vm.next() }
        vm.selectMethod("single-draft") // 6 seats × 3 = 18 > 12 heroes
        assertThat(vm.uiState.value.blocker).contains("Needs 18 heroes")
        vm.setPoolSource(PoolSource.CUSTOM)
        assertThat(vm.uiState.value.candidateCount).isEqualTo(0)
    }

    @Test
    fun `resume info reflects an in-progress draft and can be discarded`() = runTest {
        val vm = viewModel()
        listOf("p0", "p1").forEach(vm::togglePlayer)
        repeat(2) { vm.next() }
        vm.selectMethod("all-pick")
        vm.next()
        vm.setTeamA(Team.RED)
        vm.start()
        assertThat(vm.uiState.value.resume?.title).isEqualTo("All Pick")
        vm.discardActive()
        assertThat(vm.uiState.value.resume).isNull()
    }
}
