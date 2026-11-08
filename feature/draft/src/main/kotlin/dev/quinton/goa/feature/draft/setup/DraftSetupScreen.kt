package dev.quinton.goa.feature.draft.setup

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTooltipState
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
import androidx.compose.ui.platform.LocalDensity
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
import dev.quinton.goa.core.designsystem.component.BannerTextButton
import dev.quinton.goa.core.designsystem.component.BarAction
import dev.quinton.goa.core.designsystem.component.BarActions
import dev.quinton.goa.core.designsystem.component.BarIconButton
import dev.quinton.goa.core.designsystem.component.ConfirmDialog
import dev.quinton.goa.core.designsystem.component.EmptyState
import dev.quinton.goa.core.designsystem.component.GoaButton
import dev.quinton.goa.core.designsystem.component.GoaFilterChip
import dev.quinton.goa.core.designsystem.component.GoaOutlinedButton
import dev.quinton.goa.core.designsystem.component.GoaPlate
import dev.quinton.goa.core.designsystem.component.GoaScreen
import dev.quinton.goa.core.designsystem.component.GoaSegment
import dev.quinton.goa.core.designsystem.component.GoaSegmentedRow
import dev.quinton.goa.core.designsystem.component.GoaSheet
import dev.quinton.goa.core.designsystem.component.GoaSwitch
import dev.quinton.goa.core.designsystem.component.LARGE_FONT_SCALE
import dev.quinton.goa.core.designsystem.component.PlateShape
import dev.quinton.goa.core.designsystem.component.PlayerAvatar
import dev.quinton.goa.core.designsystem.component.TeamBadge
import dev.quinton.goa.core.designsystem.component.TitlePlate
import dev.quinton.goa.core.designsystem.component.teamColor
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
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
import dev.quinton.goa.feature.draft.ui.PLATE_BRONZE
import dev.quinton.goa.feature.draft.ui.PlateBar
import dev.quinton.goa.feature.draft.ui.PreviewData
import dev.quinton.goa.feature.draft.ui.SectionCard
import dev.quinton.goa.feature.draft.ui.SequenceStrip
import dev.quinton.goa.feature.draft.ui.TeamText
import dev.quinton.goa.feature.draft.ui.TieBreakerCoin
import dev.quinton.goa.feature.draft.ui.TileState
import dev.quinton.goa.feature.draft.ui.coinTeamWords
import dev.quinton.goa.feature.draft.ui.goaTonalButtonColors
import dev.quinton.goa.feature.draft.ui.plateEdge
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

    /**
     * Leaves the draft for the screen that opened it (the bar's back arrow). Nothing to confirm: every choice is
     * saved as it is made, and the setup opens where it was left.
     */
    fun exit() {}

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
    onExit: () -> Unit = {},
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
            override fun exit() = onExit()
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
        // The bar's arrow leaves the draft, as on every sub-screen; Back in the bottom bar steps back a stage.
        onBack = actions::exit,
        actions = {
            // The list glyph alone doesn't say "methods": it is named on the bar, the gear beside it. At large text
            // the word gives way to the glyph (labelled for TalkBack and on a long press).
            val methods = BarAction("Draft methods", Icons.AutoMirrored.Outlined.ListAlt, actions::manageMethods)
            if (LocalDensity.current.fontScale < LARGE_FONT_SCALE) {
                BannerTextButton(
                    "Methods",
                    onClick = methods.onClick,
                    icon = methods.icon,
                    modifier = Modifier.semantics { contentDescription = methods.label },
                )
                BarIconButton(BarAction("Settings", Icons.Outlined.Settings, actions::openSettings))
            } else {
                BarActions(listOf(methods, BarAction("Settings", Icons.Outlined.Settings, actions::openSettings)))
            }
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
                        // The step tabs above already end in their own padding: a small top keeps the stage's
                        // controls above the footer.
                        contentPadding = PaddingValues(
                            start = Spacing.screen,
                            end = Spacing.screen,
                            top = Spacing.sm,
                            bottom = Spacing.screen,
                        ),
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
    PlateBar {
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
                    GoaOutlinedButton(onClick = actions::back, modifier = Modifier.heightIn(min = 52.dp)) {
                        Text("Back")
                    }
                }
                val last = state.stage == SetupStage.COIN
                GoaButton(
                    onClick = if (last) actions::start else actions::next,
                    enabled = state.blocker == null && !state.form.starting,
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                ) {
                    if (last) {
                        Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(Spacing.sm))
                    }
                    Text(state.primaryLabel)
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
                message = "Add players above.",
                modifier = Modifier.fillMaxWidth(),
                action = {
                    GoaOutlinedButton(onClick = actions::openPlayerEditor) {
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
                    state.lastGameNote?.let { note ->
                        Text(
                            note,
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
    GoaPlate(Modifier.fillMaxWidth().plateEdge(PLATE_BRONZE, 1.dp), contentPadding = PaddingValues(0.dp)) {
        Column(Modifier.fillMaxWidth().padding(Spacing.lg)) {
            Text(
                if (resume.complete) "Draft complete" else "Draft in progress",
                style = GoaTextStyles.sectionHead,
                color = GoaTheme.colors.heading,
            )
            Text("${resume.title} · ${resume.detail}", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(Spacing.md))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                GoaButton(onClick = actions::resume, modifier = Modifier.heightIn(min = 48.dp)) {
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
    GoaPlate(
        Modifier
            .fillMaxWidth()
            .plateEdge(if (selected) PLATE_BRONZE else null)
            .clip(PlateShape)
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onToggle() }),
        contentPadding = PaddingValues(0.dp),
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
                    option.ratingLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Checkbox(checked = selected, onCheckedChange = null)
        }
    }
}

// ---------------------------------------------------------------- Teams

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TeamsStage(state: SetupUiState, actions: SetupActions) {
    val form = state.form
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        GoaSegmentedRow(Modifier.fillMaxWidth()) {
            TeamMode.entries.forEachIndexed { i, mode ->
                GoaSegment(
                    selected = form.teamMode == mode,
                    onClick = { actions.setTeamMode(mode) },
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
        subtitle = form.balanceDifference?.let { "Average Elo differs by ${it.roundToInt()}" },
        trailing = if (balanced) {
            {
                // The shared secondary button: steel with light words, readable at the table.
                GoaOutlinedButton(onClick = actions::rebalance, modifier = Modifier.heightIn(min = 48.dp)) {
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

@OptIn(ExperimentalMaterial3Api::class)
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
        shape = RoundedCornerShape(6.dp),
        // A row of the teams' plate: the team's faint tint, no edge of its own (DESIGN.md "Framing levels"); the
        // column's head names the team with its mark.
        color = color.copy(alpha = 0.10f),
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
                // A 48 dp toggle: "Keep Ada on Red when re-balancing", locked or not. What it does is its label and,
                // on a long press, its tooltip, so the teams keep their room.
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                    tooltip = { PlainTooltip { Text(LOCK_TIP) } },
                    state = rememberTooltipState(),
                ) {
                    IconToggleButton(
                        checked = locked,
                        onCheckedChange = { onLock() },
                        modifier = Modifier.size(48.dp).semantics {
                            contentDescription = "Keep $name on ${teamName(team)}"
                            stateDescription = if (locked) "Locked" else "Not locked"
                        },
                    ) {
                        Icon(
                            if (locked) Icons.Filled.Lock else Icons.Outlined.LockOpen,
                            contentDescription = null,
                            tint = activeColor(locked),
                        )
                    }
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
            val name = state.soloPlayerName
            InfoCard(
                "Official 3-player setup",
                "$name controls two heroes and replaces a gold card of one of them with its Handicap card. " +
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
    val smaller = state.doublePilotChoices
    val current = state.currentDoublePilot
    Column {
        Text("Who pilots two heroes?", style = MaterialTheme.typography.labelLarge)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            smaller.forEach { id ->
                GoaFilterChip(
                    selected = id == current,
                    onClick = { actions.setDoublePilot(id) },
                    label = state.player(id)?.player?.name ?: "Unknown",
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
            DraftSetupPresenter.playerDraftExplanation(state.playerCount),
        )
        for (team in Team.entries) {
            SectionCard(title = "${teamName(team)} captain", titleTeam = team) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    form.selected.forEach { id ->
                        val chosen = if (team == Team.RED) form.redCaptain == id else form.blueCaptain == id
                        GoaFilterChip(
                            selected = chosen,
                            onClick = { actions.setCaptain(team, id) },
                            modifier = Modifier.heightIn(min = 48.dp),
                            leadingIcon = { PlayerAvatar(state.player(id)?.player, size = 24.dp) },
                        ) { Text(state.player(id)?.player?.name ?: "Unknown") }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    GoaPlate(Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
        Column(Modifier.fillMaxWidth().padding(Spacing.md)) {
            Text(title, style = GoaTextStyles.sectionHead, color = GoaTheme.colors.heading)
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
            trailing = { TextButton(onClick = actions::manageMethods) { Text("Compare all") } },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                state.methods.forEach { option ->
                    val selected = option.method.id == form.methodId
                    ChoiceCard(
                        title = option.method.name,
                        badge = DraftSetupPresenter.originLabel(option.method),
                        // What a method does is said for the picked one only; the others are their names.
                        body = option.unavailable ?: if (selected) coinTeamWords(option.method.summary) else "",
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
            GoaSegmentedRow(Modifier.fillMaxWidth()) {
                PoolSource.entries.forEachIndexed { i, source ->
                    GoaSegment(
                        selected = form.poolSource == source,
                        onClick = { actions.setPoolSource(source) },
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
                PoolSource.ALL -> Unit

                PoolSource.OWNED -> Hint(state.ownedProductsLabel)

                PoolSource.CUSTOM -> GoaOutlinedButton(onClick = {
                    choosing = true
                }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Choose heroes (${form.customSelection.size})")
                }
            }
            Spacer(Modifier.height(Spacing.md))
            HorizontalDivider(color = GoaTheme.colors.engravedBorder)
            Spacer(Modifier.height(Spacing.md))
            Text("Least experienced player", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(Spacing.sm))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                GoaFilterChip(
                    selected = form.maxComplexity == null,
                    onClick = { actions.setMaxComplexity(null) },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("Any") }
                for (stars in 1..3) {
                    GoaFilterChip(
                        selected = form.maxComplexity == stars,
                        onClick = { actions.setMaxComplexity(stars) },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .semantics { contentDescription = "Up to $stars stars, ${ComplexityFilter.name(stars)}" },
                    ) { Text("${"★".repeat(stars)} ${ComplexityFilter.name(stars)}") }
                }
            }
        }
        SectionCard(title = "Step timer", trailing = {
            GoaSwitch(
                checked = form.stepTimer,
                onCheckedChange = actions::setStepTimer,
                modifier = Modifier.semantics { contentDescription = "Step timer" },
            )
        }) {
            if (form.stepTimer) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    listOf(15, 30, 45, 60, 90).forEach { s ->
                        GoaFilterChip(
                            selected = form.stepSeconds == s,
                            onClick = { actions.setStepSeconds(s) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) { Text("${s}s") }
                    }
                }
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
    GoaSheet(
        onDismissRequest = { onDone(selection.toSet()) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.padding(horizontal = Spacing.screen)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TitlePlate("Custom pool · ${selection.size}", Modifier.weight(1f), gear = false)
                TextButton(onClick = { selection = state.heroes.map { it.id } }) { Text("All") }
                TextButton(onClick = { selection = emptyList() }) { Text("None") }
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(88.dp),
                // Takes the room left over; the button below always stays on screen.
                modifier = Modifier.weight(1f, fill = false),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
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
            GoaButton(
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
            "The team the Tie Breaker coin shows is the coin team: it starts the draft.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        TieBreakerCoin(result = form.teamA, flipCount = form.flipCount)
        val teamA = form.teamA
        TeamText(
            text = if (teamA == null) "Not flipped yet" else "${teamLabel(teamA)} is the coin team",
            style = MaterialTheme.typography.headlineSmall,
            color = teamA?.let { teamColor(it) } ?: MaterialTheme.colorScheme.onSurfaceVariant,
        )
        GoaButton(onClick = actions::flipCoin, modifier = Modifier.heightIn(min = 52.dp)) {
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
                    label = { TeamText("${teamLabel(team)} shows") },
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
    SectionCard(title = method.method.name) {
        if (form.teamMode != TeamMode.PLAYER_DRAFT) {
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

/** A padlock's tooltip: what locking does. */
private const val LOCK_TIP = "Locked players stay put when you re-balance"
