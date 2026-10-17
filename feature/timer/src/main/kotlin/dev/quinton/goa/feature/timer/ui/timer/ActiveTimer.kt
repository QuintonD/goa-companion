package dev.quinton.goa.feature.timer.ui.timer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.MilitaryTech
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.Toll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import dev.quinton.goa.core.designsystem.component.LARGE_FONT_SCALE
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.domain.timer.RoundSection
import dev.quinton.goa.feature.timer.ui.components.AlertsOffBanner
import dev.quinton.goa.feature.timer.ui.components.CountdownRing
import dev.quinton.goa.feature.timer.ui.components.KeepScreenOn
import dev.quinton.goa.feature.timer.ui.components.RingTone
import dev.quinton.goa.feature.timer.ui.components.RoundPips
import dev.quinton.goa.feature.timer.ui.components.goaTonalIconColors
import dev.quinton.goa.feature.timer.ui.components.rememberReducedMotion
import dev.quinton.goa.feature.timer.ui.components.timerDigits

@Composable
internal fun ActiveTimer(state: ActiveUiState, actions: TimerActions) {
    var confirmEnd by remember { mutableStateOf(false) }
    var coinDialog by remember { mutableStateOf(false) }
    KeepScreenOn()
    VolumeKeyControls(
        enabled = state.volumeKeys,
        focusKey = "${state.round}:${state.title}:${state.context}",
        onNext = actions.onNext,
        onTogglePause = actions.onTogglePause,
    ) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            BoxWithConstraints(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(
                        WindowInsets.statusBars.union(WindowInsets.displayCutout)
                            .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                    ),
            ) {
                val header: @Composable () -> Unit = {
                    TimerHeader(state, actions, onEnd = { confirmEnd = true }, onCoin = { coinDialog = true })
                    if (state.alertsOff) {
                        AlertsOffBanner(
                            onOpenSettings = actions.onOpenNotificationSettings,
                            onDismiss = actions.onDismissAlertsBanner,
                            modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.sm),
                        )
                    }
                }
                if (maxWidth > maxHeight) {
                    LandscapeTimer(state, actions, header)
                } else {
                    PortraitTimer(state, actions, header)
                }
            }
        }
    }
    if (confirmEnd) {
        AlertDialog(
            onDismissRequest = { confirmEnd = false },
            icon = { Icon(Icons.Outlined.Flag, contentDescription = null) },
            title = { Text("End the match?") },
            text = {
                Text(
                    "A match can end mid-turn: the last push, a push into the Throne Zone, or a team's last " +
                        "Life counter. The timer stops and the duration is kept for the match log.",
                )
            },
            confirmButton = {
                Button(onClick = {
                    confirmEnd = false
                    actions.onEndMatch()
                }) { Text("End match") }
            },
            dismissButton = { TextButton(onClick = { confirmEnd = false }) { Text("Keep playing") } },
        )
    }
    if (coinDialog) {
        CoinSideDialog(
            current = state.tieBreaker,
            onPick = {
                coinDialog = false
                actions.onSetCoin(it)
            },
            onDismiss = { coinDialog = false },
        )
    }
}

/** Portrait: header, then a scrolling column (pips, phase, dial, extras), controls at the bottom. */
@Composable
private fun PortraitTimer(state: ActiveUiState, actions: TimerActions, header: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        header()
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val ringMax = min(maxWidth - Spacing.xl * 2, maxHeight * 0.62f).coerceAtLeast(160.dp)
            val viewport = maxHeight
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = viewport)
                    .padding(horizontal = Spacing.screen),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                RoundPips(state.pips, state.round, Modifier.padding(top = Spacing.sm))
                Spacer(Modifier.height(Spacing.lg))
                PhaseHeading(state, actions.onBack)
                Spacer(Modifier.height(Spacing.lg))
                PrimaryArea(state, actions, ringMax * dialScale(state))
                StepDetail(state, actions.onOpenLevels)
                SecondaryArea(state, actions)
                Spacer(Modifier.height(Spacing.lg))
            }
        }
        PinnedShotClock(state, actions, compact = false)
        TimerControls(state, actions)
    }
}

