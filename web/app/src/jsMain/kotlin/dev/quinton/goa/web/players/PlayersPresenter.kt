package dev.quinton.goa.web.players

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.quinton.goa.core.data.CatalogRepository
import dev.quinton.goa.core.data.rating.RatingsSnapshot
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.feature.players.match.MatchLogUiState
import dev.quinton.goa.feature.players.match.MatchRows
import dev.quinton.goa.feature.players.meta.HeroSort
import dev.quinton.goa.feature.players.meta.MetaBoard
import dev.quinton.goa.feature.players.meta.MetaUiState
import dev.quinton.goa.feature.players.meta.MetaView
import dev.quinton.goa.feature.players.players.Leaderboard
import dev.quinton.goa.feature.players.players.PlayersUiState
import dev.quinton.goa.web.data.GroupData
import kotlin.js.Date
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/**
 * What the Players tab shows, built by the shared builders (core:group-presentation) from the
 * data contracts — the web's stand-in for the phone's PlayersViewModel, MatchLogViewModel and
 * MetaViewModel. One per app (the screens come and go; the undo and "just saved" survive them, as
 * the phone's process-scoped RecentlyDeletedMatch and RecentlySavedMatch do).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayersPresenter(private val group: GroupData, catalog: CatalogRepository) {
    val catalog: Flow<Catalog> = catalog.catalog.onStart { emit(Catalog.EMPTY) }

    /** "Show archived players". */
    val showArchived = MutableStateFlow(false)

    /** The Meta tab's table/cards and sort. */
    var metaView by mutableStateOf(MetaView.TABLE)
    var heroSort by mutableStateOf(HeroSort.RANK)

    /** The match just saved in the editor: the log scrolls to it once. */
    var recentlySaved by mutableStateOf<String?>(null)

    /** The match just deleted, offered for undo for [UNDO_WINDOW_MS]. */
    var recentlyDeleted: Pair<Match, Double>? = null

    private val snapshot: Flow<RatingsSnapshot> = group.ratingsSnapshot.observeSnapshot()

    /** "You", resolved through merges, and whether this browser is in a group. */
    private val me: Flow<Pair<String?, Boolean>> = group.group.state
        .map { it.mePlayerId to it.inGroup }
        .distinctUntilChanged()
        .flatMapLatest { (id, inGroup) ->
            if (id == null) flowOf(null to inGroup) else group.players.observePlayer(id).map { it?.id to inGroup }
        }

    private val serverFlags: Flow<Pair<Boolean, Boolean>> = group.group.state.map {
        val attention = it.server.error?.problem?.needsAttention == true
        it.serverSyncOn to (it.serverSyncOn && attention)
    }.distinctUntilChanged()

    val players: Flow<PlayersUiState> = combine(
        group.players.observePlayers(includeArchived = true),
        snapshot,
        showArchived,
        me,
        serverFlags,
    ) { list, snap, archived, (meId, inGroup), (on, attention) ->
        Leaderboard.build(list, snap, archived, meId)
            .copy(inGroup = inGroup, serverNeedsAttention = attention, serverSyncOn = on)
    }

    val matchLog: Flow<MatchLogUiState> = combine(
        group.matches.observeMatches(),
        group.players.observePlayers(includeArchived = true),
        this.catalog,
        snapshot,
    ) { list, people, cat, snap ->
        MatchLogUiState(
            loading = false,
            groups = MatchRows.group(
                list,
                people.associateBy { it.id },
                cat,
                snap.ratings.matchChanges,
                WebDateLabels,
                WebDateLabels.today(),
            ),
            total = list.size,
        )
    }

    val meta: Flow<MetaUiState> = combine(
        snapshot,
        group.players.observePlayers(includeArchived = true),
        this.catalog,
    ) { snap, people, cat -> MetaBoard.build(snap, people.associateBy { it.id }, cat) }

    /** Deletes [match] (a tombstone, ADR-025) and keeps it for "Undo". */
    suspend fun delete(match: Match) {
        group.matches.delete(match.id)
        recentlyDeleted = match to Date.now()
    }

    /** Puts the deleted match back (a later stamp beats its tombstone everywhere). */
    suspend fun undoDelete() {
        val (match, at) = recentlyDeleted ?: return
        recentlyDeleted = null
        if (Date.now() - at <= UNDO_WINDOW_MS) group.matches.upsert(match)
    }

    companion object {
        const val UNDO_WINDOW_MS = 30_000.0
    }
}
