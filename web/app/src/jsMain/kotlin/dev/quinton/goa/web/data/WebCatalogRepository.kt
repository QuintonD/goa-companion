package dev.quinton.goa.web.data

import dev.quinton.goa.core.data.CatalogRepository
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.catalog.CatalogFiles
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The hero catalog bundled with the site (`catalog/index.json` + `catalog/heroes/<id>.json`, the
 * Android app's own files), parsed by the phone's strict loader [CatalogFiles.load] and then held
 * in memory. Never game data of its own (AGENTS.md).
 *
 * Unlike the phone (where a broken catalog is a build defect and it shows [Catalog.EMPTY]), a fetch
 * here can fail for ordinary reasons (offline before the service worker cached the files): [get]
 * throws, the screen offers Try again, and the next call fetches again.
 */
class WebCatalogRepository(
    private val read: suspend (path: String) -> String = Assets::text,
) : CatalogRepository {
    private val lock = Mutex()
    private var cached: Catalog? = null

    override val catalog: Flow<Catalog> = flow { emit(get()) }

    /** Loads on first use (the hero files in parallel); later calls return the same instance. */
    suspend fun get(): Catalog = cached ?: lock.withLock { cached ?: load().also { cached = it } }

    private suspend fun load(): Catalog {
        val index = read(CatalogFiles.INDEX_PATH)
        val heroPaths = CatalogFiles.parseIndex(index).heroes.map(CatalogFiles::heroPath)
        val files = coroutineScope { heroPaths.map { path -> async { path to read(path) } }.awaitAll() }
            .toMap() + (CatalogFiles.INDEX_PATH to index)
        return CatalogFiles.load { path -> files[path] ?: error("$path was not fetched") }
    }
}
