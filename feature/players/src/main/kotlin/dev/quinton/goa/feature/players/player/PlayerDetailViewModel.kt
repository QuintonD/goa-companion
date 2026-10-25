package dev.quinton.goa.feature.players.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.quinton.goa.core.data.CatalogRepository
import dev.quinton.goa.core.data.MatchRepository
import dev.quinton.goa.core.data.PlayerMergeRepository
import dev.quinton.goa.core.data.PlayerMergeResult
import dev.quinton.goa.core.data.PlayerRepository
import dev.quinton.goa.core.data.rating.RatingsSnapshot
import dev.quinton.goa.core.data.rating.RatingsSnapshotRepository
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.feature.players.common.PhoneDateLabels
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
        outcome,
    ) { people, log, snapshot, cat, merged ->
        build(playerId, people, log, snapshot, cat).copy(mergeOutcome = merged)
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

                is PlayerMergeResult.PlayedTogether -> PlayerDetail.playedTogether(result.matches, people, PhoneDateLabels)

                PlayerMergeResult.NotPossible -> null
            }
        }
    }

    fun mergeOutcomeShown() {
        outcome.value = null
    }

    companion object {
        fun build(
            playerId: String,
            players: List<Player>,
            matches: List<Match>,
            snapshot: RatingsSnapshot,
            catalog: Catalog,
        ): PlayerDetailUiState = PlayerDetail.build(playerId, players, matches, snapshot, catalog, PhoneDateLabels)
    }
}
