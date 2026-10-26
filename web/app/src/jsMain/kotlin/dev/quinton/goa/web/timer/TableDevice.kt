package dev.quinton.goa.web.timer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.quinton.goa.web.storage.KeyValueStore
import dev.quinton.goa.web.storage.getJson
import dev.quinton.goa.web.storage.putJson
import kotlin.js.Promise
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.w3c.dom.events.Event

/** This device's table preferences (not synced): IndexedDB `table/prefs`. */
@Serializable
data class TablePrefs(
    /** Keep the screen awake while the timer runs (Screen Wake Lock). */
    val keepScreenOn: Boolean = true,
    /** The "keep this screen open" note under the timer was dismissed. */
    val screenNoteDismissed: Boolean = false,
)

/** What the Screen Wake Lock is doing, for the timer's menu and its note. */
enum class WakeState {
    /** Not asked for (the timer isn't running, or the person turned it off). */
    OFF,

    /** The screen stays on. */
    ON,

    /** The browser has no Wake Lock (iOS before 16.4) or refused it (a home-screen app before iOS 18.4). */
    UNAVAILABLE,
}

/**
 * Screen Wake Lock (`navigator.wakeLock`), feature-detected: held while the timer runs and the person
 * wants it (Android keeps the screen on on the running timer). The browser drops the lock whenever the
 * page is hidden, so it is asked for again when the page shows.
 */
class WakeLock(private val scope: CoroutineScope) {
    var state: WakeState by mutableStateOf(WakeState.OFF)
        private set

    private var wanted = false
    private var sentinel: dynamic = null

    val supported: Boolean get() = window.navigator.asDynamic().wakeLock != undefined

    init {
        document.addEventListener("visibilitychange", { _: Event ->
            if (document.asDynamic().visibilityState == "visible" && wanted) acquire()
        })
    }

    /** Hold the lock while [on] (re-acquired after the page was hidden). */
    fun want(on: Boolean) {
        if (on == wanted) return
        wanted = on
        if (on) acquire() else release()
    }

    private fun acquire() {
        if (!supported) {
            state = WakeState.UNAVAILABLE
            return
        }
        scope.launch {
            try {
                val lock: dynamic = (
                    window.navigator.asDynamic().wakeLock.request(
                        "screen",
                    ) as Promise<dynamic>
                    ).await()
                if (!wanted) {
                    lock.release()
                    return@launch
                }
                sentinel = lock
                state = WakeState.ON
                // The browser releases it when the page is hidden; visibilitychange asks again.
                lock.addEventListener("release", { _: dynamic ->
                    if (sentinel === lock) {
                        sentinel = null
                        state = WakeState.OFF
                    }
                })
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
                // NotAllowedError: not visible yet, battery saver, or no Wake Lock in a home-screen app.
                console.info("Screen Wake Lock refused", e)
                state = WakeState.UNAVAILABLE
            }
        }
    }

    private fun release() {
        val lock = sentinel
        sentinel = null
        if (lock != null) runCatching { lock.release() }
        state = WakeState.OFF
    }
}

/** The table preferences, read once and written through. */
class TablePrefsStore(private val store: KeyValueStore, private val scope: CoroutineScope) {
    var prefs: TablePrefs by mutableStateOf(TablePrefs())
        private set

    init {
        scope.launch { store.getJson<TablePrefs>(KEY)?.let { prefs = it } }
    }

    fun update(transform: (TablePrefs) -> TablePrefs) {
        prefs = transform(prefs)
        val value = prefs
        scope.launch { store.putJson(KEY, value) }
    }

    private companion object {
        const val KEY = "prefs"
    }
}