/** A running "Hurry up" sits above the controls, outside the scrolling area, so it is always in view. */
@Composable
private fun PinnedShotClock(state: ActiveUiState, actions: TimerActions, compact: Boolean) {
    val shot = state.shotClock ?: return
    ShotClockPanel(
        shot,
        onRestart = actions.onShotClock,
        onCancel = actions.onCancelShotClock,
        compact = compact,
        modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.xs),
    )
}

/**
 * Landscape: the dial fills the left half (a running Hurry up under it); header, phase, extras
 * and controls sit on the right.
 */
@Composable
private fun LandscapeTimer(state: ActiveUiState, actions: TimerActions, header: @Composable () -> Unit) {
    Row(Modifier.fillMaxSize()) {
        // A running Hurry up sits under the dial here, leaving the right column to the step text.
        Column(Modifier.weight(0.45f).fillMaxHeight()) {
            BoxWithConstraints(
                Modifier.weight(1f).fillMaxWidth().padding(Spacing.md),
                contentAlignment = Alignment.Center,
            ) {
                if (state.checklist != null && !state.timed) {
                    Column(Modifier.verticalScroll(rememberScrollState())) { ChecklistStepCard(state, actions.onNext) }
                } else {
                    TimerDial(state, min(maxWidth, maxHeight).coerceAtLeast(120.dp), actions.onTogglePause)
                }
            }
            PinnedShotClock(state, actions, compact = true)
        }
        Column(Modifier.weight(0.55f).fillMaxHeight()) {
            header()
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.screen),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                RoundPips(state.pips, state.round, Modifier.padding(top = Spacing.xs))
                Spacer(Modifier.height(Spacing.sm))
                PhaseHeading(state, actions.onBack)
                StepDetail(state, actions.onOpenLevels)
                SecondaryArea(state, actions)
                Spacer(Modifier.height(Spacing.md))
            }
            TimerControls(state, actions, compact = true)
        }
    }
}

/** The dial shrinks where more must fit under it: hero tiles (Action) or the checklist (Upgrade). */
private fun dialScale(state: ActiveUiState): Float = when {
    state.seats != null -> 0.55f
    state.checklist != null -> 0.8f
    else -> 1f
}

@Composable
private fun PrimaryArea(state: ActiveUiState, actions: TimerActions, size: Dp) {
    if (state.checklist == null || state.timed) {
        TimerDial(state, size, actions.onTogglePause)
    } else {
        ChecklistStepCard(state, actions.onNext)
    }
}

/** The rule text of a timed end-of-round step (Level Up: mandatory), shown under the dial. */
@Composable
private fun StepDetail(state: ActiveUiState, onOpenLevels: () -> Unit) {
    val detail = state.currentDetail?.takeIf { state.timed } ?: return
    Spacer(Modifier.height(Spacing.md))
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, GoaTheme.colors.engravedBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            detail,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
        )
    }
    // Timed Level Up: the level ladder sits right under the rule, above the fold.
    if (state.checklist?.any { it.state == ChecklistState.CURRENT && it.id == LEVEL_UP_STEP } == true) {
        Spacer(Modifier.height(Spacing.sm))
        LevelLadderButton(onOpenLevels, label = "Level costs & card Tiers")
    }
}

@Composable
private fun SecondaryArea(state: ActiveUiState, actions: TimerActions) {
    TableRulesBar(state, actions)
    state.seats?.let { seats ->
        Spacer(Modifier.height(Spacing.lg))
        ResolvingHeroes(seats, actions.onHeroResolves)
    }
    state.checklist?.let { items ->
        Spacer(Modifier.height(Spacing.lg))
        // Untimed steps: the ladder button sits in the Level Up row (timed: under the rule, StepDetail).
        EndOfRoundChecklist(items, actions.onOpenLevels.takeIf { !state.timed })
    }
}

