package dev.quinton.goa.feature.board.map

import dev.quinton.goa.core.domain.board.BoardRules
import dev.quinton.goa.core.model.board.BoardPiece
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
        val battle = zone != null && (zone in state.board.state.battleZones.values || (state.board.state.battleZones.isEmpty() && map.lanes.any { it.startingBattleZone == zone }))
        return buildList {
            add(occupant?.let { BoardText.piece(it) { id -> state.heroes.hero(id).name } } ?: "Empty space")
            add(BoardText.zone(map, zone))
            if (battle) add("Battle Zone")
            space?.spawn?.let { add("${BoardText.team(it.team)} ${BoardText.spawn(it.kind)}") }
            if (occupant != null && occupant.id == state.selectedId) add("selected")
            val overlay = state.overlay
            overlay?.movement?.get(hex)?.let { add("Movement: ${BoardText.spaces(it)}") }
            if (overlay?.fastTravel?.contains(hex) == true) add("Fast Travel")
            if (overlay?.range?.contains(hex) == true) add("in Range")
            if (overlay?.radius?.contains(hex) == true) add("in Radius")
            if (state.activeWaiting?.targets?.contains(hex) == true) add("a nearest empty space")
            state.measure?.takeIf { it.target == hex }?.let {
                add("${BoardText.spaces(it.distance)}, ${if (it.straight) "in a straight line" else "not in a straight line"}")
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
            state.activeWaiting != null -> if (empty) "Place here" else null
            state.selectedId != null && empty -> "Move here"
            else -> null
        }
    }
}
