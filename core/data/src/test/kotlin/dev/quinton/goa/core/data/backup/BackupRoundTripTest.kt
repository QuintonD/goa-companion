package dev.quinton.goa.core.data.backup

import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.data.match.RoomDraftRecordRepository
import dev.quinton.goa.core.data.match.RoomMatchRepository
import dev.quinton.goa.core.data.player.RoomPlayerRepository
import dev.quinton.goa.core.data.rating.FakeEloConfigRepository
import dev.quinton.goa.core.database.GoaDatabase
import dev.quinton.goa.core.domain.rating.EloConfig
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
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private class FakeContributor(override val key: String, var data: JsonElement?) : BackupContributor {
    val imports = mutableListOf<Pair<JsonElement, ImportMode>>()

    override suspend fun export(): JsonElement? = data

    override suspend fun import(data: JsonElement, mode: ImportMode) {
        imports += data to mode
        this.data = data
    }
}

@RunWith(RobolectricTestRunner::class)
class BackupRoundTripTest {
    private val databases = mutableListOf<GoaDatabase>()

    private inner class Device(config: EloConfig = EloConfig.DEFAULT, contributor: FakeContributor? = null) {
        val db = GoaDatabase.inMemory(ApplicationProvider.getApplicationContext(), AndroidSQLiteDriver()).also { databases += it }
        val config = FakeEloConfigRepository(config)
        val players = RoomPlayerRepository(db.playerDao())
        val matches = RoomMatchRepository(db.matchDao())
        val drafts = RoomDraftRecordRepository(db.draftRecordDao())
        val backup = RoomBackupRepository(db, this.config, setOfNotNull(contributor))
    }

    @After
    fun tearDown() = databases.forEach { it.close() }

    private val ann = Player("6f1c-ann", "Ann \"the Wall\" Ø", 0xFF3FC1B0, avatarId = 23, createdAt = 1_700_000_000_000)
    private val bob = Player("6f1c-bob", "Bob", 0xFFE26459, avatarId = 0, createdAt = 1_700_000_000_001, archived = true)
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
        participants = listOf(
            Participant(ann.id, "hero-1", Team.RED),
            Participant(ann.id, "hero-2", Team.RED, handicap = true),
            Participant(bob.id, null, Team.BLUE),
            Participant(cid.id, "hero-3", Team.BLUE),
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
        pool = listOf("hero-1", "hero-2", "hero-3", "hero-4"),
        teamA = Team.BLUE,
        steps = listOf(
            DraftStepRecord(Team.BLUE, DraftAction.BAN, "hero-4"),
            DraftStepRecord(Team.BLUE, DraftAction.PICK, "hero-3"),
            DraftStepRecord(Team.RED, DraftAction.PICK, "hero-1"),
        ),
        matchId = "m-a",
    )

    private suspend fun Device.seed() {
        listOf(ann, bob, cid).forEach { players.upsert(it) }
        matches.upsert(matchA)
        matches.upsert(matchB)
        drafts.save(draft)
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
        assertThat(BackupCodec.encode(second.copy(exportedAt = 0))).isEqualTo(BackupCodec.encode(first.copy(exportedAt = 0)))
        assertThat(target.config.config.first()).isEqualTo(custom)
        assertThat(target.matches.observeMatch("m-a").first()).isEqualTo(matchA)
        assertThat(target.players.observePlayers(includeArchived = true).first()).containsExactly(ann, bob, cid)
        assertThat(document.summary()).isEqualTo(
            BackupSummary(1, document.exportedAt, players = 3, matches = 2, drafts = 1, hasEloConfig = true, extras = setOf("timer-presets")),
        )
    }

    @Test
    fun replaceWipesLocalDataAndResetsConfig() = runTest {
        val source = Device()
        source.players.upsert(ann)
        val document = source.backup.export()

        val target = Device(EloConfig(kEstablished = 10.0))
        target.seed()
        target.backup.import(document, ImportMode.REPLACE)

        assertThat(target.players.observePlayers(includeArchived = true).first()).containsExactly(ann)
        assertThat(target.matches.observeMatches().first()).isEmpty()
        assertThat(target.drafts.observeDrafts().first()).isEmpty()
        assertThat(target.config.config.first()).isEqualTo(EloConfig.DEFAULT)
    }

    @Test
    fun mergeKeepsLocalDataOverwritesSameIdsAndKeepsLocalConfig() = runTest {
        val source = Device(EloConfig(kEstablished = 10.0))
        source.players.upsert(ann.copy(name = "Ann (renamed)"))
        source.matches.upsert(matchB.copy(notes = "from backup"))
        val document = source.backup.export()

        val local = EloConfig(heroInfluence = 0.1)
        val target = Device(local)
        target.seed()
        target.backup.import(document, ImportMode.MERGE)

        val players = target.players.observePlayers(includeArchived = true).first()
        assertThat(players.map { it.name }).containsExactly("Ann (renamed)", "Bob", "Cid")
        assertThat(target.matches.observeMatches().first()).hasSize(2)
        assertThat(target.matches.observeMatch("m-b").first()!!.notes).isEqualTo("from backup")
        assertThat(target.config.config.first()).isEqualTo(local)
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
        // Unknown keys from a newer minor version are ignored.
        val doc = BackupCodec.decode("""{"format":"goa-companion-backup","version":1,"exportedAt":5,"future":true}""")
        assertThat(doc.exportedAt).isEqualTo(5)
    }
}