@Composable
private fun TimerHeader(state: ActiveUiState, actions: TimerActions, onEnd: () -> Unit, onCoin: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = Spacing.screen, end = Spacing.xs, top = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "Round ${state.round}",
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                "${state.presetName} · ${state.matchClock}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics {
                    contentDescription =
                        "${state.presetName}, match time ${state.matchClock}"
                },
            )
        }
        TimerTopActions(state, actions, onEnd, onCoin)
    }
}

/**
 * Top-bar actions, left to right: Tie Breaker coin (when tracked), Levels & defeats (rules
 * reference), End match, overflow menu (with Rules).
 */
@Composable
private fun RowScope.TimerTopActions(
    state: ActiveUiState,
    actions: TimerActions,
    onEnd: () -> Unit,
    onCoin: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    if (state.tieBreaker != null) {
        TieBreakerChip(
            state.tieBreaker,
            actions.onFlipCoin,
            actionKey = "${state.round}:${state.title}:${state.paused}",
        )
    }
    // Large text needs the room for "Round N": the ladder moves into the menu.
    val largeText = LocalDensity.current.fontScale > LARGE_FONT_SCALE
    if (!largeText) {
        IconButton(onClick = actions.onOpenLevels) {
            Icon(Icons.Outlined.MilitaryTech, contentDescription = "Levels and defeats")
        }
    }
    IconButton(onClick = onEnd) { Icon(Icons.Outlined.Flag, contentDescription = "End match") }
    Box {
        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "More options") }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text("Restart phase") },
                leadingIcon = { Icon(Icons.Outlined.Replay, contentDescription = null) },
                onClick = {
                    menu = false
                    actions.onRestart()
                },
            )
            DropdownMenuItem(
                text = { Text("Skip to end of round") },
                leadingIcon = { Icon(Icons.Outlined.SkipNext, contentDescription = null) },
                enabled = state.section != RoundSection.ROUND_END,
                onClick = {
                    menu = false
                    actions.onSkipToEnd()
                },
            )
            DropdownMenuItem(
                text = { Text(if (state.tieBreaker == null) "Track Tie Breaker coin…" else "Tie Breaker coin…") },
                leadingIcon = { Icon(Icons.Outlined.Toll, contentDescription = null) },
                onClick = {
                    menu = false
                    onCoin()
                },
            )
            DropdownMenuItem(
                text = { Text("End match…") },
                leadingIcon = { Icon(Icons.Outlined.Flag, contentDescription = null) },
                onClick = {
                    menu = false
                    onEnd()
                },
            )
            if (largeText) {
                DropdownMenuItem(
                    text = { Text("Levels & defeats") },
                    leadingIcon = { Icon(Icons.Outlined.MilitaryTech, contentDescription = null) },
                    onClick = {
                        menu = false
                        actions.onOpenLevels()
                    },
                )
            }
            DropdownMenuItem(
                text = { Text("Rules") },
                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null) },
                onClick = {
                    menu = false
                    actions.onOpenRules()
                },
            )
            DropdownMenuItem(
                text = { Text("Settings") },
                leadingIcon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                onClick = {
                    menu = false
                    actions.onSettings()
                },
            )
        }
    }
}

/**
 * The step chip with "Previous phase" beside it, and the step title and hint. Previous sits up here,
 * away from the bottom edge: with no tab bar on the running timer, a habitual tap where the Heroes
 * tab usually is went back a phase (game night).
 */
@Composable
private fun PhaseHeading(state: ActiveUiState, onBack: () -> Unit) {
    val reduced = rememberReducedMotion()
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(
                Icons.Filled.SkipPrevious,
                contentDescription = "Previous phase",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            border = BorderStroke(1.dp, GoaTheme.colors.engravedBorder),
            modifier = Modifier.weight(1f, fill = false),
        ) {
            Text(
                state.context.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                textAlign = TextAlign.Center,
            )
        }
        // Balances the button so the chip stays centred.
        Spacer(Modifier.width(48.dp))
    }
    Spacer(Modifier.height(Spacing.sm))
    AnimatedContent(
        targetState = state.title,
        transitionSpec = {
            val ms = if (reduced) 0 else 250
            fadeIn(tween(ms)) togetherWith fadeOut(tween(ms))
        },
        label = "phase",
    ) { title ->
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
    state.hint?.let { hint ->
        Text(
            hint,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
        )
    }
}

