package dev.quinton.goa.hub

import dev.quinton.goa.core.domain.sync.GroupInvite
import dev.quinton.goa.core.domain.sync.SyncDocument
import dev.quinton.goa.core.domain.sync.SyncJson
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.hub.config.HubConfig
import dev.quinton.goa.hub.config.HubConfigStore
import dev.quinton.goa.hub.config.HubPaths
import dev.quinton.goa.hub.config.MembershipStore
import dev.quinton.goa.hub.store.AtomicFiles
import dev.quinton.goa.hub.store.Devices
import dev.quinton.goa.hub.store.GroupData
import dev.quinton.goa.hub.store.SyncStateStore
import dev.quinton.goa.hub.web.hubModule
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import java.io.PrintStream
import java.nio.file.Files
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The `goa-hub` command line (ADR-030 §2, §10). The deploy kit (systemd unit, Caddy, scripts)
 * relies on exactly these commands, flags and the `GOA_HUB_DATA` variable.
 *
 * ```
 * goa-hub serve  [--data DIR] [--host 127.0.0.1] [--port 8080]
 * goa-hub join   [--data DIR] "<invite text>"
 * goa-hub config [--data DIR] [--public-url https://x.duckdns.org] [--android-cert-sha256 AA:BB:…]
 *                [--time-zone Europe/London|system]
 * goa-hub status [--data DIR]
 * ```
 */
