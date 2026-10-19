package dev.quinton.goa.core.data.backup

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.data.EditClock
import dev.quinton.goa.core.data.match.RoomDraftRecordRepository
import dev.quinton.goa.core.data.match.RoomMatchRepository
import dev.quinton.goa.core.data.player.RoomPlayerRepository
import dev.quinton.goa.core.data.rating.FakeEloConfigRepository
import dev.quinton.goa.core.data.trivia.RoomTriviaRoundRepository
import dev.quinton.goa.core.database.GoaDatabase
import dev.quinton.goa.core.datastore.draft.DraftStore
import dev.quinton.goa.core.datastore.settings.SettingsDataSource
import dev.quinton.goa.core.datastore.timer.DataStoreTimerStateStore
import dev.quinton.goa.core.datastore.timer.TimerStateStore
import dev.quinton.goa.core.domain.rating.EloConfig
import dev.quinton.goa.core.model.board.BoardLineup
import dev.quinton.goa.core.model.board.BoardState
import dev.quinton.goa.core.model.board.HeroPiece
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionPiece
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.board.SavedBoard
import dev.quinton.goa.core.model.board.TokenPiece
import dev.quinton.goa.core.model.catalog.Catalog
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
import dev.quinton.goa.core.model.settings.AppSettings
import dev.quinton.goa.core.model.settings.ThemeMode
import dev.quinton.goa.core.model.settings.TimerLayout
import dev.quinton.goa.core.model.trivia.TriviaRound
import dev.quinton.goa.core.model.trivia.TriviaRoundKind
import dev.quinton.goa.core.testing.FakeBoardRepository
import dev.quinton.goa.core.testing.FakeCatalogRepository
import dev.quinton.goa.core.testing.TestCatalog
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Optional
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private class FakeContributor(override val key: String, var data: JsonElement?) : BackupContributor {
    val imports = mutableListOf<Pair<JsonElement, ImportMode>>()

    override suspend fun export(): JsonElement? = data

    override suspend fun import(data: JsonElement, mode: ImportMode, useBackupSettings: Boolean) {
        imports += data to mode
        this.data = data
    }
}

@RunWith(RobolectricTestRunner::class)
class BackupRoundTripTest {
    @get:Rule val tmp = TemporaryFolder()

    private val databases = mutableListOf<GoaDatabase>()

    private fun newStore() = PreferenceDataStoreFactory.create(
        produceFile = { tmp.newFile().also { it.delete() }.let { File(it.path + ".preferences_pb") } },
    )

    /** A phone: Room database, settings/timer/draft stores and the real backup contributors. */
    private inner class Device(
        config: EloConfig = EloConfig.DEFAULT,
        contributor: FakeContributor? = null,
        realContributors: Boolean = contributor == null,
        catalog: Catalog = TestCatalog.catalog,
        /** False: a build without the board sandbox's `BoardRepository` binding. */
        boardSandbox: Boolean = true,
    ) {
        val db = GoaDatabase.inMemory(ApplicationProvider.getApplicationContext(), AndroidSQLiteDriver()).also {
            databases +=
                it
        }
        val config = FakeEloConfigRepository(config)
        var now = NOW
        val clock = EditClock { now }
        val players = RoomPlayerRepository(db, clock)
        val matches = RoomMatchRepository(db, clock)
        val drafts = RoomDraftRecordRepository(db, clock)
        val settings = SettingsDataSource(newStore())
        val timer: TimerStateStore = DataStoreTimerStateStore.forFile {
            tmp.newFile().also { it.delete() }.let { File(it.path + ".preferences_pb") }
        }
        val draftStore = DraftStore(newStore())
        val rounds = RoomTriviaRoundRepository(db, clock)
        val boards = FakeBoardRepository()
        val backup = RoomBackupRepository(
            db,
            this.config,
            this.config,
            if (realContributors) {
                setOf(SettingsBackup(settings), TimerPresetsBackup(timer), DraftMethodsBackup(draftStore))
            } else {
                setOfNotNull(contributor)
            },
            FakeCatalogRepository(catalog),
            AppInfo { "9.9.9" },
            if (boardSandbox) Optional.of(boards) else Optional.empty(),
        )
    }

    @After
    fun tearDown() = databases.forEach { it.close() }