@Composable
private fun TimerDial(state: ActiveUiState, size: Dp, onToggle: () -> Unit) {
    val tone = when {
        !state.timed -> RingTone.UNTIMED
        state.paused -> RingTone.PAUSED
        state.overtime -> RingTone.OVERTIME
        state.warning -> RingTone.WARNING
        else -> RingTone.NORMAL
    }
    val status = when {
        state.paused -> "Tap to resume"
        state.overtime -> "Overtime"
        !state.timed -> "Elapsed · tap to pause"
        else -> "Tap to pause"
    }
    val digitColor = when (tone) {
        RingTone.OVERTIME -> MaterialTheme.colorScheme.error
        RingTone.WARNING -> MaterialTheme.colorScheme.primary
        RingTone.PAUSED -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }
    val density = LocalDensity.current
    // Sized from the ring, not the font scale: the digits are already as big as they can be.
    val digitSize = with(density) { (size * if (state.clock.length > 5) 0.2f else 0.24f).toSp() }
    val labelSize = with(density) { (size * 0.05f).coerceAtLeast(12.dp).toSp() }
    val pausedSize = with(density) { (size * 0.085f).coerceAtLeast(16.dp).toSp() }
    // Paused must read from across the table: the digits blink slowly (unless reduced motion).
    val reduced = rememberReducedMotion()
    val blink = if (state.paused && !reduced) {
        rememberInfiniteTransition(label = "paused").animateFloat(
            initialValue = 1f,
            targetValue = 0.3f,
            animationSpec = infiniteRepeatable(tween(PAUSE_BLINK_MS), RepeatMode.Reverse),
            label = "blink",
        ).value
    } else {
        1f
    }
    CountdownRing(
        fraction = state.fraction,
        tone = tone,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .clickable(onClickLabel = if (state.paused) "Resume" else "Pause", role = Role.Button, onClick = onToggle)
            .semantics(mergeDescendants = true) {
                contentDescription = "${state.title}. ${state.spokenClock}"
                stateDescription = if (state.paused) "Paused" else "Running"
            },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (state.paused) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Pause,
                        contentDescription = null,
                        modifier = Modifier.size(with(density) { pausedSize.toDp() }),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "PAUSED",
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = pausedSize),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(
                state.clock,
                style = timerDigits(
                    MaterialTheme.typography.displayLarge,
                ).copy(fontSize = digitSize, lineHeight = digitSize),
                color = digitColor,
                modifier = Modifier.alpha(blink),
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                status.uppercase(),
                style = MaterialTheme.typography.labelLarge.copy(fontSize = labelSize),
                color = with(MaterialTheme.colorScheme) { if (state.overtime) error else onSurfaceVariant },
            )
        }
    }
}

/** Untimed end-of-round step: a medallion with the step number and the rule text; tap = Next. */
@Composable
private fun ChecklistStepCard(state: ActiveUiState, onNext: () -> Unit) {
    val current = state.checklist?.firstOrNull { it.state == ChecklistState.CURRENT }
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(colors.surface)
            .border(1.dp, GoaTheme.colors.engravedBorder, MaterialTheme.shapes.large)
            .clickable(onClickLabel = "Step done, next", role = Role.Button, onClick = onNext)
            .padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(colors.primary.copy(alpha = 0.14f))
                .border(2.dp, colors.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                current?.number?.toString() ?: "✓",
                style = MaterialTheme.typography.headlineMedium,
                color = colors.primary,
            )
        }
        Spacer(Modifier.height(Spacing.md))
        current?.detail?.let {
            Text(it, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.height(Spacing.sm))
        }
        Text(
            "${state.clock} at this step · tap when done",
            style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun EndOfRoundChecklist(items: List<ChecklistItemUi>, onOpenLevels: (() -> Unit)?) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, GoaTheme.colors.engravedBorder, MaterialTheme.shapes.large)
            .padding(vertical = Spacing.sm),
    ) {
        Text(
            "END OF ROUND",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm).semantics { heading() },
        )
        items.forEach { item ->
            val levelUpNow = item.state == ChecklistState.CURRENT && item.id == LEVEL_UP_STEP
            ChecklistRow(item, onOpenLevels?.takeIf { levelUpNow })
        }
    }
}

