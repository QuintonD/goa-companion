package dev.quinton.goa.core.domain.trivia

import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The stored trivia documents (ADR-028): progress per fact key and the last quiz setup, as JSON.
 * The phone keeps them in DataStore (`trivia_progress`), the hub per browser. Lenient both ways,
 * so older and newer builds read each other's: an unreadable document is "nothing yet", and one
 * unreadable entry only loses that entry.
 */
object TriviaJson {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        // Progress is ~2 000 entries at most: leave defaults (plus = false, box = 0) out.
        encodeDefaults = false
    }

    /** The setup is written in full, so a later change of a default never changes a saved one. */
    private val configJson = Json(json) { encodeDefaults = true }

    private val progressSerializer = MapSerializer(String.serializer(), FactProgress.serializer())

    fun encodeProgress(progress: Map<String, FactProgress>): String = json.encodeToString(progressSerializer, progress)

    fun decodeProgress(stored: String?): Map<String, FactProgress> = decodeProgress(stored?.let(::parseObject))

    /** [decodeProgress] of an already parsed document. */
    fun decodeProgress(document: JsonObject?): Map<String, FactProgress> {
        if (document == null) return emptyMap()
        return buildMap {
            document.forEach { (key, element) -> decodeEntry(element)?.let { put(key, it) } }
        }
    }

    private fun decodeEntry(element: JsonElement): FactProgress? = try {
        json.decodeFromJsonElement(FactProgress.serializer(), element).sanitized()
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun FactProgress.sanitized() = copy(
        box = box.coerceIn(0, TriviaMastery.MAX_BOX),
        seen = seen.coerceAtLeast(0),
        correct = correct.coerceIn(0, seen.coerceAtLeast(0)),
    )

    fun encodeConfig(config: TriviaConfig): String = configJson.encodeToString(TriviaConfig.serializer(), config)

    /** The progress document as a JSON object (as [encodeProgress] writes it). */
    fun progressElement(progress: Map<String, FactProgress>): JsonObject =
        json.encodeToJsonElement(progressSerializer, progress) as JsonObject

    /** The setup as a JSON object (as [encodeConfig] writes it). */
    fun configElement(config: TriviaConfig): JsonObject =
        configJson.encodeToJsonElement(TriviaConfig.serializer(), config) as JsonObject

    fun decodeConfig(stored: String?): TriviaConfig? = decodeConfig(stored?.let(::parseObject))

    /** [decodeConfig] of an already parsed document. */
    fun decodeConfig(document: JsonObject?): TriviaConfig? {
        if (document == null) return null
        // coerceInputValues covers an unknown answer style, not unknown entries in a set: drop those.
        val known = document.toMutableMap()
        known.keepKnown("stats") { TriviaStat.fromKey(it) != null }
        known.keepKnown("groups") { CardGroup.fromKey(it) != null }
        val config = try {
            json.decodeFromJsonElement(TriviaConfig.serializer(), JsonObject(known))
        } catch (_: SerializationException) {
            return null
        } catch (_: IllegalArgumentException) {
            return null
        }
        return config.copy(length = config.length.takeIf { it >= 0 } ?: TriviaConfig.DEFAULT_LENGTH)
    }

    /**
     * Keeps the known entries of the stored set under [key]; with none left (or not a list) the key
     * goes, so the setup falls back to the default set rather than an empty quiz.
     */
    private fun MutableMap<String, JsonElement>.keepKnown(key: String, isKnown: (String) -> Boolean) {
        val entries = (get(key) as? JsonArray)?.filter { it is JsonPrimitive && it.isString && isKnown(it.content) }
        if (entries.isNullOrEmpty()) remove(key) else put(key, JsonArray(entries))
    }

    private fun parseObject(stored: String): JsonObject? = try {
        json.parseToJsonElement(stored) as? JsonObject
    } catch (_: SerializationException) {
        null
    }
}
