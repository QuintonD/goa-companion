package dev.quinton.goa.feature.timer.ui.presets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.quinton.goa.core.designsystem.component.GoaScreen
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.domain.setup.EndOfRoundStep
import dev.quinton.goa.core.domain.timer.PhaseKind
import dev.quinton.goa.core.domain.timer.PhaseScope
import dev.quinton.goa.core.domain.timer.PhaseSpec
import dev.quinton.goa.core.domain.timer.PresetIssue
import dev.quinton.goa.core.domain.timer.TimerPresetValidator
import dev.quinton.goa.feature.timer.TimerText
import dev.quinton.goa.feature.timer.ui.components.goaChipBorder
import dev.quinton.goa.feature.timer.ui.components.goaChipColors
import dev.quinton.goa.feature.timer.ui.timer.SectionHeader
import dev.quinton.goa.feature.timer.ui.timer.Stepper

data class PresetEditActions(
    val onBack: () -> Unit = {},
    val onSave: () -> Unit = {},
    val onRename: (String) -> Unit = {},
    val onAutoAdvance: (Boolean) -> Unit = {},
    val onEnabled: (String, Boolean) -> Unit = { _, _ -> },
    val onRenamePhase: (String, String) -> Unit = { _, _ -> },
    val onSeconds: (String, Int?) -> Unit = { _, _ -> },
    val onToggleCue: (String, Int) -> Unit = { _, _ -> },
    val onEndStep: (String, String?) -> Unit = { _, _ -> },
    val onMove: (String, Int) -> Unit = { _, _ -> },
    val onAdd: (PhaseScope) -> Unit = {},
    val onRemove: (String) -> Unit = {},
    val onShotClock: (Int?) -> Unit = {},
    val onTimeOuts: (Int) -> Unit = {},
    val onTimeOutSeconds: (Int?) -> Unit = {},
    val onVolumeKeys: (Boolean) -> Unit = {},
)

@Composable
fun PresetEditRoute(viewModel: PresetEditViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.navigation.collect { if (it == PresetEditEvent.Saved) onBack() }
    }
    PresetEditScreen(
        state,
        PresetEditActions(
            onBack = onBack,
            onSave = viewModel::save,
            onRename = viewModel::rename,
            onAutoAdvance = viewModel::setAutoAdvance,
            onEnabled = viewModel::setEnabled,
            onRenamePhase = viewModel::renamePhase,
            onSeconds = viewModel::setSeconds,
            onToggleCue = viewModel::toggleCue,
            onEndStep = viewModel::setEndStep,
            onMove = viewModel::move,
            onAdd = viewModel::addPhase,
            onRemove = viewModel::removePhase,
            onShotClock = viewModel::setShotClock,
            onTimeOuts = viewModel::setTimeOutsPerRound,
            onTimeOutSeconds = viewModel::setTimeOutSeconds,
            onVolumeKeys = viewModel::setVolumeKeys,
        ),
    )
}

/** Which table-rule duration the picker edits. */
private enum class RuleDuration { SHOT_CLOCK, TIME_OUT }

