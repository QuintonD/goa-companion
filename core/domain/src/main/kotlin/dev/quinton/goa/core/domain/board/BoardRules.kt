package dev.quinton.goa.core.domain.board

import dev.quinton.goa.core.model.board.BoardPiece
import dev.quinton.goa.core.model.board.BoardState
import dev.quinton.goa.core.model.board.HeroPiece
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionPiece
import dev.quinton.goa.core.model.match.Team

/**
 * What the board sandbox measures (ADR-033). It never resolves a card: it answers "where can this
 * hero get to" and "what is in range", from the rulebook's movement and distance rules. Page
 * references are the rulebook's printed pages (DOMAIN.md *Board sandbox*).
 */
object BoardRules {
    /** The piece on each occupied space (pieces in the tray are left out). */
    fun occupancy(state: BoardState): Map<Hex, BoardPiece> =
        state.pieces.mapNotNull { piece -> piece.at?.let { it to piece } }.toMap()

    /**
     * Obstacles (p.18 "Obstacle"): "Units, terrain (including printed on the board), tokens, as
     * well as any objects represented by miniatures". Every hex that is not a space of the map is
     * terrain (p.19 "Terrain": "Any board space without a full grid outline counts as terrain and is
     * an obstacle, including the water spaces surrounding the board").
     */
    fun isObstacle(geometry: BoardGeometry, occupied: Map<Hex, BoardPiece>, hex: Hex): Boolean =
        !geometry.isSpace(hex) || hex in occupied

    /** An empty space (p.17 "Empty"): "a space with no obstacles". */
    fun isEmpty(geometry: BoardGeometry, occupied: Map<Hex, BoardPiece>, hex: Hex): Boolean =
        geometry.isSpace(hex) && hex !in occupied

    /**
     * Spaces a unit at [from] can end a move of up to [steps] spaces on, with the fewest spaces
     * needed for each (p.11 Movement: "you may move your hero up to the number of spaces shown";
     * "Units cannot move through or into spaces with obstacles").
     * With [ignoreObstacles] (p.18 "Ignoring Obstacles": "allowed to enter a space with an obstacle
     * and leave that space in any direction, while moving. However, a unit can never end its
     * movement on the same space as an obstacle") the unit may pass through units, tokens and any
     * terrain, the water around the board included, but only ends on an empty space.
     * [from] itself is not included (moving zero spaces is always allowed).
     */
    fun movementReach(
        geometry: BoardGeometry,
        state: BoardState,
        from: Hex,
        steps: Int,
        ignoreObstacles: Boolean = false,
    ): Map<Hex, Int> {
        if (steps <= 0) return emptyMap()
        val occupied = occupancy(state) - from
        val best = HashMap<Hex, Int>()
        val frontier = ArrayDeque<Pair<Hex, Int>>()
        best[from] = 0
        frontier += from to 0
        while (frontier.isNotEmpty()) {
            val (hex, cost) = frontier.removeFirst()
            if (cost == steps) continue
            HexGrid.neighbors(hex)
                .filter { next -> next !in best && (ignoreObstacles || isEmpty(geometry, occupied, next)) }
                .forEach { next ->
                    best[next] = cost + 1
                    frontier += next to cost + 1
                }
        }
        return best.filterKeys { it != from && isEmpty(geometry, occupied, it) }
    }

    /**
     * Fast Travel destinations for [hero] (p.11): any empty space in its own Zone or an adjacent
     * Zone, when there are no enemy heroes or minions in the Zone it travels from nor in the Zone it
     * travels to. No path is needed ("You do not need to have a path of empty spaces"); the
     * destination is empty because Fast Travel places the hero (p.18 "Place": "A game piece can only
     * be placed into an empty space"). Zone adjacency ignores tokens and units (p.11). Empty when
     * the hero is not on the board or its Zone holds an enemy.
     */
    fun fastTravel(geometry: BoardGeometry, state: BoardState, hero: HeroPiece): Set<Hex> {
        val from = hero.at ?: return emptySet()
        val fromZone = geometry.zoneOf(from) ?: return emptySet()
        val occupied = occupancy(state)
        val enemyZones = state.pieces
            .filter { it.isEnemyUnitOf(hero.team) }
            .mapNotNullTo(HashSet()) { piece -> piece.at?.let(geometry::zoneOf) }
        if (fromZone in enemyZones) return emptySet()
        val zones = (geometry.adjacentZones[fromZone].orEmpty() + fromZone) - enemyZones
        return zones.flatMap { geometry.zoneSpaces[it].orEmpty() }
            .filterTo(HashSet()) { it != from && isEmpty(geometry, occupied, it) }
    }

