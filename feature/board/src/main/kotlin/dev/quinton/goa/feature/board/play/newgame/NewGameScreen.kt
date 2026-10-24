package dev.quinton.goa.feature.board.play.newgame

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.EngravedRule
import dev.quinton.goa.core.designsystem.component.GoaScreen
import dev.quinton.goa.core.designsystem.component.HeroPortrait
import dev.quinton.goa.core.designsystem.component.TeamMark
import dev.quinton.goa.core.designsystem.component.UnverifiedBadge
import dev.quinton.goa.core.designsystem.component.scaledIconSize
import dev.quinton.goa.core.designsystem.component.teamColor
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.GameLength
import dev.quinton.goa.feature.board.BoardText
import dev.quinton.goa.feature.board.board.BoardSheet
import dev.quinton.goa.feature.board.board.SearchField
import dev.quinton.goa.feature.board.play.table.Coin
import dev.quinton.goa.feature.board.play.table.TableText
import dev.quinton.goa.feature.board.setup.HeroGrid
import dev.quinton.goa.feature.board.setup.NewBoardAction
import dev.quinton.goa.feature.board.setup.SourcesContent
import dev.quinton.goa.feature.board.setup.TeamSwitch
import dev.quinton.goa.feature.board.setup.Teams

/**
 * New game against the computer, in two steps. The line-up: both teams in pick order, search with
 * the "Adding to" switch, the hero grid (heroes the engine plays first; the rest "Coming later"),
 * "Use a draft or match". Then who plays: the hero you control (or your whole team), the
 * computer's level, Quick or Long, the map, the Coach and the Tie Breaker coin the app flipped; Start.
 */
@Composable
fun NewGameScreen(
    state: NewGameUiState,
    onAction: (NewGameAction) -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ready = state as? NewGameUiState.Ready
    var sources by rememberSaveable { mutableStateOf(false) }
    val options = ready?.form?.step == NewGameStep.OPTIONS
    BackHandler(enabled = options) { onAction(NewGameAction.BackToLineup) }
    GoaScreen(
        title = if (options) "Who plays" else "New game",
        onBack = if (options) ({ onAction(NewGameAction.BackToLineup) }) else onBack,
        modifier = modifier,
        actions = {
            if (ready != null && !options) {
                if (ready.form.red.isNotEmpty() || ready.form.blue.isNotEmpty()) {
                    TextButton(onClick = { onAction(NewGameAction.Lineup(NewBoardAction.Clear)) }) { Text("Clear") }
                }
                IconButton(onClick = { sources = true }, enabled = ready.sources.isNotEmpty()) {
                    Icon(Icons.Outlined.History, contentDescription = "Use a draft or match")
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                ready == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                options -> OptionsStep(ready, onAction, onStart)
                else -> LineupStep(ready, onAction)
            }
        }
    }
    if (ready != null && sources) {
        BoardSheet(onDismiss = { sources = false }) {
            SourcesContent(
                ready.sources,
                onUse = {
                    onAction(NewGameAction.Lineup(NewBoardAction.Use(it)))
                    sources = false
                },
                onDone = { sources = false },
            )
        }
    }
}

@Composable
private fun LineupStep(state: NewGameUiState.Ready, onAction: (NewGameAction) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val picker = state.picker
    val shown = picker.heroes.filter {
        query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) ||
            it.title.contains(query.trim(), ignoreCase = true)
    }
    val large = LocalDensity.current.fontScale > LARGE_FONT
    val lineup: (NewBoardAction) -> Unit = { onAction(NewGameAction.Lineup(it)) }
    val search = @Composable { mod: Modifier ->
        val field = @Composable { fieldModifier: Modifier ->
            SearchField(query, {
                query = it
            }, onSearch = {
                focus.clearFocus()
            }, modifier = fieldModifier, horizontalPadding = 0.dp, placeholder = "Search")
        }
        if (large) {
            Column(mod) {
                field(Modifier)
                TeamSwitch(picker.form.filling, onPick = { lineup(NewBoardAction.Fill(it)) })
            }
        } else {
            Row(mod, verticalAlignment = Alignment.CenterVertically) {
                field(Modifier.weight(1f))
                Spacer(Modifier.width(Spacing.sm))
                TeamSwitch(picker.form.filling, onPick = { lineup(NewBoardAction.Fill(it)) })
            }
        }
    }
    val ready = state.playable.mapNotNull { id -> picker.heroById[id]?.name }.sorted()
    val intro = @Composable {
        Text(
            "Ready to play: ${ready.joinToString(", ")}. More heroes are coming later.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth > maxHeight) {
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.weight(0.42f).fillMaxHeight()) {
                    Column(
                        Modifier.weight(
                            1f,
                        ).verticalScroll(
                            rememberScrollState(),
                        ).padding(horizontal = Spacing.screen, vertical = Spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        intro()
                        Teams(picker, lineup)
                    }
                    NextBar(state, onAction)
                }
                Column(Modifier.weight(0.58f).fillMaxHeight()) {
                    search(Modifier.padding(start = Spacing.sm, end = Spacing.screen))
                    HeroGrid(shown, picker, lineup, large, start = Spacing.sm, end = Spacing.screen)
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Column(
                    Modifier.padding(horizontal = Spacing.screen).padding(top = Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    intro()
                    Teams(picker, lineup)
                }
                search(Modifier.padding(horizontal = Spacing.screen))
                HeroGrid(shown, picker, lineup, large, start = Spacing.screen, end = Spacing.screen)
                NextBar(state, onAction)
            }
        }
    }
}

/** Why the line-up can't be played yet (or the 1v1 note) beside "Next". */
@Composable
private fun NextBar(state: NewGameUiState.Ready, onAction: (NewGameAction) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        EngravedRule()
        Row(
            Modifier.fillMaxWidth().padding(
                start = Spacing.screen,
                end = Spacing.screen,
                top = Spacing.sm,
                bottom = Spacing.sm,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                state.problem ?: state.note
                    ?: "${state.form.red.size} v ${state.form.blue.size}: the rulebook's standard is 2 v 2.",
                style = MaterialTheme.typography.bodySmall,
                color = if (state.problem !=
                    null
                ) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(Spacing.sm))
            Button(
                onClick = { onAction(NewGameAction.Next) },
                enabled = state.canContinue,
                modifier = Modifier.heightIn(min = 56.dp),
                contentPadding = PaddingValues(horizontal = Spacing.xl),
            ) { Text("Next", style = MaterialTheme.typography.titleMedium) }
        }
    }
}

