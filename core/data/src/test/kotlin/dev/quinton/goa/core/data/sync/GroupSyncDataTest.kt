package dev.quinton.goa.core.data.sync

import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.data.EditClock
import dev.quinton.goa.core.data.PlayerMergeResult
import dev.quinton.goa.core.data.match.RoomDraftRecordRepository
import dev.quinton.goa.core.data.match.RoomMatchRepository
import dev.quinton.goa.core.data.player.RoomPlayerRepository
import dev.quinton.goa.core.data.trivia.RoomTriviaRoundRepository
import dev.quinton.goa.core.data.rating.FakeEloConfigRepository
import dev.quinton.goa.core.database.GoaDatabase
import dev.quinton.goa.core.domain.rating.EloCalculator
import dev.quinton.goa.core.domain.rating.EloConfig
import dev.quinton.goa.core.domain.sync.GroupCredentials
import dev.quinton.goa.core.domain.sync.InMemorySyncNetwork
import dev.quinton.goa.core.domain.sync.SyncFileException
import dev.quinton.goa.core.domain.sync.SyncFileProblem
import dev.quinton.goa.core.domain.sync.SyncMerge
import dev.quinton.goa.core.domain.sync.protocol.ClientPhase
import dev.quinton.goa.core.domain.sync.protocol.HostPhase
import dev.quinton.goa.core.domain.sync.protocol.PeerStage
import dev.quinton.goa.core.domain.sync.protocol.SyncClient
import dev.quinton.goa.core.domain.sync.protocol.SyncDevice
import dev.quinton.goa.core.domain.sync.protocol.SyncHost
import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.core.model.match.Participant
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.core.model.sync.DeletionKind
import dev.quinton.goa.core.model.trivia.TriviaRound
import dev.quinton.goa.core.model.trivia.TriviaRoundKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Group sync with real Room databases: what a game night does to two or three phones. Every
 * phone must end with the same data and the same ratings, deletions must reach everyone, and a
 * duplicate person must become one.
 */
@RunWith(RobolectricTestRunner::class)
class GroupSyncDataTest {
    private val databases = mutableListOf<GoaDatabase>()
    private val group = GroupCredentials.create("Friday nights")

    @After
    fun tearDown() = databases.forEach { it.close() }

    private inner class Phone(val name: String) {
        val db = GoaDatabase.inMemory(ApplicationProvider.getApplicationContext(), AndroidSQLiteDriver())
            .also { databases += it }
        var now = 1_000L
        val clock = EditClock { now }
        val players = RoomPlayerRepository(db, clock)
        val matches = RoomMatchRepository(db, clock)
        val drafts = RoomDraftRecordRepository(db, clock)
        val rounds = RoomTriviaRoundRepository(db, clock)
        val elo = FakeEloConfigRepository(clock = { now })
        val sync = RoomSyncRepository(db, elo, clock)
        val device = SyncDevice("id-$name", name)

        suspend fun ratings() = EloCalculator.replay(matches.observeMatches().first(), elo.config.first())
    }

    private fun player(id: String, name: String = id.replaceFirstChar(Char::uppercase)) =
        Player(id, name, 1, 0, createdAt = 1)

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

    /**
     * One sync session: [host] hosts, the others join, everyone's data in, merged data out. Runs
     * in real time: Room works on its own threads, which virtual time can't see.
     */
    private suspend fun syncSession(host: Phone, vararg clients: Phone) = withContext(Dispatchers.Default) {
        val network = InMemorySyncNetwork()
        val hostSession = SyncHost(network.transport(host.name), group, host.device, host.sync)
        val clientSessions = clients.map { SyncClient(network.transport(it.name), group, it.device, it.sync) }
        val jobs = listOf(launch { hostSession.run() }) + clientSessions.map { launch { it.run() } }
        hostSession.state.first { s -> s.peers.size == clients.size && s.peers.all { it.stage == PeerStage.READY } }
        hostSession.syncNow()
        hostSession.state.first { it.phase == HostPhase.DONE }
        clientSessions.forEach { c ->
            assertThat(c.state.first { it.phase >= ClientPhase.DONE }.phase).isEqualTo(ClientPhase.DONE)
        }
        jobs.forEach { it.join() }
    }

