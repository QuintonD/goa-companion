package dev.quinton.goa.feature.timer.ui.presets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import dev.quinton.goa.feature.timer.ui.timer.SectionHeader

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
        ),
    )
}

@Composable
fun PresetEditScreen(state: PresetEditUiState, actions: PresetEditActions) {
    var editing by remember { mutableStateOf<PhaseSpec?>(null) }
    GoaScreen(
        title = if (state.isNew) "New preset" else "Edit preset",
        onBack = actions.onBack,
        actions = { TextButton(onClick = actions.onSave, enabled = state.preset != null) { Text("Save") } },
    ) { padding ->
        val preset = state.preset ?: return@GoaScreen
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            val nameError = state.issuesFor(null).any { it.kind == PresetIssue.Kind.BLANK_NAME }
            OutlinedTextField(
                value = preset.name,
                onValueChange = actions.onRename,
                label = { Text("Preset name") },
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
                hint = "Each of the ${preset.turnsPerRound} turns. The Action timer repeats for every hero.",
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
            Spacer(Modifier.height(Spacing.xl))
        }
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
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    .toggleable(value = phase.enabled, onValueChange = { actions.onEnabled(phase.id, it) }, role = Role.Switch),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        TimerText.kindLabel(phase.kind).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.primary,
                    )
                    Text(phase.name.ifBlank { "Unnamed" }, style = MaterialTheme.typography.titleMedium)
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
                        leadingIcon = { Icon(Icons.Outlined.Timer, contentDescription = null, modifier = Modifier.height(18.dp)) },
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
                    Text("Cues (seconds left)", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        PresetEditViewModel.CUE_OPTIONS.filter { it < seconds }.forEach { cue ->
                            FilterChip(
                                selected = cue in phase.cues,
                                onClick = { actions.onToggleCue(phase.id, cue) },
                                label = { Text(if (cue == 0) "Time's up" else "${cue}s") },
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
    val index = endSteps.indexOfFirst { it.id == phase.endStepId }
    val current = if (index >= 0) "Step ${index + 1}: ${endSteps[index].title}" else "After the checklist"
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
        val text = when (issue.kind) {
            PresetIssue.Kind.DURATION_OUT_OF_RANGE ->
                "Duration must be between ${TimerPresetValidator.MIN_SECONDS} s and ${TimerPresetValidator.MAX_SECONDS / 60} min."
            PresetIssue.Kind.CUE_OUT_OF_RANGE -> "Cues must be shorter than the phase."
            PresetIssue.Kind.BLANK_PHASE_NAME -> "Give this timer a name."
            PresetIssue.Kind.DUPLICATE_PHASE_ID -> "Duplicate timer."
            PresetIssue.Kind.ACTION_NOT_PER_TURN -> "The Action timer runs during turns only."
            PresetIssue.Kind.UNKNOWN_END_STEP -> "Pick an end-of-round step."
            PresetIssue.Kind.BLANK_NAME, PresetIssue.Kind.TURNS_OUT_OF_RANGE -> null
        } ?: return@forEach
        Text(
            text,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = Spacing.xs).semantics { error(text) },
        )
    }
}
