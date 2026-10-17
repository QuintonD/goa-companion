package dev.quinton.goa.feature.timer.ui.timer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.GoaScreen
import dev.quinton.goa.core.designsystem.component.SettingsActionButton
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing

@Composable
internal fun TimerStart(state: StartUiState, actions: TimerActions) {
    GoaScreen(
        title = "Timer",
        actions = {
            IconButton(onClick = actions.onManagePresets) {
                Icon(Icons.Outlined.Tune, contentDescription = "Manage timer presets")
            }
            SettingsActionButton(actions.onSettings)
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item { ReadyCard(state, actions) }
            if (state.selected?.usesActionTimer == true) {
                item { SeatsStepper(state.seats, actions.onSeatsChange) }
            }
            if (state.pendingMatches.isNotEmpty()) {
                item { SectionHeader("Pending matches") }
                items(state.pendingMatches, key = { it.id }) { match ->
                    PendingMatchCard(match) { actions.onOpenSetup(match.id) }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionHeader("Presets", Modifier.weight(1f))
                    TextButton(onClick = actions.onManagePresets) { Text("Manage") }
                }
            }
            item {
                Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    state.presets.forEach { preset ->
                        PresetRadioCard(preset, selected = preset.id == state.selectedPresetId) {
                            actions.onSelectPreset(preset.id)
                        }
                    }
                }
            }
            item {
                Text(
                    "Timers are table rules. The rulebook sets no time limits; strategy talk is " +
                        "allowed from the end of one turn until cards are revealed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
            }
        }
    }
}

@Composable
private fun ReadyCard(state: StartUiState, actions: TimerActions) {
    val colors = MaterialTheme.colorScheme
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(1.dp, GoaTheme.colors.engravedBorder),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(listOf(colors.secondary.copy(alpha = 0.10f), colors.surface)),
                ),
        ) {
            Column(Modifier.padding(Spacing.xl)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .background(colors.secondary.copy(alpha = 0.16f), RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.HourglassTop, contentDescription = null, tint = colors.secondary)
                    }
                    Spacer(Modifier.width(Spacing.md))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Phase timer",
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.semantics { heading() },
                        )
                        Text(
                            state.selected?.name ?: "No preset",
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.primary,
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.md))
                Text(
                    state.selected?.summary.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.xl))
                Button(
                    onClick = actions.onStart,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(Spacing.sm))
                    Text("Start timer", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(Spacing.sm))
                OutlinedButton(
                    onClick = { actions.onOpenSetup(null) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(Icons.Outlined.Flag, contentDescription = null)
                    Spacer(Modifier.width(Spacing.sm))
                    Text("Match setup")
                }
                Text(
                    "Mode, counters to lay out and the Tie Breaker coin.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xs).align(Alignment.CenterHorizontally),
                )
            }
        }
    }
}

@Composable
private fun SeatsStepper(seats: Int, onChange: (Int) -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, GoaTheme.colors.engravedBorder),
    ) {
        Row(
            Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Heroes at the table", style = MaterialTheme.typography.titleSmall)
                Text(
                    "The Action timer runs once per hero each turn.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Stepper(
                value = seats,
                range = TimerViewModel.MIN_SEATS..TimerViewModel.MAX_SEATS,
                onChange = onChange,
                label = "heroes",
            )
        }
    }
}

/** − value + stepper with 48 dp targets; announces the value as "4 heroes". */
@Composable
internal fun Stepper(
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    step: Int = 1,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(
            onClick = { onChange((value - step).coerceAtLeast(range.first)) },
            enabled = value > range.first,
            modifier = Modifier.size(48.dp),
        ) { Icon(Icons.Filled.Remove, contentDescription = "Fewer $label") }
        Text(
            value.toString(),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier
                .width(48.dp)
                .semantics { stateDescription = "$value $label" },
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        FilledTonalIconButton(
            onClick = { onChange((value + step).coerceAtMost(range.last)) },
            enabled = value < range.last,
            modifier = Modifier.size(48.dp),
        ) { Icon(Icons.Filled.Add, contentDescription = "More $label") }
    }
}

@Composable
internal fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = Spacing.md).semantics { heading() },
    )
}

@Composable
private fun PendingMatchCard(match: PendingMatchItem, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, GoaTheme.colors.engravedBorder),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(match.title, style = MaterialTheme.typography.titleSmall)
                Text(
                    match.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("Set up", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun PresetRadioCard(preset: PresetOption, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (selected) colors.secondary.copy(alpha = 0.10f) else colors.surface,
        border = BorderStroke(
            if (selected) 1.5.dp else 1.dp,
            if (selected) colors.secondary else GoaTheme.colors.engravedBorder,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
    ) {
        Row(
            Modifier.heightIn(min = 64.dp).padding(horizontal = Spacing.sm, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(Spacing.sm))
            Column(Modifier.weight(1f).padding(end = Spacing.sm)) {
                Text(preset.name, style = MaterialTheme.typography.titleSmall)
                Text(preset.summary, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
    }
}
