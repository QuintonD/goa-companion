package dev.quinton.goa.feature.players.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.quinton.goa.core.data.CatalogRepository
import dev.quinton.goa.core.data.GroupRepository
import dev.quinton.goa.core.data.MatchRepository
import dev.quinton.goa.core.data.PlayerMergeRepository
import dev.quinton.goa.core.data.PlayerMergeResult
import dev.quinton.goa.core.data.PlayerRepository
import dev.quinton.goa.core.data.rating.RatingsSnapshotRepository
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.presentation.players.MergeCopy
import dev.quinton.goa.core.presentation.players.MergeOutcome
import dev.quinton.goa.core.presentation.players.PlayerDetail
import dev.quinton.goa.core.presentation.players.PlayerDetailUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = PlayerDetailViewModel.Factory::class)
class PlayerDetailViewModel @AssistedInject constructor(
    @Assisted private val playerId: String,
    private val players: PlayerRepository,
    private val merges: PlayerMergeRepository,
    matches: MatchRepository,
    ratings: RatingsSnapshotRepository,
    catalog: CatalogRepository,
    groups: GroupRepository,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(playerId: String): PlayerDetailViewModel
    }

    private val outcome = MutableStateFlow<MergeOutcome?>(null)

    val uiState: StateFlow<PlayerDetailUiState> = combine(
        players.observePlayers(includeArchived = true),
        matches.observeMatches(),
        ratings.observeSnapshot(),
        catalog.catalog.onStart { emit(Catalog.EMPTY) },
        combine(outcome, groups.state) { merged, group -> merged to group },
    ) { people, log, snapshot, cat, (merged, group) ->
        val state = PlayerDetail.build(playerId, people, log, snapshot, cat)
        val player = state.player
        state.copy(
            mergeOutcome = merged,
            canInvite = group.inGroup && player != null && !player.archived && group.mePlayerId != player.id,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlayerDetailUiState())

    fun setArchived(archived: Boolean) {
        viewModelScope.launch { players.archive(playerId, archived) }
    }

    /** "Merge into…": this player becomes [intoId] everywhere, unless they played together. */
    fun merge(intoId: String) {
        viewModelScope.launch {
            val people = players.observePlayers(includeArchived = true).first().associateBy { it.id }
            outcome.value = when (val result = merges.merge(playerId, intoId)) {
                PlayerMergeResult.Merged -> MergeOutcome.Merged(intoId, people[intoId]?.name.orEmpty())

                is PlayerMergeResult.PlayedTogether -> MergeOutcome.PlayedTogether(
                    MergeCopy.playedTogether(result.matches, people),
                )

                PlayerMergeResult.NotPossible -> null
            }
        }
    }

    fun mergeOutcomeShown() {
        outcome.value = null
    }
}