    private val ann = Player("6f1c-ann", "Ann \"the Wall\" Ø", 0xFF3FC1B0, avatarId = 23, createdAt = 1_700_000_000_000)
    private val bob =
        Player("6f1c-bob", "Bob", 0xFFE26459, avatarId = 0, createdAt = 1_700_000_000_001, archived = true)
    private val cid = Player("6f1c-cid", "Cid", 0xFF4F8FE0, avatarId = 11, createdAt = 1_700_000_000_002)

    private val matchA = Match(
        id = "m-a",
        createdAt = 1_700_000_100_000,
        playedAt = 1_700_000_090_000,
        status = MatchStatus.COMPLETED,
        mode = GameMode.LONG,
        map = "Single lane",
        winner = Team.RED,
        winCondition = WinCondition.THRONE,
        durationMin = 64,
        notes = "Line one\nLine two — ünïcødé",
        draftId = "d-1",
        tieBreaker = Team.BLUE,
        participants = listOf(
            Participant(ann.id, "test-hero-1", Team.RED),
            Participant(ann.id, "test-hero-2", Team.RED, handicap = true),
            Participant(bob.id, null, Team.BLUE),
            Participant(cid.id, "test-hero-3", Team.BLUE),
        ),
    )
    private val matchB = Match(
        id = "m-b",
        createdAt = 1_700_000_200_000,
        playedAt = 1_700_000_200_000,
        status = MatchStatus.PENDING,
        mode = GameMode.QUICK,
        participants = listOf(Participant(cid.id, null, Team.RED), Participant(ann.id, null, Team.BLUE)),
    )
    private val draft = DraftRecord(
        id = "d-1",
        createdAt = 1_700_000_080_000,
        methodId = "house",
        methodKind = DraftMethodKind.CUSTOM,
        pool = listOf("test-hero-1", "test-hero-2", "test-hero-3", "test-hero-4"),
        teamA = Team.BLUE,
        steps = listOf(
            DraftStepRecord(Team.BLUE, DraftAction.BAN, "test-hero-4"),
            DraftStepRecord(Team.BLUE, DraftAction.PICK, "test-hero-3"),
            DraftStepRecord(Team.RED, DraftAction.PICK, "test-hero-1"),
        ),
        matchId = "m-a",
    )

    private val challenge = TriviaRound(
        id = "t-1",
        playerId = ann.id,
        kind = TriviaRoundKind.CHALLENGE,
        playedAt = 1_700_000_300_000,
        length = 20,
        questions = 20,
        correct = 18,
        bestStreak = 11,
        answerMillis = 102_345,
        heroCount = 32,
        catalogVersion = 7,
    )
    private val practice = TriviaRound(
        id = "t-2",
        playerId = cid.id,
        kind = TriviaRoundKind.PRACTICE,
        playedAt = 1_700_000_400_000,
        length = 0,
        questions = 37,
        correct = 30,
        bestStreak = 12,
        answerMillis = 300_001,
        recall = true,
        heroCount = 1,
        catalogVersion = 7,
    )
    private val board = SavedBoard(
        id = "b-1",
        name = "Arien vs Brogan",
        lineup = BoardLineup(red = listOf("test-hero-1"), blue = listOf("test-hero-2")),
        state = BoardState(
            mapId = "core",
            pieces = listOf(
                HeroPiece("p1", Hex(0, 1), "test-hero-1", Team.RED),
                MinionPiece("p2", Hex(1, -1), Team.BLUE, MinionType.HEAVY),
                TokenPiece("p3", null, "Mine"),
            ),
            battleZones = mapOf("lane" to "zone-3"),
        ),
        createdAt = 1_700_000_500_000,
        updatedAt = 1_700_000_600_000,
    )

    private suspend fun Device.seed() {
        listOf(ann, bob, cid).forEach { players.upsert(it) }
        matches.upsert(matchA)
        matches.upsert(matchB)
        drafts.save(draft)
        rounds.add(challenge)
        rounds.add(practice)
        boards.upsert(board)
    }

