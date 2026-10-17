package dev.quinton.goa.feature.players.match

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.quinton.goa.core.data.CatalogRepository
import dev.quinton.goa.core.data.MatchRepository
import dev.quinton.goa.core.data.PlayerRepository
import dev.quinton.goa.core.data.rating.RatingsSnapshotRepository
import dev.quinton.goa.core.domain.match.RematchKind
import dev.quinton.goa.core.domain.rating.MatchRatingChange
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.match.WinCondition
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.feature.players.common.Dates
import dev.quinton.goa.feature.players.common.heroName
import dev.quinton.goa.feature.players.common.playerName
import java.time.LocalDate
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

/** One hero seat as shown in lists. */
data class SeatUi(
    val player: Player?,
    val playerName: String,
    val heroId: String?,
    val heroName: String,
    val portrait: String?,
    val handicap: Boolean,
    val doublePilot: Boolean,
    /** Player-space rating change from this match (only on the pilot's first seat). */
    val delta: Double? = null,
)

data class MatchRowUi(
    val id: String,
    val status: MatchStatus,
    val winner: Team?,
    val mode: GameMode,
    val winCondition: WinCondition?,
    val durationMin: Int?,
    val map: String?,
    val time: String,
    val red: List<SeatUi>,
    val blue: List<SeatUi>,
    val notes: String,
    /** Rematch choices for a completed match; empty otherwise. */
    val rematchOptions: List<RematchOption> = emptyList(),
)

data class MatchGroup(val header: String, val matches: List<MatchRowUi>)

data class MatchLogUiState(
    val loading: Boolean = true,
    val groups: List<MatchGroup> = emptyList(),
    val total: Int = 0,
    /** The match just deleted, offered for undo. */
    val undo: Match? = null,
    /** The match just saved in the editor: the list scrolls to it once. */
    val highlightId: String? = null,
) {
    val isEmpty: Boolean get() = !loading && groups.isEmpty()
}

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
            groups = group(list, people.associateBy { it.id }, cat, snapshot.ratings.matchChanges),
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
            val match = matches.observeMatch(id).first() ?: return@launch
            matches.delete(id)
            recentlyDeleted.put(match)
        }
    }

    fun undoDelete() {
        val match = recentlyDeleted.match.value ?: return
        recentlyDeleted.clear()
        viewModelScope.launch { matches.upsert(match) }
    }

    fun dismissUndo() = recentlyDeleted.clear()

    companion object {
        fun group(
            matches: List<Match>,
            players: Map<String, Player>,
            catalog: Catalog,
            changes: Map<String, MatchRatingChange>,
            today: LocalDate = LocalDate.now(),
        ): List<MatchGroup> = matches
            .sortedWith(
                compareByDescending<Match> {
                    it.playedAt
                }.thenByDescending { it.createdAt }.thenByDescending { it.id },
            )
            .groupBy { Dates.localDate(it.playedAt) }
            .map { (_, dayMatches) ->
                MatchGroup(
                    header = Dates.dayHeader(dayMatches.first().playedAt, today),
                    matches = dayMatches.map { it.toRow(players, catalog, changes[it.id]) },
                )
            }

        fun Match.toRow(players: Map<String, Player>, catalog: Catalog, change: MatchRatingChange?): MatchRowUi {
            fun seats(team: Team): List<SeatUi> {
                val seats = team(team)
                val counts = seats.groupingBy { it.playerId }.eachCount()
                val shownDelta = mutableSetOf<String>()
                return seats.map { seat ->
                    SeatUi(
                        player = players[seat.playerId],
                        playerName = players.playerName(seat.playerId),
                        heroId = seat.heroId,
                        heroName = catalog.heroName(seat.heroId),
                        portrait = seat.heroId?.let { catalog.hero(it)?.portrait },
                        handicap = seat.handicap,
                        doublePilot = (counts[seat.playerId] ?: 0) > 1,
                        delta = change?.playerDeltas?.get(seat.playerId)?.takeIf { shownDelta.add(seat.playerId) },
                    )
                }
            }
            return MatchRowUi(
                id = id,
                status = status,
                winner = winner,
                mode = mode,
                winCondition = winCondition,
                durationMin = durationMin,
                map = map,
                time = Dates.time(playedAt),
                red = seats(Team.RED),
                blue = seats(Team.BLUE),
                notes = notes,
                rematchOptions = if (status == MatchStatus.COMPLETED) rematchOptions(this) else emptyList(),
            )
        }
    }
}
