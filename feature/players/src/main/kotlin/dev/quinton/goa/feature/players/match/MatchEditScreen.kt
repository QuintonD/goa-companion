package dev.quinton.goa.feature.players.match

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.ChipIconSize
import dev.quinton.goa.core.designsystem.component.EmptyState
import dev.quinton.goa.core.designsystem.component.GoaButton
import dev.quinton.goa.core.designsystem.component.GoaDialog
import dev.quinton.goa.core.designsystem.component.GoaFilterChip
import dev.quinton.goa.core.designsystem.component.GoaOutlinedButton
import dev.quinton.goa.core.designsystem.component.GoaPlate
import dev.quinton.goa.core.designsystem.component.GoaPlateTheme
import dev.quinton.goa.core.designsystem.component.GoaScreen
import dev.quinton.goa.core.designsystem.component.GoaSegment
import dev.quinton.goa.core.designsystem.component.GoaSegmentedRow
import dev.quinton.goa.core.designsystem.component.HeroPortrait
import dev.quinton.goa.core.designsystem.component.PlateRowDivider
import dev.quinton.goa.core.designsystem.component.PlateShape
import dev.quinton.goa.core.designsystem.component.SelectablePlate
import dev.quinton.goa.core.designsystem.component.SelectionMark
import dev.quinton.goa.core.designsystem.component.TeamBadge
import dev.quinton.goa.core.designsystem.component.scaledIconSize
import dev.quinton.goa.core.designsystem.component.slatePlate
import dev.quinton.goa.core.designsystem.component.teamColor
import dev.quinton.goa.core.designsystem.component.teamTextColor
import dev.quinton.goa.core.designsystem.icon.Destination
import dev.quinton.goa.core.designsystem.icon.icon
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.domain.match.RematchKind
import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.match.WinCondition
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.feature.players.common.Dates
import dev.quinton.goa.feature.players.common.EdgedPlate
import dev.quinton.goa.feature.players.common.LARGE_FONT_SCALE
import dev.quinton.goa.feature.players.common.LoadingState
import dev.quinton.goa.feature.players.common.Motion
import dev.quinton.goa.feature.players.common.SectionHeader
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
    /** Adds a player by name from the pilot picker and seats them. */
    val onAddPilot: (Team, Long, String) -> Unit = { _, _, _ -> },
    /** null = draft again. */
    val onRematch: (RematchKind?) -> Unit = {},
    /** Opens these heroes (per team, seat order) on a new board. */
    val onOpenBoard: (red: List<String>, blue: List<String>) -> Unit = { _, _ -> },
)

/** Which picker sheet is open. */
private sealed interface Picker {
    data class HeroFor(val team: Team, val key: Long) : Picker

    data class PilotFor(val team: Team, val key: Long) : Picker
}

@Composable
fun MatchEditScreen(state: MatchEditUiState, actions: MatchEditActions, modifier: Modifier = Modifier) {
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var picker by remember { mutableStateOf<Picker?>(null) }
    LaunchedEffect(state.finished) { if (state.finished) actions.onBack() }
    val saved = state.saved
    val unsaved = (state.dirty || state.unsavedResult) && saved == null
    val requestBack = { if (unsaved) confirmDiscard = true else actions.onBack() }
    BackHandler(enabled = unsaved && !state.finished) { confirmDiscard = true }

    GoaScreen(
        title = when {
            saved != null -> "Result saved"
            state.resultFirst -> "Who won?"
            state.isNew -> "Log match"
            else -> "Edit match"
        },
        modifier = modifier,
        onBack = requestBack,
        // No Save in the banner: the form ends with the bronze Save pinned under it, as every flow does.
    ) { padding ->
        when {
            state.loading -> LoadingState(Modifier.padding(padding))

            state.notFound -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyState(Icons.Outlined.EventBusy, "Match not found", "It may have been deleted.")
            }

            saved != null -> ResultSavedContent(
                saved,
                onDone = actions.onBack,
                onRematch = actions.onRematch,
                modifier = Modifier.padding(padding),
            )

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
                onAddPlayer = { name ->
                    actions.onAddPilot(open.team, open.key, name)
                    picker = null
                },
                onDismiss = { picker = null },
            )
        }

        null -> Unit
    }

    if (confirmDiscard) {
        val words = MatchEditing.leaveDialog(state.unsavedResult)
        GoaDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(words.title) },
            text = { Text(words.body) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    actions.onBack()
                }) { Text(words.leave) }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(words.keep) } },
        )
    }
    if (confirmDelete) {
        GoaDialog(
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
    val scroll = rememberScrollState()
    // A failed save shows the error card at the top: bring it into view (the Save button is at the bottom).
    LaunchedEffect(state.saveAttempts) { if (state.saveAttempts > 0) scroll.animateScrollTo(0) }
    Column(modifier.fillMaxSize().imePadding()) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scroll)
                .padding(horizontal = Spacing.screen)
                .padding(bottom = Spacing.xl),
        ) {
            MatchFormFields(state, actions, onPickHero, onPickPilot, onDelete)
        }
        SaveBar(state, actions)
    }
}