    @Test
    fun exportImportExportIsLossless() = runTest {
        val custom = EloConfig(heroInfluence = 0.25, seatAdvantage = 30.0, longMultiplier = 1.5)
        val source = Device(custom, FakeContributor("timer-presets", JsonPrimitive("presets-v1")))
        source.seed()

        val out = ByteArrayOutputStream()
        source.backup.exportTo(out)
        val target = Device(contributor = FakeContributor("timer-presets", null))
        val document = target.backup.read(ByteArrayInputStream(out.toByteArray()))
        target.backup.import(document, ImportMode.REPLACE)

        val first = source.backup.export()
        val second = target.backup.export()
        assertThat(second.copy(exportedAt = 0)).isEqualTo(first.copy(exportedAt = 0))
        assertThat(
            BackupCodec.encode(second.copy(exportedAt = 0)),
        ).isEqualTo(BackupCodec.encode(first.copy(exportedAt = 0)))
        assertThat(target.config.config.first()).isEqualTo(custom)
        assertThat(target.matches.observeMatch("m-a").first()).isEqualTo(matchA.copy(updatedAt = NOW))
        assertThat(target.players.observePlayers(includeArchived = true).first())
            .containsExactly(ann.copy(updatedAt = NOW), bob.copy(updatedAt = NOW), cid.copy(updatedAt = NOW))
        assertThat(target.rounds.observeRounds().first())
            .containsExactly(challenge.copy(updatedAt = NOW), practice.copy(updatedAt = NOW)).inOrder()
        assertThat(target.boards.state.value).containsExactly(board)
        assertThat(document.summary()).isEqualTo(
            BackupSummary(
                BackupDocument.CURRENT_VERSION,
                document.exportedAt,
                players = 3,
                matches = 2,
                drafts = 1,
                hasEloConfig = true,
                extras = setOf("timer-presets"),
                appVersion = "9.9.9",
                triviaRounds = 2,
                boards = 1,
            ),
        )
    }

    @Test
    fun version5MergesRoundsLikeMatchesAndBoardsByTheLaterEdit() = runTest {
        val source = Device()
        source.seed()
        source.now = NOW + 10
        source.rounds.delete(challenge.id) // removed from the leaderboard on the other phone
        source.boards.upsert(board.copy(name = "Renamed", updatedAt = board.updatedAt + 5))
        source.boards.upsert(board.copy(id = "b-2", updatedAt = 1))
        val document = BackupCodec.decode(BackupCodec.encode(source.backup.export()))
        assertThat(document.version).isEqualTo(5)
        assertThat(document.triviaRounds.map { it.id }).containsExactly(practice.id)

        val phone = Device()
        phone.seed()
        phone.boards.upsert(board.copy(id = "b-3", name = "Only here", updatedAt = 3))
        phone.backup.import(document, ImportMode.MERGE)
        // The file's tombstone removes the round here too; boards keep the later edit of each id.
        assertThat(phone.rounds.observeRounds().first().map { it.id }).containsExactly(practice.id)
        assertThat(phone.boards.state.value.associate { it.id to it.name })
            .containsExactly("b-1", "Renamed", "b-2", board.name, "b-3", "Only here")

        val fresh = Device()
        fresh.boards.upsert(board.copy(id = "b-9"))
        fresh.backup.import(document, ImportMode.REPLACE)
        assertThat(fresh.boards.state.value.map { it.id }).containsExactly("b-1", "b-2")
        assertThat(BackupCodec.encode(fresh.backup.export().copy(exportedAt = 0)))
            .isEqualTo(BackupCodec.encode(source.backup.export().copy(exportedAt = 0)))
    }

    @Test
    fun withoutTheBoardSandboxBoardsAreNeitherExportedNorImportedNorCounted() = runTest {
        val bare = Device(boardSandbox = false)
        bare.seed() // its fake store holds a board, but nothing binds it
        val exported = bare.backup.export()
        assertThat(exported.boards).isNull()
        assertThat(BackupCodec.encode(exported)).contains("\"boards\": null")
        // A phone with the sandbox keeps its boards when that file is restored over them.
        val full = Device()
        full.boards.upsert(board)
        full.backup.import(BackupCodec.decode(BackupCodec.encode(exported)), ImportMode.REPLACE)
        assertThat(full.boards.state.value).containsExactly(board)

        // A file with boards, opened on a phone without the sandbox: not imported, not counted.
        val withBoards = Device().apply { seed() }.backup.export()
        assertThat(withBoards.summary().boards).isEqualTo(1)
        val other = Device(boardSandbox = false)
        assertThat(other.backup.summarize(withBoards).boards).isNull()
        other.backup.import(withBoards, ImportMode.REPLACE)
        assertThat(other.boards.state.value).isEmpty()
        assertThat(other.rounds.observeRounds().first()).hasSize(2)
    }

