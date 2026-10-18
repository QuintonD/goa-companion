package dev.quinton.goa.hub.store

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.domain.sync.SyncDocument
import dev.quinton.goa.core.domain.sync.SyncJson
import dev.quinton.goa.hub.FakeClock
import dev.quinton.goa.hub.player
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GroupStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val clock = FakeClock(start = 1_760_000_000_000L) // 2025-10-09 UTC
    private val dir: Path get() = tmp.root.toPath()

    private fun store() = GroupStore(dir.resolve("data.json"), dir.resolve("backups"), clock)

    @Test
    fun `data survives a restart and is stored canonically`() = runBlocking<Unit> {
        store().upsertPlayer(player("b", "Bea"))
        store().upsertPlayer(player("a", "Al"))
        val reloaded = store().read()
        assertThat(reloaded.players.map { it.id }).containsExactly("a", "b").inOrder()
        val onDisk = SyncJson.decodeFromString(SyncDocument.serializer(), Files.readString(dir.resolve("data.json")))
        assertThat(onDisk).isEqualTo(reloaded)
    }

    @Test
    fun `a crash mid-write leaves the old data and its temp file is swept`() = runBlocking<Unit> {
        store().upsertPlayer(player("a", "Al"))
        // A crash between writing the temp file and the rename: a half-written temp file is left.
        val temp = dir.resolve(".data.json.goa-0000.tmp")
        Files.writeString(temp, """{"format":"goa-sync","players":[{"id":"half""")
        val reopened = store()
        assertThat(reopened.read().players.map { it.id }).containsExactly("a")
        assertThat(Files.exists(temp)).isFalse()
        assertThat(Files.list(dir).use { s -> s.filter { AtomicFiles.isTemp(it) }.count() }).isEqualTo(0)
    }

    @Test
    fun `each day keeps a copy and copies older than 30 days are pruned`() = runBlocking<Unit> {
        val s = store()
        s.upsertPlayer(player("a", "Al"))
        val day = 24 * 3600_000L
        clock.advance(day)
        s.upsertPlayer(player("b", "Bea"))
        val backups = dir.resolve("backups")
        val names = Files.list(backups).use { it.map { p -> p.fileName.toString() }.sorted().toList() }
        assertThat(names).containsExactly("data-2025-10-09.json", "data-2025-10-10.json").inOrder()
        // The copy of a day is that day's last state.
        val first = SyncJson.decodeFromString(SyncDocument.serializer(), Files.readString(backups.resolve(names[0])))
        assertThat(first.players.map { it.id }).containsExactly("a")

        Files.writeString(backups.resolve("data-2025-08-01.json"), "{}")
        Files.writeString(backups.resolve("notes.txt"), "kept")
        clock.advance(30 * day) // 2025-11-09: the window is 2025-10-11 … 2025-11-09
        s.upsertPlayer(player("c", "Cid"))
        val left = Files.list(backups).use { it.map { p -> p.fileName.toString() }.sorted().toList() }
        assertThat(left).containsExactly("data-2025-11-09.json", "notes.txt")
    }

    @Test
    fun `concurrent writes are serialised and none is lost`() = runBlocking<Unit> {
        val s = store()
        withContext(Dispatchers.Default) {
            (1..40).map { i -> async { s.upsertPlayer(player("p$i", "Player $i")) } }.awaitAll()
        }
        assertThat(s.read().players).hasSize(40)
        assertThat(store().read().players).hasSize(40)
    }

    @Test
    fun `an unchanged update writes nothing`() = runBlocking<Unit> {
        val s = store()
        s.update { it }
        assertThat(Files.exists(dir.resolve("data.json"))).isFalse()
    }
}
