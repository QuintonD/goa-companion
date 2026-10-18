package dev.quinton.goa.core.datastore.trivia

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Card-trivia practice record (ADR-028) as JSON documents whose schema lives in core:domain
 * (`FactProgress`, `TriviaConfig`; encoded/decoded by core:data). Its own DataStore file,
 * `datastore/trivia_progress.preferences_pb`, which Android Auto Backup includes; never synced
 * and not in the backup file.
 */
@Singleton
class TriviaProgressStore @Inject constructor(@TriviaStore private val store: DataStore<Preferences>) {
    /** JSON object of fact key to progress, or null before the first answer. */
    val progressJson: Flow<String?> = store.data.map { it[PROGRESS] }

    /** JSON of the last quiz setup, or null before the first quiz. */
    val configJson: Flow<String?> = store.data.map { it[CONFIG] }

    /** Who the next round is played as: a player id or "guest" (ADR-031); null before the first pick. */
    val playingAs: Flow<String?> = store.data.map { it[PLAYING_AS] }

    suspend fun writePlayingAs(value: String) {
        store.edit { prefs -> prefs[PLAYING_AS] = value }
    }

    /**
     * Rewrites the progress document in one transaction: [transform] gets the stored JSON (or null)
     * and returns the new one (null removes it).
     */
    suspend fun updateProgress(transform: (String?) -> String?) {
        store.edit { prefs ->
            val next = transform(prefs[PROGRESS])
            if (next == null) prefs.remove(PROGRESS) else prefs[PROGRESS] = next
        }
    }

    suspend fun writeConfig(json: String?) {
        store.edit { prefs -> if (json == null) prefs.remove(CONFIG) else prefs[CONFIG] = json }
    }

    private companion object {
        val PROGRESS = stringPreferencesKey("trivia_progress_json")
        val CONFIG = stringPreferencesKey("trivia_last_config_json")
        val PLAYING_AS = stringPreferencesKey("trivia_playing_as")
    }
}

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class TriviaStore

@Module
@InstallIn(SingletonComponent::class)
object TriviaStoreModule {
    @Provides
    @Singleton
    @TriviaStore
    fun store(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(produceFile = { context.preferencesDataStoreFile("trivia_progress") })
}
