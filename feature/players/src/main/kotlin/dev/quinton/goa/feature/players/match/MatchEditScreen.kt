package dev.quinton.goa.feature.players.match

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.EmptyState
import dev.quinton.goa.core.designsystem.component.GoaScreen
import dev.quinton.goa.core.designsystem.component.HeroPortrait
import dev.quinton.goa.core.designsystem.component.PlayerAvatar
import dev.quinton.goa.core.designsystem.component.TeamBadge
import dev.quinton.goa.core.designsystem.component.teamColor
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.match.WinCondition
import dev.quinton.goa.feature.players.common.Dates
import dev.quinton.goa.feature.players.common.LARGE_FONT_SCALE
import dev.quinton.goa.feature.players.common.LoadingState
import dev.quinton.goa.feature.players.common.Motion
import dev.quinton.goa.feature.players.common.Pill
import dev.quinton.goa.feature.players.common.SectionHeader
import dev.quinton.goa.feature.players.common.goaFilterChipColors
import dev.quinton.goa.feature.players.common.goaSegmentedColors
import dev.quinton.goa.feature.players.common.label
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

data class MatchEditActions(
    val onBack: () -> Unit = {},
    val onPlayedAt: (Long) -> Unit = {},
    val onMode: (GameMode) -> Unit = {},
    val onMap: (String) -> Unit = {},
    val onStatus: (MatchStatus) -> Unit = {},
    val onWinner: (Team) -> Unit = {},
    val onWinCondition: (WinCondition?) -> Unit = {},
    val onDuration: (String) -> Unit = {},
    val onNotes: (String) -> Unit = {},
    val onAddSeat: (Team) -> Unit = {},
    val onRemoveSeat: (Team, Long) -> Unit = { _, _ -> },
    val onPilot: (Team, Long, String) -> Unit = { _, _, _ -> },
    val onHero: (Team, Long, String?) -> Unit = { _, _, _ -> },
    val onToggleHandicap: (Team, Long) -> Unit = { _, _ -> },
    val onSwapTeams: () -> Unit = {},
    val onSave: () -> Unit = {},
    val onDelete: () -> Unit = {},
)

/** Which picker sheet is open. */
private sealed interface Picker {
    data class HeroFor(val team: Team, val key: Long) : Picker

    data class PilotFor(val team: Team, val key: Long) : Picker
}

/** Map suggestions: the rulebook names no maps, only the board's two sides (DOMAIN.md). */
private val MapSuggestions = listOf("Single lane", "Double lane")

