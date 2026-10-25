package dev.quinton.goa.web.timer

import dev.quinton.goa.core.domain.setup.MatchRules
import dev.quinton.goa.core.domain.timer.TimerJson
import dev.quinton.goa.core.domain.timer.TimerPreset
import dev.quinton.goa.core.domain.timer.TimerPresetMigration
import dev.quinton.goa.core.domain.timer.TimerSnapshot
import dev.quinton.goa.feature.timer.data.TonightTurn
import dev.quinton.goa.web.storage.KeyValueStore
import dev.quinton.goa.web.storage.getJson
import dev.quinton.goa.web.storage.putJson
import dev.quinton.goa.web.table.TableAssets
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The timer's presets (seeded from the bundled defaults, all editable), the selected preset, tonight's
 * turn length and the running session, kept in IndexedDB under `table/timer/` — the web app's
 * Android `TimerRepository` + `TimerStateStore`. The stored formats are the phone's (`TimerJson`);
 * stored copies of bundled presets are upgraded on read ([TimerPresetMigration]). The session
 * snapshot survives a reload, Safari putting the page away and the phone locking: everything is
 * computed from its timestamps.
 */
class WebTimerRepository(private val store: KeyValueStore, private val assets: TableAssets) {
    private val lock = Mutex()
    private val state = MutableStateFlow<Loaded?>(null)

    /** Everything stored, once read. */
    private data class Loaded(
        val bundled: List<TimerPreset>,
        val defaultId: String,
        val stored: List<TimerPreset>?,
        val selectedId: String?,
        val snapshot: TimerSnapshot?,
        val tonight: TonightTurn?,
    ) {
        val presets: List<TimerPreset> get() = stored?.takeIf {
            it.isNotEmpty()
        }?.let { TimerPresetMigration.upgrade(it, bundled) }
            ?: bundled

        /** The selected preset id, falling back to the bundled default (or the first preset). */
        val selected: String
            get() {
                val list = presets
                return list.firstOrNull { it.id == selectedId }?.id
                    ?: list.firstOrNull { it.id == defaultId }?.id
                    ?: list.first().id
            }
    }

    private val loaded: Flow<Loaded> = state.filterNotNull()

    val presets: Flow<List<TimerPreset>> = loaded.map { it.presets }

    val selectedPresetId: Flow<String> = loaded.map { it.selected }

    val snapshot: Flow<TimerSnapshot?> = loaded.map { it.snapshot }

    val tonightTurn: Flow<TonightTurn?> = loaded.map { it.tonight }

    private val failure = MutableStateFlow<Throwable?>(null)

    /** Why loading failed (the bundled presets couldn't be read), or null. */
    val loadError: StateFlow<Throwable?> = failure.asStateFlow()

    /** Reads the store and the bundled presets once; call again after a failure. */
    suspend fun load() {
        if (state.value != null) return
        lock.withLock {
            if (state.value != null) return
            try {
                val catalog = assets.timerPresets()
                state.value = Loaded(
                    bundled = catalog.presets,
                    defaultId = catalog.defaultPresetId,
                    stored = store.get(PRESETS)?.let { runCatching { TimerJson.decodePresets(it) }.getOrNull() },
                    selectedId = store.get(SELECTED),
                    // A snapshot from an incompatible older version is dropped rather than crashing the app.
                    snapshot = store.get(SESSION)?.let { runCatching { TimerJson.decodeSnapshot(it) }.getOrNull() },
                    tonight = store.getJson<TonightTurn>(TONIGHT),
                )
                failure.value = null
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
                failure.value = e // offline before the presets were cached: the screen offers Try again
            }
        }
    }

    private suspend fun current(): Loaded {
        load()
        return state.value ?: loaded.first()
    }

    suspend fun rules(): MatchRules = assets.rules()

    suspend fun preset(id: String): TimerPreset? = current().presets.firstOrNull { it.id == id }

    suspend fun selectedPreset(): TimerPreset {
        val s = current()
        return s.presets.first { it.id == s.selected }
    }

    suspend fun select(id: String) = write { it.copy(selectedId = id) }.also { store.put(SELECTED, id) }

    /** Inserts or replaces [preset] (keeping its position). */
    suspend fun save(preset: TimerPreset) {
        val list = current().presets
        val updated = if (list.any { it.id == preset.id }) {
            list.map {
                if (it.id ==
                    preset.id
                ) {
                    preset
                } else {
                    it
                }
            }
        } else {
            list + preset
        }
        writePresets(updated)
    }

    /** Deletes [id] unless it's the last preset. Returns false when refused. */
    suspend fun delete(id: String): Boolean {
        val list = current().presets
        if (list.size <= 1 || list.none { it.id == id }) return false
        writePresets(list.filterNot { it.id == id })
        return true
    }

    /** Brings back any bundled preset that was deleted. */
    suspend fun restoreDefaults() {
        val s = current()
        val missing = s.bundled.filter { bundled -> s.presets.none { it.id == bundled.id } }
        writePresets(missing + s.presets)
    }

    private suspend fun writePresets(list: List<TimerPreset>) {
        write { it.copy(stored = list) }
        store.put(PRESETS, TimerJson.encodePresets(list))
    }

    /** Sets tonight's turn length for [presetId]; null (or the preset's own length) clears it. */
    suspend fun setTonightTurn(presetId: String, seconds: Int?) {
        val tonight = TonightTurn.of(preset(presetId), seconds)
        write { it.copy(tonight = tonight) }
        if (tonight == null) store.delete(TONIGHT) else store.putJson(TONIGHT, tonight)
    }

    /** [preset] with tonight's turn length applied, if one is set for it. */
    suspend fun withTonight(preset: TimerPreset): TimerPreset = TonightTurn.apply(preset, current().tonight)

    /** Writes tonight's turn length into the preset itself and clears the override. */
    suspend fun saveTonightToPreset() {
        val tonight = current().tonight ?: return
        val preset = preset(tonight.presetId) ?: return
        save(tonight.applyTo(preset))
        setTonightTurn(tonight.presetId, null)
    }

    suspend fun currentSnapshot(): TimerSnapshot? = current().snapshot

    /** Transforms the stored snapshot; returning null clears the session. Callers serialise (the controller's lock). */
    suspend fun updateSnapshot(transform: (TimerSnapshot?) -> TimerSnapshot?): TimerSnapshot? {
        val updated = transform(current().snapshot)
        write { it.copy(snapshot = updated) }
        if (updated == null) store.delete(SESSION) else store.put(SESSION, TimerJson.encodeSnapshot(updated))
        return updated
    }

    private suspend fun write(transform: (Loaded) -> Loaded) {
        val s = current()
        state.value = transform(state.value ?: s)
    }

    private companion object {
        const val PRESETS = "timer/presets"
        const val SELECTED = "timer/selected"
        const val SESSION = "timer/session"
        const val TONIGHT = "timer/tonight"
    }
}
