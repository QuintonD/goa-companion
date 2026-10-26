package dev.quinton.goa.web.draft

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.quinton.goa.core.domain.draft.StepSpec
import dev.quinton.goa.core.domain.draft.TeamSlot
import dev.quinton.goa.core.model.draft.DraftAction
import dev.quinton.goa.feature.draft.methods.MethodEditUiState
import dev.quinton.goa.feature.draft.methods.MethodEdits
import dev.quinton.goa.feature.draft.methods.MethodRow
import dev.quinton.goa.feature.draft.methods.MethodsUiState
import dev.quinton.goa.feature.draft.methods.PoolKind
import dev.quinton.goa.feature.draft.methods.PreviewLayout
import dev.quinton.goa.feature.draft.methods.StepSection
import dev.quinton.goa.feature.draft.methods.poolText
import dev.quinton.goa.feature.draft.methods.previewOf
import dev.quinton.goa.feature.draft.ui.actionName
import dev.quinton.goa.web.AppServices
import dev.quinton.goa.web.LocalServices
import dev.quinton.goa.web.nav.Route
import dev.quinton.goa.web.rememberRetained
import dev.quinton.goa.web.table.Badge
import dev.quinton.goa.web.table.SectionCard
import dev.quinton.goa.web.table.SequenceStrip
import dev.quinton.goa.web.table.Stepper
import dev.quinton.goa.web.table.SwitchRow
import dev.quinton.goa.web.table.TextField
import dev.quinton.goa.web.ui.AlertDialog
import dev.quinton.goa.web.ui.ButtonKind
import dev.quinton.goa.web.ui.Chip
import dev.quinton.goa.web.ui.GoaButton
import dev.quinton.goa.web.ui.Icon
import dev.quinton.goa.web.ui.IconSize
import dev.quinton.goa.web.ui.LoadingState
import dev.quinton.goa.web.ui.Screen
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.attributes.ButtonType
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.attributes.type
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.Li
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.Ul

// ---------------------------------------------------------------- Method list

/** "Draft methods": the built-in methods (with their sources) and the group's own, previewed for a seat layout. */
@OptIn(ExperimentalUuidApi::class)
@Composable
fun DraftMethodsScreen() {
    val services = LocalServices.current
    val drafts = services.data.table.drafts
    val scope = rememberCoroutineScope()
    val layout = remember { MutableStateFlow(PreviewLayout.OPTIONS.first()) }
    val state by remember {
        combine(drafts.methods, layout) { library, l ->
            fun row(m: dev.quinton.goa.core.domain.draft.DraftMethod) = MethodRow(
                m,
                previewOf(m, l),
                m.id == library.defaultId,
            )
            MethodsUiState(
                loading = false,
                layout = l,
                builtIn = library.builtIn.map(::row),
                custom = library.custom.map(::row),
            )
        }.stateIn(scope, SharingStarted.Eagerly, MethodsUiState())
    }.collectAsState()
    LaunchedEffect(Unit) { drafts.load() }
    var deleting by remember { mutableStateOf<String?>(null) }
    val duplicate: (String) -> Unit = { methodId ->
        scope.launch {
            val source = drafts.library().find(methodId) ?: return@launch
            val copy = MethodEdits.duplicate(source, MethodEdits.customId(Uuid.random().toString()))
            drafts.saveCustomMethod(copy)
            services.router.navigate(Route.DraftMethodEdit(copy.id))
        }
    }
    Screen("Draft methods") {
        if (state.loading) {
            LoadingState()
            return@Screen
        }
        Div({ classes("goa-table-stack") }) {
            P({ classes("t-label-large", "c-variant", "goa-table-tight") }) { Text("Preview for") }
            Div({ classes("goa-chip-row", "wrap") }) {
                PreviewLayout.OPTIONS.forEach { option ->
                    Chip("${option.label} heroes", state.layout == option, onClick = { layout.value = option })
                }
            }
            H2({ classes("t-title-large", "c-primary") }) { Text("Built in") }
            state.builtIn.forEach { row ->
                key(row.method.id) { MethodCard(row, onDelete = null, onEdit = null, onDuplicate = duplicate) }
            }
            H2({ classes("t-title-large", "c-primary") }) { Text("Your methods") }
            if (state.custom.isEmpty()) {
                P({ classes("t-body-medium", "c-variant") }) {
                    Text(
                        "Duplicate a method to tweak it, or create one from scratch. Pool size, bans and the pick " +
                            "pattern are all editable.",
                    )
                }
            }
            state.custom.forEach { row ->
                key(row.method.id) {
                    MethodCard(
                        row,
                        onDelete = { deleting = row.method.id },
                        onEdit = { services.router.navigate(Route.DraftMethodEdit(row.method.id)) },
                        onDuplicate = duplicate,
                    )
                }
            }
            GoaButton("New method", { services.router.navigate(Route.DraftMethodEdit()) }, icon = "add", block = true)
        }
    }
    deleting?.let { id ->
        val name = state.custom.firstOrNull { it.method.id == id }?.method?.name ?: "this method"
        AlertDialog(
            "Delete $name?",
            onDismiss = { deleting = null },
            buttons = {
                GoaButton("Cancel", { deleting = null }, ButtonKind.TEXT)
                GoaButton("Delete", {
                    deleting = null
                    scope.launch { drafts.deleteCustomMethod(id) }
                }, ButtonKind.DANGER)
            },
        ) { Text("Past drafts keep their record; the method just won't be offered again.") }
    }
}

