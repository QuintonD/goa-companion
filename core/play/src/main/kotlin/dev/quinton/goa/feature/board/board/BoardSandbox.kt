package dev.quinton.goa.feature.board.board

import dev.quinton.goa.core.domain.board.BoardGeometry
import dev.quinton.goa.core.domain.board.BoardRules
import dev.quinton.goa.core.domain.board.BoardSetup
import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.model.board.BoardLineup
import dev.quinton.goa.core.model.board.BoardMap
import dev.quinton.goa.core.model.board.BoardPiece
import dev.quinton.goa.core.model.board.BoardState
import dev.quinton.goa.core.model.board.HeroPiece
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionPiece
import dev.quinton.goa.core.model.board.SavedBoard
import dev.quinton.goa.core.model.board.TokenPiece
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.board.BoardHero
import dev.quinton.goa.feature.board.BoardText
import dev.quinton.goa.feature.board.defaultMap
import dev.quinton.goa.feature.board.hero
import dev.quinton.goa.feature.board.inOrderOf
import dev.quinton.goa.feature.board.sameLayoutAs
import dev.quinton.goa.feature.board.size

/**
 * One open board being edited in the sandbox (ADR-033): moves pieces freely (one per space),
 * measures with `BoardRules`, pushes lanes with `BoardSetup`; it never plays the game. The phone's
 * BoardViewModel and the web app's board screen both drive one (ADR-043): they load and store the
 * board, this holds the selection, the tools, the per-piece overlay switches, Undo/Redo of this
 * visit and works out everything the screen shows ([state]).
 *
 * [listener] hears what the platform keeps or writes: the selection and the flip (kept across a
 * rotation or process death on the phone), and every change of the board (to save).
 */
