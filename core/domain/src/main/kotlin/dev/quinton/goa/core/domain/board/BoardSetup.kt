package dev.quinton.goa.core.domain.board

import dev.quinton.goa.core.model.board.BoardLineup
import dev.quinton.goa.core.model.board.BoardPiece
import dev.quinton.goa.core.model.board.BoardState
import dev.quinton.goa.core.model.board.HeroPiece
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionPiece
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
     */
    fun opening(geometry: BoardGeometry, lineup: BoardLineup, newId: () -> String): BoardState {
        val pieces = mutableListOf<BoardPiece>()
        val battleZones = geometry.map.lanes.associate { it.id to it.startingBattleZone }
        battleZones.values.distinct().forEach { zone ->
            geometry.minionSpawns(zone).forEach { space ->
                val spawn = space.spawn ?: return@forEach
                val type = spawn.kind.minion ?: return@forEach
                pieces += MinionPiece(newId(), space.hex, spawn.team, type)
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
     * Battle Zone ([nearestEmpty]). Minions already waiting in the tray are left there: the tray
     * doesn't record which lane they belong to. On a double-lane map only [laneId] moves (p.20).
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

        val kept = state.pieces.filterNot { it is MinionPiece && it.at?.let(geometry::zoneOf) == current }
            .toMutableList()
        val waiting = mutableListOf<WaitingMinion>()
        geometry.minionSpawns(next).forEach { space ->
            val spawn = space.spawn ?: return@forEach
            val type = spawn.kind.minion ?: return@forEach
            val occupant = kept.firstOrNull { it.at == space.hex }
            if (occupant is TokenPiece) kept.remove(occupant)
            val minion = MinionPiece(newId(), space.hex, spawn.team, type)
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
