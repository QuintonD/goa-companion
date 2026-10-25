package dev.quinton.goa.feature.draft.methods

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.quinton.goa.core.domain.draft.DraftMethod
import dev.quinton.goa.feature.draft.data.DraftEnvironment
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
        fun row(m: DraftMethod) = MethodRow(m, previewOf(m, l), m.id == library.defaultId)
        MethodsUiState(
            loading = false,
            layout = l,
            builtIn = library.builtIn.map(::row),
            custom = library.custom.map(::row),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MethodsUiState())

    fun setLayout(value: PreviewLayout) {
        layout.value = value
    }

    /** Saves an editable copy of [methodId] as a custom method and opens it. */
    fun duplicate(methodId: String) {
        viewModelScope.launch {
            val source = drafts.methods.first().find(methodId) ?: return@launch
            val copy = MethodEdits.duplicate(source, MethodEdits.customId(env.newId()))
            drafts.saveCustomMethod(copy)
            events.send(MethodsEvent.Edit(copy.id))
        }
    }

    fun delete(methodId: String) {
        viewModelScope.launch { drafts.deleteCustomMethod(methodId) }
    }
}