    /**
     * Spaces of the map 1..[distance] spaces from [from], obstacles counted (p.9: "With a
     * Range/Radius value of 3, your primary action can affect targets up to 3 spaces away"; "There
     * are no 'line of sight' rules"; p.17 "Farther"): what a Range or Radius value covers. [from]
     * is left out: it holds the acting unit, which a card never affects unless it says so.
     */
    fun within(geometry: BoardGeometry, from: Hex, distance: Int): Set<Hex> =
        HexGrid.within(from, distance).filterTo(HashSet()) { geometry.isSpace(it) || geometry.isTerrain(it) }

    /**
     * Where a defeated hero of [team] may respawn (p.7 "Hero Respawn"): "on any empty Spawn point in
     * your Throne Zone ... If no empty Spawn points are available, respawn in the nearest empty
     * space, adjacent to a Spawn point in your Throne Zone". When no space next to a Spawn point is
     * empty either, the empty spaces nearest to one are offered (our reading of "nearest").
     */
    fun heroRespawn(geometry: BoardGeometry, state: BoardState, team: Team): Set<Hex> {
        val occupied = occupancy(state)
        val spawns = geometry.heroSpawns(team)
        val emptySpawns = spawns.filterTo(HashSet()) { it !in occupied }
        if (emptySpawns.isNotEmpty() || spawns.isEmpty()) return emptySpawns
        val empty = geometry.spaces.keys.filter { it !in occupied }
        val nearest = empty.minOfOrNull { hex -> spawns.minOf { HexGrid.distance(it, hex) } } ?: return emptySet()
        return empty.filterTo(HashSet()) { hex -> spawns.minOf { HexGrid.distance(it, hex) } == nearest }
    }

    /**
     * Where a minion at [from] outside its Battle Zone [zoneId] goes back to (p.18 "Minions Outside
     * of the Battle Zone"): "move it by the shortest path of empty spaces to an empty space in the
     * same Battle Zone" (the end spaces of every shortest path; "the minion's team decides"); "If
     * there is no path to the Battle Zone, or the minion cannot move, place that minion in the
     * nearest empty space of the Battle Zone". Empty when the Zone has no empty space.
     */
    fun returnToBattleZone(geometry: BoardGeometry, state: BoardState, from: Hex, zoneId: String): Set<Hex> {
        if (geometry.zoneOf(from) == zoneId) return setOf(from)
        val occupied = occupancy(state) - from
        val seen = hashSetOf(from)
        var frontier = listOf(from)
        while (frontier.isNotEmpty()) {
            val next = frontier.flatMap(HexGrid::neighbors).distinct()
                .filter { it !in seen && isEmpty(geometry, occupied, it) }
            seen += next
            val arrived = next.filterTo(HashSet()) { geometry.zoneOf(it) == zoneId }
            if (arrived.isNotEmpty()) return arrived
            frontier = next
        }
        return BoardSetup.nearestEmpty(
            geometry,
            state.copy(
                pieces = state.pieces.filter {
                    it.at != from
                },
            ),
            zoneId,
            from,
        )
    }

    /**
     * Where a unit or token at [target] ends when pushed [spaces] spaces by a unit at [pusher]
     * (p.18 "Push"): "move it in a straight line directly away from the pushing unit"; "If the
     * target cannot be moved the full distance, move it as far as possible; this can result in
     * pushing it 0 spaces". With [ignoreObstacles] "the pushed target is moved through obstacles as
     * long as it can reach an empty space": it ends on the farthest empty space within [spaces].
     * Returns [target] when the two are not in a straight line (no direction to push in).
     */
    fun push(
        geometry: BoardGeometry,
        state: BoardState,
        pusher: Hex,
        target: Hex,
        spaces: Int,
        ignoreObstacles: Boolean = false,
    ): Hex {
        val dir = HexGrid.straightLineDirection(pusher, target) ?: return target
        val step = HexGrid.DIRECTIONS[dir]
        val occupied = occupancy(state) - target
        var end = target
        var at = target
        repeat(spaces.coerceAtLeast(0)) {
            at += step
            val empty = isEmpty(geometry, occupied, at)
            if (empty) {
                end = at
            } else if (!ignoreObstacles) {
                return end
            }
        }
        return end
    }

    private fun BoardPiece.isEnemyUnitOf(team: Team): Boolean = when (this) {
        is HeroPiece -> this.team != team
        is MinionPiece -> this.team != team
        else -> false
    }
}
