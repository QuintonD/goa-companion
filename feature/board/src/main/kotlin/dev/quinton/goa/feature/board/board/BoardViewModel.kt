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
import dev.quinton.goa.core.data.EditClock
import dev.quinton.goa.core.domain.board.BoardGeometry
import dev.quinton.goa.core.domain.board.BoardRules
import dev.quinton.goa.core.domain.board.BoardSetup
import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.model.board.BoardLineup
import dev.quinton.goa.core.model.board.BoardPiece
import dev.quinton.goa.core.model.board.BoardState
import dev.quinton.goa.core.model.board.HeroPiece
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionPiece
import dev.quinton.goa.core.model.board.SavedBoard
import dev.quinton.goa.core.model.board.SpawnKind
import dev.quinton.goa.core.model.board.TokenPiece
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.navigation.BoardRoute
import dev.quinton.goa.feature.board.BoardHero
import dev.quinton.goa.feature.board.BoardText
import dev.quinton.goa.feature.board.boardHeroes
import dev.quinton.goa.feature.board.defaultMap
import dev.quinton.goa.feature.board.hero
import dev.quinton.goa.feature.board.pieceIds
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A board in the sandbox (ADR-033): moves pieces freely (one per space), measures with
 * `BoardRules`, pushes lanes with `BoardSetup`. It never plays the game.
 *
 * [BoardRoute.boardId] opens a saved board; null creates one for the route's line-up, exactly once:
 * the new id is kept in [savedState] before the board is written, so a recomposition, rotation or
 * process death reopens the same board instead of creating another. Every change is autosaved
 * ([SAVE_DELAY_MS] after the last one, and at once by [flush]); Undo/Redo keep this visit's history.
 */
