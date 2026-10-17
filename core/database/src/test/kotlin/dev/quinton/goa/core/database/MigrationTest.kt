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
        assertThat(GoaDatabase.VERSION).isEqualTo(2)
    }
}
