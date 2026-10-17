package dev.quinton.goa.core.datastore.timer

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Raw persisted timer data (ADR-007): the running session snapshot and the user's presets,
 * stored as JSON documents encoded by `core:domain`'s `TimerJson`. Kept in its own DataStore
 * file so the timer can be restored by broadcast receivers after process death or reboot.
 */
interface TimerStateStore {
    /** The active session snapshot, or null when no session runs. */
    val snapshotJson: Flow<String?>

    /** Saved presets, or null before the user has saved anything (= bundled defaults). */
    val presetsJson: Flow<String?>

    val selectedPresetId: Flow<String?>

    suspend fun currentSnapshotJson(): String? = snapshotJson.first()

    /** Atomically transforms the snapshot; return null to clear it. */
    suspend fun updateSnapshot(transform: (String?) -> String?): String?

    suspend fun writePresets(json: String?)

    suspend fun writeSelectedPresetId(id: String?)
}

@Singleton
class DataStoreTimerStateStore internal constructor(private val store: DataStore<Preferences>) : TimerStateStore {
    @Inject constructor(@ApplicationContext context: Context) : this(
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile(FILE_NAME) },
    )

    override val snapshotJson: Flow<String?> = store.data.map { it[SNAPSHOT] }
    override val presetsJson: Flow<String?> = store.data.map { it[PRESETS] }
    override val selectedPresetId: Flow<String?> = store.data.map { it[SELECTED] }

    override suspend fun updateSnapshot(transform: (String?) -> String?): String? {
        var result: String? = null
        store.edit { prefs ->
            result = transform(prefs[SNAPSHOT])
            val value = result
            if (value == null) prefs.remove(SNAPSHOT) else prefs[SNAPSHOT] = value
        }
        return result
    }

    override suspend fun writePresets(json: String?) {
        store.edit { prefs -> if (json == null) prefs.remove(PRESETS) else prefs[PRESETS] = json }
    }

    override suspend fun writeSelectedPresetId(id: String?) {
        store.edit { prefs -> if (id == null) prefs.remove(SELECTED) else prefs[SELECTED] = id }
    }

    companion object {
        const val FILE_NAME = "timer"
        private val SNAPSHOT = stringPreferencesKey("snapshot")
        private val PRESETS = stringPreferencesKey("presets")
        private val SELECTED = stringPreferencesKey("selected_preset")

        /** For tests: a store backed by an arbitrary file. */
        fun forFile(produceFile: () -> java.io.File): DataStoreTimerStateStore =
            DataStoreTimerStateStore(PreferenceDataStoreFactory.create(produceFile = produceFile))
    }
}
