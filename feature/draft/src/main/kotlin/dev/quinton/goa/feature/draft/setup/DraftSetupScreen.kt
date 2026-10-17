package dev.quinton.goa.feature.draft.setup

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.quinton.goa.core.designsystem.component.ConfirmDialog
import dev.quinton.goa.core.designsystem.component.EmptyState
import dev.quinton.goa.core.designsystem.component.GoaScreen
import dev.quinton.goa.core.designsystem.component.PlayerAvatar
import dev.quinton.goa.core.designsystem.component.SettingsActionButton
import dev.quinton.goa.core.designsystem.component.TeamBadge
import dev.quinton.goa.core.designsystem.component.teamColor
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.domain.draft.DraftPlanner
import dev.quinton.goa.core.domain.draft.MethodOrigin
import dev.quinton.goa.core.domain.draft.PoolSource
import dev.quinton.goa.core.domain.teams.ComplexityFilter
import dev.quinton.goa.core.domain.teams.OddSeatMode
import dev.quinton.goa.core.domain.teams.SeatLayouts
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.settings.ThemeMode
import dev.quinton.goa.feature.draft.ui.ChoiceCard
import dev.quinton.goa.feature.draft.ui.HeroTile
import dev.quinton.goa.feature.draft.ui.PreviewData
import dev.quinton.goa.feature.draft.ui.SectionCard
import dev.quinton.goa.feature.draft.ui.SequenceStrip
import dev.quinton.goa.feature.draft.ui.TieBreakerCoin
import dev.quinton.goa.feature.draft.ui.TileState
import dev.quinton.goa.feature.draft.ui.goaFilterChipColors
import dev.quinton.goa.feature.draft.ui.goaSegmentedColors
import dev.quinton.goa.feature.draft.ui.goaTonalButtonColors
import dev.quinton.goa.feature.draft.ui.plural
import dev.quinton.goa.feature.draft.ui.teamFilterChipColors
import dev.quinton.goa.feature.draft.ui.teamLabel
import dev.quinton.goa.feature.draft.ui.teamName
import dev.quinton.goa.feature.draft.ui.tokens
import kotlin.math.roundToInt

/** Callbacks from the setup wizard; a single object keeps the stateless screen signature short. */
interface SetupActions {
    fun togglePlayer(id: String) {}

    fun clearPlayers() {}

    fun quickAdd(name: String) {}

    fun openPlayerEditor() {}

    fun setTeamMode(mode: TeamMode) {}

    fun movePlayer(id: String) {}

    fun toggleLock(id: String) {}

    fun rebalance() {}

    fun setOddMode(mode: OddSeatMode) {}

    fun setDoublePilot(id: String) {}

    fun setCaptain(team: Team, id: String) {}

    fun selectMethod(id: String) {}

    fun manageMethods() {}

    fun setPoolSource(source: PoolSource) {}

    fun setCustomSelection(ids: Set<String>) {}

    fun setMaxComplexity(max: Int?) {}

    fun setStepTimer(enabled: Boolean) {}

    fun setStepSeconds(seconds: Int) {}

    fun flipCoin() {}

    fun setTeamA(team: Team) {}

    fun next() {}

    fun back() {}

    fun goTo(stage: SetupStage) {}

    fun start() {}

    fun resume() {}

    fun discardActive() {}

    fun dismissMessage() {}

    fun openSettings() {}

    companion object {
        val None = object : SetupActions {}
    }
}

