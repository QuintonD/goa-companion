package dev.quinton.goa.tools.catalogimport

import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

@Serializable
data class SourcesLock(val comment: String? = null, val sources: List<PinnedSource>) {
    fun source(id: String): PinnedSource =
        sources.firstOrNull { it.id == id } ?: error("Source '$id' missing from sources.lock.json")
}

@Serializable
data class PinnedSource(
    val id: String,
    val name: String,
    val repo: String,
    val branch: String,
    val sha: String,
    val pinned: String,
    val use: String,
) {
    fun rawUrl(path: String): String = "https://raw.githubusercontent.com/$repo/$sha/${encodePath(path)}"

    /** Human-facing link to a file at the pinned commit. */
    fun blobUrl(path: String): String = "https://github.com/$repo/blob/$sha/${encodePath(path)}"

    companion object {
        fun encodePath(path: String): String = path.split('/').joinToString("/") {
            URLEncoder.encode(it, Charsets.UTF_8).replace("+", "%20")
        }
    }
}

@Serializable
data class GitTree(val sha: String, val tree: List<GitTreeEntry>, val truncated: Boolean = false)

@Serializable
data class GitTreeEntry(val path: String, val type: String, val size: Long? = null)

/**
 * Downloads pinned files into `tools/catalog-import/cache/<owner>__<repo>/<sha>/<path>` and
 * serves them from there afterwards. Files come from raw.githubusercontent.com; the only GitHub
 * API call is one recursive tree listing per pinned commit (also cached), because image file
 * names cannot be derived from the data.
 */
class SourceCache(private val cacheDir: File, private val offline: Boolean) {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    var downloads: Int = 0
        private set

    private fun dirOf(source: PinnedSource) = File(cacheDir, "${source.repo.replace("/", "__")}/${source.sha}")

    fun file(source: PinnedSource, path: String): File {
        val target = File(dirOf(source), path)
        if (target.isFile && target.length() > 0) return target
        check(!offline) { "Offline and not cached: ${source.repo}/$path" }
        download(source.rawUrl(path), target)
        return target
    }

    fun text(source: PinnedSource, path: String): String = file(source, path).readText(Charsets.UTF_8)

    fun tree(source: PinnedSource): GitTree {
        val target = File(dirOf(source), ".tree.json")
        if (!target.isFile) {
            check(!offline) { "Offline and no cached tree for ${source.repo}" }
            download("https://api.github.com/repos/${source.repo}/git/trees/${source.sha}?recursive=1", target)
        }
        val tree = json.decodeFromString(GitTree.serializer(), target.readText())
        check(!tree.truncated) { "Tree listing of ${source.repo} is truncated" }
        return tree
    }

    private fun download(url: String, target: File) {
        val request = Request.Builder().url(url).header("User-Agent", "goa-catalog-import").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $url")
            target.parentFile.mkdirs()
            val tmp = File(target.parentFile, target.name + ".part")
            tmp.outputStream().use { out -> response.body.byteStream().copyTo(out) }
            check(tmp.renameTo(target) || (target.delete() && tmp.renameTo(target))) { "Cannot move $tmp" }
            downloads++
        }
    }
}
