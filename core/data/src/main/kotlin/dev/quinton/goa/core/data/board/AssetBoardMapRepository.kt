package dev.quinton.goa.core.data.board

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.quinton.goa.core.data.BoardMapRepository
import dev.quinton.goa.core.data.catalog.CatalogAssets
import dev.quinton.goa.core.data.catalog.CatalogLoader
import dev.quinton.goa.core.model.board.BoardMap
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException

/** Contents of `catalog/maps/index.json`: map ids in catalog order (the core single-lane map first). */
@Serializable
internal data class BoardMapIndex(val maps: List<String>)

/**
 * Reads the bundled maps (`catalog/maps/index.json` + `catalog/maps/<id>.json`, written by
 * tools/catalog-import). Pure JVM so the validation tests run the same code. Strict like the hero
 * catalog ([CatalogLoader.json]): an unknown key means the importer and the app disagree.
 */
object BoardMapLoader {
    const val INDEX_PATH = "catalog/maps/index.json"

    fun mapPath(id: String): String = "catalog/maps/$id.json"

    fun readIndex(assets: CatalogAssets): List<String> =
        CatalogLoader.json.decodeFromString(BoardMapIndex.serializer(), assets.read(INDEX_PATH)).maps

    fun readMap(assets: CatalogAssets, id: String): BoardMap =
        CatalogLoader.json.decodeFromString(BoardMap.serializer(), assets.read(mapPath(id))).also {
            require(it.id == id) { "${mapPath(id)} declares id '${it.id}'" }
        }

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
