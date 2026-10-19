package dev.quinton.goa.hub.store

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.domain.rating.EloConfig
import dev.quinton.goa.core.domain.sync.NamedPlayer
import dev.quinton.goa.core.domain.sync.PlayerMergeOutcome
import dev.quinton.goa.core.domain.sync.SyncDocument
import dev.quinton.goa.core.domain.sync.SyncMerge
import dev.quinton.goa.core.model.sync.Deletion
import dev.quinton.goa.core.model.sync.DeletionKind
import dev.quinton.goa.hub.FakeClock
import dev.quinton.goa.hub.match
import dev.quinton.goa.hub.player
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Web writes stamp like the phones' Room repositories (RoomPlayerRepositoryTest, GroupSyncDataTest):
 * an edit beats the copy it edited, a tombstone beats an older edit, a later edit resurrects.
 */
class SharedWritesTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val clock = FakeClock(start = 1_000)
    private val store by lazy {
        GroupStore(tmp.root.toPath().resolve("data.json"), tmp.root.toPath().resolve("backups"), clock)
    }

    private val ann = player("ann", "Ann", createdAt = 1)
    private val bob = player("bob", "Bob", createdAt = 2)

    @Test
    fun `every edit, archiving included, is stamped later than the last one even if the clock goes back`() = runTest {
        store.upsertPlayer(ann)
        assertThat(store.read().players.single().updatedAt).isEqualTo(1_000)
        clock.set(500) // the hub's clock went back
        store.upsertPlayer(ann.copy(name = "  Annie  "))
        val edited = store.read().players.single()
        assertThat(edited.updatedAt).isEqualTo(1_001)
        assertThat(edited.name).isEqualTo("Annie")
        clock.set(2_000)
        store.archivePlayer("ann", true)
        assertThat(store.read().players.single().archived).isTrue()
        assertThat(store.read().players.single().updatedAt).isEqualTo(2_000)
    }

    @Test
    fun `a blank name is refused and nothing is stored`() = runTest {
        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking { store.upsertPlayer(ann.copy(name = " ")) }
        }
        assertThat(store.read().players).isEmpty()
    }

    @Test
    fun `an edit beats the copy it edited, wherever that copy turns up later`() = runTest {
        // A phone logged the match at 5_000 with a clock ahead of the hub's.
        val phone =
            SyncDocument(
                players = listOf(ann, bob),
                matches = listOf(match("m1", listOf("ann"), listOf("bob"), updatedAt = 5_000)),
            )
        store.mergeIn(phone)
        clock.set(1_000)
        val edited = store.upsertMatch(store.read().matches.single().copy(notes = "edited on the web"))
        assertThat(edited.updatedAt).isEqualTo(5_001)
        // The phone syncs its old copy again: the web edit stays.
        val merged = store.mergeIn(phone)
        assertThat(merged.matches.single().notes).isEqualTo("edited on the web")
    }

    @Test
    fun `a delete leaves a tombstone that beats the copy it deleted and any older edit`() = runTest {
        val phone =
            SyncDocument(
                players = listOf(ann, bob),
                matches = listOf(match("m1", listOf("ann"), listOf("bob"), updatedAt = 5_000)),
            )
        store.mergeIn(phone)
        store.deleteMatch("m1")
        val doc = store.read()
        assertThat(doc.matches).isEmpty()
        assertThat(doc.deletions).containsExactly(Deletion(DeletionKind.MATCH, "m1", 5_001))
        // The phone still has its copy (and an older edit of it): it stays deleted.
        assertThat(store.mergeIn(phone).matches).isEmpty()
        val olderEdit = phone.copy(matches = listOf(phone.matches.single().copy(notes = "x", updatedAt = 5_001)))
        assertThat(store.mergeIn(olderEdit).matches).isEmpty()
        // And the phone, merging the hub's data, drops it too.
        assertThat(SyncMerge.merge(phone, store.read()).matches).isEmpty()
    }

    @Test
    fun `a later edit brings a deleted match back, on the hub and from a phone`() = runTest {
        store.mergeIn(SyncDocument(players = listOf(ann, bob)))
        store.upsertMatch(match("m1", listOf("ann"), listOf("bob")))
        store.deleteMatch("m1")
        val tombstone = store.read().deletions.single().deletedAt
        val back = store.upsertMatch(match("m1", listOf("ann"), listOf("bob"), notes = "back"))
        assertThat(back.updatedAt).isGreaterThan(tombstone)
        assertThat(store.read().matches.single().notes).isEqualTo("back")

        store.deleteMatch("m1")
        val second = store.read().deletions.single().deletedAt
        val phoneEdit = match("m1", listOf("ann"), listOf("bob"), updatedAt = second + 10, notes = "phone")
        assertThat(
            store.mergeIn(SyncDocument(players = listOf(ann, bob), matches = listOf(phoneEdit))).matches.single().notes,
        )
            .isEqualTo("phone")
    }

    @Test
    fun `deleting a missing match or draft changes nothing`() = runTest {
        store.deleteMatch("nope")
        store.deleteDraft("nope")
        assertThat(store.read()).isEqualTo(SyncDocument.EMPTY)
    }

    @Test
    fun `ensure player reuses, unarchives, or inserts trimmed - never a second player of one name`() = runTest {
        store.upsertPlayer(ann)
        assertThat(store.ensurePlayer(player("x", " ann ")).id).isEqualTo("ann")
        store.archivePlayer("ann", true)
        val back = store.ensurePlayer(player("y", "ANN"))
        assertThat(back.id).isEqualTo("ann")
        assertThat(back.archived).isFalse()
        val fresh = store.ensurePlayer(player("cid", "  Cid "))
        assertThat(fresh.name).isEqualTo("Cid")
        assertThat(store.read().players.map { it.id }).containsExactly("ann", "cid")
        assertThat(store.findPlayerByName(" CID")!!.id).isEqualTo("cid")
    }

    @Test
    fun `a merged duplicate disappears from lists and its matches read as the other player`() = runTest {
        val annOnBobsPhone = player("ann-2", "Ann", createdAt = 5)
        store.mergeIn(
            SyncDocument(
                players = listOf(ann, annOnBobsPhone, bob),
                matches = listOf(match("m1", listOf("ann-2"), listOf("bob"))),
            ),
        )
        assertThat(store.mergePlayers("ann-2", "ann")).isEqualTo(PlayerMergeOutcome.Merged)
        val data = store.data()
        assertThat(data.players(includeArchived = true).map { it.id }).containsExactly("ann", "bob")
        assertThat(data.player("ann-2")!!.id).isEqualTo("ann")
        assertThat(data.matches.single().playerIds).containsExactly("ann", "bob")
        // Stored as logged.
        assertThat(store.read().matches.single().participants.map { it.playerId }).contains("ann-2")
        assertThat(store.read().playerMerges.single().mergedAt).isEqualTo(1_000)
    }

    @Test
    fun `a merge is refused when the two played in the same match`() = runTest {
        store.mergeIn(
            SyncDocument(
                players = listOf(ann, player("ann-2", "Ann B")),
                matches = listOf(match("rivals", listOf("ann"), listOf("ann-2"))),
            ),
        )
        val result = store.mergePlayers("ann-2", "ann")
        assertThat((result as PlayerMergeOutcome.PlayedTogether).matches.map { it.id }).containsExactly("rivals")
        assertThat(store.read().playerMerges).isEmpty()
        assertThat(store.mergePlayers("ann", "ann")).isEqualTo(PlayerMergeOutcome.NotPossible)
        assertThat(store.mergePlayers("ann", "nobody")).isEqualTo(PlayerMergeOutcome.NotPossible)
    }

    @Test
    fun `rating constants are sanitized and stamped as one group-wide change`() = runTest {
        store.updateEloConfig { it.copy(kEstablished = 32.0, kProvisional = 500.0) }
        val first = store.read().eloConfig
        assertThat(first.config.kEstablished).isEqualTo(32.0)
        assertThat(first.config.kProvisional).isEqualTo(100.0) // clamped, as the phone does
        assertThat(first.updatedAt).isEqualTo(1_000)
        clock.set(10)
        store.resetEloConfig()
        assertThat(store.read().eloConfig.config).isEqualTo(EloConfig.DEFAULT)
        assertThat(store.read().eloConfig.updatedAt).isEqualTo(1_001)
    }

    @Test
    fun `two browsers adding one name at the same moment make one player`() = runTest {
        store.upsertPlayer(ann.copy(archived = true))
        val adds = (1..8).map { i ->
            async(kotlinx.coroutines.Dispatchers.Default) {
                store.upsertPlayerIfNameFree(player("sam-$i", if (i % 2 == 0) " sam " else "Sam", createdAt = 10L + i))
            }
        }.awaitAll()
        assertThat(adds.count { it is NamedPlayer.Saved }).isEqualTo(1)
        assertThat(store.read().players.count { it.name.equals("sam", ignoreCase = true) }).isEqualTo(1)
        // An archived player's name stays taken; renaming yourself to your own name is fine.
        assertThat(store.upsertPlayerIfNameFree(player("ann-2", "ANN"))).isInstanceOf(NamedPlayer.Taken::class.java)
        assertThat(store.upsertPlayerIfNameFree(ann.copy(name = "Ann "))).isInstanceOf(NamedPlayer.Saved::class.java)
    }

    @Test
    fun `a timer-style edit changes the stored match as logged, and undo never beats a newer copy`() = runTest {
        store.mergeIn(
            SyncDocument(
                players = listOf(ann, bob, player("ann-2", "Ann 2")),
                matches = listOf(match("m1", listOf("ann-2"), listOf("bob"), updatedAt = 5)),
                playerMerges = listOf(dev.quinton.goa.core.model.sync.PlayerMerge("ann-2", "ann", 6)),
            ),
        )
        val edited = store.editMatch("m1") { it.copy(durationMin = 47) }!!
        // The pilot it was logged with is kept (reads resolve it to Ann; the log isn't rewritten).
        assertThat(edited.participants.map { it.playerId }).containsExactly("ann-2", "bob").inOrder()
        assertThat(edited.updatedAt).isEqualTo(1_000)
        assertThat(store.editMatch("m1") { it.copy(durationMin = 47) }).isNull() // nothing changed
        assertThat(store.editMatch("nope") { it }).isNull()

        val deleted = store.deleteMatch("m1")!!
        assertThat(deleted).isEqualTo(edited)
        clock.set(2_000)
        assertThat(store.restoreMatch(deleted)).isTrue()
        val restored = store.read().matches.single()
        assertThat(restored.updatedAt).isGreaterThan(store.read().deletions.single().deletedAt)
        // Restoring again while it exists (say an edit synced in) writes nothing.
        assertThat(store.restoreMatch(deleted)).isFalse()
        assertThat(store.read().matches.single()).isEqualTo(restored)
    }

    @Test
    fun `a write that breaks a sync rule is refused and nothing is stored`() = runTest {
        store.upsertPlayer(ann)
        val before = store.read()
        assertThrows(InvalidGroupDataException::class.java) {
            kotlinx.coroutines.runBlocking { store.upsertMatch(match("m1", listOf("ann"), listOf("ghost"))) }
        }
        assertThat(store.read()).isEqualTo(before)
    }
}
