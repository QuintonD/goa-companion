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

/** One puzzle in the list, with how it has gone on this phone. */
@Immutable
data class PuzzleRowUi(
    val info: PuzzleInfo,
    val heroes: List<BoardHero>,
    val progress: PuzzleProgress,
)

/** The list's filters: none chosen = everything. */
@Immutable
data class PuzzleFilters(val difficulties: Set<Int> = emptySet(), val themes: Set<String> = emptySet()) {
    fun matches(info: PuzzleInfo): Boolean = (difficulties.isEmpty() || info.difficulty in difficulties) &&
        (themes.isEmpty() || info.theme in themes)

    val any: Boolean get() = difficulties.isNotEmpty() || themes.isNotEmpty()
}

sealed interface PuzzleListUiState {
    data object Loading : PuzzleListUiState

    /** No puzzle can be played in this build yet (their heroes are not playable). */
    data object Empty : PuzzleListUiState

    /**
     * [next]: the first unsolved puzzle in book order (Continue), null once all are solved.
     * [rows]: the puzzles that pass [filters]; [difficulties] and [themes] are the choices there are.
     */
    @Immutable
    data class Ready(
        val rows: List<PuzzleRowUi>,
        val next: PuzzleRowUi?,
        val solved: Int,
        val total: Int,
        val filters: PuzzleFilters,
        val difficulties: List<Int>,
        val themes: List<String>,
    ) : PuzzleListUiState
}

sealed interface PuzzleListAction {
    data class ToggleDifficulty(val difficulty: Int) : PuzzleListAction

    data class ToggleTheme(val theme: String) : PuzzleListAction

    data object ClearFilters : PuzzleListAction

    /** Forgets every puzzle's progress (the screen asks first). */
    data object ResetProgress : PuzzleListAction
}

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
        if (list.isEmpty()) return@combine PuzzleListUiState.Empty
        val heroes = catalog.boardHeroes()
        val rows = list.map { info ->
            PuzzleRowUi(info, info.heroes.map(heroes::hero), progress[info.id] ?: PuzzleProgress())
        }
        PuzzleListUiState.Ready(
            rows = rows.filter { filters.matches(it.info) },
            next = rows.firstOrNull { !it.progress.solved },
            solved = rows.count { it.progress.solved },
            total = rows.size,
            filters = filters,
            difficulties = list.map { it.difficulty }.distinct().sorted(),
            themes = list.map { it.theme }.distinct().sorted(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PuzzleListUiState.Loading)

    fun onAction(action: PuzzleListAction) {
        when (action) {
            is PuzzleListAction.ToggleDifficulty -> filters.update {
                it.copy(difficulties = it.difficulties.toggle(action.difficulty))
            }

            is PuzzleListAction.ToggleTheme -> filters.update { it.copy(themes = it.themes.toggle(action.theme)) }

            PuzzleListAction.ClearFilters -> filters.value = PuzzleFilters()

            PuzzleListAction.ResetProgress -> viewModelScope.launch {
                withContext(NonCancellable) { progressRepository.reset() }
            }
        }
    }

    private fun <T> Set<T>.toggle(item: T): Set<T> = if (item in this) this - item else this + item

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
