package dev.quinton.goa.core.data.sync

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import dev.quinton.goa.core.data.EditClock
import dev.quinton.goa.core.data.PlayerMergeResult
import dev.quinton.goa.core.data.match.RoomDraftRecordRepository
import dev.quinton.goa.core.data.match.RoomMatchRepository
import dev.quinton.goa.core.data.player.RoomPlayerRepository
import dev.quinton.goa.core.data.rating.DataStoreEloConfigRepository
import dev.quinton.goa.core.database.GoaDatabase
import dev.quinton.goa.core.datastore.rating.EloConfigDataSource
import dev.quinton.goa.core.domain.backup.BackupFiles
import dev.quinton.goa.core.domain.rating.RatingsSnapshot
import dev.quinton.goa.core.domain.sync.PlayerMergeOutcome
import dev.quinton.goa.core.domain.sync.PlayerMerges
import dev.quinton.goa.core.domain.sync.SharedWrites
import dev.quinton.goa.core.domain.sync.SyncDocument
import dev.quinton.goa.core.domain.sync.SyncMerge
import dev.quinton.goa.core.domain.sync.SyncValidation
import dev.quinton.goa.core.model.draft.DraftAction
import dev.quinton.goa.core.model.draft.DraftMethodKind
import dev.quinton.goa.core.model.draft.DraftRecord
import dev.quinton.goa.core.model.draft.DraftStepRecord
import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.core.model.match.Participant
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.match.WinCondition
import dev.quinton.goa.core.model.player.Player
import java.io.File
import java.time.ZoneId
import kotlin.random.Random
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The hub's web writes ([SharedWrites], run by its `GroupStore`) against the phones' Room
 * repositories, with real Room and DataStore:
 *
 * - *parity:* the same edits, at the same clock (going back now and then), leave the same stored
 *   data (ids, createdAt, updatedAt stamps, tombstones, merge rows, refusals, rating constants);
 * - *convergence:* phones editing on their own, the hub editing from web pages, Nearby syncs
 *   between phones, hub syncs, backup imports on the web and clock skew, in random order, end
 *   with one document everywhere and identical ratings (ADR-003, ADR-025, ADR-030).
 */
@RunWith(RobolectricTestRunner::class)
class SharedWritesParityTest {
    @get:Rule val tmp = TemporaryFolder()

    private val databases = mutableListOf<GoaDatabase>()

    @After
    fun tearDown() = databases.forEach { it.close() }

    /** A phone: Room repositories, the DataStore rating constants, and its sync repository. */
    private inner class Phone(val name: String) {
        val db = GoaDatabase.inMemory(ApplicationProvider.getApplicationContext(), AndroidSQLiteDriver())
            .also { databases += it }
        var now = 1_000L
        val clock = EditClock { now }
        val players = RoomPlayerRepository(db, clock)
        val matches = RoomMatchRepository(db, clock)
        val drafts = RoomDraftRecordRepository(db, clock)
        val elo = DataStoreEloConfigRepository(
            EloConfigDataSource(
                PreferenceDataStoreFactory.create(produceFile = { File(tmp.newFolder(), "elo.preferences_pb") }),
            ),
            clock,
        )
        val sync = RoomSyncRepository(db, elo, clock)
        private val local = LocalSyncData(db)

        /** Everything stored, as a canonical document (what this phone would sync). */
        suspend fun document(): SyncDocument = SyncMerge.normalize(local.read().copy(eloConfig = elo.stamped()))
    }

    /** The hub's `GroupStore` in memory: a write, then normalize and validate, as it stores. */
    private class Hub {
        var doc: SyncDocument = SyncDocument.EMPTY
        var now = 1_000L

        fun <T> write(change: (SyncDocument) -> Pair<SyncDocument, T>): T {
            val (next, result) = change(doc)
            val normal = SyncMerge.normalize(next)
            SyncValidation.problem(normal)?.let { error("The hub would refuse: $it") }
            doc = normal
            return result
        }

