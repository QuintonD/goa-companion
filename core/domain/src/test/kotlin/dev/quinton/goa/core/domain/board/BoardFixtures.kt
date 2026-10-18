package dev.quinton.goa.core.domain.board

import dev.quinton.goa.core.domain.timer.bundledAsset
import dev.quinton.goa.core.model.board.BoardLane
import dev.quinton.goa.core.model.board.BoardMap
import dev.quinton.goa.core.model.board.BoardSpace
import dev.quinton.goa.core.model.board.BoardZone
import dev.quinton.goa.core.model.board.HeroPiece
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionPiece
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.board.SpawnKind
import dev.quinton.goa.core.model.board.SpawnPoint
import dev.quinton.goa.core.model.board.TokenPiece
import dev.quinton.goa.core.model.board.ZoneKind
import dev.quinton.goa.core.model.match.Team
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The bundled maps, read from `app/src/main/assets/catalog/maps` like the app reads them. */
object BundledMaps {
    @Serializable
    private data class Index(val maps: List<String>)

    private val json = Json { ignoreUnknownKeys = false }

    val ids: List<String> by lazy { json.decodeFromString(Index.serializer(), bundledAsset("maps/index.json")).maps }

    val all: List<BoardMap> by lazy {
        ids.map { json.decodeFromString(BoardMap.serializer(), bundledAsset("maps/$it.json")) }
    }

    fun map(id: String): BoardMap = all.first { it.id == id }

    fun geometry(id: String): BoardGeometry = BoardGeometry.of(map(id))
}

/**
 * Small synthetic maps for the rule tests. [strip]: a band of five rows (r 0..4) split into five
 * Zones of three columns each along q: `red-throne` (q 0..2), `a` (3..5), `b` (6..8), `c` (9..11),
 * `blue-throne` (12..14); one lane through all of them with `b` as the Battle Zone. Holes (terrain)
 * as given. Spawn points as given.
 */
object Fixtures {
    val ZONES = listOf("red-throne", "a", "b", "c", "blue-throne")

    fun strip(
        holes: Set<Hex> = emptySet(),
        spawns: Map<Hex, SpawnPoint> = emptyMap(),
        zoneOf: (Hex) -> String = { ZONES[it.q / 3] },
    ): BoardMap {
        val hexes = (0..14).flatMap { q -> (0..4).map { r -> Hex(q, r) } }.filter { it !in holes }
        return BoardMap(
            id = "strip-${holes.hashCode()}-${spawns.hashCode()}",
            name = "Strip",
            lanes = listOf(BoardLane("lane-1", ZONES, startingBattleZone = "b")),
            zones = ZONES.map { id ->
                when (id) {
                    "red-throne" -> BoardZone(id, ZoneKind.THRONE, Team.RED)
                    "blue-throne" -> BoardZone(id, ZoneKind.THRONE, Team.BLUE)
                    else -> BoardZone(id, ZoneKind.LANE)
                }
            },
            spaces = hexes.map { BoardSpace(it.q, it.r, zoneOf(it), spawns[it]) },
            terrain = holes.toList(),
            printing = 2,
            verified = false,
        )
    }

    private var next = 0

    fun id(): String = "p${next++}"

    fun hero(team: Team, at: Hex?, heroId: String = "hero") = HeroPiece(id(), at, heroId, team)

    fun minion(team: Team, at: Hex?, type: MinionType = MinionType.MELEE) = MinionPiece(id(), at, team, type)

    fun token(at: Hex?) = TokenPiece(id(), at, "Token")

    fun spawn(team: Team, kind: SpawnKind) = SpawnPoint(team, kind)
}
