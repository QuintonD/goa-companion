package dev.quinton.goa.feature.players

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.data.GroupState
import dev.quinton.goa.core.data.HubError
import dev.quinton.goa.core.data.HubProblem
import dev.quinton.goa.core.domain.sync.GroupCredentials
import dev.quinton.goa.core.testing.FakeDraftRecordRepository
import dev.quinton.goa.core.testing.FakeGroupRepository
import dev.quinton.goa.core.testing.FakeMatchRepository
import dev.quinton.goa.core.testing.FakePlayerMatchUpRepository
import dev.quinton.goa.core.testing.FakePlayerMergeRepository
import dev.quinton.goa.core.testing.FakePlayerRepository
import dev.quinton.goa.feature.players.players.PlayersViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/** The Players tab's sync icon flags a hub sync that needs a person, and only that (ADR-030 §5). */
@OptIn(ExperimentalCoroutinesApi::class)
class HubAttentionTest {
    @get:Rule val main = MainDispatcherRule()

    private val group = GroupCredentials("g", ByteArray(32) { 1 }, "Friday nights")
    private val groups = FakeGroupRepository(
        GroupState(group, "phone", null, null, emptyList(), null, hubUrl = "https://goa.example"),
    )
    private val ratings = FakeRatingsSnapshotRepository(
        FakeMatchRepository(Fixtures.matches),
        FakeDraftRecordRepository(Fixtures.drafts),
        FakeEloConfigRepository(),
    )

    @Test
    fun onlyAProblemThatWontPassOnItsOwnRaisesTheFlag() = runTest {
        val players = FakePlayerRepository(Fixtures.players)
        val matches = FakeMatchRepository(Fixtures.matches)
        val vm = PlayersViewModel(
            players,
            ratings,
            groups,
            matches,
            FakePlayerMergeRepository(matches, players.merges),
            FakePlayerMatchUpRepository(),
        )
        backgroundScope.launch { vm.uiState.collect {} }
        assertThat(vm.uiState.first { !it.loading }.hubNeedsAttention).isFalse()
        // With a hub the sync action opens the Group screen (how the automatic sync went).
        assertThat(vm.uiState.first { !it.loading }.hasHub).isTrue()

        groups.current.value = groups.current.value.copy(hubError = HubError(HubProblem.UNREACHABLE, "Offline", 1))
        assertThat(vm.uiState.first { !it.loading }.hubNeedsAttention).isFalse()

        groups.current.value = groups.current.value.copy(hubError = HubError(HubProblem.VERSION, "Update", 2))
        assertThat(vm.uiState.first { it.hubNeedsAttention }.inGroup).isTrue()

        groups.setHubUrl(null)
        assertThat(vm.uiState.first { !it.hubNeedsAttention }.hubNeedsAttention).isFalse()
        assertThat(vm.uiState.first { !it.hasHub }.inGroup).isTrue()
    }
}
