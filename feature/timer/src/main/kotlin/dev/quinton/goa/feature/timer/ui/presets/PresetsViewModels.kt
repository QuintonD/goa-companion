package dev.quinton.goa.feature.timer.ui.presets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.quinton.goa.core.domain.setup.EndOfRoundStep
import dev.quinton.goa.core.domain.timer.PhaseKind
import dev.quinton.goa.core.domain.timer.PhaseScope
import dev.quinton.goa.core.domain.timer.PhaseSpec
import dev.quinton.goa.core.domain.timer.PresetIssue
import dev.quinton.goa.core.domain.timer.TimerPreset
import dev.quinton.goa.core.domain.timer.TimerPresetValidator
import dev.quinton.goa.feature.timer.TimerText
import dev.quinton.goa.feature.timer.data.TimerRepository
import dev.quinton.goa.feature.timer.ui.timer.PresetOption
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

data class PresetsUiState(
    val loading: Boolean = true,
    val presets: List<PresetOption> = emptyList(),
    val selectedId: String? = null,
) {
    val canDelete: Boolean get() = presets.size > 1
}

@HiltViewModel
class PresetsViewModel @Inject constructor(private val repository: TimerRepository) : ViewModel() {
    val uiState: StateFlow<PresetsUiState> = combine(repository.presets, repository.selectedPresetId) {
            list,
            selected,
        ->
        PresetsUiState(
            loading = false,
            presets = list.map {
                PresetOption(
                    it.id,
                    it.name,
                    TimerText.presetSummary(it),
                    it.phases.any { p ->
                        p.enabled &&
                            p.kind == PhaseKind.ACTION
                    },
                )
            },
            selectedId = selected,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PresetsUiState())

    fun select(id: String) = launch { repository.select(id) }

    fun duplicate(id: String) = launch {
        val source = repository.preset(id) ?: return@launch
        repository.save(source.copy(id = UUID.randomUUID().toString(), name = "${source.name} copy"))
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

data class PresetEditUiState(
    val loading: Boolean = true,
    val isNew: Boolean = false,
    val preset: TimerPreset? = null,
    val endSteps: List<EndOfRoundStep> = emptyList(),
    val issues: List<PresetIssue> = emptyList(),
    val showIssues: Boolean = false,
) {
    fun issuesFor(phaseId: String?): List<PresetIssue> = issues.filter { it.phaseId == phaseId }
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
            val preset = existing ?: newPreset()
            state.value = PresetEditUiState(
                loading = false,
                isNew = existing == null,
                preset = preset,
                endSteps = endSteps,
                issues = TimerPresetValidator.validate(preset, endSteps.map { it.id }.toSet()),
            )
        }
    }

    private suspend fun newPreset(): TimerPreset {
        val template = repository.presets.first().firstOrNull { it.id == repository.selectedPresetId.first() }
        return TimerPreset(
            id = UUID.randomUUID().toString(),
            name = "New preset",
            phases = template?.phases.orEmpty(),
            autoAdvance = false,
        )
    }

    fun rename(name: String) = edit { it.copy(name = name) }

    fun setAutoAdvance(on: Boolean) = edit { it.copy(autoAdvance = on) }

    fun setEnabled(phaseId: String, enabled: Boolean) = editPhase(phaseId) { it.copy(enabled = enabled) }

    fun renamePhase(phaseId: String, name: String) = editPhase(phaseId) { it.copy(name = name) }

    /** Sets the duration; null = untimed. Cues that no longer fit are dropped. */
    fun setSeconds(phaseId: String, seconds: Int?) = editPhase(phaseId) { phase ->
        phase.copy(
            seconds = seconds,
            cues = if (seconds == null) emptyList() else phase.cues.filter { it < seconds }.ifEmpty { listOf(0) },
        )
    }

    fun toggleCue(phaseId: String, cue: Int) = editPhase(phaseId) { phase ->
        val cues = if (cue in phase.cues) phase.cues - cue else (phase.cues + cue).sortedDescending()
        phase.copy(cues = cues)
    }

    fun setEndStep(phaseId: String, endStepId: String?) = editPhase(phaseId) { it.copy(endStepId = endStepId) }

    /** Moves a phase up (-1) or down (+1) among the phases of the same scope. */
    fun move(phaseId: String, direction: Int) = edit { preset ->
        val phases = preset.phases.toMutableList()
        val index = phases.indexOfFirst { it.id == phaseId }
        if (index < 0) return@edit preset
        val scope = phases[index].scope
        val sameScope = phases.indices.filter { phases[it].scope == scope }
        val pos = sameScope.indexOf(index)
        val target = sameScope.getOrNull(pos + direction) ?: return@edit preset
        phases[index] = phases[target].also { phases[target] = phases[index] }
        preset.copy(phases = phases)
    }

    fun addPhase(scope: PhaseScope) = edit { preset ->
        val phase = PhaseSpec(
            id = UUID.randomUUID().toString(),
            name = when (scope) {
                PhaseScope.ROUND_START -> "Round start timer"
                PhaseScope.TURN -> "Turn timer"
                PhaseScope.ROUND_END -> "End of round timer"
            },
            kind = PhaseKind.CUSTOM,
            scope = scope,
            seconds = DEFAULT_NEW_SECONDS,
            cues = listOf(10, 0),
        )
        preset.copy(phases = preset.phases + phase)
    }

    fun removePhase(phaseId: String) = edit { preset ->
        preset.copy(phases = preset.phases.filterNot { it.id == phaseId && it.kind == PhaseKind.CUSTOM })
    }

    fun save() {
        val current = state.value
        val preset = current.preset ?: return
        if (current.issues.isNotEmpty()) {
            state.update { it.copy(showIssues = true) }
            return
        }
        viewModelScope.launch {
            repository.save(preset.copy(name = preset.name.trim()))
            events.send(PresetEditEvent.Saved)
        }
    }

    private fun editPhase(phaseId: String, transform: (PhaseSpec) -> PhaseSpec) = edit { preset ->
        preset.copy(phases = preset.phases.map { if (it.id == phaseId) transform(it) else it })
    }

    private fun edit(transform: (TimerPreset) -> TimerPreset) {
        state.update { s ->
            val preset = s.preset ?: return@update s
            val updated = transform(preset)
            s.copy(preset = updated, issues = TimerPresetValidator.validate(updated, s.endSteps.map { it.id }.toSet()))
        }
    }

    companion object {
        const val DEFAULT_NEW_SECONDS = 60

        /** Cue thresholds offered in the editor (seconds remaining). */
        val CUE_OPTIONS = listOf(60, 30, 10, 5, 3, 0)
    }
}
