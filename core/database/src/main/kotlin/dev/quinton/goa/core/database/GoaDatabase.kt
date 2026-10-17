package dev.quinton.goa.core.database

import android.content.Context
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import dev.quinton.goa.core.database.dao.DeletionDao
import dev.quinton.goa.core.database.dao.DraftRecordDao
import dev.quinton.goa.core.database.dao.MatchDao
import dev.quinton.goa.core.database.dao.PlayerDao
import dev.quinton.goa.core.database.entity.DeletionEntity
import dev.quinton.goa.core.database.entity.DraftRecordEntity
import dev.quinton.goa.core.database.entity.DraftStepEntity
import dev.quinton.goa.core.database.entity.MatchEntity
import dev.quinton.goa.core.database.entity.ParticipantEntity
import dev.quinton.goa.core.database.entity.PlayerEntity
import dev.quinton.goa.core.database.entity.PlayerMergeEntity
import kotlinx.coroutines.Dispatchers

/**
 * User data: players, the match log (source of truth for ratings, ADR-003) and draft records.
 * Ratings are never stored here; they are recomputed from the match log.
 */
@Database(
    entities = [
        PlayerEntity::class,
        MatchEntity::class,
        ParticipantEntity::class,
        DraftRecordEntity::class,
        DraftStepEntity::class,
        DeletionEntity::class,
        PlayerMergeEntity::class,
    ],
    version = GoaDatabase.VERSION,
    exportSchema = true,
)
abstract class GoaDatabase : RoomDatabase() {
    abstract fun playerDao(): PlayerDao

    abstract fun matchDao(): MatchDao

    abstract fun draftRecordDao(): DraftRecordDao

    abstract fun deletionDao(): DeletionDao

    companion object {
        const val VERSION = 3
        const val NAME = "goa.db"

        /**
         * v2: `matches.tie_breaker`, the team on the Tie Breaker coin at setup (ADR-016).
         * Existing matches keep null: the coin they were played with was never recorded.
         */
        val MIGRATION_1_2 = Migration(1, 2) { connection ->
            connection.execSQL("ALTER TABLE matches ADD COLUMN tie_breaker TEXT")
        }

        /**
         * v3 (group sync, ADR-025): `updated_at` on players, matches and drafts, backfilled to when
         * each row was last known to change (players and drafts: created; matches: created or
         * played, whichever is later), plus the `deletions` tombstones and `player_merges`.
         */
        val MIGRATION_2_3 = Migration(2, 3) { connection ->
            connection.execSQL("ALTER TABLE players ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0")
            connection.execSQL("UPDATE players SET updated_at = created_at")
            connection.execSQL("ALTER TABLE matches ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0")
            connection.execSQL("UPDATE matches SET updated_at = MAX(created_at, played_at)")
            connection.execSQL("ALTER TABLE draft_records ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0")
            connection.execSQL("UPDATE draft_records SET updated_at = created_at")
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `deletions` (`kind` TEXT NOT NULL, `id` TEXT NOT NULL, " +
                    "`deleted_at` INTEGER NOT NULL, PRIMARY KEY(`kind`, `id`))",
            )
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `player_merges` (`from_id` TEXT NOT NULL, `to_id` TEXT NOT NULL, " +
                    "`merged_at` INTEGER NOT NULL, PRIMARY KEY(`from_id`))",
            )
        }

        /** Every schema migration, oldest first. Never destructive: the match log is the source of truth. */
        val MIGRATIONS: List<Migration> = listOf(MIGRATION_1_2, MIGRATION_2_3)

        /** On-disk database with the bundled SQLite driver (same SQLite on every device). */
        fun create(context: Context, driver: SQLiteDriver = BundledSQLiteDriver()): GoaDatabase =
            Room.databaseBuilder(context, GoaDatabase::class.java, NAME)
                .addMigrations(*MIGRATIONS.toTypedArray())
                .setDriver(driver)
                .setQueryCoroutineContext(Dispatchers.IO)
                .build()

        /** In-memory database for tests. */
        fun inMemory(context: Context, driver: SQLiteDriver): GoaDatabase =
            Room.inMemoryDatabaseBuilder(context, GoaDatabase::class.java)
                .setDriver(driver)
                .build()
    }
}
