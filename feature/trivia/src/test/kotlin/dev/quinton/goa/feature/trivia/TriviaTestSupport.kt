package dev.quinton.goa.feature.trivia

import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.catalog.CatalogIndex
import dev.quinton.goa.core.model.catalog.Hero
import java.io.File
import kotlinx.serialization.json.Json

/** A file under the app's bundled `assets/catalog/`, found from the test's working directory. */
fun bundledCatalogFile(path: String): File {
    var dir: File? = File(System.getProperty("user.dir")).absoluteFile
    while (dir != null) {
        File(dir, "app/src/main/assets/catalog/$path").takeIf { it.exists() }?.let { return it }
        dir = dir.parentFile
    }
    error("$path not found above ${System.getProperty("user.dir")}")
}

private val json = Json { ignoreUnknownKeys = false }

/**
 * The real bundled catalog (all heroes, real card numbers). Images are dropped: the feature
 * module has no assets, so screenshots show the sigil fallback and no card scan, every run alike.
 */
val bundledCatalog: Catalog by lazy {
    val index = json.decodeFromString(CatalogIndex.serializer(), bundledCatalogFile("index.json").readText())
    Catalog(
        dataVersion = index.dataVersion,
        printing = index.printing,
        products = index.products,
        heroes = index.heroes.map { id ->
            val hero = json.decodeFromString(Hero.serializer(), bundledCatalogFile("heroes/$id.json").readText())
            hero.copy(portrait = null, cards = hero.cards.map { it.copy(image = null) })
        },
    )
}
