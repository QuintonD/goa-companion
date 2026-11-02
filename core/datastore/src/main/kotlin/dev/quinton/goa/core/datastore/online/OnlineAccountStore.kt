package dev.quinton.goa.core.datastore.online

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.io.IOException
import javax.inject.Singleton
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first

/** This device's online account (ADR-056). The token never goes in a log, a backup or an export. */
data class OnlineAccountPrefs(val accountId: String, val token: String, val name: String)

/**
 * The online account, in its own DataStore file under `noBackupFilesDir`: Android never backs that
 * directory up, whatever `backup_rules.xml` and `data_extraction_rules.xml` say. A token copied to a
 * new phone would keep working on both; moving to a new phone is what the recovery code is for
 * (docs/ONLINE.md). No `BackupContributor` reads it, so it isn't in the backup file either.
 */
class OnlineAccountStore(private val store: DataStore<Preferences>) {
    suspend fun load(): OnlineAccountPrefs? {
        val prefs = store.data.catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }.first()
        val id = prefs[Keys.ACCOUNT_ID] ?: return null
        val token = prefs[Keys.TOKEN] ?: return null
        return OnlineAccountPrefs(id, token, prefs[Keys.NAME].orEmpty())
    }

    suspend fun save(account: OnlineAccountPrefs) {
        store.edit {
            it[Keys.ACCOUNT_ID] = account.accountId
            it[Keys.TOKEN] = account.token
            it[Keys.NAME] = account.name
        }
    }

    suspend fun clear() {
        store.edit { it.clear() }
    }

    private object Keys {
        val ACCOUNT_ID = stringPreferencesKey("account_id")
        val TOKEN = stringPreferencesKey("token")
        val NAME = stringPreferencesKey("name")
    }

    companion object {
        const val FILE_NAME = "online_account.preferences_pb"

        fun file(context: Context): File = File(context.noBackupFilesDir, FILE_NAME)
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal object OnlineAccountStoreModule {
    @Provides
    @Singleton
    fun onlineAccountStore(@ApplicationContext context: Context): OnlineAccountStore = OnlineAccountStore(
        PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            produceFile = { OnlineAccountStore.file(context) },
        ),
    )
}
