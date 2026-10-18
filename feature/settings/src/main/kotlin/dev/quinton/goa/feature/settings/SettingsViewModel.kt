package dev.quinton.goa.feature.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.quinton.goa.core.data.CatalogRepository
import dev.quinton.goa.core.data.SettingsRepository
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.settings.AppSettings
import dev.quinton.goa.core.model.settings.ThemeMode
import dev.quinton.goa.core.model.settings.TimerLayout
import dev.quinton.goa.core.presentation.settings.OwnedProducts
import dev.quinton.goa.core.presentation.settings.ProductOption
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface SettingsUiState {
    data object Loading : SettingsUiState

    @Immutable
    data class Ready(
        val theme: ThemeMode,
        /** True when no subset is stored (empty selection = every product). */
        val ownsEverything: Boolean,
        val products: List<ProductOption>,
        val hapticsEnabled: Boolean,
        val soundsEnabled: Boolean,
        val timerLayout: TimerLayout = TimerLayout.STEPS,
    ) : SettingsUiState {
        val ownedCount: Int get() = products.count { it.owned }
    }
}

sealed interface SettingsAction {
    data class SetTheme(val theme: ThemeMode) : SettingsAction

    data class ToggleProduct(val productId: String) : SettingsAction

    data class SetOwnsEverything(val ownsEverything: Boolean) : SettingsAction

    data class SetHaptics(val enabled: Boolean) : SettingsAction

    data class SetSounds(val enabled: Boolean) : SettingsAction

    data class SetTimerLayout(val layout: TimerLayout) : SettingsAction
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val catalogRepository: CatalogRepository,
) : ViewModel() {
    val uiState: StateFlow<SettingsUiState> =
        combine(settingsRepository.settings, catalogRepository.catalog.onStart { emit(Catalog.EMPTY) }) { s, c ->
            toUiState(s, c)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState.Loading)

    fun onAction(action: SettingsAction) {
        viewModelScope.launch {
            when (action) {
                is SettingsAction.SetTheme -> settingsRepository.update { it.copy(theme = action.theme) }
                is SettingsAction.SetHaptics -> settingsRepository.update { it.copy(hapticsEnabled = action.enabled) }
                is SettingsAction.SetSounds -> settingsRepository.update { it.copy(soundsEnabled = action.enabled) }
                is SettingsAction.SetTimerLayout -> settingsRepository.update { it.copy(timerLayout = action.layout) }
                is SettingsAction.SetOwnsEverything -> setOwnsEverything(action.ownsEverything)
                is SettingsAction.ToggleProduct -> toggleProduct(action.productId)
            }
        }
    }

    private suspend fun allProductIds(): Set<String> = catalogRepository.catalog.first().products.map { it.id }.toSet()

    private suspend fun setOwnsEverything(everything: Boolean) {
        val all = allProductIds()
        settingsRepository.update { s -> OwnedProducts.setOwnsEverything(s, all, everything) }
    }

    private suspend fun toggleProduct(id: String) {
        val all = allProductIds()
        settingsRepository.update { s -> OwnedProducts.toggle(s, all, id) }
    }

    private fun toUiState(s: AppSettings, c: Catalog): SettingsUiState.Ready = SettingsUiState.Ready(
        theme = s.theme,
        ownsEverything = OwnedProducts.ownsEverything(s),
        products = OwnedProducts.options(s, c),
        hapticsEnabled = s.hapticsEnabled,
        soundsEnabled = s.soundsEnabled,
        timerLayout = s.timerLayout,
    )
}
