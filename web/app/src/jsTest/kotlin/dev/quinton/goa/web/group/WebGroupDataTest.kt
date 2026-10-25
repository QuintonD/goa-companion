package dev.quinton.goa.web.group

import dev.quinton.goa.core.data.EditClock
import dev.quinton.goa.core.data.PlayerMergeResult
import dev.quinton.goa.core.domain.rating.EloCalculator
import dev.quinton.goa.core.domain.rating.EloConfig
import dev.quinton.goa.core.domain.sync.GroupCredentials
import dev.quinton.goa.core.domain.sync.SyncFileException
import dev.quinton.goa.core.domain.sync.SyncFileProblem
import dev.quinton.goa.core.domain.sync.SyncMerge
import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.core.model.match.Participant
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.core.model.sync.Deletion
import dev.quinton.goa.core.model.sync.DeletionKind
import dev.quinton.goa.core.model.trivia.TriviaRound
import dev.quinton.goa.core.model.trivia.TriviaRoundKind
import dev.quinton.goa.web.storage.KeyValueStore
import dev.quinton.goa.web.storage.MemoryKeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

/**
 * The web's repositories keep the phone's rules (core:data `RoomPlayerRepositoryTest`,
 * `RoomTriviaRoundRepositoryTest`, `GroupSyncDataTest`, ported): stamps that never go back,
 * tombstones that beat older copies and lose to later edits, merges resolved on read and stored as
 * logged, and browsers that exchange documents end with the same data and the same ratings.
 */
class WebGroupDataTest {
    /** One browser: its stored data and its clock. */
    private class Browser(val storage: KeyValueStore = MemoryKeyValueStore()) {
        var now = 1_000L
        private val clock = EditClock { now }
        val docs = GroupDocumentStore(storage)
        val players = WebPlayerRepository(docs, clock)
        val matches = WebMatchRepository(docs, clock)
        val rounds = WebTriviaRoundRepository(docs, clock)
        val drafts = WebDraftRecordRepository(docs, clock)
        val elo = WebEloConfigRepository(docs, clock)
        val sync = WebSyncRepository(docs, clock)

        suspend fun ratings() = EloCalculator.replay(matches.observeMatches().first(), elo.config.first())
    }

    private val group = GroupCredentials.create("Friday nights")

    private fun player(id: String, name: String = id.replaceFirstChar(Char::uppercase), createdAt: Long = 1) =
        Player(id, name, 0xFF3FC1B0, 0, createdAt = createdAt)

    private fun match(id: String, red: List<String>, blue: List<String>, playedAt: Long = 10, winner: Team = Team.RED) =
        Match(
            id = id,
            createdAt = playedAt,
            playedAt = playedAt,
            status = MatchStatus.COMPLETED,
            mode = GameMode.QUICK,
            winner = winner,
            participants = red.map { Participant(it, null, Team.RED) } + blue.map { Participant(it, null, Team.BLUE) },
        )

    private fun round(id: String, playerId: String, correct: Int = 15, playedAt: Long = 100) = TriviaRound(
        id = id,
        playerId = playerId,
        kind = TriviaRoundKind.CHALLENGE,
        playedAt = playedAt,
        length = 20,
        questions = 20,
        correct = correct,
        bestStreak = 6,
        answerMillis = 100_000,
        heroCount = 32,
        catalogVersion = 7,
    )

    /** What a sync does between two browsers: each merges the other's document. */
    private suspend fun exchange(a: Browser, b: Browser) {
        b.sync.apply(a.sync.export())
        a.sync.apply(b.sync.export())
    }

    @Test
    fun findByNameIgnoresCaseAndSpacesAndEnsurePlayerNeverDuplicates() = runTest {
        val web = Browser()
        web.players.upsert(player("ann", "Ann"))
        web.players.upsert(player("bob", "Bob").copy(archived = true))
        assertEquals("ann", web.players.findByName("  aNN ")?.id)
        assertEquals("bob", web.players.findByName("BOB")?.id)
        assertNull(web.players.findByName("Cid"))

        assertEquals("ann", web.players.ensurePlayer(player("new", " ann ")).id)
        val bob = web.players.ensurePlayer(player("new-2", "bob"))
        assertEquals("bob", bob.id)
        assertEquals(listOf("ann", "bob"), web.players.observePlayers().first().map { it.id })
        val cid = web.players.ensurePlayer(player("cid", "  Cid "))
        assertEquals("Cid", cid.name)
        assertEquals(listOf("Ann", "Bob", "Cid"), web.players.observePlayers().first().map { it.name })
    }