@Composable
fun PresetEditScreen(state: PresetEditUiState, actions: PresetEditActions) {
    var editing by remember { mutableStateOf<PhaseSpec?>(null) }
    var editingRule by remember { mutableStateOf<RuleDuration?>(null) }
    GoaScreen(
        title = if (state.isNew) "New preset" else "Edit preset",
        onBack = actions.onBack,
        actions = { TextButton(onClick = actions.onSave, enabled = state.preset != null) { Text("Save") } },
    ) { padding ->
        val preset = state.preset ?: return@GoaScreen
        Column(
            Modifier.fillMaxSize().padding(
                padding,
            ).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            // A new preset starts blank: only flag the name once the user tries to save.
            val nameError = state.issuesFor(null).any { it.kind == PresetIssue.Kind.BLANK_NAME } &&
                (state.showIssues || !state.isNew)
            OutlinedTextField(
                value = preset.name,
                onValueChange = actions.onRename,
                label = { Text("Preset name") },
                placeholder = { Text("e.g. Fast game") },
                singleLine = true,
                isError = nameError,
                supportingText = if (nameError) ({ Text("Give the preset a name") }) else null,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
            )
            ToggleRow(
                title = "Auto-advance",
                subtitle = "Move on when a timed phase hits zero. Off: overtime counts up until you tap Next.",
                checked = preset.autoAdvance,
                onChange = actions.onAutoAdvance,
            )
            if (state.showIssues && state.issues.isNotEmpty()) {
                Text(
                    "Fix the highlighted items to save.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                )
            }
            ScopeSection(
                title = "Round start",
                hint = "Once per round, before turn 1.",
                scope = PhaseScope.ROUND_START,
                state = state,
                actions = actions,
                onEditDuration = { editing = it },
            )
            ScopeSection(
                title = "Every turn",
                hint = "Each of the ${preset.turnsPerRound} turns: play a card, then resolve after the reveal. " +
                    "The Action timer repeats for every hero and replaces Resolve when on.",
                scope = PhaseScope.TURN,
                state = state,
                actions = actions,
                onEditDuration = { editing = it },
            )
            ScopeSection(
                title = "End of round",
                hint = "Runs on a checklist step (the Upgrade timer on Level Up) or after the checklist.",
                scope = PhaseScope.ROUND_END,
                state = state,
                actions = actions,
                onEditDuration = { editing = it },
            )
            TableRulesSection(state, actions, onEditDuration = { editingRule = it })
            Spacer(Modifier.height(Spacing.xl))
        }
    }
    editingRule?.let { rule ->
        val preset = state.preset
        DurationPickerSheet(
            title = if (rule == RuleDuration.SHOT_CLOCK) "Hurry up" else "Time-out length",
            initialSeconds = if (rule == RuleDuration.SHOT_CLOCK) preset?.shotClockSeconds else preset?.timeOutSeconds,
            onDismiss = { editingRule = null },
            onConfirm = {
                if (rule == RuleDuration.SHOT_CLOCK) actions.onShotClock(it) else actions.onTimeOutSeconds(it)
                editingRule = null
            },
            off = if (rule == RuleDuration.SHOT_CLOCK) OffOption("Off", "No Hurry up button on the timer.") else null,
            maxSeconds = TimerPresetValidator.MAX_CUE_SECONDS,
        )
    }
    editing?.let { phase ->
        DurationPickerSheet(
            title = phase.name,
            initialSeconds = phase.seconds,
            onDismiss = { editing = null },
            onConfirm = {
                actions.onSeconds(phase.id, it)
                editing = null
            },
        )
    }
}

/** House rules for pace and control; none of these are in the rulebook. */
@Composable
private fun TableRulesSection(
    state: PresetEditUiState,
    actions: PresetEditActions,
    onEditDuration: (RuleDuration) -> Unit,
) {
    val preset = state.preset ?: return
    val colors = MaterialTheme.colorScheme
    SectionHeader("Table rules")
    Text(
        "House rules, not in the rulebook.",
        style = MaterialTheme.typography.bodySmall,
        color = colors.onSurfaceVariant,
    )
    RuleRow(
        title = "Hurry up",
        subtitle = "On-demand countdown for a slow decision, in any phase or in overtime.",
        value = preset.shotClockSeconds?.let { TimerText.durationLabel(it) } ?: "Off",
        onClick = { onEditDuration(RuleDuration.SHOT_CLOCK) },
    )
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = Spacing.md)) {
            Text("Time-outs per team per round", style = MaterialTheme.typography.titleSmall)
            Text(
                "Each adds time to the current phase. 0 = off.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
        Stepper(
            value = preset.timeOutsPerRound,
            range = TimerPresetValidator.TIME_OUTS_RANGE,
            onChange = actions.onTimeOuts,
            label = "time-outs",
        )
    }
    if (preset.timeOutsPerRound > 0) {
        RuleRow(
            title = "Time-out length",
            subtitle = "Added to the current phase.",
            value = "+" + TimerText.durationLabel(preset.timeOutSeconds),
            onClick = { onEditDuration(RuleDuration.TIME_OUT) },
        )
    }
    ToggleRow(
        title = "Volume keys control the timer",
        subtitle = "While the timer shows: volume up = Next, volume down = Pause/Resume. Works with most " +
            "Bluetooth camera shutters. The keys won't change the volume then.",
        checked = preset.volumeKeys,
        onChange = actions.onVolumeKeys,
    )
    if (state.showIssues) {
        state.issuesFor(null).filter {
            it.kind == PresetIssue.Kind.SHOT_CLOCK_OUT_OF_RANGE || it.kind == PresetIssue.Kind.TIME_OUTS_OUT_OF_RANGE
        }.forEach { issue ->
            val text = PresetEdits.tableRuleIssueText(issue) ?: return@forEach
            Text(
                text,
                color = colors.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.semantics { error(text) },
            )
        }
    }
}