@Composable
fun MatchEditScreen(state: MatchEditUiState, actions: MatchEditActions, modifier: Modifier = Modifier) {
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var picker by remember { mutableStateOf<Picker?>(null) }
    LaunchedEffect(state.finished) { if (state.finished) actions.onBack() }
    val requestBack = { if (state.dirty) confirmDiscard = true else actions.onBack() }
    BackHandler(enabled = state.dirty && !state.finished) { confirmDiscard = true }

    GoaScreen(
        title = if (state.isNew) "Log match" else "Edit match",
        modifier = modifier,
        onBack = requestBack,
        actions = {
            if (!state.loading && !state.notFound) {
                TextButton(onClick = actions.onSave, enabled = !state.saving) { Text("Save") }
            }
        },
    ) { padding ->
        when {
            state.loading -> LoadingState(Modifier.padding(padding))

            state.notFound -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyState(Icons.Outlined.EventBusy, "Match not found", "It may have been deleted.")
            }

            else -> MatchFormContent(
                state = state,
                actions = actions,
                onPickHero = { team, key -> picker = Picker.HeroFor(team, key) },
                onPickPilot = { team, key -> picker = Picker.PilotFor(team, key) },
                onDelete = { confirmDelete = true },
                modifier = Modifier.padding(padding),
            )
        }
    }

    when (val open = picker) {
        is Picker.HeroFor -> {
            val seat = state.form.seats(open.team).firstOrNull { it.key == open.key }
            HeroPickerSheet(
                heroes = state.heroes,
                selected = seat?.heroId,
                taken = state.form.heroesInUse().toSet() - setOfNotNull(seat?.heroId),
                onPick = { heroId ->
                    actions.onHero(open.team, open.key, heroId)
                    picker = null
                },
                onDismiss = { picker = null },
            )
        }

        is Picker.PilotFor -> {
            val seat = state.form.seats(open.team).firstOrNull { it.key == open.key }
            PilotPickerSheet(
                team = open.team,
                pilots = state.pilots,
                selected = seat?.playerId,
                sameTeam = state.form.seats(open.team).filter { it.key != open.key }.mapNotNull { it.playerId }.toSet(),
                otherTeam = state.form.seats(open.team.opponent).mapNotNull { it.playerId }.toSet(),
                onPick = { playerId ->
                    actions.onPilot(open.team, open.key, playerId)
                    picker = null
                },
                onDismiss = { picker = null },
            )
        }

        null -> Unit
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard changes?") },
            text = { Text("Your edits to this match haven't been saved.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    actions.onBack()
                }) { Text("Discard") }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
            title = { Text("Delete this match?") },
            text = { Text("All ratings are recalculated without it. You can undo from the match log.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    actions.onDelete()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun MatchFormContent(
    state: MatchEditUiState,
    actions: MatchEditActions,
    onPickHero: (Team, Long) -> Unit,
    onPickPilot: (Team, Long) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val form = state.form
    Column(
        modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen)
            .padding(bottom = Spacing.xxl),
    ) {
        AnimatedVisibility(
            visible = state.showErrors && state.errors.isNotEmpty(),
            enter = fadeIn(Motion.spec()) + expandVertically(Motion.spec()),
            exit = fadeOut(Motion.spec()) + shrinkVertically(Motion.spec()),
        ) {
            ErrorCard(state.errors.map { it.message })
        }
        if (state.fromDraft) {
            Text(
                "Created from a draft. Fill in the result when the game ends.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.md),
            )
        }

        SectionHeader("When and where")
        WhenRow(form.playedAt, actions.onPlayedAt)
        Spacer(Modifier.size(Spacing.md))
        ChoiceRow(
            options = GameMode.entries,
            selected = form.mode,
            label = { it.label() },
            onSelect = actions.onMode,
        )
        Spacer(Modifier.size(Spacing.md))
        OutlinedTextField(
            value = form.map,
            onValueChange = actions.onMap,
            label = { Text("Map (optional)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            MapSuggestions.forEach { suggestion ->
                AssistChip(onClick = { actions.onMap(suggestion) }, label = { Text(suggestion) })
            }
        }

        SectionHeader(
            "Teams",
            trailing = {
                TextButton(onClick = actions.onSwapTeams) {
                    Icon(Icons.Outlined.SwapVert, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Spacing.xs))
                    Text("Swap")
                }
            },
        )
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val sideBySide = maxWidth >= 600.dp && LocalDensity.current.fontScale < 1.5f
            if (sideBySide) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                    TeamEditor(Team.RED, state, actions, onPickHero, onPickPilot, Modifier.weight(1f))
                    TeamEditor(Team.BLUE, state, actions, onPickHero, onPickPilot, Modifier.weight(1f))
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                    TeamEditor(Team.RED, state, actions, onPickHero, onPickPilot)
                    TeamEditor(Team.BLUE, state, actions, onPickHero, onPickPilot)
                }
            }
        }

        SectionHeader("Result")
        ChoiceRow(
            options = listOf(MatchStatus.COMPLETED, MatchStatus.PENDING, MatchStatus.ABANDONED),
            selected = form.status,
            label = { it.label() },
            onSelect = actions.onStatus,
        )
        AnimatedVisibility(
            visible = form.status == MatchStatus.COMPLETED,
            enter = fadeIn(Motion.spec()) + expandVertically(Motion.spec()),
            exit = fadeOut(Motion.spec()) + shrinkVertically(Motion.spec()),
        ) {
            Column {
                Text(
                    "Winner",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = Spacing.lg, bottom = Spacing.sm),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Team.entries.forEach { team ->
                        WinnerOption(team, selected = form.winner == team, onClick = {
                            actions.onWinner(team)
                        }, Modifier.weight(1f))
                    }
                }
                Text(
                    "Win condition",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = Spacing.lg, bottom = Spacing.xs),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    WinCondition.entries.forEach { condition ->
                        val selected = form.winCondition == condition
                        FilterChip(
                            selected = selected,
                            onClick = { actions.onWinCondition(if (selected) null else condition) },
                            label = { Text(condition.label()) },
                            leadingIcon = if (selected) {
                                { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            } else {
                                null
                            },
                            colors = goaFilterChipColors(),
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.size(Spacing.md))
        OutlinedTextField(
            value = form.duration,
            onValueChange = actions.onDuration,
            label = { Text("Duration (minutes, optional)") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Outlined.Schedule, contentDescription = null) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.size(Spacing.md))
        OutlinedTextField(
            value = form.notes,
            onValueChange = actions.onNotes,
            label = { Text("Notes") },
            minLines = 2,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.size(Spacing.xl))
        Button(
            onClick = actions.onSave,
            enabled = !state.saving,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) {
            Text(if (state.isNew) "Save match" else "Save changes")
        }
        if (!state.isNew) {
            TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)) {
                Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(Spacing.sm))
                Text("Delete match", color = MaterialTheme.colorScheme.error)
            }
        }
        Text(
            "Ratings recompute from the whole match log whenever a match is saved, edited or deleted.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.md),
        )
    }
}

