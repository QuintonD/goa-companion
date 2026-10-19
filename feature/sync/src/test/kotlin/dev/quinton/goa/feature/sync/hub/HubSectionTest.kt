package dev.quinton.goa.feature.sync.hub

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.data.EditClock
import dev.quinton.goa.core.data.HubError
import dev.quinton.goa.core.data.HubProblem
import dev.quinton.goa.core.domain.sync.GroupInvite
import dev.quinton.goa.core.testing.FakeGroupRepository
import dev.quinton.goa.core.testing.FakeMatchRepository
import dev.quinton.goa.core.testing.FakePlayerMergeRepository
import dev.quinton.goa.core.testing.FakePlayerRepository
import dev.quinton.goa.feature.sync.FakeSyncRepository
import dev.quinton.goa.feature.sync.MainDispatcherRule
import dev.quinton.goa.feature.sync.PhoneName
import dev.quinton.goa.feature.sync.TestGroup
import dev.quinton.goa.feature.sync.group.GroupEvent
import dev.quinton.goa.feature.sync.group.GroupViewModel
import dev.quinton.goa.feature.sync.groupState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The Group screen's Hub section (ADR-030 §5): address, status, sync now, remove. */
@RunWith(RobolectricTestRunner::class)
class HubSectionTest {
    @get:Rule val main = MainDispatcherRule()

    private var now = 10 * 60_000L
    private val clock = EditClock { now }

    private class FakeControl : HubSyncControl {
        override val running = MutableStateFlow(false)
        var syncs = 0

        override fun syncNow() {
            syncs++
        }
    }

    private val control = FakeControl()

    private fun vm(groups: FakeGroupRepository) = GroupViewModel(
        groups,
        FakePlayerRepository(),
        FakeMatchRepository(),
        FakeSyncRepository(),
        clock,
        PhoneName { "Pixel" },
        FakePlayerMergeRepository(),
        control,
    )

    @Test
    fun aValidAddressIsSavedAsAnOriginAndABadOneSaysWhy() = runTest {
        val groups = FakeGroupRepository(groupState())
        val vm = vm(groups)
        backgroundScope.launch { vm.uiState.collect {} }
        assertThat(vm.uiState.first { !it.loading }.hub.url).isNull()

        vm.setHub("http://goa.example")
        assertThat(vm.uiState.first { it.hub.addressError != null }.hub.addressError).contains("https")
        assertThat(groups.current.value.hubUrl).isNull()
        vm.clearHubAddressError()
        assertThat(vm.uiState.first { it.hub.addressError == null }.hub.addressError).isNull()

        vm.setHub("  Goa.Tail4F2A9C.ts.net/ ")
        val hub = vm.uiState.first { it.hub.url != null }.hub
        assertThat(groups.current.value.hubUrl).isEqualTo("https://goa.tail4f2a9c.ts.net")
        assertThat(hub.host).isEqualTo("goa.tail4f2a9c.ts.net")
        assertThat(hub.status).isEqualTo("Not synced yet")
        assertThat(vm.uiState.value.message).contains("Hub saved")
    }

    @Test
    fun theStatusLineFollowsTheLastSyncAndARunningOne() = runTest {
        val groups = FakeGroupRepository(
            groupState().copy(hubUrl = "https://goa.example", hubSyncedAt = now - 2 * 60_000),
        )
        val vm = vm(groups)
        backgroundScope.launch { vm.uiState.collect {} }
        assertThat(vm.uiState.first { !it.loading }.hub.status).isEqualTo("Synced 2 min ago")

        control.running.value = true
        val syncing = vm.uiState.first { it.hub.syncing }.hub
        assertThat(syncing.status).isEqualTo("Syncing…")

        control.running.value = false
        groups.current.value = groups.current.value.copy(
            hubError = HubError(HubProblem.UNREACHABLE, "Can't reach the hub.", now - 5 * 60_000),
        )
        val failed = vm.uiState.first { it.hub.hasProblem && !it.hub.syncing }.hub
        assertThat(failed.error).isEqualTo("Can't reach the hub.")
        assertThat(failed.errorAgo).isEqualTo("5 min ago")
        assertThat(failed.retrying).isTrue()
        assertThat(failed.status).isEqualTo("Last synced 2 min ago")

        groups.current.value = groups.current.value.copy(
            hubError = HubError(HubProblem.WRONG_KEY, "Key refused.", now),
        )
        assertThat(vm.uiState.first { it.hub.error == "Key refused." }.hub.retrying).isFalse()
    }

    @Test
    fun syncNowAndRetryAskTheSchedulerAndRemovingForgetsTheHub() = runTest {
        val groups = FakeGroupRepository(groupState().copy(hubUrl = "https://goa.example"))
        val vm = vm(groups)
        backgroundScope.launch { vm.uiState.collect {} }

        vm.syncHub()
        assertThat(control.syncs).isEqualTo(1)

        vm.removeHub()
        val state = vm.uiState.first { it.hub.url == null }
        assertThat(groups.current.value.hubUrl).isNull()
        assertThat(state.message).contains("Hub removed")
    }

