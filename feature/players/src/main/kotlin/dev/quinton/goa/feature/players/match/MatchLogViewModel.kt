package dev.quinton.goa.feature.players.match

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.quinton.goa.core.data.CatalogRepository
import dev.quinton.goa.core.data.MatchRepository
import dev.quinton.goa.core.data.PlayerRepository
import dev.quinton.goa.core.data.rating.RatingsSnapshotRepository
import dev.quinton.goa.core.domain.match.RematchKind
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.presentation.players.MatchLog
import dev.quinton.goa.core.presentation.players.MatchLogUiState
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Holds the most recently deleted match so whichever screen is visible next (the log) can offer
 * "Undo". Process-scoped and in memory only.
 */
@Singleton
class RecentlyDeletedMatch @Inject constructor() {
    private val state = MutableStateFlow<Match?>(null)
    val match: StateFlow<Match?> = state.asStateFlow()

    /** When [match] was deleted (epoch ms); undo is only offered for a short while. */
    var deletedAt: Long = 0L
        private set

    fun put(match: Match, now: Long = System.currentTimeMillis()) {
        deletedAt = now
        state.value = match
    }

    fun isFresh(now: Long = System.currentTimeMillis()): Boolean = now - deletedAt <= UNDO_WINDOW_MS

    fun clear() {
        state.value = null
    }

    companion object {
        const val UNDO_WINDOW_MS = 30_000L
    }
}

@HiltViewModel
class MatchLogViewModel @Inject constructor(
    private val matches: MatchRepository,
    players: PlayerRepository,
    catalog: CatalogRepository,
    ratings: RatingsSnapshotRepository,
    private val recentlyDeleted: RecentlyDeletedMatch,
    private val recentlySaved: RecentlySavedMatch,
    private val rematches: RematchLauncher,
) : ViewModel() {
    private val events = Channel<MatchNav>(Channel.BUFFERED)

    /** Rematch / resume requests: open Match setup (Timer tab) or the draft. */
    val navigation: Flow<MatchNav> = events.receiveAsFlow()

    private val base = combine(
        matches.observeMatches(),
        players.observePlayers(includeArchived = true),
        catalog.catalog.onStart { emit(Catalog.EMPTY) },
        ratings.observeSnapshot(),
        recentlyDeleted.match,
    ) { list, people, cat, snapshot, undo ->
        MatchLogUiState(
            loading = false,
            groups = MatchLog.group(list, people.associateBy { it.id }, cat, snapshot.ratings.matchChanges),
            total = list.size,
            undo = undo?.takeIf { recentlyDeleted.isFresh() },
        )
    }

    val uiState: StateFlow<MatchLogUiState> = combine(base, recentlySaved.matchId) { state, saved ->
        state.copy(highlightId = saved?.takeIf { id -> state.groups.any { g -> g.matches.any { it.id == id } } })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MatchLogUiState())

    /** The list scrolled to the newly saved match. */
    fun highlightShown(id: String) = recentlySaved.consumed(id)

    /** [kind] null = draft again with the same players. */
    fun rematch(matchId: String, kind: RematchKind?) {
        viewModelScope.launch {
            val source = matches.observeMatch(matchId).first() ?: return@launch
            if (kind == null) {
                rematches.redraft(source)
                events.send(MatchNav.OpenDraft)
            } else {
                rematches.start(source, kind)?.let { events.send(it) }
            }
        }
    }

    /** A pending match continues in Match setup, then the timer. */
    fun resume(matchId: String) {
        viewModelScope.launch { events.send(MatchNav.OpenSetup(matchId)) }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            // The copy as stored (logged pilots, not merge-resolved), for undo.
            val match = matches.delete(id) ?: return@launch
            recentlyDeleted.put(match)
        }
    }

    fun undoDelete() {
        val match = recentlyDeleted.match.value?.takeIf { recentlyDeleted.isFresh() } ?: return
        recentlyDeleted.clear()
        // Not if the match exists again (an edit synced in since wins), as on the web.
        viewModelScope.launch { matches.restore(match) }
    }

    fun dismissUndo() = recentlyDeleted.clear()
}
