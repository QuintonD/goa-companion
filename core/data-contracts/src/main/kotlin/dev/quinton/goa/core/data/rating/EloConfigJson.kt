package dev.quinton.goa.core.data.rating

import dev.quinton.goa.core.domain.rating.EloConfig
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * How the rating constants are stored on a device (the phone's DataStore, the web app's IndexedDB):
 * JSON, with the defaults stored as nothing so future default changes reach people who never
 * edited them. Lenient so constants written by newer versions (extra keys) still load.
 */
object EloConfigJson {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** What to store for [config]: null for the defaults. */
    fun encode(config: EloConfig): String? =
        json.encodeToString(EloConfig.serializer(), config).takeIf { config != EloConfig.DEFAULT }

    /** The stored constants, sanitized; the defaults when nothing (or nothing readable) is stored. */
    fun decode(stored: String?): EloConfig = if (stored == null) {
        EloConfig.DEFAULT
    } else {
        try {
            json.decodeFromString(EloConfig.serializer(), stored).sanitized()
        } catch (_: SerializationException) {
            EloConfig.DEFAULT
        } catch (_: IllegalArgumentException) {
            EloConfig.DEFAULT
        }
    }
}
