package dev.quinton.goa.feature.rules

import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.catalog.Hero
import dev.quinton.goa.core.model.rules.RulesReference
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

val bundledReference: RulesReference by lazy {
    RulesReference.parse(bundledCatalogFile("rules-reference.json").readText())
}

private val heroJson = Json { ignoreUnknownKeys = false }

fun bundledHero(id: String): Hero = heroJson.decodeFromString(
    Hero.serializer(),
    bundledCatalogFile("heroes/$id.json").readText(),
)

/** The real heroes whose cards matter for the ladder, plus two that don't. */
val ladderCatalog: Catalog by lazy {
    Catalog(
        dataVersion = 1,
        printing = 2,
        products = emptyList(),
        heroes = listOf("arien", "bain", "brogan", "gydion", "rowenna", "ursafar").map(::bundledHero),
    )
}
