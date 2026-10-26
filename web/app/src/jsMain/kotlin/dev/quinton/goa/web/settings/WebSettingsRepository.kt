package dev.quinton.goa.web.settings

import dev.quinton.goa.core.data.SettingsRepository
import dev.quinton.goa.core.model.settings.AppSettings
import dev.quinton.goa.web.storage.KeyValueStore
import dev.quinton.goa.web.storage.getJson
import dev.quinton.goa.web.storage.putJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The app settings ([AppSettings], the phone's model) kept in IndexedDB as one JSON value, the web's
 * `DataStoreSettingsRepository`. Unknown or unreadable values fall back to the defaults, never fail
 * (as `SettingsDataSource` does); [update] is atomic within the page.
 *
 * The theme is the exception: the web keeps it in `ThemeController` (localStorage, read before the
 * first paint so the page never flashes the wrong palette). [AppSettings.theme] is stored here too
 * but no web screen reads it.
 */
class WebSettingsRepository(private val store: KeyValueStore) : SettingsRepository {
    private val lock = Mutex()
    private val state = MutableStateFlow<AppSettings?>(null)

    override val settings: Flow<AppSettings> = flow {
        loaded()
        emitAll(state.filterNotNull())
    }.distinctUntilChanged()

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        lock.withLock {
            val next = transform(current())
            if (next != state.value) {
                store.putJson(KEY, next)
                state.value = next
            }
        }
    }

    private suspend fun loaded(): AppSettings = state.value ?: lock.withLock { current() }

    /** Call with [lock] held. */
    private suspend fun current(): AppSettings = state.value
        ?: (store.getJson<AppSettings>(KEY) ?: AppSettings()).also { state.value = it }

    private companion object {
        const val KEY = "settings/app"
    }
}
