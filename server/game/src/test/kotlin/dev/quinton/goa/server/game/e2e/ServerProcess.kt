package dev.quinton.goa.server.game.e2e

import dev.quinton.goa.server.game.room.TestRules
import java.io.File
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * `main` itself, in a process of its own, as the container runs it: the only way to stop a server hard is to
 * kill it, and the only way to measure its heap is to give it one of its own. Same JVM flags as compose.yaml.
 */
internal class ServerProcess(
    dir: Path = Files.createTempDirectory("goa-e2e-process"),
    private val env: Map<String, String> = emptyMap(),
    private val heap: String = "512m",
) : Host(dir) {
    private var process: Process? = null
    val output: MutableList<String> = CopyOnWriteArrayList()

    init {
        port = ServerSocket(0).use { it.localPort }
    }

    val pid: Long get() = checkNotNull(process).pid()

    val cpuMillis: Long
        get() = checkNotNull(process).toHandle().info().totalCpuDuration().map { it.toMillis() }.orElse(0L)

    /** Starts it and waits until it answers; returns how long that took, restoring every running game. */
    fun startProcess(): Long {
        check(process == null) { "Already running" }
        val began = System.nanoTime()
        val builder = ProcessBuilder(
            javaBin("java"),
            "-Xmx$heap",
            "-XX:+ExitOnOutOfMemoryError",
            "-XX:-UsePerfData",
            "-Dorg.sqlite.tmpdir=${System.getProperty("org.sqlite.tmpdir")}",
            "-cp",
            System.getProperty("java.class.path"),
            "dev.quinton.goa.server.game.MainKt",
        ).redirectErrorStream(true)
        builder.environment() += mapOf(
            "GOA_GAME_DATA" to dir.toString(),
            "GOA_GAME_ASSETS" to TestRules.repoFile("app/src/main/assets").toAbsolutePath().toString(),
            "GOA_GAME_PORT" to port.toString(),
            "GOA_GAME_RULES_BUILD" to "e2e",
            "GOA_GAME_RATE_PER_CLIENT" to ROOMY,
            "GOA_GAME_RATE_GLOBAL" to ROOMY,
            "GOA_GAME_ACCOUNTS_PER_CLIENT" to ROOMY,
            "GOA_GAME_ACCOUNTS_GLOBAL" to ROOMY,
            "GOA_GAME_SOCKET_FRAMES" to ROOMY,
        ) + env
        val started = builder.start()
        process = started
        thread(isDaemon = true, name = "server-output") {
            started.inputStream.bufferedReader().forEachLine { output += it }
        }
        while (!answers()) {
            check(started.isAlive) { "The server died:\n" + output.takeLast(SHOWN_LINES).joinToString("\n") }
            Thread.sleep(POLL_MILLIS)
        }
        return (System.nanoTime() - began) / NANOS_PER_MILLI
    }

    private fun answers(): Boolean = try {
        val url = URI("http://127.0.0.1:$port/v1/health").toURL()
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = POLL_MILLIS.toInt()
        connection.responseCode == HttpURLConnection.HTTP_OK
    } catch (_: java.io.IOException) {
        false
    }

    /** `kill -9`: nothing runs after this, not the shutdown hook, not SQLite's close. */
    fun kill() {
        val running = checkNotNull(process)
        running.destroyForcibly()
        running.waitFor(WAIT_SECONDS, TimeUnit.SECONDS)
        process = null
    }

    /** Full collection, then what is live: `jcmd GC.heap_info` after `GC.run`. */
    fun heapAfterGc(): String {
        jcmd("GC.run")
        return jcmd("GC.heap_info")
    }

    private fun jcmd(command: String): String {
        val tool = ProcessBuilder(javaBin("jcmd"), pid.toString(), command).redirectErrorStream(true).start()
        val text = tool.inputStream.bufferedReader().readText()
        tool.waitFor(WAIT_SECONDS, TimeUnit.SECONDS)
        return text
    }

    private fun javaBin(name: String) = File(System.getProperty("java.home"), "bin/$name").path

    override fun close() {
        closePeople()
        process?.destroyForcibly()?.waitFor(WAIT_SECONDS, TimeUnit.SECONDS)
        dir.toFile().deleteRecursively()
    }

    private companion object {
        const val ROOMY = "1000000"
        const val POLL_MILLIS = 100L
        const val WAIT_SECONDS = 30L
        const val NANOS_PER_MILLI = 1_000_000L
        const val SHOWN_LINES = 30
    }
}