@Composable
private fun RuleRow(title: String, subtitle: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = Spacing.md)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AssistChip(
            onClick = onClick,
            label = { Text(value) },
            leadingIcon = { Icon(Icons.Outlined.Timer, contentDescription = null, modifier = Modifier.height(18.dp)) },
            colors = AssistChipDefaults.assistChipColors(labelColor = MaterialTheme.colorScheme.secondary),
            modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "$title: $value. Change" },
        )
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, onValueChange = onChange, role = Role.Switch),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = Spacing.md)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun ScopeSection(
    title: String,
    hint: String,
    scope: PhaseScope,
    state: PresetEditUiState,
    actions: PresetEditActions,
    onEditDuration: (PhaseSpec) -> Unit,
) {
    val phases = state.preset?.phases.orEmpty().filter { it.scope == scope }
    SectionHeader(title)
    Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    phases.forEachIndexed { index, phase ->
        PhaseCard(
            phase = phase,
            canMoveUp = index > 0,
            canMoveDown = index < phases.lastIndex,
            issues = state.issuesFor(phase.id),
            showIssues = true,
            endSteps = state.endSteps,
            actions = actions,
            onEditDuration = { onEditDuration(phase) },
        )
    }
    OutlinedButton(onClick = { actions.onAdd(scope) }, modifier = Modifier.heightIn(min = 48.dp)) {
        Icon(Icons.Filled.Add, contentDescription = null)
        Spacer(Modifier.width(Spacing.sm))
        Text("Add ${title.lowercase()} timer")
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PhaseCard(
    phase: PhaseSpec,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    issues: List<PresetIssue>,
    showIssues: Boolean,
    endSteps: List<EndOfRoundStep>,
    actions: PresetEditActions,
    onEditDuration: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val hasError = showIssues && issues.isNotEmpty()
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = colors.surface,
        border = BorderStroke(1.dp, if (hasError) colors.error else GoaTheme.colors.engravedBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.md)) {
            Row(
                Modifier
                    .heightIn(min = 48.dp)
                    .toggleable(value = phase.enabled, onValueChange = {
                        actions.onEnabled(phase.id, it)
                    }, role = Role.Switch),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        TimerText.kindLabel(phase.kind).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.primary,
                    )
                }
                Switch(checked = phase.enabled, onCheckedChange = null)
            }
            Column(Modifier.alpha(if (phase.enabled) 1f else 0.55f)) {
                Spacer(Modifier.height(Spacing.sm))
                OutlinedTextField(
                    value = phase.name,
                    onValueChange = { actions.onRenamePhase(phase.id, it) },
                    label = { Text("Name") },
                    singleLine = true,
                    isError = issues.any { it.kind == PresetIssue.Kind.BLANK_PHASE_NAME },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(Spacing.sm))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AssistChip(
                        onClick = onEditDuration,
                        label = { Text(TimerText.durationLabel(phase.seconds)) },
                        leadingIcon = {
                            Icon(Icons.Outlined.Timer, contentDescription = null, modifier = Modifier.height(18.dp))
                        },
                        colors = AssistChipDefaults.assistChipColors(labelColor = colors.secondary),
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { actions.onMove(phase.id, -1) }, enabled = canMoveUp) {
                        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move ${phase.name} up")
                    }
                    IconButton(onClick = { actions.onMove(phase.id, 1) }, enabled = canMoveDown) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move ${phase.name} down")
                    }
                    if (phase.kind == PhaseKind.CUSTOM) {
                        IconButton(onClick = { actions.onRemove(phase.id) }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Remove ${phase.name}")
                        }
                    }
                }
                val seconds = phase.seconds
                if (seconds != null) {
                    Text(
                        "Cues (seconds left)",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        PresetEdits.CUE_OPTIONS.filter { it < seconds }.forEach { cue ->
                            FilterChip(
                                selected = cue in phase.cues,
                                onClick = { actions.onToggleCue(phase.id, cue) },
                                label = { Text(if (cue == 0) "Time's up" else "${cue}s") },
                                colors = goaChipColors(),
                                border = goaChipBorder(cue in phase.cues),
                                modifier = Modifier.heightIn(min = 48.dp),
                            )
                        }
                    }
                }
                if (phase.scope == PhaseScope.ROUND_END) {
                    EndStepPicker(phase, endSteps) { actions.onEndStep(phase.id, it) }
                }
                IssueText(issues)
            }
        }
    }
}

@Composable
private fun EndStepPicker(phase: PhaseSpec, endSteps: List<EndOfRoundStep>, onPick: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val current = PresetEdits.endStepLabel(phase, endSteps)
    Box {
        TextButton(onClick = { open = true }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text("Runs during: $current")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            endSteps.forEachIndexed { i, step ->
                DropdownMenuItem(
                    text = { Text("Step ${i + 1}: ${step.title}") },
                    onClick = {
                        open = false
                        onPick(step.id)
                    },
                )
            }
            DropdownMenuItem(
                text = { Text("After the checklist") },
                onClick = {
                    open = false
                    onPick(null)
                },
            )
        }
    }
}

@Composable
private fun IssueText(issues: List<PresetIssue>) {
    issues.forEach { issue ->
        val text = PresetEdits.issueText(issue) ?: return@forEach
        Text(
            text,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = Spacing.xs).semantics { error(text) },
        )
    }
}
