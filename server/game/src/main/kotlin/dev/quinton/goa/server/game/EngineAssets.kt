package dev.quinton.goa.server.game

import dev.quinton.goa.core.domain.setup.MatchRules
import dev.quinton.goa.core.game.engine.GameEngine
import dev.quinton.goa.core.model.board.BoardMap
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.catalog.CatalogIndex
import dev.quinton.goa.core.model.catalog.Hero
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val assetJson = Json { ignoreUnknownKeys = true }

@Serializable
private data class MapIndex(val maps: List<String>)

/** The rules engine over `<assets>/catalog`, the same files the app bundles. */
fun loadEngine(assetsDir: Path): GameEngine {
    val root = assetsDir.resolve("catalog")
    fun text(name: String) = Files.readString(root.resolve(name))
    val index = assetJson.decodeFromString(CatalogIndex.serializer(), text("index.json"))
    val heroes = index.heroes.map { assetJson.decodeFromString(Hero.serializer(), text("heroes/$it.json")) }
    val maps = assetJson.decodeFromString(MapIndex.serializer(), text("maps/index.json")).maps.map {
        assetJson.decodeFromString(BoardMap.serializer(), text("maps/$it.json"))
    }
    return GameEngine(
        Catalog(index.dataVersion, index.printing, index.products, heroes),
        maps,
        MatchRules.parse(text("match-setup.json")),
    )
}
