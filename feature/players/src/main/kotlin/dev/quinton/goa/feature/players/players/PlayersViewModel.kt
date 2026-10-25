package dev.quinton.goa.feature.players.players

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.quinton.goa.core.data.GroupRepository
import dev.quinton.goa.core.data.PlayerRepository
import dev.quinton.goa.core.data.SyncServerControl
import dev.quinton.goa.core.data.rating.RatingsSnapshot
import dev.quinton.goa.core.data.rating.RatingsSnapshotRepository
import dev.quinton.goa.core.model.player.Player
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

/** The sync icon's server state: switched on, and a problem a person must fix. */
private data class ServerFlags(val on: Boolean, val attention: Boolean)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlayersViewModel @Inject constructor(
    players: PlayerRepository,
    ratings: RatingsSnapshotRepository,
    groups: GroupRepository,
    private val serverSync: SyncServerControl = SyncServerControl.Off,
) : ViewModel() {
    private val showArchived = MutableStateFlow(false)

    /** "You", resolved through player merges (a merged-away id reads as the player it joined). */
    private val me: Flow<Pair<String?, Boolean>> = groups.state
        .map { it.mePlayerId to it.inGroup }
        .distinctUntilChanged()
        .flatMapLatest { (id, inGroup) ->
            if (id == null) flowOf(null to inGroup) else players.observePlayer(id).map { it?.id to inGroup }
        }

    val uiState: StateFlow<PlayersUiState> = combine(
        players.observePlayers(includeArchived = true),
        ratings.observeSnapshot(),
        showArchived,
        me,
        groups.state.map {
            val attention = it.server.error?.problem?.needsAttention == true
            ServerFlags(on = it.serverSyncOn, attention = it.serverSyncOn && attention)
        }.distinctUntilChanged(),
    ) { list, snapshot, archivedVisible, (meId, inGroup), server ->
        val board = leaderboard(list, snapshot, archivedVisible, meId)
        board.copy(inGroup = inGroup, serverNeedsAttention = server.attention, serverSyncOn = server.on)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlayersUiState())

    fun toggleArchived() = showArchived.update { !it }

    /** The sync icon with a server: sync through it now. */
    fun syncWithServer() = serverSync.syncNow()

    companion object {
        fun leaderboard(
            players: List<Player>,
            snapshot: RatingsSnapshot,
            showArchived: Boolean,
            meId: String? = null,
        ): PlayersUiState = Leaderboard.build(players, snapshot, showArchived, meId)
    }
}
