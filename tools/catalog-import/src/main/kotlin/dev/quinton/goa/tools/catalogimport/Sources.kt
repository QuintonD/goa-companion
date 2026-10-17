package dev.quinton.goa.tools.catalogimport

import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

@Serializable
data class SourcesLock(
    val comment: String? = null,
    val sources: List<PinnedSource>,
    /** Single files pinned by URL + SHA-256 (the official rulebook PDF). */
    val documents: List<PinnedDocument> = emptyList(),
) {
    fun source(id: String): PinnedSource =
        sources.firstOrNull { it.id == id } ?: error("Source '$id' missing from sources.lock.json")

    fun document(id: String): PinnedDocument =
        documents.firstOrNull { it.id == id } ?: error("Document '$id' missing from sources.lock.json")
}

/**
 * A single downloadable file pinned by content: [sha256] of the exact bytes. A different file at
 * [url] (e.g. a reworded rulebook for a new printing) fails the download instead of being used.
 */
@Serializable
data class PinnedDocument(
    val id: String,
    val name: String,
    val url: String,
    /** Where a human finds the file (the publisher's page that links it). */
    val landingPage: String? = null,
    val sha256: String,
    val printing: Int,
    val pinned: String,
    val use: String,
)

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

    /**
     * The pinned document, cached as `documents/<id>/<sha256>.pdf`. Its bytes must hash to the
     * pinned SHA-256, both when downloaded and when read back from the cache.
     */
    fun document(doc: PinnedDocument): File {
        val target = File(cacheDir, "documents/${doc.id}/${doc.sha256.lowercase()}.pdf")
        if (!target.isFile || target.length() == 0L) {
            check(!offline) { "Offline and not cached: ${doc.name} (${doc.url})" }
            download(doc.url, target)
        }
        val actual = sha256(target)
        if (!actual.equals(doc.sha256, ignoreCase = true)) {
            target.delete()
            error(
                "${doc.name} changed: ${doc.url} now has SHA-256 $actual, pinned ${doc.sha256}. " +
                    "A new printing or revision? Review every rules excerpt against it, then re-pin it in " +
                    "sources.lock.json.",
            )
        }
        return target
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

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

    private companion object {
        const val BUFFER_SIZE = 1 shl 16
    }
}
