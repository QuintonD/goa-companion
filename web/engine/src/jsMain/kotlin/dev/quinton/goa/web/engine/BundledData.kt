package dev.quinton.goa.web.engine

import dev.quinton.goa.core.model.board.BoardMap
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.catalog.CatalogFiles
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * The bundled catalog and maps (`catalog/` of the Android app, served with the site), parsed by the
 * phone's strict loaders. [read] fetches a file by its path relative to the site (or reads it from
 * the repository in tests); the files are read in parallel.
 */
object BundledData {
    suspend fun load(read: suspend (path: String) -> String): Pair<Catalog, List<BoardMap>> = coroutineScope {
        val index = read(CatalogFiles.INDEX_PATH)
        val mapIds = async { CatalogFiles.parseMapIndex(read(CatalogFiles.MAPS_INDEX_PATH)) }
        val heroes = CatalogFiles.parseIndex(index).heroes.map(CatalogFiles::heroPath)
            .map { path -> async { path to read(path) } }
        val maps = mapIds.await().map { id -> async { CatalogFiles.parseMap(id, read(CatalogFiles.mapPath(id))) } }
        val files = heroes.awaitAll().toMap() + (CatalogFiles.INDEX_PATH to index)
        CatalogFiles.load { path -> files[path] ?: error("$path was not read") } to maps.awaitAll()
    }
}