@HiltViewModel(assistedFactory = BoardViewModel.Factory::class)
class BoardViewModel internal constructor(
    private val route: BoardRoute,
    private val savedState: SavedStateHandle,
    private val catalogRepository: CatalogRepository,
    private val mapRepository: BoardMapRepository,
    private val boardRepository: BoardRepository,
    private val clock: EditClock,
    private val newBoardId: () -> String = { UUID.randomUUID().toString() },
    /** Writes left pending when the screen goes away (the ViewModel's own scope is cancelled by then). */
    private val flushScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : ViewModel() {
    @AssistedInject constructor(
        @Assisted route: BoardRoute,
        savedState: SavedStateHandle,
        catalogRepository: CatalogRepository,
        mapRepository: BoardMapRepository,
        boardRepository: BoardRepository,
        clock: EditClock,
    ) : this(route, savedState, catalogRepository, mapRepository, boardRepository, clock, {
        UUID.randomUUID().toString()
    })

    @AssistedFactory
    interface Factory {
        fun create(route: BoardRoute): BoardViewModel
    }

    private val state = MutableStateFlow<BoardUiState>(BoardUiState.Loading)
    val uiState: StateFlow<BoardUiState> = state.asStateFlow()

    private val undo = ArrayDeque<BoardState>()
    private val redo = ArrayDeque<BoardState>()
    private val overlays = HashMap<String, OverlaySettings>()

    /** Minions sent to the tray by a push in this visit, and the Spawn point each couldn't take. */
    private val waitingSpawns = HashMap<String, Hex>()
    private var saveJob: Job? = null
    private var unsaved: SavedBoard? = null

    /** The board's id once it is known (opened, or created for this entry). */
    val boardId: String? get() = (state.value as? BoardUiState.Ready)?.board?.id

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val catalog = catalogRepository.catalog.catch { emit(Catalog.EMPTY) }.first()
        val maps = mapRepository.maps.catch { emit(emptyList()) }.first()
        val heroes = catalog.boardHeroes()
        val id =
            route.boardId ?: savedState.get<String>(KEY_CREATED) ?: newBoardId().also { savedState[KEY_CREATED] = it }
        var board = boardRepository.observe(id).first()
        if (board == null && route.boardId == null) {
            val lineup = BoardLineup(route.red.distinct(), route.blue.distinct().filterNot { it in route.red })
            val map = defaultMap(maps, lineup.size, route.mapId)
            if (map == null) {
                state.value = BoardUiState.Missing("No map is bundled with this build.")
                return
            }
            val now = clock.now()
            board = SavedBoard(
                id = id,
                name = route.name?.takeIf { it.isNotBlank() } ?: BoardText.boardName(lineup) { heroes.hero(it).name },
                lineup = lineup,
                state = BoardSetup.opening(BoardGeometry.of(map), lineup, pieceIds(BoardState(map.id))),
                createdAt = now,
                updatedAt = now,
            )
            withContext(NonCancellable) { boardRepository.upsert(board) }
        }
        if (board == null) {
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
                bottomTeam = bottom,
                selectedId = savedState.get<String>(KEY_SELECTED)?.takeIf { sel ->
                    board.state.pieces.any { it.id == sel }
                },
            ),
        )
    }

    /**
     * A tap on the map. Returns true when it changed the board (a move or a placement), so the
     * screen can tell a double tap meant to zoom from a tap that already did something.
     */
    fun tap(tap: MapTap): Boolean {
        val ready = state.value as? BoardUiState.Ready ?: return false
        val geometry = ready.geometry
        val occupied = BoardRules.occupancy(ready.board.state)
        val hex = when (tap) {
            is MapTap.Space -> tap.hex
            is MapTap.Piece -> ready.pieces.firstOrNull { it.id == tap.pieceId }?.at
        }
        val tool = ready.tool
        return when {
            tool is BoardTool.Measure -> {
                if (hex != null && ready.selected?.at != null) setReady { it.copy(measure = measure(it, hex)) }
                false
            }

            tool is BoardTool.Place -> placeNew(ready, tool.piece, hex, occupied)

            ready.activeWaiting != null && tap is MapTap.Space -> placeWaiting(
                ready,
                ready.activeWaiting!!,
                tap.hex,
                occupied,
            )

            tap is MapTap.Piece -> {
                select(tap.pieceId)
                false
            }

            tap is MapTap.Space -> {
                val selected = ready.selected
                when {
                    selected != null && BoardRules.isEmpty(geometry, occupied, tap.hex) -> {
                        commit(ready.board.state.move(selected.id, tap.hex))
                        true
                    }

                    !geometry.isSpace(tap.hex) -> {
                        select(null)
                        false
                    }

                    else -> false
                }
            }

            else -> false
        }
    }

    fun onAction(action: BoardAction) {
        val ready = state.value as? BoardUiState.Ready ?: return
        when (action) {
            is BoardAction.Select -> select(action.pieceId)

            BoardAction.Deselect -> select(null)

            is BoardAction.Drop -> drop(ready, action.pieceId, action.hex)

            is BoardAction.ChooseCard, BoardAction.ToggleMovement, BoardAction.ToggleIgnoreObstacles,
            BoardAction.ToggleFastTravel, BoardAction.ToggleRange, BoardAction.ToggleRadius,
            is BoardAction.MovementBonus, is BoardAction.RangeBonus, is BoardAction.RadiusBonus, is BoardAction.Steps,
            -> editOverlay { settings -> overlaySettings(ready, settings, action) }

            BoardAction.StartMeasure -> setReady {
                if (it.selected?.at != null) it.copy(tool = BoardTool.Measure, measure = null) else it
            }

            is BoardAction.Add -> setReady { it.copy(tool = BoardTool.Place(action.piece), measure = null) }

            BoardAction.CancelTool -> setReady { it.copy(tool = BoardTool.Move, measure = null) }

            BoardAction.Remove -> commit(
                ready.board.state.copy(
                    pieces = ready.pieces.filterNot {
                        it.id ==
                            ready.selectedId
                    },
                ),
            )

            BoardAction.Undo -> step(from = undo, to = redo)

            BoardAction.Redo -> step(from = redo, to = undo)

            BoardAction.ResetToOpening -> {
                val opening = BoardSetup.opening(ready.geometry, ready.board.lineup, pieceIds(BoardState(ready.map.id)))
                select(null)
                commit(opening)
            }

            is BoardAction.Push -> push(ready, action.laneId, action.winner)

            is BoardAction.ActivateWaiting -> setReady { it.copy(activeWaitingId = action.pieceId) }

            BoardAction.Flip -> {
                val bottom = ready.bottomTeam.opponent
                savedState[KEY_BOTTOM] = bottom.name
                setReady { it.copy(bottomTeam = bottom) }
            }

            BoardAction.DismissNotice -> setReady { it.copy(notice = null) }
        }
    }

    /** The overlay switches after [action] (one of the overlay actions). */
    private fun overlaySettings(
        ready: BoardUiState.Ready,
        settings: OverlaySettings,
        action: BoardAction,
    ): OverlaySettings =
        when (action) {
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

            BoardAction.ToggleIgnoreObstacles -> settings.copy(ignoreObstacles = !settings.ignoreObstacles)

            BoardAction.ToggleFastTravel -> settings.copy(fastTravel = !settings.fastTravel)

            BoardAction.ToggleRange -> settings.copy(range = !settings.range)

            BoardAction.ToggleRadius -> settings.copy(radius = !settings.radius)

            is BoardAction.MovementBonus -> settings.copy(movementBonus = bonus(settings.movementBonus, action.delta))

            is BoardAction.RangeBonus -> settings.copy(rangeBonus = bonus(settings.rangeBonus, action.delta))

            is BoardAction.RadiusBonus -> settings.copy(radiusBonus = bonus(settings.radiusBonus, action.delta))

            is BoardAction.Steps -> settings.copy(
                steps = (settings.steps + action.delta).coerceIn(1, MAX_STEPS),
                movement = true,
            )

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
        flushScope.launch { boardRepository.upsert(pending) }
    }

    override fun onCleared() {
        flush()
        super.onCleared()
    }

    // ---- Changes -------------------------------------------------------------------------

    private fun select(pieceId: String?) {
        if (pieceId == null) savedState.remove<String>(KEY_SELECTED) else savedState[KEY_SELECTED] = pieceId
        setReady {
            it.copy(
                selectedId = pieceId,
                tool = if (pieceId ==
                    null
                ) {
                    BoardTool.Move
                } else {
                    it.tool
                },
                measure = null,
            )
        }
    }

    private fun drop(ready: BoardUiState.Ready, pieceId: String, hex: Hex) {
        val piece = ready.pieces.firstOrNull { it.id == pieceId } ?: return
        val from = piece.at ?: return
        if (hex == from || !ready.geometry.isSpace(hex)) return
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
    }

    private fun placeNew(
        ready: BoardUiState.Ready,
        spec: NewPiece,
        hex: Hex?,
        occupied: Map<Hex, BoardPiece>,
    ): Boolean {
        if (hex == null || !BoardRules.isEmpty(ready.geometry, occupied, hex)) {
            hint(
                if (hex != null &&
                    ready.geometry.isSpace(hex)
                ) {
                    "That space is taken: one piece per space."
                } else {
                    "Tap an empty space."
                },
            )
            return false
        }
        val id = pieceIds(ready.board.state)()
        val piece = when (spec) {
            is NewPiece.Hero -> HeroPiece(id, hex, spec.heroId, spec.team)
            is NewPiece.Minion -> MinionPiece(id, hex, spec.team, spec.type)
            is NewPiece.Token -> TokenPiece(id, hex, spec.label, spec.team)
        }
        if (piece is HeroPiece && piece.heroId in ready.heroIdsOnBoard) {
            hint("${ready.heroes.hero(piece.heroId).name} is already on this board.")
            return false
        }
        commit(ready.board.state.copy(pieces = ready.pieces + piece))
        setReady { it.copy(tool = BoardTool.Move) }
        select(id)
        return true
    }

    private fun placeWaiting(
        ready: BoardUiState.Ready,
        waiting: WaitingUi,
        hex: Hex,
        occupied: Map<Hex, BoardPiece>,
    ): Boolean {
        val allowed = if (waiting.targets.isEmpty()) {
            BoardRules.isEmpty(ready.geometry, occupied, hex)
        } else {
            hex in
                waiting.targets
        }
        if (!allowed) {
            hint("Tap a highlighted space: the nearest empty spaces of the Battle Zone (p.8).")
            return false
        }
        commit(ready.board.state.move(waiting.piece.id, hex))
        return true
    }

    private fun push(ready: BoardUiState.Ready, laneId: String, winner: Team) {
        when (
            val result = BoardSetup.pushLane(
                ready.geometry,
                ready.board.state,
                laneId,
                winner,
                pieceIds(ready.board.state),
            )
        ) {
            is BoardSetup.PushResult.ThroneCaptured -> setReady {
                it.copy(notice = BoardNotice.ThroneCaptured(result.winner))
            }

            is BoardSetup.PushResult.Pushed -> {
                if (result.state == ready.board.state) return
                result.waiting.forEach { waitingSpawns[it.piece.id] = it.spawn }
                commit(result.state)
                val waiting = result.waiting.firstOrNull()?.piece?.id
                if (waiting != null) setReady { it.copy(activeWaitingId = waiting) }
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
            withContext(NonCancellable) { boardRepository.upsert(pending) }
        }
    }

    private fun editOverlay(transform: (OverlaySettings) -> OverlaySettings) {
        val ready = state.value as? BoardUiState.Ready ?: return
        val id = ready.selectedId ?: return
        overlays[id] = transform(overlays[id] ?: defaultSettings(ready, id))
        setReady { it }
    }

    private fun hint(text: String) = setReady { it.copy(notice = BoardNotice.Hint(text)) }

    private inline fun setReady(transform: (BoardUiState.Ready) -> BoardUiState.Ready) {
        state.update { current -> if (current is BoardUiState.Ready) derive(transform(current)) else current }
    }

    // ---- Derived state -------------------------------------------------------------------

    /** Recomputes everything that follows from the board, the selection and its overlay switches. */
    private fun derive(ready: BoardUiState.Ready): BoardUiState.Ready {
        val waiting = waitingPieces(ready)
        val active =
            ready.activeWaitingId?.takeIf { id -> waiting.any { it.piece.id == id } }
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
        if (piece !is HeroPiece) {
            val steps = settings.steps
            return OverlayUi(
                settings = settings,
                movementValue = steps,
                movement = if (settings.movement) {
                    BoardRules.movementReach(
                        geometry,
                        state,
                        from,
                        steps,
                        if (settings.ignoreObstacles) BoardRules.MovePath.IGNORE_OBSTACLES else BoardRules.MovePath.NORMAL,
                    )
                } else {
                    emptyMap()
                },
            )
        }
        val card = ready.heroes.hero(piece.heroId).cards.firstOrNull { it.id == settings.cardId }
        val movementValue = card?.values?.movement?.plus(settings.movementBonus)
        val rangeValue = card?.values?.range?.plus(settings.rangeBonus)
        val radiusValue = card?.values?.radius?.plus(settings.radiusBonus)
        return OverlayUi(
            settings = settings,
            card = card,
            movementValue = movementValue,
            movement = if (settings.movement && movementValue != null) {
                BoardRules.movementReach(geometry, state, from, movementValue, if (settings.ignoreObstacles) BoardRules.MovePath.IGNORE_OBSTACLES else BoardRules.MovePath.NORMAL)
            } else {
                emptyMap()
            },
            fastTravel = if (settings.fastTravel) BoardRules.fastTravel(geometry, state, piece) else emptySet(),
            rangeValue = rangeValue,
            range = if (settings.range &&
                rangeValue != null
            ) {
                BoardRules.within(geometry, from, rangeValue)
            } else {
                emptySet()
            },
            radiusValue = radiusValue,
            radius = if (settings.radius &&
                radiusValue != null
            ) {
                BoardRules.within(geometry, from, radiusValue)
            } else {
                emptySet()
            },
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
     * Pieces in the tray. A minion's targets are the nearest empty spaces of the Battle Zone to its
     * Spawn point (p.8); a piece with no Spawn point to go to can go on any empty space.
     */
    private fun waitingPieces(ready: BoardUiState.Ready): List<WaitingUi> {
        val geometry = ready.geometry
        val state = ready.board.state
        val occupied = BoardRules.occupancy(state)
        return state.pieces.filter { it.at == null }.map { piece ->
            val targets = (piece as? MinionPiece)?.let { minion ->
                // The Spawn point it couldn't take: known from this visit's push, else a taken Spawn
                // point of its team and type in a Battle Zone (the tray doesn't record the lane).
                waitingSpawns[minion.id]?.let { spawn ->
                    geometry.zoneOf(spawn)?.let { zone -> BoardSetup.nearestEmpty(geometry, state, zone, spawn) }
                } ?: battleZones(ready).firstNotNullOfOrNull { zone ->
                    val spawns = geometry.minionSpawns(zone).filter { space ->
                        space.spawn?.team == minion.team && space.spawn?.kind == spawnKind(minion)
                    }
                    val spawn = spawns.firstOrNull { it.hex in occupied }
                        ?: spawns.firstOrNull()
                        ?: return@firstNotNullOfOrNull null
                    BoardSetup.nearestEmpty(geometry, state, zone, spawn.hex).takeIf { it.isNotEmpty() }
                }
            }
            WaitingUi(piece, targets.orEmpty())
        }
    }

    private fun battleZones(ready: BoardUiState.Ready): List<String> =
        ready.board.state.battleZones.values.ifEmpty { ready.map.lanes.map { it.startingBattleZone } }.distinct()

    private fun spawnKind(minion: MinionPiece): SpawnKind = SpawnKind.entries.first { it.minion == minion.type }

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
    }
}