    @Test
    fun aVersion4FileImportsWithoutRoundsAndLeavesThePhonesBoardsAlone() = runTest {
        val source = Device()
        source.seed()
        val v4 = source.backup.export().copy(version = 4, triviaRounds = emptyList(), boards = null)
        val text = BackupCodec.encode(v4).replace(Regex(""",\s*"triviaRounds": \[\s*]"""), "")
            .replace(Regex(""",\s*"boards": \[\s*]"""), "")
        assertThat(text).doesNotContain("triviaRounds")
        val document = BackupCodec.decode(text)

        val phone = Device()
        phone.seed()
        phone.backup.import(document, ImportMode.REPLACE)
        // Replace clears the synced data (the rounds come back from the group) but a v4 file
        // never knew about boards, so they stay.
        assertThat(phone.rounds.observeRounds().first()).isEmpty()
        assertThat(phone.boards.state.value).containsExactly(board)
        assertThat(phone.matches.observeMatches().first().map { it.id }).containsExactly(matchA.id, matchB.id)
        assertThat(phone.backup.export().version).isEqualTo(BackupDocument.CURRENT_VERSION)
    }

    @Test
    fun aBrokenTriviaRoundIsRejectedByDateAndId() {
        val players = listOf(ann, bob, cid)
        fun decode(vararg rounds: TriviaRound) = BackupCodec.decode(
            BackupCodec.encode(BackupDocument(exportedAt = 1, players = players, triviaRounds = rounds.toList())),
        )
        assertThat(decode(challenge, practice).triviaRounds).containsExactly(challenge, practice)
        val inflated = assertThrows(InvalidBackupException::class.java) { decode(challenge.copy(correct = 21)) }
        assertThat(inflated.message).contains("This backup has trivia rounds the app can't import:")
        assertThat(inflated.message).contains("(id t-1)")
        assertThat(inflated.message).contains("more right answers than questions")
        val ghost = assertThrows(InvalidBackupException::class.java) { decode(practice.copy(playerId = "ghost")) }
        assertThat(ghost.message).contains("player ghost isn't in this backup")
        val twice = assertThrows(InvalidBackupException::class.java) { decode(challenge, challenge) }
        assertThat(twice.message).contains("duplicate or empty ids")
        val boardsTwice = assertThrows(InvalidBackupException::class.java) {
            BackupCodec.decode(BackupCodec.encode(BackupDocument(exportedAt = 1, boards = listOf(board, board))))
        }
        assertThat(boardsTwice.message).contains("duplicate or empty ids")
    }

    @Test
    fun replaceWipesLocalDataAndResetsConfig() = runTest {
        val source = Device()
        source.players.upsert(ann)
        val document = source.backup.export()

        val target = Device(EloConfig(kEstablished = 10.0))
        target.seed()
        target.backup.import(document, ImportMode.REPLACE)

        assertThat(
            target.players.observePlayers(includeArchived = true).first(),
        ).containsExactly(ann.copy(updatedAt = NOW))
        assertThat(target.matches.observeMatches().first()).isEmpty()
        assertThat(target.drafts.observeDrafts().first()).isEmpty()
        assertThat(target.config.config.first()).isEqualTo(EloConfig.DEFAULT)
    }

    @Test
    fun mergeAddsWhatIsNewKeepsTheLaterEditOfEachRecordAndKeepsLocalConfig() = runTest {
        val target = Device(EloConfig(heroInfluence = 0.1))
        target.seed() // edited at NOW
        val source = Device(EloConfig(kEstablished = 10.0))
        source.now = NOW + 60_000 // edited later on another phone
        source.players.upsert(ann.copy(name = "Ann (renamed)"))
        source.matches.upsert(matchB.copy(notes = "from backup"))
        source.now = NOW - 60_000 // and an older copy of match A
        source.matches.upsert(matchA.copy(notes = "stale"))
        source.matches.upsert(matchA.copy(id = "m-new"))
        target.backup.import(source.backup.export(), ImportMode.MERGE)

        val players = target.players.observePlayers(includeArchived = true).first()
        assertThat(players.map { it.name }).containsExactly("Ann (renamed)", "Bob", "Cid")
        assertThat(target.matches.observeMatches().first().map { it.id }).containsExactly("m-a", "m-b", "m-new")
        assertThat(target.matches.observeMatch("m-b").first()!!.notes).isEqualTo("from backup")
        assertThat(target.matches.observeMatch("m-a").first()!!.notes).isEqualTo(matchA.notes)
        assertThat(target.config.config.first()).isEqualTo(EloConfig(heroInfluence = 0.1))
    }

