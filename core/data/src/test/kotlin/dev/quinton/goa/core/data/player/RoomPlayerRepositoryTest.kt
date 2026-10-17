package dev.quinton.goa.core.data.player

import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.database.GoaDatabase
import dev.quinton.goa.core.model.player.Player
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** One player per name: quick-adding a known name must not split their ratings across two ids. */
@RunWith(RobolectricTestRunner::class)
class RoomPlayerRepositoryTest {
    private val db = GoaDatabase.inMemory(ApplicationProvider.getApplicationContext(), AndroidSQLiteDriver())
    private val repo = RoomPlayerRepository(db.playerDao())

    private val ann = Player("ann", "Ann", 0xFF3FC1B0, avatarId = 1, createdAt = 1)
    private val bob = Player("bob", "Bob", 0xFFE26459, avatarId = 2, createdAt = 2, archived = true)

    @After
    fun tearDown() = db.close()

    @Test
    fun `find by name ignores case and surrounding spaces and sees archived players`() = runTest {
        repo.upsert(ann)
        repo.upsert(bob)
        assertThat(repo.findByName("  aNN ")).isEqualTo(ann)
        assertThat(repo.findByName("BOB")).isEqualTo(bob)
        assertThat(repo.findByName("Cid")).isNull()
    }

    @Test
    fun `ensure player reuses an existing player with the same name`() = runTest {
        repo.upsert(ann)
        val result = repo.ensurePlayer(Player("new-id", " ann  ", 0xFF000000, 5, createdAt = 9))
        assertThat(result).isEqualTo(ann)
        assertThat(repo.observePlayers(includeArchived = true).first()).containsExactly(ann)
    }

    @Test
    fun `ensure player unarchives an archived player instead of duplicating them`() = runTest {
        repo.upsert(bob)
        val result = repo.ensurePlayer(Player("new-id", "bob", 0xFF000000, 5, createdAt = 9))
        assertThat(result).isEqualTo(bob.copy(archived = false))
        assertThat(repo.observePlayers().first()).containsExactly(bob.copy(archived = false))
    }

    @Test
    fun `ensure player inserts a new name trimmed`() = runTest {
        repo.upsert(ann)
        val result = repo.ensurePlayer(Player("cid", "  Cid ", 0xFF4F8FE0, 3, createdAt = 3))
        assertThat(result).isEqualTo(Player("cid", "Cid", 0xFF4F8FE0, 3, createdAt = 3))
        assertThat(repo.observePlayers().first().map { it.id }).containsExactly("ann", "cid")
    }
}
