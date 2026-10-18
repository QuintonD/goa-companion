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
     * Obstacles (p.19): units, terrain printed on the board and tokens. A space that is not on the
     * board can't be entered at all, so it counts as one too.
     */
    fun isObstacle(geometry: BoardGeometry, occupied: Map<Hex, BoardPiece>, hex: Hex): Boolean =
        !geometry.isSpace(hex) || hex in occupied

    /** An empty space (p.18): a space of the board with no obstacle. */
    fun isEmpty(geometry: BoardGeometry, occupied: Map<Hex, BoardPiece>, hex: Hex): Boolean =
        geometry.isSpace(hex) && hex !in occupied

    /**
     * Spaces a unit at [from] can end a move of up to [steps] spaces on, with the fewest spaces
     * needed for each (p.11 Movement: "Units cannot move through or into spaces with obstacles").
     * With [ignoreObstacles] (p.19 "Ignoring Obstacles") the unit may enter and leave obstacle
     * spaces, terrain included, but never ends its movement on one; it still can't leave the board.
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
                .filter { next ->
                    next !in best && when {
                        ignoreObstacles -> geometry.isSpace(next) || geometry.isTerrain(next)
                        else -> isEmpty(geometry, occupied, next)
                    }
                }
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
     * travels to. No path is needed. Empty when the hero is not on the board or its Zone holds an enemy.
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
     * Spaces within [distance] of [from] (1..distance spaces away, obstacles counted, p.18 "Farther /
     * Farthest"): what a Range or Radius value covers. Only spaces on the board.
     */
    fun within(geometry: BoardGeometry, from: Hex, distance: Int): Set<Hex> =
        HexGrid.within(from, distance).filterTo(HashSet()) { geometry.isSpace(it) || geometry.isTerrain(it) }

    private fun BoardPiece.isEnemyUnitOf(team: Team): Boolean = when (this) {
        is HeroPiece -> this.team != team
        is MinionPiece -> this.team != team
        else -> false
    }
}