@Composable
fun DraftSetupDestination(
    viewModel: DraftSetupViewModel,
    onOpenBoard: () -> Unit,
    onManageMethods: () -> Unit,
    onAddPlayer: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.setupEvents.collect { if (it == SetupEvent.OpenBoard) onOpenBoard() }
    }
    val actions = remember(viewModel) {
        object : SetupActions {
            override fun togglePlayer(id: String) = viewModel.togglePlayer(id)
            override fun clearPlayers() = viewModel.clearPlayers()
            override fun quickAdd(name: String) = viewModel.quickAddPlayer(name)
            override fun openPlayerEditor() = onAddPlayer()
            override fun setTeamMode(mode: TeamMode) = viewModel.setTeamMode(mode)
            override fun movePlayer(id: String) = viewModel.movePlayer(id)
            override fun toggleLock(id: String) = viewModel.toggleLock(id)
            override fun rebalance() = viewModel.balance()
            override fun setOddMode(mode: OddSeatMode) = viewModel.setOddMode(mode)
            override fun setDoublePilot(id: String) = viewModel.setDoublePilot(id)
            override fun setCaptain(team: Team, id: String) = viewModel.setCaptain(team, id)
            override fun selectMethod(id: String) = viewModel.selectMethod(id)
            override fun manageMethods() = onManageMethods()
            override fun setPoolSource(source: PoolSource) = viewModel.setPoolSource(source)
            override fun setCustomSelection(ids: Set<String>) = viewModel.setCustomSelection(ids)
            override fun setMaxComplexity(max: Int?) = viewModel.setMaxComplexity(max)
            override fun setStepTimer(enabled: Boolean) = viewModel.setStepTimer(enabled)
            override fun setStepSeconds(seconds: Int) = viewModel.setStepSeconds(seconds)
            override fun flipCoin() = viewModel.flipCoin()
            override fun setTeamA(team: Team) = viewModel.setTeamA(team)
            override fun next() = viewModel.next()
            override fun back() {
                viewModel.back()
            }
            override fun goTo(stage: SetupStage) = viewModel.goTo(stage)
            override fun start() = viewModel.start()
            override fun resume() = viewModel.resume()
            override fun discardActive() = viewModel.discardActive()
            override fun dismissMessage() = viewModel.dismissMessage()
            override fun openSettings() = onOpenSettings()
        }
    }
    BackHandler(enabled = state.stage != SetupStage.PLAYERS) { viewModel.back() }
    DraftSetupScreen(state, actions)
}

@Composable
fun DraftSetupScreen(state: SetupUiState, actions: SetupActions) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            actions.dismissMessage()
        }
    }
    GoaScreen(
        title = "Draft",
        actions = {
            IconButton(onClick = actions::manageMethods) {
                Icon(Icons.AutoMirrored.Outlined.ListAlt, contentDescription = "Draft methods")
            }
            SettingsActionButton(actions::openSettings)
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            // imePadding: the keyboard (quick add) must not cover "Next: Teams".
            Column(Modifier.fillMaxSize().imePadding()) {
                StageIndicator(state.stage, onSelect = actions::goTo)
                AnimatedContent(
                    targetState = state.stage,
                    transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(200)) },
                    modifier = Modifier.weight(1f),
                    label = "stage",
                ) { stage ->
                    LazyColumn(
                        contentPadding = PaddingValues(Spacing.screen),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        when (stage) {
                            SetupStage.PLAYERS -> playersStage(state, actions)
                            SetupStage.TEAMS -> item { TeamsStage(state, actions) }
                            SetupStage.HEROES -> item { HeroesStage(state, actions) }
                            SetupStage.COIN -> item { CoinStage(state, actions) }
                        }
                    }
                }
                BottomBar(state, actions)
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp))
        }
    }
}

@Composable
private fun StageIndicator(current: SetupStage, onSelect: (SetupStage) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        SetupStage.entries.forEach { stage ->
            val done = stage.ordinal < current.ordinal
            val active = stage == current
            val color = when {
                active -> MaterialTheme.colorScheme.primary
                done -> MaterialTheme.colorScheme.secondary
                else -> MaterialTheme.colorScheme.outlineVariant
            }
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = done, role = Role.Tab) { onSelect(stage) }
                    .heightIn(min = 48.dp)
                    .semantics {
                        selected = active
                        stateDescription = when {
                            active -> "Current step"
                            done -> "Done"
                            else -> "Not started"
                        }
                    }
                    .padding(vertical = Spacing.xs),
                horizontalAlignment = Alignment.CenterHorizontally,
                // Top: the bars stay level even if one label wraps at a large font scale.
                verticalArrangement = Arrangement.Top,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(color, RoundedCornerShape(2.dp)),
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    "${stage.ordinal + 1}. ${stage.shortTitle}",
                    style = MaterialTheme.typography.labelMedium,
                    color = activeColor(active),
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    modifier = Modifier.semantics { contentDescription = "${stage.ordinal + 1}. ${stage.title}" },
                )
            }
        }
    }
}

