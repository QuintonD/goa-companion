package dev.quinton.goa.feature.timer.ui.timer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.BarAction
import dev.quinton.goa.core.designsystem.component.BarActions
import dev.quinton.goa.core.designsystem.component.GoaDialog
import dev.quinton.goa.core.designsystem.component.GoaPlate
import dev.quinton.goa.core.designsystem.component.GoaScreen
import dev.quinton.goa.core.designsystem.component.MetalButton
import dev.quinton.goa.core.designsystem.component.PlateRowDivider
import dev.quinton.goa.core.designsystem.component.SectionHeader
import dev.quinton.goa.core.designsystem.component.TeamBadge
import dev.quinton.goa.core.designsystem.component.TitlePlate
import dev.quinton.goa.core.designsystem.icon.Destination
import dev.quinton.goa.core.designsystem.icon.icon
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.timer.TimerText
import dev.quinton.goa.feature.timer.ui.components.PhaseGrid
import dev.quinton.goa.feature.timer.ui.components.goaTonalIconColors
import dev.quinton.goa.feature.timer.ui.components.timerDigits

@Composable
internal fun TimerStart(state: StartUiState, actions: TimerActions) {
    GoaScreen(
        title = "Timer",
        actions = {
            BarActions(
                listOf(
                    BarAction("Manage timer presets", Icons.Outlined.Tune, actions.onManagePresets),
                    BarAction("Settings", Icons.Outlined.Settings, actions.onSettings),
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item { ReadyCard(state, actions) }
            item { StepsBefore(actions) }
            if (state.pendingMatches.isNotEmpty()) {
                item { SectionHeader("Pending matches") }
                items(state.pendingMatches, key = { it.id }) { match ->
                    PendingMatchCard(
                        match,
                        onClick = { actions.onOpenSetup(match.id) },
                        onDiscard = { actions.onDiscardPending(match.id) },
                    )
                }
            }
        }
    }
}

/**
 * The start card reads as a start button: the preset (one row that opens the presets), its phases as a labelled
 * grid with tonight's length marked in it, tonight's adjustments folded away, and one bronze Start timer, the
 * card's only button.
 */
@Composable
private fun ReadyCard(state: StartUiState, actions: TimerActions) {
    GoaPlate(Modifier.fillMaxWidth(), contentPadding = PaddingValues(Spacing.lg)) {
        TitlePlate("Phase timer", Modifier.fillMaxWidth(), gear = false)
        Spacer(Modifier.height(Spacing.sm))
        PresetRow(state.selected, actions.onManagePresets)
        PlateRowDivider()
        val selected = state.selected
        if (selected != null && selected.phases.isNotEmpty()) {
            PhaseGrid(selected.phases, Modifier.fillMaxWidth(), tonight = state.tonight)
        } else {
            Text(
                selected?.summary.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = Spacing.sm),
            )
        }
        val seats = state.seats.takeIf { selected?.usesActionTimer == true }
        if (state.tonight != null || seats != null) {
            PlateRowDivider()
            AdjustForTonight(state.tonight, seats, actions)
            PlateRowDivider()
        }
        Spacer(Modifier.height(Spacing.lg))
        MetalButton(
            onClick = actions.onStart,
            bronze = true,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(Spacing.sm))
            Text("Start timer", style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** The active preset as one tappable row: its name, and Change opens the presets to pick or edit another. */
@Composable
private fun PresetRow(preset: PresetOption?, onOpen: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClickLabel = "Change preset", role = Role.Button, onClick = onOpen)
            .heightIn(min = 48.dp)
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Preset", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            Text(preset?.name ?: "No preset", style = MaterialTheme.typography.titleMedium, color = colors.primary)
        }
        Text("Change", style = MaterialTheme.typography.labelLarge, color = colors.primary)
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.primary,
        )
    }
}

/**
 * The steps a game night takes before the timer (draft → Match setup → timer), as a quiet row under the start
 * card: text buttons, so Start timer stays the one obvious action.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StepsBefore(actions: TimerActions) {
    // Side by side; one under the other where large text has no room for both.
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        StepButton(Destination.DRAFT.icon, "Draft first", actions.onOpenDraft)
        StepButton(Destination.MATCH_SETUP.icon, Destination.MATCH_SETUP.label, { actions.onOpenSetup(null) })
    }
}

@Composable
private fun StepButton(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = Spacing.xs),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(Spacing.sm))
        Text(label, textAlign = TextAlign.Center)
    }
}

private const val SUMMARY_SEPARATOR = " · "

/**
 * Tonight's changes, folded away under one row that names what it changes ("Play a card length · 4 heroes"; the
 * grid above shows the values): the per-turn length (±15 s, the preset unchanged unless saved) and, when the
 * preset times each hero's Action, the heroes at the table. A row of the start plate between hairlines, with no
 * frame of its own (DESIGN.md "Framing levels").
 */
@Composable
private fun AdjustForTonight(tonight: TonightUi?, seats: Int?, actions: TimerActions) {
    var open by rememberSaveable { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val values = listOfNotNull(
        tonight?.let { "${it.phaseName} length" },
        seats?.let { "$it heroes" },
    ).joinToString(SUMMARY_SEPARATOR)
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .clickable(onClickLabel = if (open) "Hide" else "Adjust") { open = !open }
                .semantics { stateDescription = if (open) "Expanded" else "Collapsed" }
                .heightIn(min = 56.dp)
                .padding(vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Tune, contentDescription = null, tint = colors.primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text("Adjust for tonight", style = MaterialTheme.typography.titleSmall)
                Text(values, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.rotate(if (open) HALF_TURN else 0f),
            )
        }
        if (open) {
            Column(Modifier.padding(bottom = Spacing.sm)) {
                tonight?.let { TonightTurn(it, actions) }
                if (tonight != null && seats != null) PlateRowDivider(Modifier.padding(vertical = Spacing.sm))
                seats?.let { SeatsStepper(it, actions.onSeatsChange) }
            }
        }
    }
}

