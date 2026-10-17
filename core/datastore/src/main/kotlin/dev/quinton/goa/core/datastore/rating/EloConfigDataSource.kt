package dev.quinton.goa.core.datastore.rating

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
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
 * Persists the Elo constants as a JSON document (encoded/decoded by core:data, which owns the
 * `EloConfig` type from core:domain). Lives in its own DataStore file, `datastore/elo_config.preferences_pb`,
 * which Android Auto Backup includes. A missing value means "defaults".
 */
@Singleton
class EloConfigDataSource @Inject constructor(@EloConfigStore private val store: DataStore<Preferences>) {
    val json: Flow<String?> = store.data.map { it[KEY] }

    /**
     * When the constants were last changed (epoch ms), for group sync (ADR-025); null for configs
     * stored before sync existed.
     */
    val updatedAt: Flow<Long?> = store.data.map { it[UPDATED_AT] }

    /** Writes the config and its change time together. */
    suspend fun write(json: String?, updatedAt: Long? = null) {
        store.edit { prefs ->
            if (json == null) prefs.remove(KEY) else prefs[KEY] = json
            if (updatedAt == null) prefs.remove(UPDATED_AT) else prefs[UPDATED_AT] = updatedAt
        }
    }

    private companion object {
        val KEY = stringPreferencesKey("elo_config_json")
        val UPDATED_AT = longPreferencesKey("elo_config_updated_at")
    }
}

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class EloConfigStore

@Module
@InstallIn(SingletonComponent::class)
object EloConfigStoreModule {
    @Provides
    @Singleton
    @EloConfigStore
    fun store(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(produceFile = { context.preferencesDataStoreFile("elo_config") })
}
