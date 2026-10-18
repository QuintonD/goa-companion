package dev.quinton.goa.core.data.trivia

import dev.quinton.goa.core.data.CatalogRepository
import dev.quinton.goa.core.data.TriviaProgressRepository
import dev.quinton.goa.core.datastore.trivia.TriviaProgressStore
import dev.quinton.goa.core.domain.trivia.CardGroup
import dev.quinton.goa.core.domain.trivia.FactProgress
import dev.quinton.goa.core.domain.trivia.TriviaConfig
import dev.quinton.goa.core.domain.trivia.TriviaFact
import dev.quinton.goa.core.domain.trivia.TriviaMastery
import dev.quinton.goa.core.domain.trivia.TriviaStat
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Card-trivia progress (ADR-028): this phone's own practice record, in its own DataStore file.
 * Reading never fails: an unreadable document is "no progress yet" / "no last setup", and one
 * unreadable entry only loses that entry.
 *
 * The progress document holds up to ~2,000 entries, so decoding, merging and encoding it run on
 * [computeDispatcher], never on the caller's (main) thread. DataStore runs an edit's transform in
 * the caller's context, hence the `withContext` around each edit.
 */
@Singleton
internal class DataStoreTriviaProgressRepository internal constructor(
    private val store: TriviaProgressStore,
    private val catalogRepository: CatalogRepository,
    private val computeDispatcher: CoroutineDispatcher,
) : TriviaProgressRepository {
    @Inject
    constructor(store: TriviaProgressStore, catalogRepository: CatalogRepository) :
        this(store, catalogRepository, Dispatchers.Default)

    override val progress: Flow<Map<String, FactProgress>> =
        store.progressJson.distinctUntilChanged().map(TriviaJson::decodeProgress).flowOn(computeDispatcher)

    override val lastConfig: Flow<TriviaConfig?> = store.configJson.distinctUntilChanged().map(TriviaJson::decodeConfig)

    override suspend fun record(updates: Map<String, FactProgress>) {
        if (updates.isEmpty()) return
        withContext(computeDispatcher) {
            store.updateProgress { stored -> TriviaJson.encodeProgress(TriviaJson.decodeProgress(stored) + updates) }
        }
    }

    override suspend fun saveConfig(config: TriviaConfig) = store.writeConfig(TriviaJson.encodeConfig(config))

    override suspend fun reset(heroIds: Set<String>?) {
        if (heroIds == null) {
            store.updateProgress { null }
            return
        }
        // The heroes' facts by their cards in the catalog: card ids need not start with the hero id.
        val catalog = catalogRepository.catalog.first()
        val keys = heroIds.mapNotNull(catalog::hero).flatMap { it.cards }
            .flatMap { card -> TriviaStat.entries.map { TriviaFact.keyOf(card.id, it) } }
            .toSet()
        if (keys.isEmpty()) return
        withContext(computeDispatcher) {
            store.updateProgress { stored ->
                val kept = TriviaJson.decodeProgress(stored) - keys
                if (kept.isEmpty()) null else TriviaJson.encodeProgress(kept)
            }
        }
    }
}

/** The stored trivia documents. Lenient both ways, so older and newer builds read each other's. */
internal object TriviaJson {
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
