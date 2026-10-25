package dev.quinton.goa.core.data.catalog

import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.catalog.CatalogFiles
import dev.quinton.goa.core.model.catalog.CatalogIndex
import dev.quinton.goa.core.model.catalog.Hero
import java.io.InputStream
import kotlinx.serialization.json.Json

/** Opens a bundled asset by its path relative to `assets/` (e.g. `catalog/index.json`). */
fun interface CatalogAssets {
    fun open(path: String): InputStream
}

/**
 * Reads the bundled catalog (`catalog/index.json` + one `catalog/heroes/<id>.json` per hero) from
 * the app's assets with the shared [CatalogFiles] (the web app reads the same files the same way,
 * ADR-043). Pure JVM so the same code is exercised by the validation tests. Parsing is strict: an
 * unknown key means the importer and the app disagree on the schema, which must fail loudly.
 */
object CatalogLoader {
    const val INDEX_PATH = CatalogFiles.INDEX_PATH

    val json: Json = CatalogFiles.json

    fun heroPath(id: String): String = CatalogFiles.heroPath(id)

    fun readIndex(assets: CatalogAssets): CatalogIndex = CatalogFiles.parseIndex(assets.read(INDEX_PATH))

    /** The hero file of [id]; it must declare that id. */
    fun readHero(assets: CatalogAssets, id: String): Hero = CatalogFiles.parseHero(id, assets.read(heroPath(id)))

    /** Heroes come back in index order. */
    fun load(assets: CatalogAssets): Catalog = CatalogFiles.load { assets.read(it) }

    private fun CatalogAssets.read(path: String): String = open(path).use { it.readBytes().toString(Charsets.UTF_8) }
}
