package dev.quinton.goa.core.database

import androidx.room3.testing.MigrationTestHelper
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.database.dao.MatchDao
import dev.quinton.goa.core.model.match.Team
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Every exported schema (`core/database/schemas`) must migrate to the current version without
 * losing data. Each version bump adds a `migrate_N_to_N+1` test here (ARCHITECTURE.md).
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    private val dbFile: File = ApplicationProvider.getApplicationContext<android.content.Context>()
        .getDatabasePath("migration-test.db")

    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation = InstrumentationRegistry.getInstrumentation(),
        file = dbFile,
        driver = AndroidSQLiteDriver(),
        databaseClass = GoaDatabase::class,
    )

    private suspend fun seedV1() {
        helper.createDatabase(1).apply {
            execSQL(
                "INSERT INTO matches (id, created_at, played_at, status, mode, map, winner, win_condition, " +
                    "duration_min, notes, draft_id) " +
                    "VALUES ('m1', 1, 2, 'completed', 'quick', NULL, 'red', NULL, NULL, '', 'd1')",
            )
            execSQL(
                "INSERT INTO participants (match_id, seat_index, player_id, hero_id, team, handicap) " +
                    "VALUES ('m1', 0, 'p1', NULL, 'red', 0)",
            )
            close()
        }
    }

    private fun openCurrent(): GoaDatabase = androidx.room3.Room.databaseBuilder(
        ApplicationProvider.getApplicationContext(),
        GoaDatabase::class.java,
        dbFile.absolutePath,
    ).addMigrations(*GoaDatabase.MIGRATIONS.toTypedArray()).setDriver(AndroidSQLiteDriver()).build()
        .also { helper.closeWhenFinished(it) }

    @Test
    fun migrate1To2AddsAnEmptyTieBreakerAndKeepsTheMatch() = runTest {
        seedV1()
        helper.runMigrationsAndValidate(2, listOf(GoaDatabase.MIGRATION_1_2)).use { connection ->
            connection.prepare("SELECT tie_breaker, draft_id FROM matches WHERE id = 'm1'").use {
                assertThat(it.step()).isTrue()
                assertThat(it.isNull(0)).isTrue()
                assertThat(it.getText(1)).isEqualTo("d1")
            }
        }
    }

    @Test
    fun v1DatabaseOpensAtTheCurrentVersionAndStoresTheCoin() = runTest {
        seedV1()
        val dao: MatchDao = openCurrent().matchDao()
        val match = dao.getAll().single().toModel()
        assertThat(match.id).isEqualTo("m1")
        assertThat(match.winner).isEqualTo(Team.RED)
        assertThat(match.participants.single().heroId).isNull()
        assertThat(match.tieBreaker).isNull()

        dao.upsert(match.copy(tieBreaker = Team.BLUE).toEntity(), match.participantEntities())
        assertThat(dao.getAll().single().toModel().tieBreaker).isEqualTo(Team.BLUE)
    }

    private suspend fun seedV2() {
        helper.createDatabase(2).apply {
            execSQL(
                "INSERT INTO players (id, name, color, avatar_id, created_at, archived) " +
                    "VALUES ('p1', 'Ann', 1, 0, 500, 0)",
            )
            // Logged late (created after it was played) and pre-dated (played after it was created).
            execSQL(
                "INSERT INTO matches (id, created_at, played_at, status, mode, map, winner, win_condition, " +
                    "duration_min, notes, draft_id, tie_breaker) " +
                    "VALUES ('late', 900, 100, 'completed', 'quick', NULL, 'red', NULL, NULL, '', NULL, 'blue')",
            )
            execSQL(
                "INSERT INTO matches (id, created_at, played_at, status, mode, map, winner, win_condition, " +
                    "duration_min, notes, draft_id, tie_breaker) " +
                    "VALUES ('ahead', 100, 700, 'pending', 'long', NULL, NULL, NULL, NULL, '', 'd1', NULL)",
            )
            execSQL(
                "INSERT INTO participants (match_id, seat_index, player_id, hero_id, team, handicap) " +
                    "VALUES ('late', 0, 'p1', 'arien', 'red', 0)",
            )
            execSQL(
                "INSERT INTO draft_records (id, created_at, method_id, method_kind, pool, team_a, match_id) " +
                    "VALUES ('d1', 300, 'house', 'custom', '[\"arien\"]', 'red', 'ahead')",
            )
            close()
        }
    }

    @Test
    fun migrate2To3BackfillsUpdatedAtAndAddsEmptySyncTables() = runTest {
        seedV2()
        helper.runMigrationsAndValidate(3, listOf(GoaDatabase.MIGRATION_2_3)).use { connection ->
            fun long(sql: String): Long = connection.prepare(sql).use {
                assertThat(it.step()).isTrue()
                it.getLong(0)
            }
            assertThat(long("SELECT updated_at FROM players WHERE id = 'p1'")).isEqualTo(500)
            assertThat(long("SELECT updated_at FROM matches WHERE id = 'late'")).isEqualTo(900)
            assertThat(long("SELECT updated_at FROM matches WHERE id = 'ahead'")).isEqualTo(700)
            assertThat(long("SELECT updated_at FROM draft_records WHERE id = 'd1'")).isEqualTo(300)
            assertThat(long("SELECT COUNT(*) FROM deletions")).isEqualTo(0)
            assertThat(long("SELECT COUNT(*) FROM player_merges")).isEqualTo(0)
        }
    }

    @Test
    fun v1DatabaseOpensAtTheCurrentVersionWithEveryRowKept() = runTest {
        seedV1()
        val db = openCurrent()
        val match = db.matchDao().getAll().single().toModel()
        assertThat(match.id).isEqualTo("m1")
        assertThat(match.participants).hasSize(1)
        assertThat(match.updatedAt).isEqualTo(2) // max(created 1, played 2)
        assertThat(db.deletionDao().getAll()).isEmpty()
        assertThat(db.playerDao().getMerges()).isEmpty()
        assertThat(GoaDatabase.VERSION).isEqualTo(3)
    }
}
