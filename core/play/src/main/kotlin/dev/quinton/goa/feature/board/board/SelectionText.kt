package dev.quinton.goa.feature.board.board

import dev.quinton.goa.core.model.board.BoardPiece
import dev.quinton.goa.core.model.board.HeroPiece
import dev.quinton.goa.core.model.board.MinionPiece
import dev.quinton.goa.core.model.board.TokenPiece
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.board.BoardText
import dev.quinton.goa.feature.board.hero

/*
 * The sandbox selection's words (feature:board SelectionPanel), shared by the phone and the web app.
 */

/** The selected piece's name: the hero, the minion's type, or the token's label. */
fun pieceTitle(state: BoardUiState.Ready, piece: BoardPiece): String = when (piece) {
    is HeroPiece -> state.heroes.hero(piece.heroId).name
    is MinionPiece -> BoardText.minion(piece.type)
    is TokenPiece -> "Token: ${piece.label}"
}

/** Where the selected piece is: its Zone ("in the tray" while it waits) and Battle Zone. */
fun pieceWhere(state: BoardUiState.Ready, piece: BoardPiece): String {
    val zone = piece.at?.let { state.geometry.zoneOf(it) }
    val battleLane = state.map.lanes.firstOrNull {
        (state.board.state.battleZones[it.id] ?: it.startingBattleZone) == zone
    }
    return listOfNotNull(
        if (piece.at == null) "in the tray" else BoardText.zone(state.map, zone),
        battleLane?.let { BoardText.battleZone(state.map, it.id) },
    ).joinToString(" · ")
}

/** The panel header's line: team, a minion's Lane (double-lane maps, p.20), Zone and Battle Zone. */
fun pieceSubtitle(state: BoardUiState.Ready, piece: BoardPiece): String {
    val team: Team? = when (piece) {
        is HeroPiece -> piece.team
        is MinionPiece -> piece.team
        is TokenPiece -> piece.team
    }
    // A minion on a double-lane map is bound to its Lane (p.20).
    val boundTo = (piece as? MinionPiece)?.let { BoardText.lane(state.map, state.geometry.laneOf(it)) }
    return listOfNotNull(
        team?.let { "${BoardText.team(it)} team" },
        boundTo?.let { "$it minion" },
        pieceWhere(state, piece),
    ).joinToString(" · ")
}