/**
 * The bronze Save, pinned under the form so it is always in reach: it says what it saves ("Save result") and
 * stays off until the form can be saved, with the reason under it ("Choose the winning team…").
 */
@Composable
private fun SaveBar(state: MatchEditUiState, actions: MatchEditActions) {
    val form = state.form
    val reason = state.errors.firstOrNull()?.message?.let { first ->
        if (state.errors.size > 1) "$first (and ${state.errors.size - 1} more)" else first
    }
    GoaPlateTheme {
        Column(
            Modifier
                .fillMaxWidth()
                .slatePlate(RectangleShape, brackets = false)
                .navigationBarsPadding()
                .padding(horizontal = Spacing.screen, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            GoaButton(
                onClick = actions.onSave,
                enabled = !state.saving && reason == null,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) {
                Text(
                    when {
                        state.resultFirst && form.status == MatchStatus.COMPLETED -> "Save result"
                        state.isNew -> "Save match"
                        else -> "Save changes"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (reason != null) {
                Text(
                    reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.MatchFormFields(
    state: MatchEditUiState,
    actions: MatchEditActions,
    onPickHero: (Team, Long) -> Unit,
    onPickPilot: (Team, Long) -> Unit,
    onDelete: () -> Unit,
) {
    val form = state.form
    AnimatedVisibility(
        visible = state.showErrors && state.errors.isNotEmpty(),
        enter = fadeIn(Motion.spec()) + expandVertically(Motion.spec()),
        exit = fadeOut(Motion.spec()) + shrinkVertically(Motion.spec()),
    ) {
        ErrorCard(state.errors.map { it.message })
    }
    if (state.resultFirst) {
        // "Who won?": the two answers the table has right now, then both line-ups as short rosters (a seat opens
        // only to be edited), then the optional rest folded under labelled rows.
        WinnerAndCondition(state, actions, showHeader = false)
        TeamsSection(state, actions, onPickHero, onPickPilot)
        WhenSection(form, actions)
        DetailsSection(form, actions)
        FoldSection("Match status", form.status.label()) { StatusChoice(form, actions) }
    } else {
        TeamsSection(state, actions, onPickHero, onPickPilot)
        SectionHeader("Result")
        StatusChoice(form, actions)
        AnimatedVisibility(
            visible = form.status == MatchStatus.COMPLETED,
            enter = fadeIn(Motion.spec()) + expandVertically(Motion.spec()),
            exit = fadeOut(Motion.spec()) + shrinkVertically(Motion.spec()),
        ) {
            WinnerAndCondition(state, actions, showHeader = true)
        }
        WhenSection(form, actions)
        DetailsSection(form, actions)
    }
    val boardRed = form.seats(Team.RED).mapNotNull { it.heroId }
    val boardBlue = form.seats(Team.BLUE).mapNotNull { it.heroId }
    if (!state.isNew && (boardRed.isNotEmpty() || boardBlue.isNotEmpty())) {
        GoaOutlinedButton(
            onClick = { actions.onOpenBoard(boardRed, boardBlue) },
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg).heightIn(min = 52.dp),
        ) {
            Icon(Destination.BOARD.icon, contentDescription = null)
            Spacer(Modifier.width(Spacing.sm))
            Text("Open on the board")
        }
    }
    if (!state.isNew) {
        TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)) {
            Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(Spacing.sm))
            Text("Delete match", color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun StatusChoice(form: MatchForm, actions: MatchEditActions) {
    ChoiceRow(
        options = listOf(MatchStatus.COMPLETED, MatchStatus.PENDING, MatchStatus.ABANDONED),
        selected = form.status,
        label = { it.label() },
        onSelect = actions.onStatus,
    )
}

/** Date, time, mode and map: optional, folded under one row that says what they are now. */
@Composable
private fun WhenSection(form: MatchForm, actions: MatchEditActions) {
    val summary = listOfNotNull(
        Dates.day(form.playedAt),
        Dates.time(form.playedAt),
        form.mode.label(),
        form.map.takeIf { it.isNotBlank() },
    ).joinToString(SUMMARY_SEPARATOR)
    FoldSection("When and where", summary) {
        WhenRow(form.playedAt, actions.onPlayedAt)
        Spacer(Modifier.size(Spacing.md))
        ChoiceRow(
            options = GameMode.entries,
            selected = form.mode,
            label = { it.label() },
            onSelect = actions.onMode,
        )
        Spacer(Modifier.size(Spacing.md))
        // Free text: the mode above already says single or double lane.
        OutlinedTextField(
            value = form.map,
            onValueChange = actions.onMap,
            label = { Text("Map or table") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private const val SUMMARY_SEPARATOR = " · "

/**
 * An optional part of the form folded under one labelled row: its name, what it holds now ([summary]) and a chevron.
 * A hairline above it; it opens in place.
 */
@Composable
private fun FoldSection(title: String, summary: String, content: @Composable ColumnScope.() -> Unit) {
    var open by rememberSaveable(title) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(top = Spacing.md).animateContentSize(Motion.spec())) {
        PlateRowDivider()
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = if (open) "Hide $title" else "Show $title") { open = !open }
                .semantics(mergeDescendants = true) {
                    heading()
                    stateDescription = if (open) "Expanded" else "Collapsed"
                }
                .heightIn(min = 56.dp)
                .padding(vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                if (summary.isNotEmpty()) {
                    Text(
                        summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.rotate(if (open) HALF_TURN else 0f),
            )
        }
        if (open) Column(Modifier.padding(bottom = Spacing.sm)) { content() }
    }
}

private const val HALF_TURN = 180f

@Composable
private fun TeamsSection(
    state: MatchEditUiState,
    actions: MatchEditActions,
    onPickHero: (Team, Long) -> Unit,
    onPickPilot: (Team, Long) -> Unit,
) {
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
}

@Composable
private fun WinnerAndCondition(state: MatchEditUiState, actions: MatchEditActions, showHeader: Boolean) {
    val form = state.form
    Column {
        QuestionLabel(if (showHeader) "Winner" else "Which team won?", REQUIRED, showHeader)
        Row(
            Modifier.selectableGroup().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Team.entries.forEach { team ->
                WinnerOption(
                    team = team,
                    pilots = state.pilotNames(team),
                    selected = form.winner == team,
                    onClick = { actions.onWinner(team) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
        QuestionLabel(if (showHeader) "Win condition" else "How did they win?", null, showHeader)
        // One choice (tap again to clear): radio semantics, so TalkBack reads "selected" per chip. A grid of equal
        // cells, two to a row, so no option sits alone on a line of its own.
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            WinCondition.entries.chunked(WIN_CONDITION_COLUMNS).forEach { line ->
                Row(
                    Modifier.height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    line.forEach { condition ->
                        val selected = form.winCondition == condition
                        // The winner cards' look and role (radio, selected), so the two questions read alike.
                        SelectablePlate(
                            selected = selected,
                            onClick = { actions.onWinCondition(if (selected) null else condition) },
                            mark = SelectionMark.None,
                            contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = Spacing.sm),
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        ) {
                            Text(
                                condition.label(),
                                style = MaterialTheme.typography.labelLarge,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    repeat(WIN_CONDITION_COLUMNS - line.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun DetailsSection(form: MatchForm, actions: MatchEditActions) {
    val summary = listOfNotNull(
        form.duration.takeIf { it.isNotBlank() }?.let { "$it min" },
        "Notes".takeIf { form.notes.isNotBlank() },
    ).joinToString(SUMMARY_SEPARATOR)
    FoldSection("Details", summary) {
        OutlinedTextField(
            value = form.duration,
            onValueChange = actions.onDuration,
            label = { Text("Duration (min)") },
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
    }
}

@Composable
private fun ErrorCard(messages: List<String>) {
    GoaPlate(
        brackets = false,
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
    ) {
        Row(
            Modifier
                .border(1.dp, MaterialTheme.colorScheme.error, PlateShape)
                .fillMaxWidth()
                .padding(Spacing.md),
        ) {
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
        // Matches are logged after they're played: no day after today.
        val today = LocalDate.now(zone)
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = dateTime.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            yearRange = DatePickerDefaults.YearRange.first..today.year,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    !Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().isAfter(today)

                override fun isSelectableYear(year: Int): Boolean = year <= today.year
            },
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { utc ->
                        val date = Instant.ofEpochMilli(utc).atZone(ZoneOffset.UTC).toLocalDate()
                        val picked = date.atTime(dateTime.toLocalTime()).atZone(zone).toInstant().toEpochMilli()
                        onPlayedAt(minOf(picked, System.currentTimeMillis()))
                    }
                    pickDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Cancel") } },
        ) { DatePicker(state = pickerState) }
    }
    if (pickTime) {
        val timeState = rememberTimePickerState(initialHour = dateTime.hour, initialMinute = dateTime.minute)
        GoaDialog(
            onDismissRequest = { pickTime = false },
            confirmButton = {
                TextButton(onClick = {
                    val date: LocalDate = dateTime.toLocalDate()
                    val picked = date.atTime(LocalTime.of(timeState.hour, timeState.minute))
                        .atZone(zone).toInstant().toEpochMilli()
                    // A time later today than now is taken as "just now".
                    onPlayedAt(minOf(picked, System.currentTimeMillis()))
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
    // Seats open one at a time, on demand: the roster stays a short list (hero, pilot, Handicap) until edited.
    var expanded by remember { mutableStateOf(emptySet<Long>()) }
    val doublePilots = state.form.doublePilots(team)
    val duplicates = state.form.heroesInUse().groupingBy { it }.eachCount().filterValues { it > 1 }.keys
    val otherTeam = state.form.seats(team.opponent).mapNotNull { it.playerId }.toSet()
    val problems = seats.associate { seat ->
        seat.key to when {
            state.showErrors && seat.playerId == null -> "Choose a pilot"
            seat.heroId != null && seat.heroId in duplicates -> "Hero picked twice"
            seat.playerId != null && seat.playerId in otherTeam -> "Also on ${team.opponent.label()}"
            else -> null
        }
    }
    EdgedPlate(
        modifier = modifier.fillMaxWidth().animateContentSize(Motion.spec()),
        brackets = true,
        edge = {
            val problem = problems.values.any { it != null }
            BorderStroke(1.dp, if (problem) MaterialTheme.colorScheme.error else teamColor(team).copy(alpha = 0.6f))
        },
    ) {
        Column {
            Row(Modifier.padding(start = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                // The count goes under the team as a whole when large text leaves no room beside it.
                FlowRow(Modifier.weight(1f), itemVerticalAlignment = Alignment.CenterVertically) {
                    TeamBadge(team)
                }
                TextButton(
                    onClick = { actions.onAddSeat(team) },
                    enabled = seats.size < MAX_SEATS_PER_TEAM,
                    modifier = Modifier.semantics { contentDescription = "Add seat to ${team.label()}" },
                ) {
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
                    modifier = Modifier.padding(start = Spacing.md, end = Spacing.md, bottom = Spacing.md),
                )
            }
            seats.forEach { seat ->
                PlateRowDivider()
                SeatEditor(
                    seat = seat,
                    state = state,
                    expanded = seat.key in expanded,
                    onToggleExpanded = {
                        expanded = if (seat.key in expanded) expanded - seat.key else expanded + seat.key
                    },
                    pilotSeats = if (seat.playerId != null && seat.playerId in doublePilots) {
                        seats.count { it.playerId == seat.playerId }
                    } else {
                        1
                    },
                    problem = problems[seat.key],
                    onPickHero = { onPickHero(team, seat.key) },
                    onPickPilot = { onPickPilot(team, seat.key) },
                    onToggleHandicap = { actions.onToggleHandicap(team, seat.key) },
                    onRemove = { actions.onRemoveSeat(team, seat.key) },
                )
            }
        }
    }
}

/**
 * One seat as a short roster line: the hero (a tap changes it), the pilot as a dropdown pill (a tap changes them, "×2"
 * for a double pilot), a Handicap tag when the seat plays its Handicap card, and an edit button that opens the
 * rest (the Handicap card, removing the seat) under the line.
 */
@Composable
private fun SeatEditor(
    seat: SeatDraft,
    state: MatchEditUiState,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    /** How many of this team's seats the seat's pilot has (2 or more: a double pilot). */
    pilotSeats: Int,
    problem: String?,
    onPickHero: () -> Unit,
    onPickPilot: () -> Unit,
    onToggleHandicap: () -> Unit,
    onRemove: () -> Unit,
) {
    val hero = state.heroOption(seat.heroId)
    val heroLabel = if (seat.heroId == null) "Unknown hero" else state.heroName(seat.heroId)
    val pilot = seat.playerId?.let { state.playersById[it] }
    val large = LocalDensity.current.fontScale >= LARGE_FONT_SCALE
    Column(Modifier.padding(start = Spacing.md, top = Spacing.xs, bottom = Spacing.xs)) {
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
                    modifier = Modifier.size(40.dp),
                )
                Spacer(Modifier.width(Spacing.sm))
                Column {
                    Text(
                        if (seat.heroId == null) "Choose hero" else heroLabel,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        color = if (seat.heroId == null) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    if (seat.handicap) HandicapTag()
                }
            }
            if (!large) {
                Spacer(Modifier.width(Spacing.xs))
                PilotPill(pilot, pilotSeats, error = problem == "Choose a pilot", onClick = onPickPilot)
            }
            IconButton(onClick = onToggleExpanded) {
                Icon(
                    if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Outlined.Edit,
                    contentDescription = if (expanded) "Done editing $heroLabel" else "Edit seat: $heroLabel",
                )
            }
        }
        // Large text: the pilot goes under the hero, so neither name breaks mid-word.
        if (large) {
            PilotPill(
                pilot,
                pilotSeats,
                error = problem == "Choose a pilot",
                onClick = onPickPilot,
                modifier = Modifier.padding(start = 48.dp, end = Spacing.md, bottom = Spacing.xs),
            )
        }
        if (expanded) {
            FlowRow(
                Modifier.padding(top = Spacing.xs, end = Spacing.md, bottom = Spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                GoaFilterChip(
                    selected = seat.handicap,
                    onClick = onToggleHandicap,
                    modifier = Modifier.heightIn(min = 48.dp).semantics {
                        contentDescription = "Handicap card for $heroLabel"
                    },
                    leadingIcon = {
                        Icon(
                            if (seat.handicap) Icons.Filled.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
                            contentDescription = null,
                            modifier = Modifier.size(scaledIconSize(ChipIconSize)),
                        )
                    },
                ) { Text("Handicap card") }
                TextButton(
                    onClick = onRemove,
                    modifier = Modifier.heightIn(min = 48.dp).semantics {
                        contentDescription = "Remove seat: $heroLabel"
                    },
                ) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(Spacing.xs))
                    Text("Remove seat", color = MaterialTheme.colorScheme.error)
                }
            }
        }
        if (problem != null) {
            Text(
                problem,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = Spacing.xs),
            )
        }
    }
}

/** "Handicap" under the hero's name: this seat plays its Handicap card. */
@Composable
private fun HandicapTag() {
    Text(
        "Handicap",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .padding(top = 2.dp)
            .border(1.dp, GoaTheme.colors.selection, RoundedCornerShape(4.dp))
            .padding(horizontal = Spacing.xs),
    )
}

/**
 * The seat's pilot as a dropdown pill ("Ann ▾", "Ann ×2" for a double pilot); a tap opens the pilot picker.
 * One TalkBack node ("Pilot: Ann. Change pilot").
 */
@Composable
private fun PilotPill(
    pilot: Player?,
    seats: Int,
    error: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier
            .widthIn(max = scaledIconSize(PILOT_PILL_MAX, PILOT_PILL_SCALE))
            .heightIn(min = 48.dp)
            .clip(shape)
            .border(1.dp, if (error) colors.error else colors.outline, shape)
            .clickable(onClickLabel = "Change pilot", role = Role.DropdownList, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = "Pilot: ${pilot?.name ?: "none"}. Change pilot"
                if (seats > 1) stateDescription = "Pilots $seats heroes"
                role = Role.DropdownList
                onClick(label = "Change pilot") {
                    onClick()
                    true
                }
            }
            .padding(start = Spacing.sm, end = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Person,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(scaledIconSize(18.dp)),
        )
        Spacer(Modifier.width(Spacing.xs))
        Text(
            pilot?.name ?: "Choose pilot",
            style = MaterialTheme.typography.bodyMedium,
            color = when {
                error -> colors.error
                pilot == null -> colors.onSurfaceVariant
                else -> colors.onSurface
            },
            maxLines = 2,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (seats > 1) {
            Text(
                " ×$seats",
                style = MaterialTheme.typography.labelLarge,
                color = colors.onSurfaceVariant,
            )
        }
        Icon(Icons.Outlined.ArrowDropDown, contentDescription = null, tint = colors.onSurface)
    }
}

/** Room for a name like "Kit Featherstone" beside the glyph and the ▾; it grows with the text up to 1.5×. */
private val PILOT_PILL_MAX = 150.dp
private const val PILOT_PILL_SCALE = 1.5f

/** The team's pilots in seat order, each once (a double pilot sits twice). */
private fun MatchEditUiState.pilotNames(team: Team): List<String> =
    form.seats(team).mapNotNull { seat -> seat.playerId?.let { playersById[it]?.name } }.distinct()

/**
 * "Red won" as a radio card: an empty trophy outline until chosen, then a bronze trophy and the bronze selection
 * frame (bronze is selected; the team keeps its own mark and colour in the words). The team's pilots under it, so
 * the table confirms who it is crediting at a glance (the seats themselves are further down the form).
 */
@Composable
private fun WinnerOption(
    team: Team,
    pilots: List<String>,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SelectablePlate(
        selected = selected,
        onClick = onClick,
        mark = SelectionMark.None,
        modifier = modifier
            .heightIn(min = 72.dp)
            .semantics {
                contentDescription = "${team.label()} won" +
                    if (pilots.isEmpty()) "" else ": ${pilots.joinToString(", ")}"
            },
    ) {
        Icon(
            if (selected) Icons.Filled.EmojiEvents else Icons.Outlined.EmojiEvents,
            contentDescription = null,
            tint = if (selected) GoaTheme.colors.selection else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.width(Spacing.sm))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TeamBadge(team)
                Text(
                    " won",
                    style = MaterialTheme.typography.labelLarge,
                    color = teamTextColor(team),
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (pilots.isNotEmpty()) {
                Text(
                    pilots.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
        }
    }
}

/** A question on the form, tagged "Required" when it must be answered; an optional one goes untagged. */
@Composable
private fun QuestionLabel(text: String, need: String?, small: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(top = Spacing.lg, bottom = Spacing.sm),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text,
            style = if (small) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f, fill = false).semantics { heading() },
        )
        if (need != null) {
            Spacer(Modifier.width(Spacing.sm))
            Text("· $need", style = MaterialTheme.typography.labelMedium, color = GoaTheme.colors.selection)
        }
    }
}

private const val REQUIRED = "Required"

/** Win conditions two to a row: four equal cells. */
private const val WIN_CONDITION_COLUMNS = 2

/** Single choice: a segmented row, or wrapping chips when the font is large. */
@Composable
private fun <T> ChoiceRow(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    if (LocalDensity.current.fontScale >= LARGE_FONT_SCALE) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            options.forEach { option ->
                val isSelected = option == selected
                GoaFilterChip(
                    selected = isSelected,
                    onClick = { onSelect(option) },
                    modifier = Modifier.heightIn(min = 48.dp),
                    leadingIcon = if (isSelected) {
                        { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else {
                        null
                    },
                ) { Text(label(option)) }
            }
        }
    } else {
        GoaSegmentedRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, option ->
                GoaSegment(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                ) { Text(label(option), maxLines = 1) }
            }
        }
    }
}