private const val HALF_TURN = 180f

/** The per-turn phase's length for tonight in one tap (±15 s), named as the preset names it. */
@Composable
private fun TonightTurn(tonight: TonightUi, actions: TimerActions) {
    val colors = MaterialTheme.colorScheme
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tonight.phaseName, style = MaterialTheme.typography.titleSmall)
                Text(
                    if (tonight.adjusted) {
                        "Each turn · preset ${TimerText.durationLabel(tonight.presetSeconds)}"
                    } else {
                        "Each turn · from the preset"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
            FilledTonalIconButton(
                onClick = { actions.onTonightTurn(tonight.seconds - TimerViewModel.TONIGHT_STEP_SECONDS) },
                enabled = tonight.seconds > TimerViewModel.TONIGHT_MIN_SECONDS,
                modifier = Modifier.size(48.dp),
                colors = goaTonalIconColors(),
            ) { Icon(Icons.Filled.Remove, contentDescription = "${tonight.phaseName} 15 seconds shorter") }
            Text(
                TimerText.durationLabel(tonight.seconds),
                style = timerDigits(MaterialTheme.typography.titleLarge),
                textAlign = TextAlign.Center,
                color = if (tonight.adjusted) colors.primary else colors.onSurface,
                modifier = Modifier
                    .width(64.dp)
                    .semantics {
                        stateDescription =
                            "${tonight.phaseName} ${TimerText.spokenDuration(tonight.seconds.toLong())} tonight"
                    },
            )
            FilledTonalIconButton(
                onClick = { actions.onTonightTurn(tonight.seconds + TimerViewModel.TONIGHT_STEP_SECONDS) },
                enabled = tonight.seconds < TimerViewModel.TONIGHT_MAX_SECONDS,
                modifier = Modifier.size(48.dp),
                colors = goaTonalIconColors(),
            ) { Icon(Icons.Filled.Add, contentDescription = "${tonight.phaseName} 15 seconds longer") }
        }
        if (tonight.adjusted) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Just for tonight.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = { actions.onTonightTurn(tonight.presetSeconds) },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("Reset") }
                TextButton(onClick = actions.onSaveTonight, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Save to preset")
                }
            }
        }
    }
}

@Composable
private fun SeatsStepper(seats: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
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
            colors = goaTonalIconColors(),
        ) { Icon(Icons.Filled.Remove, contentDescription = "Fewer $label") }
        Text(
            value.toString(),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier
                .width(48.dp)
                .semantics { stateDescription = "$value $label" },
            textAlign = TextAlign.Center,
        )
        FilledTonalIconButton(
            onClick = { onChange((value + step).coerceAtMost(range.last)) },
            enabled = value < range.last,
            modifier = Modifier.size(48.dp),
            colors = goaTonalIconColors(),
        ) { Icon(Icons.Filled.Add, contentDescription = "More $label") }
    }
}

/**
 * A pending match: its heroes per team and when it was set up, with "Open match setup" (the card, and its trailing
 * button) and Discard, both 48 dp text buttons with their icon.
 */
@Composable
private fun PendingMatchCard(match: PendingMatchItem, onClick: () -> Unit, onDiscard: () -> Unit) {
    var confirm by rememberSaveable(match.id) { mutableStateOf(false) }
    GoaPlate(Modifier.fillMaxWidth(), onClick = onClick, contentPadding = PaddingValues(0.dp)) {
        Column(Modifier.fillMaxWidth().padding(start = Spacing.lg, end = Spacing.sm, top = Spacing.md)) {
            Text(match.title, style = MaterialTheme.typography.titleSmall)
            match.red?.let { TeamHeroesLine(Team.RED, it) }
            match.blue?.let { TeamHeroesLine(Team.BLUE, it) }
            Text(
                match.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = { confirm = true },
                    modifier = Modifier.heightIn(min = 48.dp),
                    contentPadding = PaddingValues(horizontal = Spacing.sm),
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Spacing.xs))
                    Text("Discard")
                }
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = onClick,
                    modifier = Modifier.heightIn(min = 48.dp),
                    contentPadding = PaddingValues(horizontal = Spacing.sm),
                ) {
                    Icon(Destination.MATCH_SETUP.icon, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Spacing.xs))
                    Text("Open match setup")
                }
            }
        }
    }
    if (confirm) {
        GoaDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Discard this pending match?") },
            text = { Text("It leaves this list and the match log. Matches with a result aren't affected.") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    onDiscard()
                }) { Text("Discard") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Keep") } },
        )
    }
}

@Composable
private fun TeamHeroesLine(team: Team, heroes: String) {
    val name = if (team == Team.RED) "Red" else "Blue"
    Row(
        Modifier.padding(top = Spacing.xs).semantics(mergeDescendants = true) {
            contentDescription = "$name: $heroes"
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TeamBadge(team)
        Spacer(Modifier.width(Spacing.sm))
        Text(heroes, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}
