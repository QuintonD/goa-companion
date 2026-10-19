package dev.quinton.goa.hub.store

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.hub.FakeClock
import dev.quinton.goa.hub.player
import dev.quinton.goa.hub.testHub
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Everything in the data directory is the group's (its data, backups, devices, the key): owner-only
 * whatever the umask, also when the hub runs outside the systemd unit (which sets `UMask=0077`).
 * POSIX only; elsewhere the deploy kit's folder ACLs do it.
 */
class DataPermissionsTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `the data directory is 0700 and every file the hub writes is 0600`() {
        assumeTrue(AtomicFiles.supportsPosix)
        val root = tmp.root.toPath().resolve("hub")
        val open = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwxr-xr-x"))
        Files.createDirectories(root, open)
        val clock = FakeClock()
        val hub = testHub(root, clock)
        runBlocking { hub.groupStore.upsertPlayer(player("ann", "Ann")) }
        hub.syncState.record("phone", "Phone", clock.nowMillis())
        hub.devices.create("Browser", clock.nowMillis())

        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(root))).isEqualTo("rwx------")
        val files = Files.walk(root).use { paths -> paths.filter { Files.isRegularFile(it) }.toList() }
        assertThat(files.map { root.relativize(it).toString() }).containsAtLeast(
            "hub.json",
            "group.json",
            "data.json",
            "sync-state.json",
            "vapid.json",
        )
        files.forEach { file ->
            assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(file))).isEqualTo("rw-------")
        }
    }
}
