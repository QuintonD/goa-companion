package dev.quinton.goa.feature.timer.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.quinton.goa.core.datastore.timer.TimerStateStore
import dev.quinton.goa.core.domain.setup.MatchRules
import dev.quinton.goa.core.domain.timer.TimerJson
import dev.quinton.goa.core.domain.timer.TimerPreset
import dev.quinton.goa.core.domain.timer.TimerPresetCatalog
import dev.quinton.goa.core.domain.timer.TimerSnapshot
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Bundled, read-only rules data (`assets/catalog/match-setup.json`, `timer-presets.json`). */
interface TimerAssets {
    suspend fun rules(): MatchRules

    suspend fun presetCatalog(): TimerPresetCatalog
}

@Singleton
class AndroidTimerAssets @Inject constructor(@ApplicationContext private val context: Context) : TimerAssets {
    private val mutex = Mutex()
    private var rules: MatchRules? = null
    private var catalog: TimerPresetCatalog? = null

    override suspend fun rules(): MatchRules = mutex.withLock {
        rules ?: MatchRules.parse(read("catalog/match-setup.json")).also { rules = it }
    }

    override suspend fun presetCatalog(): TimerPresetCatalog = mutex.withLock {
        catalog ?: TimerJson.parseCatalog(read("catalog/timer-presets.json")).also { catalog = it }
    }

    private suspend fun read(path: String): String = withContext(Dispatchers.IO) {
        context.assets.open(path).bufferedReader().use { it.readText() }
    }
}

/**
 * Presets (user-saved, seeded from the bundled defaults) and the persisted session snapshot.
 * Presets are fully editable; "restore defaults" brings back any bundled preset that was removed.
 */
@Singleton
class TimerRepository @Inject constructor(
    private val store: TimerStateStore,
    private val assets: TimerAssets,
) {
    val presets: Flow<List<TimerPreset>> = store.presetsJson.map { json ->
        json?.let(::decodePresetsOrNull)?.takeIf { it.isNotEmpty() } ?: assets.presetCatalog().presets
    }

    /** The selected preset id, falling back to the bundled default (or the first preset). */
    val selectedPresetId: Flow<String> = combine(presets, store.selectedPresetId) { list, id ->
        list.firstOrNull { it.id == id }?.id ?: defaultIdIn(list)
    }

    val snapshot: Flow<TimerSnapshot?> = store.snapshotJson.map { json -> json?.let(::decodeSnapshotOrNull) }

    suspend fun rules(): MatchRules = assets.rules()

    suspend fun preset(id: String): TimerPreset? = presets.first().firstOrNull { it.id == id }

    suspend fun selectedPreset(): TimerPreset {
        val id = selectedPresetId.first()
        return presets.first().first { it.id == id }
    }

    suspend fun select(id: String) = store.writeSelectedPresetId(id)

    /** Inserts or replaces [preset] (keeping its position). */
    suspend fun save(preset: TimerPreset) {
        val list = presets.first()
        val updated = if (list.any { it.id == preset.id }) {
            list.map { if (it.id == preset.id) preset else it }
        } else {
            list + preset
        }
        store.writePresets(TimerJson.encodePresets(updated))
    }

    /** Deletes [id] unless it's the last preset. Returns false when refused. */
    suspend fun delete(id: String): Boolean {
        val list = presets.first()
        if (list.size <= 1 || list.none { it.id == id }) return false
        store.writePresets(TimerJson.encodePresets(list.filterNot { it.id == id }))
        return true
    }

    suspend fun restoreDefaults() {
        val list = presets.first()
        val missing = assets.presetCatalog().presets.filter { bundled -> list.none { it.id == bundled.id } }
        store.writePresets(TimerJson.encodePresets(missing + list))
    }

    suspend fun currentSnapshot(): TimerSnapshot? = store.currentSnapshotJson()?.let(::decodeSnapshotOrNull)

    /** Atomically transforms the persisted snapshot; returning null clears the session. */
    suspend fun updateSnapshot(transform: (TimerSnapshot?) -> TimerSnapshot?): TimerSnapshot? {
        var result: TimerSnapshot? = null
        store.updateSnapshot { json ->
            result = transform(json?.let(::decodeSnapshotOrNull))
            result?.let(TimerJson::encodeSnapshot)
        }
        return result
    }

    private suspend fun defaultIdIn(list: List<TimerPreset>): String {
        val bundledDefault = assets.presetCatalog().defaultPresetId
        return list.firstOrNull { it.id == bundledDefault }?.id ?: list.first().id
    }

    private fun decodePresetsOrNull(json: String): List<TimerPreset>? = runCatching {
        TimerJson.decodePresets(json)
    }.getOrNull()

    /** A snapshot from an incompatible older version is dropped rather than crashing the app. */
    private fun decodeSnapshotOrNull(json: String): TimerSnapshot? =
        runCatching { TimerJson.decodeSnapshot(json) }.getOrNull()
}
