package dev.quinton.goa.feature.timer.ui.timer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.BarAction
import dev.quinton.goa.core.designsystem.component.BarActions
import dev.quinton.goa.core.designsystem.component.GoaDialog
import dev.quinton.goa.core.designsystem.component.GoaOutlinedButton
import dev.quinton.goa.core.designsystem.component.GoaPlate
import dev.quinton.goa.core.designsystem.component.GoaScreen
import dev.quinton.goa.core.designsystem.component.MetalButton
import dev.quinton.goa.core.designsystem.component.PlateShape
import dev.quinton.goa.core.designsystem.component.SectionHeader
import dev.quinton.goa.core.designsystem.component.TeamBadge
import dev.quinton.goa.core.designsystem.component.TitlePlate
import dev.quinton.goa.core.designsystem.icon.GoaIcons
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.timer.TimerText
import dev.quinton.goa.feature.timer.ui.components.goaTonalIconColors
import dev.quinton.goa.feature.timer.ui.components.plateEdge
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
            item {
                SectionHeader(
                    "Presets",
                    trailing = { TextButton(onClick = actions.onManagePresets) { Text("Manage") } },
                )
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

/**
 * The start card reads as a start button: the preset and its durations (tonight's length in them), one bronze
 * Start timer, one row for the steps before it (Draft first · Match setup), and tonight's adjustments folded away.
 */
@Composable
private fun ReadyCard(state: StartUiState, actions: TimerActions) {
    GoaPlate(Modifier.fillMaxWidth(), contentPadding = PaddingValues(Spacing.lg)) {
        val colors = MaterialTheme.colorScheme
        TitlePlate("Phase timer", Modifier.fillMaxWidth())
        Spacer(Modifier.height(Spacing.md))
        Text(
            state.selected?.name ?: "No preset",
            style = MaterialTheme.typography.titleMedium,
            color = colors.primary,
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            tonightSummary(state.selected?.summary.orEmpty(), state.tonight, colors.primary),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
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
        Spacer(Modifier.height(Spacing.sm))
        // Game night runs draft, then Match setup, then the timer: both steps before it are one tap away.
        Row(
            Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            StartStepButton(
                painterResource(GoaIcons.NavDraft),
                "Draft first",
                actions.onOpenDraft,
                Modifier.weight(1f).fillMaxHeight(),
            )
            StartStepButton(
                rememberVectorPainter(Icons.Outlined.Flag),
                "Match setup",
                { actions.onOpenSetup(null) },
                Modifier.weight(1f).fillMaxHeight(),
            )
        }
        Text(
            "Match setup: mode, counters and the Tie Breaker coin.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.xs).fillMaxWidth(),
        )
        val seats = state.seats.takeIf { state.selected?.usesActionTimer == true }
        if (state.tonight != null || seats != null) {
            Spacer(Modifier.height(Spacing.md))
            AdjustForTonight(state.tonight, seats, actions)
        }
    }
}

/**
 * The preset's summary with tonight's length in it, named and marked: "Play a card 0:45 ×4 tonight" in bronze
 * where the preset says 0:30 (the preset's own value is in Adjust for tonight).
 */
internal fun tonightSummary(summary: String, tonight: TonightUi?, accent: Color): AnnotatedString {
    if (tonight == null || !tonight.adjusted) return AnnotatedString(summary)
    val presetPart = "${tonight.phaseName} ${TimerText.durationLabel(tonight.presetSeconds)}"
    val at = summary.indexOf(presetPart)
    if (at < 0) return AnnotatedString(summary)
    val end = summary.indexOf(SUMMARY_SEPARATOR, at).takeIf { it >= 0 } ?: summary.length
    // Kept on one line (no-break spaces): "Play a card 0:45 ×4 tonight" reads as one thing.
    val tonightPart = (
        "${tonight.phaseName} ${TimerText.durationLabel(tonight.seconds)}" +
            summary.substring(at + presetPart.length, end) + " tonight"
        ).replace(' ', NO_BREAK)
    return buildAnnotatedString {
        append(summary.substring(0, at))
        withStyle(SpanStyle(color = accent, fontWeight = FontWeight.SemiBold)) { append(tonightPart) }
        append(summary.substring(end))
    }
}

private const val SUMMARY_SEPARATOR = " · "
private const val NO_BREAK = ' '

@Composable
private fun StartStepButton(icon: Painter, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    GoaOutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 52.dp),
        contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = Spacing.xs),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(Spacing.sm))
        Text(label, textAlign = TextAlign.Center)
    }
}

/**
 * Tonight's changes, folded away under one row that still says what they are ("Play a card 0:45 (preset 0:30)
 * · 4 heroes"): the per-turn length (±15 s, the preset unchanged unless saved) and, when the preset times
 * each hero's Action, the heroes at the table.
 */
@Composable
private fun AdjustForTonight(tonight: TonightUi?, seats: Int?, actions: TimerActions) {
    var open by rememberSaveable { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val adjusted = tonight?.adjusted == true
    val values = listOfNotNull(
        tonight?.let {
            "${it.phaseName} ${TimerText.durationLabel(it.seconds)}" +
                if (it.adjusted) " (preset ${TimerText.durationLabel(it.presetSeconds)})" else ""
        },
        seats?.let { "$it heroes" },
    ).joinToString(SUMMARY_SEPARATOR)
    Column(
        Modifier
            .fillMaxWidth()
            .plateEdge(
                // Bronze: tonight's own choice of length (teal is kept for "next / ready").
                if (adjusted) colors.primary else GoaTheme.colors.engravedBorder,
                1.dp,
                MaterialTheme.shapes.medium,
            ),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .clickable(onClickLabel = if (open) "Hide" else "Adjust") { open = !open }
                .semantics { stateDescription = if (open) "Expanded" else "Collapsed" }
                .heightIn(min = 56.dp)
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Tune, contentDescription = null, tint = colors.primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text("Adjust for tonight", style = MaterialTheme.typography.titleSmall)
                Text(
                    values,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (adjusted) colors.primary else colors.onSurfaceVariant,
                )
            }
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.rotate(if (open) HALF_TURN else 0f),
            )
        }
        if (open) {
            Column(Modifier.padding(start = Spacing.md, end = Spacing.md, bottom = Spacing.sm)) {
                tonight?.let { TonightTurn(it, actions) }
                if (tonight != null && seats != null) {
                    HorizontalDivider(
                        Modifier.padding(vertical = Spacing.sm),
                        color = GoaTheme.colors.engravedBorder,
                    )
                }
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

/** A pending match: its heroes per team and when it was set up, with Set up (the card) and Discard. */
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { confirm = true }) { Text("Discard") }
                Spacer(Modifier.weight(1f))
                Text("Set up", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = Spacing.sm),
                )
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

@Composable
private fun PresetRadioCard(preset: PresetOption, selected: Boolean, onClick: () -> Unit) {
    GoaPlate(
        Modifier
            .fillMaxWidth()
            .clip(PlateShape)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        contentPadding = PaddingValues(0.dp),
        selected = selected,
    ) {
        val colors = MaterialTheme.colorScheme
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
