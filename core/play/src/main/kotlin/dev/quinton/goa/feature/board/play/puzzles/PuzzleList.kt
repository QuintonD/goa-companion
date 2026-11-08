package dev.quinton.goa.feature.board.play.puzzles

import dev.quinton.goa.core.data.puzzle.PuzzleProgress
import dev.quinton.goa.core.game.PuzzleInfo
import dev.quinton.goa.feature.board.BoardHero
import dev.quinton.goa.feature.board.hero

/** One puzzle in the list, with how it has gone on this phone. */
data class PuzzleRowUi(
    val info: PuzzleInfo,
    val heroes: List<BoardHero>,
    val progress: PuzzleProgress,
)

/** The list's filters: none chosen = everything. */
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
     * [rows]: the puzzles that pass [filters]; [difficulties] and [themes] (those shared by two or
     * more puzzles) are the choices there are. [solved] and [total] count the whole book; [roundsSolved]
     * and [roundsTotal] its full rounds (ADR-052), so the list and "Puzzle 3 of 50" agree part by part.
     */
    data class Ready(
        val rows: List<PuzzleRowUi>,
        val next: PuzzleRowUi?,
        val solved: Int,
        val total: Int,
        val filters: PuzzleFilters,
        val difficulties: List<Int>,
        val themes: List<String>,
        val roundsSolved: Int = 0,
        val roundsTotal: Int = 0,
        /** How many of the book's positions each difficulty has (the filter says it, as "Positions" counts). */
        val difficultyCounts: Map<Int, Int> = emptyMap(),
    ) : PuzzleListUiState {
        /** The book's first part, the positions ("Puzzle 3 of 50"). */
        val positionsSolved: Int get() = solved - roundsSolved

        val positionsTotal: Int get() = total - roundsTotal
    }
}

sealed interface PuzzleListAction {
    data class ToggleDifficulty(val difficulty: Int) : PuzzleListAction

    data class ToggleTheme(val theme: String) : PuzzleListAction

    data object ClearFilters : PuzzleListAction

    /** Forgets every puzzle's progress (the screen asks first). */
    data object ResetProgress : PuzzleListAction
}

/** The filters after a filter [action] (the list's other actions leave them as they are). */
fun PuzzleFilters.after(action: PuzzleListAction): PuzzleFilters = when (action) {
    is PuzzleListAction.ToggleDifficulty -> copy(difficulties = difficulties.toggle(action.difficulty))
    is PuzzleListAction.ToggleTheme -> copy(themes = themes.toggle(action.theme))
    PuzzleListAction.ClearFilters -> PuzzleFilters()
    PuzzleListAction.ResetProgress -> this
}

private fun <T> Set<T>.toggle(item: T): Set<T> = if (item in this) this - item else this + item

/**
 * The list from the book's playable puzzles ([list], in book order), the heroes, this device's
 * [progress] and the [filters]: Continue = the first unsolved puzzle; theme chips for themes two or
 * more puzzles share.
 */
fun puzzleList(
    list: List<PuzzleInfo>,
    heroes: Map<String, BoardHero>,
    progress: Map<String, PuzzleProgress>,
    filters: PuzzleFilters,
): PuzzleListUiState {
    if (list.isEmpty()) return PuzzleListUiState.Empty
    val rows = list.map { info ->
        PuzzleRowUi(info, info.heroes.map(heroes::hero), progress[info.id] ?: PuzzleProgress())
    }
    return PuzzleListUiState.Ready(
        rows = rows.filter { filters.matches(it.info) },
        next = rows.firstOrNull { !it.progress.solved },
        solved = rows.count { it.progress.solved },
        total = rows.size,
        filters = filters,
        difficulties = list.map { it.difficulty }.distinct().sorted(),
        // Only themes shared by two or more puzzles make a useful filter.
        themes = list.groupingBy { it.theme }.eachCount().filterValues { it > 1 }.keys.sorted(),
        roundsSolved = rows.count { it.info.fullRound && it.progress.solved },
        roundsTotal = rows.count { it.info.fullRound },
        // Counted as the list's first part is ("1 of 50 positions"): one way of counting on the screen.
        difficultyCounts = list.filterNot { it.fullRound }.groupingBy { it.difficulty }.eachCount(),
    )
}