    @Test
    fun version4CarriesTombstonesAndMergesSoADeletedMatchStaysDeleted() = runTest {
        val source = Device()
        source.seed()
        source.players.upsert(Player("ann-2", "Ann", 1, 0, createdAt = 9))
        source.players.merge("ann-2", ann.id)
        source.now = NOW + 1
        source.matches.delete(matchB.id)
        val document = BackupCodec.decode(BackupCodec.encode(source.backup.export()))
        assertThat(document.version).isAtLeast(4)
        assertThat(document.deletions.map { it.id }).containsExactly(matchB.id)
        assertThat(document.playerMerges.map { it.fromId to it.toId }).containsExactly("ann-2" to ann.id)

        // A phone that still has match B: merging the backup in deletes it.
        val other = Device()
        other.seed()
        other.backup.import(document, ImportMode.MERGE)
        assertThat(other.matches.observeMatches().first().map { it.id }).containsExactly(matchA.id)
        assertThat(other.players.observePlayers().first().map { it.id }).doesNotContain("ann-2")

        val fresh = Device()
        fresh.backup.import(document, ImportMode.REPLACE)
        assertThat(BackupCodec.encode(fresh.backup.export().copy(exportedAt = 0)))
            .isEqualTo(BackupCodec.encode(source.backup.export().copy(exportedAt = 0)))
    }

    @Test
    fun anOldBackupsRatingConstantsNeverBeatTheGroups() = runTest {
        val phone = Device(EloConfig(kEstablished = 25.0))
        phone.config.updatedAt = NOW // changed by the group last night
        val v3 = BackupDocument(version = 3, exportedAt = 1, eloConfig = EloConfig(kEstablished = 15.0))
        phone.backup.import(v3, ImportMode.REPLACE)
        // The file's constants apply, with an old stamp: the group's next sync restores its own.
        assertThat(phone.config.config.first()).isEqualTo(EloConfig(kEstablished = 15.0))
        assertThat(phone.config.stamped().updatedAt).isEqualTo(1)
    }

    @Test
    fun contributorsReceiveTheirDataOnlyWhenPresent() = runTest {
        val withData = Device(contributor = FakeContributor("draft-methods", JsonPrimitive(42)))
        val document = withData.backup.export()
        val contributor = FakeContributor("draft-methods", null)
        Device(contributor = contributor).backup.import(document, ImportMode.MERGE)
        assertThat(contributor.imports).containsExactly(JsonPrimitive(42) to ImportMode.MERGE)

        val other = FakeContributor("timer-presets", null)
        Device(contributor = other).backup.import(document, ImportMode.REPLACE)
        assertThat(other.imports).isEmpty()
    }

    @Test
    fun invalidFilesAreRejectedWithReadableMessages() {
        assertThrows(InvalidBackupException::class.java) { BackupCodec.decode("not json") }
        assertThrows(InvalidBackupException::class.java) { BackupCodec.decode("""{"format":"other","exportedAt":1}""") }
        val newer = assertThrows(InvalidBackupException::class.java) {
            BackupCodec.decode("""{"format":"goa-companion-backup","version":99,"exportedAt":1}""")
        }
        assertThat(newer.message).contains("newer version")
        val dupes = """{"format":"goa-companion-backup","version":1,"exportedAt":1,"players":[""" +
            """{"id":"a","name":"A","color":1,"avatarId":0,"createdAt":0},""" +
            """{"id":"a","name":"B","color":1,"avatarId":0,"createdAt":0}]}"""
        assertThrows(InvalidBackupException::class.java) { BackupCodec.decode(dupes) }
        // A merge naming a missing player would block every sync afterwards.
        val badMerge = BackupDocument(
            exportedAt = 1,
            players = listOf(Player("a", "A", 1, 0, 0)),
            playerMerges = listOf(dev.quinton.goa.core.model.sync.PlayerMerge("a", "ghost", 1)),
        )
        val merge =
            assertThrows(InvalidBackupException::class.java) { BackupCodec.decode(BackupCodec.encode(badMerge)) }
        assertThat(merge.message).contains("merges a player")
    }

