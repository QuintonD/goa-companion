package dev.quinton.goa.core.data.trivia

import dev.quinton.goa.core.domain.trivia.CardGroup
import dev.quinton.goa.core.domain.trivia.FactProgress
import dev.quinton.goa.core.domain.trivia.TriviaConfig
import dev.quinton.goa.core.domain.trivia.TriviaMastery
import dev.quinton.goa.core.domain.trivia.TriviaStat
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The stored trivia documents (the phone's DataStore, the web app's IndexedDB): this device's
 * progress per fact and the last quiz setup. Lenient both ways, so older and newer builds read each
 * other's; one unreadable entry only loses that entry.
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

    fun decodeProgress(stored: String?): Map<String, FactProgress> {
        val document = stored?.let(::parseObject) ?: return emptyMap()
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

    fun decodeConfig(stored: String?): TriviaConfig? {
        val document = stored?.let(::parseObject) ?: return null
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
