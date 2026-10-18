package dev.quinton.goa.core.data.catalog

import dev.quinton.goa.core.domain.catalog.CatalogLoader
import dev.quinton.goa.core.domain.catalog.CatalogAssets
import android.content.Context
import android.util.Log
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.quinton.goa.core.data.CatalogRepository
import dev.quinton.goa.core.model.catalog.Catalog
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException

/**
 * The read-only hero catalog bundled in `assets/catalog/`. Parsed once on [ioDispatcher] the
 * first time [catalog] is collected and then held in memory for the life of the process.
 *
 * A catalog that fails to parse is a build defect (the validation tests in this module guard
 * against it); at runtime it is logged and [Catalog.EMPTY] is emitted rather than crashing.
 */
@Singleton
class AssetCatalogRepository internal constructor(
    private val assets: CatalogAssets,
    private val ioDispatcher: CoroutineDispatcher,
    private val onError: (Throwable) -> Unit = {},
) : CatalogRepository {
    @Inject
    constructor(@ApplicationContext context: Context) : this(
        assets = CatalogAssets { path -> context.assets.open(path) },
        ioDispatcher = Dispatchers.IO,
        onError = { Log.e(TAG, "Bundled catalog failed to load", it) },
    )

    private val mutex = Mutex()

    @Volatile
    private var cached: Catalog? = null

    override val catalog: Flow<Catalog> = flow { emit(get()) }

    /** Loads on first use; later calls return the same in-memory instance. */
    suspend fun get(): Catalog = cached ?: mutex.withLock {
        cached ?: withContext(ioDispatcher) { loadOrEmpty() }.also { cached = it }
    }

    private fun loadOrEmpty(): Catalog = try {
        CatalogLoader.load(assets)
    } catch (e: IOException) {
        failed(e)
    } catch (e: SerializationException) {
        failed(e)
    } catch (e: IllegalArgumentException) {
        failed(e)
    }

    private fun failed(e: Throwable): Catalog {
        onError(e)
        return Catalog.EMPTY
    }

    private companion object {
        const val TAG = "AssetCatalogRepository"
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal interface CatalogModule {
    @Binds
    fun bindCatalogRepository(impl: AssetCatalogRepository): CatalogRepository
}