@Composable
private fun MethodCard(row: MethodRow, onDelete: (() -> Unit)?, onEdit: (() -> Unit)?, onDuplicate: (String) -> Unit) {
    val method = row.method
    var notes by remember(method.id) { mutableStateOf(false) }
    Div({
        classes("goa-card", "goa-draft-method")
        if (row.isDefault) classes("default")
    }) {
        Div({ classes("goa-table-heading-row") }) {
            H2({ classes("t-title-large", "goa-table-tight") }) { Text(method.name) }
            Badge(row.badge)
        }
        P({ classes("t-body-medium", "c-variant", "goa-table-tight") }) { Text(method.summary) }
        P({ classes("t-label-large", "goa-table-tight") }) { Text(row.poolLine) }
        val error = row.preview.error
        when {
            error != null -> P({ classes("t-body-small", "c-variant") }) { Text(error) }
            row.preview.tokens.isNotEmpty() -> SequenceStrip(row.preview.tokens)
        }
        if (notes && method.notes.isNotEmpty()) {
            Ul({ classes("t-body-small", "c-variant", "goa-draft-notes") }) { method.notes.forEach { Li { Text(it) } } }
        }
        Div({ classes("goa-button-row") }) {
            if (method.notes.isNotEmpty()) {
                GoaButton(if (notes) "Hide sources" else "Sources", {
                    notes = !notes
                }, ButtonKind.TEXT)
            }
            Span({ classes("goa-table-grow") })
            if (onDelete != null && onEdit != null) {
                GoaButton("Delete", onDelete, ButtonKind.TEXT, icon = "delete")
                GoaButton("Edit", onEdit, ButtonKind.TEXT, icon = "edit")
            } else {
                GoaButton("Duplicate", { onDuplicate(method.id) }, ButtonKind.TEXT, icon = "copy")
            }
        }
    }
}

// ---------------------------------------------------------------- Method editor

/** The method editor's state (the phone's `DraftMethodEditViewModel`), kept while its history entry exists. */
@OptIn(ExperimentalUuidApi::class)
private class MethodEditModel(services: AppServices, methodId: String?, scope: CoroutineScope) {
    private val drafts = services.data.table.drafts
    val state = MutableStateFlow(MethodEditUiState())

    init {
        scope.launch {
            val source = drafts.library().find(methodId)
            state.value = MethodEditUiState.open(source, MethodEdits.customId(Uuid.random().toString()))
        }
    }

