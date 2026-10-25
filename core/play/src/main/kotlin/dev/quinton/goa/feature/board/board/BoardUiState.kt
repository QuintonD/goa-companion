package dev.quinton.goa.feature.board.board

import dev.quinton.goa.core.domain.board.BoardGeometry
import dev.quinton.goa.core.domain.board.BoardRules.MovePath
import dev.quinton.goa.core.model.board.BoardMap
import dev.quinton.goa.core.model.board.BoardPiece
import dev.quinton.goa.core.model.board.HeroPiece
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.board.SavedBoard
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.board.BoardHero

/** What a tap on the map does right now. */
sealed interface BoardTool {
    /** Tap selects a piece; with one selected, tap an empty space to move it there. */
    data object Move : BoardTool

    /** Tap any space to read its distance from the selected piece. */
    data object Measure : BoardTool

    /** Tap an empty space to put [piece] there. */
    data class Place(val piece: NewPiece) : BoardTool
}

/** A piece the user is adding (Add → then tap where). */
sealed interface NewPiece {
    data class Hero(val heroId: String, val team: Team) : NewPiece

    data class Minion(val team: Team, val type: MinionType) : NewPiece

    data class Token(val label: String, val team: Team?) : NewPiece
}

/** The overlay switches of one selected piece (kept per piece while the board is open). */
data class OverlaySettings(
    /** The hero's card being measured; null = none chosen yet. */
    val cardId: String? = null,
    val movement: Boolean = true,
    /** Which obstacles a move may pass (p.11, p.18 "Ignoring Obstacles", p.18 "Immune": tokens). */
    val path: MovePath = MovePath.NORMAL,
    /** The card says "in a straight line" (p.19 "Straight Line"). */
    val straightLine: Boolean = false,
    val fastTravel: Boolean = false,
    val range: Boolean = true,
    val radius: Boolean = true,
    /** + from items tucked under the hero (each item is +1 to one value). */
    val movementBonus: Int = 0,
    val rangeBonus: Int = 0,
    val radiusBonus: Int = 0,
    /** Minions and tokens: the Movement N to show. */
    val steps: Int = 1,
)

/** What the overlays of the selected piece cover, worked out by `BoardRules` (never by card effects). */
data class OverlayUi(
    val settings: OverlaySettings,
    /** The chosen card (heroes only). */
    val card: Card? = null,
    /** Movement value in use (card value + items, or the stepper); null = the card has none. */
    val movementValue: Int? = null,
    /** Spaces the piece can end a move on, with the fewest spaces needed. */
    val movement: Map<Hex, Int> = emptyMap(),
    val fastTravel: Set<Hex> = emptySet(),
    val rangeValue: Int? = null,
    val range: Set<Hex> = emptySet(),
    val radiusValue: Int? = null,
    val radius: Set<Hex> = emptySet(),
    /** A minion outside its own Lane's Battle Zone: where it goes back to (p.18, p.20). */
    val returnTo: Set<Hex> = emptySet(),
) {
    val any: Boolean get() = movement.isNotEmpty() || fastTravel.isNotEmpty() || range.isNotEmpty() ||
        radius.isNotEmpty()

    /** Every space the overlays mark except Fast Travel (whole Zones): what the view keeps in sight. */
    val reach: Set<Hex> get() = movement.keys + range + radius + returnTo
}

/** A measured space: [distance] in spaces, obstacles counted (p.18); [straight] = in a straight line. */
data class MeasureUi(val target: Hex, val distance: Int, val straight: Boolean)

/** A piece waiting in the tray, and the spaces it should go to (p.8: the nearest empty spaces of the Battle Zone). */
data class WaitingUi(val piece: BoardPiece, val targets: Set<Hex>)

/** Pushing made the losing team's Throne Zone the Battle Zone: [winner] wins (p.8). */
data class ThroneCaptured(val winner: Team)

/** A short message for the snackbar; [id] tells two equal messages apart. */
data class BoardHint(val text: String, val id: Long)

/** What TalkBack reads after a change ("Brogan moved 2 spaces, to …"); [id] makes each one new. */
data class Announcement(val text: String, val id: Long)

sealed interface BoardUiState {
    data object Loading : BoardUiState

