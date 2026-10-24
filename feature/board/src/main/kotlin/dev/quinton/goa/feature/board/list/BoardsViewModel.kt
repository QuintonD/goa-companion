package dev.quinton.goa.feature.board.list

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.quinton.goa.core.data.BoardMapRepository
import dev.quinton.goa.core.data.BoardRepository
import dev.quinton.goa.core.data.CatalogRepository
import dev.quinton.goa.core.data.DraftRecordRepository
import dev.quinton.goa.core.data.EditClock
import dev.quinton.goa.core.data.MatchRepository
import dev.quinton.goa.core.data.game.GameEntry
import dev.quinton.goa.core.data.game.SavedGameRepository
import dev.quinton.goa.core.data.game.StoredGame
import dev.quinton.goa.core.game.Controller
import dev.quinton.goa.core.game.GameFactoryProvider
import dev.quinton.goa.core.model.board.SavedBoard
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.board.BoardHero
import dev.quinton.goa.feature.board.boardHeroes
import dev.quinton.goa.feature.board.hero
import dev.quinton.goa.feature.board.setup.LineupSource
import dev.quinton.goa.feature.board.setup.lineupSources
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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
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

/** A game against the computer in progress (or finished, until its result is left). */
@Immutable
data class GameRowUi(
    val id: String,
    val red: List<BoardHero>,
    val blue: List<BoardHero>,
    /** The heroes the player controls. */
    val you: List<BoardHero>,
    val round: Int,
    val turn: Int,
    val updatedAt: Long,
    val finished: Boolean,
    val winner: Team?,
    /** Why it can't be resumed (unreadable, other rules): offered for deletion. */
    val problem: String? = null,
)

sealed interface BoardsUiState {
    data object Loading : BoardsUiState

    /**
     * [latest]: the newest finished draft or match, set up in one tap. [unreadable]: the boards
     * couldn't be read (the list shows what it can, never crashes).
     */
    @Immutable
    data class Ready(
        val boards: List<BoardRowUi>,
        val now: Long,
        val latest: LineupSource? = null,
        val unreadable: Boolean = false,
        /** Games against the computer, most recently played first. */
        val games: List<GameRowUi> = emptyList(),
    ) : BoardsUiState
}

/**
 * The Boards list: open, rename, duplicate, delete (undoable). Most recently changed first, under
 * the latest draft's or match's line-up. [restoreScope] outlives the screen: Undo on the app-wide
 * snackbar may come after Boards has closed.
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
    private val gameRepository: SavedGameRepository,
    provider: GameFactoryProvider,
) : ViewModel() {
    @Inject constructor(
        catalogRepository: CatalogRepository,
        mapRepository: BoardMapRepository,
        boardRepository: BoardRepository,
        draftRepository: DraftRecordRepository,
        matchRepository: MatchRepository,
        clock: EditClock,
        gameRepository: SavedGameRepository,
        provider: GameFactoryProvider,
    ) : this(
        catalogRepository,
        mapRepository,
        boardRepository,
        draftRepository,
        matchRepository,
        clock,
        CoroutineScope(SupervisorJob() + Dispatchers.Default),
        gameRepository,
        provider,
    )

    private val newId: () -> String = { UUID.randomUUID().toString() }

    private val boards = boardRepository.boards
        .map { it to false }
        .catch { emit(emptyList<SavedBoard>() to true) }

    private val latest = combine(
        draftRepository.observeDrafts().catch { emit(emptyList()) },
        matchRepository.observeMatches().catch { emit(emptyList()) },
    ) { drafts, matches -> drafts to matches }

    /** Saved games and the engine version they must have been saved with (null: the engine didn't load). */
    private val games = combine(
        gameRepository.games.catch { emit(emptyList()) },
        flow<Int?> { emit(provider.get().engineVersion) }.catch { emit(null) },
    ) { games, version -> games to version }

    val uiState: StateFlow<BoardsUiState> = combine(
        catalogRepository.catalog.catch { emit(Catalog.EMPTY) },
        mapRepository.maps.catch { emit(emptyList()) },
        boards,
        latest,
        games,
    ) { catalog, maps, (boards, unreadable), (drafts, matches), (games, version) ->
        val heroes = catalog.boardHeroes()
        BoardsUiState.Ready(
            games = games.map { gameRow(it, version, heroes) },
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
            latest = lineupSources(drafts, matches, heroes, limit = 1).firstOrNull(),
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

    /** Deletes a saved game (one that can't be resumed, or one the player gives up). */
    fun deleteGame(id: String) {
        restoreScope.launch { gameRepository.delete(id) }
    }

    private fun gameRow(entry: GameEntry, version: Int?, heroes: Map<String, BoardHero>): GameRowUi {
        val game = entry as? StoredGame
            ?: return GameRowUi(entry.id, emptyList(), emptyList(), emptyList(), 0, 0, 0L, false, null, UNREADABLE)
        val seats = game.game.setup.seats
        return GameRowUi(
            id = game.id,
            red = seats.filter { it.team == Team.RED }.map { heroes.hero(it.heroId) },
            blue = seats.filter { it.team == Team.BLUE }.map { heroes.hero(it.heroId) },
            you = seats.filter { it.controller == Controller.Human }.map { heroes.hero(it.heroId) },
            round = game.round,
            turn = game.turn,
            updatedAt = game.updatedAt,
            finished = game.finished,
            winner = game.winner,
            problem = OTHER_RULES.takeIf { version != null && game.game.engineVersion != version },
        )
    }

    internal companion object {
        const val UNREADABLE = "This saved game can't be read."
        const val OTHER_RULES = "Saved with another version of the rules: it can't be resumed."
        const val MAX_NAME = 60
        private const val STOP_TIMEOUT_MS = 5_000L

        fun copyName(name: String): String = "$name (copy)".take(MAX_NAME)
    }
}
