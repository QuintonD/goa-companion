package dev.quinton.goa.feature.sync.server

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.data.EditClock
import dev.quinton.goa.core.data.ServerSyncState
import dev.quinton.goa.core.data.SyncServerDefaults
import dev.quinton.goa.core.data.SyncServerError
import dev.quinton.goa.core.data.SyncServerProblem
import dev.quinton.goa.core.data.SyncServerResult
import dev.quinton.goa.core.domain.sync.GroupInvite
import dev.quinton.goa.core.domain.sync.SyncChanges
import dev.quinton.goa.core.testing.FakeGroupRepository
import dev.quinton.goa.core.testing.FakeMatchRepository
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

/** The Group screen's Sync server section (ADR-036): status, sync now, switch, address, joining. */
@RunWith(RobolectricTestRunner::class)
class SyncServerSectionTest {
    @get:Rule val main = MainDispatcherRule()

    private var now = 10 * 60_000L
    private val clock = EditClock { now }
    private val default = "https://oldboi.taila4d38d.ts.net:8443"
    private val withDefault = ServerSyncState(url = default, defaultUrl = default)

    private class FakeControl : SyncServerControl {
        override val running = MutableStateFlow(false)
        var syncs = 0
        var onces = 0
        var result: SyncServerResult = SyncServerResult.Synced(SyncChanges(matchesAdded = 3), 1, 1, uploaded = false)

        override fun syncNow() {
            syncs++
        }

        override suspend fun syncOnce(): SyncServerResult {
            onces++
            return result
        }
    }

    private val control = FakeControl()

    private fun vm(groups: FakeGroupRepository, defaults: SyncServerDefaults = SyncServerDefaults(default)) =
        GroupViewModel(
            groups,
            FakePlayerRepository(),
            FakeMatchRepository(),
            FakeSyncRepository(),
            clock,
            PhoneName { "Pixel" },
            control,
            defaults,
        )

    @Test
    fun theStatusLineFollowsTheLastSyncARunningOneAndErrors() = runTest {
        val groups = FakeGroupRepository(groupState().copy(server = withDefault.copy(syncedAt = now - 2 * 60_000)))
        val vm = vm(groups)
        backgroundScope.launch { vm.uiState.collect {} }
        val idle = vm.uiState.first { !it.loading }.server
        assertThat(idle.status).isEqualTo("Synced 2 min ago")
        assertThat(idle.host).isEqualTo("oldboi.taila4d38d.ts.net:8443")

        control.running.value = true
        assertThat(vm.uiState.first { it.server.syncing }.server.status).isEqualTo("Syncing…")
        control.running.value = false

        val offline = SyncServerError(SyncServerProblem.UNREACHABLE, "Can't reach the sync server.", now - 5 * 60_000)
        groups.current.value = groups.current.value.copy(server = groups.current.value.server.copy(error = offline))
        val failed = vm.uiState.first { it.server.hasProblem && !it.server.syncing }.server
        assertThat(failed.error).isEqualTo("Can't reach the sync server.")
        assertThat(failed.errorAgo).isEqualTo("5 min ago")
        assertThat(failed.retrying).isTrue()
        assertThat(failed.status).isEqualTo("Synced 2 min ago")

        val key = SyncServerError(SyncServerProblem.WRONG_KEY, "Key refused.", now)
        groups.current.value = groups.current.value.copy(server = groups.current.value.server.copy(error = key))
        assertThat(vm.uiState.first { it.server.error == "Key refused." }.server.retrying).isFalse()

        // Switched off: no error, no syncing, "Off".
        vm.setServerSyncEnabled(false)
        val off = vm.uiState.first { !it.server.enabled }.server
        assertThat(off.status).isEqualTo("Off")
        assertThat(off.error).isNull()
        assertThat(off.active).isFalse()
    }

    @Test
    fun syncNowAsksTheScheduler() = runTest {
        val vm = vm(FakeGroupRepository(groupState().copy(server = withDefault)))
        vm.syncWithServer()
        assertThat(control.syncs).isEqualTo(1)
    }

