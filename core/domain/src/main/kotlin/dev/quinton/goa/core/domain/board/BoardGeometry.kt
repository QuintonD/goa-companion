package dev.quinton.goa.core.domain.board

import dev.quinton.goa.core.model.board.BoardLane
import dev.quinton.goa.core.model.board.BoardMap
import dev.quinton.goa.core.model.board.BoardSpace
import dev.quinton.goa.core.model.board.BoardZone
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.SpawnKind
import dev.quinton.goa.core.model.board.SpawnPoint
import dev.quinton.goa.core.model.board.ZoneKind
import dev.quinton.goa.core.model.match.Team

/**
 * A [BoardMap] indexed for lookups. Build once per map ([of]); everything here is static map data
 * (pieces live in [dev.quinton.goa.core.model.board.BoardState]).
 */
class BoardGeometry private constructor(val map: BoardMap) {
    val spaces: Map<Hex, BoardSpace> = map.spaces.associateBy { it.hex }
    val terrain: Set<Hex> = map.terrain.toSet()
    val zones: Map<String, BoardZone> = map.zones.associateBy { it.id }
    val lanes: Map<String, BoardLane> = map.lanes.associateBy { it.id }

    /** Spaces per zone id, in map order. */
    val zoneSpaces: Map<String, List<Hex>> = map.spaces.groupBy({ it.zone }, { it.hex })

    /**
     * Zone adjacency (p.18 "Adjacent"): "Two Zones are adjacent if they have at least two nonterrain
     * spaces that are adjacent to each other", i.e. a nonterrain space of one borders a nonterrain
     * space of the other. Static: tokens and units have no effect on Zone adjacency (p.11).
     */
    val adjacentZones: Map<String, Set<String>> = buildMap<String, MutableSet<String>> {
        map.zones.forEach { put(it.id, mutableSetOf()) }
        map.spaces.forEach { space ->
            HexGrid.neighbors(space.hex).forEach { n ->
                val other = spaces[n]?.zone
                if (other != null && other != space.zone) {
                    getOrPut(space.zone) { mutableSetOf() } += other
                    getOrPut(other) { mutableSetOf() } += space.zone
                }
            }
        }
    }

    fun isSpace(hex: Hex): Boolean = hex in spaces

    fun isTerrain(hex: Hex): Boolean = hex in terrain

    fun zoneOf(hex: Hex): String? = spaces[hex]?.zone

    fun throneZone(team: Team): String? = map.zones.firstOrNull { it.kind == ZoneKind.THRONE && it.team == team }?.id

    /** Hero spawn points of [team], in map order. */
    fun heroSpawns(team: Team): List<Hex> =
        map.spaces.filter { it.spawn == SpawnPoint(team, SpawnKind.HERO) }.map { it.hex }

    /** Minion spawn points in [zoneId], in map order. */
    fun minionSpawns(zoneId: String): List<BoardSpace> =
        map.spaces.filter { space -> space.zone == zoneId && space.spawn?.kind?.minion != null }

    /** Lanes whose Zones include [zoneId]. */
    fun lanesThrough(zoneId: String): List<BoardLane> = map.lanes.filter { zoneId in it.zones }

    companion object {
        private val cache = HashMap<String, BoardGeometry>()

        /** The geometry of [map], built once per loaded map instance. */
        fun of(map: BoardMap): BoardGeometry = synchronized(cache) {
            cache[map.id]?.takeIf { it.map === map } ?: BoardGeometry(map).also { cache[map.id] = it }
        }
    }
}
