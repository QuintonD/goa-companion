package dev.quinton.goa.core.testing

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
import dev.quinton.goa.core.model.catalog.Hero
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path.Companion.toOkioPath

/** A file under the app's bundled `assets/`, found from the test's working directory. */
fun bundledAsset(path: String): File {
    var dir: File? = File(System.getProperty("user.dir")).absoluteFile
    while (dir != null) {
        File(dir, "app/src/main/assets/$path").takeIf { it.exists() }?.let { return it }
        dir = dir.parentFile
    }
    error("$path not found above ${System.getProperty("user.dir")}")
}

private val catalogJson = Json { ignoreUnknownKeys = true }

/** A hero as bundled in `assets/catalog/heroes/<id>.json` (real catalog data, official images). */
fun bundledHero(id: String): Hero =
    catalogJson.decodeFromString(Hero.serializer(), bundledAsset("catalog/heroes/$id.json").readText())

/**
 * Coil for screenshot tests: serves `file:///android_asset/…` from the app's bundled assets on disk
 * (library modules have none of their own) and counts loads in flight, so a capture can wait until
 * every official image is drawn (`compose.waitUntil { BundledAssetImages.idle }`).
 */
object BundledAssetImages {
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
                return AssetFetcher(bundledAsset(path.removePrefix(ASSET_PREFIX)))
            }
        }
    }

    private const val ASSET_PREFIX = "/android_asset/"
}
