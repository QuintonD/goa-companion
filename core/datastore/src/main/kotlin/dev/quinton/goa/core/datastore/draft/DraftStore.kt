package dev.quinton.goa.core.datastore.draft

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
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Draft persistence (owner: draft). Stores JSON documents whose schema lives in core:domain
 * (`DraftSession`, `DraftMethod`), so this module stays free of engine types:
 * - the in-progress draft, so it survives process death;
 * - the user's custom draft methods.
 */
class DraftStore(private val dataStore: DataStore<Preferences>) {
    /** JSON of the in-progress draft, or null when none. */
    val activeDraft: Flow<String?> = dataStore.data.map { it[ACTIVE] }

    /** JSON array of user-saved methods, or null before the first save. */
    val customMethods: Flow<String?> = dataStore.data.map { it[CUSTOM_METHODS] }

    suspend fun setActiveDraft(json: String?) {
        dataStore.edit { prefs -> if (json == null) prefs.remove(ACTIVE) else prefs[ACTIVE] = json }
    }

    suspend fun setCustomMethods(json: String) {
        dataStore.edit { it[CUSTOM_METHODS] = json }
    }

    private companion object {
        val ACTIVE = stringPreferencesKey("active_draft")
        val CUSTOM_METHODS = stringPreferencesKey("custom_methods")
    }
}

/** Reads the bundled `assets/catalog/draft-methods.json`. */
class BundledDraftMethods @Inject constructor(@ApplicationContext private val context: Context) {
    suspend fun read(): String = withContext(Dispatchers.IO) {
        context.assets.open(PATH).bufferedReader().use { it.readText() }
    }

    companion object {
        const val PATH = "catalog/draft-methods.json"
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal object DraftStoreModule {
    @Provides
    @Singleton
    fun draftStore(@ApplicationContext context: Context): DraftStore =
        DraftStore(PreferenceDataStoreFactory.create(produceFile = { context.preferencesDataStoreFile("draft") }))
}
