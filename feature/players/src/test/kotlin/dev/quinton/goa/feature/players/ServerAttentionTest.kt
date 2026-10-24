package dev.quinton.goa.feature.players

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.data.GroupState
import dev.quinton.goa.core.data.ServerSyncState
import dev.quinton.goa.core.data.SyncServerError
import dev.quinton.goa.core.data.SyncServerProblem
import dev.quinton.goa.core.domain.sync.GroupCredentials
import dev.quinton.goa.core.testing.FakeDraftRecordRepository
import dev.quinton.goa.core.testing.FakeGroupRepository
import dev.quinton.goa.core.testing.FakeMatchRepository
import dev.quinton.goa.core.testing.FakePlayerRepository
import dev.quinton.goa.feature.players.players.PlayersViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/** The Players tab's sync icon flags a server sync that needs a person, and only that (ADR-036). */
@OptIn(ExperimentalCoroutinesApi::class)
class ServerAttentionTest {
    @get:Rule val main = MainDispatcherRule()

    private val group = GroupCredentials("g", ByteArray(32) { 1 }, "Friday nights")
    private val server = ServerSyncState(url = "https://goa.example", defaultUrl = "https://goa.example")
    private val groups = FakeGroupRepository(GroupState(group, "phone", null, null, emptyList(), null, server))
    private val ratings = FakeRatingsSnapshotRepository(
        FakeMatchRepository(Fixtures.matches),
        FakeDraftRecordRepository(Fixtures.drafts),
        FakeEloConfigRepository(),
    )

    private fun failWith(problem: SyncServerProblem) {
        val state = groups.current.value
        groups.current.value = state.copy(server = state.server.copy(error = SyncServerError(problem, "x", 1)))
    }

    @Test
    fun onlyAProblemThatWontPassOnItsOwnRaisesTheFlag() = runTest {
        val vm = PlayersViewModel(FakePlayerRepository(Fixtures.players), ratings, groups)
        backgroundScope.launch { vm.uiState.collect {} }
        assertThat(vm.uiState.first { !it.loading }.serverNeedsAttention).isFalse()

        failWith(SyncServerProblem.UNREACHABLE)
        assertThat(vm.uiState.first { !it.loading }.serverNeedsAttention).isFalse()

        failWith(SyncServerProblem.VERSION)
        assertThat(vm.uiState.first { it.serverNeedsAttention }.inGroup).isTrue()

        // Switched off: the server's old problem no longer matters.
        groups.setServerSyncEnabled(false)
        assertThat(vm.uiState.first { !it.serverNeedsAttention }.serverNeedsAttention).isFalse()
    }
}