    /** The board was deleted, can't be read, or its map isn't bundled in this build. */
    data class Missing(val message: String) : BoardUiState

    data class Ready(
        val board: SavedBoard,
        val map: BoardMap,
        val heroes: Map<String, BoardHero>,
        /** Products the group owns (Settings); empty = all. The Add sheet lists these heroes first. */
        val ownedProducts: Set<String> = emptySet(),
        val selectedId: String? = null,
        val tool: BoardTool = BoardTool.Move,
        /** Whose Throne Zone is at the bottom (portrait) or left (landscape); "Flip" swaps. */
        val bottomTeam: Team = Team.RED,
        val overlay: OverlayUi? = null,
        val measure: MeasureUi? = null,
        val waiting: List<WaitingUi> = emptyList(),
        val activeWaitingId: String? = null,
        val canUndo: Boolean = false,
        val canRedo: Boolean = false,
        val throneCaptured: ThroneCaptured? = null,
        val hint: BoardHint? = null,
        val announcement: Announcement? = null,
    ) : BoardUiState {
        val geometry: BoardGeometry get() = BoardGeometry.of(map)

        val pieces: List<BoardPiece> get() = board.state.pieces

        val selected: BoardPiece? get() = selectedId?.let { id -> pieces.firstOrNull { it.id == id } }

        val activeWaiting: WaitingUi? get() = waiting.firstOrNull { it.piece.id == activeWaitingId }

        /**
         * Taps on empty spaces place the active tray piece: nothing on the board is selected (a
         * selected board piece moves instead), and nothing is being added or measured.
         */
        val trayActive: Boolean
            get() = tool == BoardTool.Move && activeWaiting != null && selected?.at == null

        /** The spaces the active tray piece may go to, when taps place it. */
        val trayTargets: Set<Hex> get() = if (trayActive) activeWaiting?.targets.orEmpty() else emptySet()

        /** The current Battle Zone of each lane, in map lane order. */
        val battleZones: List<String>
            get() = map.lanes.map { lane -> board.state.battleZones[lane.id] ?: lane.startingBattleZone }

        /** Heroes already on this board (a hero is on a board at most once). */
        val heroIdsOnBoard: Set<String>
            get() = pieces.filterIsInstance<HeroPiece>().mapTo(HashSet()) { it.heroId }
    }
}

/** A tap on the map, resolved by the screen: a piece, or a space (which may be off the board). */
sealed interface MapTap {
    data class Piece(val pieceId: String) : MapTap

    data class Space(val hex: Hex) : MapTap
}

sealed interface BoardAction {
    data class Select(val pieceId: String) : BoardAction

    data object Deselect : BoardAction

    /**
     * A piece dragged onto [hex]: moves there, or swaps with the piece standing there. Dropped where
     * it started, it is just selected.
     */
    data class Drop(val pieceId: String, val hex: Hex) : BoardAction

    data class ChooseCard(val cardId: String?) : BoardAction

    data object ToggleMovement : BoardAction

    data class SetPath(val path: MovePath) : BoardAction

    data object ToggleStraightLine : BoardAction

    data object ToggleFastTravel : BoardAction

    data object ToggleRange : BoardAction

    data object ToggleRadius : BoardAction

    data class MovementBonus(val delta: Int) : BoardAction

    data class RangeBonus(val delta: Int) : BoardAction

    data class RadiusBonus(val delta: Int) : BoardAction

    data class Steps(val delta: Int) : BoardAction

    data object StartMeasure : BoardAction

    data class Add(val piece: NewPiece) : BoardAction

    /** Leaves Measure or Add (back to moving pieces). */
    data object CancelTool : BoardAction

    data object Remove : BoardAction

    data object Undo : BoardAction

    data object Redo : BoardAction

    data object ResetToOpening : BoardAction

    data class Push(val laneId: String, val winner: Team) : BoardAction

    /** A tray piece tapped in the tray: taps on the board place it next. */
    data class ActivateWaiting(val pieceId: String) : BoardAction

    data object Flip : BoardAction

    data object DismissThrone : BoardAction

    /** The snackbar showed hint [id]. */
    data class HintShown(val id: Long) : BoardAction
}
