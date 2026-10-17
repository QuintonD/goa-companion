package dev.quinton.goa.core.data.player

import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.data.EditClock
import dev.quinton.goa.core.data.PlayerMergeResult
import dev.quinton.goa.core.data.match.RoomMatchRepository
import dev.quinton.goa.core.database.GoaDatabase
import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.core.model.match.Participant
import dev.quinton.goa.core.model.match.Team
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
    private var now = 1_000L
    private val clock = EditClock { now }
    private val repo = RoomPlayerRepository(db, clock)
    private val matches = RoomMatchRepository(db, clock)

    private val ann = Player("ann", "Ann", 0xFF3FC1B0, avatarId = 1, createdAt = 1)
    private val bob = Player("bob", "Bob", 0xFFE26459, avatarId = 2, createdAt = 2, archived = true)

    /** The player as the test wrote it: `updatedAt` is the repository's business. */
    private fun Player?.plain() = this?.copy(updatedAt = createdAt)

    private fun List<Player>.plain() = map { it.copy(updatedAt = it.createdAt) }

    @After
    fun tearDown() = db.close()

    @Test
    fun `find by name ignores case and surrounding spaces and sees archived players`() = runTest {
        repo.upsert(ann)
        repo.upsert(bob)
        assertThat(repo.findByName("  aNN ").plain()).isEqualTo(ann)
        assertThat(repo.findByName("BOB").plain()).isEqualTo(bob)
        assertThat(repo.findByName("Cid")).isNull()
    }

    @Test
    fun `ensure player reuses an existing player with the same name`() = runTest {
        repo.upsert(ann)
        val result = repo.ensurePlayer(Player("new-id", " ann  ", 0xFF000000, 5, createdAt = 9))
        assertThat(result.plain()).isEqualTo(ann)
        assertThat(repo.observePlayers(includeArchived = true).first().plain()).containsExactly(ann)
    }

    @Test
    fun `ensure player unarchives an archived player instead of duplicating them`() = runTest {
        repo.upsert(bob)
        val result = repo.ensurePlayer(Player("new-id", "bob", 0xFF000000, 5, createdAt = 9))
        assertThat(result.plain()).isEqualTo(bob.copy(archived = false))
        assertThat(repo.observePlayers().first().plain()).containsExactly(bob.copy(archived = false))
    }

    @Test
    fun `ensure player inserts a new name trimmed`() = runTest {
        repo.upsert(ann)
        val result = repo.ensurePlayer(Player("cid", "  Cid ", 0xFF4F8FE0, 3, createdAt = 3))
        assertThat(result.plain()).isEqualTo(Player("cid", "Cid", 0xFF4F8FE0, 3, createdAt = 3))
        assertThat(repo.observePlayers().first().map { it.id }).containsExactly("ann", "cid")
    }

    @Test
    fun `every edit, archiving included, is stamped later than the last one even if the clock goes back`() = runTest {
        repo.upsert(ann)
        assertThat(repo.observePlayer("ann").first()!!.updatedAt).isEqualTo(1_000)
        now = 500 // the phone's clock was moved back
        repo.upsert(ann.copy(name = "Annie"))
        assertThat(repo.observePlayer("ann").first()!!.updatedAt).isEqualTo(1_001)
        now = 2_000
        repo.archive("ann", true)
        val archived = repo.observePlayer("ann").first()!!
        assertThat(archived.archived).isTrue()
        assertThat(archived.updatedAt).isEqualTo(2_000)
    }

    private fun match(id: String, red: List<String>, blue: List<String>) = Match(
        id = id,
        createdAt = 1,
        playedAt = 1,
        status = MatchStatus.COMPLETED,
        mode = GameMode.QUICK,
        winner = Team.RED,
        participants = red.map { Participant(it, null, Team.RED) } + blue.map { Participant(it, null, Team.BLUE) },
    )

    @Test
    fun `a merged duplicate disappears from lists and its matches count for the other player`() = runTest {
        val annOnBobsPhone = Player("ann-2", "Ann", 1, 0, createdAt = 5)
        repo.upsert(ann)
        repo.upsert(annOnBobsPhone)
        repo.upsert(bob.copy(archived = false))
        matches.upsert(match("m1", listOf("ann-2"), listOf("bob")))

        assertThat(repo.merge("ann-2", "ann")).isEqualTo(PlayerMergeResult.Merged)

        assertThat(repo.observePlayers(includeArchived = true).first().map { it.id }).containsExactly("ann", "bob")
        assertThat(repo.observePlayer("ann-2").first()!!.id).isEqualTo("ann")
        assertThat(repo.findByName("ann")!!.id).isEqualTo("ann")
        // Read resolved; stored as logged.
        assertThat(matches.observeMatch("m1").first()!!.playerIds).containsExactly("ann", "bob")
        assertThat(db.matchDao().getAll().single().participants.map { it.playerId }).contains("ann-2")
    }

    @Test
    fun `a merge is refused when the two played in the same match, and listed`() = runTest {
        repo.upsert(ann)
        repo.upsert(Player("ann-2", "Ann B", 1, 0, createdAt = 5))
        matches.upsert(match("rivals", listOf("ann"), listOf("ann-2")))
        val result = repo.merge("ann-2", "ann")
        assertThat(result).isInstanceOf(PlayerMergeResult.PlayedTogether::class.java)
        assertThat((result as PlayerMergeResult.PlayedTogether).matches.map { it.id }).containsExactly("rivals")
        assertThat(repo.observePlayers().first()).hasSize(2)
        assertThat(repo.merge("ann", "ann")).isEqualTo(PlayerMergeResult.NotPossible)
        assertThat(repo.merge("ann", "nobody")).isEqualTo(PlayerMergeResult.NotPossible)
    }
}