    @Test
    fun matchesWithAHeroTwiceOrAPlayerOnBothTeamsAreRejectedByName() {
        fun match(id: String, vararg seats: Participant) = Match(
            id = id,
            createdAt = NOON_UTC,
            playedAt = NOON_UTC,
            status = MatchStatus.PENDING,
            mode = GameMode.QUICK,
            participants = seats.toList(),
        )
        val ann = Player("ann", "Ann", 1, 0, 0)
        val bob = Player("bob", "Bob", 1, 0, 0)
        val good = match("good", Participant("ann", "h1", Team.RED), Participant("bob", "h2", Team.BLUE))
        val twice = match("twice", Participant("ann", "h1", Team.RED), Participant("bob", "h1", Team.BLUE))
        val both = match("both", Participant("ann", "h1", Team.RED), Participant("ann", "h2", Team.BLUE))
        fun decode(vararg matches: Match) = BackupCodec.decode(
            BackupCodec.encode(BackupDocument(exportedAt = 1, players = listOf(ann, bob), matches = matches.toList())),
        )

        assertThat(decode(good).matches).containsExactly(good)
        val heroTwice = assertThrows(InvalidBackupException::class.java) { decode(good, twice) }
        assertThat(heroTwice.message).contains("twice")
        assertThat(heroTwice.message).contains("h1")
        assertThat(heroTwice.message).contains("1970-01-01")
        assertThat(heroTwice.message).contains("(id twice)")
        val bothTeams = assertThrows(InvalidBackupException::class.java) { decode(both) }
        assertThat(bothTeams.message).contains("Ann is on both teams")
        assertThat(bothTeams.message).contains("(id both)")
        val all = assertThrows(InvalidBackupException::class.java) { decode(twice, both) }
        assertThat(all.message).contains("(id twice)")
        assertThat(all.message).contains("(id both)")
    }

    @Test
    fun aPilotMissingFromTheFileIsRejectedNeverAGhostPlayer() {
        val ann = Player("ann", "Ann", 1, 0, 0)
        val match = Match(
            id = "ghost",
            createdAt = NOON_UTC,
            playedAt = NOON_UTC,
            status = MatchStatus.COMPLETED,
            mode = GameMode.QUICK,
            winner = Team.RED,
            participants = listOf(Participant("ann", null, Team.RED), Participant("nobody", null, Team.BLUE)),
        )
        val error = assertThrows(InvalidBackupException::class.java) {
            BackupCodec.decode(
                BackupCodec.encode(BackupDocument(exportedAt = 1, players = listOf(ann), matches = listOf(match))),
            )
        }
        assertThat(error.message).contains("player nobody isn't in this backup")
        assertThat(error.message).contains("(id ghost)")
    }

    @Test
    fun unknownHeroesAreKeptAsTheyAreAndListedInTheSummary() = runTest {
        val source = Device(realContributors = false)
        source.seed()
        val bad = matchA.copy(
            participants = matchA.participants.map {
                if (it.heroId ==
                    "test-hero-3"
                ) {
                    it.copy(heroId = "notahero")
                } else {
                    it
                }
            },
        )
        val document = source.backup.export().let { doc ->
            doc.copy(
                matches = doc.matches.map { if (it.id == bad.id) bad.copy(updatedAt = it.updatedAt) else it },
                drafts = doc.drafts.map { d ->
                    d.copy(steps = d.steps + DraftStepRecord(Team.RED, DraftAction.BAN, "notahero"))
                },
            )
        }
        val target = Device(realContributors = false)
        assertThat(target.backup.summarize(document).unknownHeroes).containsExactly("notahero")

        target.backup.import(document, ImportMode.REPLACE)

        // Stored as is (a newer phone's hero must survive a pass through this one); the ratings
        // read it as an Unknown hero (ReplayRatingsRepositoryTest).
        val seats = target.matches.observeMatch(bad.id).first()!!.participants
        assertThat(seats.map { it.heroId }).containsExactly("test-hero-1", "test-hero-2", null, "notahero").inOrder()
        assertThat(target.drafts.observeDrafts().first().single().steps.map { it.heroId }).contains("notahero")
    }

