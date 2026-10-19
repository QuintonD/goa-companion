package dev.quinton.goa.feature.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.quinton.goa.core.data.CatalogRepository
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.rules.RulesReference
import dev.quinton.goa.core.presentation.rules.LadderSelection
import dev.quinton.goa.core.presentation.rules.LadderUiState
import dev.quinton.goa.core.presentation.rules.RulesTopicUiState
import dev.quinton.goa.core.presentation.rules.RulesUiState
import dev.quinton.goa.feature.rules.data.RulesReferenceSource
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Loads the bundled reference once; emits null if it can't be read (a build defect, see tests). */
private fun RulesReferenceSource.flow(): Flow<RulesReference?> = flow<RulesReference?> { emit(load()) }
    .catch { emit(null) }

// ---- Topic list -------------------------------------------------------------------------------

@HiltViewModel
class RulesViewModel @Inject constructor(source: RulesReferenceSource) : ViewModel() {
    private val query = MutableStateFlow("")

    val uiState: StateFlow<RulesUiState> = combine(source.flow(), query) { reference, q ->
        RulesUiState.of(reference, q)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), RulesUiState.Loading)

    fun setQuery(value: String) {
        query.value = value
    }
}

// ---- Topic detail -----------------------------------------------------------------------------

@HiltViewModel(assistedFactory = RulesTopicViewModel.Factory::class)
class RulesTopicViewModel @AssistedInject constructor(
    @Assisted topicId: String,
    source: RulesReferenceSource,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(topicId: String): RulesTopicViewModel
    }

    val uiState: StateFlow<RulesTopicUiState> = source.flow().map { reference ->
        RulesTopicUiState.of(reference, topicId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), RulesTopicUiState.Loading)
}

// ---- Level & defeat ladder --------------------------------------------------------------------

@HiltViewModel(assistedFactory = LevelLadderViewModel.Factory::class)
class LevelLadderViewModel @AssistedInject constructor(
    @Assisted heroIds: List<String>,
    source: RulesReferenceSource,
    catalogRepository: CatalogRepository,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(heroIds: List<String>): LevelLadderViewModel
    }

    private val selected = MutableStateFlow<Int?>(null)

    private val catalog: Flow<Catalog> = catalogRepository.catalog.catch { emit(Catalog.EMPTY) }

    val uiState: StateFlow<LadderUiState> = combine(source.flow(), catalog, selected) { reference, catalog, level ->
        LadderUiState.of(reference, catalog, heroIds, level)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), LadderUiState.Loading)

    /** Picks the defeated hero's level; picking it again clears the selection. */
    fun selectLevel(level: Int) {
        selected.value = LadderSelection.afterPicking(selected.value, level)
    }
}

private const val STOP_TIMEOUT_MS = 5_000L
