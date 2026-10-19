package dev.quinton.goa.feature.players.players

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.quinton.goa.core.data.GroupRepository
import dev.quinton.goa.core.data.MatchRepository
import dev.quinton.goa.core.data.PlayerMatchUpRepository
import dev.quinton.goa.core.data.PlayerMergeRepository
import dev.quinton.goa.core.data.PlayerRepository
import dev.quinton.goa.core.data.rating.RatingsSnapshotRepository
import dev.quinton.goa.core.presentation.players.Leaderboard
import dev.quinton.goa.core.presentation.players.PlayersUiState
import dev.quinton.goa.feature.players.samepeople.SamePeopleViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlayersViewModel @Inject constructor(
    players: PlayerRepository,
    ratings: RatingsSnapshotRepository,
    groups: GroupRepository,
    matches: MatchRepository,
    merges: PlayerMergeRepository,
    matchUp: PlayerMatchUpRepository,
) : ViewModel() {
    private val showArchived = MutableStateFlow(false)

    /** "You", resolved through player merges (a merged-away id reads as the player it joined). */
    private val me: Flow<Pair<String?, Boolean>> = groups.state
        .map { it.mePlayerId to it.inGroup }
        .distinctUntilChanged()
        .flatMapLatest { (id, inGroup) ->
            if (id == null) flowOf(null to inGroup) else players.observePlayer(id).map { it?.id to inGroup }
        }

    /** Worked out from the current data, so any sync (Nearby, file, hub) updates it. */
    private val samePeople: Flow<Int> =
        SamePeopleViewModel.suggestions(players, matches, merges, matchUp).map { it.size }.distinctUntilChanged()

    /** The group has a hub, and whether its last sync needs a person. */
    private val hub: Flow<Pair<Boolean, Boolean>> = groups.state
        .map { it.hubSyncOn to (it.hubSyncOn && it.hubError?.problem?.transient == false) }
        .distinctUntilChanged()

    /** "Same people?" count and the hub flags, together (combine takes five typed flows). */
    private val hints: Flow<Pair<Int, Pair<Boolean, Boolean>>> = combine(samePeople, hub) { same, h -> same to h }

    val uiState: StateFlow<PlayersUiState> = combine(
        players.observePlayers(includeArchived = true),
        ratings.observeSnapshot(),
        showArchived,
        me,
        hints,
    ) { list, snapshot, archivedVisible, (meId, inGroup), (same, hubFlags) ->
        val (hasHub, hubAttention) = hubFlags
        Leaderboard.build(list, snapshot, archivedVisible, meId)
            .copy(inGroup = inGroup, samePeople = same, hubNeedsAttention = hubAttention, hasHub = hasHub)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlayersUiState())

    fun toggleArchived() = showArchived.update { !it }
}
