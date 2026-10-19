package dev.quinton.goa.feature.board.board

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.quinton.goa.core.data.BoardMapRepository
import dev.quinton.goa.core.data.BoardRepository
import dev.quinton.goa.core.data.CatalogRepository
import dev.quinton.goa.core.data.DraftRecordRepository
import dev.quinton.goa.core.data.EditClock
import dev.quinton.goa.core.data.SettingsRepository
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
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.settings.AppSettings
import dev.quinton.goa.core.navigation.BoardRoute
import dev.quinton.goa.feature.board.BoardHero
import dev.quinton.goa.feature.board.BoardText
import dev.quinton.goa.feature.board.boardHeroes
import dev.quinton.goa.feature.board.defaultMap
import dev.quinton.goa.feature.board.hero
import dev.quinton.goa.feature.board.inOrderOf
import dev.quinton.goa.feature.board.lineup
import dev.quinton.goa.feature.board.newPieceId as freshPieceId
import dev.quinton.goa.feature.board.sameLayoutAs
import dev.quinton.goa.feature.board.size
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A board in the sandbox (ADR-033): moves pieces freely (one per space), measures with
 * `BoardRules`, pushes lanes with `BoardSetup`. It never plays the game.
 *
 * [BoardRoute.boardId] opens a saved board; null creates one for the route's line-up, exactly once:
 * the new id is kept in [savedState] before the board is written, so a recomposition, rotation or
 * process death reopens the same board instead of creating another. When an untouched board of the
 * same line-up and map exists (still on its opening), that one is reopened instead of a duplicate.
 * Every change is autosaved ([SAVE_DELAY_MS] after the last one, and at once by [flush]); Undo/Redo
 * keep this visit's history. The stored board is watched while the board is open: a change made
 * elsewhere (a backup import, a rename in Boards) is adopted and a deletion closes the board, so a
 * board left open in the back stack never writes over them.
 */
