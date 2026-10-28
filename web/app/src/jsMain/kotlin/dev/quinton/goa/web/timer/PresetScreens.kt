package dev.quinton.goa.web.timer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.quinton.goa.core.domain.setup.EndOfRoundStep
import dev.quinton.goa.core.domain.timer.PhaseKind
import dev.quinton.goa.core.domain.timer.PhaseScope
import dev.quinton.goa.core.domain.timer.PhaseSpec
import dev.quinton.goa.core.domain.timer.PresetIssue
import dev.quinton.goa.core.domain.timer.TimerPreset
import dev.quinton.goa.core.domain.timer.TimerPresetValidator
import dev.quinton.goa.feature.timer.TimerText
import dev.quinton.goa.feature.timer.ui.presets.PresetEditUiState
import dev.quinton.goa.feature.timer.ui.presets.PresetEdits
import dev.quinton.goa.feature.timer.ui.presets.PresetsUiState
import dev.quinton.goa.feature.timer.ui.timer.PresetOption
import dev.quinton.goa.feature.timer.ui.timer.TimerUiMapper
import dev.quinton.goa.web.AppServices
import dev.quinton.goa.web.LocalServices
import dev.quinton.goa.web.nav.Route
import dev.quinton.goa.web.rememberRetained
import dev.quinton.goa.web.table.MenuItem
import dev.quinton.goa.web.table.MenuSheet
import dev.quinton.goa.web.table.RadioCard
import dev.quinton.goa.web.table.RadioGroup
import dev.quinton.goa.web.table.SmallHeading
import dev.quinton.goa.web.table.Stepper
import dev.quinton.goa.web.table.SwitchRow
import dev.quinton.goa.web.table.TextField
import dev.quinton.goa.web.ui.ButtonKind
import dev.quinton.goa.web.ui.GoaButton
import dev.quinton.goa.web.ui.Icon
import dev.quinton.goa.web.ui.IconButton
import dev.quinton.goa.web.ui.IconSize
import dev.quinton.goa.web.ui.LoadingState
import dev.quinton.goa.web.ui.Screen
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.attributes.ButtonType
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.attributes.type
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

// ---------------------------------------------------------------- Preset list

/** "Timer presets": choose, edit, duplicate, delete, restore the bundled ones (the phone's PresetsScreen). */
@Composable
fun PresetsScreen() {
    val services = LocalServices.current
    val repository = services.data.table.timerStore
    val scope = rememberCoroutineScope()
    val state by remember {
        combine(repository.presets, repository.selectedPresetId) { list, selected ->
            PresetsUiState(loading = false, presets = list.map(TimerUiMapper::presetOption), selectedId = selected)
        }.stateIn(scope, SharingStarted.Eagerly, PresetsUiState())
    }.collectAsState()
    LaunchedEffect(Unit) { repository.load() }
    var menuFor by remember { mutableStateOf<PresetOption?>(null) }
    Screen(
        "Timer presets",
        actions = {
            IconButton("refresh", "Restore default presets", onClick = {
                scope.launch {
                    repository.restoreDefaults()
                    services.messages.show("Default presets restored.")
                }
            })
        },
    ) {
        if (state.loading) {
            LoadingState()
            return@Screen
        }
        Div({ classes("goa-table-stack") }) {
            P({ classes("t-body-small", "c-variant") }) {
                Text(
                    "The selected preset is used when you start the timer. " +
                        "Timers are table rules; the rulebook sets none.",
                )
            }
            RadioGroup("Presets") {
                state.presets.forEach { preset ->
                    Div({ classes("goa-table-preset-row") }) {
                        RadioCard(preset.id == state.selectedId, onClick = {
                            scope.launch { repository.select(preset.id) }
                        }) {
                            Span({ classes("t-title-small") }) { Text(preset.name) }
                            Span({ classes("t-body-small", "c-variant") }) { Text(preset.summary) }
                        }
                        IconButton("edit", "Edit ${preset.name}", onClick = {
                            services.router.navigate(Route.TimerPresetEdit(preset.id))
                        })
                        IconButton("more", "More options for ${preset.name}", onClick = { menuFor = preset })
                    }
                }
            }
            GoaButton("New preset", { services.router.navigate(Route.TimerPresetEdit()) }, icon = "add", block = true)
        }
    }
    menuFor?.let { preset ->
        MenuSheet(
            preset.name,
            listOf(
                MenuItem("Duplicate", "copy", onClick = { scope.launch { duplicate(services, preset.id) } }),
                MenuItem("Delete", "delete", enabled = state.canDelete, onClick = {
                    scope.launch { repository.delete(preset.id) }
                }),
            ),
            onDismiss = { menuFor = null },
        )
    }
}