        fun mergeIn(incoming: SyncDocument): SyncDocument = write { SyncMerge.merge(it, incoming) to Unit }.let { doc }
    }

    private val names = listOf("Ann", "ann ", "Bob", "Cy", "Dee", "Eve", "Fay", "Gus", "  Hal", "Ivy")
    private val heroes = List(14) { "hero-$it" } + "hero-from-a-newer-catalog"

    private fun Random.player(id: String, now: Long) =
        Player(id, names.random(this), color = nextLong(1, 10), avatarId = nextInt(24), createdAt = now)

    /** A valid match among [ids] (distinct pilots per team, a double pilot sometimes, no hero twice). */
    private fun Random.match(id: String, ids: List<String>, createdAt: Long, previous: Match?): Match? {
        if (ids.size < 2) return null
        val people = ids.shuffled(this).take(nextInt(2, minOf(ids.size, 6) + 1))
        val split = nextInt(1, people.size)
        val red = people.take(split)
        val blue = people.drop(split)
        val seatsRed = red + if (nextInt(4) == 0) listOf(red.first()) else emptyList()
        val heroPool = heroes.shuffled(this)
        val seats = seatsRed.map { it to Team.RED } + blue.map { it to Team.BLUE }
        val participants = seats.mapIndexed { i, (p, t) ->
            Participant(p, heroPool[i].takeIf { nextInt(8) != 0 }, t, handicap = nextInt(6) == 0)
        }
        val status = listOf(MatchStatus.COMPLETED, MatchStatus.COMPLETED, MatchStatus.PENDING, MatchStatus.ABANDONED)
            .random(this)
        val completed = status == MatchStatus.COMPLETED
        return Match(
            id = id,
            createdAt = previous?.createdAt ?: createdAt,
            playedAt = createdAt - nextLong(0, 100_000),
            status = status,
            mode = GameMode.entries.random(this),
            map = listOf(null, "Kitchen", "M".repeat(80)).random(this),
            winner = if (completed) Team.entries.random(this) else null,
            winCondition = if (completed) WinCondition.entries.random(this) else null,
            durationMin = listOf(null, 35, 48).random(this),
            notes = listOf("", "Close one", "N".repeat(1_200)).random(this),
            draftId = previous?.draftId,
            participants = participants,
            tieBreaker = listOf(null, Team.RED, Team.BLUE).random(this),
        )
    }

    private fun Random.draft(id: String, createdAt: Long, matchId: String?) = DraftRecord(
        id = id,
        createdAt = createdAt,
        methodId = "house",
        methodKind = DraftMethodKind.entries.random(this),
        pool = heroes.shuffled(this).take(nextInt(0, 8)),
        teamA = Team.entries.random(this),
        steps = heroes.shuffled(this).take(nextInt(0, 6)).map {
            DraftStepRecord(Team.entries.random(this), DraftAction.entries.random(this), it)
        },
        matchId = matchId,
    )

    private fun PlayerMergeResult.shared(): Any = when (this) {
        PlayerMergeResult.Merged -> "merged"
        PlayerMergeResult.NotPossible -> "not possible"
        is PlayerMergeResult.PlayedTogether -> matches.map { it.id }
    }

    private fun PlayerMergeOutcome.shared(): Any = when (this) {
        PlayerMergeOutcome.Merged -> "merged"
        PlayerMergeOutcome.NotPossible -> "not possible"
        is PlayerMergeOutcome.PlayedTogether -> matches.map { it.id }
    }

