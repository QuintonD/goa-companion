package dev.quinton.goa.feature.draft.methods

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.quinton.goa.core.domain.draft.StepSpec
import dev.quinton.goa.feature.draft.data.DraftEnvironment
import dev.quinton.goa.feature.draft.data.DraftRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface MethodEditEvent {
    data object Saved : MethodEditEvent
}

@HiltViewModel(assistedFactory = DraftMethodEditViewModel.Factory::class)
class DraftMethodEditViewModel @AssistedInject constructor(
    @Assisted private val methodId: String?,
    private val drafts: DraftRepository,
    private val env: DraftEnvironment,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(methodId: String?): DraftMethodEditViewModel
    }

    private val state = MutableStateFlow(MethodEditUiState())
    val uiState: StateFlow<MethodEditUiState> = state.asStateFlow()
    private val events = Channel<MethodEditEvent>(Channel.BUFFERED)
    val editEvents: Flow<MethodEditEvent> = events.receiveAsFlow()

    init {
        viewModelScope.launch {
            val source = drafts.methods.first().find(methodId)
            state.value = MethodEditUiState.open(source, MethodEdits.customId(env.newId()))
        }
    }

    fun setName(name: String) = state.update { it.withName(name) }

    fun setPoolKind(kind: PoolKind) = state.update { it.withPoolKind(kind) }

    fun setPoolValue(value: Int) = state.update { it.withPoolValue(value) }

    fun addStep(section: StepSection, step: StepSpec) = state.update { it.addStep(section, step) }

    fun removeStep(section: StepSection, index: Int) = state.update { it.removeStep(section, index) }

    fun toggleTeam(section: StepSection, index: Int) = state.update { it.toggleTeam(section, index) }

    fun toggleAction(section: StepSection, index: Int) = state.update { it.toggleAction(section, index) }

    fun toggleHidden(section: StepSection, index: Int) = state.update { it.toggleHidden(section, index) }

    fun moveStep(section: StepSection, index: Int, delta: Int) = state.update { it.moveStep(section, index, delta) }

    fun setStepSeconds(seconds: Int?) = state.update { it.copy(stepSeconds = seconds) }

    fun setLayout(layout: PreviewLayout) = state.update { it.copy(layout = layout) }

    fun save() {
        val current = state.value
        if (current.error != null || current.saving) return
        state.update { it.copy(saving = true) }
        viewModelScope.launch {
            drafts.saveCustomMethod(current.toMethod())
            events.send(MethodEditEvent.Saved)
        }
    }
}
