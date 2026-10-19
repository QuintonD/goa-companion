package dev.quinton.goa.core.datastore.board

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
 * Board-sandbox boards (ADR-033) as one JSON document per board, keyed by board id, in their own
 * DataStore file `datastore/boards.preferences_pb`. The schema (`SavedBoard`, core:model) is
 * encoded and decoded by core:data. One key per board: an edit rewrites only that board's
 * document, and a corrupt document loses only that board. Local only: never synced.
 */
@Singleton
class BoardStore @Inject constructor(@BoardsDataStore private val store: DataStore<Preferences>) {
    /** Board id → its JSON document. */
    val documents: Flow<Map<String, String>> = store.data.map { prefs ->
        buildMap {
            prefs.asMap().forEach { (key, value) ->
                if (key.name.startsWith(PREFIX) && value is String) put(key.name.removePrefix(PREFIX), value)
            }
        }
    }

    suspend fun write(id: String, json: String) {
        store.edit { prefs -> prefs[key(id)] = json }
    }

    suspend fun delete(id: String) {
        store.edit { prefs -> prefs.remove(key(id)) }
    }

    /**
     * Rewrites every board in one transaction: [transform] gets the stored documents and returns
     * the documents to keep (ids missing from the result are removed).
     */
    suspend fun update(transform: (Map<String, String>) -> Map<String, String>) {
        store.edit { prefs ->
            val current = buildMap {
                prefs.asMap().forEach { (key, value) ->
                    if (key.name.startsWith(PREFIX) && value is String) put(key.name.removePrefix(PREFIX), value)
                }
            }
            val next = transform(current)
            (current.keys - next.keys).forEach { prefs.remove(key(it)) }
            next.forEach { (id, json) -> if (current[id] != json) prefs[key(id)] = json }
        }
    }

    private fun key(id: String) = stringPreferencesKey(PREFIX + id)

    private companion object {
        const val PREFIX = "board:"
    }
}

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BoardsDataStore

@Module
@InstallIn(SingletonComponent::class)
object BoardStoreModule {
    @Provides
    @Singleton
    @BoardsDataStore
    fun store(@ApplicationContext context: Context): DataStore<Preferences> =
        boardsDataStore(CoroutineScope(Dispatchers.IO + SupervisorJob())) { context.preferencesDataStoreFile("boards") }

    /**
     * The boards file. A file that can't be read at all is replaced by an empty one (like the
     * settings and sync stores): losing the sandbox's boards beats a crash on every start.
     */
    fun boardsDataStore(scope: CoroutineScope, file: () -> File): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            scope = scope,
            produceFile = file,
        )
}
