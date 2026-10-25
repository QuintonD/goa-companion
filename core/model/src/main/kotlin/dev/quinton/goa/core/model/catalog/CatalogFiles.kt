package dev.quinton.goa.core.model.catalog

import dev.quinton.goa.core.model.board.BoardMap
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * How the bundled catalog files are read: `catalog/index.json` + `catalog/heroes/<id>.json`, and
 * `catalog/maps/index.json` + `catalog/maps/<id>.json` (written by tools/catalog-import). Shared by
 * the Android app (assets) and the web app (fetched files), ADR-043. Parsing is strict: an unknown
 * key means the importer and the app disagree on the schema, which must fail loudly.
 */
object CatalogFiles {
    const val INDEX_PATH = "catalog/index.json"
    const val MAPS_INDEX_PATH = "catalog/maps/index.json"

    val json: Json = Json {
        ignoreUnknownKeys = false
        isLenient = false
    }

    fun heroPath(id: String): String = "catalog/heroes/$id.json"

    fun mapPath(id: String): String = "catalog/maps/$id.json"

    fun parseIndex(text: String): CatalogIndex = json.decodeFromString(CatalogIndex.serializer(), text)

    /** The hero file of [id]; it must declare that id. */
    fun parseHero(id: String, text: String): Hero = json.decodeFromString(Hero.serializer(), text).also {
        require(it.id == id) { "${heroPath(id)} declares id '${it.id}'" }
    }

    fun catalog(index: CatalogIndex, heroes: List<Hero>): Catalog = Catalog(
        dataVersion = index.dataVersion,
        printing = index.printing,
        products = index.products,
        heroes = heroes,
    )

    /** The whole catalog, heroes in index order; [read] returns a file's text by its path. */
    inline fun load(read: (path: String) -> String): Catalog {
        val index = parseIndex(read(INDEX_PATH))
        return catalog(index, index.heroes.map { parseHero(it, read(heroPath(it))) })
    }

    /** Map ids in catalog order (the core single-lane map first). */
    fun parseMapIndex(text: String): List<String> = json.decodeFromString(MapIndex.serializer(), text).maps

    /** The map file of [id]; it must declare that id. */
    fun parseMap(id: String, text: String): BoardMap = json.decodeFromString(BoardMap.serializer(), text).also {
        require(it.id == id) { "${mapPath(id)} declares id '${it.id}'" }
    }

    @Serializable
    private data class MapIndex(val maps: List<String>)
}