    @Test
    fun aValidAddressIsSavedAsAnOriginAndABadOneSaysWhy() = runTest {
        val groups = FakeGroupRepository(groupState().copy(server = withDefault))
        val vm = vm(groups)
        backgroundScope.launch { vm.uiState.collect {} }

        vm.setServerAddress("http://goa.example")
        assertThat(vm.uiState.first { it.server.addressError != null }.server.addressError).contains("https")
        assertThat(groups.current.value.server.url).isEqualTo(default)
        vm.clearAddressError()
        assertThat(vm.uiState.first { it.server.addressError == null }.server.addressError).isNull()

        vm.setServerAddress("  Goa.Example.org:8443/ ")
        val custom = vm.uiState.first { it.server.custom }
        assertThat(groups.current.value.server.url).isEqualTo("https://goa.example.org:8443")
        assertThat(custom.server.host).isEqualTo("goa.example.org:8443")
        assertThat(custom.message).contains("Server saved")
        // Its invite now carries the server; the app's own server never travels.
        assertThat(GroupInvite.parseInvite(custom.invite!!).getOrThrow().serverUrl)
            .isEqualTo("https://goa.example.org:8443")

        vm.useDefaultServer()
        val back = vm.uiState.first { !it.server.custom }
        assertThat(back.server.url).isEqualTo(default)
        assertThat(GroupInvite.parseInvite(back.invite!!).getOrThrow().serverUrl).isNull()
        assertThat(back.invite).doesNotContain("&s=")
    }

    @Test
    fun releaseBuildsRefusePlainHttpEvenLocally() = runTest {
        val groups = FakeGroupRepository(groupState().copy(server = withDefault))
        val release = vm(groups, SyncServerDefaults(default, allowLocalHttp = false))
        release.setServerAddress("http://10.0.2.2:8787")
        assertThat(groups.current.value.server.url).isEqualTo(default)
        val debug = vm(groups, SyncServerDefaults(default, allowLocalHttp = true))
        debug.setServerAddress("http://10.0.2.2:8787")
        debug.uiState.first { !it.loading }
        assertThat(groups.current.value.server.url).isEqualTo("http://10.0.2.2:8787")
    }

    @Test
    fun joiningWithAServerGetsTheDataFromItWithoutANearbySession() = runTest {
        val groups = FakeGroupRepository(groupState(group = null).copy(server = withDefault))
        val vm = vm(groups)
        backgroundScope.launch { vm.uiState.collect {} }
        vm.navigation.test {
            vm.join(GroupInvite.format(TestGroup))
            val joined = vm.uiState.first { it.inGroup && it.message != null }
            assertThat(joined.message).isEqualTo("Joined “Friday nights”. From the sync server: +3 matches.")
            assertThat(control.onces).isEqualTo(1)
            expectNoEvents()
        }
    }

    @Test
    fun anInvitesServerIsKeptForTheGroup() = runTest {
        val groups = FakeGroupRepository(groupState(group = null).copy(server = withDefault))
        val vm = vm(groups)
        backgroundScope.launch { vm.uiState.collect {} }
        vm.join(GroupInvite.format(TestGroup, "https://goa.example.org"))
        vm.uiState.first { it.inGroup }
        assertThat(groups.current.value.server.url).isEqualTo("https://goa.example.org")
        assertThat(groups.current.value.server.custom).isTrue()
    }

    @Test
    fun whenTheServerCantHelpTheFirstSyncIsFaceToFace() = runTest {
        listOf(
            SyncServerResult.Failed(SyncServerError(SyncServerProblem.UNREACHABLE, "Offline", 1)),
            SyncServerResult.Synced(SyncChanges.NONE, 1, 1, uploaded = true),
        ).forEach { outcome ->
            control.result = outcome
            val groups = FakeGroupRepository(groupState(group = null).copy(server = withDefault))
            val vm = vm(groups)
            backgroundScope.launch { vm.uiState.collect {} }
            vm.navigation.test {
                vm.join(GroupInvite.format(TestGroup))
                assertThat(awaitItem()).isEqualTo(GroupEvent.OpenFirstSync)
            }
            assertThat(vm.uiState.first { it.message != null }.message).contains("sync face to face")
        }
    }

    @Test
    fun withoutAServerTheFirstSyncIsFaceToFaceAsBefore() = runTest {
        val groups = FakeGroupRepository(groupState(group = null))
        val vm = vm(groups, SyncServerDefaults.NONE)
        backgroundScope.launch { vm.uiState.collect {} }
        vm.navigation.test {
            vm.join(GroupInvite.format(TestGroup))
            assertThat(awaitItem()).isEqualTo(GroupEvent.OpenFirstSync)
        }
        assertThat(control.onces).isEqualTo(0)
        val state = vm.uiState.first { it.inGroup }
        assertThat(state.server.url).isNull()
        assertThat(state.server.status).isNull()
    }

    @Test
    fun outsideAGroupThereIsNoServerToShow() {
        val state = groupState(group = null).copy(server = withDefault)
        assertThat(SyncServerText.ui(state, syncing = true, now = now).url).isNull()
        assertThat(SyncServerText.ui(state, syncing = true, now = now).syncing).isFalse()
    }
}
