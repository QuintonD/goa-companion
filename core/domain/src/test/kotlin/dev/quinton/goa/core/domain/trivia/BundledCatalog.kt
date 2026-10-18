package dev.quinton.goa.core.domain.trivia

import dev.quinton.goa.core.domain.timer.bundledAsset
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.catalog.CatalogIndex
import dev.quinton.goa.core.model.catalog.Hero
import kotlinx.serialization.json.Json

/** The shipped catalog, read from `app/src/main/assets/catalog` so tests check the real numbers. */
object BundledCatalog {
    private val json = Json { ignoreUnknownKeys = false }

    val index: CatalogIndex by lazy { json.decodeFromString(CatalogIndex.serializer(), bundledAsset("index.json")) }

    val heroes: List<Hero> by lazy {
        index.heroes.map { json.decodeFromString(Hero.serializer(), bundledAsset("heroes/$it.json")) }
    }

    val catalog: Catalog by lazy { Catalog(index.dataVersion, index.printing, index.products, heroes) }

    val ranges: Map<TriviaStat, List<Int>> by lazy { TriviaFacts.valueRange(catalog) }

    fun hero(id: String): Hero = heroes.first { it.id == id }
}
