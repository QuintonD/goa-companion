package dev.quinton.goa.feature.catalog.viewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.quinton.goa.core.data.CatalogRepository
import dev.quinton.goa.core.navigation.CardViewerRoute
import dev.quinton.goa.core.presentation.catalog.CardViewerUiState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel(assistedFactory = CardViewerViewModel.Factory::class)
class CardViewerViewModel @AssistedInject constructor(
    @Assisted route: CardViewerRoute,
    catalogRepository: CatalogRepository,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(route: CardViewerRoute): CardViewerViewModel
    }

    val uiState: StateFlow<CardViewerUiState> = catalogRepository.catalog
        .map { catalog -> CardViewerUiState.of(catalog, route.heroId, route.cardId) }
        .catch { emit(CardViewerUiState.NotFound) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CardViewerUiState.Loading)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
