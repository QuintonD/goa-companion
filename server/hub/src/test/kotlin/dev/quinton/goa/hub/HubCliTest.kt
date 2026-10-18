package dev.quinton.goa.hub

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.domain.sync.GroupInvite
import dev.quinton.goa.hub.config.HubConfigStore
import dev.quinton.goa.hub.config.HubPaths
import dev.quinton.goa.hub.config.MembershipStore
import dev.quinton.goa.hub.store.AtomicFiles
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class HubCliTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val out = ByteArrayOutputStream()
    private val err = ByteArrayOutputStream()

    private fun run(vararg args: String, env: Map<String, String> = emptyMap(), stdin: String = ""): Int =
        HubCli(
            PrintStream(out, true),
            PrintStream(err, true),
            env,
            FakeClock(),
            stdin.byteInputStream(),
        ).run(args.toList())

    private val data get() = tmp.root.toPath().resolve("hub")

    @Test
    fun `join stores the group owner-only and takes the public URL from the invite`() {
        val code = run("join", "--data", data.toString(), "Join us! ${GroupInvite.format(TestGroup, HUB_URL)}")
        assertThat(code).isEqualTo(HubCli.OK)
        assertThat(MembershipStore(data.resolve("group.json")).group()).isEqualTo(TestGroup)
        assertThat(HubConfigStore(data.resolve("hub.json")).get().publicUrl).isEqualTo(HUB_URL)
        if (AtomicFiles.supportsPosix) {
            assertThat(
                PosixFilePermissions.toString(Files.getPosixFilePermissions(data.resolve("group.json"))),
            ).isEqualTo("rw-------")
        }
        // The same group again is fine; another one is refused.
        assertThat(run("join", "--data", data.toString(), GroupInvite.format(TestGroup))).isEqualTo(HubCli.OK)
        assertThat(run("join", "--data", data.toString(), GroupInvite.format(OtherGroup))).isEqualTo(HubCli.FAILED)
        assertThat(err.toString()).contains("already belongs to")
        assertThat(MembershipStore(data.resolve("group.json")).group()).isEqualTo(TestGroup)
    }

    @Test
    fun `join reads the invite from standard input, as cmd exe would split it at every ampersand`() {
        val message = listOf("Join us in the app:", "", GroupInvite.format(TestGroup, HUB_URL), "").joinToString("\n")
        assertThat(run("join", "--data", data.toString(), stdin = message)).isEqualTo(HubCli.OK)
        assertThat(MembershipStore(data.resolve("group.json")).group()).isEqualTo(TestGroup)
        assertThat(run("join", "-", "--data", data.toString(), stdin = "")).isEqualTo(HubCli.USAGE_ERROR)
    }

    @Test
    fun `the data directory comes from --data, then GOA_HUB_DATA, then the default`() {
        assertThat(HubPaths.resolve("/x", mapOf("GOA_HUB_DATA" to "/y")).root.toString()).contains("x")
        assertThat(
            HubPaths.resolve(null, mapOf("GOA_HUB_DATA" to data.toString())).root,
        ).isEqualTo(data.toAbsolutePath())
        assertThat(HubPaths.resolve(null, emptyMap()).root.toString().replace('\\', '/')).endsWith("/var/lib/goa-hub")
        assertThat(
            run("join", GroupInvite.format(TestGroup), env = mapOf("GOA_HUB_DATA" to data.toString())),
        ).isEqualTo(HubCli.OK)
        assertThat(Files.exists(data.resolve("group.json"))).isTrue()
    }

    @Test
    fun `config sets the public URL and the Android fingerprint, and refuses bad ones`() {
        val fp = List(32) { "%02x".format(it) }.joinToString("")
        assertThat(
            run("config", "--data", data.toString(), "--public-url", "goa.example.org", "--android-cert-sha256", fp),
        )
            .isEqualTo(HubCli.OK)
        val config = HubConfigStore(data.resolve("hub.json")).get()
        assertThat(config.publicUrl).isEqualTo("https://goa.example.org")
        assertThat(config.androidCertSha256.single()).startsWith("00:01:02")
        // Only options given change: a later call without the fingerprint keeps it.
        val again = run("config", "--data", data.toString(), "--public-url", "https://other.example.org")
        assertThat(again).isEqualTo(HubCli.OK)
        val later = HubConfigStore(data.resolve("hub.json")).get()
        assertThat(later.publicUrl).isEqualTo("https://other.example.org")
        assertThat(later.androidCertSha256).isEqualTo(config.androidCertSha256)
        assertThat(
            run("config", "--data", data.toString(), "--public-url", "http://goa.example.org"),
        ).isEqualTo(HubCli.USAGE_ERROR)
        assertThat(
            run("config", "--data", data.toString(), "--android-cert-sha256", "AA:BB"),
        ).isEqualTo(HubCli.USAGE_ERROR)
        assertThat(run("config", "--data", data.toString(), "--bogus", "1")).isEqualTo(HubCli.USAGE_ERROR)
    }

    @Test
    fun `status reports the group, data, devices and last sync without writing anything`() {
        run("join", "--data", data.toString(), GroupInvite.format(TestGroup))
        val before = Files.walk(data).use { it.count() }
        assertThat(run("status", "--data", data.toString())).isEqualTo(HubCli.OK)
        val text = out.toString()
        assertThat(text).contains("Group:     Table Night")
        assertThat(text).contains("Last sync: never")
        assertThat(text).contains("Devices:   0 signed in")
        assertThat(Files.walk(data).use { it.count() }).isEqualTo(before)
    }

    @Test
    fun `unknown commands and a bare call print the usage`() {
        assertThat(run()).isEqualTo(HubCli.USAGE_ERROR)
        assertThat(out.toString()).contains("goa-hub <command>")
        assertThat(run("frobnicate")).isEqualTo(HubCli.USAGE_ERROR)
        assertThat(run("join", "--data", data.toString(), "not an invite")).isEqualTo(HubCli.FAILED)
    }
}
