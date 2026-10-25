package dev.quinton.goa.web.data

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/*
 * ===== SEAM (F2 → integration, ADR-043) ==========================================================
 * Until core:model has a JS target (F1), the web shell decodes the catalog into the local
 * placeholder types below. At integration, replace WebCatalogIndex/WebHero with core:model's
 * catalog types (the same JSON the Android app reads, `app/src/main/assets/catalog/`) and decode
 * with core:data's catalog codec moved to commonMain, then delete these placeholders.
 * Never add game data here: the web reads the bundled catalog JSON only (AGENTS.md).
 * ================================================================================================
 */

/** The catalog's `index.json` (placeholder for core:model's index type). */
@Serializable
data class WebCatalogIndex(
    val dataVersion: Int,
    val printing: Int,
    val heroes: List<String>,
)

/** A hero file's header fields (placeholder for core:model's `Hero`). */
@Serializable
data class WebHero(
    val id: String,
    val name: String,
    val title: String? = null,
    val product: String? = null,
    val complexity: Int? = null,
    val portrait: String? = null,
    val symbol: String? = null,
)

/** Where the screens get the catalog from. */
interface CatalogSource {
    suspend fun index(): WebCatalogIndex

    /** Every hero, in the index's order. */
    suspend fun heroes(): List<WebHero>

    /** The hero [id], or null when the catalog has no such hero. */
    suspend fun hero(id: String): WebHero?
}

/** [CatalogSource] over the bundled JSON (`catalog/index.json`, `catalog/heroes/<id>.json`), read once. */
class BundledCatalogSource(
    private val read: suspend (path: String) -> String = Assets::text,
) : CatalogSource {
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Mutex()
    private var index: WebCatalogIndex? = null
    private var heroes: List<WebHero>? = null

    override suspend fun index(): WebCatalogIndex = lock.withLock {
        index ?: json.decodeFromString(WebCatalogIndex.serializer(), read("catalog/index.json")).also { index = it }
    }

    override suspend fun heroes(): List<WebHero> {
        heroes?.let { return it }
        val ids = index().heroes
        val loaded = coroutineScope {
            ids.map { id -> async { json.decodeFromString(WebHero.serializer(), read("catalog/heroes/$id.json")) } }
                .awaitAll()
        }
        return lock.withLock { heroes ?: loaded.also { heroes = it } }
    }

    override suspend fun hero(id: String): WebHero? = heroes().firstOrNull { it.id == id }
}