@Composable
private fun BottomBar(state: SetupUiState, actions: SetupActions) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.md)) {
            state.blocker?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Spacing.sm),
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.stage != SetupStage.PLAYERS) {
                    OutlinedButton(onClick = actions::back, modifier = Modifier.heightIn(min = 52.dp)) { Text("Back") }
                }
                val last = state.stage == SetupStage.COIN
                Button(
                    onClick = if (last) actions::start else actions::next,
                    enabled = state.blocker == null && !state.form.starting,
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                ) {
                    if (last) {
                        Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(Spacing.sm))
                        Text(if (state.form.teamMode == TeamMode.PLAYER_DRAFT) "Start Player Draft" else "Start draft")
                    } else {
                        Text("Next: ${SetupStage.entries[state.stage.ordinal + 1].title}")
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Players

private fun androidx.compose.foundation.lazy.LazyListScope.playersStage(state: SetupUiState, actions: SetupActions) {
    state.resume?.let { resume -> item { ResumeCard(resume, actions) } }
    item { QuickAddRow(actions) }
    if (state.players.isEmpty()) {
        item {
            EmptyState(
                icon = Icons.Outlined.Groups,
                title = "Who's playing?",
                message = "Add the people at the table above, or create full profiles in Players.",
                modifier = Modifier.fillMaxWidth(),
                action = {
                    OutlinedButton(onClick = actions::openPlayerEditor) {
                        Icon(Icons.Outlined.PersonAdd, contentDescription = null)
                        Spacer(Modifier.width(Spacing.sm))
                        Text("New player profile")
                    }
                },
            )
        }
    } else {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${state.playerCount} selected · 2–${SetupUiState.MAX_PLAYERS} players",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.semantics { heading() },
                    )
                    if (state.form.lastGame && state.playerCount > 0) {
                        Text(
                            "Same players and teams as last game",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
                if (state.playerCount > 0) {
                    TextButton(onClick = actions::clearPlayers, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("Clear")
                    }
                }
            }
        }
        items(state.players, key = { it.player.id }) { option ->
            PlayerRow(option, selected = option.player.id in state.form.selected, onToggle = {
                actions.togglePlayer(option.player.id)
            })
        }
    }
}

@Composable
private fun ResumeCard(resume: ResumeInfo, actions: SetupActions) {
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
    ) {
        Column(Modifier.fillMaxWidth().padding(Spacing.lg)) {
            Text(
                if (resume.complete) "Draft complete" else "Draft in progress",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text("${resume.title} · ${resume.detail}", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(Spacing.md))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Button(onClick = actions::resume, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(if (resume.complete) "Open" else "Resume")
                }
                TextButton(onClick = { confirmDiscard = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Discard")
                }
            }
        }
    }
    if (confirmDiscard) {
        // Same confirmation as the board's "Discard draft".
        ConfirmDialog(
            title = "Discard this draft?",
            message = if (resume.complete) {
                "The finished draft is thrown away. Nothing is saved to the match log."
            } else {
                "Bans and picks so far are thrown away. Nothing is saved to the match log."
            },
            confirmLabel = "Discard",
            dismissLabel = if (resume.complete) "Keep it" else "Keep drafting",
            destructive = true,
            onConfirm = {
                confirmDiscard = false
                actions.discardActive()
            },
            onDismiss = { confirmDiscard = false },
        )
    }
}

@Composable
private fun QuickAddRow(actions: SetupActions) {
    var name by rememberSaveable { mutableStateOf("") }
    val submit = {
        actions.quickAdd(name)
        name = ""
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Quick add a player") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(Spacing.sm))
        FilledTonalButton(
            colors = goaTonalButtonColors(),
            onClick = submit,
            enabled = name.isNotBlank(),
            modifier = Modifier.heightIn(min = 56.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Add player")
        }
    }
}

@Composable
private fun PlayerRow(option: PlayerOption, selected: Boolean, onToggle: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(
                alpha = 0.10f,
            )
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else GoaTheme.colors.engravedBorder,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onToggle() }),
    ) {
        Row(
            Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlayerAvatar(option.player)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(option.player.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    ratingLabel(option),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Checkbox(checked = selected, onCheckedChange = null)
        }
    }
}

