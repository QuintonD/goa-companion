package dev.quinton.goa.web.data

import kotlinx.browser.window
import kotlinx.coroutines.await

/** A bundled file couldn't be read (offline before it was cached, or a broken deploy). */
class AssetUnavailableException(val path: String, message: String, cause: Throwable? = null) :
    Exception(message, cause)

/**
 * Reads the bundled files of the site (catalog JSON, SOURCES.md, licences) by path relative to the
 * page, e.g. `catalog/index.json`. Relative URLs keep the app working under `/goa-companion/`.
 * The service worker answers these from its cache, so they work offline once installed.
 */
object Assets {
    @Suppress("TooGenericExceptionCaught") // fetch rejects with a plain JS TypeError
    suspend fun text(path: String): String {
        val response = try {
            window.fetch(path).await()
        } catch (e: Throwable) {
            throw AssetUnavailableException(path, "Couldn't load $path: ${e.message}", e)
        }
        if (!response.ok) throw AssetUnavailableException(path, "Couldn't load $path (${response.status})")
        return response.text().await()
    }
}
