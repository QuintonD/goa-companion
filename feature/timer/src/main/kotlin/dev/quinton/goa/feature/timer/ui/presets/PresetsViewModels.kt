package dev.quinton.goa.feature.timer.ui.presets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.quinton.goa.core.domain.timer.PhaseScope
import dev.quinton.goa.core.domain.timer.PhaseSpec
import dev.quinton.goa.core.domain.timer.TimerPreset
import dev.quinton.goa.feature.timer.data.TimerRepository
import dev.quinton.goa.feature.timer.ui.timer.TimerUiMapper
import java.util.UUID
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class PresetsViewModel @Inject constructor(private val repository: TimerRepository) : ViewModel() {
    val uiState: StateFlow<PresetsUiState> = combine(repository.presets, repository.selectedPresetId) {
            list,
            selected,
        ->
        PresetsUiState(
            loading = false,
            presets = list.map(TimerUiMapper::presetOption),
            selectedId = selected,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PresetsUiState())

    fun select(id: String) = launch { repository.select(id) }

    fun duplicate(id: String) = launch {
        val source = repository.preset(id) ?: return@launch
        repository.save(PresetEdits.duplicate(source, UUID.randomUUID().toString()))
    }

    fun delete(id: String) = launch { repository.delete(id) }

    fun restoreDefaults() = launch { repository.restoreDefaults() }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

sealed interface PresetEditEvent {
    data object Saved : PresetEditEvent
}

@HiltViewModel(assistedFactory = PresetEditViewModel.Factory::class)
class PresetEditViewModel @AssistedInject constructor(
    @Assisted private val presetId: String?,
    private val repository: TimerRepository,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(presetId: String?): PresetEditViewModel
    }

    private val state = MutableStateFlow(PresetEditUiState())
    val uiState: StateFlow<PresetEditUiState> = state
    private val events = Channel<PresetEditEvent>(Channel.BUFFERED)
    val navigation: Flow<PresetEditEvent> = events.receiveAsFlow()

    init {
        viewModelScope.launch {
            val endSteps = repository.rules().endOfRound
            val existing = presetId?.let { repository.preset(it) }
            val template = repository.presets.first().firstOrNull { it.id == repository.selectedPresetId.first() }
            state.value = PresetEditUiState.open(existing, template, endSteps, UUID.randomUUID().toString())
        }
    }

    fun rename(name: String) = edit { it.copy(name = name) }

    fun setAutoAdvance(on: Boolean) = edit { it.copy(autoAdvance = on) }

    /** "Hurry up" shot clock length; null = no shot clock. */
    fun setShotClock(seconds: Int?) = edit { it.copy(shotClockSeconds = seconds) }

    fun setTimeOutsPerRound(count: Int) = edit { PresetEdits.setTimeOutsPerRound(it, count) }

    fun setTimeOutSeconds(seconds: Int?) = edit { PresetEdits.setTimeOutSeconds(it, seconds) }

    fun setVolumeKeys(on: Boolean) = edit { it.copy(volumeKeys = on) }

    fun setEnabled(phaseId: String, enabled: Boolean) = editPhase(phaseId) { it.copy(enabled = enabled) }

    fun renamePhase(phaseId: String, name: String) = editPhase(phaseId) { it.copy(name = name) }

    /** Sets the duration; null = untimed. Cues that no longer fit are dropped. */
    fun setSeconds(phaseId: String, seconds: Int?) = edit { PresetEdits.setSeconds(it, phaseId, seconds) }

    fun toggleCue(phaseId: String, cue: Int) = edit { PresetEdits.toggleCue(it, phaseId, cue) }

    fun setEndStep(phaseId: String, endStepId: String?) = editPhase(phaseId) { it.copy(endStepId = endStepId) }

    /** Moves a phase up (-1) or down (+1) among the phases of the same scope. */
    fun move(phaseId: String, direction: Int) = edit { PresetEdits.move(it, phaseId, direction) }

    fun addPhase(scope: PhaseScope) = edit { PresetEdits.addPhase(it, scope, UUID.randomUUID().toString()) }

    fun removePhase(phaseId: String) = edit { PresetEdits.removePhase(it, phaseId) }

    fun save() {
        val current = state.value
        val preset = current.preset ?: return
        if (current.issues.isNotEmpty()) {
            state.update { it.copy(showIssues = true) }
            return
        }
        viewModelScope.launch {
            repository.save(PresetEdits.forSave(preset))
            events.send(PresetEditEvent.Saved)
        }
    }

    private fun editPhase(phaseId: String, transform: (PhaseSpec) -> PhaseSpec) =
        edit { PresetEdits.editPhase(it, phaseId, transform) }

    private fun edit(transform: (TimerPreset) -> TimerPreset) {
        state.update { it.edit(transform) }
    }

    companion object {
        const val DEFAULT_NEW_SECONDS = PresetEdits.DEFAULT_NEW_SECONDS

        /** Cue thresholds offered in the editor (seconds remaining). */
        val CUE_OPTIONS = PresetEdits.CUE_OPTIONS
    }
}
