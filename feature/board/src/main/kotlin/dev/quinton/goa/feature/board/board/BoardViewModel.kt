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

    /** The board being edited (shared with the web app, core:play); null until loaded. */
    private var sandbox: BoardSandbox? = null
    private var saveJob: Job? = null
    private var unsaved: SavedBoard? = null

    /** Boards this ViewModel wrote lately: the store echoing one back is not a change made elsewhere. */
    private val written = ArrayDeque<SavedBoard>()

    /** The board's id once it is known (opened, or created for this entry). */
    val boardId: String? get() = (state.value as? BoardUiState.Ready)?.board?.id

    /** What the sandbox asks the phone to keep (selection, flip: SavedStateHandle) and to write. */
    private val keeper = object : BoardSandbox.Listener {
        override fun selected(pieceId: String?) {
            if (pieceId == null) savedState.remove<String>(KEY_SELECTED) else savedState[KEY_SELECTED] = pieceId
        }

        override fun flipped(bottom: Team) {
            savedState[KEY_BOTTOM] = bottom.name
        }

        override fun changed(board: SavedBoard) = scheduleSave(board)
    }

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
            val twin = BoardSandbox.twinOf(boardRepository.boards.catch { emit(emptyList()) }.first(), fresh)
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
        val opened = BoardSandbox(
            BoardUiState.Ready(
                board = board,
                map = map,
                heroes = heroes,
                ownedProducts = owned,
                bottomTeam = bottom,
                selectedId = savedState.get<String>(KEY_SELECTED)?.takeIf { sel ->
                    board.state.pieces.any { it.id == sel }
                },
            ),
            newPieceId = newPieceId,
            now = clock::now,
            listener = keeper,
        )
        if (reopened) opened.hint("Opened your untouched board for this line-up.")
        sandbox = opened
        state.value = opened.state
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
        return BoardSandbox.newBoard(
            red = route.red,
            blue = route.blue,
            drafted = drafted,
            mapId = route.mapId,
            name = route.name,
            maps = maps,
            heroes = heroes,
            id = newBoardId(),
            now = clock.now(),
            newPieceId = newPieceId,
        )
    }

    /** The stored board changed: ours echoing back, or a change made elsewhere. */
    private fun onStored(stored: SavedBoard?) {
        val sandbox = sandbox ?: return
        val ready = state.value as? BoardUiState.Ready ?: return
        if (stored == null) {
            // Deleted (in Boards, or by a backup Replace): never write it back.
            saveJob?.cancel()
            unsaved = null
            this.sandbox = null
            state.value = BoardUiState.Missing("This board was deleted.")
            return
        }
        if (stored == ready.board || stored in written || stored == unsaved) return
        saveJob?.cancel()
        unsaved = null
        sandbox.adopt(stored)
        state.value = sandbox.state
    }

    /**
     * A tap on the map. Returns true when it did anything (moved or placed a piece, changed the
     * selection, measured), so the screen arms a double tap to zoom only after a tap that did nothing.
     */
    fun tap(tap: MapTap): Boolean {
        val sandbox = sandbox ?: return false
        val did = sandbox.tap(tap)
        state.value = sandbox.state
        return did
    }

    fun onAction(action: BoardAction) {
        val sandbox = sandbox ?: return
        sandbox.onAction(action)
        state.value = sandbox.state
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

    internal companion object {
        const val KEY_CREATED = "board.createdId"
        const val KEY_SELECTED = "board.selected"
        const val KEY_BOTTOM = "board.bottomTeam"
        const val SAVE_DELAY_MS = BoardSandbox.SAVE_DELAY_MS
        const val MAX_WRITTEN = 16
    }
}
