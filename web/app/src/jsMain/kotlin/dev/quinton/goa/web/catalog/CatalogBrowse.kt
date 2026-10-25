package dev.quinton.goa.web.catalog

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.quinton.goa.core.data.CatalogRepository
import dev.quinton.goa.core.domain.catalog.CatalogFilter
import dev.quinton.goa.core.domain.catalog.CatalogSearchIndex
import dev.quinton.goa.core.domain.catalog.CatalogSort
import dev.quinton.goa.core.model.catalog.Catalog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The catalog with its search index, built once per page (the phone's `CatalogViewModel.indexed`). */
class IndexedCatalog(val catalog: Catalog, val index: CatalogSearchIndex)

/**
 * The Heroes tab's long-lived parts (`AppServices.data.content.browse`): the search index, built on
 * first use, and the search, filters and sort, which live as long as the page — like the phone's
 * `CatalogViewModel` while the Heroes tab is on the back stack, so switching tabs or opening a hero
 * and coming back keeps them.
 */
class CatalogBrowse(private val repository: CatalogRepository) {
    private val lock = Mutex()
    private var indexed: IndexedCatalog? = null

    var query: String by mutableStateOf("")
    var filter: CatalogFilter by mutableStateOf(CatalogFilter())
    var sort: CatalogSort by mutableStateOf(CatalogSort.NAME)

    /** Throws when the catalog can't be fetched (offline before it was cached): the screen offers Try again. */
    suspend fun indexed(): IndexedCatalog = indexed ?: lock.withLock {
        indexed ?: repository.catalog.first().let { IndexedCatalog(it, CatalogSearchIndex(it.heroes)) }
            .also { indexed = it }
    }
}

/**
 * [source] with [fallback] first and whenever it fails, including a contract another stream hasn't
 * built yet (its stub throws when called) or IndexedDB refusing: personal stats are extras on the
 * catalog's screens and never keep a hero from showing (the phone's `.catch { emit(empty) }`).
 */
fun <T> optional(fallback: T, source: () -> Flow<T>): Flow<T> = flow {
    val upstream = try {
        source()
    } catch (e: CancellationException) {
        throw e
    } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
        console.info("Optional data unavailable: ${e.message}")
        emit(fallback)
        return@flow
    }
    emitAll(upstream.catch { emit(fallback) })
}.onStart { emit(fallback) }