@Composable
private fun ChecklistRow(item: ChecklistItemUi, onOpenLevels: (() -> Unit)? = null) {
    val colors = MaterialTheme.colorScheme
    val stateText = when (item.state) {
        ChecklistState.DONE -> "done"
        ChecklistState.CURRENT -> "current step"
        ChecklistState.UPCOMING -> "upcoming"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(
                if (item.state == ChecklistState.CURRENT) colors.secondary.copy(alpha = 0.10f) else colors.surface,
            )
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
            .semantics(mergeDescendants = true) { stateDescription = stateText },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val badge = when (item.state) {
            ChecklistState.DONE -> colors.primary.copy(alpha = 0.55f)
            ChecklistState.CURRENT -> colors.secondary
            ChecklistState.UPCOMING -> colors.onSurfaceVariant.copy(alpha = 0.5f)
        }
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(if (item.state == ChecklistState.UPCOMING) colors.surface else badge)
                .border(1.5.dp, badge, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (item.state == ChecklistState.DONE) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = colors.onPrimary,
                    modifier = Modifier.size(16.dp),
                )
            } else {
                Text(
                    item.number.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (item.state == ChecklistState.CURRENT) colors.onSecondary else colors.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                style = MaterialTheme.typography.titleSmall,
                color = if (item.state == ChecklistState.UPCOMING) colors.onSurfaceVariant else colors.onSurface,
                modifier = Modifier.alpha(if (item.state == ChecklistState.DONE) 0.7f else 1f),
            )
            if (item.timerName != null) {
                Text(item.timerName, style = MaterialTheme.typography.bodySmall, color = colors.secondary)
            }
        }
        onOpenLevels?.let { LevelLadderButton(it) }
    }
}

/** End-of-round id of the Level Up step (match-setup.json). */
private const val LEVEL_UP_STEP = "level_up"

/** On the current Level Up step: the level ladder (costs, max Tier) one tap away. */
@Composable
private fun LevelLadderButton(onClick: () -> Unit, label: String = "Levels") {
    OutlinedButton(onClick = onClick, contentPadding = PaddingValues(horizontal = Spacing.md)) {
        Icon(Icons.Outlined.MilitaryTech, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(Spacing.xs))
        Text(label)
    }
}

/** Pause and Next. "Previous phase" is up by the step chip ([PhaseHeading]), away from the edge. */
@Composable
private fun TimerControls(state: ActiveUiState, actions: TimerActions, compact: Boolean = false) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
        Row(
            Modifier.fillMaxWidth().padding(
                horizontal = Spacing.screen,
                vertical = if (compact) Spacing.sm else Spacing.md,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            FilledTonalIconButton(
                onClick = actions.onTogglePause,
                modifier = Modifier.size(56.dp),
                colors = goaTonalIconColors(),
            ) {
                Icon(
                    if (state.paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    contentDescription = if (state.paused) "Resume" else "Pause",
                )
            }
            Button(
                onClick = actions.onNext,
                modifier = Modifier.weight(1f).heightIn(min = if (compact) 56.dp else 64.dp),
                shape = RoundedCornerShape(20.dp),
            ) {
                Text(
                    state.nextLabel,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(Spacing.sm))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
            }
        }
    }
}

private const val PAUSE_BLINK_MS = 900