    /** One random edit, made the same way on [phone] (Room) and [hub] (SharedWrites) at the same time. */
    @Suppress("CyclomaticComplexMethod", "LongMethod")
    private suspend fun sameEdit(random: Random, phone: Phone, hub: Hub, step: Int): String {
        val now = hub.now
        val doc = hub.doc
        val ids = doc.players.map { it.id }
        val live = PlayerMerges(doc.playerMerges).visible(doc.players).map { it.id }
        return when (random.nextInt(11)) {
            0 -> {
                val candidate = random.player("p$step", now)
                val onPhone = phone.players.ensurePlayer(candidate)
                val onHub = hub.write { SharedWrites.ensurePlayer(it, candidate, now) }
                assertThat(onPhone.id).isEqualTo(onHub.id)
                "ensure ${candidate.name}"
            }

            1 -> {
                val target = doc.players.randomOrNull(random) ?: return "nothing"
                val renamed = target.copy(name = names.random(random), color = random.nextLong(1, 10))
                phone.players.upsert(renamed)
                hub.write { SharedWrites.upsertPlayer(it, renamed, now) }
                "rename ${target.id}"
            }

            2 -> {
                val target = (ids + "nobody").random(random)
                val archived = random.nextBoolean()
                phone.players.archive(target, archived)
                hub.write { SharedWrites.archivePlayer(it, target, archived, now) to Unit }
                "archive $target"
            }

            3 -> {
                val previous = doc.matches.randomOrNull(random)?.takeIf { random.nextBoolean() }
                val match = random.match(previous?.id ?: "m$step", ids, now, previous) ?: return "nothing"
                phone.matches.upsert(match)
                hub.write { SharedWrites.upsertMatch(it, match, now) }
                "match ${match.id}"
            }

            4 -> {
                val id = (doc.matches.map { it.id } + "gone").random(random)
                val minutes = random.nextInt(20, 90)
                val leave = random.nextInt(5) == 0
                val change = { m: Match -> if (leave) null else m.copy(durationMin = minutes) }
                val onPhone = phone.matches.edit(id, change)
                val onHub = hub.write { SharedWrites.editMatch(it, id, now, change) }
                assertThat(onPhone).isEqualTo(onHub)
                "edit $id"
            }

            5 -> {
                val id = (doc.matches.map { it.id } + "gone").random(random)
                val onPhone = phone.matches.delete(id)
                val before = hub.doc.matches.firstOrNull { it.id == id }
                hub.write { SharedWrites.deleteMatch(it, id, now) to Unit }
                assertThat(onPhone).isEqualTo(before)
                deleted = onPhone ?: deleted
                "delete $id"
            }

            6 -> {
                val copy = deleted ?: return "nothing"
                val onPhone = phone.matches.restore(copy)
                val onHub = hub.write { SharedWrites.restoreMatch(it, copy, now) }
                assertThat(onPhone).isEqualTo(onHub)
                "restore ${copy.id}"
            }

            7 -> {
                val previous = doc.drafts.randomOrNull(random)?.takeIf { random.nextBoolean() }
                val record = random.draft(
                    previous?.id ?: "d$step",
                    previous?.createdAt ?: now,
                    doc.matches.randomOrNull(random)?.id,
                )
                phone.drafts.save(record)
                hub.write { SharedWrites.saveDraft(it, record, now) }
                "draft ${record.id}"
            }

            8 -> {
                val from = (ids + "nobody").random(random)
                val to = (live.ifEmpty { ids } + "nobody").random(random)
                val onPhone = phone.players.merge(from, to)
                val onHub = hub.write { SharedWrites.mergePlayers(it, from, to, now) }
                assertThat(onPhone.shared()).isEqualTo(onHub.shared())
                "merge $from into $to"
            }

            9 -> {
                val k = listOf(20.0, 32.0, 40.0, 500.0).random(random)
                phone.elo.update { it.copy(kEstablished = k) }
                hub.write { SharedWrites.updateEloConfig(it, now) { c -> c.copy(kEstablished = k) } to Unit }
                "constants k=$k"
            }

            else -> {
                phone.elo.reset()
                hub.write { SharedWrites.resetEloConfig(it, now) to Unit }
                "reset constants"
            }
        }
    }

