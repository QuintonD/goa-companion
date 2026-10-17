package dev.quinton.goa.core.domain.timer

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** JSON codec for bundled presets, saved presets and the persisted snapshot. */
object TimerJson {
    /** Bundled data is strict: unknown keys are a bug in the data file. */
    private val strict = Json { ignoreUnknownKeys = false }

    /** Persisted user data tolerates fields from newer app versions. */
    private val lenient = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val presetList = ListSerializer(TimerPreset.serializer())

    fun parseCatalog(text: String): TimerPresetCatalog = strict.decodeFromString(TimerPresetCatalog.serializer(), text)

    fun encodePresets(presets: List<TimerPreset>): String = lenient.encodeToString(presetList, presets)

    fun decodePresets(text: String): List<TimerPreset> = lenient.decodeFromString(presetList, text)

    fun encodeSnapshot(snapshot: TimerSnapshot): String = lenient.encodeToString(TimerSnapshot.serializer(), snapshot)

    fun decodeSnapshot(text: String): TimerSnapshot = lenient.decodeFromString(TimerSnapshot.serializer(), text)
}
