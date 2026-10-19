package dev.quinton.goa.feature.board.map

import dev.quinton.goa.core.domain.board.BoardRules
import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.model.board.BoardPiece
import dev.quinton.goa.core.model.board.HeroPiece
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.feature.board.BoardText
import dev.quinton.goa.feature.board.board.BoardTool
import dev.quinton.goa.feature.board.board.BoardUiState
import dev.quinton.goa.feature.board.hero

/** What TalkBack reads for a space, and what its click does. */
internal object SpaceText {
    fun describe(state: BoardUiState.Ready, hex: Hex, occupant: BoardPiece?): String {
        val map = state.map
        val space = state.geometry.spaces[hex]
        val zone = space?.zone
        val battleLane = map.lanes.firstOrNull { (state.board.state.battleZones[it.id] ?: it.startingBattleZone) == zone }
        val selected = state.selected
        return buildList {
            add(occupant?.let { BoardText.piece(it) { id -> state.heroes.hero(id).name } } ?: "Empty space")
            add(BoardText.zone(map, zone))
            battleLane?.let { add(BoardText.battleZone(map, it.id)) }
            space?.spawn?.let { add("${BoardText.team(it.team)} ${BoardText.spawn(it.kind)}") }
            if (occupant != null && occupant.id == state.selectedId) add("selected")
            // How far from the selected piece, so a space can be found by its distance.
            val from = selected?.at
            if (from != null && from != hex) {
                val name = (selected as? HeroPiece)?.let { state.heroes.hero(it.heroId).name }
                    ?: BoardText.piece(selected) { id -> state.heroes.hero(id).name }
                add("${BoardText.spaces(HexGrid.distance(from, hex))} from $name")
            }
            val overlay = state.overlay
            overlay?.movement?.get(hex)?.let { add("Movement: ${BoardText.spaces(it)}") }
            if (overlay?.fastTravel?.contains(hex) == true) add("Fast Travel")
            if (overlay?.range?.contains(hex) == true) add("in Range")
            if (overlay?.radius?.contains(hex) == true) add("in Radius")
            if (overlay?.returnTo?.contains(hex) == true) add("back to its Battle Zone")
            if (hex in state.trayTargets) add("a nearest empty space")
            state.measure?.takeIf { it.target == hex }?.let {
                val line = if (it.straight) "in a straight line" else "not in a straight line"
                add("${BoardText.spaces(it.distance)}, $line")
            }
        }.joinToString(", ")
    }

    /** The click label of a space, or null when tapping it does nothing. */
    fun action(state: BoardUiState.Ready, hex: Hex, occupant: BoardPiece?): String? {
        val empty = BoardRules.isEmpty(state.geometry, BoardRules.occupancy(state.board.state), hex)
        return when {
            state.tool == BoardTool.Measure -> "Measure"
            state.tool is BoardTool.Place -> if (empty) "Place here" else null
            occupant != null -> if (occupant.id == state.selectedId) null else "Select"
            state.selected?.at != null -> if (empty) "Move here" else null
            state.trayActive -> when {
                state.trayTargets.isNotEmpty() -> if (hex in state.trayTargets) "Place here" else null
                empty -> "Place here"
                else -> null
            }
            else -> null
        }
    }
}
