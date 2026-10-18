package dev.quinton.goa.core.domain.backup

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.domain.rating.EloConfig
import dev.quinton.goa.core.domain.sync.StampedEloConfig
import dev.quinton.goa.core.domain.sync.SyncDocument
import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.core.model.match.Participant
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.core.model.settings.AppSettings
import dev.quinton.goa.core.model.settings.ThemeMode
import dev.quinton.goa.core.model.sync.Deletion
import dev.quinton.goa.core.model.sync.DeletionKind
import dev.quinton.goa.core.model.sync.PlayerMerge
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertThrows
import org.junit.Test

/** The backup file's codec and the export/merge rules shared by the phones and the hub (moved from core:data). */
class BackupCodecTest {
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
            playerMerges = listOf(PlayerMerge("a", "ghost", 1)),
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
    fun unknownKeysAreIgnored() {
        // Unknown keys from a newer minor version are ignored.
        val doc = BackupCodec.decode("""{"format":"goa-companion-backup","version":1,"exportedAt":5,"future":true}""")
        assertThat(doc.exportedAt).isEqualTo(5)
    }

    @Test
    fun exportCarriesRecordsTombstonesMergesAndOnlyChangedConstants() {
        val ann = Player("ann", "Ann", 1, 0, 0, updatedAt = 5)
        val bob = Player("bob", "Bob", 1, 0, 0, updatedAt = 6)
        val data = SyncDocument(
            players = listOf(ann, bob),
            deletions = listOf(Deletion(DeletionKind.MATCH, "gone", 9)),
            playerMerges = listOf(PlayerMerge("bob", "ann", 7)),
        )
        val plain = BackupFiles.export(data, exportedAt = 42, appVersion = "web")
        assertThat(plain.version).isEqualTo(BackupDocument.CURRENT_VERSION)
        assertThat(plain.players).containsExactly(ann, bob).inOrder()
        assertThat(plain.deletions).isEqualTo(data.deletions)
        assertThat(plain.playerMerges).isEqualTo(data.playerMerges)
        assertThat(plain.eloConfig).isNull()
        assertThat(plain.eloConfigUpdatedAt).isNull()

        val tuned = EloConfig(kEstablished = 30.0)
        val custom = BackupFiles.export(data.copy(eloConfig = StampedEloConfig(tuned, 11)), 42, null)
        assertThat(custom.eloConfig).isEqualTo(tuned)
        assertThat(custom.eloConfigUpdatedAt).isEqualTo(11)
        // Changed back to the defaults: the file says when, not what.
        val reset = BackupFiles.export(data.copy(eloConfig = StampedEloConfig(EloConfig.DEFAULT, 12)), 42, null)
        assertThat(reset.eloConfig).isNull()
        assertThat(reset.eloConfigUpdatedAt).isEqualTo(12)
    }

    @Test
    fun mergeKeepsTheLaterEditOfEachRecordLikeASync() {
        val local = SyncDocument(players = listOf(Player("ann", "Ann", 1, 0, 0, updatedAt = 10)))
        val file = BackupDocument(
            exportedAt = 1,
            players = listOf(
                Player("ann", "Anna", 1, 0, 0, updatedAt = 5),
                Player("cid", "Cid", 1, 0, 0, updatedAt = 3),
            ),
        )
        val merged = BackupFiles.merge(local, file)
        assertThat(merged.players.map { it.name }).containsExactly("Ann", "Cid")
        val newer = file.copy(players = listOf(Player("ann", "Anna", 1, 0, 0, updatedAt = 20)))
        assertThat(BackupFiles.merge(local, newer).players.single().name).isEqualTo("Anna")
    }

    @Test
    fun restoredConstantsKeepTheFilesChangeTime() {
        val file = BackupDocument(exportedAt = 1, eloConfig = EloConfig(kEstablished = 30.0))
        assertThat(BackupFiles.restoredEloConfig(file)).isEqualTo(StampedEloConfig(EloConfig(kEstablished = 30.0), 1))
        assertThat(BackupFiles.restoredEloConfig(file.copy(eloConfigUpdatedAt = 77)).updatedAt).isEqualTo(77)
        assertThat(BackupFiles.restoredEloConfig(BackupDocument(exportedAt = 1))).isEqualTo(StampedEloConfig.UNSET)
    }

    @Test
    fun settingsExtraLeavesDefaultsOutAndReadsBack() {
        val json = BackupFiles.settingsJson(AppSettings(theme = ThemeMode.LIGHT, soundsEnabled = false)) as JsonObject
        assertThat(json.keys).containsExactly("theme", "soundsEnabled")
        assertThat(BackupFiles.settings(json)).isEqualTo(AppSettings(theme = ThemeMode.LIGHT, soundsEnabled = false))
        assertThat(BackupFiles.settings(JsonObject(mapOf("theme" to kotlinx.serialization.json.JsonPrimitive("NEON")))))
            .isEqualTo(AppSettings())
    }
}

/** 1970-01-01 12:00 UTC: the same calendar day in every time zone from UTC−11 to UTC+11. */
private const val NOON_UTC = 43_200_000L
