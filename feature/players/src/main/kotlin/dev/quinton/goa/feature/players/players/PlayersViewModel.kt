package dev.quinton.goa.feature.players.players

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.quinton.goa.core.data.EditClock
import dev.quinton.goa.core.data.GroupRepository
import dev.quinton.goa.core.data.MatchRepository
import dev.quinton.goa.core.data.PlayerMatchUpRepository
import dev.quinton.goa.core.data.PlayerMergeRepository
import dev.quinton.goa.core.data.PlayerRepository
import dev.quinton.goa.core.data.rating.RatingsSnapshotRepository
import dev.quinton.goa.core.data.sync.HubSyncControl
import dev.quinton.goa.core.data.sync.HubSyncOutcome
import dev.quinton.goa.core.data.sync.syncAndWait
import dev.quinton.goa.core.presentation.players.Leaderboard
import dev.quinton.goa.core.presentation.players.PlayersUiState
import dev.quinton.goa.feature.players.samepeople.SamePeopleViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlayersViewModel @Inject constructor(
    players: PlayerRepository,
    ratings: RatingsSnapshotRepository,
    private val groups: GroupRepository,
    matches: MatchRepository,
    merges: PlayerMergeRepository,
    matchUp: PlayerMatchUpRepository,
    private val hubSync: HubSyncControl = HubSyncControl.Off,
    private val clock: EditClock = EditClock.System,
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

    private val hubTap = MutableStateFlow(HubSyncTap())

    /** The sync icon's own hub sync: running, then its outcome for the snackbar. */
    val hubSyncTap: StateFlow<HubSyncTap> = hubTap.asStateFlow()

    /**
     * The sync icon with a hub (ADR-030 §5): syncs with the hub now, as the Group screen's "Sync
     * now" does, and says how it went. A second tap while one runs does nothing.
     */
    fun syncWithHub() {
        if (hubTap.value.syncing) return
        hubTap.value = HubSyncTap(syncing = true)
        viewModelScope.launch {
            val outcome = hubSync.syncAndWait(groups, clock)
            hubTap.value = HubSyncTap(
                message = HubSyncTap.message(outcome),
                details = outcome !is HubSyncOutcome.Synced,
            )
        }
    }

    fun hubSyncMessageShown() = hubTap.update { it.copy(message = null) }
}

/**
 * A sync with the hub tapped on the Players tab: [message] once it is over; [details] offers the
 * Group screen (what went wrong, Retry, the hub's address).
 */
data class HubSyncTap(val syncing: Boolean = false, val message: String? = null, val details: Boolean = false) {
    companion object {
        const val SYNCED = "Synced with the hub"
        const val STILL_SYNCING = "The hub hasn't answered yet. The phone keeps trying on its own."

        fun message(outcome: HubSyncOutcome): String = when (outcome) {
            HubSyncOutcome.Synced -> SYNCED
            is HubSyncOutcome.Failed -> outcome.error.message
            HubSyncOutcome.StillSyncing -> STILL_SYNCING
        }
    }
}
