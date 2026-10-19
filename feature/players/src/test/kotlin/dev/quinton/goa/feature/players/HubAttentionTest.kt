package dev.quinton.goa.feature.players

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.data.EditClock
import dev.quinton.goa.core.data.GroupState
import dev.quinton.goa.core.data.HubError
import dev.quinton.goa.core.data.HubProblem
import dev.quinton.goa.core.data.sync.HubSyncControl
import dev.quinton.goa.core.domain.sync.GroupCredentials
import dev.quinton.goa.core.testing.FakeDraftRecordRepository
import dev.quinton.goa.core.testing.FakeGroupRepository
import dev.quinton.goa.core.testing.FakeMatchRepository
import dev.quinton.goa.core.testing.FakePlayerMatchUpRepository
import dev.quinton.goa.core.testing.FakePlayerMergeRepository
import dev.quinton.goa.core.testing.FakePlayerRepository
import dev.quinton.goa.feature.players.players.HubSyncTap
import dev.quinton.goa.feature.players.players.PlayersViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/** The Players tab's sync icon: syncs with the hub and says how it went, and flags a problem that needs a person. */
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
        // With a hub the sync icon syncs with it (see below); with a problem it opens the Group screen.
        assertThat(vm.uiState.first { !it.loading }.hasHub).isTrue()

        groups.current.value = groups.current.value.copy(hubError = HubError(HubProblem.UNREACHABLE, "Offline", 1))
        assertThat(vm.uiState.first { !it.loading }.hubNeedsAttention).isFalse()

        groups.current.value = groups.current.value.copy(hubError = HubError(HubProblem.VERSION, "Update", 2))
        assertThat(vm.uiState.first { it.hubNeedsAttention }.inGroup).isTrue()

        groups.setHubUrl(null)
        assertThat(vm.uiState.first { !it.hubNeedsAttention }.hubNeedsAttention).isFalse()
        assertThat(vm.uiState.first { !it.hasHub }.inGroup).isTrue()
    }

    /** Stands in for the WorkManager sync: [answer] is what the hub sync records when asked. */
    private inner class FakeHub(var answer: (Long) -> GroupState?) : HubSyncControl {
        override val running = MutableStateFlow(false)
        var syncs = 0

        override fun syncNow() {
            syncs++
            answer(now)?.let { groups.current.value = it }
        }
    }

    private var now = 1_000_000L

    private fun tabVm(hub: HubSyncControl): PlayersViewModel {
        val players = FakePlayerRepository(Fixtures.players)
        val matches = FakeMatchRepository(Fixtures.matches)
        return PlayersViewModel(
            players,
            ratings,
            groups,
            matches,
            FakePlayerMergeRepository(matches, players.merges),
            FakePlayerMatchUpRepository(),
            hub,
            EditClock { now },
        )
    }

    @Test
    fun theSyncIconSyncsWithTheHubAndSaysHowItWent() = runTest {
        val hub = FakeHub { at -> groups.current.value.copy(hubSyncedAt = at, hubError = null) }
        val vm = tabVm(hub)
        vm.syncWithHub()
        val done = vm.hubSyncTap.first { it.message != null }
        assertThat(done).isEqualTo(HubSyncTap(message = "Synced with the hub", details = false))
        assertThat(hub.syncs).isEqualTo(1)
        vm.hubSyncMessageShown()
        assertThat(vm.hubSyncTap.value.message).isNull()

        // A failure says what went wrong and offers the Group screen.
        now += 60_000
        hub.answer = { at ->
            groups.current.value.copy(hubError = HubError(HubProblem.UNREACHABLE, "Can't reach the hub.", at))
        }
        vm.syncWithHub()
        assertThat(vm.hubSyncTap.first { it.message != null })
            .isEqualTo(HubSyncTap(message = "Can't reach the hub.", details = true))
    }

    @Test
    fun anOlderOutcomeIsNotTakenForTheTappedSyncAndNoAnswerSaysSo() = runTest {
        // The last sync (before the tap) failed; nothing new arrives.
        groups.current.value = groups.current.value.copy(
            hubSyncedAt = now - 5_000,
            hubError = HubError(HubProblem.UNREACHABLE, "Old", now - 1_000),
        )
        val hub = FakeHub { null }
        val vm = tabVm(hub)
        vm.syncWithHub()
        assertThat(vm.hubSyncTap.value.syncing).isTrue()
        vm.syncWithHub() // a second tap while it runs does nothing
        assertThat(hub.syncs).isEqualTo(1)
        advanceTimeBy(HubSyncControl.WAIT_MS + 1)
        assertThat(vm.hubSyncTap.first { it.message != null })
            .isEqualTo(HubSyncTap(message = HubSyncTap.STILL_SYNCING, details = true))
    }
}
