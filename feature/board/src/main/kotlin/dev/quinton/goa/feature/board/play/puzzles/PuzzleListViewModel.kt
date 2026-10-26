package dev.quinton.goa.feature.board.play.puzzles

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.quinton.goa.core.data.CatalogRepository
import dev.quinton.goa.core.data.PuzzleProgressRepository
import dev.quinton.goa.core.data.puzzle.PuzzleProgress
import dev.quinton.goa.core.game.PuzzleBook
import dev.quinton.goa.core.game.PuzzleInfo
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.feature.board.BoardHero
import dev.quinton.goa.feature.board.boardHeroes
import dev.quinton.goa.feature.board.hero
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The puzzle list (PRODUCT §10): numbered, with theme, difficulty, heroes and progress; Continue first. */
@HiltViewModel
class PuzzleListViewModel internal constructor(
    catalogRepository: CatalogRepository,
    private val progressRepository: PuzzleProgressRepository,
    book: suspend () -> PuzzleBook,
) : ViewModel() {
    @Inject constructor(
        catalogRepository: CatalogRepository,
        progressRepository: PuzzleProgressRepository,
        book: Provider<PuzzleBook>,
    ) : this(catalogRepository, progressRepository, { withContext(Dispatchers.Default) { book.get() } })

    private val filters = MutableStateFlow(PuzzleFilters())

    private val puzzles = flow {
        emit(
            try {
                book().puzzles
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emptyList()
            },
        )
    }

    val uiState: StateFlow<PuzzleListUiState> = combine(
        puzzles,
        catalogRepository.catalog.catch { emit(Catalog.EMPTY) },
        progressRepository.progress.catch { emit(emptyMap()) },
        filters,
    ) { list, catalog, progress, filters ->
        puzzleList(list, catalog.boardHeroes(), progress, filters)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PuzzleListUiState.Loading)

    fun onAction(action: PuzzleListAction) {
        when (action) {
            is PuzzleListAction.ToggleDifficulty, is PuzzleListAction.ToggleTheme, PuzzleListAction.ClearFilters ->
                filters.update { it.after(action) }

            PuzzleListAction.ResetProgress -> viewModelScope.launch {
                withContext(NonCancellable) { progressRepository.reset() }
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