    @Test
    fun aGameNightOnThreePhonesEndsWithTheSameMatchesAndRatingsEverywhere() = runTest {
        val ann = Phone("Ann")
        val bob = Phone("Bob")
        val cid = Phone("Cid")
        // Everyone knew everyone from an earlier sync.
        listOf(ann, bob, cid).forEach { phone ->
            listOf("ann", "bob", "cid", "dee").forEach { phone.players.upsert(player(it)) }
        }
        ann.matches.upsert(match("a1", listOf("ann", "bob"), listOf("cid", "dee"), playedAt = 10))
        bob.now = 1_200
        bob.matches.upsert(match("b1", listOf("ann", "cid"), listOf("bob", "dee"), playedAt = 20, winner = Team.BLUE))
        cid.now = 1_300
        cid.matches.upsert(match("c1", listOf("bob", "cid"), listOf("ann", "dee"), playedAt = 30))
        cid.elo.update { EloConfig(kEstablished = 25.0) }

        syncSession(ann, bob, cid)

        val expected = ann.sync.export()
        assertThat(bob.sync.export()).isEqualTo(expected)
        assertThat(cid.sync.export()).isEqualTo(expected)
        assertThat(expected.matches.map { it.id }).containsExactly("a1", "b1", "c1")
        assertThat(ann.elo.config.first()).isEqualTo(EloConfig(kEstablished = 25.0))
        assertThat(bob.ratings()).isEqualTo(ann.ratings())
        assertThat(cid.ratings()).isEqualTo(ann.ratings())
    }

    @Test
    fun aDeletionAndAnEditMadeAfterTheLastSyncReachTheOtherPhone() = runTest {
        val ann = Phone("Ann")
        val bob = Phone("Bob")
        listOf(ann, bob).forEach { phone -> listOf("ann", "bob").forEach { phone.players.upsert(player(it)) } }
        ann.matches.upsert(match("m1", listOf("ann"), listOf("bob")))
        ann.matches.upsert(match("m2", listOf("ann"), listOf("bob")))
        syncSession(ann, bob)
        assertThat(bob.matches.observeMatches().first().map { it.id }).containsExactly("m1", "m2")

        // Next week: Bob deletes m1, Ann fixes m2's winner.
        bob.now = 5_000
        bob.matches.delete("m1")
        ann.now = 5_100
        ann.matches.upsert(ann.matches.observeMatch("m2").first()!!.copy(winner = Team.BLUE))
        syncSession(bob, ann)

        listOf(ann, bob).forEach { phone ->
            val log = phone.matches.observeMatches().first()
            assertThat(log.map { it.id }).containsExactly("m2")
            assertThat(log.single().winner).isEqualTo(Team.BLUE)
        }
        assertThat(ann.db.deletionDao().getAll().map { it.id }).containsExactly("m1")
    }

    @Test
    fun theSameFriendCreatedOnTwoPhonesBecomesOnePlayerAfterAMerge() = runTest {
        val ann = Phone("Ann")
        val bob = Phone("Bob")
        // Both phones added "Dee" before they ever synced: two ids for one person.
        ann.players.upsert(player("ann"))
        ann.players.upsert(player("dee-a", "Dee"))
        ann.matches.upsert(match("a1", listOf("ann"), listOf("dee-a")))
        bob.players.upsert(player("bob"))
        bob.players.upsert(player("dee-b", "Dee"))
        bob.matches.upsert(match("b1", listOf("dee-b"), listOf("bob")))
        syncSession(ann, bob)
        assertThat(ann.players.observePlayers().first().count { it.name == "Dee" }).isEqualTo(2)

        bob.now = 9_000
        assertThat(bob.players.merge("dee-b", "dee-a")).isEqualTo(PlayerMergeResult.Merged)
        syncSession(bob, ann)

        listOf(ann, bob).forEach { phone ->
            assertThat(phone.players.observePlayers().first().map { it.id }).containsExactly("ann", "bob", "dee-a")
            val dee = phone.ratings().player("dee-a")
            assertThat(dee.games).isEqualTo(2)
            assertThat(phone.ratings().players.keys).doesNotContain("dee-b")
        }
        assertThat(ann.ratings()).isEqualTo(bob.ratings())
    }

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

