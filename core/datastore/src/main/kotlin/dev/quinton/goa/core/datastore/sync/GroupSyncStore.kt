package dev.quinton.goa.core.datastore.sync

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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
 * This phone's group-sync state (ADR-025), in its own DataStore file (`datastore/group_sync`),
 * apart from the app settings: the group joined (id, key, name), this install's id and name,
 * "who am I" and the phones last synced with. None of it is synced or put in a backup file.
 * Values are plain strings; core:data owns their meaning.
 */
class GroupSyncStore(private val store: DataStore<Preferences>) {
    val prefs: Flow<GroupSyncPrefs> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map(::read)
        .distinctUntilChanged()

    /** Atomically applies [transform] and saves the result. */
    suspend fun edit(transform: (GroupSyncPrefs) -> GroupSyncPrefs) {
        store.edit { prefs -> write(prefs, transform(read(prefs))) }
    }

    private object Keys {
        val GROUP_ID = stringPreferencesKey("group_id")
        val GROUP_KEY = stringPreferencesKey("group_key")
        val GROUP_NAME = stringPreferencesKey("group_name")
        val DEVICE_ID = stringPreferencesKey("device_id")
        val DEVICE_NAME = stringPreferencesKey("device_name")
        val ME = stringPreferencesKey("me_player_id")
        val PHONES = stringPreferencesKey("phones_json")
        val LAST_SYNC = longPreferencesKey("last_sync_at")
    }

    private fun read(prefs: Preferences) = GroupSyncPrefs(
        groupId = prefs[Keys.GROUP_ID],
        groupKey = prefs[Keys.GROUP_KEY],
        groupName = prefs[Keys.GROUP_NAME],
        deviceId = prefs[Keys.DEVICE_ID],
        deviceName = prefs[Keys.DEVICE_NAME],
        mePlayerId = prefs[Keys.ME],
        phonesJson = prefs[Keys.PHONES],
        lastSyncAt = prefs[Keys.LAST_SYNC],
    )

    private fun write(prefs: MutablePreferences, value: GroupSyncPrefs) {
        prefs.set(Keys.GROUP_ID, value.groupId)
        prefs.set(Keys.GROUP_KEY, value.groupKey)
        prefs.set(Keys.GROUP_NAME, value.groupName)
        prefs.set(Keys.DEVICE_ID, value.deviceId)
        prefs.set(Keys.DEVICE_NAME, value.deviceName)
        prefs.set(Keys.ME, value.mePlayerId)
        prefs.set(Keys.PHONES, value.phonesJson)
        prefs.set(Keys.LAST_SYNC, value.lastSyncAt)
    }

    private fun <T> MutablePreferences.set(key: Preferences.Key<T>, value: T?) {
        if (value == null) remove(key) else this[key] = value
    }

    companion object {
        const val FILE_NAME = "group_sync"
    }
}

/** Raw group-sync values; null = not set. */
data class GroupSyncPrefs(
    val groupId: String? = null,
    val groupKey: String? = null,
    val groupName: String? = null,
    val deviceId: String? = null,
    val deviceName: String? = null,
    val mePlayerId: String? = null,
    val phonesJson: String? = null,
    val lastSyncAt: Long? = null,
)

@Module
@InstallIn(SingletonComponent::class)
internal object GroupSyncStoreModule {
    @Provides
    @Singleton
    fun groupSyncStore(@ApplicationContext context: Context): GroupSyncStore = GroupSyncStore(
        PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            produceFile = { context.preferencesDataStoreFile(GroupSyncStore.FILE_NAME) },
        ),
    )
}