    fun update(transform: (MethodEditUiState) -> MethodEditUiState) = state.update(transform)

    /** Saves and returns true (false while there is an error or a save in flight). */
    suspend fun save(): Boolean {
        val current = state.value
        if (current.error != null || current.saving) return false
        state.update { it.copy(saving = true) }
        drafts.saveCustomMethod(current.toMethod())
        return true
    }
}

@Composable
fun DraftMethodEditScreen(methodId: String?) {
    val services = LocalServices.current
    val scope = rememberCoroutineScope()
    val model = rememberRetained("methodEdit") { MethodEditModel(services, methodId, services.data.scope) }
    val state by model.state.collectAsState()
    val save: () -> Unit = { scope.launch { if (model.save()) services.router.back() } }
    Screen(
        if (state.isNew) "New method" else "Edit method",
        actions = {
            Button({
                type(ButtonType.Button)
                classes("goa-button", "text", "goa-table-on-banner")
                if (state.error != null || state.loading) disabled()
                onClick { save() }
            }) { Text("Save") }
        },
    ) {
        if (state.loading) {
            LoadingState()
            return@Screen
        }
        Div({ classes("goa-table-stack") }) {
            TextField(
                "Name",
                state.name,
                onValue = { name -> model.update { it.withName(name) } },
                supporting = state.copyOf?.let { "Copy of $it — the original stays unchanged" },
                maxLength = MethodEdits.MAX_NAME,
            )
            PoolCard(state, model)
            if (state.fixedSequence == null) {
                StepsCard(StepSection.OPENING, "Opening", "Runs once, in order", state.opening, model)
                StepsCard(
                    StepSection.REPEAT,
                    "Then repeat",
                    "Cycles until every seat is filled; picks for a full team are skipped",
                    state.repeat,
                    model,
                )
            } else {
                SectionCard("Sequence", "Set by the original method for each player count") {
                    P({ classes("t-body-medium", "c-variant") }) {
                        Text(
                            "This order depends on the number of heroes, so it can't be edited step by step. " +
                                "You can still change the name, pool and timer.",
                        )
                    }
                }
            }
            TimerCard(state, model)
            PreviewCard(state, model)
            state.error?.let {
                P({
                    classes("t-body-medium", "c-error")
                    attr("role", "alert")
                }) { Text(it) }
            }
            GoaButton("Save method", save, enabled = state.error == null && !state.saving, block = true)
        }
    }
}

@Composable
private fun PoolCard(state: MethodEditUiState, model: MethodEditModel) {
    SectionCard("Hero pool", poolText(state.pool)) {
        Div({ classes("goa-chip-row", "wrap") }) {
            PoolKind.entries.forEach { kind ->
                Chip(kind.label, state.poolKind == kind, onClick = { model.update { it.withPoolKind(kind) } })
            }
        }
        if (state.poolKind != PoolKind.ALL) {
            Div({ classes("goa-table-inline", "goa-table-spread") }) {
                Span({ classes("t-body-large") }) { Text(state.poolValueLabel) }
                Stepper(
                    state.poolValue,
                    (if (state.poolKind == PoolKind.SEATS_PLUS) 0 else 1)..MethodEdits.MAX_POOL,
                    { value -> model.update { it.withPoolValue(value) } },
                    "heroes",
                )
            }
        }
    }
}

@Composable
private fun StepsCard(
    section: StepSection,
    title: String,
    subtitle: String,
    steps: List<StepSpec>,
    model: MethodEditModel,
) {
    SectionCard(title, subtitle) {
        if (steps.isEmpty()) P({ classes("t-body-medium", "c-variant") }) { Text("No steps") }
        steps.forEachIndexed { i, step -> StepRow(section, i, step, steps.size, model) }
        Div({ classes("goa-chip-row", "wrap") }) {
            MethodEdits.ADDABLE.forEach { spec ->
                Button({
                    type(ButtonType.Button)
                    classes("goa-chip")
                    onClick { model.update { it.addStep(section, spec) } }
                }) {
                    Icon("add", IconSize.SMALL)
                    Text("${actionName(spec.action)} ${spec.team.name}")
                }
            }
        }
    }
}

