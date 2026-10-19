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
import dev.quinton.goa.core.database.dao.TriviaRoundDao
import dev.quinton.goa.core.database.entity.DeletionEntity
import dev.quinton.goa.core.database.entity.DraftRecordEntity
import dev.quinton.goa.core.database.entity.DraftStepEntity
import dev.quinton.goa.core.database.entity.MatchEntity
import dev.quinton.goa.core.database.entity.ParticipantEntity
import dev.quinton.goa.core.database.entity.PlayerEntity
import dev.quinton.goa.core.database.entity.PlayerMergeEntity
import dev.quinton.goa.core.database.entity.TriviaRoundEntity
import kotlinx.coroutines.Dispatchers

/**
 * User data: players, the match log (source of truth for ratings, ADR-003), draft records and
 * trivia rounds (ADR-032).
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
        TriviaRoundEntity::class,
    ],
    version = GoaDatabase.VERSION,
    exportSchema = true,
)
abstract class GoaDatabase : RoomDatabase() {
    abstract fun playerDao(): PlayerDao

    abstract fun matchDao(): MatchDao

    abstract fun draftRecordDao(): DraftRecordDao

    abstract fun deletionDao(): DeletionDao

    abstract fun triviaRoundDao(): TriviaRoundDao

    companion object {
        const val VERSION = 4
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

        /**
         * v4 (trivia leaderboard, ADR-032): the empty `trivia_rounds` table. Its tombstones go in
         * `deletions` with kind `trivia_round`, so that table is unchanged.
         */
        val MIGRATION_3_4 = Migration(3, 4) { connection ->
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `trivia_rounds` (`id` TEXT NOT NULL, `player_id` TEXT NOT NULL, " +
                    "`kind` TEXT NOT NULL, `played_at` INTEGER NOT NULL, `length` INTEGER NOT NULL, " +
                    "`questions` INTEGER NOT NULL, `correct` INTEGER NOT NULL, `best_streak` INTEGER NOT NULL, " +
                    "`answer_millis` INTEGER NOT NULL, `recall` INTEGER NOT NULL, `hero_count` INTEGER NOT NULL, " +
                    "`catalog_version` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_trivia_rounds_player_id` ON `trivia_rounds` (`player_id`)",
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_trivia_rounds_played_at` ON `trivia_rounds` (`played_at`)",
            )
        }

        /** Every schema migration, oldest first. Never destructive: the match log is the source of truth. */
        val MIGRATIONS: List<Migration> = listOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)

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
