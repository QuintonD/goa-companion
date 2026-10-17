package dev.quinton.goa.feature.timer.ui.timer

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import dev.quinton.goa.core.designsystem.component.HeroPortrait
import dev.quinton.goa.core.designsystem.component.TeamBadge
import dev.quinton.goa.core.designsystem.component.teamColor
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
import dev.quinton.goa.feature.timer.ui.components.teamName
import dev.quinton.goa.feature.timer.ui.components.timerDigits

@Composable
internal fun ActiveTimer(state: ActiveUiState, actions: TimerActions) {
    var confirmEnd by remember { mutableStateOf(false) }
    KeepScreenOn()
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            TimerHeader(state, actions, onEnd = { confirmEnd = true })
            if (state.alertsOff) {
                AlertsOffBanner(
                    onOpenSettings = actions.onOpenNotificationSettings,
                    onDismiss = actions.onDismissAlertsBanner,
                    modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.sm),
                )
            }
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
                    PhaseHeading(state)
                    Spacer(Modifier.height(Spacing.lg))
                    val inChecklist = state.checklist != null
                    if (!inChecklist || state.timed) {
                        TimerDial(state, if (inChecklist) ringMax * 0.62f else ringMax, actions.onTogglePause)
                    } else {
                        ChecklistStepCard(state, actions.onTogglePause)
                    }
                    state.seats?.let { seats ->
                        Spacer(Modifier.height(Spacing.lg))
                        ResolvingHeroes(seats, actions.onHeroResolves)
                    }
                    state.checklist?.let { items ->
                        Spacer(Modifier.height(Spacing.lg))
                        EndOfRoundChecklist(items)
                    }
                    Spacer(Modifier.height(Spacing.lg))
                }
            }
            TimerControls(state, actions)
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
}

@Composable
private fun TimerHeader(state: ActiveUiState, actions: TimerActions, onEnd: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
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
        if (state.tieBreaker != null) TieBreakerChip(state, actions.onFlipCoin)
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
                if (state.tieBreaker == null) {
                    DropdownMenuItem(
                        text = { Text("Track Tie Breaker coin") },
                        onClick = {
                            menu = false
                            actions.onFlipCoin()
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text("End match…") },
                    leadingIcon = { Icon(Icons.Outlined.Flag, contentDescription = null) },
                    onClick = {
                        menu = false
                        onEnd()
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
}

/** Coin side currently showing; tap when the coin is flipped at the table. */
@Composable
private fun TieBreakerChip(state: ActiveUiState, onFlip: () -> Unit) {
    val team = state.tieBreaker ?: return
    val color = teamColor(team)
    Surface(
        onClick = onFlip,
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.6f)),
        modifier = Modifier
            .heightIn(min = 48.dp)
            .padding(vertical = Spacing.xs)
            .semantics {
                contentDescription = "Tie Breaker coin shows ${teamName(team)}"
                stateDescription = teamName(team)
            },
    ) {
        Row(
            Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TeamBadge(team)
        }
    }
}

@Composable
private fun PhaseHeading(state: ActiveUiState) {
    val reduced = rememberReducedMotion()
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, GoaTheme.colors.engravedBorder),
    ) {
        Text(
            state.context.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            textAlign = TextAlign.Center,
        )
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
        state.paused -> "Paused · tap to resume"
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
            Text(
                state.clock,
                style = timerDigits(
                    MaterialTheme.typography.displayLarge,
                ).copy(fontSize = digitSize, lineHeight = digitSize),
                color = digitColor,
            )
            Spacer(Modifier.height(Spacing.xs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.paused) {
                    Icon(
                        Icons.Filled.Pause,
                        contentDescription = null,
                        modifier = Modifier.size(with(density) { labelSize.toDp() }),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(Spacing.xs))
                }
                Text(
                    status.uppercase(),
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = labelSize),
                    color = with(MaterialTheme.colorScheme) { if (state.overtime) error else onSurfaceVariant },
                )
            }
        }
    }
}

/** Untimed end-of-round step: a medallion with the step number and the rule text. */
@Composable
private fun ChecklistStepCard(state: ActiveUiState, onToggle: () -> Unit) {
    val current = state.checklist?.firstOrNull { it.state == ChecklistState.CURRENT }
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(colors.surface)
            .border(1.dp, GoaTheme.colors.engravedBorder, MaterialTheme.shapes.large)
            .clickable(onClickLabel = if (state.paused) "Resume" else "Pause", onClick = onToggle)
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
            "${state.clock} at this step",
            style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
            color = colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun EndOfRoundChecklist(items: List<ChecklistItemUi>) {
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
        items.forEach { item -> ChecklistRow(item) }
    }
}

@Composable
private fun ChecklistRow(item: ChecklistItemUi) {
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
                if (item.state ==
                    ChecklistState.CURRENT
                ) {
                    colors.secondary.copy(alpha = 0.10f)
                } else {
                    colors.surface
                },
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
    }
}

private const val SEATS_PER_ROW = 3

/** Action timer: tap the hero who resolves now (highest unresolved initiative acts first). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResolvingHeroes(seats: List<SeatUi>, onResolve: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            "Who resolves now? Highest initiative acts first.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Spacing.sm),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            maxItemsInEachRow = SEATS_PER_ROW,
        ) {
            seats.forEach { seat -> SeatChip(seat, Modifier.weight(1f)) { onResolve(seat.seat) } }
            // Keep the last row's chips the same width as the rows above.
            repeat((SEATS_PER_ROW - seats.size % SEATS_PER_ROW) % SEATS_PER_ROW) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun SeatChip(seat: SeatUi, modifier: Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val team = teamColor(seat.team)
    val status = when {
        seat.acting -> "resolving now"
        seat.resolved -> "already resolved"
        else -> "not resolved yet"
    }
    Surface(
        onClick = onClick,
        enabled = !seat.resolved && !seat.acting,
        shape = MaterialTheme.shapes.medium,
        color = if (seat.acting) colors.secondary.copy(alpha = 0.16f) else colors.surface,
        border = BorderStroke(
            if (seat.acting) 2.dp else 1.dp,
            if (seat.acting) colors.secondary else team.copy(alpha = 0.5f),
        ),
        modifier = modifier
            .alpha(if (seat.resolved) 0.5f else 1f)
            .semantics(mergeDescendants = true) {
                contentDescription = "${seat.heroName}, ${teamName(seat.team)} team" +
                    (seat.playerName?.let { ", $it" } ?: "")
                stateDescription = status
            },
    ) {
        Column(Modifier.padding(Spacing.sm), horizontalAlignment = Alignment.CenterHorizontally) {
            HeroPortrait(
                heroId = seat.heroId ?: "unknown",
                portrait = seat.portrait,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                seat.heroName,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TeamBadge(seat.team, showLabel = false)
                if (seat.resolved) {
                    Spacer(Modifier.width(2.dp))
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                }
                seat.playerName?.let {
                    Spacer(Modifier.width(Spacing.xs))
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun TimerControls(state: ActiveUiState, actions: TimerActions) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            FilledTonalIconButton(
                onClick = actions.onBack,
                modifier = Modifier.size(56.dp),
                colors = goaTonalIconColors(),
            ) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous phase")
            }
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
                modifier = Modifier.weight(1f).heightIn(min = 64.dp),
                shape = RoundedCornerShape(20.dp),
            ) {
                Text(
                    state.nextLabel,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
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