class BoardSandbox(
    initial: BoardUiState.Ready,
    private val newPieceId: () -> String,
    private val now: () -> Long,
    private val listener: Listener = object : Listener {},
) {
    /** What the platform keeps or writes. */
    interface Listener {
        fun selected(pieceId: String?) {}

        fun flipped(bottom: Team) {}

        /** The board changed (a move, an undo, a push…): save [board]. */
        fun changed(board: SavedBoard) {}
    }

    private val undo = ArrayDeque<BoardState>()
    private val redo = ArrayDeque<BoardState>()
    private val overlays = HashMap<String, OverlaySettings>()
    private var messages = 0L

    var state: BoardUiState.Ready = derive(initial)
        private set

    /**
     * A tap on the map. Returns true when it did anything (moved or placed a piece, changed the
     * selection, measured), so the screen arms a double tap to zoom only after a tap that did nothing.
     */
    fun tap(tap: MapTap): Boolean {
        val before = state
        val hex = when (tap) {
            is MapTap.Space -> tap.hex
            is MapTap.Piece -> before.pieces.firstOrNull { it.id == tap.pieceId }?.at
        }
        when (val tool = before.tool) {
            BoardTool.Measure -> if (hex != null && before.geometry.isOnBoard(hex) && before.selected?.at != null) {
                setReady { it.copy(measure = measure(it, hex)) }
            }

            is BoardTool.Place -> placeNew(before, tool.piece, hex)

            BoardTool.Move -> when (tap) {
                is MapTap.Piece -> select(tap.pieceId)
                is MapTap.Space -> tapSpace(before, tap.hex)
            }
        }
        return state != before
    }

    /**
     * A tap on an empty space or off the board while moving pieces: a selected piece on the board
     * moves there; with none (or a tray piece) selected, the tray's active minion is placed there.
     */
    private fun tapSpace(ready: BoardUiState.Ready, hex: Hex) {
        val geometry = ready.geometry
        val occupied = BoardRules.occupancy(ready.board.state)
        val selected = ready.selected
        when {
            !geometry.isSpace(hex) -> if (!ready.trayActive) select(null)
            selected?.at != null -> if (BoardRules.isEmpty(geometry, occupied, hex)) move(ready, selected, hex)
            ready.trayActive -> placeWaiting(ready, ready.activeWaiting ?: return, hex, occupied)
        }
    }

    @Suppress("CyclomaticComplexMethod")
    fun onAction(action: BoardAction) {
        val ready = state
        when (action) {
            is BoardAction.Select -> select(action.pieceId)

            BoardAction.Deselect -> select(null)

            is BoardAction.Drop -> drop(ready, action.pieceId, action.hex)

            is BoardAction.ChooseCard, BoardAction.ToggleMovement, is BoardAction.SetPath,
            BoardAction.ToggleStraightLine, BoardAction.ToggleFastTravel, BoardAction.ToggleRange,
            BoardAction.ToggleRadius, is BoardAction.MovementBonus, is BoardAction.RangeBonus,
            is BoardAction.RadiusBonus, is BoardAction.Steps,
            -> editOverlay { settings -> overlaySettings(ready, settings, action) }

            BoardAction.StartMeasure -> setReady {
                if (it.selected?.at != null) it.copy(tool = BoardTool.Measure, measure = null) else it
            }

            is BoardAction.Add -> setReady { it.copy(tool = BoardTool.Place(action.piece), measure = null) }

            BoardAction.CancelTool -> setReady { it.copy(tool = BoardTool.Move, measure = null) }

            BoardAction.Remove -> remove(ready)

            BoardAction.Undo -> step(from = undo, to = redo, said = "Undone.")

            BoardAction.Redo -> step(from = redo, to = undo, said = "Redone.")

            BoardAction.ResetToOpening -> {
                select(null)
                commit(BoardSetup.opening(ready.geometry, ready.board.lineup, newPieceId))
                announce("Back to the opening.")
            }

            is BoardAction.Push -> push(ready, action.laneId, action.winner)

            is BoardAction.ActivateWaiting -> {
                listener.selected(null)
                setReady { it.copy(selectedId = null, activeWaitingId = action.pieceId, tool = BoardTool.Move) }
            }

            BoardAction.Flip -> {
                val bottom = ready.bottomTeam.opponent
                listener.flipped(bottom)
                setReady { it.copy(bottomTeam = bottom) }
            }

            BoardAction.DismissThrone -> setReady { it.copy(throneCaptured = null) }

            is BoardAction.HintShown -> setReady { if (it.hint?.id == action.id) it.copy(hint = null) else it }
        }
    }

    /**
     * The stored board changed elsewhere (a backup import, a rename in Boards): adopted, Undo and Redo
     * forgotten; a hint says so when the layout changed. ([stored] is never this sandbox's own echo:
     * the caller filters those out.)
     */
    fun adopt(stored: SavedBoard) {
        undo.clear()
        redo.clear()
        val sameLayout = stored.state == state.board.state
        setReady {
            it.copy(
                board = stored,
                selectedId = it.selectedId?.takeIf { id -> stored.state.pieces.any { p -> p.id == id } },
                measure = null,
                hint = if (sameLayout) it.hint else nextHint("This board was changed elsewhere (a backup import)."),
            )
        }
    }

    /** A short message for the snackbar. */
    fun hint(text: String) = setReady { it.copy(hint = nextHint(text)) }

    // ---- Changes -------------------------------------------------------------------------

    /** The overlay switches after [action] (one of the overlay actions). */
    private fun overlaySettings(
        ready: BoardUiState.Ready,
        settings: OverlaySettings,
        action: BoardAction,
    ): OverlaySettings = when (action) {
        is BoardAction.ChooseCard -> {
            val card = ready.selectedHero()?.measurable?.firstOrNull { it.id == action.cardId }
            // A newly chosen card shows what it has: its Movement, Range and Radius.
            settings.copy(
                cardId = card?.id,
                movement = true,
                range = card?.values?.range != null,
                radius = card?.values?.radius != null,
            )
        }

        BoardAction.ToggleMovement -> settings.copy(movement = !settings.movement)

        is BoardAction.SetPath -> settings.copy(path = action.path, movement = true)

        BoardAction.ToggleStraightLine -> settings.copy(straightLine = !settings.straightLine, movement = true)

        BoardAction.ToggleFastTravel -> settings.copy(fastTravel = !settings.fastTravel)

        BoardAction.ToggleRange -> settings.copy(range = !settings.range)

        BoardAction.ToggleRadius -> settings.copy(radius = !settings.radius)

        is BoardAction.MovementBonus -> settings.copy(movementBonus = bonus(settings.movementBonus, action.delta))

        is BoardAction.RangeBonus -> settings.copy(rangeBonus = bonus(settings.rangeBonus, action.delta))

        is BoardAction.RadiusBonus -> settings.copy(radiusBonus = bonus(settings.radiusBonus, action.delta))

        is BoardAction.Steps ->
            settings.copy(steps = (settings.steps + action.delta).coerceIn(1, MAX_STEPS), movement = true)

        else -> settings
    }

    private fun bonus(current: Int, delta: Int) = (current + delta).coerceIn(0, MAX_BONUS)

    /** Undo or Redo: the board goes back to the last state of [from]; the current one goes to [to]. */
    private fun step(from: ArrayDeque<BoardState>, to: ArrayDeque<BoardState>, said: String) {
        val next = from.removeLastOrNull() ?: return
        to.addLast(state.board.state)
        apply(next)
        announce(said)
    }

    private fun select(pieceId: String?) {
        listener.selected(pieceId)
        setReady { ready ->
            val piece = pieceId?.let { id -> ready.pieces.firstOrNull { it.id == id } }
            ready.copy(
                selectedId = pieceId,
                tool = if (pieceId == null) BoardTool.Move else ready.tool,
                measure = null,
                // A tray piece selected is the one the next tap places.
                activeWaitingId = if (piece != null && piece.at == null) piece.id else ready.activeWaitingId,
            )
        }
    }

    private fun move(ready: BoardUiState.Ready, piece: BoardPiece, to: Hex) {
        val from = piece.at ?: return
        commit(ready.board.state.move(piece.id, to))
        announce("${name(ready, piece)} moved ${BoardText.spaces(HexGrid.distance(from, to))}, to ${where(ready, to)}.")
    }

    private fun remove(ready: BoardUiState.Ready) {
        val removed = ready.selected ?: return
        commit(ready.board.state.copy(pieces = ready.pieces.filterNot { it.id == removed.id }))
        announce("${name(ready, removed)} removed.")
    }

    private fun drop(ready: BoardUiState.Ready, pieceId: String, hex: Hex) {
        val piece = ready.pieces.firstOrNull { it.id == pieceId } ?: return
        val from = piece.at ?: return
        if (hex == from || !ready.geometry.isSpace(hex)) {
            select(pieceId)
            return
        }
        val other = BoardRules.occupancy(ready.board.state)[hex]
        val pieces = ready.pieces.map {
            when (it.id) {
                piece.id -> it.moveTo(hex)
                other?.id -> it.moveTo(from)
                else -> it
            }
        }
        commit(ready.board.state.copy(pieces = pieces))
        select(pieceId)
        announce(
            if (other == null) {
                "${name(ready, piece)} moved ${BoardText.spaces(HexGrid.distance(from, hex))}, to ${where(ready, hex)}."
            } else {
                "${name(ready, piece)} and ${name(ready, other)} swapped places."
            },
        )
    }

    private fun placeNew(ready: BoardUiState.Ready, spec: NewPiece, hex: Hex?) {
        val occupied = BoardRules.occupancy(ready.board.state)
        if (hex == null || !BoardRules.isEmpty(ready.geometry, occupied, hex)) {
            hint(
                if (hex != null && ready.geometry.isSpace(hex)) {
                    "That space is taken: one piece per space."
                } else {
                    "Tap an empty space."
                },
            )
            return
        }
        val id = newPieceId()
        val piece = when (spec) {
            is NewPiece.Hero -> HeroPiece(id, hex, spec.heroId, spec.team)
            is NewPiece.Minion -> MinionPiece(id, hex, spec.team, spec.type)
            is NewPiece.Token -> TokenPiece(id, hex, spec.label, spec.team)
        }
        if (piece is HeroPiece && piece.heroId in ready.heroIdsOnBoard) {
            hint("${ready.heroes.hero(piece.heroId).name} is already on this board.")
            return
        }
        commit(ready.board.state.copy(pieces = ready.pieces + piece))
        setReady { it.copy(tool = BoardTool.Move) }
        select(id)
        announce("${name(ready, piece)} placed in ${where(ready, hex)}.")
    }

    private fun placeWaiting(ready: BoardUiState.Ready, waiting: WaitingUi, hex: Hex, occupied: Map<Hex, BoardPiece>) {
        val allowed = if (waiting.targets.isEmpty()) {
            BoardRules.isEmpty(ready.geometry, occupied, hex)
        } else {
            hex in waiting.targets
        }
        if (!allowed) {
            hint("Tap a highlighted space: the nearest empty spaces of the Battle Zone (p.8).")
            return
        }
        commit(ready.board.state.move(waiting.piece.id, hex))
        announce("${name(ready, waiting.piece)} placed in ${where(ready, hex)}.")
    }

    private fun push(ready: BoardUiState.Ready, laneId: String, winner: Team) {
        when (val result = BoardSetup.pushLane(ready.geometry, ready.board.state, laneId, winner, newPieceId)) {
            is BoardSetup.PushResult.ThroneCaptured -> setReady {
                it.copy(throneCaptured = ThroneCaptured(result.winner))
            }

            is BoardSetup.PushResult.Pushed -> {
                if (result.state == ready.board.state) return
                select(null)
                commit(result.state)
                val zone = result.state.battleZones[laneId]
                announce("The ${BoardText.battleZone(ready.map, laneId)} is now ${BoardText.zone(ready.map, zone)}.")
                result.waiting.firstOrNull()?.piece?.id?.let { first -> setReady { it.copy(activeWaitingId = first) } }
            }
        }
    }

    private fun commit(next: BoardState) {
        if (next == state.board.state) return
        undo.addLast(state.board.state)
        while (undo.size > MAX_HISTORY) undo.removeFirst()
        redo.clear()
        apply(next)
    }

    private fun apply(next: BoardState) {
        val ready = state
        val board = ready.board.copy(state = next, updatedAt = now())
        val selected = ready.selectedId?.takeIf { id -> next.pieces.any { it.id == id } }
        if (selected == null) listener.selected(null)
        setReady { it.copy(board = board, selectedId = selected, measure = null) }
        listener.changed(board)
    }

    private fun editOverlay(transform: (OverlaySettings) -> OverlaySettings) {
        val id = state.selectedId ?: return
        overlays[id] = transform(overlays[id] ?: defaultSettings(state, id))
        setReady { it }
    }

    private fun nextHint(text: String) = BoardHint(text, ++messages)

    private fun announce(text: String) = setReady { it.copy(announcement = Announcement(text, ++messages)) }

    private fun name(ready: BoardUiState.Ready, piece: BoardPiece): String = when (piece) {
        is HeroPiece -> ready.heroes.hero(piece.heroId).name
        else -> BoardText.piece(piece) { ready.heroes.hero(it).name }
    }

    /** "Lane Zone 2, Battle Zone": a space's Zone for an announcement. */
    private fun where(ready: BoardUiState.Ready, hex: Hex): String {
        val zone = ready.geometry.zoneOf(hex)
        val lane = ready.map.lanes.firstOrNull {
            (ready.board.state.battleZones[it.id] ?: it.startingBattleZone) == zone
        }
        return listOfNotNull(BoardText.zone(ready.map, zone), lane?.let { BoardText.battleZone(ready.map, it.id) })
            .joinToString(", ")
    }

    private inline fun setReady(transform: (BoardUiState.Ready) -> BoardUiState.Ready) {
        state = derive(transform(state))
    }

    // ---- Derived state -------------------------------------------------------------------

    /** Recomputes everything that follows from the board, the selection and its overlay switches. */
    private fun derive(ready: BoardUiState.Ready): BoardUiState.Ready {
        val waiting = waitingPieces(ready)
        val active = ready.activeWaitingId?.takeIf { id -> waiting.any { it.piece.id == id } }
            ?: waiting.firstOrNull()?.piece?.id
        val selected = ready.selected
        val tool = if (selected?.at == null && ready.tool == BoardTool.Measure) BoardTool.Move else ready.tool
        val measure = ready.measure?.let { measure(ready, it.target) }
        return ready.copy(
            waiting = waiting,
            activeWaitingId = active,
            tool = tool,
            measure = if (tool == BoardTool.Measure) measure else null,
            overlay = selected?.let { overlay(ready, it) },
            canUndo = undo.isNotEmpty(),
            canRedo = redo.isNotEmpty(),
        )
    }

    private fun overlay(ready: BoardUiState.Ready, piece: BoardPiece): OverlayUi {
        val settings = overlays[piece.id] ?: defaultSettings(ready, piece.id)
        val from = piece.at ?: return OverlayUi(settings)
        val geometry = ready.geometry
        val state = ready.board.state
        fun reach(steps: Int?) = if (settings.movement && steps != null) {
            BoardRules.movementReach(geometry, state, from, steps, settings.path, settings.straightLine)
        } else {
            emptyMap()
        }
        if (piece !is HeroPiece) {
            return OverlayUi(
                settings = settings,
                movementValue = settings.steps,
                movement = reach(settings.steps),
                returnTo = (piece as? MinionPiece)?.let {
                    BoardRules.returnToBattleZone(geometry, state, it)
                }.orEmpty(),
            )
        }
        val card = ready.heroes.hero(piece.heroId).measurable.firstOrNull { it.id == settings.cardId }
        val movementValue = card?.values?.movement?.plus(settings.movementBonus)
        val rangeValue = card?.values?.range?.plus(settings.rangeBonus)
        val radiusValue = card?.values?.radius?.plus(settings.radiusBonus)
        fun area(on: Boolean, value: Int?) =
            if (on && value != null) BoardRules.within(geometry, from, value) else emptySet()
        return OverlayUi(
            settings = settings,
            card = card,
            movementValue = movementValue,
            movement = reach(movementValue),
            fastTravel = if (settings.fastTravel) BoardRules.fastTravel(geometry, state, piece) else emptySet(),
            rangeValue = rangeValue,
            range = area(settings.range, rangeValue),
            radiusValue = radiusValue,
            radius = area(settings.radius, radiusValue),
        )
    }

    private fun defaultSettings(ready: BoardUiState.Ready, pieceId: String): OverlaySettings {
        val piece = ready.pieces.firstOrNull { it.id == pieceId }
        return if (piece is HeroPiece) OverlaySettings() else OverlaySettings(steps = 1, movement = false)
    }

    private fun measure(ready: BoardUiState.Ready, target: Hex): MeasureUi? {
        val from = ready.selected?.at ?: return null
        return MeasureUi(target, HexGrid.distance(from, target), HexGrid.straightLineDirection(from, target) != null)
    }

    /**
     * Pieces in the tray. A minion's targets are the nearest empty spaces of its Lane's Battle Zone
     * to the Spawn point it couldn't take (p.8, `BoardSetup.waitingTargets`); any other piece (or a
     * minion with no Lane) can go on any empty space.
     */
    private fun waitingPieces(ready: BoardUiState.Ready): List<WaitingUi> {
        val geometry = ready.geometry
        val state = ready.board.state
        return state.pieces.filter { it.at == null }.map { piece ->
            val targets = (piece as? MinionPiece)?.let { BoardSetup.waitingTargets(geometry, state, it) }
            WaitingUi(piece, targets.orEmpty())
        }
    }

    private fun BoardUiState.Ready.selectedHero(): BoardHero? = (selected as? HeroPiece)?.let { heroes.hero(it.heroId) }

    private fun BoardState.move(pieceId: String, hex: Hex): BoardState =
        copy(pieces = pieces.map { if (it.id == pieceId) it.moveTo(hex) else it })

    companion object {
        /** An untouched board of [fresh]'s line-up and map (still on its opening): reopened rather than duplicated. */
        fun twinOf(boards: List<SavedBoard>, fresh: SavedBoard): SavedBoard? =
            boards.firstOrNull { it.lineup == fresh.lineup && it.state.sameLayoutAs(fresh.state) }

        /** A change is written this long after the last one (and at once when the screen goes away). */
        const val SAVE_DELAY_MS = 600L
        const val MAX_HISTORY = 200
        const val MAX_BONUS = 5
        const val MAX_STEPS = 9

        /**
         * A new board for the line-up [red] / [blue] (pick order; [drafted] = the draft's pick order
         * when the line-up is a logged match's) on [mapId] (null = the default map for that many
         * heroes), at the opening (p.6), named [name] or after the line-up; null when no map is bundled.
         */
        @Suppress("LongParameterList")
        fun newBoard(
            red: List<String>,
            blue: List<String>,
            drafted: BoardLineup?,
            mapId: String?,
            name: String?,
            maps: List<BoardMap>,
            heroes: Map<String, BoardHero>,
            id: String,
            now: Long,
            newPieceId: () -> String,
        ): SavedBoard? {
            val r = red.distinct()
            val lineup = BoardLineup(r, blue.distinct().filterNot { it in r }).inOrderOf(drafted)
            val map = defaultMap(maps, lineup.size, mapId) ?: return null
            return SavedBoard(
                id = id,
                name = name?.takeIf { it.isNotBlank() } ?: BoardText.boardName(lineup) { heroes.hero(it).name },
                lineup = lineup,
                state = BoardSetup.opening(BoardGeometry.of(map), lineup, newPieceId),
                createdAt = now,
                updatedAt = now,
            )
        }
    }
}
