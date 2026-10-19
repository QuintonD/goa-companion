package dev.quinton.goa.feature.trivia.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.quinton.goa.core.data.CatalogRepository
import dev.quinton.goa.core.data.SettingsRepository
import dev.quinton.goa.core.data.TriviaProgressRepository
import dev.quinton.goa.core.domain.trivia.TriviaConfig
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.navigation.TriviaQuizRoute
import dev.quinton.goa.core.presentation.trivia.TriviaCatalogFacts
import dev.quinton.goa.core.presentation.trivia.TriviaHomeAction
import dev.quinton.goa.core.presentation.trivia.TriviaHomeUiState
import dev.quinton.goa.core.presentation.trivia.TriviaText
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = TriviaHomeViewModel.Factory::class)
class TriviaHomeViewModel internal constructor(
    heroId: String?,
    catalogRepository: CatalogRepository,
    settingsRepository: SettingsRepository,
    private val progressRepository: TriviaProgressRepository,
    /** Builds the state off the main thread; a test dispatcher in tests. */
    computeDispatcher: CoroutineDispatcher,
) : ViewModel() {
    @AssistedInject constructor(
        @Assisted heroId: String?,
        catalogRepository: CatalogRepository,
        settingsRepository: SettingsRepository,
        progressRepository: TriviaProgressRepository,
    ) : this(heroId, catalogRepository, settingsRepository, progressRepository, Dispatchers.Default)

    @AssistedFactory
    interface Factory {
        fun create(heroId: String?): TriviaHomeViewModel
    }

    /** The setup being edited; null until the last one has been read. */
    private val setup = MutableStateFlow<TriviaConfig?>(null)

    init {
        viewModelScope.launch {
            val last = runCatching { progressRepository.lastConfig.first() }.getOrNull() ?: TriviaConfig()
            setup.value = if (heroId != null) last.copy(heroIds = listOf(heroId)) else last
        }
    }

    /** The facts, rebuilt only when the catalog itself changes (it is loaded once per process). */
    private val facts = catalogRepository.catalog.catch { emit(Catalog.EMPTY) }
        .distinctUntilChanged { a, b -> a === b }
        .map(::TriviaCatalogFacts)

    val uiState: StateFlow<TriviaHomeUiState> = combine(
        facts,
        settingsRepository.settings,
        progressRepository.progress,
        setup,
    ) { facts, settings, progress, config ->
        if (config == null) return@combine TriviaHomeUiState.Loading
        TriviaHomeUiState.of(facts, settings.ownedProducts, progress, config)
    }
        .flowOn(computeDispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), TriviaHomeUiState.Loading)

    fun onAction(action: TriviaHomeAction) {
        setup.update { current -> current?.let { TriviaHomeAction.reduce(it, action) } }
    }

    /** Saves the setup and returns the quiz to open, or null when it would ask nothing. */
    fun start(): TriviaQuizRoute? {
        val ready = uiState.value as? TriviaHomeUiState.Ready ?: return null
        val config = ready.startConfig() ?: return null
        save(config)
        return TriviaQuizRoute(TriviaText.encode(config))
    }

    /** A quiz on [heroId] alone with the current Numbers, Cards and answer settings. */
    fun startHero(heroId: String): TriviaQuizRoute? {
        val ready = uiState.value as? TriviaHomeUiState.Ready ?: return null
        val config = ready.heroConfig(heroId) ?: return null
        save(ready.setup)
        return TriviaQuizRoute(TriviaText.encode(config))
    }

    /** Forgets every learned and learning number. */
    fun resetProgress() {
        viewModelScope.launch { progressRepository.reset(null) }
    }

    private fun save(config: TriviaConfig) {
        viewModelScope.launch { progressRepository.saveConfig(config) }
    }
}

private const val STOP_TIMEOUT_MS = 5_000L
