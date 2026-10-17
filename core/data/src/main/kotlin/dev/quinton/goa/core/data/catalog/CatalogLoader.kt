package dev.quinton.goa.core.data.catalog

import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.catalog.CatalogIndex
import dev.quinton.goa.core.model.catalog.Hero
import java.io.InputStream
import kotlinx.serialization.json.Json

/** Opens a bundled asset by its path relative to `assets/` (e.g. `catalog/index.json`). */
fun interface CatalogAssets {
    fun open(path: String): InputStream
}

/**
 * Reads the bundled catalog (`catalog/index.json` + one `catalog/heroes/<id>.json` per hero).
 * Pure JVM so the same code is exercised by the validation tests. Parsing is strict: an unknown
 * key means the importer and the app disagree on the schema, which must fail loudly.
 */
object CatalogLoader {
    const val INDEX_PATH = "catalog/index.json"

    val json: Json = Json {
        ignoreUnknownKeys = false
        isLenient = false
    }

    fun heroPath(id: String): String = "catalog/heroes/$id.json"

    fun readIndex(assets: CatalogAssets): CatalogIndex =
        json.decodeFromString(CatalogIndex.serializer(), assets.read(INDEX_PATH))

    fun readHero(assets: CatalogAssets, id: String): Hero =
        json.decodeFromString(Hero.serializer(), assets.read(heroPath(id)))

    /** Heroes come back in index order. */
    fun load(assets: CatalogAssets): Catalog {
        val index = readIndex(assets)
        val heroes = index.heroes.map { id ->
            readHero(assets, id).also { require(it.id == id) { "${heroPath(id)} declares id '${it.id}'" } }
        }
        return Catalog(
            dataVersion = index.dataVersion,
            printing = index.printing,
            products = index.products,
            heroes = heroes,
        )
    }

    private fun CatalogAssets.read(path: String): String = open(path).use { it.readBytes().toString(Charsets.UTF_8) }
}
