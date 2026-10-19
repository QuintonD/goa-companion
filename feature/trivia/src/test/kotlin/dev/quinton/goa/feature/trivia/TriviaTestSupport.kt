package dev.quinton.goa.feature.trivia

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
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.catalog.CatalogIndex
import dev.quinton.goa.core.model.catalog.Hero
import dev.quinton.goa.core.model.player.Player
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path.Companion.toOkioPath

/** A file under the app's bundled `assets/`, found from the test's working directory. */
fun bundledAssetFile(path: String): File {
    var dir: File? = File(System.getProperty("user.dir")).absoluteFile
    while (dir != null) {
        File(dir, "app/src/main/assets/$path").takeIf { it.exists() }?.let { return it }
        dir = dir.parentFile
    }
    error("$path not found above ${System.getProperty("user.dir")}")
}

/** A file under the app's bundled `assets/catalog/`. */
fun bundledCatalogFile(path: String): File = bundledAssetFile("catalog/$path")

/** A play group for the Playing-as and leaderboard screenshots (palette colours, crests). */
object ScreenshotPlayers {
    val ann = Player("ann", "Ann", 0xFF3FC1B0, avatarId = 1, createdAt = 1)
    val sam = Player("sam", "Sam", 0xFFD9B44A, avatarId = 5, createdAt = 2)
    val zoe = Player("zoe", "Zoe", 0xFFEE7F6F, avatarId = 9, createdAt = 3)
    val ravi = Player("ravi", "Ravi", 0xFF62A8F2, avatarId = 13, createdAt = 4)
    val mo = Player("mo", "Mo", 0xFFB892E6, avatarId = 17, createdAt = 5)
    val kit = Player("kit", "Kit Featherstonehaugh-Llewellyn", 0xFF6CC27A, avatarId = 21, createdAt = 6)
    val all = listOf(ann, sam, zoe, ravi, mo, kit)
}

private val json = Json { ignoreUnknownKeys = false }

/**
 * The real bundled catalog (all heroes, real card numbers). Card scans stay (screenshots load them
 * from the app's assets through [BundledImages]); portraits are dropped so every screen shows the
 * sigil fallback, every run alike.
 */
val bundledCatalog: Catalog by lazy {
    val index = json.decodeFromString(CatalogIndex.serializer(), bundledCatalogFile("index.json").readText())
    Catalog(
        dataVersion = index.dataVersion,
        printing = index.printing,
        products = index.products,
        heroes = index.heroes.map { id ->
            val hero = json.decodeFromString(Hero.serializer(), bundledCatalogFile("heroes/$id.json").readText())
            hero.copy(portrait = null)
        },
    )
}

/**
 * Coil for screenshot tests: serves `file:///android_asset/…` from the app's bundled assets on disk
 * (the feature module has none of its own) and counts loads in flight, so a capture can wait for
 * every card scan to be drawn.
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
                    // ImageDecoder isn't available to Robolectric on every host (Windows): BitmapFactory is.
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
