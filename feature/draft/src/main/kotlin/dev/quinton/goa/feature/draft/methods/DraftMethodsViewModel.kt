package dev.quinton.goa.feature.draft.methods

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.quinton.goa.core.domain.draft.DraftMethod
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.draft.data.DraftEnvironment
import dev.quinton.goa.feature.draft.data.DraftRepository
import dev.quinton.goa.feature.draft.data.DraftSetupPrefills
import dev.quinton.goa.feature.draft.setup.SetupForm
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
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
    /** The draft setup behind this screen: choosing a method here is choosing it there. */
    private val prefills: DraftSetupPrefills = DraftSetupPrefills(),
) : ViewModel() {
    private val layout = MutableStateFlow(PreviewLayout.OPTIONS.first())
    private val events = Channel<MethodsEvent>(Channel.BUFFERED)
    val methodEvents: Flow<MethodsEvent> = events.receiveAsFlow()

    val uiState: StateFlow<MethodsUiState> =
        combine(drafts.methods, layout, drafts.setupForm.onStart { emit(null) }) { library, l, form ->
            // Steps in colours, as the board shows them: the coin's team goes as A, or Blue as the example.
            val teamA = form?.teamA ?: Team.BLUE
            fun row(m: DraftMethod) = MethodRow(m, previewOf(m, l, teamA), m.id == library.defaultId)
            val all = library.builtIn + library.custom
            MethodsUiState(
                loading = false,
                layout = l,
                builtIn = library.builtIn.map(::row),
                custom = library.custom.map(::row),
                selectedId = form?.methodId?.takeIf { id -> all.any { it.id == id } } ?: library.defaultId,
                previewTeamA = teamA,
                coinKnown = form?.teamA != null,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MethodsUiState())

    fun setLayout(value: PreviewLayout) {
        layout.value = value
    }

    /** The draft setup's method becomes [methodId] (saved, and handed to a setup screen that is open). */
    fun select(methodId: String) {
        viewModelScope.launch {
            val form = (drafts.setupForm.first() ?: SetupForm()).copy(methodId = methodId)
            drafts.saveSetupForm(form)
            prefills.push(form)
        }
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
