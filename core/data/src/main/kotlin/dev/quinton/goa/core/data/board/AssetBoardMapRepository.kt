package dev.quinton.goa.core.data.board

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.quinton.goa.core.data.BoardMapRepository
import dev.quinton.goa.core.data.catalog.CatalogAssets
import dev.quinton.goa.core.model.board.BoardMap
import dev.quinton.goa.core.model.catalog.CatalogFiles
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
 * Reads the bundled maps (`catalog/maps/index.json` + `catalog/maps/<id>.json`, written by
 * tools/catalog-import) with the shared [CatalogFiles] (ADR-043). Pure JVM so the validation tests
 * run the same code. Strict like the hero catalog: an unknown key means the importer and the app
 * disagree.
 */
object BoardMapLoader {
    const val INDEX_PATH = CatalogFiles.MAPS_INDEX_PATH

    fun mapPath(id: String): String = CatalogFiles.mapPath(id)

    fun readIndex(assets: CatalogAssets): List<String> = CatalogFiles.parseMapIndex(assets.read(INDEX_PATH))

    fun readMap(assets: CatalogAssets, id: String): BoardMap = CatalogFiles.parseMap(id, assets.read(mapPath(id)))

    /**
     * Every map in index order. One map that fails to read is reported through [onError] and left
     * out; the others still load.
     */
    fun load(assets: CatalogAssets, onError: (String, Throwable) -> Unit = { _, _ -> }): List<BoardMap> =
        readIndex(assets).mapNotNull { id ->
            try {
                readMap(assets, id)
            } catch (e: IOException) {
                null.also { onError(id, e) }
            } catch (e: SerializationException) {
                null.also { onError(id, e) }
            } catch (e: IllegalArgumentException) {
                null.also { onError(id, e) }
            }
        }

    private fun CatalogAssets.read(path: String): String = open(path).use { it.readBytes().toString(Charsets.UTF_8) }
}

/**
 * The bundled board maps (ADR-033), parsed once on [ioDispatcher] the first time [maps] is collected
 * and then held in memory. No index (or an unreadable one) means no maps, logged, never a crash.
 */
@Singleton
class AssetBoardMapRepository internal constructor(
    private val assets: CatalogAssets,
    private val ioDispatcher: CoroutineDispatcher,
    private val onError: (Throwable) -> Unit = {},
) : BoardMapRepository {
    @Inject
    constructor(@ApplicationContext context: Context) : this(
        assets = CatalogAssets { path -> context.assets.open(path) },
        ioDispatcher = Dispatchers.IO,
        onError = { Log.e(TAG, "Bundled board maps failed to load", it) },
    )

    private val mutex = Mutex()

    @Volatile
    private var cached: List<BoardMap>? = null

    override val maps: Flow<List<BoardMap>> = flow { emit(get()) }

    suspend fun get(): List<BoardMap> = cached ?: mutex.withLock {
        cached ?: withContext(ioDispatcher) { loadOrEmpty() }.also { cached = it }
    }

    private fun loadOrEmpty(): List<BoardMap> = try {
        BoardMapLoader.load(assets) { _, e -> onError(e) }
    } catch (e: IOException) {
        emptyList<BoardMap>().also { onError(e) }
    } catch (e: SerializationException) {
        emptyList<BoardMap>().also { onError(e) }
    }

    private companion object {
        const val TAG = "AssetBoardMapRepository"
    }
}
