package dev.quinton.goa.core.domain.board

import dev.quinton.goa.core.model.board.BoardPiece
import dev.quinton.goa.core.model.board.BoardState
import dev.quinton.goa.core.model.board.HeroPiece
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionPiece
import dev.quinton.goa.core.model.board.TokenPiece
import dev.quinton.goa.core.model.match.Team

/**
 * What the board sandbox measures (ADR-033). It never resolves a card: it answers "where can this
 * hero get to" and "what is in range", from the rulebook's movement and distance rules. Page
 * references are the rulebook's printed pages (DOMAIN.md *Board sandbox*).
 */
object BoardRules {
    /**
     * What a moving unit may pass through ([movementReach]). It always ends on an empty space: "a
     * unit can never end its movement on the same space as an obstacle" (p.18 "Ignoring Obstacles").
     */
    enum class MovePath {
        /** p.11: "Units cannot move through or into spaces with obstacles". */
        NORMAL,

        /**
         * Through tokens, never units or terrain: tokens a card says "units may move through" (e.g.
         * mines), or p.18 "Immune": "Units that ignore obstacles can move through an Immune unit, or
         * a token".
         */
        THROUGH_TOKENS,

        /**
         * p.18 "Ignoring Obstacles": "allowed to enter a space with an obstacle and leave that space in
         * any direction, while moving": units, tokens and terrain, the water around the map included.
         */
        IGNORE_OBSTACLES,
    }

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
     * "Units cannot move through or into spaces with obstacles"). [path] says which obstacles it may
     * pass ([MovePath]); it only ever ends on an empty space.
     * [straightLine]: the card says "in a straight line" (p.19 "Straight Line": "a sequence of spaces
     * arranged in a file"): the unit goes along one of the six directions (p.17 "Direction") and
     * stops before the first obstacle it may not pass.
     * [from] itself is not included (moving zero spaces is always allowed).
     */
    fun movementReach(
        geometry: BoardGeometry,
        state: BoardState,
        from: Hex,
        steps: Int,
        path: MovePath = MovePath.NORMAL,
        straightLine: Boolean = false,
    ): Map<Hex, Int> {
        if (steps <= 0) return emptyMap()
        val occupied = occupancy(state) - from
        val passable: (Hex) -> Boolean = when (path) {
            MovePath.NORMAL -> { hex -> isEmpty(geometry, occupied, hex) }

            MovePath.THROUGH_TOKENS -> { hex ->
                geometry.isSpace(hex) && (hex !in occupied || occupied[hex] is TokenPiece)
            }

            MovePath.IGNORE_OBSTACLES -> { _ -> true }
        }
        return if (straightLine) {
            straightReach(geometry, occupied, from, steps, passable)
        } else {
            anyReach(geometry, occupied, from, steps, passable)
        }
    }

    private fun anyReach(
        geometry: BoardGeometry,
        occupied: Map<Hex, BoardPiece>,
        from: Hex,
        steps: Int,
        passable: (Hex) -> Boolean,
    ): Map<Hex, Int> {
        val best = HashMap<Hex, Int>()
        val frontier = ArrayDeque<Pair<Hex, Int>>()
        best[from] = 0
        frontier += from to 0
        while (frontier.isNotEmpty()) {
            val (hex, cost) = frontier.removeFirst()
            if (cost == steps) continue
            HexGrid.neighbors(hex)
                .filter { next -> next !in best && passable(next) }
                .forEach { next ->
                    best[next] = cost + 1
                    frontier += next to cost + 1
                }
        }
        return best.filterKeys { it != from && isEmpty(geometry, occupied, it) }
    }

    private fun straightReach(
        geometry: BoardGeometry,
        occupied: Map<Hex, BoardPiece>,
        from: Hex,
        steps: Int,
        passable: (Hex) -> Boolean,
    ): Map<Hex, Int> {
        val out = HashMap<Hex, Int>()
        HexGrid.DIRECTIONS.forEach { step ->
            var at = from
            for (n in 1..steps) {
                at += step
                if (!passable(at)) break
                if (isEmpty(geometry, occupied, at)) out[at] = n
            }
        }
        return out
    }

    /**
     * Fast Travel destinations for [hero] (p.11): any empty space in its own Zone or an adjacent
     * Zone, when there are no enemy heroes or minions in the Zone it travels from nor in the Zone it
     * travels to. No path is needed ("You do not need to have a path of empty spaces"). The
     * destination is empty: "There can never be more than one obstacle in any one space" (p.18
     * "Obstacle"), and Fast Travel puts the hero there (goa2-faq: "a placement, not movement"; p.18
     * "Place": "A game piece can only be placed into an empty space"). Zone adjacency ignores tokens
     * and units (p.11). Empty when the hero is not on the board or its Zone holds an enemy.
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
     * Board spaces 1..[distance] spaces from [from], obstacles counted (p.9: "With a Range/Radius
     * value of 3, your primary action can affect targets up to 3 spaces away"; "There are no 'line
     * of sight' rules"; p.17 "Farther"): what a Range or Radius value covers. That includes terrain:
     * the enclosed rocks and the water around the map, which p.19 counts as board spaces ("Any board
     * space without a full grid outline counts as terrain ... including the water spaces surrounding
     * the board"), [APP] drawn and covered one ring deep ([BoardGeometry.edge]). [from] is left out:
     * it holds the acting unit, which a card never affects unless it says so.
     */
    fun within(geometry: BoardGeometry, from: Hex, distance: Int): Set<Hex> =
        HexGrid.within(from, distance).filterTo(HashSet()) { geometry.isOnBoard(it) }

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
     * The Battle Zone [minion] belongs to: the current Battle Zone of the Lane it is bound to (p.20:
     * "Minions are bound to the Battle Zone they were originally spawned in"), or null when its Lane
     * is unknown ([BoardGeometry.laneOf]).
     */
    fun battleZoneOf(geometry: BoardGeometry, state: BoardState, minion: MinionPiece): String? {
        val lane = geometry.laneOf(minion) ?: return null
        return state.battleZones[lane] ?: geometry.lanes[lane]?.startingBattleZone
    }

    /**
     * Where [minion] goes when it stands outside the Battle Zone of its own Lane (p.18 "Minions
     * Outside of the Battle Zone", bound per p.20). Empty when it stands in that Battle Zone, waits
     * in the tray, or its Lane is unknown.
     */
    fun returnToBattleZone(geometry: BoardGeometry, state: BoardState, minion: MinionPiece): Set<Hex> {
        val from = minion.at ?: return emptySet()
        val zone = battleZoneOf(geometry, state, minion) ?: return emptySet()
        if (geometry.zoneOf(from) == zone) return emptySet()
        return returnToBattleZone(geometry, state, from, zone)
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
            state.copy(pieces = state.pieces.filter { it.at != from }),
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
     * Null when the two are not in a straight line: there is no direction "directly away" (the
     * table decides). One target at a time: "push all those units that many spaces at the same
     * time" (p.18, several targets) is not modelled.
     */
    fun push(
        geometry: BoardGeometry,
        state: BoardState,
        pusher: Hex,
        target: Hex,
        spaces: Int,
        ignoreObstacles: Boolean = false,
    ): Hex? {
        val dir = HexGrid.straightLineDirection(pusher, target) ?: return null
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
