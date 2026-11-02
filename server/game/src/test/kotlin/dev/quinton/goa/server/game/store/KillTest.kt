package dev.quinton.goa.server.game.store

import com.google.common.truth.Truth.assertThat
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Test

/** A process killed outright, mid-write, loses nothing it had acknowledged. */
class KillTest {
    @Test
    fun everyAcknowledgedAnswerLockAndMatchSurvivesAKill(): Unit = runBlocking {
        val dir = Files.createTempDirectory("goa-kill-test")
        val java = File(System.getProperty("java.home"), "bin/java").path
        val writer = ProcessBuilder(
            java,
            "-Dorg.sqlite.tmpdir=${System.getProperty("org.sqlite.tmpdir")}",
            "-cp",
            System.getProperty("java.class.path"),
            KillWriter::class.java.name,
            dir.toString(),
        ).redirectErrorStream(true).start()

        var acknowledged = -1L
        try {
            writer.inputStream.bufferedReader().use { lines ->
                while (acknowledged < KILL_AFTER) {
                    val line = lines.readLine() ?: error("The writer stopped by itself after $acknowledged")
                    if (line.startsWith("ack ")) acknowledged = line.removePrefix("ack ").toLong()
                }
                writer.destroyForcibly()
            }
        } finally {
            writer.destroyForcibly()
            writer.waitFor(WAIT_SECONDS, TimeUnit.SECONDS)
        }

        val store = SqliteGameStore.open(dir)
        try {
            val game = store.runningGames().single { it.game.id == "running" }
            assertThat(game.answers.size.toLong()).isAtLeast(acknowledged + 1)
            assertThat(game.answers.map { it.seq }).isEqualTo(game.answers.indices.map { it.toLong() })
            assertThat(game.answers.take(acknowledged.toInt() + 1)).isEqualTo(
                (0..acknowledged).map { StoreFixtures.answer("running", it) },
            )
            assertThat(game.locks.size.toLong()).isAtLeast(acknowledged + 1)
            assertThat(store.matches().map { it.id }).containsExactly("kept")
        } finally {
            store.close()
            dir.toFile().deleteRecursively()
        }
    }

    private companion object {
        const val KILL_AFTER = 40L
        const val WAIT_SECONDS = 30L
    }
}

/** The process [KillTest] kills: says "ack n" only once answer and lock n have returned. */
object KillWriter {
    @JvmStatic
    fun main(args: Array<String>): Unit = runBlocking {
        val store = SqliteGameStore.open(Paths.get(args[0]))
        store.createGame(StoreFixtures.game("finished"), StoreFixtures.seats("finished"))
        store.finish("finished", GameResult(1, rated = true), StoreFixtures.match("kept", 1, 1))
        store.createGame(StoreFixtures.game("running"), StoreFixtures.seats("running"))
        var n = 0L
        while (true) {
            store.appendAnswer(StoreFixtures.answer("running", n))
            store.putLock(StoreFixtures.lock("running", "wasp", n.toInt()))
            println("ack $n")
            System.out.flush()
            n++
        }
    }
}
