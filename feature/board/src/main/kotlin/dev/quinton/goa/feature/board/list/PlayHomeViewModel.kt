package dev.quinton.goa.feature.board.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.quinton.goa.core.data.CatalogRepository
import dev.quinton.goa.core.data.EditClock
import dev.quinton.goa.core.data.GameSaveRepository
import dev.quinton.goa.core.data.PuzzleProgressRepository
import dev.quinton.goa.core.data.game.StoredGame
import dev.quinton.goa.core.game.GameFactory
import dev.quinton.goa.core.game.PuzzleBook
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.feature.board.BoardHero
import dev.quinton.goa.feature.board.boardHeroes
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

/** The Play tab's first screen: what its entries say. */
sealed interface PlayHomeUiState {
    data object Loading : PlayHomeUiState

    /** [play]: whether games can be set up, the game in progress, the puzzles solved. */
    data class Ready(val play: PlayHubUi, val now: Long) : PlayHomeUiState
}

/**
 * The Play tab's first screen (ADR-055): a game in progress to resume, whether a game against the computer can be
 * set up (two playable heroes and a map), and how many of the playable puzzles are solved. All from the engine,
 * the saved game and the puzzle progress, never assumed.
 */
@HiltViewModel
class PlayHomeViewModel internal constructor(
    catalogRepository: CatalogRepository,
    games: GameSaveRepository,
    private val clock: EditClock,
    factory: suspend () -> GameFactory?,
    puzzleBook: suspend () -> PuzzleBook?,
    puzzleProgress: PuzzleProgressRepository,
) : ViewModel() {
    @Inject constructor(
        catalogRepository: CatalogRepository,
        games: GameSaveRepository,
        clock: EditClock,
        factory: Provider<GameFactory>,
        puzzleBook: Provider<PuzzleBook>,
        puzzleProgress: PuzzleProgressRepository,
    ) : this(
        catalogRepository,
        games,
        clock,
        { withContext(Dispatchers.Default) { factory.get() } },
        { withContext(Dispatchers.Default) { puzzleBook.get() } },
        puzzleProgress,
    )

    /** Whether any game can be set up yet: two playable heroes and a playable map. */
    private val playable = flow {
        val engine = orNull { factory() }
        emit(engine != null && engine.playableHeroes.size >= 2 && engine.playableMaps.isNotEmpty())
    }

    /** The playable puzzles (empty while none can be played). */
    private val playablePuzzles = flow { emit(orNull { puzzleBook()?.puzzles }.orEmpty()) }

    private val puzzles = combine(
        playablePuzzles,
        puzzleProgress.progress.catch { emit(emptyMap()) },
    ) { list, progress -> BoardsRules.puzzleHubByPart(list, progress) }

    val uiState: StateFlow<PlayHomeUiState> = combine(
        catalogRepository.catalog.catch { emit(Catalog.EMPTY) },
        playable,
        games.current.catch { emit(StoredGame.None) },
        puzzles,
    ) { catalog, available, stored, puzzles ->
        PlayHomeUiState.Ready(
            play = PlayHubUi(
                available = available,
                resume = resumeOf(stored, catalog.boardHeroes()),
                puzzles = puzzles,
            ),
            now = clock.now(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PlayHomeUiState.Loading)

    internal companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        fun resumeOf(stored: StoredGame, heroes: Map<String, BoardHero>): ResumeUi? = BoardsRules.resumeOf(
            stored,
            heroes,
        )

        /** [block]'s value, or null when it fails: a broken engine or puzzle book leaves its entry unavailable. */
        private suspend fun <T> orNull(block: suspend () -> T?): T? = try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }
}
