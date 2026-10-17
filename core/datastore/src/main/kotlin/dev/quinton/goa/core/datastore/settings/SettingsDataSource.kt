package dev.quinton.goa.core.datastore.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import dev.quinton.goa.core.model.settings.AppSettings
import dev.quinton.goa.core.model.settings.ThemeMode
import dev.quinton.goa.core.model.settings.TimerLayout
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * App settings persisted as Preferences in `datastore/app_settings.preferences_pb` (covered by
 * Auto Backup). Unknown or corrupt values fall back to [AppSettings] defaults, never crash.
 */
@Singleton
class SettingsDataSource @Inject constructor(@param:SettingsStore private val store: DataStore<Preferences>) {
    val settings: Flow<AppSettings> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map(::read)
        .distinctUntilChanged()

    /** Atomically applies [transform] to the current settings and persists the result. */
    suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.edit { prefs ->
            val next = transform(read(prefs))
            prefs[Keys.THEME] = next.theme.name
            prefs[Keys.OWNED_PRODUCTS] = next.ownedProducts
            prefs[Keys.HAPTICS] = next.hapticsEnabled
            prefs[Keys.SOUNDS] = next.soundsEnabled
            prefs[Keys.CATALOG_LIST] = next.catalogListView
            prefs[Keys.TIMER_LAYOUT] = next.timerLayout.name
        }
    }

    internal object Keys {
        val THEME = stringPreferencesKey("theme")
        val OWNED_PRODUCTS = stringSetPreferencesKey("owned_products")
        val HAPTICS = booleanPreferencesKey("haptics_enabled")
        val SOUNDS = booleanPreferencesKey("sounds_enabled")
        val CATALOG_LIST = booleanPreferencesKey("catalog_list_view")
        val TIMER_LAYOUT = stringPreferencesKey("timer_layout")
    }

    private companion object {
        val Defaults = AppSettings()

        fun read(prefs: Preferences) = AppSettings(
            theme = prefs[Keys.THEME]?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }
                ?: Defaults.theme,
            ownedProducts = prefs[Keys.OWNED_PRODUCTS] ?: Defaults.ownedProducts,
            hapticsEnabled = prefs[Keys.HAPTICS] ?: Defaults.hapticsEnabled,
            soundsEnabled = prefs[Keys.SOUNDS] ?: Defaults.soundsEnabled,
            catalogListView = prefs[Keys.CATALOG_LIST] ?: Defaults.catalogListView,
            timerLayout = prefs[Keys.TIMER_LAYOUT]?.let { name -> TimerLayout.entries.firstOrNull { it.name == name } }
                ?: Defaults.timerLayout,
        )
    }
}
