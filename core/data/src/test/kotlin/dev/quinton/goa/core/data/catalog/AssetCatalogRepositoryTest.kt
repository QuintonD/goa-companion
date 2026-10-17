package dev.quinton.goa.core.data.catalog

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.model.catalog.Catalog
import java.io.FileNotFoundException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AssetCatalogRepositoryTest {
    private val opens = AtomicInteger()
    private val countingAssets = CatalogAssets { path ->
        opens.incrementAndGet()
        RepoAssets.assets.open(path)
    }

    @Test
    fun `emits the full bundled catalog once`() = runTest {
        val repo = AssetCatalogRepository(countingAssets, StandardTestDispatcher(testScheduler))
        repo.catalog.test {
            val catalog = awaitItem()
            assertThat(catalog.heroes).hasSize(32)
            assertThat(catalog.products).hasSize(6)
            awaitComplete()
        }
    }

    @Test
    fun `parses only once however many collectors there are`() = runTest {
        val repo = AssetCatalogRepository(countingAssets, StandardTestDispatcher(testScheduler))
        val results = (1..5).map { async { repo.get() } }.awaitAll()
        repo.catalog.test {
            awaitItem()
            awaitComplete()
        }
        assertThat(results.distinct()).hasSize(1)
        assertThat(opens.get()).isEqualTo(1 + 32)
    }

    @Test
    fun `a broken catalog yields the empty catalog and reports the error`() = runTest {
        val errors = mutableListOf<Throwable>()
        val repo = AssetCatalogRepository(
            assets = { path -> throw FileNotFoundException(path) },
            ioDispatcher = StandardTestDispatcher(testScheduler),
            onError = { errors += it },
        )
        assertThat(repo.get()).isEqualTo(Catalog.EMPTY)
        assertThat(errors).hasSize(1)
    }
}
