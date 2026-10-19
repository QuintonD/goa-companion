package dev.quinton.goa.core.domain.board

import dev.quinton.goa.core.model.board.BoardLineup
import dev.quinton.goa.core.model.board.BoardPiece
import dev.quinton.goa.core.model.board.BoardState
import dev.quinton.goa.core.model.board.HeroPiece
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionPiece
import dev.quinton.goa.core.model.board.SpawnKind
import dev.quinton.goa.core.model.board.SpawnPoint
import dev.quinton.goa.core.model.board.TokenPiece
import dev.quinton.goa.core.model.match.Team

/** Setting up and changing a board the way the rulebook does (ADR-033). Pure; ids come from [newId]. */
object BoardSetup {
    /**
     * The opening position (p.6 steps 9–10, p.20): each lane's starting Battle Zone gets one minion
     * of the matching team and type on every minion spawn point ("on spaces marked with a minion
     * icon that matches the team color and the type of that minion"); heroes stand on their team's
     * hero spawn points in [lineup] order ("Place the hero miniatures on the marked spaces in their
     * corresponding Throne Zone"). With more heroes than spawn points, the rest go to empty spaces
     * adjacent to their team's occupied hero spawn points (p.20: "When playing with more than 3
     * heroes per team, place the remaining heroes into any empty spaces adjacent to their team's
     * occupied hero spawn points"), first free neighbour first; any still left wait in the tray.
     * Each minion is bound to its Lane (p.20, [MinionPiece.lane]).
     */
    fun opening(geometry: BoardGeometry, lineup: BoardLineup, newId: () -> String): BoardState {
        val pieces = mutableListOf<BoardPiece>()
        val battleZones = geometry.map.lanes.associate { it.id to it.startingBattleZone }
        battleZones.entries.distinctBy { it.value }.forEach { (lane, zone) ->
            geometry.minionSpawns(zone).forEach { space ->
                val spawn = space.spawn ?: return@forEach
                val type = spawn.kind.minion ?: return@forEach
                pieces += MinionPiece(newId(), space.hex, spawn.team, type, lane = lane)
            }
        }
        for (team in Team.entries) {
            val heroes = if (team == Team.RED) lineup.red else lineup.blue
            pieces += placeHeroes(geometry, pieces, team, heroes, newId)
        }
        return BoardState(mapId = geometry.map.id, pieces = pieces, battleZones = battleZones)
    }

    private fun placeHeroes(
        geometry: BoardGeometry,
        placed: List<BoardPiece>,
        team: Team,
        heroIds: List<String>,
        newId: () -> String,
    ): List<BoardPiece> {
        val taken = placed.mapNotNullTo(HashSet()) { it.at }
        val spawns = geometry.heroSpawns(team).filter { it !in taken }
        val out = mutableListOf<BoardPiece>()
        val onSpawns = heroIds.zip(spawns)
        onSpawns.forEach { (heroId, hex) ->
            out += HeroPiece(newId(), hex, heroId, team)
            taken += hex
        }
        heroIds.drop(onSpawns.size).forEach { heroId ->
            val spot = onSpawns.asSequence()
                .flatMap { (_, spawn) -> HexGrid.neighbors(spawn).asSequence() }
                .firstOrNull { geometry.isSpace(it) && it !in taken }
            out += HeroPiece(newId(), spot, heroId, team)
            if (spot != null) taken += spot
        }
        return out
    }

    /** A minion in the tray after a push, and the Spawn point it could not respawn on. */
    data class WaitingMinion(val piece: MinionPiece, val spawn: Hex)

    sealed interface PushResult {
        /** The lane moved on: [state] has the new Battle Zone and respawned minions. */
        data class Pushed(
            val state: BoardState,
            /** Minions whose spawn point was occupied by a non-token obstacle: they wait in the tray. */
            val waiting: List<WaitingMinion>,
        ) : PushResult

        /** The push made [winner]'s enemy Throne Zone the Battle Zone: [winner] wins (p.8 step 2). */
        data class ThroneCaptured(val winner: Team) : PushResult
    }