@Composable
private fun StepRow(section: StepSection, index: Int, step: StepSpec, count: Int, model: MethodEditModel) {
    Div({ classes("goa-draft-step-row") }) {
        Span({ classes("t-label-large", "goa-draft-step-number") }) { Text("${index + 1}.") }
        Button({
            type(ButtonType.Button)
            classes("goa-chip")
            attr("aria-pressed", (step.action == DraftAction.BAN).toString())
            attr("aria-label", "${actionName(step.action)}, tap to switch")
            onClick { model.update { it.toggleAction(section, index) } }
        }) { Text(actionName(step.action)) }
        Button({
            type(ButtonType.Button)
            classes("goa-chip")
            attr("aria-pressed", (step.team == TeamSlot.A).toString())
            attr("aria-label", "Team ${step.team.name}, tap to switch")
            onClick { model.update { it.toggleTeam(section, index) } }
        }) { Text("Team ${step.team.name}") }
        Span({ classes("goa-table-grow") })
        Button({
            type(ButtonType.Button)
            classes("goa-icon-button")
            if (step.hidden) classes("goa-table-active")
            attr("aria-label", if (step.hidden) "Hidden step, tap to open" else "Open step, tap to hide")
            onClick { model.update { it.toggleHidden(section, index) } }
        }) { Icon(if (step.hidden) "lock" else "lock_open") }
        StepIconButton("arrow_up", "Move up", index > 0) { model.update { it.moveStep(section, index, -1) } }
        StepIconButton("arrow_down", "Move down", index < count - 1) { model.update { it.moveStep(section, index, 1) } }
        StepIconButton("close", "Remove step ${index + 1}", true) { model.update { it.removeStep(section, index) } }
    }
}

@Composable
private fun StepIconButton(icon: String, label: String, enabled: Boolean, onClick: () -> Unit) {
    Button({
        type(ButtonType.Button)
        classes("goa-icon-button")
        attr("aria-label", label)
        title(label)
        if (!enabled) disabled()
        onClick { onClick() }
    }) { Icon(icon) }
}

@Composable
private fun TimerCard(state: MethodEditUiState, model: MethodEditModel) {
    SectionCard("Step timer", state.stepSeconds?.let { "$it seconds per step by default" } ?: "Off by default") {
        SwitchRow("Step timer", null, state.stepSeconds != null) { on ->
            model.update { it.copy(stepSeconds = if (on) DEFAULT_STEP_SECONDS else null) }
        }
        if (state.stepSeconds != null) {
            Div({ classes("goa-chip-row", "wrap") }) {
                MethodEdits.STEP_SECONDS.forEach { s ->
                    Chip("${s}s", state.stepSeconds == s, onClick = { model.update { it.copy(stepSeconds = s) } })
                }
            }
        }
    }
}

@Composable
private fun PreviewCard(state: MethodEditUiState, model: MethodEditModel) {
    val preview = state.preview
    SectionCard("Preview", "The exact order for a seat layout") {
        Div({ classes("goa-chip-row", "wrap") }) {
            PreviewLayout.OPTIONS.forEach { option ->
                Chip(option.label, state.layout == option, onClick = { model.update { it.copy(layout = option) } })
            }
        }
        val error = preview.error
        if (error != null) {
            P({ classes("t-body-medium", "c-error") }) { Text(error) }
        } else {
            SequenceStrip(preview.tokens)
            if (preview.heroesNeeded > 0) {
                P({
                    classes("t-body-small", "c-variant")
                }) { Text("Needs at least ${preview.heroesNeeded} heroes in the pool") }
            }
        }
    }
}

private const val DEFAULT_STEP_SECONDS = 30
