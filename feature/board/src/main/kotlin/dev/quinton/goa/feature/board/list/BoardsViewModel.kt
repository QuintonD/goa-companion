package dev.quinton.goa.feature.board.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.quinton.goa.core.data.BoardMapRepository
import dev.quinton.goa.core.data.BoardRepository
import dev.quinton.goa.core.data.CatalogRepository
import dev.quinton.goa.core.data.DraftRecordRepository
import dev.quinton.goa.core.data.EditClock
import dev.quinton.goa.core.data.MatchRepository
import dev.quinton.goa.core.model.board.SavedBoard
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.feature.board.boardHeroes
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The Boards list (the board sandbox): open, rename, duplicate, delete (undoable). Most recently changed first,
 * under the latest draft's or match's line-up. Games against the computer and puzzles start from the Play tab's
 * first screen ([PlayHomeViewModel]). [restoreScope] outlives the screen: Undo on the app-wide snackbar may come
 * after Boards has closed.
 */
@HiltViewModel
class BoardsViewModel internal constructor(
    catalogRepository: CatalogRepository,
    mapRepository: BoardMapRepository,
    private val boardRepository: BoardRepository,
    draftRepository: DraftRecordRepository,
    matchRepository: MatchRepository,
    private val clock: EditClock,
    private val restoreScope: CoroutineScope,
) : ViewModel() {
    @Inject constructor(
        catalogRepository: CatalogRepository,
        mapRepository: BoardMapRepository,
        boardRepository: BoardRepository,
        draftRepository: DraftRecordRepository,
        matchRepository: MatchRepository,
        clock: EditClock,
    ) : this(
        catalogRepository,
        mapRepository,
        boardRepository,
        draftRepository,
        matchRepository,
        clock,
        CoroutineScope(SupervisorJob() + Dispatchers.Default),
    )

    private val newId: () -> String = { UUID.randomUUID().toString() }

    private val boards = boardRepository.boards
        .map { it to false }
        .catch { emit(emptyList<SavedBoard>() to true) }

    private val latest = combine(
        draftRepository.observeDrafts().catch { emit(emptyList()) },
        matchRepository.observeMatches().catch { emit(emptyList()) },
    ) { drafts, matches -> drafts to matches }

    val uiState: StateFlow<BoardsUiState> = combine(
        catalogRepository.catalog.catch { emit(Catalog.EMPTY) },
        mapRepository.maps.catch { emit(emptyList()) },
        boards,
        latest,
    ) { catalog, maps, (boards, unreadable), (drafts, matches) ->
        val heroes = catalog.boardHeroes()
        BoardsUiState.Ready(
            boards = boards.map { BoardsRules.row(it, maps, heroes) },
            now = clock.now(),
            latest = BoardsRules.latest(drafts, matches, heroes),
            unreadable = unreadable,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), BoardsUiState.Loading)

    fun rename(id: String, name: String) {
        val trimmed = name.trim().take(MAX_NAME)
        if (trimmed.isEmpty()) return
        edit(id) { it.copy(name = trimmed, updatedAt = clock.now()) }
    }

    fun duplicate(id: String) {
        viewModelScope.launch {
            val board = boardRepository.observe(id).first() ?: return@launch
            val now = clock.now()
            withContext(NonCancellable) {
                boardRepository.upsert(
                    board.copy(id = newId(), name = copyName(board.name), createdAt = now, updatedAt = now),
                )
            }
        }
    }

    /** Deletes the board and hands it back, so the caller can offer Undo with [restore]. */
    suspend fun delete(id: String): SavedBoard? {
        val board = boardRepository.observe(id).first() ?: return null
        withContext(NonCancellable) { boardRepository.delete(id) }
        return board
    }

    /** Puts a deleted board back; runs even when Boards has closed since (the snackbar is app-wide). */
    fun restore(board: SavedBoard) {
        restoreScope.launch { boardRepository.upsert(board) }
    }

    private fun edit(id: String, transform: (SavedBoard) -> SavedBoard) {
        viewModelScope.launch {
            val board = boardRepository.observe(id).first() ?: return@launch
            withContext(NonCancellable) { boardRepository.upsert(transform(board)) }
        }
    }

    internal companion object {
        const val MAX_NAME = BoardsRules.MAX_NAME
        private const val STOP_TIMEOUT_MS = 5_000L

        fun copyName(name: String): String = BoardsRules.copyName(name)
    }
}
