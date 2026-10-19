package dev.quinton.goa.feature.draft

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.data.rating.EloConfigRepository
import dev.quinton.goa.core.domain.draft.MethodOrigin
import dev.quinton.goa.core.domain.draft.PoolRule
import dev.quinton.goa.core.domain.draft.PoolSource
import dev.quinton.goa.core.domain.rating.EloConfig
import dev.quinton.goa.core.domain.teams.OddSeatMode
import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.core.model.match.Participant
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.core.model.rating.Rating
import dev.quinton.goa.core.presentation.draft.DraftProgress
import dev.quinton.goa.core.presentation.draft.SetupForm
import dev.quinton.goa.core.presentation.draft.SetupStage
import dev.quinton.goa.core.presentation.draft.TeamMode
import dev.quinton.goa.core.testing.FakeCatalogRepository
import dev.quinton.goa.core.testing.FakeMatchRepository
import dev.quinton.goa.core.testing.FakePlayerRepository
import dev.quinton.goa.core.testing.FakeRatingsRepository
import dev.quinton.goa.core.testing.FakeSettingsRepository
import dev.quinton.goa.feature.draft.setup.DraftSetupViewModel
import dev.quinton.goa.feature.draft.setup.SetupEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
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
    private val eloConfig = object : EloConfigRepository {
        override val config = MutableStateFlow(EloConfig.DEFAULT)

        override suspend fun update(transform: (EloConfig) -> EloConfig) = config.update(transform)

        override suspend fun reset() {
            config.value = EloConfig.DEFAULT
        }
    }
    private val drafts = FakeDraftRepository()
    private val matches = FakeMatchRepository()

    private fun TestScope.viewModel(): DraftSetupViewModel {
        val vm =
            DraftSetupViewModel(
                playerRepo,
                ratings,
                eloConfig,
                FakeCatalogRepository(),
                FakeSettingsRepository(),
                drafts,
                FakeDraftEnvironment(),
                matches,
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
    fun `an unrated player counts as the group's starting rating when balancing, as the replay starts them`() =
        runTest {
            eloConfig.config.value = EloConfig.DEFAULT.copy(initialRating = 1350.0)
            val vm = viewModel()
            listOf("p0", "p1", "p4", "p5").forEach(vm::togglePlayer) // p4 and p5 are unrated
            vm.setTeamMode(TeamMode.BALANCED)
            val form = vm.uiState.value.form
            // 1400+1300 vs 1350+1350 (with 1500 for the unrated, 1400+1500 vs 1300+1500 would win).
            assertThat(form.balanceDifference).isEqualTo(0.0)
            assertThat(setOf(form.red.toSet(), form.blue.toSet()))
                .containsExactly(setOf("p0", "p1"), setOf("p4", "p5"))
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
    fun `quick add reuses a player with the same name instead of creating a duplicate`() = runTest {
        val vm = viewModel()
        vm.quickAddPlayer("  player 2 ")
        assertThat(playerRepo.state.value).hasSize(6)
        assertThat(vm.uiState.value.form.selected).containsExactly("p2")
    }

    @Test
    fun `quick add brings back an archived player with the same name`() = runTest {
        playerRepo.state.value = players + Player("old", "Old Friend", 0xFF3FC1B0, 0, 0L, archived = true)
        val vm = viewModel()
        assertThat(vm.uiState.value.players.map { it.player.id }).doesNotContain("old")
        vm.quickAddPlayer("OLD FRIEND")
        assertThat(playerRepo.state.value).hasSize(7)
        assertThat(playerRepo.state.value.single { it.id == "old" }.archived).isFalse()
        assertThat(vm.uiState.value.form.selected).containsExactly("old")
        assertThat(vm.uiState.value.players.map { it.player.id }).contains("old")
    }

    @Test
    fun `custom methods that deal hands and ban are unavailable`() = runTest {
        val house = bundledMethods.methods.first { it.id == "house" }
        drafts.customState.value = listOf(
            house.copy(
                id = "dealt-bans",
                name = "Dealt bans",
                origin = MethodOrigin.CUSTOM,
                pool = PoolRule.DealPerSeat(3),
            ),
        )
        val vm = viewModel()
        listOf("p0", "p1", "p2", "p3").forEach(vm::togglePlayer)
        repeat(2) { vm.next() }
        assertThat(vm.uiState.value.methods.first { it.method.id == "dealt-bans" }.unavailable)
            .contains("Dealt hands can't be combined with bans")
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
    fun `the form survives the view model being cleared and process death`() = runTest {
        val first = viewModel()
        listOf("p0", "p1", "p2", "p3").forEach(first::togglePlayer)
        first.next()
        first.movePlayer("p0")
        first.setTeamA(Team.BLUE)
        // Leaving the tab, Back out of the draft or process death: a new view model, same store.
        val second = viewModel()
        val form = second.uiState.value.form
        assertThat(form.stage).isEqualTo(SetupStage.TEAMS)
        assertThat(form.selected).containsExactly("p0", "p1", "p2", "p3")
        assertThat(form.blue).contains("p0")
        assertThat(form.teamA).isEqualTo(Team.BLUE)
    }

    @Test
    fun `the next game starts from last game's players, teams and method, and Clear forgets them`() = runTest {
        val vm = viewModel()
        listOf("p0", "p1", "p2", "p3").forEach(vm::togglePlayer)
        vm.movePlayer("p0")
        vm.movePlayer("p1")
        repeat(2) { vm.next() }
        vm.selectMethod("all-pick")
        vm.next()
        vm.setTeamA(Team.RED)
        vm.start()
        val next = vm.uiState.value.form
        assertThat(next.stage).isEqualTo(SetupStage.PLAYERS)
        assertThat(next.selected).containsExactly("p0", "p1", "p2", "p3")
        assertThat(next.red).containsExactly("p2", "p1")
        assertThat(next.blue).containsExactly("p3", "p0")
        assertThat(next.methodId).isEqualTo("all-pick")
        assertThat(next.teamA).isNull() // a fresh coin flip every game
        assertThat(next.lastGame).isTrue()
        assertThat(drafts.setupFormState.value?.selected).hasSize(4) // stored for the next session too

        vm.clearPlayers()
        val cleared = vm.uiState.value.form
        assertThat(cleared.selected).isEmpty()
        assertThat(cleared.red + cleared.blue).isEmpty()
        assertThat(cleared.methodId).isEqualTo("all-pick")
    }

    @Test
    fun `the last-game note shows only after this group really played the last completed match`() = runTest {
        drafts.setupFormState.value = SetupForm(
            selected = listOf("p0", "p1", "p2", "p3"),
            red = listOf("p0", "p1"),
            blue = listOf("p2", "p3"),
            lastGame = true,
        )
        val vm = viewModel()
        // Carried over from a draft, but no game was ever completed: no claim about a last game.
        assertThat(vm.uiState.value.lastGameNote).isNull()

        fun match(id: String, at: Long, status: MatchStatus, red: List<String>, blue: List<String>) = Match(
            id = id,
            createdAt = at,
            playedAt = at,
            status = status,
            mode = GameMode.QUICK,
            participants = red.map { Participant(it, null, Team.RED) } + blue.map { Participant(it, null, Team.BLUE) },
        )
        matches.state.value = listOf(match("pending", 3, MatchStatus.PENDING, listOf("p0", "p1"), listOf("p2", "p3")))
        assertThat(vm.uiState.value.lastGameNote).isNull()

        matches.state.value += match("played", 2, MatchStatus.COMPLETED, listOf("p2", "p3"), listOf("p0", "p1"))
        assertThat(vm.uiState.value.lastGameNote).isEqualTo("Same players and teams as last game")

        matches.state.value += match("swap", 4, MatchStatus.COMPLETED, listOf("p0", "p2"), listOf("p1", "p3"))
        assertThat(vm.uiState.value.lastGameNote).isEqualTo("Same players as last game")

        matches.state.value += match("others", 5, MatchStatus.COMPLETED, listOf("p0", "p4"), listOf("p1", "p5"))
        assertThat(vm.uiState.value.lastGameNote).isNull()
    }

    @Test
    fun `players deleted since last game are dropped from the restored form`() = runTest {
        drafts.setupFormState.value = SetupForm(
            stage = SetupStage.TEAMS,
            selected = listOf("p0", "gone", "p1"),
            red = listOf("p0", "gone"),
            blue = listOf("p1"),
        )
        val form = viewModel().uiState.value.form
        assertThat(form.selected).containsExactly("p0", "p1")
        assertThat(form.red + form.blue).containsExactly("p0", "p1")
        assertThat(form.stage).isEqualTo(SetupStage.PLAYERS)
    }

    @Test
    fun `quick adding someone already selected does not count them twice`() = runTest {
        val vm = viewModel()
        vm.togglePlayer("p2")
        vm.quickAddPlayer("Player 2")
        vm.quickAddPlayer("player 2")
        assertThat(vm.uiState.value.form.selected).containsExactly("p2")
        assertThat(vm.uiState.value.playerCount).isEqualTo(1)
    }

    @Test
    fun `a random pool larger than the filtered roster is flagged`() = runTest {
        val vm = viewModel()
        listOf("p0", "p1", "p2", "p3").forEach(vm::togglePlayer)
        repeat(2) { vm.next() }
        vm.setPoolSource(PoolSource.ALL)
        // House draft asks for 15; the test catalog has 12.
        assertThat(vm.uiState.value.poolNote)
            .isEqualTo("Only 12 heroes match your filters, so the pool is 12 instead of 15.")
        vm.selectMethod("all-pick")
        assertThat(vm.uiState.value.poolNote).isNull()
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
