package dev.quinton.goa.core.datastore.game

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
 * Games against the computer in progress (ADR-035) as one JSON document per game, keyed by game id,
 * in their own DataStore file `datastore/saved_games.preferences_pb`. The schema (`StoredGame`,
 * core:data) is encoded and decoded by core:data. One key per game: an autosave rewrites only that
 * game's document, and a corrupt document loses only that game. Local only: never synced, never in
 * the backup file.
 */
@Singleton
class SavedGameStore @Inject constructor(@SavedGamesDataStore private val store: DataStore<Preferences>) {
    /** Game id → its JSON document. */
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

    private fun key(id: String) = stringPreferencesKey(PREFIX + id)

    private companion object {
        const val PREFIX = "game:"
    }
}

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SavedGamesDataStore

@Module
@InstallIn(SingletonComponent::class)
object SavedGameStoreModule {
    @Provides
    @Singleton
    @SavedGamesDataStore
    fun store(@ApplicationContext context: Context): DataStore<Preferences> =
        savedGamesDataStore(CoroutineScope(Dispatchers.IO + SupervisorJob())) {
            context.preferencesDataStoreFile("saved_games")
        }

    /** The games file. A file that can't be read at all is replaced by an empty one: never a crash on start. */
    fun savedGamesDataStore(scope: CoroutineScope, file: () -> File): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            scope = scope,
            produceFile = file,
        )
}
