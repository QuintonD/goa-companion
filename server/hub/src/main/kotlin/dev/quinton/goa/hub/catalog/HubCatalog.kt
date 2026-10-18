package dev.quinton.goa.hub.catalog

import dev.quinton.goa.core.domain.catalog.CatalogAssets
import dev.quinton.goa.core.domain.catalog.CatalogLoader
import dev.quinton.goa.core.model.catalog.Catalog
import java.io.FileNotFoundException
import java.io.InputStream

/**
 * The app's bundled files on the hub: the same `catalog/` JSON and `official/` images as the APK's
 * `assets/`, packaged into the hub's resources by the build (`bundleAppFiles`; no second copy in
 * git). Paths are the asset paths the catalog itself uses (`catalog/index.json`,
 * `official/cards/arien-gold.webp`); [url] turns one into the hub URL that serves it to signed-in
 * devices.
 */
class HubCatalog(private val classLoader: ClassLoader = HubCatalog::class.java.classLoader) {
    /** Opens an asset the way the app's `AssetManager` would (for the core:domain loaders). */
    val assets: CatalogAssets = CatalogAssets { path ->
        open(path) ?: throw FileNotFoundException(path)
    }

    /** The hero catalog, parsed once (strictly, as in the app) on first use. */
    val catalog: Catalog by lazy { CatalogLoader.load(assets) }

    /** The asset at [path], or null if there is none or the path isn't a clean relative path. */
    fun open(path: String): InputStream? = clean(path)?.let { classLoader.getResourceAsStream("$ROOT/$it") }

    fun exists(path: String): Boolean = clean(path)?.let { classLoader.getResource("$ROOT/$it") } != null

    fun readText(path: String): String =
        (open(path) ?: throw FileNotFoundException(path)).use { it.readBytes().toString(Charsets.UTF_8) }

    companion object {
        const val ROOT = "goa-assets"
        const val URL_PREFIX = "/assets"

        /** The hub URL of the asset at [path] (served to signed-in devices only). */
        fun url(path: String): String = "$URL_PREFIX/$path"

        private val SAFE = Regex("[A-Za-z0-9._-]+(/[A-Za-z0-9._-]+)*")

        /** [path] if it is a plain relative path inside the assets (no `..`, no absolute paths). */
        fun clean(path: String): String? = path.trim('/').takeIf { p ->
            SAFE.matches(p) && p.split('/').none { it == "." || it == ".." }
        }
    }
}