class HubCli(
    private val out: PrintStream,
    private val err: PrintStream,
    private val env: Map<String, String> = System.getenv(),
    private val clock: HubClock = HubClock.System,
    private val input: java.io.InputStream = System.`in`,
) {
    /** Runs one command; returns the exit code, or [SERVING] once the server has stopped normally. */
    fun run(args: List<String>): Int {
        val command = args.firstOrNull()
        val options = try {
            Options.parse(args.drop(1))
        } catch (e: IllegalArgumentException) {
            err.println(e.message)
            return USAGE_ERROR
        }
        return when (command) {
            "serve" -> serve(options)

            "join" -> join(options)

            "config" -> config(options)

            "status" -> status(options)

            "version", "--version" -> OK.also { out.println("goa-hub ${VERSION ?: "(development build)"}") }

            null, "help", "--help", "-h" -> usage(out).let { if (command == null) USAGE_ERROR else OK }

            else -> {
                err.println("Unknown command: $command")
                usage(err)
                USAGE_ERROR
            }
        }
    }

    private fun paths(options: Options) = HubPaths.resolve(options.value("data"), env)

    private fun serve(options: Options): Int {
        val paths = paths(options)
        val host = options.value("host") ?: DEFAULT_HOST
        val port = options.value("port")?.toIntOrNull()?.takeIf { it in 1..MAX_PORT }
            ?: if (options.value("port") == null) DEFAULT_PORT else return usageError("--port must be 1–65535")
        val hub = Hub(paths, clock)
        val group = hub.membership.group()
        out.println("goa-hub: data in ${paths.root}")
        out.println(
            if (group == null) {
                "goa-hub: no group yet — run `goa-hub join \"<invite>\"` (every page says so until then)"
            } else {
                "goa-hub: group '${group.name}'"
            },
        )
        out.println("goa-hub: dates in ${zoneLine(hub.config.get())}")
        if (hub.config.get().publicUrl == null) {
            out.println(
                "goa-hub: no public URL — set one with `goa-hub config --public-url https://…` before publishing",
            )
        }
        embeddedServer(CIO, port = port, host = host) { hubModule(hub) }.start(wait = true)
        return SERVING
    }

    private fun join(options: Options): Int {
        val text = options.positional.joinToString(" ").takeUnless { it.isBlank() || it == "-" } ?: readInvite()
        if (text.isBlank()) return usageError("join needs the invite text (as an argument, or on standard input)")
        val invite = GroupInvite.parseInvite(text).getOrElse {
            err.println(it.message)
            return FAILED
        }
        val paths = paths(options)
        Files.createDirectories(paths.root)
        val membership = MembershipStore(paths.group)
        when (val result = membership.join(invite.group, clock.nowMillis())) {
            is MembershipStore.JoinResult.OtherGroup -> {
                err.println(
                    "This hub already belongs to '${result.current.name}'. One group per hub: use another data " +
                        "directory (--data) for another group.",
                )
                return FAILED
            }

            is MembershipStore.JoinResult.Joined -> {
                val verb = if (result.rejoined) "Rejoined" else "Joined"
                out.println("$verb '${result.group.name}' (${result.group.groupId}).")
            }
        }
        invite.hubUrl?.let { url ->
            val config = HubConfigStore(paths.config)
            if (config.get().publicUrl == null) {
                config.update { it.copy(publicUrl = url) }
                out.println("Public URL set to $url (from the invite).")
            }
        }
        out.println("Restart the hub service if it is running.")
        return OK
    }

    /**
     * The invite from standard input: `goa-hub join` / `goa-hub join -`. On Windows, `goa-hub.bat`
     * runs through cmd.exe, which splits an unquoted invite at every `&`; piping it in avoids that
     * (`Get-Content invite.txt | goa-hub.bat join`). Reads until a line holds an invite, or the end.
     */
    private fun readInvite(): String {
        out.println("Paste the invite (or the whole invite message), then press Enter:")
        val lines = ArrayList<String>()
        val reader = input.bufferedReader()
        do {
            val line = reader.readLine()?.also { lines += it }
        } while (line != null && "${GroupInvite.SCHEME}:" !in line)
        return lines.joinToString(" ")
    }

    private fun config(options: Options): Int {
        val paths = paths(options)
        Files.createDirectories(paths.root)
        val store = HubConfigStore(paths.config)
        val url = options.value("public-url")?.let {
            HubConfig.normalizeUrl(it)
                ?: return usageError("Not an https origin: $it")
        }
        val certs = options.values("android-cert-sha256").flatMap { it.split(',') }.filter { it.isNotBlank() }.map {
            HubConfig.normalizeFingerprint(it) ?: return usageError("Not a SHA-256 fingerprint: $it")
        }
        val zone = options.value("time-zone")?.let {
            if (it.trim().equals(SYSTEM_ZONE, ignoreCase = true)) {
                ""
            } else {
                HubConfig.normalizeZone(it) ?: return usageError("Not a time zone: $it (try Europe/London or UTC)")
            }
        }
        val config = if (url != null || certs.isNotEmpty() || zone != null) {
            store.update { current ->
                current.copy(
                    publicUrl = url ?: current.publicUrl,
                    androidCertSha256 = certs.ifEmpty { current.androidCertSha256 },
                    timeZone = if (zone == null) current.timeZone else zone.ifEmpty { null },
                )
            }
        } else {
            store.get()
        }
        out.println("Public URL: ${config.publicUrl ?: "(not set)"}")
        out.println(
            "Android certificate SHA-256: ${config.androidCertSha256.joinToString(", ").ifEmpty {
                "(not set)"
            }}",
        )
        out.println("Time zone: ${zoneLine(config)}")
        if (zone != null) out.println("Restart the hub service if it is running.")
        return OK
    }

    /** "Europe/London" or "Europe/Berlin (this machine's; set the group's with --time-zone)". */
    private fun zoneLine(config: HubConfig): String =
        config.zone()?.id ?: "${ZoneId.systemDefault().id} (this machine's; set the group's with --time-zone)"

    private fun status(options: Options): Int {
        val paths = paths(options)
        if (!Files.isDirectory(paths.root)) {
            out.println("No hub data in ${paths.root}.")
            return FAILED
        }
        // Read-only: nothing here creates or sweeps files (status may run as another user).
        val group = MembershipStore(paths.group).group()
        val data = GroupData(
            AtomicFiles.readOrNull(paths.data)
                ?.let { SyncJson.decodeFromString(SyncDocument.serializer(), it.decodeToString()) }
                ?: SyncDocument.EMPTY,
        )
        val doc = data.document
        val sync = SyncStateStore(paths.syncState).read()
        val devices = Devices(paths.devices).list()
        out.println("Data:      ${paths.root}")
        out.println("Group:     ${group?.let { "${it.name} (${it.groupId})" } ?: "not joined"}")
        val config = HubConfigStore(paths.config).get()
        zone = config.zone() ?: ZoneId.systemDefault()
        out.println("Public URL: ${config.publicUrl ?: "(not set)"}")
        out.println("Time zone: ${zoneLine(config)}")
        out.println(
            "Players:   ${data.players().size} active, ${data.players(includeArchived = true).size} in all",
        )
        out.println(
            "Matches:   ${doc.matches.count {
                it.status == MatchStatus.COMPLETED
            }} completed, ${doc.matches.size} in all; " +
                "drafts: ${doc.drafts.size}",
        )
        out.println("Last sync: ${sync.lastSyncAt?.let { format(it) } ?: "never"}")
        sync.clients.take(STATUS_LIST).forEach {
            out.println("  ${it.name}: ${format(it.lastSyncAt)} (${it.syncs} syncs)")
        }
        out.println("Devices:   ${devices.size} signed in")
        devices.take(STATUS_LIST).forEach { out.println("  ${it.name}: last seen ${format(it.lastSeenAt)}") }
        val apk = if (Files.isRegularFile(
                paths.apk,
            )
        ) {
            "${paths.apk} (${Files.size(paths.apk)} bytes)"
        } else {
            "none uploaded"
        }
        out.println("App:       $apk")
        return OK
    }

    private var zone: ZoneId = ZoneId.systemDefault()

    private fun format(millis: Long): String = TIME.withZone(zone).format(Instant.ofEpochMilli(millis))

    private fun usageError(message: String): Int {
        err.println(message)
        return USAGE_ERROR
    }

    private fun usage(stream: PrintStream) {
        stream.println(
            """
            |Usage: goa-hub <command> [options]
            |
            |  serve  [--data DIR] [--host 127.0.0.1] [--port 8080]   run the hub
            |  join   [--data DIR] "<invite text>"                   join the group (once; no text: read stdin)
            |  config [--data DIR] [--public-url URL] [--android-cert-sha256 FP] [--time-zone ZONE|system]
            |  status [--data DIR]                                   group, data, devices, last sync
            |  version                                               the hub's version
            |
            |The data directory is --data, else ${'$'}${HubPaths.ENV}, else ${HubPaths.DEFAULT}.
            """.trimMargin(),
        )
    }

    /** `--name value` / `--name=value` options and positional arguments. */
    internal class Options(private val named: Map<String, List<String>>, val positional: List<String>) {
        fun value(name: String): String? = named[name]?.lastOrNull()

        fun values(name: String): List<String> = named[name].orEmpty()

        companion object {
            private val KNOWN = setOf("data", "host", "port", "public-url", "android-cert-sha256", "time-zone")

            fun parse(args: List<String>): Options {
                val named = LinkedHashMap<String, MutableList<String>>()
                val positional = ArrayList<String>()
                var i = 0
                while (i < args.size) {
                    val arg = args[i]
                    if (arg.startsWith("--")) {
                        val name = arg.removePrefix("--").substringBefore('=')
                        require(name in KNOWN) { "Unknown option: --$name" }
                        val value = if ('=' in arg) {
                            arg.substringAfter('=')
                        } else {
                            args.getOrNull(++i) ?: throw IllegalArgumentException("--$name needs a value")
                        }
                        named.getOrPut(name) { ArrayList() } += value
                    } else {
                        positional += arg
                    }
                    i++
                }
                return Options(named, positional)
            }
        }
    }

    companion object {
        const val OK = 0
        const val FAILED = 1
        const val USAGE_ERROR = 2
        const val SERVING = -1
        const val DEFAULT_HOST = "127.0.0.1"
        const val DEFAULT_PORT = 8080
        private const val MAX_PORT = 65_535
        private const val STATUS_LIST = 10
        private const val SYSTEM_ZONE = "system"

        /** The jar's Implementation-Version (null when run from the IDE or tests). */
        val VERSION: String? = HubCli::class.java.`package`?.implementationVersion
        private val TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)
    }
}
