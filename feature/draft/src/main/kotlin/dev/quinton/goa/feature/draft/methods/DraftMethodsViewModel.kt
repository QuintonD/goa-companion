package dev.quinton.goa.feature.draft.methods

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.quinton.goa.core.domain.draft.DraftError
import dev.quinton.goa.core.domain.draft.DraftMethod
import dev.quinton.goa.core.domain.draft.DraftPlanner
import dev.quinton.goa.core.domain.draft.MethodOrigin
import dev.quinton.goa.core.domain.draft.PoolRule
import dev.quinton.goa.core.domain.draft.SequenceRule
import dev.quinton.goa.core.model.draft.DraftMethodKind
import dev.quinton.goa.core.presentation.draft.DraftEnvironment
import dev.quinton.goa.core.presentation.draft.MethodsUiState
import dev.quinton.goa.core.presentation.draft.PreviewLayout
import dev.quinton.goa.core.presentation.draft.StepToken
import dev.quinton.goa.core.presentation.draft.tokens
import dev.quinton.goa.feature.draft.data.DraftRepository
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface MethodsEvent {
    data class Edit(val methodId: String) : MethodsEvent
}

@HiltViewModel
class DraftMethodsViewModel @Inject constructor(
    private val drafts: DraftRepository,
    private val env: DraftEnvironment,
) : ViewModel() {
    private val layout = MutableStateFlow(PreviewLayout.OPTIONS.first())
    private val events = Channel<MethodsEvent>(Channel.BUFFERED)
    val methodEvents: Flow<MethodsEvent> = events.receiveAsFlow()

    val uiState: StateFlow<MethodsUiState> = combine(drafts.methods, layout) { library, l ->
        MethodsUiState.of(library, l)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MethodsUiState())

    fun setLayout(value: PreviewLayout) {
        layout.value = value
    }

    /** Saves an editable copy of [methodId] as a custom method and opens it. */
    fun duplicate(methodId: String) {
        viewModelScope.launch {
            val library = drafts.methods.first()
            if (library.find(methodId) == null) return@launch
            val copy = library.duplicate(methodId, env.newId()) ?: return@launch
            drafts.saveCustomMethod(copy)
            events.send(MethodsEvent.Edit(copy.id))
        }
    }

    fun delete(methodId: String) {
        viewModelScope.launch { drafts.deleteCustomMethod(methodId) }
    }
}
