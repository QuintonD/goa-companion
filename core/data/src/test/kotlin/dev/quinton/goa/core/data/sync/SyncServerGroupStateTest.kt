package dev.quinton.goa.core.data.sync

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.data.SyncServerDefaults
import dev.quinton.goa.core.data.SyncServerError
import dev.quinton.goa.core.data.SyncServerProblem
import dev.quinton.goa.core.data.SyncServerResult
import dev.quinton.goa.core.datastore.sync.GroupSyncStore
import dev.quinton.goa.core.domain.sync.GroupCredentials
import dev.quinton.goa.core.domain.sync.SyncChanges
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The group's sync server in `group_sync` (ADR-036): default, invite override, switch, status. */
class SyncServerGroupStateTest {
    @get:Rule val tmp = TemporaryFolder()

    private val default = "https://oldboi.taila4d38d.ts.net:8443"
    private val group = GroupCredentials.create("Friday nights")

    private fun repository(defaults: SyncServerDefaults = SyncServerDefaults(default)) = DataStoreGroupRepository(
        GroupSyncStore(PreferenceDataStoreFactory.create(produceFile = { File(tmp.root, "g.preferences_pb") })),
        defaults,
    )

    private val synced = SyncServerResult.Synced(SyncChanges.NONE, at = 5, rev = 3, uploaded = false)

    @Test
    fun theBuildDefaultIsUsedAndOnUntilSwitchedOff() = runTest {
        val groups = repository()
        assertThat(groups.state.first().serverSyncOn).isFalse()
        groups.join(group)
        val server = groups.state.first().server
        assertThat(server.url).isEqualTo(default)
        assertThat(server.custom).isFalse()
        assertThat(server.defaultUrl).isEqualTo(default)
        assertThat(groups.state.first().serverSyncOn).isTrue()

        groups.setServerSyncEnabled(false)
        assertThat(groups.state.first().server.activeUrl).isNull()
        assertThat(groups.state.first().serverSyncOn).isFalse()
        groups.setServerSyncEnabled(true)
        assertThat(groups.state.first().serverSyncOn).isTrue()
    }

    @Test
    fun withoutADefaultThereIsNoServerUntilOneIsSet() = runTest {
        val groups = repository(SyncServerDefaults(""))
        groups.join(group)
        assertThat(groups.state.first().server.url).isNull()
        assertThat(groups.state.first().serverSyncOn).isFalse()
        // A release build never takes plain HTTP, even to a local test server.
        groups.setServerUrl("http://10.0.2.2:8787")
        assertThat(groups.state.first().server.url).isNull()
        groups.setServerUrl("goa.example.org")
        assertThat(groups.state.first().server.url).isEqualTo("https://goa.example.org")
        assertThat(groups.state.first().server.custom).isTrue()
    }

    @Test
    fun anInvitesServerOverridesTheDefaultOnlyWhenItDiffers() = runTest {
        val groups = repository()
        groups.join(group, serverUrl = "$default/")
        assertThat(groups.state.first().server.custom).isFalse()

        val other = GroupCredentials.create("Tuesday club")
        groups.join(other, serverUrl = "https://goa.example.org")
        assertThat(groups.state.first().server.url).isEqualTo("https://goa.example.org")
        assertThat(groups.state.first().server.custom).isTrue()
        // Re-joining the same group from an invite without a server keeps the group's server.
        groups.join(other)
        assertThat(groups.state.first().server.url).isEqualTo("https://goa.example.org")
        // Another group without a server in its invite uses the default.
        groups.join(group)
        assertThat(groups.state.first().server.url).isEqualTo(default)
    }

    @Test
    fun theStatusBelongsToOneGroupAndOneServer() = runTest {
        val groups = repository()
        groups.join(group)
        groups.recordServerSync(group.groupId, default, synced)
        assertThat(groups.state.first().server.syncedAt).isEqualTo(5)
        assertThat(groups.state.first().server.rev).isEqualTo(3)

        // Switching off and on keeps it; the same server typed again keeps it.
        groups.setServerSyncEnabled(false)
        groups.setServerSyncEnabled(true)
        groups.setServerUrl(default)
        assertThat(groups.state.first().server.syncedAt).isEqualTo(5)

        groups.setServerUrl("https://goa.example.org")
        assertThat(groups.state.first().server.syncedAt).isNull()
        // An outcome for the old server, arriving late, is dropped.
        groups.recordServerSync(group.groupId, default, synced)
        assertThat(groups.state.first().server.syncedAt).isNull()

        val error = SyncServerError(SyncServerProblem.WRONG_KEY, "Key", 9)
        groups.recordServerSync(group.groupId, "https://goa.example.org", SyncServerResult.Failed(error))
        assertThat(groups.state.first().server.error).isEqualTo(error)
        groups.setServerUrl(null)
        assertThat(groups.state.first().server.url).isEqualTo(default)
        assertThat(groups.state.first().server.error).isNull()

        groups.recordServerSync(group.groupId, default, synced)
        groups.join(GroupCredentials.create("Other"))
        assertThat(groups.state.first().server.syncedAt).isNull()
    }

    @Test
    fun leavingForgetsTheServerChoices() = runTest {
        val groups = repository()
        groups.join(group, serverUrl = "https://goa.example.org")
        groups.setServerSyncEnabled(false)
        groups.recordServerSync(group.groupId, "https://goa.example.org", synced)

        groups.leave()
        groups.join(group)

        val server = groups.state.first().server
        assertThat(server.url).isEqualTo(default)
        assertThat(server.enabled).isTrue()
        assertThat(server.syncedAt).isNull()
    }
}