@OptIn(ExperimentalUuidApi::class)
private suspend fun duplicate(services: AppServices, id: String) {
    val repository = services.data.table.timerStore
    val source = repository.preset(id) ?: return
    repository.save(PresetEdits.duplicate(source, Uuid.random().toString()))
}

// ---------------------------------------------------------------- Preset editor

/** The preset editor's state (the phone's `PresetEditViewModel`), kept while its history entry exists. */
private class PresetEditModel(private val services: AppServices, private val presetId: String?, scope: CoroutineScope) {
    private val repository = services.data.table.timerStore
    val state = MutableStateFlow(PresetEditUiState())

    init {
        scope.launch { open() }
    }

    @OptIn(ExperimentalUuidApi::class)
    private suspend fun open() {
        repository.load()
        val endSteps = repository.rules().endOfRound
        val existing = presetId?.let { repository.preset(it) }
        val template = repository.presets.first().firstOrNull { it.id == repository.selectedPresetId.first() }
        state.value = PresetEditUiState.open(existing, template, endSteps, Uuid.random().toString())
    }

    fun edit(transform: (TimerPreset) -> TimerPreset) = state.update { it.edit(transform) }

    fun editPhase(id: String, transform: (PhaseSpec) -> PhaseSpec) = edit { PresetEdits.editPhase(it, id, transform) }

    /** Saves and returns true; with problems left, shows them and returns false. */
    suspend fun save(): Boolean {
        val current = state.value
        val preset = current.preset ?: return false
        if (current.issues.isNotEmpty()) {
            state.update { it.copy(showIssues = true) }
            return false
        }
        repository.save(PresetEdits.forSave(preset))
        return true
    }
}

/** Which duration the picker edits. */
private sealed interface Picking {
    data class Phase(val phase: PhaseSpec) : Picking

    data object ShotClock : Picking

    data object TimeOut : Picking
}

@OptIn(ExperimentalUuidApi::class)
@Composable
fun PresetEditScreen(presetId: String?) {
    val services = LocalServices.current
    val scope = rememberCoroutineScope()
    val model = rememberRetained("presetEdit") { PresetEditModel(services, presetId, services.data.scope) }
    val state by model.state.collectAsState()
    var picking by remember { mutableStateOf<Picking?>(null) }
    val save: () -> Unit = {
        scope.launch { if (model.save()) services.router.back() }
    }
    Screen(
        if (state.isNew) "New preset" else "Edit preset",
        actions = {
            Button({
                type(ButtonType.Button)
                classes("goa-button", "text", "goa-table-on-banner")
                if (state.preset == null) disabled()
                onClick { save() }
            }) { Text("Save") }
        },
    ) {
        val preset = state.preset
        if (preset == null) {
            LoadingState()
            return@Screen
        }
        Div({ classes("goa-table-stack") }) {
            // A new preset starts blank: only flag the name once the user tries to save.
            val nameError =
                state.issuesFor(null).any { it.kind == PresetIssue.Kind.BLANK_NAME } &&
                    (state.showIssues || !state.isNew)
            TextField(
                "Preset name",
                preset.name,
                onValue = { name -> model.edit { it.copy(name = name) } },
                placeholder = "e.g. Fast game",
                error = if (nameError) "Give the preset a name" else null,
            )
            SwitchRow(
                "Auto-advance",
                "Move on when a timed phase hits zero. Off: overtime counts up until you tap Next.",
                preset.autoAdvance,
            ) { on -> model.edit { it.copy(autoAdvance = on) } }
            if (state.showIssues && state.issues.isNotEmpty()) {
                P({
                    classes("t-body-medium", "c-error")
                    attr("role", "alert")
                }) { Text("Fix the highlighted items to save.") }
            }
            ScopeSection(
                "Round start",
                "Once per round, before turn 1.",
                PhaseScope.ROUND_START,
                state,
                model,
                onPick = { picking = Picking.Phase(it) },
            )
            ScopeSection(
                "Every turn",
                "Each of the ${preset.turnsPerRound} turns: play a card, then resolve after the reveal. " +
                    "The Action timer repeats for every hero and replaces Resolve when on.",
                PhaseScope.TURN,
                state,
                model,
                onPick = { picking = Picking.Phase(it) },
            )
            ScopeSection(
                "End of round",
                "Runs on a checklist step (the Upgrade timer on Level Up) or after the checklist.",
                PhaseScope.ROUND_END,
                state,
                model,
                onPick = { picking = Picking.Phase(it) },
            )
            TableRulesSection(state, model, onPick = { picking = it })
            GoaButton("Save preset", save, block = true)
        }
    }
    when (val p = picking) {
        is Picking.Phase -> DurationPickerSheet(p.phase.name, p.phase.seconds, onDismiss = {
            picking = null
        }, onConfirm = { seconds ->
            model.edit { PresetEdits.setSeconds(it, p.phase.id, seconds) }
            picking = null
        })

        Picking.ShotClock -> DurationPickerSheet(
            "Hurry up",
            state.preset?.shotClockSeconds,
            onDismiss = { picking = null },
            onConfirm = { seconds ->
                model.edit { it.copy(shotClockSeconds = seconds) }
                picking = null
            },
            off = OffOption("Off", "No Hurry up button on the timer."),
            maxSeconds = TimerPresetValidator.MAX_CUE_SECONDS,
        )

        Picking.TimeOut -> DurationPickerSheet(
            "Time-out length",
            state.preset?.timeOutSeconds,
            onDismiss = { picking = null },
            onConfirm = { seconds ->
                model.edit { PresetEdits.setTimeOutSeconds(it, seconds) }
                picking = null
            },
            off = null,
            maxSeconds = TimerPresetValidator.MAX_CUE_SECONDS,
        )

        null -> Unit
    }
}

