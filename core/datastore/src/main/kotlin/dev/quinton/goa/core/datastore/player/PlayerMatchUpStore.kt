package dev.quinton.goa.core.datastore.player

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.IOException
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * "Same people?" answers (ADR-031) in their own DataStore file (`datastore/player_matchup`): the
 * pairs of players this phone was told are different people, as opaque pair keys (core:domain's
 * `PlayerMatchUp.pairKey`). This phone's own: never synced and not in the backup file.
 */
class PlayerMatchUpStore(private val store: DataStore<Preferences>) {
    val differentPairs: Flow<Set<String>> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it[DIFFERENT].orEmpty() }
        .distinctUntilChanged()

    suspend fun addDifferent(pairKey: String) {
        store.edit { it[DIFFERENT] = it[DIFFERENT].orEmpty() + pairKey }
    }

    suspend fun clearDifferent() {
        store.edit { it.remove(DIFFERENT) }
    }

    companion object {
        const val FILE_NAME = "player_matchup"
        private val DIFFERENT = stringSetPreferencesKey("different_pairs")
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal object PlayerMatchUpStoreModule {
    @Provides
    @Singleton
    fun playerMatchUpStore(@ApplicationContext context: Context): PlayerMatchUpStore = PlayerMatchUpStore(
        PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            produceFile = { context.preferencesDataStoreFile(PlayerMatchUpStore.FILE_NAME) },
        ),
    )
}