@Composable
private fun ErrorCard(messages: List<String>) {
    Surface(
        color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
    ) {
        Row(Modifier.padding(Spacing.md)) {
            Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(Spacing.md))
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text("Can't save yet", style = MaterialTheme.typography.titleSmall)
                messages.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WhenRow(playedAt: Long, onPlayedAt: (Long) -> Unit) {
    var pickDate by rememberSaveable { mutableStateOf(false) }
    var pickTime by rememberSaveable { mutableStateOf(false) }
    val zone = ZoneId.systemDefault()
    val dateTime = Instant.ofEpochMilli(playedAt).atZone(zone)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        AssistChip(
            onClick = { pickDate = true },
            label = { Text(Dates.day(playedAt)) },
            leadingIcon = { Icon(Icons.Outlined.CalendarMonth, contentDescription = null) },
            modifier = Modifier.heightIn(min = 48.dp).semantics {
                contentDescription =
                    "Date ${Dates.day(playedAt)}. Change"
            },
        )
        AssistChip(
            onClick = { pickTime = true },
            label = { Text(Dates.time(playedAt)) },
            leadingIcon = { Icon(Icons.Outlined.Schedule, contentDescription = null) },
            modifier = Modifier.heightIn(min = 48.dp).semantics {
                contentDescription =
                    "Time ${Dates.time(playedAt)}. Change"
            },
        )
    }
    if (pickDate) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = dateTime.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { utc ->
                        val date = Instant.ofEpochMilli(utc).atZone(ZoneOffset.UTC).toLocalDate()
                        onPlayedAt(date.atTime(dateTime.toLocalTime()).atZone(zone).toInstant().toEpochMilli())
                    }
                    pickDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Cancel") } },
        ) { DatePicker(state = pickerState) }
    }
    if (pickTime) {
        val timeState = rememberTimePickerState(initialHour = dateTime.hour, initialMinute = dateTime.minute)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            confirmButton = {
                TextButton(onClick = {
                    val date: LocalDate = dateTime.toLocalDate()
                    onPlayedAt(
                        date.atTime(
                            LocalTime.of(timeState.hour, timeState.minute),
                        ).atZone(zone).toInstant().toEpochMilli(),
                    )
                    pickTime = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text("Cancel") } },
            text = { TimePicker(state = timeState) },
        )
    }
}