    @Test
    fun outsideAGroupThereIsNoHubToShow() {
        val state = groupState(group = null).copy(hubUrl = "https://goa.example")
        assertThat(HubText.ui(state, syncing = true, now = now).url).isNull()
        assertThat(HubText.ui(state, syncing = true, now = now).syncing).isFalse()
    }

    @Test
    fun anInviteLinkOpenedFromOutsideJoinsButNeverReplacesAnotherGroup() = runTest {
        val link = "https://goa.example/join#" + GroupInvite.format(TestGroup, hubUrl = "https://goa.example")
        val other = dev.quinton.goa.core.domain.sync.GroupCredentials.create("Tuesday club")
        val groups = FakeGroupRepository(groupState(group = other))
        val inOther = vm(groups)
        backgroundScope.launch { inOther.uiState.collect {} }
        inOther.openLink(link)
        assertThat(inOther.uiState.first { it.message != null }.message)
            .isEqualTo("This phone is in “Tuesday club”. Leave it first to join “Friday nights”.")
        assertThat(groups.current.value.group).isEqualTo(other)

        val fresh = FakeGroupRepository(groupState(group = null))
        val vm = vm(fresh)
        backgroundScope.launch { vm.uiState.collect {} }
        vm.openLink(link)
        vm.uiState.first { it.inGroup }
        assertThat(fresh.current.value.group?.groupId).isEqualTo(TestGroup.groupId)
        assertThat(fresh.current.value.hubUrl).isEqualTo("https://goa.example")
        // The same link handled once, however often the screen recomposes.
        vm.openLink(link)
        vm.openLink("https://goa.example/join")
    }

    @Test
    fun anInviteWithThisGroupsIdButAnotherKeyNeverReplacesTheKeyOrTheHub() = runTest {
        // Someone who knows the group id (a sync file's envelope, a past member) makes their own key
        // and hub: joining would send this phone's whole match log there, sealed with their key.
        val forged = dev.quinton.goa.core.domain.sync.GroupCredentials(
            TestGroup.groupId,
            ByteArray(32) { 7 },
            TestGroup.name,
        )
        val groups = FakeGroupRepository(groupState().copy(hubUrl = "https://goa.example"))
        val before = groups.current.value
        val vm = vm(groups)
        backgroundScope.launch { vm.uiState.collect {} }

        vm.join(GroupInvite.format(forged, hubUrl = "https://evil.example"))
        assertThat(vm.uiState.first { it.message != null }.message).contains("different key")
        assertThat(groups.current.value).isEqualTo(before)
        vm.messageShown()

        val link = "https://goa.example/join#" + GroupInvite.format(forged, hubUrl = "https://goa.example")
        vm(groups).apply {
            backgroundScope.launch { uiState.collect {} }
            openLink(link)
            assertThat(uiState.first { it.message != null }.message).contains("different key")
        }
        assertThat(groups.current.value).isEqualTo(before)

        // The group's real invite is still "already in".
        vm.join(GroupInvite.format(TestGroup, hubUrl = "https://goa.example"))
        assertThat(vm.uiState.first { it.message != null }.message).contains("already in")
    }

    @Test
    fun anInviteLinkNamingAnotherHubThanItsOwnIsRefused() = runTest {
        val groups = FakeGroupRepository(groupState(group = null))
        val vm = vm(groups)
        backgroundScope.launch { vm.uiState.collect {} }
        val link = "https://goa.example/join#" + GroupInvite.format(TestGroup, hubUrl = "https://evil.example")
        vm.openLink(link)
        assertThat(vm.uiState.first { it.message != null }.message).contains("names another hub")
        assertThat(groups.current.value.group).isNull()

        // Its own hub, any spelling of it, joins.
        val fresh = FakeGroupRepository(groupState(group = null))
        val ok = vm(fresh)
        backgroundScope.launch { ok.uiState.collect {} }
        ok.openLink("https://Goa.Example:443/join#" + GroupInvite.format(TestGroup, hubUrl = "https://goa.example"))
        ok.uiState.first { it.inGroup }
        assertThat(fresh.current.value.hubUrl).isEqualTo("https://goa.example")
    }

    @Test
    fun anInviteWithAHubJoinsWithoutANearbySessionTheHubSyncsInstead() = runTest {
        val groups = FakeGroupRepository(groupState(group = null))
        val vm = vm(groups)
        backgroundScope.launch { vm.uiState.collect {} }
        vm.navigation.test {
            vm.join(GroupInvite.format(TestGroup, hubUrl = "https://goa.example"))
            val joined = vm.uiState.first { it.inGroup && it.message != null }
            assertThat(joined.message).contains("from its hub")
            assertThat(joined.hub.url).isEqualTo("https://goa.example")
            expectNoEvents()
            // Without a hub, the first sync is face to face as before.
            vm.leave()
            vm.uiState.first { !it.inGroup }
            vm.join(GroupInvite.format(TestGroup))
            assertThat(awaitItem()).isEqualTo(GroupEvent.OpenFirstSync)
        }
    }
}