    private var deleted: Match? = null

    @Test
    fun webWritesStoreExactlyWhatThePhonesRepositoriesStore() = runTest {
        for (seed in 1..6) {
            val random = Random(seed)
            val phone = Phone("phone-$seed")
            val hub = Hub()
            deleted = null
            repeat(150) { step ->
                // The clock mostly moves on, sometimes jumps back (a phone or server clock corrected).
                val now = maxOf(1L, hub.now + random.nextLong(-3_000, 10_000))
                hub.now = now
                phone.now = now
                val what = sameEdit(random, phone, hub, step)
                assertWithMessage("seed $seed, step $step: $what").that(phone.document()).isEqualTo(hub.doc)
            }
        }
    }

    // ---- Convergence ------------------------------------------------------------------------------

    /** What every screen and the hub compute: the replay of the resolved log, newest first. */
    private fun ratings(doc: SyncDocument): RatingsSnapshot {
        val merges = PlayerMerges(doc.playerMerges)
        val log = merges.resolve(
            doc.matches.sortedWith(
                compareByDescending<Match> { it.playedAt }.thenByDescending { it.createdAt }.thenByDescending { it.id },
            ),
        )
        val known = heroes.filterNot { it.startsWith("hero-from") }.toSet()
        return RatingsSnapshot.compute(log, doc.drafts, doc.eloConfig.config.sanitized(), known, ZoneId.of("UTC"))
    }

    /** A phone's own edit (its repositories only), valid on its own data. */
    private suspend fun phoneEdit(random: Random, phone: Phone, step: Int) {
        val doc = phone.document()
        val ids = doc.players.map { it.id }
        when (random.nextInt(7)) {
            0 -> phone.players.ensurePlayer(random.player("${phone.name}-p$step", phone.now))

            1, 2 -> {
                val previous = doc.matches.randomOrNull(random)?.takeIf { random.nextBoolean() }
                random.match(previous?.id ?: "${phone.name}-m$step", ids, phone.now, previous)?.let {
                    phone.matches.upsert(it)
                }
            }

            3 -> doc.matches.randomOrNull(random)?.let { phone.matches.delete(it.id) }

            4 -> doc.matches.randomOrNull(random)?.let { m -> phone.matches.edit(m.id) { it.copy(durationMin = 42) } }

            5 -> {
                val live = PlayerMerges(doc.playerMerges).visible(doc.players)
                if (live.size >= 2) {
                    val (a, b) = live.shuffled(random)
                    phone.players.merge(a.id, b.id)
                }
            }

            else -> phone.elo.update { it.copy(heroInfluence = listOf(0.25, 0.5, 0.75).random(random)) }
        }
    }

    /** A web edit on the hub (SharedWrites through the store's checks). */
    private fun hubEdit(random: Random, hub: Hub, step: Int) {
        val now = hub.now
        val ids = hub.doc.players.map { it.id }
        when (random.nextInt(6)) {
            0 -> hub.write { SharedWrites.ensurePlayer(it, random.player("hub-p$step", now), now) }

            1, 2 -> {
                val previous = hub.doc.matches.randomOrNull(random)?.takeIf { random.nextBoolean() }
                random.match(previous?.id ?: "hub-m$step", ids, now, previous)?.let { m ->
                    hub.write { SharedWrites.upsertMatch(it, m, now) }
                }
            }

            3 -> hub.doc.matches.randomOrNull(random)?.let { m ->
                hub.write {
                    SharedWrites.deleteMatch(it, m.id, now) to
                        Unit
                }
            }

            4 -> {
                val live = PlayerMerges(hub.doc.playerMerges).visible(hub.doc.players)
                if (live.size >= 2) {
                    val (a, b) = live.shuffled(random)
                    hub.write { SharedWrites.mergePlayers(it, a.id, b.id, now) }
                }
            }

            else -> hub.write {
                SharedWrites.updateEloConfig(it, now) { c -> c.copy(kEstablished = 24.0 + step % 3) } to
                    Unit
            }
        }
    }