    @Test
    fun playersListLikeSqliteNocase() = runTest {
        val web = Browser()
        listOf("bob" to "bob", "ann" to "Ann", "zed" to "Zed", "eve" to "éve", "al" to "al").forEach { (id, name) ->
            web.players.upsert(player(id, name))
        }
        // NOCASE folds ASCII only: "éve" sorts after every ASCII letter.
        assertEquals(listOf("al", "Ann", "bob", "Zed", "éve"), web.players.observePlayers().first().map { it.name })
    }

    @Test
    fun everyEditIsStampedLaterThanTheLastEvenWhenTheClockGoesBack() = runTest {
        val web = Browser()
        web.players.upsert(player("ann"))
        assertEquals(1_000, web.players.observePlayer("ann").first()?.updatedAt)
        web.now = 500
        web.players.upsert(player("ann", "Annie"))
        assertEquals(1_001, web.players.observePlayer("ann").first()?.updatedAt)
        web.now = 2_000
        web.players.archive("ann", true)
        val archived = web.players.observePlayer("ann").first()
        assertEquals(true, archived?.archived)
        assertEquals(2_000, archived?.updatedAt)
    }

    @Test
    fun aMergedDuplicateDisappearsAndItsMatchesCountForTheOtherPlayer() = runTest {
        val web = Browser()
        web.players.upsert(player("ann", "Ann"))
        web.players.upsert(player("ann-2", "Ann", createdAt = 5))
        web.players.upsert(player("bob"))
        web.matches.upsert(match("m1", listOf("ann-2"), listOf("bob")))

        assertEquals(PlayerMergeResult.Merged, web.players.merge("ann-2", "ann"))

        assertEquals(listOf("ann", "bob"), web.players.observePlayers(includeArchived = true).first().map { it.id })
        assertEquals("ann", web.players.observePlayer("ann-2").first()?.id)
        assertEquals(setOf("ann", "bob"), web.matches.observeMatch("m1").first()?.playerIds?.toSet())
        // Stored as logged.
        assertTrue("ann-2" in web.sync.export().matches.single().playerIds)
    }

    @Test
    fun aMergeIsRefusedWhenTheTwoPlayedTogether() = runTest {
        val web = Browser()
        web.players.upsert(player("ann", "Ann"))
        web.players.upsert(player("ann-2", "Ann B"))
        web.matches.upsert(match("rivals", listOf("ann"), listOf("ann-2")))
        val result = web.players.merge("ann-2", "ann")
        assertIs<PlayerMergeResult.PlayedTogether>(result)
        assertEquals(listOf("rivals"), result.matches.map { it.id })
        assertEquals(PlayerMergeResult.NotPossible, web.players.merge("ann", "ann"))
        assertEquals(PlayerMergeResult.NotPossible, web.players.merge("ann", "nobody"))
    }

    @Test
    fun theMatchLogIsNewestFirstAndADeleteLeavesATombstoneThatUndoBeats() = runTest {
        val web = Browser()
        web.players.upsert(player("ann"))
        web.players.upsert(player("bob"))
        web.matches.upsert(match("old", listOf("ann"), listOf("bob"), playedAt = 10))
        web.matches.upsert(match("new", listOf("ann"), listOf("bob"), playedAt = 20))
        assertEquals(listOf("new", "old"), web.matches.observeMatches().first().map { it.id })

        val logged = web.matches.observeMatch("old").first()!!
        web.now = 3_000
        web.matches.delete("old")
        val tombstone = web.sync.export().deletions.single()
        assertEquals(Deletion(DeletionKind.MATCH, "old", 3_000), tombstone)
        assertEquals(listOf("new"), web.matches.observeMatches().first().map { it.id })

        web.now = 2_000 // clock went back: the undo still beats the tombstone
        web.matches.upsert(logged)
        val restored = web.matches.observeMatch("old").first()!!
        assertEquals(3_001, restored.updatedAt)

        // A browser that saw the delete but not the undo gets the match back.
        val other = Browser()
        other.sync.apply(
            SyncMerge.normalize(web.sync.export().copy(matches = listOf(logged), deletions = listOf(tombstone))),
        )
        assertTrue(other.matches.observeMatches().first().isEmpty())
        other.sync.apply(web.sync.export())
        assertEquals(restored, other.matches.observeMatch("old").first())
    }