    @Test
    fun triviaRoundsReachEveryPhoneAndARemovedOneGoesEverywhere() = runTest {
        val ann = Phone("Ann")
        val bob = Phone("Bob")
        val cid = Phone("Cid")
        listOf(ann, bob, cid).forEach { phone -> listOf("ann", "bob", "cid").forEach { phone.players.upsert(player(it)) } }
        ann.rounds.add(round("a-r1", "ann", correct = 18))
        bob.now = 1_100
        bob.rounds.add(round("b-r1", "bob", correct = 12))
        bob.rounds.add(round("b-r2", "cid", correct = 9))
        syncSession(ann, bob, cid)

        val expected = ann.sync.export()
        assertThat(expected.triviaRounds.map { it.id }).containsExactly("a-r1", "b-r1", "b-r2")
        listOf(bob, cid).forEach { assertThat(it.sync.export()).isEqualTo(expected) }
        assertThat(cid.rounds.observeRounds().first().map { it.id }).containsExactly("a-r1", "b-r1", "b-r2")

        // Cid removes Bob's round from the leaderboard; after the next syncs it is gone everywhere.
        cid.now = 5_000
        cid.rounds.delete("b-r2")
        syncSession(cid, ann)
        syncSession(ann, bob)
        listOf(ann, bob, cid).forEach { phone ->
            assertThat(phone.rounds.observeRounds().first().map { it.id }).containsExactly("a-r1", "b-r1")
            assertThat(phone.db.deletionDao().getAll().map { it.kind to it.id }).containsExactly("trivia_round" to "b-r2")
        }
    }

    @Test
    fun aMergedDuplicatesRoundsCountForOnePlayerOnEveryPhone() = runTest {
        val ann = Phone("Ann")
        val bob = Phone("Bob")
        ann.players.upsert(player("dee-a", "Dee"))
        ann.rounds.add(round("r1", "dee-a"))
        bob.players.upsert(player("dee-b", "Dee"))
        bob.rounds.add(round("r2", "dee-b"))
        syncSession(ann, bob)
        bob.now = 9_000
        assertThat(bob.players.merge("dee-b", "dee-a")).isEqualTo(PlayerMergeResult.Merged)
        syncSession(bob, ann)
        listOf(ann, bob).forEach { phone ->
            assertThat(phone.rounds.observeRounds().first().map { it.playerId }).containsExactly("dee-a", "dee-a")
        }
        // Stored as recorded: the merge is applied when reading, so it can still be resolved differently.
        assertThat(ann.sync.export().triviaRounds.map { it.playerId }).containsExactly("dee-a", "dee-b")
    }

    @Test
    fun undoingADeleteBeatsTheTombstoneOnEveryPhone() = runTest {
        val ann = Phone("Ann")
        ann.players.upsert(player("ann"))
        ann.players.upsert(player("bob"))
        ann.matches.upsert(match("m1", listOf("ann"), listOf("bob")))
        val logged = ann.matches.observeMatch("m1").first()!!
        ann.matches.delete("m1")
        val tombstone = ann.db.deletionDao().getAll().single()
        assertThat(tombstone.kind).isEqualTo("match")
        assertThat(tombstone.deletedAt).isAtLeast(logged.updatedAt)
        ann.matches.upsert(logged) // the snackbar's Undo
        val restored = ann.matches.observeMatch("m1").first()!!
        assertThat(restored.updatedAt).isGreaterThan(tombstone.deletedAt)

        // A phone that saw the delete but not the undo gets the match back.
        val bob = Phone("Bob")
        bob.players.upsert(player("ann"))
        bob.players.upsert(player("bob"))
        bob.sync.apply(
            SyncMerge.normalize(
                ann.sync.export().copy(
                    matches = listOf(logged),
                    deletions = listOf(
                        dev.quinton.goa.core.model.sync.Deletion(DeletionKind.MATCH, "m1", tombstone.deletedAt),
                    ),
                ),
            ),
        )
        assertThat(bob.matches.observeMatches().first()).isEmpty()
        bob.sync.apply(ann.sync.export())
        assertThat(bob.matches.observeMatch("m1").first()).isEqualTo(restored)
    }

    @Test
    fun aSyncFileCarriesTheDataAndOnlyOpensForItsGroup() = runTest {
        val ann = Phone("Ann")
        ann.players.upsert(player("ann"))
        ann.players.upsert(player("bob"))
        ann.matches.upsert(match("m1", listOf("ann"), listOf("bob")))
        val file = ann.sync.exportFile(group)

        val bob = Phone("Bob")
        val result = bob.sync.importFile(file, group)
        assertThat(result.changes.matchesAdded).isEqualTo(1)
        assertThat(result.changes.playersAdded).isEqualTo(2)
        assertThat(bob.sync.export()).isEqualTo(ann.sync.export())

        val other = assertThrows(SyncFileException::class.java) {
            kotlinx.coroutines.runBlocking { bob.sync.importFile(file, GroupCredentials.create("Other")) }
        }
        assertThat(other.problem).isEqualTo(SyncFileProblem.OTHER_GROUP)
        // Importing the same file again changes nothing.
        assertThat(bob.sync.importFile(file, group).changes.isEmpty).isTrue()
    }
}
