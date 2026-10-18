package dev.quinton.goa.feature.board

import android.content.Context
import coil3.EventListener
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.Uri
import coil3.annotation.DelicateCoilApi
import coil3.decode.BitmapFactoryDecoder
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.Options
import coil3.request.SuccessResult
import coil3.request.allowHardware
import dev.quinton.goa.core.model.board.BoardLane
import dev.quinton.goa.core.model.board.BoardMap
import dev.quinton.goa.core.model.board.BoardSpace
import dev.quinton.goa.core.model.board.BoardZone
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.SpawnKind
import dev.quinton.goa.core.model.board.SpawnPoint
import dev.quinton.goa.core.model.board.ZoneKind
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.catalog.CatalogIndex
import dev.quinton.goa.core.model.catalog.Hero
import dev.quinton.goa.core.model.match.Team
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path.Companion.toOkioPath

/**
 * A small synthetic map for ViewModel tests (never real game data): a strip 15 spaces long and 3
 * wide, five Zones of 3 columns from Red's Throne Zone (q 0–2) to Blue's (q 12–14), the middle one
 * the starting Battle Zone. One terrain hole at (7,1).
 */
object TestMap {
    val terrain = Hex(7, 1)

    private val spawns = mapOf(
        Hex(0, 0) to SpawnPoint(Team.RED, SpawnKind.HERO),
        Hex(0, 1) to SpawnPoint(Team.RED, SpawnKind.HERO),
        Hex(0, 2) to SpawnPoint(Team.RED, SpawnKind.HERO),
        Hex(14, 0) to SpawnPoint(Team.BLUE, SpawnKind.HERO),
        Hex(14, 1) to SpawnPoint(Team.BLUE, SpawnKind.HERO),
        Hex(14, 2) to SpawnPoint(Team.BLUE, SpawnKind.HERO),
        Hex(6, 0) to SpawnPoint(Team.RED, SpawnKind.MELEE),
        Hex(6, 2) to SpawnPoint(Team.RED, SpawnKind.HEAVY),
        Hex(8, 0) to SpawnPoint(Team.BLUE, SpawnKind.MELEE),
        Hex(8, 2) to SpawnPoint(Team.BLUE, SpawnKind.RANGED),
        Hex(3, 0) to SpawnPoint(Team.RED, SpawnKind.MELEE),
        Hex(5, 0) to SpawnPoint(Team.BLUE, SpawnKind.MELEE),
        Hex(9, 0) to SpawnPoint(Team.RED, SpawnKind.MELEE),
        Hex(11, 0) to SpawnPoint(Team.BLUE, SpawnKind.MELEE),
    )

    private val zoneIds = listOf("red-throne", "lane-1", "mid", "lane-3", "blue-throne")

    val map = BoardMap(
        id = "test-map",
        name = "Test map",
        lanes = listOf(BoardLane("lane", zoneIds, startingBattleZone = "mid")),
        zones = listOf(
            BoardZone("red-throne", ZoneKind.THRONE, Team.RED),
            BoardZone("lane-1", ZoneKind.LANE),
            BoardZone("mid", ZoneKind.LANE),
            BoardZone("lane-3", ZoneKind.LANE),
            BoardZone("blue-throne", ZoneKind.THRONE, Team.BLUE),
        ),
        spaces = (0..14).flatMap { q ->
            (0..2).mapNotNull { r ->
                val hex = Hex(q, r)
                if (hex == terrain) null else BoardSpace(q, r, zoneIds[q / 3], spawns[hex])
            }
        },
        terrain = listOf(terrain),
        printing = 2,
        verified = false,
    )
}

/** A file under the app's bundled `assets/`, found from the test's working directory. */
fun bundledAssetFile(path: String): File {
    var dir: File? = File(System.getProperty("user.dir")).absoluteFile
    while (dir != null) {
        File(dir, "app/src/main/assets/$path").takeIf { it.exists() }?.let { return it }
        dir = dir.parentFile
    }
    error("$path not found above ${System.getProperty("user.dir")}")
}

private val json = Json { ignoreUnknownKeys = true }

/** The real bundled catalog: every hero and its real cards and portraits. */
val bundledCatalog: Catalog by lazy {
    val index = json.decodeFromString(CatalogIndex.serializer(), bundledAssetFile("catalog/index.json").readText())
    Catalog(
        dataVersion = index.dataVersion,
        printing = index.printing,
        products = index.products,
        heroes = index.heroes.map { id ->
            json.decodeFromString(Hero.serializer(), bundledAssetFile("catalog/heroes/$id.json").readText())
        },
    )
}

/** The bundled maps (whatever the import pipeline wrote), in index order. */
val bundledMaps: List<BoardMap> by lazy {
    val ids = json.parseToJsonElement(bundledAssetFile("catalog/maps/index.json").readText())
        .let { it as kotlinx.serialization.json.JsonObject }["maps"]
        .let { it as kotlinx.serialization.json.JsonArray }
        .map { (it as kotlinx.serialization.json.JsonPrimitive).content }
    ids.map { id -> json.decodeFromString(BoardMap.serializer(), bundledAssetFile("catalog/maps/$id.json").readText()) }
}

/**
 * Coil for screenshot tests: serves `file:///android_asset/…` from the app's bundled assets and
 * counts loads in flight, so a capture can wait for every portrait.
 */
object BundledImages {
    private val pending = AtomicInteger(0)

    val idle: Boolean get() = pending.get() == 0

    @OptIn(DelicateCoilApi::class)
    fun install(context: Context) {
        pending.set(0)
        SingletonImageLoader.setUnsafe(
            ImageLoader.Builder(context)
                .components {
                    add(AssetFetcher.Factory())
                    add(BitmapFactoryDecoder.Factory())
                }
                .allowHardware(false)
                .eventListener(
                    object : EventListener() {
                        override fun onStart(request: ImageRequest) {
                            pending.incrementAndGet()
                        }

                        override fun onSuccess(request: ImageRequest, result: SuccessResult) {
                            pending.decrementAndGet()
                        }

                        override fun onError(request: ImageRequest, result: ErrorResult) {
                            pending.decrementAndGet()
                        }

                        override fun onCancel(request: ImageRequest) {
                            pending.decrementAndGet()
                        }
                    },
                )
                .build(),
        )
    }

    private class AssetFetcher(private val file: File) : Fetcher {
        override suspend fun fetch(): FetchResult = SourceFetchResult(
            source = ImageSource(file.toOkioPath(), FileSystem.SYSTEM),
            mimeType = null,
            dataSource = DataSource.DISK,
        )

        class Factory : Fetcher.Factory<Uri> {
            override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
                val path = data.path?.takeIf { data.scheme == "file" && it.startsWith(ASSET_PREFIX) } ?: return null
                return AssetFetcher(bundledAssetFile(path.removePrefix(ASSET_PREFIX)))
            }
        }
    }

    private const val ASSET_PREFIX = "/android_asset/"
}
