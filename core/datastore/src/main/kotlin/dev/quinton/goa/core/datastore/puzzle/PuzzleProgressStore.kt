package dev.quinton.goa.core.datastore.puzzle

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Puzzle progress (ADR-035, PRODUCT §10): one JSON document (puzzle id to solved / attempts /
 * nudges used / solution shown) in its own DataStore file `datastore/puzzle_progress.preferences_pb`.
 * The schema is encoded and decoded by core:data. Local only: never synced and not in the backup
 * file (Android Auto Backup still covers it).
 */
@Singleton
class PuzzleProgressStore @Inject constructor(@PuzzleStore private val store: DataStore<Preferences>) {
    /** The stored document, or null before the first puzzle was tried. */
    val progressJson: Flow<String?> = store.data.map { it[PROGRESS] }

    /** Rewrites the document in one transaction: [transform] gets the stored JSON (null removes it). */
    suspend fun update(transform: (String?) -> String?) {
        store.edit { prefs ->
            val next = transform(prefs[PROGRESS])
            if (next == null) prefs.remove(PROGRESS) else prefs[PROGRESS] = next
        }
    }

    private companion object {
        val PROGRESS = stringPreferencesKey("puzzle_progress_json")
    }
}

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PuzzleStore

@Module
@InstallIn(SingletonComponent::class)
object PuzzleStoreModule {
    @Provides
    @Singleton
    @PuzzleStore
    fun store(@ApplicationContext context: Context): DataStore<Preferences> =
        puzzleDataStore(CoroutineScope(Dispatchers.IO + SupervisorJob())) {
            context.preferencesDataStoreFile("puzzle_progress")
        }

    /** The progress file. A file that can't be read at all is replaced by an empty one, never a crash. */
    fun puzzleDataStore(scope: CoroutineScope, file: () -> File): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            scope = scope,
            produceFile = file,
        )
}
