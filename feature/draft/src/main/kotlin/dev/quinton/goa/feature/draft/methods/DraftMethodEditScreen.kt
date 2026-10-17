package dev.quinton.goa.feature.draft.methods

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.quinton.goa.core.designsystem.component.GoaScreen
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.domain.draft.StepSpec
import dev.quinton.goa.core.domain.draft.TeamSlot
import dev.quinton.goa.core.domain.draft.ban
import dev.quinton.goa.core.domain.draft.pick
import dev.quinton.goa.core.model.draft.DraftAction
import dev.quinton.goa.feature.draft.ui.SectionCard
import dev.quinton.goa.feature.draft.ui.SequenceStrip
import dev.quinton.goa.feature.draft.ui.actionName
import dev.quinton.goa.feature.draft.ui.goaFilterChipColors
import dev.quinton.goa.feature.draft.ui.goaSegmentedColors
import dev.quinton.goa.feature.draft.ui.goaTonalIconColors

interface MethodEditActions {
    fun back() {}

    fun save() {}

    fun setName(name: String) {}

    fun setPoolKind(kind: PoolKind) {}

    fun setPoolValue(value: Int) {}

    fun addStep(section: StepSection, step: StepSpec) {}

    fun removeStep(section: StepSection, index: Int) {}

    fun toggleTeam(section: StepSection, index: Int) {}

    fun toggleAction(section: StepSection, index: Int) {}

    fun toggleHidden(section: StepSection, index: Int) {}

    fun moveStep(section: StepSection, index: Int, delta: Int) {}

    fun setStepSeconds(seconds: Int?) {}

    fun setLayout(layout: PreviewLayout) {}

    companion object {
        val None = object : MethodEditActions {}
    }
}

@Composable
fun DraftMethodEditDestination(viewModel: DraftMethodEditViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.editEvents.collect { if (it == MethodEditEvent.Saved) onBack() } }
    val actions = remember(viewModel) {
        object : MethodEditActions {
            override fun back() = onBack()
            override fun save() = viewModel.save()
            override fun setName(name: String) = viewModel.setName(name)
            override fun setPoolKind(kind: PoolKind) = viewModel.setPoolKind(kind)
            override fun setPoolValue(value: Int) = viewModel.setPoolValue(value)
            override fun addStep(section: StepSection, step: StepSpec) = viewModel.addStep(section, step)
            override fun removeStep(section: StepSection, index: Int) = viewModel.removeStep(section, index)
            override fun toggleTeam(section: StepSection, index: Int) = viewModel.toggleTeam(section, index)
            override fun toggleAction(section: StepSection, index: Int) = viewModel.toggleAction(section, index)
            override fun toggleHidden(section: StepSection, index: Int) = viewModel.toggleHidden(section, index)
            override fun moveStep(
                section: StepSection,
                index: Int,
                delta: Int,
            ) = viewModel.moveStep(section, index, delta)
            override fun setStepSeconds(seconds: Int?) = viewModel.setStepSeconds(seconds)
            override fun setLayout(layout: PreviewLayout) = viewModel.setLayout(layout)
        }
    }
    DraftMethodEditScreen(state, actions)
}

