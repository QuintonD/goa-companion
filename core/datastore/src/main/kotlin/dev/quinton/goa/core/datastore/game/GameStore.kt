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
 * The game against the computer in progress (ADR-035), as one JSON document in its own DataStore
 * file `datastore/games.preferences_pb`. The schema is encoded and decoded by core:data. Local only:
 * never synced and not in the backup file. One game at a time: starting a new one replaces it.
 */
@Singleton
class GameStore @Inject constructor(@GamesDataStore private val store: DataStore<Preferences>) {
    /** The stored document, or null when no game is in progress. */
    val current: Flow<String?> = store.data.map { it[CURRENT] }

    suspend fun write(json: String) {
        store.edit { it[CURRENT] = json }
    }

    suspend fun clear() {
        store.edit { it.remove(CURRENT) }
    }

    private companion object {
        val CURRENT = stringPreferencesKey("game:current")
    }
}

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GamesDataStore

@Module
@InstallIn(SingletonComponent::class)
object GameStoreModule {
    @Provides
    @Singleton
    @GamesDataStore
    fun store(@ApplicationContext context: Context): DataStore<Preferences> =
        gamesDataStore(CoroutineScope(Dispatchers.IO + SupervisorJob())) { context.preferencesDataStoreFile("games") }

    /** The games file. A file that can't be read at all is replaced by an empty one, never a crash. */
    fun gamesDataStore(scope: CoroutineScope, file: () -> File): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            scope = scope,
            produceFile = file,
        )
}
