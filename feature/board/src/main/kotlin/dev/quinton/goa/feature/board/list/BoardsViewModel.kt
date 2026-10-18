package dev.quinton.goa.feature.board.list

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.quinton.goa.core.data.BoardMapRepository
import dev.quinton.goa.core.data.BoardRepository
import dev.quinton.goa.core.data.CatalogRepository
import dev.quinton.goa.core.data.EditClock
import dev.quinton.goa.core.model.board.SavedBoard
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.board.BoardHero
import dev.quinton.goa.feature.board.boardHeroes
import dev.quinton.goa.feature.board.hero
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One saved board in the list. */
@Immutable
data class BoardRowUi(
    val id: String,
    val name: String,
    val mapName: String,
    val mapVerified: Boolean,
    val red: List<BoardHero>,
    val blue: List<BoardHero>,
    val updatedAt: Long,
) {
    fun team(team: Team): List<BoardHero> = if (team == Team.RED) red else blue
}

sealed interface BoardsUiState {
    data object Loading : BoardsUiState

    @Immutable
    data class Ready(val boards: List<BoardRowUi>, val now: Long) : BoardsUiState
}

/** The Boards list: open, rename, duplicate, delete (undoable). Most recently changed first. */
@HiltViewModel
class BoardsViewModel @Inject constructor(
    catalogRepository: CatalogRepository,
    mapRepository: BoardMapRepository,
    private val boardRepository: BoardRepository,
    private val clock: EditClock,
) : ViewModel() {
    private val newId: () -> String = { UUID.randomUUID().toString() }

    val uiState: StateFlow<BoardsUiState> = combine(
        catalogRepository.catalog.catch { emit(Catalog.EMPTY) },
        mapRepository.maps.catch { emit(emptyList()) },
        boardRepository.boards,
    ) { catalog, maps, boards ->
        val heroes = catalog.boardHeroes()
        BoardsUiState.Ready(
            boards = boards.map { board ->
                val map = maps.firstOrNull { it.id == board.state.mapId }
                BoardRowUi(
                    id = board.id,
                    name = board.name,
                    mapName = map?.name ?: "Map not in this build",
                    mapVerified = map?.verified ?: true,
                    red = board.lineup.red.map(heroes::hero),
                    blue = board.lineup.blue.map(heroes::hero),
                    updatedAt = board.updatedAt,
                )
            },
            now = clock.now(),
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

    fun restore(board: SavedBoard) {
        viewModelScope.launch { withContext(NonCancellable) { boardRepository.upsert(board) } }
    }

    private fun edit(id: String, transform: (SavedBoard) -> SavedBoard) {
        viewModelScope.launch {
            val board = boardRepository.observe(id).first() ?: return@launch
            withContext(NonCancellable) { boardRepository.upsert(transform(board)) }
        }
    }

    internal companion object {
        const val MAX_NAME = 60
        private const val STOP_TIMEOUT_MS = 5_000L

        fun copyName(name: String): String = "$name (copy)".take(MAX_NAME)
    }
}