    @Test
    fun aGameNightOnThreeBrowsersEndsWithTheSameDataAndRatings() = runTest {
        val ann = Browser()
        val bob = Browser()
        val cid = Browser()
        listOf(ann, bob, cid).forEach { b ->
            listOf("ann", "bob", "cid", "dee").forEach { b.players.upsert(player(it)) }
        }
        ann.matches.upsert(match("a1", listOf("ann", "bob"), listOf("cid", "dee"), playedAt = 10))
        bob.now = 1_200
        bob.matches.upsert(match("b1", listOf("ann", "cid"), listOf("bob", "dee"), playedAt = 20, winner = Team.BLUE))
        cid.now = 1_300
        cid.matches.upsert(match("c1", listOf("bob", "cid"), listOf("ann", "dee"), playedAt = 30))
        cid.elo.update { EloConfig(kEstablished = 25.0) }

        exchange(ann, bob)
        exchange(bob, cid)
        exchange(ann, bob)

        val expected = ann.sync.export()
        assertEquals(expected, bob.sync.export())
        assertEquals(expected, cid.sync.export())
        assertEquals(listOf("a1", "b1", "c1"), expected.matches.map { it.id })
        assertEquals(EloConfig(kEstablished = 25.0), ann.elo.config.first())
        assertEquals(ann.ratings(), bob.ratings())
        assertEquals(ann.ratings(), cid.ratings())
    }

    @Test
    fun triviaRoundsSyncAndARemovedOneGoesEverywhere() = runTest {
        val ann = Browser()
        val bob = Browser()
        ann.rounds.add(round("a-r1", "ann", correct = 18))
        bob.now = 1_100
        bob.rounds.add(round("b-r1", "bob", correct = 12))
        exchange(ann, bob)
        assertEquals(listOf("a-r1", "b-r1"), bob.rounds.observeRounds().first().map { it.id })
        bob.now = 5_000
        bob.rounds.delete("b-r1")
        exchange(bob, ann)
        listOf(ann, bob).forEach { b ->
            assertEquals(listOf("a-r1"), b.rounds.observeRounds().first().map { it.id })
            assertEquals(
                listOf(DeletionKind.TRIVIA_ROUND to "b-r1"),
                b.sync.export().deletions.map {
                    it.kind to it.id
                },
            )
        }
    }

    @Test
    fun aMergedDuplicatesRoundsReadAsOnePlayerButStayAsRecorded() = runTest {
        val web = Browser()
        web.players.upsert(player("dee-a", "Dee"))
        web.players.upsert(player("dee-b", "Dee B"))
        web.rounds.add(round("r1", "dee-a"))
        web.rounds.add(round("r2", "dee-b", playedAt = 200))
        web.players.merge("dee-b", "dee-a")
        assertEquals(listOf("dee-a", "dee-a"), web.rounds.observeRounds().first().map { it.playerId })
        assertEquals(listOf("dee-a", "dee-b"), web.sync.export().triviaRounds.map { it.playerId })
    }

    @Test
    fun untouchedConstantsNeverBeatTheGroupsAndAnEditBeatsThem() = runTest {
        val fresh = Browser()
        val group = Browser()
        group.now = 5_000
        group.elo.update { it.copy(kEstablished = 20.0) }
        fresh.sync.apply(group.sync.export())
        assertEquals(20.0, fresh.elo.config.first().kEstablished)
        fresh.now = 9_000
        fresh.elo.reset()
        group.sync.apply(fresh.sync.export())
        assertEquals(EloConfig.DEFAULT, group.elo.config.first())
    }

    @Test
    fun aSyncFileCarriesTheDataAndOnlyOpensForItsGroup() = runTest {
        val ann = Browser()
        ann.players.upsert(player("ann"))
        ann.players.upsert(player("bob"))
        ann.matches.upsert(match("m1", listOf("ann"), listOf("bob")))
        val file = ann.sync.exportFile(group)

        val bob = Browser()
        val result = bob.sync.importFile(file, group)
        assertEquals(1, result.changes.matchesAdded)
        assertEquals(2, result.changes.playersAdded)
        assertEquals(ann.sync.export(), bob.sync.export())

        val other = runCatching { bob.sync.importFile(file, GroupCredentials.create("Other")) }.exceptionOrNull()
        assertIs<SyncFileException>(other)
        assertEquals(SyncFileProblem.OTHER_GROUP, other.problem)
        assertTrue(bob.sync.importFile(file, group).changes.isEmpty)
    }

    @Test
    fun theDataSurvivesAReloadAndChangesAreAnnounced() = runTest {
        val storage = MemoryKeyValueStore()
        val first = Browser(storage)
        first.players.upsert(player("ann"))
        first.matches.upsert(match("m1", listOf("ann"), listOf("ann")))
        first.matches.delete("m1")

        val reloaded = Browser(storage)
        assertEquals(first.sync.export(), reloaded.sync.export())
        assertEquals(listOf("ann"), reloaded.players.observePlayers().first().map { it.id })
        assertEquals(1, reloaded.sync.export().deletions.size)
    }

    @Test
    fun unreadableStoredDataIsKeptAsideNotLost() = runTest {
        val storage = MemoryKeyValueStore()
        storage.put("document", "{not json")
        val web = Browser(storage)
        assertTrue(web.players.observePlayers().first().isEmpty())
        assertEquals("{not json", storage.get("document.unreadable"))
    }
}