    /** The phone's hub sync: its document up, the hub merges and stores, the phone applies the reply. */
    private suspend fun hubSync(phone: Phone, hub: Hub, editMeanwhile: suspend () -> Unit = {}) {
        val reply = hub.mergeIn(phone.sync.export())
        editMeanwhile() // the user keeps editing while the request is out
        phone.sync.apply(reply)
    }

    /** Nearby: both phones end with the merge of both (what a sync session does). */
    private suspend fun nearby(a: Phone, b: Phone) {
        val merged = SyncMerge.merge(a.sync.export(), b.sync.export())
        a.sync.apply(merged)
        b.sync.apply(merged)
    }

    @Test
    fun phonesAndTheHubConvergeToOneDocumentAndIdenticalRatingsInAnyOrder() = runTest {
        for (seed in 1..4) {
            val random = Random(100 + seed)
            val phones = List(3) { Phone("ph$it-s$seed") }
            val hub = Hub()
            // Clock skew: each device runs ahead or behind by up to a minute.
            val skew = phones.associateWith { random.nextLong(-60_000, 60_000) }
            var wall = 10_000_000L
            repeat(120) { step ->
                wall += random.nextLong(-2_000, 20_000)
                phones.forEach { it.now = wall + skew.getValue(it) }
                hub.now = wall
                val phone = phones.random(random)
                when (random.nextInt(9)) {
                    0, 1, 2 -> phoneEdit(random, phone, step)

                    3, 4 -> hubEdit(random, hub, step)

                    5 -> hubSync(phone, hub) { if (random.nextBoolean()) phoneEdit(random, phone, step + 1_000) }

                    6 -> nearby(phone, (phones - phone).random(random))

                    7 -> {
                        // A backup exported on a phone, merged on the web.
                        val file = BackupFiles.export(phone.sync.export(), exportedAt = wall, appVersion = "test")
                        hub.write { BackupFiles.merge(it, file) to Unit }
                    }

                    else -> {
                        // Two devices edit the same match at once (or one deletes it): the later wins everywhere.
                        val shared = hub.doc.matches.randomOrNull(random) ?: return@repeat
                        phone.sync.apply(hub.doc)
                        if (random.nextBoolean()) {
                            phone.matches.edit(shared.id) { it.copy(notes = "phone") }
                        } else {
                            phone.matches.delete(shared.id)
                        }
                        hub.write<Match?> {
                            SharedWrites.editMatch(it, shared.id, hub.now) { m -> m.copy(notes = "web") }
                        }
                    }
                }
            }
            // Everyone syncs until nothing changes: hub, then phones among themselves, in both directions.
            repeat(2) {
                phones.forEach { hubSync(it, hub) }
                phones.reversed().forEach { hubSync(it, hub) }
            }
            val expected = hub.doc
            phones.forEach { phone ->
                assertWithMessage("seed $seed, ${phone.name}").that(phone.document()).isEqualTo(expected)
            }
            val reference = ratings(expected)
            phones.forEach { phone ->
                val snapshot = ratings(phone.document())
                assertThat(snapshot.ratings).isEqualTo(reference.ratings)
                assertThat(snapshot.meta).isEqualTo(reference.meta)
                // What the phone's ReplayRatingsRepository reads: Room's resolved log and its constants.
                val roomLog = phone.matches.observeMatches().first()
                val known = heroes.filterNot { it.startsWith("hero-from") }.toSet()
                val onPhone = RatingsSnapshot.compute(
                    roomLog,
                    phone.drafts.observeDrafts().first(),
                    phone.elo.config.first(),
                    known,
                    ZoneId.of("UTC"),
                )
                assertThat(onPhone.ratings).isEqualTo(reference.ratings)
            }
        }
    }
}