    /**
     * Pushes [laneId] (p.8 "Push the Lane", steps 2–3; flipping the Wave counter is left to the table):
     * "The next Zone towards the losing team's Throne becomes the new Battle Zone. If the enemy Throne
     * Zone itself becomes the new Battle Zone, the team who won the push wins the game." Then "Remove
     * all minions in the previous Battle Zone. Respawn one minion of the matching type in each Spawn
     * point in the new Battle Zone (If a Spawn point is occupied by a token, first remove that token,
     * then respawn the minion)." A minion whose Spawn point holds another obstacle ("a non-token
     * obstacle") waits in the tray, to be placed by its team in the nearest empty space of the
     * Battle Zone ([nearestEmpty]). On a double-lane map only [laneId] moves (p.20), and minions are
     * bound to their Lane ("Minions are bound to the Battle Zone they were originally spawned in"):
     * the removal takes the previous Battle Zone's minions except those bound to the other Lane, and
     * this Lane's minions still waiting in the tray (they belong to the previous Battle Zone too).
     * New minions are bound to [laneId].
     */
    fun pushLane(
        geometry: BoardGeometry,
        state: BoardState,
        laneId: String,
        winner: Team,
        newId: () -> String,
    ): PushResult {
        val lane = geometry.lanes[laneId] ?: return PushResult.Pushed(state, emptyList())
        val current = state.battleZones[laneId] ?: lane.startingBattleZone
        val index = lane.zones.indexOf(current)
        val nextIndex = if (winner == Team.RED) index + 1 else index - 1
        val next = lane.zones.getOrNull(nextIndex) ?: return PushResult.ThroneCaptured(winner)
        if (next == geometry.throneZone(winner.opponent)) return PushResult.ThroneCaptured(winner)

        val kept = state.pieces.filterNot { piece ->
            piece is MinionPiece && when (val at = piece.at) {
                null -> geometry.laneOf(piece) == laneId
                else -> geometry.zoneOf(at) == current && (geometry.laneOf(piece) ?: laneId) == laneId
            }
        }.toMutableList()
        val waiting = mutableListOf<WaitingMinion>()
        geometry.minionSpawns(next).forEach { space ->
            val spawn = space.spawn ?: return@forEach
            val type = spawn.kind.minion ?: return@forEach
            val occupant = kept.firstOrNull { it.at == space.hex }
            if (occupant is TokenPiece) kept.remove(occupant)
            val minion = MinionPiece(newId(), space.hex, spawn.team, type, lane = laneId)
            if (occupant == null || occupant is TokenPiece) {
                kept += minion
            } else {
                val inTray = minion.copy(at = null)
                kept += inTray
                waiting += WaitingMinion(inTray, space.hex)
            }
        }
        return PushResult.Pushed(state.copy(pieces = kept, battleZones = state.battleZones + (laneId to next)), waiting)
    }

    /**
     * Where a minion waiting in the tray may go (p.8: "the owning team must place that minion in the
     * nearest empty space of their choice in the Battle Zone"): the Battle Zone of its Lane (p.20),
     * and [APP] "nearest" measured from the Spawn point it could not take. The tray doesn't record
     * that Spawn point: waiting minions of one Lane, team and type take that Zone's Spawn points of
     * their type held by something else, in order (the order the push sent them to the tray). Null
     * when the minion
     * has no Lane or its Battle Zone has no such Spawn point: then any empty space will do.
     */
    fun waitingTargets(geometry: BoardGeometry, state: BoardState, minion: MinionPiece): Set<Hex>? {
        if (minion.at != null) return null
        val lane = geometry.laneOf(minion) ?: return null
        val zone = BoardRules.battleZoneOf(geometry, state, minion) ?: return null
        val kind = SpawnKind.entries.first { it.minion == minion.type }
        val spawns = geometry.minionSpawns(zone)
            .filter { it.spawn == SpawnPoint(minion.team, kind) }
            .map { it.hex }
        if (spawns.isEmpty()) return null
        val occupied = BoardRules.occupancy(state)
        // Taken = held by anything but a minion of this kind (those respawned there).
        val taken = spawns.filter { hex ->
            val occupant = occupied[hex]
            occupant != null && !(occupant is MinionPiece && occupant.team == minion.team && occupant.type == minion.type)
        }.ifEmpty { spawns }
        val queue = state.pieces.filter {
            it is MinionPiece && it.at == null && it.team == minion.team && it.type == minion.type &&
                geometry.laneOf(it) == lane
        }
        val index = queue.indexOfFirst { it.id == minion.id }.coerceAtLeast(0)
        return nearestEmpty(geometry, state, zone, taken[minOf(index, taken.lastIndex)])
    }

    /**
     * The empty spaces of [zoneId] nearest to [from] (p.8: a minion that can't respawn on its Spawn
     * point goes to "the nearest empty space of their choice in the Battle Zone"; distance counts
     * spaces with obstacles, p.18 "Nearest"). Empty when the Zone has no empty space.
     */
    fun nearestEmpty(geometry: BoardGeometry, state: BoardState, zoneId: String, from: Hex): Set<Hex> {
        val occupied = BoardRules.occupancy(state)
        val empty = geometry.zoneSpaces[zoneId].orEmpty().filter { it !in occupied }
        val nearest = empty.minOfOrNull { HexGrid.distance(from, it) } ?: return emptySet()
        return empty.filterTo(HashSet()) { HexGrid.distance(from, it) == nearest }
    }
}
