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
import dev.quinton.goa.core.data.GameSaveRepository
import dev.quinton.goa.core.data.MatchRepository
import dev.quinton.goa.core.data.game.StoredGame
import dev.quinton.goa.core.game.Controller
import dev.quinton.goa.core.game.GameFactory
import dev.quinton.goa.feature.board.play.PlayText
import javax.inject.Provider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
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

/** The game against the computer saved on this phone, as the Boards entry shows it. */
@Immutable
data class ResumeUi(
    val red: List<BoardHero>,
    val blue: List<BoardHero>,
    /** "You play Arien · Computer: Hard". */
    val summary: String,
    val savedAt: Long,
    /** The save can't be read: opening it offers to delete it. */
    val unreadable: Boolean = false,
)

/** Play against the computer (ADR-035): offered once heroes are playable; a game in progress to resume. */
@Immutable
data class PlayHubUi(val available: Boolean = false, val resume: ResumeUi? = null)

sealed interface BoardsUiState {
    data object Loading : BoardsUiState

    /**
     * [latest]: the newest finished draft or match, set up in one tap. [unreadable]: the boards
     * couldn't be read (the list shows what it can, never crashes). [play]: the entry to games
     * against the computer and puzzles.
     */
    @Immutable
    data class Ready(
        val boards: List<BoardRowUi>,
        val now: Long,
        val latest: LineupSource? = null,
        val unreadable: Boolean = false,
        val play: PlayHubUi = PlayHubUi(),
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
    games: GameSaveRepository? = null,
    factory: (suspend () -> GameFactory)? = null,
) : ViewModel() {
    @Inject constructor(
        catalogRepository: CatalogRepository,
        mapRepository: BoardMapRepository,
        boardRepository: BoardRepository,
        draftRepository: DraftRecordRepository,
        matchRepository: MatchRepository,
        clock: EditClock,
        games: GameSaveRepository,
        factory: Provider<GameFactory>,
    ) : this(
        catalogRepository,
        mapRepository,
        boardRepository,
        draftRepository,
        matchRepository,
        clock,
        CoroutineScope(SupervisorJob() + Dispatchers.Default),
        games,
        { withContext(Dispatchers.Default) { factory.get() } },
    )

    /** Whether any game can be set up yet: two playable heroes and a playable map. */
    private val playable = flow {
        val engine = try {
            factory?.invoke()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        emit(engine != null && engine.playableHeroes.size >= 2 && engine.playableMaps.isNotEmpty())
    }

    private val play = combine(
        playable,
        games?.current?.catch { emit(StoredGame.None) } ?: flowOf(StoredGame.None),
    ) { available, stored -> available to stored }

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
        play,
    ) { catalog, maps, (boards, unreadable), (drafts, matches), (available, stored) ->
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
            latest = lineupSources(drafts, matches, heroes, limit = 1).firstOrNull(),
            unreadable = unreadable,
            play = PlayHubUi(available = available, resume = resumeOf(stored, heroes)),
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
        const val MAX_NAME = 60
        private const val STOP_TIMEOUT_MS = 5_000L

        fun copyName(name: String): String = "$name (copy)".take(MAX_NAME)

        /** The saved game as its entry shows it: line-up, who you play, the computer's levels. */
        fun resumeOf(stored: StoredGame, heroes: Map<String, BoardHero>): ResumeUi? = when (stored) {
            StoredGame.None -> null
            StoredGame.Unreadable -> ResumeUi(emptyList(), emptyList(), "This saved game can't be read.", 0, unreadable = true)
            is StoredGame.Saved -> {
                val seats = stored.game.game.setup.seats
                val mine = seats.filter { it.controller == Controller.Human }.map { heroes.hero(it.heroId).name }
                val levels = seats.mapNotNull { (it.controller as? Controller.Ai)?.level }.distinct()
                ResumeUi(
                    red = seats.filter { it.team == Team.RED }.map { heroes.hero(it.heroId) },
                    blue = seats.filter { it.team == Team.BLUE }.map { heroes.hero(it.heroId) },
                    summary = listOfNotNull(
                        "You play ${mine.joinToString(" and ")}".takeIf { mine.isNotEmpty() },
                        levels.takeIf { it.isNotEmpty() }?.let { l -> "Computer: " + l.joinToString(", ") { PlayText.level(it) } },
                    ).joinToString(" · "),
                    savedAt = stored.game.game.savedAt,
                )
            }
        }
    }
}