private fun ratingLabel(option: PlayerOption): String {
    val r = option.rating ?: return "Unrated"
    if (r.games == 0) return "Unrated"
    val provisional = if (r.provisional) " · provisional" else ""
    return "Elo ${r.value.roundToInt()} · ${plural(r.games, "game")}$provisional"
}

// ---------------------------------------------------------------- Teams

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TeamsStage(state: SetupUiState, actions: SetupActions) {
    val form = state.form
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            TeamMode.entries.forEachIndexed { i, mode ->
                SegmentedButton(
                    colors = goaSegmentedColors(),
                    selected = form.teamMode == mode,
                    onClick = { actions.setTeamMode(mode) },
                    shape = SegmentedButtonDefaults.itemShape(i, TeamMode.entries.size),
                    label = {
                        Text(
                            mode.label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.semantics {
                                if (mode == TeamMode.BALANCED) contentDescription = "Balance by Elo"
                            },
                        )
                    },
                )
            }
        }
        if (form.teamMode == TeamMode.PLAYER_DRAFT) {
            PlayerDraftSetup(state, actions)
        } else {
            TeamColumns(state, actions)
            SeatLayoutCard(state, actions)
        }
    }
}

@Composable
private fun TeamColumns(state: SetupUiState, actions: SetupActions) {
    val form = state.form
    val balanced = form.teamMode == TeamMode.BALANCED
    SectionCard(
        title = if (balanced) "Balanced by Elo" else "Tap a player to switch teams",
        subtitle = form.balanceDifference?.let { "Average Elo differs by ${it.roundToInt()}" }
            ?: if (balanced) "Lock players to keep them on a team, then re-balance" else null,
        trailing = if (balanced) {
            {
                FilledTonalButton(colors = goaTonalButtonColors(), onClick = actions::rebalance) {
                    Icon(Icons.Outlined.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Spacing.xs))
                    Text("Re-balance")
                }
            }
        } else {
            null
        },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            for (team in Team.entries) {
                val ids = if (team == Team.RED) form.red else form.blue
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    val seats = state.seats.count { it.team == team }
                    TeamBadge(team)
                    Text(
                        "${plural(ids.size, "player")} · ${plural(seats, "hero", "heroes")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ids.forEach { id ->
                        TeamMemberChip(
                            name = state.player(id)?.player?.name ?: "Unknown",
                            option = state.player(id),
                            team = team,
                            locked = id in form.locked,
                            showLock = balanced,
                            onMove = { actions.movePlayer(id) },
                            onLock = { actions.toggleLock(id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TeamMemberChip(
    name: String,
    option: PlayerOption?,
    team: Team,
    locked: Boolean,
    showLock: Boolean,
    onMove: () -> Unit,
    onLock: () -> Unit,
) {
    val color = teamColor(team)
    Surface(
        onClick = onMove,
        enabled = !locked,
        shape = MaterialTheme.shapes.small,
        color = color.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.6f)),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .semantics {
                contentDescription = "$name, ${teamName(team)} team" + if (locked) ", locked" else ""
                stateDescription = if (locked) "Locked" else "Tap to move to ${teamName(team.opponent)}"
            },
    ) {
        Row(Modifier.padding(start = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            PlayerAvatar(option?.player, size = 28.dp)
            Spacer(Modifier.width(Spacing.sm))
            Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 2)
            if (showLock) {
                IconButton(onClick = onLock) {
                    Icon(
                        if (locked) Icons.Filled.Lock else Icons.Outlined.LockOpen,
                        contentDescription = if (locked) "Unlock $name" else "Lock $name to ${teamName(team)}",
                        tint = activeColor(locked),
                    )
                }
            } else {
                Spacer(Modifier.width(Spacing.sm))
            }
        }
    }
}

@Composable
private fun SeatLayoutCard(state: SetupUiState, actions: SetupActions) {
    val form = state.form
    val n = state.playerCount
    when {
        n == 2 -> InfoCard("Two players", "Each player controls two heroes (rulebook), so the draft fills 2 v 2 seats.")

        n == 3 -> {
            val solo = (if (form.red.size == 1) form.red else form.blue).firstOrNull()
            val name = solo?.let { state.player(it)?.player?.name } ?: "The solo player"
            InfoCard(
                "Official 3-player setup",
                "$name controls two heroes; one of them uses its Handicap card. " +
                    "Put your most experienced player alone.",
            )
        }

        SeatLayouts.offersOddChoice(n) -> SectionCard(
            title = "Odd player count",
            subtitle = "How should the teams be evened out?",
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                ChoiceCard(
                    title = "Handicap",
                    badge = "Rulebook",
                    body = "Teams stay uneven. Every hero on the larger team swaps a basic card for its Handicap card.",
                    selected = form.oddMode == OddSeatMode.HANDICAP,
                    onClick = { actions.setOddMode(OddSeatMode.HANDICAP) },
                )
                ChoiceCard(
                    title = "Double pilot",
                    badge = "House rule",
                    body = "One player on the smaller team controls two heroes, so both teams draft the same " +
                        "number of heroes.",
                    selected = form.oddMode == OddSeatMode.DOUBLE_PILOT,
                    onClick = { actions.setOddMode(OddSeatMode.DOUBLE_PILOT) },
                    extra = if (form.oddMode == OddSeatMode.DOUBLE_PILOT) {
                        { DoublePilotPicker(state, actions) }
                    } else {
                        null
                    },
                )
            }
        }

        else -> Unit
    }
}

@Composable
private fun DoublePilotPicker(state: SetupUiState, actions: SetupActions) {
    val form = state.form
    val smaller = if (form.red.size < form.blue.size) form.red else form.blue
    val current = state.seats.groupBy { it.pilotId }.entries.firstOrNull { it.value.size > 1 }?.key
    Column {
        Text("Who pilots two heroes?", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            smaller.forEach { id ->
                FilterChip(
                    colors = goaFilterChipColors(),
                    selected = id == current,
                    onClick = { actions.setDoublePilot(id) },
                    label = { Text(state.player(id)?.player?.name ?: "Unknown") },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
    }
}

@Composable
private fun PlayerDraftSetup(state: SetupUiState, actions: SetupActions) {
    val form = state.form
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        InfoCard(
            "Official Player Draft",
            "The two most experienced players are Captains. First every player gets a hero with the method you " +
                "choose next. Then the Captains draft players one at a time, Team A's captain first. Everyone plays " +
                "their pre-selected hero." + if (state.playerCount % 2 == 1) {
                    " With an odd count, the larger team's heroes use their Handicap cards."
                } else {
                    ""
                },
        )
        for (team in Team.entries) {
            SectionCard(title = "${teamLabel(team)} captain", titleColor = teamColor(team)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    form.selected.forEach { id ->
                        val chosen = if (team == Team.RED) form.redCaptain == id else form.blueCaptain == id
                        FilterChip(
                            colors = goaFilterChipColors(),
                            selected = chosen,
                            onClick = { actions.setCaptain(team, id) },
                            label = { Text(state.player(id)?.player?.name ?: "Unknown") },
                            leadingIcon = { PlayerAvatar(state.player(id)?.player, size = 24.dp) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(Spacing.md)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.height(2.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

// ---------------------------------------------------------------- Heroes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HeroesStage(state: SetupUiState, actions: SetupActions) {
    val form = state.form
    var choosing by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        SectionCard(
            title = "Method",
            subtitle = "How heroes are chosen",
            trailing = { TextButton(onClick = actions::manageMethods) { Text("Manage") } },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                state.methods.forEach { option ->
                    val selected = option.method.id == form.methodId
                    ChoiceCard(
                        title = option.method.name,
                        badge = originLabel(option.method.origin),
                        body = option.unavailable ?: option.method.summary,
                        selected = selected,
                        onClick = { actions.selectMethod(option.method.id) },
                        extra = if (selected && option.preview.isNotEmpty()) {
                            { SequenceStrip(option.preview) }
                        } else {
                            null
                        },
                    )
                }
            }
        }
        SectionCard(
            title = "Hero pool",
            subtitle = "${plural(state.candidateCount, "hero", "heroes")} eligible",
        ) {
            state.poolNote?.let { note ->
                Text(
                    note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = GoaTheme.colors.unverified,
                    modifier = Modifier.padding(bottom = Spacing.sm),
                )
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                PoolSource.entries.forEachIndexed { i, source ->
                    SegmentedButton(
                        colors = goaSegmentedColors(),
                        selected = form.poolSource == source,
                        onClick = { actions.setPoolSource(source) },
                        shape = SegmentedButtonDefaults.itemShape(i, PoolSource.entries.size),
                        label = {
                            Text(
                                when (source) {
                                    PoolSource.ALL -> "All"
                                    PoolSource.OWNED -> "Owned"
                                    PoolSource.CUSTOM -> "Custom"
                                },
                            )
                        },
                    )
                }
            }
            Spacer(Modifier.height(Spacing.sm))
            when (form.poolSource) {
                PoolSource.ALL -> Hint("Every hero in the catalog.")

                PoolSource.OWNED -> Hint(
                    "Heroes from the products you own (${state.ownedProductsLabel}, set in Settings).",
                )

                PoolSource.CUSTOM -> OutlinedButton(onClick = {
                    choosing = true
                }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Choose heroes (${form.customSelection.size})")
                }
            }
            Spacer(Modifier.height(Spacing.md))
            HorizontalDivider(color = GoaTheme.colors.engravedBorder)
            Spacer(Modifier.height(Spacing.md))
            Text("Least experienced player", style = MaterialTheme.typography.labelLarge)
            Hint("Removes heroes above their complexity (rulebook).")
            Spacer(Modifier.height(Spacing.sm))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                FilterChip(
                    colors = goaFilterChipColors(),
                    selected = form.maxComplexity == null,
                    onClick = { actions.setMaxComplexity(null) },
                    label = { Text("Any") },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
                for (stars in 1..3) {
                    FilterChip(
                        colors = goaFilterChipColors(),
                        selected = form.maxComplexity == stars,
                        onClick = { actions.setMaxComplexity(stars) },
                        label = { Text("${"★".repeat(stars)} ${ComplexityFilter.name(stars)}") },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .semantics { contentDescription = "Up to $stars stars, ${ComplexityFilter.name(stars)}" },
                    )
                }
            }
        }
        SectionCard(title = "Step timer", subtitle = "Optional countdown on every ban and pick", trailing = {
            Switch(
                checked = form.stepTimer,
                onCheckedChange = actions::setStepTimer,
                modifier = Modifier.semantics { contentDescription = "Step timer" },
            )
        }) {
            if (form.stepTimer) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    listOf(15, 30, 45, 60, 90).forEach { s ->
                        FilterChip(
                            colors = goaFilterChipColors(),
                            selected = form.stepSeconds == s,
                            onClick = { actions.setStepSeconds(s) },
                            label = { Text("${s}s") },
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                }
            } else {
                Hint("Off — no countdown. Overtime is never enforced.")
            }
        }
    }
    if (choosing) {
        HeroChooserSheet(state, onDone = {
            actions.setCustomSelection(it)
            choosing = false
        })
    }
}

private fun originLabel(origin: MethodOrigin) = when (origin) {
    MethodOrigin.RULEBOOK -> "Rulebook"
    MethodOrigin.HOUSE -> "House"
    MethodOrigin.CUSTOM -> "Custom"
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * Custom pool picker. Opens fully expanded with the apply button pinned below the grid, and
 * dismissing the sheet (swipe, scrim, Back) applies the selection too: nothing chosen is lost.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HeroChooserSheet(state: SetupUiState, onDone: (Set<String>) -> Unit) {
    var selection by rememberSaveable { mutableStateOf(state.form.customSelection.toList()) }
    ModalBottomSheet(
        onDismissRequest = { onDone(selection.toSet()) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.padding(horizontal = Spacing.screen)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Custom pool · ${selection.size}",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { selection = state.heroes.map { it.id } }) { Text("All") }
                TextButton(onClick = { selection = emptyList() }) { Text("None") }
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(88.dp),
                // Takes the room left over; the button below always stays on screen.
                modifier = Modifier.weight(1f, fill = false),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                items(state.heroes, key = { it.id }) { hero ->
                    val on = hero.id in selection
                    HeroTile(
                        hero = hero,
                        state = if (on) TileState.Available else TileState.Inactive,
                        selected = on,
                        onClick = { selection = if (on) selection - hero.id else selection + hero.id },
                    )
                }
            }
            Button(
                onClick = { onDone(selection.toSet()) },
                modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.md).heightIn(min = 52.dp),
            ) { Text("Use ${plural(selection.size, "hero", "heroes")}") }
        }
    }
}

// ---------------------------------------------------------------- Coin

@Composable
private fun CoinStage(state: SetupUiState, actions: SetupActions) {
    val form = state.form
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Text(
            "The team showing on the Tie Breaker coin is Team A. Team A starts the draft.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        TieBreakerCoin(result = form.teamA, flipCount = form.flipCount)
        val teamA = form.teamA
        Text(
            text = if (teamA == null) "Not flipped yet" else "${teamLabel(teamA)} is Team A",
            style = MaterialTheme.typography.headlineSmall,
            color = teamA?.let { teamColor(it) } ?: MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = actions::flipCoin, modifier = Modifier.heightIn(min = 52.dp)) {
            Text(if (teamA == null) "Flip the coin" else "Flip again")
        }
        Text(
            "Flipped the real coin?",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Team.entries.forEach { team ->
                FilterChip(
                    colors = teamFilterChipColors(team),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = teamA == team,
                        borderColor = teamColor(team).copy(alpha = 0.6f),
                        selectedBorderColor = teamColor(team),
                    ),
                    selected = teamA == team,
                    onClick = { actions.setTeamA(team) },
                    label = { Text("${teamLabel(team)} shows") },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
        SummaryCard(state)
    }
}

@Composable
private fun SummaryCard(state: SetupUiState) {
    val form = state.form
    val method = state.selectedMethod ?: return
    SectionCard(title = method.method.name, subtitle = method.method.summary) {
        if (form.teamMode == TeamMode.PLAYER_DRAFT) {
            Hint("Heroes first, then captains draft players.")
        } else {
            if (method.preview.isNotEmpty()) SequenceStrip(method.preview)
            Spacer(Modifier.height(Spacing.md))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                for (team in Team.entries) {
                    Column(Modifier.weight(1f)) {
                        TeamBadge(team)
                        state.seats.filter { it.team == team }.forEach { seat ->
                            Text(
                                (state.player(seat.pilotId.orEmpty())?.player?.name ?: "—") +
                                    if (seat.handicap) " · Handicap" else "",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Previews

/** Synthetic state for previews and screenshot tests. */
internal fun previewSetupState(stage: SetupStage, teamMode: TeamMode = TeamMode.BALANCED): SetupUiState {
    val players = PreviewData.players.map { PlayerOption(it, PreviewData.ratings[it.id]) }
    val ids = players.map { it.player.id }
    val red = ids.filterIndexed { i, _ -> i % 2 == 0 }.take(3)
    val blue = ids.filterIndexed { i, _ -> i % 2 == 1 }.take(2)
    val seats = SeatLayouts.build(red, blue, OddSeatMode.DOUBLE_PILOT).getOrThrow()
    val method = PreviewData.pattern
    val preview = DraftPlanner.plan(method.sequence, 3, 3).getOrThrow().tokens(Team.BLUE)
    return SetupUiState(
        loading = false,
        form = SetupForm(
            stage = stage,
            selected = red + blue,
            teamMode = teamMode,
            red = red,
            blue = blue,
            locked = mapOf(red.first() to Team.RED),
            balanceDifference = 7.5,
            oddMode = OddSeatMode.DOUBLE_PILOT,
            methodId = method.id,
            redCaptain = red.first(),
            blueCaptain = blue.first(),
            stepTimer = true,
            teamA = Team.BLUE.takeIf { stage == SetupStage.COIN },
        ),
        players = players,
        seats = seats,
        methods = listOf(
            MethodOption(method, preview, null, 8),
            MethodOption(
                method.copy(
                    id = "other",
                    name = "Pick and Ban",
                    summary = "Competitive bans and picks.",
                    origin = MethodOrigin.RULEBOOK,
                ),
                emptyList(),
                null,
                12,
            ),
        ),
        heroes = PreviewData.heroes.values.toList(),
        candidateCount = 18,
        resume = ResumeInfo("Preview draft", "Step 3 of 8").takeIf { stage == SetupStage.PLAYERS },
    )
}

@Preview
@Composable
private fun SetupPreview() {
    GoaTheme(ThemeMode.DARK) {
        DraftSetupScreen(previewSetupState(SetupStage.TEAMS), SetupActions.None)
    }
}

@Composable
private fun activeColor(active: Boolean) =
    if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