@Composable
private fun OptionsStep(state: NewGameUiState.Ready, onAction: (NewGameAction) -> Unit, onStart: () -> Unit) {
    val form = state.form
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Section("You play")
            state.seats.forEach { seat ->
                val chosen = seat.hero.id == form.controlled
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .selectable(selected = chosen, role = Role.RadioButton) {
                            onAction(NewGameAction.You(seat.hero.id))
                        },
                ) {
                    RadioButton(selected = chosen, onClick = null)
                    Spacer(Modifier.width(Spacing.sm))
                    SeatFace(seat)
                    Spacer(Modifier.width(Spacing.sm))
                    Column(Modifier.weight(1f)) {
                        Text(seat.hero.name, style = MaterialTheme.typography.titleSmall)
                        Text(
                            "${BoardText.team(seat.team)} · " +
                                if (seat.human) "You" else "Computer · ${TableText.level(seat.level ?: form.level)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            SwitchRow(
                title = "Control my whole team",
                detail = "Answer for every hero of your team (the rulebook's two-player way, p.20). " +
                    "Off: the computer plays your teammates.",
                checked = form.wholeTeam,
                onChange = { onAction(NewGameAction.WholeTeam(it)) },
            )
            Section("The computer")
            LevelPicker(form.level, onPick = { onAction(NewGameAction.Level(it)) })
            Text(TableText.levelDescription(form.level), style = MaterialTheme.typography.bodyMedium)
            SwitchRow(
                title = "Advanced: a level per seat",
                detail = null,
                checked = form.advanced,
                onChange = { onAction(NewGameAction.Advanced(it)) },
            )
            if (form.advanced) {
                state.seats.filterNot { it.human }.forEach { seat ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SeatFace(seat)
                        Spacer(Modifier.width(Spacing.sm))
                        Text(
                            seat.hero.name,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    LevelPicker(seat.level ?: form.level, onPick = {
                        onAction(NewGameAction.SeatLevel(seat.hero.id, it))
                    })
                }
            }
            Section("The game")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().semantics { contentDescription = "Game length" }) {
                GameLength.entries.forEachIndexed { i, length ->
                    SegmentedButton(
                        selected = form.length == length,
                        onClick = { onAction(NewGameAction.Length(length)) },
                        shape = SegmentedButtonDefaults.itemShape(i, GameLength.entries.size),
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text(TableText.length(length)) }
                }
            }
            MapMenu(state, onAction)
            SwitchRow(
                title = "Coach",
                detail = "A gentle nudge before a clearly worse choice, and the turning points after the game. " +
                    "Never chooses for you; switch it in the game too.",
                checked = form.coach,
                onChange = { onAction(NewGameAction.Coach(it)) },
            )
            form.coin?.let { coin ->
                Section("Tie Breaker coin")
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.semantics(mergeDescendants = true) {},
                ) {
                    Coin(coin, 28.dp)
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        "The app flipped it: ${BoardText.team(
                            coin,
                        )} side up (p.5). It breaks initiative ties between teams.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            state.note?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(Modifier.fillMaxWidth()) {
            EngravedRule()
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                OutlinedButton(onClick = {
                    onAction(NewGameAction.BackToLineup)
                }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text("Line-up")
                }
                Button(
                    onClick = onStart,
                    enabled = state.canContinue && !state.starting,
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                ) { Text(if (state.starting) "Setting up…" else "Start", style = MaterialTheme.typography.titleMedium) }
            }
        }
    }
}

