package dev.quinton.goa.web.catalog

import dev.quinton.goa.core.data.CatalogRepository
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.testing.TestCatalog
import dev.quinton.goa.web.data.WebServices
import dev.quinton.goa.web.storage.MemoryKeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest

class CatalogBrowseTest {
    private class CountingRepository(private val shipped: Catalog) : CatalogRepository {
        var reads = 0
        override val catalog: Flow<Catalog> = flow {
            reads++
            emit(shipped)
        }
    }

    @Test
    fun theSearchIndexIsBuiltOnceForThePage() = runTest {
        val repository = CountingRepository(TestCatalog.catalog)
        val browse = CatalogBrowse(repository)

        val first = browse.indexed()
        val second = browse.indexed()

        assertSame(first, second)
        assertEquals(1, repository.reads)
        assertEquals(TestCatalog.catalog.heroes.size, first.index.size)
    }

    @Test
    fun personalStatsFromAStreamNotBuiltYetFallBackInsteadOfBreakingTheHeroPage() = runTest {
        // W2's ratings are a stub that throws when called: the catalog still shows, without stats.
        val services = WebServices(MemoryKeyValueStore())
        val ratings = optional(emptyMap()) { services.group.ratings.observeHeroRatings() }.toList()

        assertEquals(listOf(emptyMap(), emptyMap()), ratings)
    }

    @Test
    fun aWorkingSourceFollowsItsFallback() = runTest {
        val values = optional(0) { flowOf(1, 2) }.toList()

        assertEquals(listOf(0, 1, 2), values)
    }

    @Test
    fun aSourceThatFailsLaterKeepsTheFallback() = runTest {
        val values = optional(0) {
            flow {
                emit(1)
                error("IndexedDB refused")
            }
        }.toList()

        assertEquals(listOf(0, 1, 0), values)
        assertEquals(0, optional(0) { flow<Int> { error("no") } }.first())
    }
}
