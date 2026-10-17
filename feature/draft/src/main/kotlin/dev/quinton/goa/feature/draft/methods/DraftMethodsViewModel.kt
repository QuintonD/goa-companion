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
import dev.quinton.goa.feature.draft.data.DraftEnvironment
import dev.quinton.goa.feature.draft.data.DraftRepository
import dev.quinton.goa.feature.draft.ui.StepToken
import dev.quinton.goa.feature.draft.ui.tokens
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

/** Seat layout used for previews: heroes on Team A and Team B. */
data class PreviewLayout(val a: Int, val b: Int) {
    val label: String get() = "$a v $b"

    companion object {
        val OPTIONS =
            listOf(
                PreviewLayout(2, 2),
                PreviewLayout(3, 3),
                PreviewLayout(4, 4),
                PreviewLayout(5, 5),
                PreviewLayout(3, 2),
            )
    }
}

/** A method's sequence for one layout, or why it can't run. */
data class SequencePreview(val tokens: List<StepToken>, val error: String?, val heroesNeeded: Int)

fun previewOf(method: DraftMethod, layout: PreviewLayout): SequencePreview {
    val plan = DraftPlanner.plan(method, layout.a, layout.b)
    val steps = plan.getOrNull()
    val error = when (val e = plan.exceptionOrNull()) {
        null -> null
        is DraftError.NotSupported -> e.reason
        is DraftError.SequenceTooShort -> "Never fills ${layout.label} seats — add picks for both teams"
        else -> e.message
    }
    val needed = when (val pool = method.pool) {
        is PoolRule.SeatsPlus -> layout.a + layout.b + pool.extra
        is PoolRule.DealPerSeat -> (layout.a + layout.b) * pool.perSeat
        else -> steps?.size ?: 0
    }
    return SequencePreview(steps?.tokens().orEmpty(), error, if (method.sequence is SequenceRule.Lineup) 0 else needed)
}

data class MethodRow(val method: DraftMethod, val preview: SequencePreview, val isDefault: Boolean)

data class MethodsUiState(
    val loading: Boolean = true,
    val layout: PreviewLayout = PreviewLayout.OPTIONS.first(),
    val builtIn: List<MethodRow> = emptyList(),
    val custom: List<MethodRow> = emptyList(),
)

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
            val copy = source.copy(
                id = "custom-${env.newId()}",
                name = "${source.name} (copy)",
                kind = DraftMethodKind.CUSTOM,
                origin = MethodOrigin.CUSTOM,
                notes = listOf("Based on ${source.name}."),
            )
            drafts.saveCustomMethod(copy)
            events.send(MethodsEvent.Edit(copy.id))
        }
    }

    fun delete(methodId: String) {
        viewModelScope.launch { drafts.deleteCustomMethod(methodId) }
    }
}