    @Test
    fun everythingSurvivesExportThenImportIntoAnEmptyPhone() = runTest {
        val custom = EloConfig(heroInfluence = 0.3, kProvisional = 32.0)
        val source = Device(custom)
        source.seed()
        val settings = AppSettings(
            theme = ThemeMode.LIGHT,
            ownedProducts = setOf("core", "devoted"),
            hapticsEnabled = false,
            soundsEnabled = false,
            timerLayout = TimerLayout.ROUND,
        )
        source.settings.update { settings }
        val presets = """[{"id":"table-rules","name":"Table rules","x":1},{"id":"mine","name":"Mine","x":[1,2]}]"""
        source.timer.writePresets(presets)
        source.timer.writeSelectedPresetId("mine")
        val methods = """[{"id":"house-2","name":"House 2","steps":["ban_a","pick_a"]}]"""
        source.draftStore.setCustomMethods(methods)

        val out = ByteArrayOutputStream()
        source.backup.exportTo(out)
        val document = BackupCodec.decode(out.toString(Charsets.UTF_8.name()))
        assertThat(document.appVersion).isEqualTo("9.9.9")
        assertThat(document.extras.keys).containsExactly("settings", "timer-presets", "draft-methods")

        val target = Device()
        target.backup.import(document, ImportMode.REPLACE)

        assertThat(target.settings.settings.first()).isEqualTo(settings)
        assertThat(
            Json.parseToJsonElement(target.timer.presetsJson.first()!!),
        ).isEqualTo(Json.parseToJsonElement(presets))
        assertThat(target.timer.selectedPresetId.first()).isEqualTo("mine")
        assertThat(
            Json.parseToJsonElement(target.draftStore.customMethods.first()!!),
        ).isEqualTo(Json.parseToJsonElement(methods))
        assertThat(target.config.config.first()).isEqualTo(custom)
        // import → export is lossless for everything (bar the export time).
        assertThat(BackupCodec.encode(target.backup.export().copy(exportedAt = 0)))
            .isEqualTo(BackupCodec.encode(source.backup.export().copy(exportedAt = 0)))
    }

    @Test
    fun mergeKeepsThePhonesSettingsUnlessAskedAndMergesListsById() = runTest {
        val source = Device(EloConfig(kEstablished = 12.0))
        source.settings.update { it.copy(theme = ThemeMode.LIGHT) }
        source.timer.writePresets("""[{"id":"a","v":"file"},{"id":"c","v":"file"}]""")
        source.timer.writeSelectedPresetId("c")
        source.draftStore.setCustomMethods("""[{"id":"m2","v":"file"}]""")
        val document = source.backup.export()

        val keep = Device(EloConfig(heroInfluence = 0.1))
        keep.timer.writePresets("""[{"id":"a","v":"phone"},{"id":"b","v":"phone"}]""")
        keep.timer.writeSelectedPresetId("b")
        keep.draftStore.setCustomMethods("""[{"id":"m1","v":"phone"}]""")
        keep.backup.import(document, ImportMode.MERGE)

        assertThat(keep.config.config.first()).isEqualTo(EloConfig(heroInfluence = 0.1))
        assertThat(keep.settings.settings.first().theme).isEqualTo(ThemeMode.DARK)
        assertThat(keep.timer.selectedPresetId.first()).isEqualTo("b")
        val merged = Json.parseToJsonElement(keep.timer.presetsJson.first()!!) as JsonArray
        assertThat(
            merged.toString(),
        ).isEqualTo("""[{"id":"a","v":"file"},{"id":"b","v":"phone"},{"id":"c","v":"file"}]""")
        assertThat(
            keep.draftStore.customMethods.first(),
        ).isEqualTo("""[{"id":"m1","v":"phone"},{"id":"m2","v":"file"}]""")

        val adopt = Device(EloConfig(heroInfluence = 0.1))
        adopt.backup.import(document, ImportMode.MERGE, useBackupSettings = true)
        assertThat(adopt.config.config.first()).isEqualTo(EloConfig(kEstablished = 12.0))
        assertThat(adopt.settings.settings.first().theme).isEqualTo(ThemeMode.LIGHT)
        assertThat(adopt.timer.selectedPresetId.first()).isEqualTo("c")
    }

    @Test
    fun draftMethodSummariesFromOldBuildsArePluralisedOnExportAndImport() = runTest {
        val old = """[{"id":"m","summary":"Pool of 17. 2 ban(s). Custom order."},""" +
            """{"id":"n","summary":"Pool of 9. 1 ban(s). Custom order."}]"""
        val source = Device()
        source.draftStore.setCustomMethods(old)
        val exported = source.backup.export().extras.getValue("draft-methods").toString()
        assertThat(exported).contains("Pool of 17. 2 bans. Custom order.")
        assertThat(exported).contains("Pool of 9. 1 ban. Custom order.")

        val target = Device()
        target.backup.import(
            source.backup.export().copy(extras = mapOf("draft-methods" to Json.parseToJsonElement(old))),
            ImportMode.REPLACE,
        )
        assertThat(target.draftStore.customMethods.first()).doesNotContain("ban(s)")
    }