@Composable
private fun SeatFace(seat: SeatUi) {
    val color = teamColor(seat.team)
    Row(verticalAlignment = Alignment.CenterVertically) {
        TeamMark(seat.team, markSize = scaledIconSize(9.dp))
        Spacer(Modifier.width(3.dp))
        HeroPortrait(
            heroId = seat.hero.id,
            portrait = seat.hero.portrait,
            contentDescription = null,
            shape = CircleShape,
            framed = false,
            modifier = Modifier
                .size(scaledIconSize(36.dp, maxScale = 1.4f))
                .border(2.dp, color, CircleShape),
        )
    }
}

@Composable
private fun LevelPicker(level: AiLevel, onPick: (AiLevel) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().semantics { contentDescription = "Computer level" }) {
        AiLevel.entries.forEachIndexed { i, entry ->
            SegmentedButton(
                selected = level == entry,
                onClick = { onPick(entry) },
                shape = SegmentedButtonDefaults.itemShape(i, AiLevel.entries.size),
                modifier = Modifier.heightIn(min = 48.dp).semantics {
                    contentDescription =
                        "${TableText.level(entry)}: ${TableText.levelDescription(entry)}"
                },
            ) { Text(TableText.level(entry), maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
private fun MapMenu(state: NewGameUiState.Ready, onAction: (NewGameAction) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Surface(
            onClick = { menu = true },
            enabled = state.maps.size > 1,
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, GoaTheme.colors.engravedBorder),
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).semantics {
                contentDescription = "Map: ${state.map?.name ?: "none"}. Change map"
            },
        ) {
            Row(
                Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("MAP", style = GoaTextStyles.overline, color = MaterialTheme.colorScheme.primary)
                    Text(state.map?.name ?: "No verified map", style = MaterialTheme.typography.titleSmall)
                    if (state.map?.verified == false) UnverifiedBadge()
                }
                if (state.maps.size > 1) Icon(Icons.Outlined.ArrowDropDown, contentDescription = null)
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            state.maps.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(option.name)
                            Text(
                                if (option.lanes > 1) "${option.lanes} lanes" else "Single lane",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    leadingIcon = {
                        if (option.id ==
                            state.map?.id
                        ) {
                            Icon(Icons.Outlined.Check, contentDescription = "Chosen")
                        } else {
                            Spacer(Modifier.width(24.dp))
                        }
                    },
                    onClick = {
                        menu = false
                        onAction(NewGameAction.Map(option.id))
                    },
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, detail: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            detail?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(Spacing.sm))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title.uppercase(),
        style = GoaTextStyles.overline,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = Spacing.sm).semantics { heading() },
    )
}

private const val LARGE_FONT = 1.3f
