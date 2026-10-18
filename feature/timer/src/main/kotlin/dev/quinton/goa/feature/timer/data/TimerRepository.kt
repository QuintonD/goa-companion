package dev.quinton.goa.feature.timer.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.quinton.goa.core.datastore.timer.TimerStateStore
import dev.quinton.goa.core.domain.setup.MatchRules
import dev.quinton.goa.core.domain.timer.TimerJson
import dev.quinton.goa.core.domain.timer.TimerPreset
import dev.quinton.goa.core.domain.timer.TimerPresetCatalog
import dev.quinton.goa.core.domain.timer.TimerSnapshot
import dev.quinton.goa.core.presentation.timer.TimerPresets
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

/** "Tonight" turn length for one preset, used by the next start without editing the preset. */
data class TonightTurn(val presetId: String, val seconds: Int)

/**
 * Presets (user-saved, seeded from the bundled defaults) and the persisted session snapshot.
 * Presets are fully editable; "restore defaults" brings back any bundled preset that was removed.
 * Stored copies of bundled presets are upgraded on read ([TimerPresetMigration]).
 */
@Singleton
class TimerRepository @Inject constructor(
    private val store: TimerStateStore,
    private val assets: TimerAssets,
) {
    val presets: Flow<List<TimerPreset>> = store.presetsJson.map { json ->
        TimerPresets.effective(json?.let(::decodePresetsOrNull), assets.presetCatalog())
    }

    private val tonight = MutableStateFlow<TonightTurn?>(null)

    /** Turn length chosen for tonight's games (kept in memory until changed or saved). */
    val tonightTurn: StateFlow<TonightTurn?> = tonight.asStateFlow()

    /** Sets tonight's turn length for [presetId]; null (or the preset's own length) clears it. */
    suspend fun setTonightTurn(presetId: String, seconds: Int?) {
        val preset = preset(presetId)
        val own = preset?.let(TimerPresets::turnSeconds)
        tonight.value = when {
            seconds == null || preset == null || seconds == own -> null
            else -> TonightTurn(presetId, seconds)
        }
    }

    /** [preset] with tonight's turn length applied, if one is set for it. */
    fun withTonight(preset: TimerPreset): TimerPreset {
        val override = tonight.value?.takeIf { it.presetId == preset.id } ?: return preset
        return TimerPresets.withTonight(preset, override.seconds)
    }

    /** Writes tonight's turn length into the preset itself and clears the override. */
    suspend fun saveTonightToPreset() {
        val override = tonight.value ?: return
        val preset = preset(override.presetId) ?: return
        save(withTonight(preset))
        tonight.value = null
    }

    /** The selected preset id, falling back to the bundled default (or the first preset). */
    val selectedPresetId: Flow<String> = combine(presets, store.selectedPresetId) { list, id ->
        TimerPresets.selectedId(list, id, assets.presetCatalog())
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
        store.writePresets(TimerJson.encodePresets(TimerPresets.save(presets.first(), preset)))
    }

    /** Deletes [id] unless it's the last preset. Returns false when refused. */
    suspend fun delete(id: String): Boolean {
        val remaining = TimerPresets.delete(presets.first(), id) ?: return false
        store.writePresets(TimerJson.encodePresets(remaining))
        return true
    }

    suspend fun restoreDefaults() {
        store.writePresets(
            TimerJson.encodePresets(TimerPresets.restoreDefaults(presets.first(), assets.presetCatalog())),
        )
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

    private fun decodePresetsOrNull(json: String): List<TimerPreset>? = runCatching {
        TimerJson.decodePresets(json)
    }.getOrNull()

    /** A snapshot from an incompatible older version is dropped rather than crashing the app. */
    private fun decodeSnapshotOrNull(json: String): TimerSnapshot? =
        runCatching { TimerJson.decodeSnapshot(json) }.getOrNull()
}
