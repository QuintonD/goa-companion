package dev.quinton.goa.web.play.board

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.quinton.goa.core.data.BoardMapRepository
import dev.quinton.goa.core.data.BoardRepository
import dev.quinton.goa.core.data.CatalogRepository
import dev.quinton.goa.core.data.SettingsRepository
import dev.quinton.goa.core.model.board.SavedBoard
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.settings.AppSettings
import dev.quinton.goa.feature.board.board.BoardAction
import dev.quinton.goa.feature.board.board.BoardSandbox
import dev.quinton.goa.feature.board.board.BoardUiState
import dev.quinton.goa.feature.board.board.MapTap
import dev.quinton.goa.feature.board.boardHeroes
import dev.quinton.goa.feature.board.newPieceId
import kotlin.js.Date
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * One board open on the web (the phone's BoardViewModel): loads it, drives the shared [BoardSandbox]
 * (core:play) and writes every change [BoardSandbox.SAVE_DELAY_MS] after the last one ([flush] at
 * once when the screen goes away). The stored copy is watched while the board is open: a change made
 * elsewhere (Boards' rename, another tab) is adopted and a deletion closes it, never written over.
 * [selected] and [bottom] are where the screen keeps the selection and the flip (its history entry).
 */
@Suppress("LongParameterList")
class BoardController(
    private val boardId: String,
    private val catalog: CatalogRepository,
    private val maps: BoardMapRepository,
    private val boards: BoardRepository,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
    /** Writes left pending when the screen goes away (the screen's own scope is cancelled by then). */
    private val flushScope: CoroutineScope,
    private val keep: Keep,
    private val now: () -> Long = { Date.now().toLong() },
) {
    /** What the screen keeps for its history entry: the selection and the flip. */
    class Keep(var selected: String? = null, var bottom: Team = Team.RED, var panelHidden: Boolean = false)

    var state by mutableStateOf<BoardUiState>(BoardUiState.Loading)
        private set

    private var sandbox: BoardSandbox? = null
    private var saveJob: Job? = null
    private var unsaved: SavedBoard? = null
    private val written = ArrayDeque<SavedBoard>()

    private val listener = object : BoardSandbox.Listener {
        override fun selected(pieceId: String?) {
            keep.selected = pieceId
        }

        override fun flipped(bottom: Team) {
            keep.bottom = bottom
        }

        override fun changed(board: SavedBoard) = scheduleSave(board)
    }

    fun start() {
        scope.launch { load() }
    }

    @Suppress("TooGenericExceptionCaught") // a JS fetch failure arrives as a plain Error
    private suspend fun load() {
        val catalog = try {
            catalog.catalog.first()
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("SwallowedException") e: Throwable) {
            state = BoardUiState.Missing("The heroes couldn't be loaded. Check the connection and try again.")
            return
        }
        val maps = maps.maps.catch { emit(emptyList()) }.first()
        val owned = ownedProducts(settings)
        val loaded = boards.observe(boardId).map { Result.success(it) }.catch { emit(Result.failure(it)) }.first()
        if (loaded.isFailure) {
            state = BoardUiState.Missing("This board couldn't be read.")
            return
        }
        val board = loaded.getOrNull() ?: run {
            state = BoardUiState.Missing("This board was deleted.")
            return
        }
        val map = maps.firstOrNull { it.id == board.state.mapId } ?: run {
            state = BoardUiState.Missing("This board's map isn't in this build.")
            return
        }
        val opened = BoardSandbox(
            BoardUiState.Ready(
                board = board,
                map = map,
                heroes = catalog.boardHeroes(),
                ownedProducts = owned,
                bottomTeam = keep.bottom,
                selectedId = keep.selected?.takeIf { sel -> board.state.pieces.any { it.id == sel } },
            ),
            newPieceId = ::newPieceId,
            now = now,
            listener = listener,
        )
        sandbox = opened
        state = opened.state
        boards.observe(boardId).catch { /* An unreadable store keeps what is on screen. */ }.collect(::onStored)
    }

    private fun onStored(stored: SavedBoard?) {
        val sandbox = sandbox ?: return
        val ready = state as? BoardUiState.Ready ?: return
        if (stored == null) {
            // Deleted (in Boards, in another tab): never write it back.
            saveJob?.cancel()
            unsaved = null
            this.sandbox = null
            state = BoardUiState.Missing("This board was deleted.")
            return
        }
        if (stored == ready.board || stored in written || stored == unsaved) return
        saveJob?.cancel()
        unsaved = null
        sandbox.adopt(stored)
        state = sandbox.state
    }

    /** A tap on the map; true when it did anything (so a second tap on nothing zooms in). */
    fun tap(tap: MapTap): Boolean {
        val sandbox = sandbox ?: return false
        val did = sandbox.tap(tap)
        state = sandbox.state
        return did
    }

    fun onAction(action: BoardAction) {
        val sandbox = sandbox ?: return
        sandbox.onAction(action)
        state = sandbox.state
    }

    /** Writes a pending change now (the screen leaves, the page is hidden). */
    fun flush() {
        val pending = unsaved ?: return
        saveJob?.cancel()
        unsaved = null
        remember(pending)
        flushScope.launch { boards.upsert(pending) }
    }

    private fun scheduleSave(board: SavedBoard) {
        unsaved = board
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(BoardSandbox.SAVE_DELAY_MS)
            val pending = unsaved ?: return@launch
            unsaved = null
            remember(pending)
            flushScope.launch { boards.upsert(pending) }
        }
    }

    private fun remember(board: SavedBoard) {
        written.addLast(board)
        while (written.size > MAX_WRITTEN) written.removeFirst()
    }

    private companion object {
        const val MAX_WRITTEN = 16
    }
}

/** The products the group owns (Settings; empty = all). Settings not readable yet: all. */
@Suppress("TooGenericExceptionCaught") // a stub or a storage failure: the default
suspend fun ownedProducts(settings: SettingsRepository): Set<String> = try {
    settings.settings.catch { emit(AppSettings()) }.first().ownedProducts
} catch (e: CancellationException) {
    throw e
} catch (_: Throwable) {
    AppSettings().ownedProducts
}
