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
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class ProductOption(val id: String, val name: String, val released: Int?, val heroCount: Int, val owned: Boolean)

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
                is SettingsAction.SetOwnsEverything -> setOwnsEverything(action.ownsEverything)
                is SettingsAction.ToggleProduct -> toggleProduct(action.productId)
            }
        }
    }

    private suspend fun allProductIds(): Set<String> = catalogRepository.catalog.first().products.map { it.id }.toSet()

    private suspend fun setOwnsEverything(everything: Boolean) {
        val all = allProductIds()
        settingsRepository.update { s ->
            when {
                everything -> s.copy(ownedProducts = emptySet())

                // Switching "everything" off keeps every product ticked; the group then unticks
                // what it doesn't own (the pool is never empty).
                else -> s.copy(ownedProducts = all.ifEmpty { s.ownedProducts })
            }
        }
    }

    private suspend fun toggleProduct(id: String) {
        val all = allProductIds()
        settingsRepository.update { s ->
            val current = s.ownedProducts.ifEmpty { all }
            val next = if (id in current) current - id else current + id
            when {
                next.isEmpty() -> s

                // must own at least one product
                next.containsAll(all) -> s.copy(ownedProducts = emptySet())

                else -> s.copy(ownedProducts = next)
            }
        }
    }

    private fun toUiState(s: AppSettings, c: Catalog): SettingsUiState.Ready {
        val counts = c.heroes.groupingBy { it.product }.eachCount()
        val ownsEverything = s.ownedProducts.isEmpty()
        return SettingsUiState.Ready(
            theme = s.theme,
            ownsEverything = ownsEverything,
            products = c.products.map { p ->
                ProductOption(
                    id = p.id,
                    name = p.name,
                    released = p.released,
                    heroCount = counts[p.id] ?: 0,
                    owned = ownsEverything || p.id in s.ownedProducts,
                )
            },
            hapticsEnabled = s.hapticsEnabled,
            soundsEnabled = s.soundsEnabled,
        )
    }
}
