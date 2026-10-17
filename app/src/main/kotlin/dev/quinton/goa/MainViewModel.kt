package dev.quinton.goa

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.quinton.goa.core.data.SettingsRepository
import dev.quinton.goa.core.model.settings.ThemeMode
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface MainUiState {
    /** Settings not read yet: the splash screen stays up so the theme never flashes. */
    data object Loading : MainUiState

    data class Ready(val theme: ThemeMode) : MainUiState
}

@HiltViewModel
class MainViewModel @Inject constructor(settings: SettingsRepository) : ViewModel() {
    val uiState: StateFlow<MainUiState> = settings.settings
        .map { MainUiState.Ready(it.theme) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, MainUiState.Loading)
}
