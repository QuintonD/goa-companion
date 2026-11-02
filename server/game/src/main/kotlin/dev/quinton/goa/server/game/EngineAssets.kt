package dev.quinton.goa.server.game

import dev.quinton.goa.core.game.engine.GameEngine
import dev.quinton.goa.core.model.catalog.CatalogFiles
import java.nio.file.Path

/**
 * The rules on a plain JVM, from the same catalog files the apps bundle: [assetsDir] holds `catalog/`.
 * No match rules are passed, exactly as on the phones and in the browser (`PlayEngineModule`, `EngineHost`):
 * the engine's built-in counter table is the same rulebook data, and one rules build means one game.
 */
fun loadEngine(assetsDir: Path): GameEngine {
    fun read(path: String): String = assetsDir.resolve(path).toFile().readText()
    val catalog = CatalogFiles.load(::read)
    val maps = CatalogFiles.parseMapIndex(read(CatalogFiles.MAPS_INDEX_PATH)).map {
        CatalogFiles.parseMap(it, read(CatalogFiles.mapPath(it)))
    }
    return GameEngine(catalog, maps)
}