/** House rules for pace; none of these are in the rulebook. (The phone's volume-key control has no web equivalent.) */
@Composable
private fun TableRulesSection(state: PresetEditUiState, model: PresetEditModel, onPick: (Picking) -> Unit) {
    val preset = state.preset ?: return
    SmallHeading("Table rules")
    P({ classes("t-body-small", "c-variant") }) { Text("House rules, not in the rulebook.") }
    RuleRow(
        "Hurry up",
        "On-demand countdown for a slow decision, in any phase or in overtime.",
        preset.shotClockSeconds?.let { TimerText.durationLabel(it) } ?: "Off",
    ) { onPick(Picking.ShotClock) }
    Div({ classes("goa-table-inline-card") }) {
        Div({ classes("goa-table-row-text") }) {
            Span({ classes("t-title-small") }) { Text("Time-outs per team per round") }
            Span({ classes("t-body-small", "c-variant") }) { Text("Each adds time to the current phase. 0 = off.") }
        }
        Stepper(
            preset.timeOutsPerRound,
            TimerPresetValidator.TIME_OUTS_RANGE,
            { count -> model.edit { PresetEdits.setTimeOutsPerRound(it, count) } },
            "time-outs",
        )
    }
    if (preset.timeOutsPerRound > 0) {
        RuleRow(
            "Time-out length",
            "Added to the current phase.",
            "+" + TimerText.durationLabel(preset.timeOutSeconds),
        ) {
            onPick(Picking.TimeOut)
        }
    }
    if (state.showIssues) {
        state.issuesFor(null).mapNotNull(PresetEdits::tableRuleIssueText).forEach {
            P({
                classes("t-body-small", "c-error")
                attr("role", "alert")
            }) { Text(it) }
        }
    }
}

@Composable
private fun RuleRow(title: String, subtitle: String, value: String, onClick: () -> Unit) {
    Div({ classes("goa-table-inline-card") }) {
        Div({ classes("goa-table-row-text") }) {
            Span({ classes("t-title-small") }) { Text(title) }
            Span({ classes("t-body-small", "c-variant") }) { Text(subtitle) }
        }
        Button({
            type(ButtonType.Button)
            classes("goa-table-duration-chip")
            attr("aria-label", "$title: $value. Change")
            onClick { onClick() }
        }) {
            Icon("timer", IconSize.SMALL)
            Text(value)
        }
    }
}

@OptIn(ExperimentalUuidApi::class)
@Composable
private fun ScopeSection(
    title: String,
    hint: String,
    scope: PhaseScope,
    state: PresetEditUiState,
    model: PresetEditModel,
    onPick: (PhaseSpec) -> Unit,
) {
    val phases = state.preset?.phases.orEmpty().filter { it.scope == scope }
    SmallHeading(title)
    P({ classes("t-body-small", "c-variant") }) { Text(hint) }
    phases.forEachIndexed { index, phase ->
        PhaseCard(phase, index > 0, index < phases.lastIndex, state.issuesFor(phase.id), state.endSteps, model) {
            onPick(phase)
        }
    }
    GoaButton("Add ${title.lowercase()} timer", {
        model.edit { PresetEdits.addPhase(it, scope, Uuid.random().toString()) }
    }, ButtonKind.OUTLINED, icon = "add")
}

