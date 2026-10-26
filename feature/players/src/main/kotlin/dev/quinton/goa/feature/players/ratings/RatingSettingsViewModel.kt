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

@HiltViewModel
class RatingSettingsViewModel @Inject constructor(
    private val configRepository: EloConfigRepository,
    ratings: RatingsSnapshotRepository,
) : ViewModel() {
    val uiState: StateFlow<RatingSettingsUiState> = combine(configRepository.config, ratings.observeSnapshot()) {
            config,
            snapshot,
        ->
        ratingSettingsState(config, snapshot)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RatingSettingsUiState())

    fun update(transform: (EloConfig) -> EloConfig) {
        viewModelScope.launch { configRepository.update(transform) }
    }

    fun reset() {
        viewModelScope.launch { configRepository.reset() }
    }
}
