package dev.quinton.goa.hub

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.domain.sync.GroupInvite
import dev.quinton.goa.hub.config.HubConfigStore
import dev.quinton.goa.hub.config.HubPaths
import dev.quinton.goa.hub.config.MembershipStore
import dev.quinton.goa.hub.store.AtomicFiles
import dev.quinton.goa.hub.store.SyncStateStore
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
    fun `the group's time zone is set with config, cleared with system, and every hub date follows it`() {
        assertThat(run("config", "--data", data.toString(), "--time-zone", "Europe/Berlin")).isEqualTo(HubCli.OK)
        assertThat(HubConfigStore(data.resolve("hub.json")).get().timeZone).isEqualTo("Europe/Berlin")
        assertThat(out.toString()).contains("Time zone: Europe/Berlin")
        // The hub's clock reads it (a UTC machine clock, a group in Berlin).
        val hub = Hub(HubPaths(data), FakeClock(start = 1_760_000_000_000L, zone = java.time.ZoneId.of("UTC")))
        assertThat(hub.clock.zone).isEqualTo(java.time.ZoneId.of("Europe/Berlin"))
        assertThat(
            run("config", "--data", data.toString(), "--time-zone", "Mars/Olympus"),
        ).isEqualTo(HubCli.USAGE_ERROR)
        assertThat(run("config", "--data", data.toString(), "--time-zone", "system")).isEqualTo(HubCli.OK)
        assertThat(HubConfigStore(data.resolve("hub.json")).get().timeZone).isNull()
        val machine = Hub(HubPaths(data), FakeClock(start = 1_760_000_000_000L, zone = java.time.ZoneId.of("UTC")))
        assertThat(machine.clock.zone).isEqualTo(java.time.ZoneId.of("UTC"))
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
    fun `status lists phones seen within 90 days, and a Windows console gets ASCII only`() {
        run("join", "--data", data.toString(), GroupInvite.format(TestGroup))
        val now = FakeClock().nowMillis()
        val state = SyncStateStore(data.resolve("sync-state.json"))
        state.record("p1", "Zoë · Pixel 9", now - 60_000)
        // Recorded last, so nothing has pruned it from the file yet.
        state.record(null, null, now - 91L * 24 * 60 * 60 * 1000)
        val ascii = ByteArrayOutputStream()
        val code = HubCli(
            CliOutput.stream(ascii, asciiOnly = true),
            PrintStream(err, true),
            emptyMap(),
            FakeClock(),
        ).run(listOf("status", "--data", data.toString()))
        assertThat(code).isEqualTo(HubCli.OK)
        val bytes = ascii.toByteArray()
        assertThat(bytes.all { it in 0..0x7F }).isTrue()
        val text = bytes.decodeToString()
        assertThat(text).contains("  Zoe - Pixel 9: ")
        assertThat(text).doesNotContain("A phone")
        // The 90-day rule is applied when shown; status itself writes nothing.
        assertThat(state.readAll().clients).hasSize(2)
    }

    @Test
    fun `CLI output folds to ASCII where asked and is UTF-8 elsewhere`() {
        val ascii = ByteArrayOutputStream()
        CliOutput.stream(ascii, asciiOnly = true).apply {
            println("iPhone · Safari — “Zoë’s” 1–2…")
            print(StringBuilder("Åsa → 日本"))
        }
        assertThat(ascii.toString(Charsets.US_ASCII).lines())
            .containsExactly("iPhone - Safari - \"Zoe's\" 1-2...", "Asa -> ??").inOrder()
        val utf8 = ByteArrayOutputStream()
        CliOutput.stream(utf8, asciiOnly = false).print("iPhone · Safari")
        assertThat(utf8.toString(Charsets.UTF_8)).isEqualTo("iPhone · Safari")
    }

    @Test
    fun `unknown commands and a bare call print the usage`() {
        assertThat(run()).isEqualTo(HubCli.USAGE_ERROR)
        assertThat(out.toString()).contains("goa-hub <command>")
        assertThat(run("frobnicate")).isEqualTo(HubCli.USAGE_ERROR)
        assertThat(run("join", "--data", data.toString(), "not an invite")).isEqualTo(HubCli.FAILED)
    }
}