@Composable
private fun PhaseCard(
    phase: PhaseSpec,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    issues: List<PresetIssue>,
    endSteps: List<EndOfRoundStep>,
    model: PresetEditModel,
    onEditDuration: () -> Unit,
) {
    var endStepMenu by remember { mutableStateOf(false) }
    Div({
        classes("goa-card", "goa-table-phase-card")
        if (issues.isNotEmpty()) classes("error")
        if (!phase.enabled) classes("off")
    }) {
        SwitchRow(TimerText.kindLabel(phase.kind).uppercase(), null, phase.enabled) { on ->
            model.editPhase(phase.id) { it.copy(enabled = on) }
        }
        Div({ classes("goa-table-phase-body") }) {
            TextField(
                "Name",
                phase.name,
                onValue = { name -> model.editPhase(phase.id) { it.copy(name = name) } },
                error = if (issues.any {
                        it.kind == PresetIssue.Kind.BLANK_PHASE_NAME
                    }
                ) {
                    issues.first { it.kind == PresetIssue.Kind.BLANK_PHASE_NAME }.let(PresetEdits::issueText)
                } else {
                    null
                },
            )
            Div({ classes("goa-table-phase-actions") }) {
                Button({
                    type(ButtonType.Button)
                    classes("goa-table-duration-chip")
                    attr("aria-label", "${phase.name}: ${TimerText.durationLabel(phase.seconds)}. Change")
                    onClick { onEditDuration() }
                }) {
                    Icon("timer", IconSize.SMALL)
                    Text(TimerText.durationLabel(phase.seconds))
                }
                Span({ classes("goa-table-grow") })
                IconMoveButton("arrow_up", "Move ${phase.name} up", canMoveUp) {
                    model.edit { PresetEdits.move(it, phase.id, -1) }
                }
                IconMoveButton("arrow_down", "Move ${phase.name} down", canMoveDown) {
                    model.edit { PresetEdits.move(it, phase.id, 1) }
                }
                if (phase.kind == PhaseKind.CUSTOM) {
                    IconButton("delete", "Remove ${phase.name}", onClick = {
                        model.edit { PresetEdits.removePhase(it, phase.id) }
                    })
                }
            }
            val seconds = phase.seconds
            if (seconds != null) {
                Span({ classes("t-label-medium", "c-variant") }) { Text("Cues (seconds left)") }
                Div({ classes("goa-chip-row", "wrap") }) {
                    PresetEdits.CUE_OPTIONS.filter { it < seconds }.forEach { cue ->
                        dev.quinton.goa.web.ui.Chip(
                            if (cue ==
                                0
                            ) {
                                "Time's up"
                            } else {
                                "${cue}s"
                            },
                            cue in phase.cues,
                            onClick = {
                                model.edit { PresetEdits.toggleCue(it, phase.id, cue) }
                            },
                        )
                    }
                }
            }
            if (phase.scope == PhaseScope.ROUND_END) {
                GoaButton("Runs during: ${PresetEdits.endStepLabel(phase, endSteps)}", {
                    endStepMenu = true
                }, ButtonKind.TEXT)
            }
            // A blank name is said once, on the Name field.
            issues.filter { it.kind != PresetIssue.Kind.BLANK_PHASE_NAME }.mapNotNull(PresetEdits::issueText).forEach {
                P({
                    classes("t-body-small", "c-error")
                    attr("role", "alert")
                }) { Text(it) }
            }
        }
    }
    if (endStepMenu) {
        MenuSheet(
            "Runs during",
            endSteps.mapIndexed { i, step ->
                MenuItem("Step ${i + 1}: ${step.title}", "checklist", onClick = {
                    model.editPhase(phase.id) { it.copy(endStepId = step.id) }
                })
            } +
                MenuItem("After the checklist", "checklist", onClick = {
                    model.editPhase(phase.id) { it.copy(endStepId = null) }
                }),
            onDismiss = { endStepMenu = false },
        )
    }
}

@Composable
private fun IconMoveButton(icon: String, label: String, enabled: Boolean, onClick: () -> Unit) {
    Button({
        type(ButtonType.Button)
        classes("goa-icon-button")
        attr("aria-label", label)
        title(label)
        if (!enabled) disabled()
        onClick { onClick() }
    }) { Icon(icon) }
}
