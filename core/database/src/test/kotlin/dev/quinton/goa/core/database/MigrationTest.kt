package dev.quinton.goa.core.database

import androidx.room3.testing.MigrationTestHelper
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.database.dao.MatchDao
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Migration baseline: schema v1 is exported to `core/database/schemas` and must open cleanly.
 * Every future version bump adds a `migrate_N_to_N+1` test here (ARCHITECTURE.md).
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

    @Test
    fun schemaV1CreatesAndRoomOpensIt() = runTest {
        helper.createDatabase(1).apply {
            execSQL(
                "INSERT INTO matches (id, created_at, played_at, status, mode, map, winner, win_condition, " +
                    "duration_min, notes, draft_id) " +
                    "VALUES ('m1', 1, 2, 'completed', 'quick', NULL, 'red', NULL, NULL, '', NULL)",
            )
            execSQL(
                "INSERT INTO participants (match_id, seat_index, player_id, hero_id, team, handicap) " +
                    "VALUES ('m1', 0, 'p1', NULL, 'red', 0)",
            )
            close()
        }
        val db = androidx.room3.Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            GoaDatabase::class.java,
            dbFile.absolutePath,
        ).setDriver(AndroidSQLiteDriver()).build()
        helper.closeWhenFinished(db)
        val dao: MatchDao = db.matchDao()
        val match = dao.getAll().single().toModel()
        assertThat(match.id).isEqualTo("m1")
        assertThat(match.participants.single().heroId).isNull()
        assertThat(GoaDatabase.VERSION).isEqualTo(1)
    }
}
