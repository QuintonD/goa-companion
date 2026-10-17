package dev.quinton.goa.core.database

import android.content.Context
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.quinton.goa.core.database.dao.DraftRecordDao
import dev.quinton.goa.core.database.dao.MatchDao
import dev.quinton.goa.core.database.dao.PlayerDao
import dev.quinton.goa.core.database.entity.DraftRecordEntity
import dev.quinton.goa.core.database.entity.DraftStepEntity
import dev.quinton.goa.core.database.entity.MatchEntity
import dev.quinton.goa.core.database.entity.ParticipantEntity
import dev.quinton.goa.core.database.entity.PlayerEntity
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
    ],
    version = GoaDatabase.VERSION,
    exportSchema = true,
)
abstract class GoaDatabase : RoomDatabase() {
    abstract fun playerDao(): PlayerDao

    abstract fun matchDao(): MatchDao

    abstract fun draftRecordDao(): DraftRecordDao

    companion object {
        const val VERSION = 1
        const val NAME = "goa.db"

        /** On-disk database with the bundled SQLite driver (same SQLite on every device). */
        fun create(context: Context, driver: SQLiteDriver = BundledSQLiteDriver()): GoaDatabase =
            Room.databaseBuilder(context, GoaDatabase::class.java, NAME)
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
