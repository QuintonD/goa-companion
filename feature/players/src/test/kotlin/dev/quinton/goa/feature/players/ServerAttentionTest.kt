package dev.quinton.goa.feature.players

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.data.GroupState
import dev.quinton.goa.core.data.ServerSyncState
import dev.quinton.goa.core.data.SyncServerControl
import dev.quinton.goa.core.data.SyncServerError
import dev.quinton.goa.core.data.SyncServerOutcome
import dev.quinton.goa.core.data.SyncServerProblem
import dev.quinton.goa.core.domain.sync.GroupCredentials
import dev.quinton.goa.core.testing.FakeDraftRecordRepository
import dev.quinton.goa.core.testing.FakeGroupRepository
import dev.quinton.goa.core.testing.FakeMatchRepository
import dev.quinton.goa.core.testing.FakePlayerRepository
import dev.quinton.goa.feature.players.players.PlayersViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
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

        // A full server waits for its owner, so it is flagged though it is retried.
        failWith(SyncServerProblem.SERVER_FULL)
        assertThat(vm.uiState.first { it.serverNeedsAttention }.serverNeedsAttention).isTrue()

        // Switched off: the server's old problem no longer matters.
        groups.setServerSyncEnabled(false)
        val off = vm.uiState.first { !it.serverNeedsAttention }
        assertThat(off.serverSyncOn).isFalse()
    }

    @Test
    fun withAServerTheSyncIconSyncsThroughIt() = runTest {
        var syncs = 0
        val control = object : SyncServerControl {
            override val running = flowOf(false)

            override fun syncNow() {
                syncs++
            }

            override fun syncAndWatch() = flowOf(SyncServerOutcome.FAILED)
        }
        val vm = PlayersViewModel(FakePlayerRepository(Fixtures.players), ratings, groups, control)
        backgroundScope.launch { vm.uiState.collect {} }
        assertThat(vm.uiState.first { !it.loading }.serverSyncOn).isTrue()
        vm.syncWithServer()
        assertThat(syncs).isEqualTo(1)
    }
}