@HiltViewModel(assistedFactory = BoardViewModel.Factory::class)
class BoardViewModel internal constructor(
    private val route: BoardRoute,
    private val savedState: SavedStateHandle,
    private val catalogRepository: CatalogRepository,
    private val mapRepository: BoardMapRepository,
    private val boardRepository: BoardRepository,
    private val draftRepository: DraftRecordRepository,
    private val settingsRepository: SettingsRepository,
    private val clock: EditClock,
    private val newBoardId: () -> String = { UUID.randomUUID().toString() },
    private val newPieceId: () -> String = { freshPieceId() },
    /** Writes left pending when the screen goes away (the ViewModel's own scope is cancelled by then). */
    private val flushScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : ViewModel() {
    @AssistedInject constructor(
        @Assisted route: BoardRoute,
        savedState: SavedStateHandle,
        catalogRepository: CatalogRepository,
        mapRepository: BoardMapRepository,
        boardRepository: BoardRepository,
        draftRepository: DraftRecordRepository,
        settingsRepository: SettingsRepository,
        clock: EditClock,
    ) : this(
        route,
        savedState,
        catalogRepository,
        mapRepository,
        boardRepository,
        draftRepository,
        settingsRepository,
        clock,
        { UUID.randomUUID().toString() },
    )

    @AssistedFactory
    interface Factory {
        fun create(route: BoardRoute): BoardViewModel
    }

    private val state = MutableStateFlow<BoardUiState>(BoardUiState.Loading)
    val uiState: StateFlow<BoardUiState> = state.asStateFlow()

    private val undo = ArrayDeque<BoardState>()
    private val redo = ArrayDeque<BoardState>()
    private val overlays = HashMap<String, OverlaySettings>()
    private var saveJob: Job? = null
    private var unsaved: SavedBoard? = null

    /** Boards this ViewModel wrote lately: the store echoing one back is not a change made elsewhere. */
    private val written = ArrayDeque<SavedBoard>()
    private var messages = 0L

    /** The board's id once it is known (opened, or created for this entry). */
    val boardId: String? get() = (state.value as? BoardUiState.Ready)?.board?.id

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val catalog = catalogRepository.catalog.catch { emit(Catalog.EMPTY) }.first()
        val maps = mapRepository.maps.catch { emit(emptyList()) }.first()
        val owned = settingsRepository.settings.catch { emit(AppSettings()) }.first().ownedProducts
        val heroes = catalog.boardHeroes()
        var reopened = false
        val id = route.boardId ?: savedState.get<String>(KEY_CREATED) ?: run {
            val fresh = newBoard(maps, heroes)
            if (fresh == null) {
                state.value = BoardUiState.Missing("No map is bundled with this build.")
                return
            }
            // An untouched board of this line-up and map is reopened rather than duplicated.
            val twin = boardRepository.boards.catch { emit(emptyList()) }.first().firstOrNull {
                it.lineup == fresh.lineup && it.state.sameLayoutAs(fresh.state)
            }
            val board = twin ?: fresh
            savedState[KEY_CREATED] = board.id
            if (twin == null) {
                remember(board)
                withContext(NonCancellable) { boardRepository.upsert(board) }
            } else {
                reopened = true
            }
            board.id
        }
        val loaded = boardRepository.observe(id)
            .map { Result.success(it) }
            .catch { emit(Result.failure(it)) }
            .first()
        if (loaded.isFailure) {
            state.value = BoardUiState.Missing("This board couldn't be read.")
            return
        }
        val board = loaded.getOrNull() ?: run {
            state.value = BoardUiState.Missing("This board was deleted.")
            return
        }
        val map = maps.firstOrNull { it.id == board.state.mapId }
        if (map == null) {
            state.value = BoardUiState.Missing("This board's map isn't in this build.")
            return
        }
        val bottom =
            savedState.get<String>(KEY_BOTTOM)?.let { runCatching { Team.valueOf(it) }.getOrNull() } ?: Team.RED
        state.value = derive(
            BoardUiState.Ready(
                board = board,
                map = map,
                heroes = heroes,
                ownedProducts = owned,
                bottomTeam = bottom,
                selectedId = savedState.get<String>(KEY_SELECTED)?.takeIf { sel ->
                    board.state.pieces.any { it.id == sel }
                },
                hint = if (reopened) nextHint("Opened your untouched board for this line-up.") else null,
            ),
        )
        boardRepository.observe(id)
            .catch { /* An unreadable store keeps what is on screen. */ }
            .collect(::onStored)
    }

    /** A new board for the route's line-up on its map, at the opening; null when no map is bundled. */
    private suspend fun newBoard(maps: List<BoardMap>, heroes: Map<String, BoardHero>): SavedBoard? {
        // From a logged match that came from a draft: the draft's pick order (the opening uses it).
        val drafted = route.matchId?.let { matchId ->
            draftRepository.observeDrafts().catch { emit(emptyList()) }.first()
                .firstOrNull { it.matchId == matchId }?.lineup()
        }
        val red = route.red.distinct()
        val lineup = BoardLineup(red, route.blue.distinct().filterNot { it in red }).inOrderOf(drafted)
        val map = defaultMap(maps, lineup.size, route.mapId) ?: return null
        val now = clock.now()
        return SavedBoard(
            id = newBoardId(),
            name = route.name?.takeIf { it.isNotBlank() } ?: BoardText.boardName(lineup) { heroes.hero(it).name },
            lineup = lineup,
            state = BoardSetup.opening(BoardGeometry.of(map), lineup, newPieceId),
            createdAt = now,
            updatedAt = now,
        )
    }

    /** The stored board changed: ours echoing back, or a change made elsewhere. */
    private fun onStored(stored: SavedBoard?) {
        val ready = state.value as? BoardUiState.Ready ?: return
        if (stored == null) {
            // Deleted (in Boards, or by a backup Replace): never write it back.
            saveJob?.cancel()
            unsaved = null
            state.value = BoardUiState.Missing("This board was deleted.")
            return
        }
        if (stored == ready.board || stored in written || stored == unsaved) return
        saveJob?.cancel()
        unsaved = null
        undo.clear()
        redo.clear()
        val sameLayout = stored.state == ready.board.state
        setReady {
            it.copy(
                board = stored,
                selectedId = it.selectedId?.takeIf { id -> stored.state.pieces.any { p -> p.id == id } },
                measure = null,
                hint = if (sameLayout) it.hint else nextHint("This board was changed elsewhere (a backup import)."),
            )
        }
    }

    /**
     * A tap on the map. Returns true when it did anything (moved or placed a piece, changed the
     * selection, measured), so the screen arms a double tap to zoom only after a tap that did nothing.
     */
    fun tap(tap: MapTap): Boolean {
        val before = state.value as? BoardUiState.Ready ?: return false
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
        return state.value != before
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

    fun onAction(action: BoardAction) {
        val ready = state.value as? BoardUiState.Ready ?: return
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

            BoardAction.Undo -> step(from = undo, to = redo)

            BoardAction.Redo -> step(from = redo, to = undo)

            BoardAction.ResetToOpening -> {
                select(null)
                commit(BoardSetup.opening(ready.geometry, ready.board.lineup, newPieceId))
                announce("Back to the opening.")
            }

            is BoardAction.Push -> push(ready, action.laneId, action.winner)

            is BoardAction.ActivateWaiting -> {
                savedState.remove<String>(KEY_SELECTED)
                setReady { it.copy(selectedId = null, activeWaitingId = action.pieceId, tool = BoardTool.Move) }
            }

            BoardAction.Flip -> {
                val bottom = ready.bottomTeam.opponent
                savedState[KEY_BOTTOM] = bottom.name
                setReady { it.copy(bottomTeam = bottom) }
            }

            BoardAction.DismissThrone -> setReady { it.copy(throneCaptured = null) }

            is BoardAction.HintShown -> setReady { if (it.hint?.id == action.id) it.copy(hint = null) else it }
        }
    }

    /** The overlay switches after [action] (one of the overlay actions). */
    private fun overlaySettings(
        ready: BoardUiState.Ready,
        settings: OverlaySettings,
        action: BoardAction,
    ): OverlaySettings = when (action) {
        is BoardAction.ChooseCard -> {
            val card = ready.selectedHero()?.cards?.firstOrNull { it.id == action.cardId }
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
    private fun step(from: ArrayDeque<BoardState>, to: ArrayDeque<BoardState>) {
        val ready = state.value as? BoardUiState.Ready ?: return
        val next = from.removeLastOrNull() ?: return
        to.addLast(ready.board.state)
        apply(next)
    }

    /** Writes a pending change now (the screen calls it when it stops or leaves). */
    fun flush() {
        val pending = unsaved ?: return
        saveJob?.cancel()
        unsaved = null
        remember(pending)
        flushScope.launch { boardRepository.upsert(pending) }
    }

    override fun onCleared() {
        flush()
        super.onCleared()
    }

    // ---- Changes -------------------------------------------------------------------------

    private fun select(pieceId: String?) {
        if (pieceId == null) savedState.remove<String>(KEY_SELECTED) else savedState[KEY_SELECTED] = pieceId
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
        val ready = state.value as? BoardUiState.Ready ?: return
        if (next == ready.board.state) return
        undo.addLast(ready.board.state)
        while (undo.size > MAX_HISTORY) undo.removeFirst()
        redo.clear()
        apply(next)
    }

    private fun apply(next: BoardState) {
        val ready = state.value as? BoardUiState.Ready ?: return
        val board = ready.board.copy(state = next, updatedAt = clock.now())
        val selected = ready.selectedId?.takeIf { id -> next.pieces.any { it.id == id } }
        if (selected == null) savedState.remove<String>(KEY_SELECTED)
        setReady { it.copy(board = board, selectedId = selected, measure = null) }
        scheduleSave(board)
    }

    private fun scheduleSave(board: SavedBoard) {
        unsaved = board
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(SAVE_DELAY_MS)
            val pending = unsaved ?: return@launch
            unsaved = null
            remember(pending)
            withContext(NonCancellable) { boardRepository.upsert(pending) }
        }
    }

    /** Notes a board this ViewModel is about to write, so its echo from the store isn't adopted. */
    private fun remember(board: SavedBoard) {
        written.addLast(board)
        while (written.size > MAX_WRITTEN) written.removeFirst()
    }

    private fun editOverlay(transform: (OverlaySettings) -> OverlaySettings) {
        val ready = state.value as? BoardUiState.Ready ?: return
        val id = ready.selectedId ?: return
        overlays[id] = transform(overlays[id] ?: defaultSettings(ready, id))
        setReady { it }
    }

    private fun nextHint(text: String) = BoardHint(text, ++messages)

    private fun hint(text: String) = setReady { it.copy(hint = nextHint(text)) }

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
        state.update { current -> if (current is BoardUiState.Ready) derive(transform(current)) else current }
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
        val card = ready.heroes.hero(piece.heroId).cards.firstOrNull { it.id == settings.cardId }
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

    internal companion object {
        const val KEY_CREATED = "board.createdId"
        const val KEY_SELECTED = "board.selected"
        const val KEY_BOTTOM = "board.bottomTeam"
        const val SAVE_DELAY_MS = 600L
        const val MAX_HISTORY = 200
        const val MAX_BONUS = 5
        const val MAX_STEPS = 9
        const val MAX_WRITTEN = 16
    }
}