@Composable
private fun TeamEditor(
    team: Team,
    state: MatchEditUiState,
    actions: MatchEditActions,
    onPickHero: (Team, Long) -> Unit,
    onPickPilot: (Team, Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val seats = state.form.seats(team)
    val doublePilots = state.form.doublePilots(team)
    val duplicates = state.form.heroesInUse().groupingBy { it }.eachCount().filterValues { it > 1 }.keys
    val otherTeam = state.form.seats(team.opponent).mapNotNull { it.playerId }.toSet()
    Column(modifier.animateContentSize(Motion.spec()), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TeamBadge(team)
            Text(
                "  ·  ${seats.size} ${if (seats.size == 1) "seat" else "seats"}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { actions.onAddSeat(team) }, enabled = seats.size < MAX_SEATS_PER_TEAM) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Spacing.xs))
                Text("Add seat")
            }
        }
        if (seats.isEmpty()) {
            Text(
                "No seats. Add at least one hero for ${team.label()}.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        seats.forEach { seat ->
            SeatEditor(
                team = team,
                seat = seat,
                state = state,
                doublePilot = seat.playerId != null && seat.playerId in doublePilots,
                problem = when {
                    state.showErrors && seat.playerId == null -> "Choose a pilot"
                    seat.heroId != null && seat.heroId in duplicates -> "Hero picked twice"
                    seat.playerId != null && seat.playerId in otherTeam -> "Also on ${team.opponent.label()}"
                    else -> null
                },
                onPickHero = { onPickHero(team, seat.key) },
                onPickPilot = { onPickPilot(team, seat.key) },
                onToggleHandicap = { actions.onToggleHandicap(team, seat.key) },
                onRemove = { actions.onRemoveSeat(team, seat.key) },
            )
        }
    }
}

@Composable
private fun SeatEditor(
    team: Team,
    seat: SeatDraft,
    state: MatchEditUiState,
    doublePilot: Boolean,
    problem: String?,
    onPickHero: () -> Unit,
    onPickPilot: () -> Unit,
    onToggleHandicap: () -> Unit,
    onRemove: () -> Unit,
) {
    val hero = state.heroOption(seat.heroId)
    val heroLabel = if (seat.heroId == null) "Unknown hero" else state.heroName(seat.heroId)
    val pilot = seat.playerId?.let { state.playersById[it] }
    val accent = teamColor(team)
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (problem != null) {
                MaterialTheme.colorScheme.error
            } else {
                accent.copy(alpha = 0.45f)
            },
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = Spacing.md, top = Spacing.sm, bottom = Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp)
                        .clickable(onClickLabel = "Choose hero", role = Role.Button, onClick = onPickHero)
                        .semantics(mergeDescendants = true) { contentDescription = "Hero: $heroLabel. Change hero" },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HeroPortrait(
                        seat.heroId ?: "?",
                        hero?.portrait,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(Modifier.width(Spacing.md))
                    Column {
                        Text(
                            heroLabel,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (seat.heroId == null) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                        Text(
                            hero?.title ?: "Tap to choose",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Outlined.Close, contentDescription = "Remove seat")
                }
            }
            FlowRow(
                Modifier.padding(top = Spacing.xs, end = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                AssistChip(
                    onClick = onPickPilot,
                    label = { Text(pilot?.name ?: "Choose pilot", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = {
                        if (pilot != null) {
                            PlayerAvatar(pilot, size = 24.dp)
                        } else {
                            Icon(Icons.Outlined.People, contentDescription = null)
                        }
                    },
                    modifier = Modifier.heightIn(min = 48.dp).semantics {
                        contentDescription = "Pilot: ${pilot?.name ?: "none"}. Change pilot"
                    },
                )
                FilterChip(
                    selected = seat.handicap,
                    onClick = onToggleHandicap,
                    label = { Text("Handicap") },
                    leadingIcon = if (seat.handicap) {
                        { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else {
                        null
                    },
                    colors = goaFilterChipColors(),
                    modifier = Modifier.heightIn(min = 48.dp),
                )
                if (doublePilot) {
                    Pill(
                        "Double pilot",
                        icon = Icons.Outlined.People,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
            if (problem != null) {
                Text(
                    problem,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
        }
    }
}

@Composable
private fun WinnerOption(team: Team, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val color = teamColor(team)
    Surface(
        modifier = modifier
            .heightIn(min = 64.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = "${team.label()} won" },
        shape = MaterialTheme.shapes.medium,
        color = if (selected) color.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) color else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(
            Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            TeamBadge(team)
            Text(" won", style = MaterialTheme.typography.labelLarge, color = color, fontWeight = FontWeight.SemiBold)
            if (selected) {
                Spacer(Modifier.width(Spacing.sm))
                Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** Single choice: a segmented row, or wrapping chips when the font is large. */
@Composable
private fun <T> ChoiceRow(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    if (LocalDensity.current.fontScale >= LARGE_FONT_SCALE) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            options.forEach { option ->
                val isSelected = option == selected
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelect(option) },
                    label = { Text(label(option)) },
                    leadingIcon = if (isSelected) {
                        { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else {
                        null
                    },
                    colors = goaFilterChipColors(),
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
    } else {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, option ->
                SegmentedButton(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    shape = SegmentedButtonDefaults.itemShape(i, options.size),
                    colors = goaSegmentedColors(),
                ) { Text(label(option), maxLines = 1) }
            }
        }
    }
}
