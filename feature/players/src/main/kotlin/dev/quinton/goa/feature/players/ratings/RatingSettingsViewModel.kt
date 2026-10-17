package dev.quinton.goa.feature.players.ratings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.quinton.goa.core.data.rating.EloConfigRepository
import dev.quinton.goa.core.data.rating.RatingsSnapshotRepository
import dev.quinton.goa.core.domain.rating.EloConfig
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RatingSettingsUiState(
    val loading: Boolean = true,
    val config: EloConfig = EloConfig.DEFAULT,
    val ratedMatches: Int = 0,
    val skippedMatches: Int = 0,
    /** True while the shown ratings were computed with an older config (recompute in flight). */
    val recomputing: Boolean = false,
) {
    val isDefault: Boolean get() = config == EloConfig.DEFAULT
}

@HiltViewModel
class RatingSettingsViewModel @Inject constructor(
    private val configRepository: EloConfigRepository,
    ratings: RatingsSnapshotRepository,
) : ViewModel() {
    val uiState: StateFlow<RatingSettingsUiState> = combine(configRepository.config, ratings.observeSnapshot()) {
            config,
            snapshot,
        ->
        RatingSettingsUiState(
            loading = false,
            config = config,
            ratedMatches = snapshot.ratings.replayOrder.size,
            skippedMatches = snapshot.ratings.skipped.size,
            recomputing = snapshot.config != config,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RatingSettingsUiState())

    fun update(transform: (EloConfig) -> EloConfig) {
        viewModelScope.launch { configRepository.update(transform) }
    }

    fun reset() {
        viewModelScope.launch { configRepository.reset() }
    }
}