    @Test
    fun version1FileImportsLosslesslyWithoutACoin() = runTest {
        // A v1 export as written by the first release: no Match.tieBreaker.
        val v1 = """
            {"format":"goa-companion-backup","version":1,"exportedAt":7,"appVersion":"1.0",
             "players":[{"id":"ann","name":"Ann","color":1,"avatarId":2,"createdAt":3,"archived":false}],
             "matches":[{"id":"m1","createdAt":10,"playedAt":11,"status":"completed","mode":"long",
               "map":null,"winner":"blue","winCondition":"last_push","durationMin":50,"notes":"n",
               "draftId":"d1","participants":[{"playerId":"ann","heroId":"arien","team":"blue","handicap":true}]}],
             "drafts":[{"id":"d1","createdAt":9,"methodId":"house","methodKind":"custom","pool":["arien"],
               "teamA":"blue","steps":[{"team":"blue","action":"pick","heroId":"arien"}],"matchId":"m1"}],
             "eloConfig":null,"extras":{}}
        """.trimIndent()
        val document = BackupCodec.decode(v1)
        // A real hero id: this test's phone has no catalog, so no id is treated as unknown.
        val target = Device(catalog = Catalog.EMPTY)
        target.backup.import(document, ImportMode.REPLACE)

        assertThat(target.matches.observeMatch("m1").first()).isEqualTo(
            Match(
                id = "m1",
                createdAt = 10,
                playedAt = 11,
                status = MatchStatus.COMPLETED,
                mode = GameMode.LONG,
                winner = Team.BLUE,
                winCondition = WinCondition.LAST_PUSH,
                durationMin = 50,
                notes = "n",
                draftId = "d1",
                participants = listOf(Participant("ann", "arien", Team.BLUE, handicap = true)),
                tieBreaker = null,
            ),
        )
        assertThat(target.drafts.observeDrafts().first().single().teamA).isEqualTo(Team.BLUE)
        assertThat(target.players.observePlayers().first().single().name).isEqualTo("Ann")
        // Re-exported as the current version, so an older app refuses it instead of dropping the coin.
        assertThat(target.backup.export().version).isEqualTo(BackupDocument.CURRENT_VERSION)
    }

    @Test
    fun version2FileImportsLosslesslyAndKeepsThePhonesSettings() = runTest {
        val coin = matchA.copy(tieBreaker = Team.BLUE)
        val v2 = BackupCodec.encode(
            BackupDocument(
                version = 2,
                exportedAt = 7,
                players = listOf(ann, bob, cid),
                matches = listOf(coin, matchB),
            ),
        )
        val target = Device()
        target.settings.update { it.copy(theme = ThemeMode.LIGHT) }
        target.backup.import(BackupCodec.decode(v2), ImportMode.REPLACE)

        assertThat(target.matches.observeMatch(coin.id).first()).isEqualTo(coin)
        assertThat(target.matches.observeMatch(matchB.id).first()).isEqualTo(matchB)
        // No settings in a v2 file: Replace leaves the phone's app settings alone.
        assertThat(target.settings.settings.first().theme).isEqualTo(ThemeMode.LIGHT)
    }

    @Test
    fun settingsFromOlderAndNewerBuildsImportWithTheTimerLayoutDefault() = runTest {
        val source = Device()
        source.settings.update { it.copy(theme = ThemeMode.LIGHT, hapticsEnabled = false) }
        val document = source.backup.export()
        // A v3 file from before the round view has no timerLayout; a newer build may know more layouts.
        val old = """{"theme":"LIGHT","hapticsEnabled":false}"""
        val newer = """{"theme":"LIGHT","hapticsEnabled":false,"timerLayout":"GRID"}"""
        for (settings in listOf(old, newer)) {
            val target = Device()
            target.settings.update { it.copy(timerLayout = TimerLayout.ROUND) }
            val file = document.copy(extras = document.extras + ("settings" to Json.parseToJsonElement(settings)))
            target.backup.import(file, ImportMode.REPLACE)

            val imported = target.settings.settings.first()
            assertThat(imported.theme).isEqualTo(ThemeMode.LIGHT)
            assertThat(imported.hapticsEnabled).isFalse()
            assertThat(imported.timerLayout).isEqualTo(TimerLayout.STEPS)
        }
    }

    @Test
    fun unknownKeysAreIgnored() {
        // Unknown keys from a newer minor version are ignored.
        val doc = BackupCodec.decode("""{"format":"goa-companion-backup","version":1,"exportedAt":5,"future":true}""")
        assertThat(doc.exportedAt).isEqualTo(5)
    }
}

/** The test phones' clock. */
private const val NOW = 1_700_100_000_000L

/** 1970-01-01 12:00 UTC: the same calendar day in every time zone from UTC−11 to UTC+11. */
private const val NOON_UTC = 43_200_000L
