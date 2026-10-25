package dev.quinton.goa.web.pwa

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.js.Promise
import kotlin.math.roundToLong
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.await
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.w3c.dom.url.URL

/** `asset-manifest.json`, written by `:web:app:site`: every file of the site with its size and hash. */
@Serializable
data class AssetManifest(val build: String, val version: String, val files: List<AssetFile>)

/** [p] path relative to the site root, [s] size in bytes, [h] content hash (shared with sw.js). */
@Serializable
data class AssetFile(val p: String, val s: Long, val h: String)

/** How much of the images is on this device. */
data class OfflineStatus(val cachedFiles: Int, val totalFiles: Int, val cachedBytes: Long, val totalBytes: Long) {
    val complete: Boolean get() = cachedFiles >= totalFiles
}

data class DownloadProgress(val doneFiles: Int, val totalFiles: Int, val doneBytes: Long, val totalBytes: Long) {
    val fraction: Double get() = if (totalBytes <= 0) 1.0 else doneBytes.toDouble() / totalBytes
}

/** Pure planning for "Download everything for offline" (unit-tested). */
object OfflinePlan {
    /** The images (card scans, art, symbols) the service worker caches on demand rather than on install. */
    fun images(manifest: AssetManifest): List<AssetFile> =
        manifest.files.filter { it.p.startsWith("official/") && !it.p.endsWith(".md") }

    /** [images] not cached with their current content ([cached]: path → hash of the cached copy). */
    fun missing(images: List<AssetFile>, cached: Map<String, String>): List<AssetFile> =
        images.filter { cached[it.p] != it.h }

    fun status(images: List<AssetFile>, cached: Map<String, String>): OfflineStatus {
        val have = images.filter { cached[it.p] == it.h }
        return OfflineStatus(have.size, images.size, have.sumOf { it.s }, images.sumOf { it.s })
    }

    /** The image cache's key for [path] at content [hash], relative to the site (sw.js `imageKey`). */
    fun imageKey(path: String, hash: String): String = "$path?v=$hash"

    /** path to hash for an image cache key relative to the site; null for anything else. */
    fun parseImageKey(key: String): Pair<String, String>? {
        val path = decodeURIComponent(key.substringBefore('?'))
        val hash = key.substringAfter("?v=", "").takeIf { it.isNotEmpty() && '&' !in it } ?: return null
        return path to hash
    }

    /** "12.3 MB", "860 KB", "0 B": decimal units, as iOS Settings shows storage. */
    fun formatBytes(bytes: Long): String = when {
        bytes >= GB -> "${oneDecimal(bytes.toDouble() / GB)} GB"
        bytes >= MB -> "${oneDecimal(bytes.toDouble() / MB)} MB"
        bytes >= KB -> "${bytes / KB} KB"
        else -> "$bytes B"
    }

    private fun oneDecimal(value: Double): String {
        val tenths = (value * 10).roundToLong()
        return "${tenths / 10}.${tenths % 10}"
    }

    private const val KB = 1_000L
    private const val MB = 1_000_000L
    private const val GB = 1_000_000_000L
}

/**
 * Puts every image of the site into the service worker's image cache (`goa-images`, keyed by path and
 * hash as sw.js does), so the catalog, card viewer and board show them offline.
 */
class OfflineAssets(private val cacheName: String = IMAGE_CACHE) {
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = MainScope()
    private var job: Job? = null

    /** Non-null while "Download everything" runs; it keeps running when the person leaves Settings. */
    var progress: DownloadProgress? by mutableStateOf(null)
        private set

    /** Starts [downloadAll] in the app's scope; [onFinished] gets the outcome (not called when stopped). */
    @Suppress("TooGenericExceptionCaught") // any failure (network, quota) ends the download with a message
    fun start(manifest: AssetManifest, onFinished: suspend (Result<Unit>) -> Unit) {
        if (job?.isActive == true) return
        job = scope.launch {
            val result = try {
                downloadAll(manifest) { progress = it }
                Result.success(Unit)
            } catch (e: CancellationException) {
                progress = null
                throw e
            } catch (e: Throwable) {
                Result.failure(e)
            }
            progress = null
            onFinished(result)
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        progress = null
    }

    /** False where the Cache API is missing (an insecure origin, some private modes). */
    val supported: Boolean
        get() {
            val caches = window.asDynamic().caches
            return caches != null && caches != undefined
        }

    suspend fun manifest(): AssetManifest {
        val response = window.fetch("asset-manifest.json").await()
        check(response.ok) { "The site's file list isn't available (${response.status})" }
        return json.decodeFromString(AssetManifest.serializer(), response.text().await())
    }

    suspend fun status(manifest: AssetManifest): OfflineStatus =
        OfflinePlan.status(OfflinePlan.images(manifest), cachedHashes())

    /** Downloads what is missing, [PARALLEL] at a time, reporting after each file. Cancel the coroutine to stop. */
    suspend fun downloadAll(manifest: AssetManifest, onProgress: (DownloadProgress) -> Unit) {
        val images = OfflinePlan.images(manifest)
        val queue = OfflinePlan.missing(images, cachedHashes()).toMutableList()
        val cache = openCache()
        val lock = Mutex()
        val totalBytes = images.sumOf { it.s }
        var doneFiles = images.size - queue.size
        var doneBytes = totalBytes - queue.sumOf { it.s }
        onProgress(DownloadProgress(doneFiles, images.size, doneBytes, totalBytes))
        coroutineScope {
            List(PARALLEL) {
                async {
                    while (true) {
                        val file = lock.withLock { queue.removeFirstOrNull() } ?: break
                        store(cache, file)
                        lock.withLock {
                            doneFiles++
                            doneBytes += file.s
                            onProgress(DownloadProgress(doneFiles, images.size, doneBytes, totalBytes))
                        }
                    }
                }
            }.awaitAll()
        }
    }

    private suspend fun store(cache: dynamic, file: AssetFile) {
        val response = window.fetch(file.p).await()
        check(response.ok) { "Couldn't download ${file.p} (${response.status})" }
        (cache.put(absolute(OfflinePlan.imageKey(file.p, file.h)), response) as Promise<Any?>).await()
    }

    /** path → hash of every image in the cache, read from the keys alone (fast even with 700 entries). */
    private suspend fun cachedHashes(): Map<String, String> {
        if (!supported) return emptyMap()
        val cache = openCache() // a local first: a dynamic call straight on a suspend call's result isn't awaited
        val requests = (cache.keys() as Promise<Array<dynamic>>).await()
        val base = absolute("")
        return requests.mapNotNull { request ->
            val url = request.url as String
            if (url.startsWith(base)) OfflinePlan.parseImageKey(url.removePrefix(base)) else null
        }.toMap()
    }

    private suspend fun openCache(): dynamic = (window.asDynamic().caches.open(cacheName) as Promise<dynamic>).await()

    companion object {
        const val IMAGE_CACHE = "goa-images"
        const val PARALLEL = 6
    }
}

private fun absolute(path: String): String = URL(path, document.baseURI).href

private external fun decodeURIComponent(value: String): String
