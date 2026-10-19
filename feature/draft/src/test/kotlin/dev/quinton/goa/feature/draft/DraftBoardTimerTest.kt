package dev.quinton.goa.feature.draft

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.domain.draft.DraftEngine
import dev.quinton.goa.core.domain.draft.DraftSetup
import dev.quinton.goa.core.domain.teams.OddSeatMode
import dev.quinton.goa.core.domain.teams.SeatLayouts
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.presentation.draft.DraftProgress
import dev.quinton.goa.core.testing.FakeCatalogRepository
import dev.quinton.goa.core.testing.FakeDraftRecordRepository
import dev.quinton.goa.core.testing.FakeMatchRepository
import dev.quinton.goa.core.testing.FakePlayerRepository
import dev.quinton.goa.core.testing.TestCatalog
import dev.quinton.goa.feature.draft.board.DraftBoardViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DraftBoardTimerTest {
    @get:Rule val main = MainDispatcherRule(StandardTestDispatcher())

    @Test
    fun `step timer counts down, runs into overtime and restarts on the next step`() = runTest(main.dispatcher) {
        val house = bundledMethods.methods.first { it.id == "house" }
        val seats = SeatLayouts.build(listOf("a", "b"), listOf("c", "d"), OddSeatMode.HANDICAP).getOrThrow()
        val session = DraftEngine.start(
            DraftSetup("d", 0L, house, seats, Team.RED, TestCatalog.heroes.map { it.id }, 3L, stepSeconds = 2),
        ).getOrThrow()
        val drafts = FakeDraftRepository(active = DraftProgress.Board(session))
        val vm = DraftBoardViewModel(
            drafts,
            FakeCatalogRepository(),
            FakePlayerRepository(),
            FakeMatchRepository(),
            FakeDraftRecordRepository(),
            FakeDraftEnvironment(),
        )
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
        assertThat(vm.uiState.value.local.remainingSeconds).isEqualTo(2)
        advanceTimeBy(3_100)
        assertThat(vm.uiState.value.local.remainingSeconds).isEqualTo(-1)
        vm.tapHero(session.pool.first())
        vm.confirm()
        runCurrent()
        assertThat(vm.uiState.value.local.remainingSeconds).isEqualTo(2)
    }
}