@Composable
fun DraftMethodEditScreen(state: MethodEditUiState, actions: MethodEditActions) {
    GoaScreen(
        title = if (state.isNew) "New method" else "Edit method",
        onBack = actions::back,
        actions = {
            TextButton(onClick = actions::save, enabled = state.error == null && !state.loading) { Text("Save") }
        },
    ) { padding ->
        if (state.loading) return@GoaScreen
        LazyColumn(
            contentPadding = PaddingValues(Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            item {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = actions::setName,
                    label = { Text("Name") },
                    singleLine = true,
                    supportingText = state.copyOf?.let { { Text("Copy of $it — the original stays unchanged") } },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item { PoolCard(state, actions) }
            if (state.fixedSequence == null) {
                item { StepsCard(StepSection.OPENING, "Opening", "Runs once, in order", state.opening, actions) }
                item {
                    StepsCard(
                        StepSection.REPEAT,
                        "Then repeat",
                        "Cycles until every seat is filled; picks for a full team are skipped",
                        state.repeat,
                        actions,
                    )
                }
            } else {
                item {
                    SectionCard(title = "Sequence", subtitle = "Set by the original method for each player count") {
                        Text(
                            "This order depends on the number of heroes, so it can't be edited step by step. " +
                                "You can still change the name, pool and timer.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item { TimerCard(state, actions) }
            item { PreviewCard(state, actions) }
            item {
                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
                Button(
                    onClick = actions::save,
                    enabled = state.error == null && !state.saving,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(top = Spacing.sm),
                ) { Text("Save method") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PoolCard(state: MethodEditUiState, actions: MethodEditActions) {
    SectionCard(title = "Hero pool", subtitle = poolText(state.pool)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            PoolKind.entries.forEach { kind ->
                FilterChip(
                    colors = goaFilterChipColors(),
                    selected = state.poolKind == kind,
                    onClick = { actions.setPoolKind(kind) },
                    label = { Text(kind.label) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
        if (state.poolKind != PoolKind.ALL) {
            Row(Modifier.padding(top = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when (state.poolKind) {
                        PoolKind.RANDOM -> "Heroes in the pool"
                        PoolKind.SEATS_PLUS -> "Extra heroes revealed"
                        else -> "Heroes dealt to each seat"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                FilledTonalIconButton(colors = goaTonalIconColors(), onClick = {
                    actions.setPoolValue(state.poolValue - 1)
                }) {
                    Icon(Icons.Filled.Remove, contentDescription = "Fewer")
                }
                Text(
                    "${state.poolValue}",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.width(48.dp).semantics { contentDescription = "${state.poolValue} heroes" },
                    textAlign = TextAlign.Center,
                )
                FilledTonalIconButton(colors = goaTonalIconColors(), onClick = {
                    actions.setPoolValue(state.poolValue + 1)
                }) {
                    Icon(Icons.Filled.Add, contentDescription = "More")
                }
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
    actions: MethodEditActions,
) {
    SectionCard(title = title, subtitle = subtitle) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            if (steps.isEmpty()) {
                Text(
                    "No steps",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            steps.forEachIndexed { i, step -> StepRow(section, i, step, steps.size, actions) }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                modifier = Modifier.padding(top = Spacing.sm),
            ) {
                listOf(ban(TeamSlot.A), pick(TeamSlot.A), ban(TeamSlot.B), pick(TeamSlot.B)).forEach { spec ->
                    AssistChip(
                        onClick = { actions.addStep(section, spec) },
                        label = { Text("${actionName(spec.action)} ${spec.team.name}") },
                        leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun StepRow(section: StepSection, index: Int, step: StepSpec, count: Int, actions: MethodEditActions) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, GoaTheme.colors.engravedBorder),
    ) {
        // FlowRow so the controls wrap instead of clipping at large font scales.
        FlowRow(
            Modifier.fillMaxWidth().padding(start = Spacing.sm),
            verticalArrangement = Arrangement.Center,
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${index + 1}.", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(28.dp))
            FilterChip(
                colors = goaFilterChipColors(),
                selected = step.action == DraftAction.BAN,
                onClick = { actions.toggleAction(section, index) },
                label = { Text(actionName(step.action), fontWeight = FontWeight.SemiBold) },
                modifier = Modifier.semantics { contentDescription = "${actionName(step.action)}, tap to switch" },
            )
            Spacer(Modifier.width(Spacing.xs))
            FilterChip(
                colors = goaFilterChipColors(),
                selected = step.team == TeamSlot.A,
                onClick = { actions.toggleTeam(section, index) },
                label = { Text("Team ${step.team.name}") },
                modifier = Modifier.semantics { contentDescription = "Team ${step.team.name}, tap to switch" },
            )
            Spacer(Modifier.weight(1f))
            StepRowButtons(section, index, step, count, actions)
        }
    }
}

@Composable
private fun StepRowButtons(section: StepSection, index: Int, step: StepSpec, count: Int, actions: MethodEditActions) {
    Row {
        IconButton(onClick = { actions.toggleHidden(section, index) }) {
            Icon(
                if (step.hidden) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
                contentDescription = if (step.hidden) "Hidden step, tap to open" else "Open step, tap to hide",
                tint = if (step.hidden) MaterialTheme.colorScheme.secondary else LocalContentColor.current,
            )
        }
        IconButton(onClick = { actions.moveStep(section, index, -1) }, enabled = index > 0) {
            Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "Move up")
        }
        IconButton(onClick = { actions.moveStep(section, index, 1) }, enabled = index < count - 1) {
            Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Move down")
        }
        IconButton(onClick = { actions.removeStep(section, index) }) {
            Icon(Icons.Outlined.Close, contentDescription = "Remove step ${index + 1}")
        }
    }
}

@Composable
private fun TimerCard(state: MethodEditUiState, actions: MethodEditActions) {
    SectionCard(
        title = "Step timer",
        subtitle = state.stepSeconds?.let { "$it seconds per step by default" } ?: "Off by default",
        trailing = {
            Switch(
                checked = state.stepSeconds != null,
                onCheckedChange = { actions.setStepSeconds(if (it) 30 else null) },
                modifier = Modifier.semantics { contentDescription = "Step timer" },
            )
        },
    ) {
        if (state.stepSeconds != null) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                listOf(15, 30, 45, 60, 90).forEach { s ->
                    FilterChip(
                        colors = goaFilterChipColors(),
                        selected = state.stepSeconds == s,
                        onClick = { actions.setStepSeconds(s) },
                        label = { Text("${s}s") },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewCard(state: MethodEditUiState, actions: MethodEditActions) {
    val preview = state.preview
    SectionCard(title = "Preview", subtitle = "The exact order for a seat layout") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            PreviewLayout.OPTIONS.forEach { layout ->
                FilterChip(
                    colors = goaFilterChipColors(),
                    selected = state.layout == layout,
                    onClick = { actions.setLayout(layout) },
                    label = { Text(layout.label) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
        Spacer(Modifier.width(Spacing.sm))
        if (preview.error != null) {
            Text(preview.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        } else {
            SequenceStrip(preview.tokens, modifier = Modifier.padding(top = Spacing.sm))
            if (preview.heroesNeeded > 0) {
                Text(
                    "Needs at least ${preview.heroesNeeded} heroes in the pool",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
            }
        }
    }
}
