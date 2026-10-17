package dev.quinton.goa.feature.draft.methods

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.quinton.goa.core.domain.draft.DraftMethod
import dev.quinton.goa.core.domain.draft.DraftPlanner
import dev.quinton.goa.core.domain.draft.MethodOrigin
import dev.quinton.goa.core.domain.draft.PoolRule
import dev.quinton.goa.core.domain.draft.SequenceRule
import dev.quinton.goa.core.domain.draft.StepSpec
import dev.quinton.goa.core.domain.draft.TeamSlot
import dev.quinton.goa.core.domain.draft.ban
import dev.quinton.goa.core.domain.draft.pick
import dev.quinton.goa.core.model.draft.DraftAction
import dev.quinton.goa.core.model.draft.DraftMethodKind
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

enum class PoolKind(val label: String) {
    ALL("All"),
    RANDOM("Random N"),
    SEATS_PLUS("Heroes + N"),
    DEAL("Deal N each"),
}

enum class StepSection { OPENING, REPEAT }

data class MethodEditUiState(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    /** Editing a built-in creates a copy; built-ins are never changed in place. */
    val copyOf: String? = null,
    val id: String = "",
    val name: String = "",
    val poolKind: PoolKind = PoolKind.RANDOM,
    val poolValue: Int = 15,
    val opening: List<StepSpec> = emptyList(),
    val repeat: List<StepSpec> = emptyList(),
    /** Non-pattern sequences (per-count tables, lineups) keep their sequence; only pool/timer edit. */
    val fixedSequence: SequenceRule? = null,
    val stepSeconds: Int? = null,
    val layout: PreviewLayout = PreviewLayout.OPTIONS.first(),
    val saving: Boolean = false,
) {
    val sequence: SequenceRule get() = fixedSequence ?: SequenceRule.Pattern(opening, repeat)

    val pool: PoolRule
        get() = when (poolKind) {
            PoolKind.ALL -> PoolRule.All
            PoolKind.RANDOM -> PoolRule.Random(poolValue)
            PoolKind.SEATS_PLUS -> PoolRule.SeatsPlus(poolValue)
            PoolKind.DEAL -> PoolRule.DealPerSeat(poolValue)
        }

    fun toMethod(): DraftMethod = DraftMethod(
        id = id,
        name = name.trim(),
        kind = DraftMethodKind.CUSTOM,
        origin = MethodOrigin.CUSTOM,
        summary = describe(),
        pool = pool,
        sequence = sequence,
        stepSeconds = stepSeconds,
        notes = copyOf?.let { listOf("Based on $it.") }.orEmpty(),
    )

    val preview: SequencePreview get() = previewOf(toMethod(), layout)

    val error: String?
        get() = when {
            name.isBlank() -> "Give the method a name"

            fixedSequence == null && repeat.none { it.action == DraftAction.PICK } &&
                opening.none { it.action == DraftAction.PICK } ->
                "Add at least one pick"

            // Checked for fixed sequences too (e.g. a Pick and Ban copy with dealt hands).
            DraftPlanner.problem(toMethod()) != null -> DraftPlanner.problem(toMethod())?.message

            fixedSequence != null -> null

            else -> PreviewLayout.OPTIONS.firstNotNullOfOrNull {
                previewOf(toMethod(), it).error?.let { e -> "${it.label}: $e" }
            }
        }

    private fun describe(): String {
        val poolText = when (poolKind) {
            PoolKind.ALL -> "All eligible heroes"
            PoolKind.RANDOM -> "Pool of $poolValue"
            PoolKind.SEATS_PLUS -> "Heroes + $poolValue revealed"
            PoolKind.DEAL -> "Deal $poolValue to each seat"
        }
        val bans = (opening + repeat).count { it.action == DraftAction.BAN }
        return if (fixedSequence !=
            null
        ) {
            "$poolText."
        } else {
            "$poolText. ${bans.takeIf { it > 0 }?.let { "$it ban(s). " } ?: ""}Custom order."
        }
    }
}

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
            val library = drafts.methods.first()
            val source = library.find(methodId)
            state.value = when {
                source == null -> MethodEditUiState(
                    loading = false,
                    id = "custom-${env.newId()}",
                    name = "",
                    opening = listOf(ban(TeamSlot.A), ban(TeamSlot.B)),
                    repeat = listOf(pick(TeamSlot.A), pick(TeamSlot.B)),
                )

                source.isBuiltIn -> fromMethod(source).copy(
                    isNew = true,
                    copyOf = source.name,
                    id = "custom-${env.newId()}",
                    name = "${source.name} (copy)",
                )

                else -> fromMethod(source).copy(isNew = false)
            }
        }
    }

    private fun fromMethod(m: DraftMethod): MethodEditUiState {
        val (kind, value) = when (val p = m.pool) {
            PoolRule.All -> PoolKind.ALL to 15
            is PoolRule.Random -> PoolKind.RANDOM to p.size
            is PoolRule.SeatsPlus -> PoolKind.SEATS_PLUS to p.extra
            is PoolRule.DealPerSeat -> PoolKind.DEAL to p.perSeat
        }
        val pattern = m.sequence as? SequenceRule.Pattern
        return MethodEditUiState(
            loading = false,
            id = m.id,
            name = m.name,
            poolKind = kind,
            poolValue = value,
            opening = pattern?.opening.orEmpty(),
            repeat = pattern?.repeat.orEmpty(),
            fixedSequence = if (pattern == null) m.sequence else null,
            stepSeconds = m.stepSeconds,
        )
    }

    fun setName(name: String) = state.update { it.copy(name = name.take(MAX_NAME)) }

    fun setPoolKind(kind: PoolKind) = state.update {
        it.copy(
            poolKind = kind,
            poolValue = when (kind) {
                PoolKind.ALL -> it.poolValue
                PoolKind.RANDOM -> 15
                PoolKind.SEATS_PLUS -> 2
                PoolKind.DEAL -> 3
            },
        )
    }

    fun setPoolValue(value: Int) = state.update { it.copy(poolValue = value.coerceIn(minPool(it.poolKind), MAX_POOL)) }

    private fun minPool(kind: PoolKind) = if (kind == PoolKind.SEATS_PLUS) 0 else 1

    fun addStep(section: StepSection, step: StepSpec) = edit(section) { it + step }

    fun removeStep(section: StepSection, index: Int) = edit(section) { list ->
        list.filterIndexed { i, _ -> i != index }
    }

    fun toggleTeam(section: StepSection, index: Int) = updateStep(section, index) { it.copy(team = it.team.other) }

    fun toggleAction(section: StepSection, index: Int) = updateStep(section, index) {
        it.copy(action = if (it.action == DraftAction.BAN) DraftAction.PICK else DraftAction.BAN)
    }

    fun toggleHidden(section: StepSection, index: Int) = updateStep(section, index) { it.copy(hidden = !it.hidden) }

    fun moveStep(section: StepSection, index: Int, delta: Int) = edit(section) { list ->
        val target = index + delta
        if (target !in list.indices) {
            list
        } else {
            list.toMutableList().apply { add(target, removeAt(index)) }
        }
    }

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

    private fun updateStep(section: StepSection, index: Int, transform: (StepSpec) -> StepSpec) = edit(
        section,
    ) { list ->
        list.mapIndexed { i, s -> if (i == index) transform(s) else s }
    }

    private fun edit(section: StepSection, transform: (List<StepSpec>) -> List<StepSpec>) = state.update {
        if (it.fixedSequence != null) {
            it
        } else {
            when (section) {
                StepSection.OPENING -> it.copy(opening = transform(it.opening).take(MAX_STEPS))
                StepSection.REPEAT -> it.copy(repeat = transform(it.repeat).take(MAX_STEPS))
            }
        }
    }

    private companion object {
        const val MAX_NAME = 40
        const val MAX_POOL = 60
        const val MAX_STEPS = 30
    }
}
