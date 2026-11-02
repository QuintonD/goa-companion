package dev.quinton.goa.server.game.room

import dev.quinton.goa.core.game.engine.GameEngine
import dev.quinton.goa.core.model.catalog.CatalogFiles
import java.nio.file.Path

/** The rules on a plain JVM, from the same catalog files the apps bundle. */
object RulesLoader {
    /**
     * [assets] holds `catalog/`. No match rules are passed: the engine's built-in counter table is the
     * same rulebook data, and the phones' results are the reference (core/game's `Counters`).
     */
    fun engine(assets: Path): GameEngine {
        fun read(path: String): String = assets.resolve(path).toFile().readText()
        val catalog = CatalogFiles.load(::read)
        val maps = CatalogFiles.parseMapIndex(read(CatalogFiles.MAPS_INDEX_PATH)).map {
            CatalogFiles.parseMap(it, read(CatalogFiles.mapPath(it)))
        }
        return GameEngine(catalog, maps)
    }
}
