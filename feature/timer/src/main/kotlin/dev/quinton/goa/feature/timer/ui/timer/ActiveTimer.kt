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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Toll
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import dev.quinton.goa.core.designsystem.component.BannerEndSpacer
import dev.quinton.goa.core.designsystem.component.BannerMedallionWidth
import dev.quinton.goa.core.designsystem.component.BevelText
import dev.quinton.goa.core.designsystem.component.GoaPlate
import dev.quinton.goa.core.designsystem.component.GoaPlateTheme
import dev.quinton.goa.core.designsystem.component.LARGE_FONT_SCALE
import dev.quinton.goa.core.designsystem.component.PlateShape
import dev.quinton.goa.core.designsystem.component.TitlePlate
import dev.quinton.goa.core.designsystem.component.steelBanner
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.domain.timer.RoundSection
import dev.quinton.goa.core.model.settings.TimerLayout
import dev.quinton.goa.feature.timer.ui.components.AlertsOffBanner
import dev.quinton.goa.feature.timer.ui.components.CountdownRing
import dev.quinton.goa.feature.timer.ui.components.KeepScreenOn
import dev.quinton.goa.feature.timer.ui.components.MetalButton
import dev.quinton.goa.feature.timer.ui.components.RingTone
import dev.quinton.goa.feature.timer.ui.components.RoundPips
import dev.quinton.goa.feature.timer.ui.components.SlateButton
import dev.quinton.goa.feature.timer.ui.components.TimerBackdrop
import dev.quinton.goa.feature.timer.ui.components.rememberReducedMotion
import dev.quinton.goa.feature.timer.ui.components.teamName
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
        // Dark in both themes: the dim sea, so the running timer never lights up a dim room.
        GoaPlateTheme {
            Box(Modifier.fillMaxSize()) {
                val round = state.layout == TimerLayout.ROUND
                TimerBackdrop(silhouette = !round)
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
                    when {
                        maxWidth > maxHeight && round -> LandscapeRoundTimer(state, actions, header)
                        maxWidth > maxHeight -> LandscapeTimer(state, actions, header)
                        round -> PortraitRoundTimer(state, actions, header)
                        else -> PortraitTimer(state, actions, header)
                    }
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

/**
 * Portrait: header, then a scrolling column from the top (the phase on its plate, round and turn, dial,
 * extras) with the battlefield showing below it; controls at the bottom.
 */
@Composable
private fun PortraitTimer(state: ActiveUiState, actions: TimerActions, header: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        header()
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val ringMax = min(maxWidth * RING_WIDTH, maxHeight * RING_HEIGHT).coerceAtLeast(160.dp)
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.screen),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
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
internal fun PinnedShotClock(state: ActiveUiState, actions: TimerActions, compact: Boolean) {
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
        // Hurry up and time-outs (and a running Hurry up) sit under the dial here, leaving the right
        // column to the step text: there they scrolled under the controls in a 384 dp-tall window.
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
            Column(Modifier.padding(horizontal = Spacing.screen)) { TableRulesBar(state, actions) }
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
                // Compact heading: the step text must fit above the controls in a short window.
                Spacer(Modifier.height(Spacing.xs))
                PhaseHeading(state, actions.onBack, compact = true)
                StepDetail(state, actions.onOpenLevels)
                SecondaryArea(state, actions, tableRules = false)
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
    GoaPlate(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.md)) {
        Text(
            detail,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    // Timed Level Up: the level ladder sits right under the rule, above the fold.
    if (state.checklist?.any { it.state == ChecklistState.CURRENT && it.id == LEVEL_UP_STEP } == true) {
        Spacer(Modifier.height(Spacing.sm))
        LevelLadderButton(onOpenLevels, label = "Level costs & card Tiers")
    }
}

@Composable
private fun SecondaryArea(state: ActiveUiState, actions: TimerActions, tableRules: Boolean = true) {
    if (tableRules) TableRulesBar(state, actions)
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

/** The steel banner, as on every screen, holding the round: "Round N" must never be clipped. */
@Composable
private fun TimerHeader(state: ActiveUiState, actions: TimerActions, onEnd: () -> Unit, onCoin: () -> Unit) {
    var coinUndo by remember { mutableStateOf(false) }
    val banner = GoaTheme.colors
    val largeText = LocalDensity.current.fontScale > LARGE_FONT_SCALE
    BoxWithConstraints(Modifier.fillMaxWidth().steelBanner(banner, MaterialTheme.colorScheme.primary)) {
        val tools = headerTools(state, maxWidth, coinUndo)
        Row(
            Modifier.fillMaxWidth().padding(start = Spacing.screen, top = Spacing.xs, bottom = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                BevelText(
                    "Round ${state.round}",
                    style = headerTitleStyle(),
                    overflow = TextOverflow.Visible,
                    modifier = Modifier.semantics { heading() },
                )
                // Large text: the match clock alone, so it isn't what gets cut (TalkBack still hears both).
                Text(
                    if (largeText) state.matchClock else "${state.presetName} · ${state.matchClock}",
                    style = MaterialTheme.typography.bodySmall.copy(shadow = TEXT_SHADOW),
                    color = banner.onBanner,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics {
                        contentDescription =
                            "${state.presetName}, match time ${state.matchClock}"
                    },
                )
            }
            CompositionLocalProvider(LocalContentColor provides banner.onBanner) {
                TimerTopActions(state, actions, tools, onEnd, onCoin, onCoinUndo = { coinUndo = it })
            }
            BannerEndSpacer()
        }
    }
}

private val TEXT_SHADOW = Shadow(Color.Black, Offset(0f, 1f), 3f)
private const val HEADER_TITLE_MAX_SCALE = 1.5f

/** Which optional top-bar parts sit in the bar; the icons that don't are in the overflow menu. */
private data class HeaderTools(
    val levels: Boolean,
    val viewSwitch: Boolean,
    val endMatch: Boolean,
    val coinLabel: Boolean,
)

/**
 * "Round N" must never be clipped (S25 Ultra: 384 dp wide, where Blue's coin chip plus four icons left
 * too little room; a small phone has less). The title, coin chip and its Undo are measured; the view
 * switch moves into the menu first (it is also in Settings), then Levels & defeats, then End match, and
 * last the coin chip keeps only its mark. Large text: the view switch and Levels are in the menu.
 */
@Composable
private fun headerTools(state: ActiveUiState, width: Dp, coinUndo: Boolean): HeaderTools {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val titleStyle = headerTitleStyle()
    val labelStyle = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
    val team = state.tieBreaker
    val undo = coinUndo && team != null
    return remember(state.round, team, undo, width, density, titleStyle, labelStyle) {
        fun textWidth(text: String, style: TextStyle) =
            with(density) { measurer.measure(text, style, maxLines = 1, softWrap = false).size.width.toDp() }
        val chipMark = if (team != null) CHIP_CHROME * density.fontScale else 0.dp
        val chip = team?.let { chipMark + textWidth(teamName(it), labelStyle) } ?: 0.dp
        val undoButton = if (undo) maxOf(UNDO_MIN_WIDTH, textWidth("Undo", labelStyle) + UNDO_PADDING) else 0.dp
        val fixed = Spacing.screen + BannerMedallionWidth + HEADER_SLACK + undoButton + HEADER_ICON
        val room = width - fixed - textWidth("Round ${state.round}", titleStyle)
        val large = density.fontScale > LARGE_FONT_SCALE
        val endMatch = room - chip >= HEADER_ICON
        HeaderTools(
            levels = !large && room - chip >= HEADER_ICON * 2,
            viewSwitch = !large && room - chip >= HEADER_ICON * 3,
            endMatch = endMatch,
            coinLabel = room >= chip,
        )
    }
}

/** Capped: at large text the round must still fit whole beside the coin chip. */
@Composable
private fun headerTitleStyle(): TextStyle = with(LocalDensity.current) {
    val scale = fontScale.coerceAtMost(HEADER_TITLE_MAX_SCALE) / fontScale
    GoaTextStyles.bannerTitle.copy(fontSize = GoaTextStyles.bannerTitle.fontSize * scale)
}

private val HEADER_ICON = 48.dp

/** Chip padding (12 dp each side), team mark (12 dp) and its gap (6 dp). */
private val CHIP_CHROME = 42.dp
private val UNDO_MIN_WIDTH = 58.dp
private val UNDO_PADDING = 24.dp

/** Rounding and font-metric safety margin. */
private val HEADER_SLACK = 4.dp

/**
 * Top-bar actions, left to right: Tie Breaker coin (when tracked), Levels & defeats (rules
 * reference), step / round view switch, End match, overflow menu (with Rules).
 */
@Composable
private fun RowScope.TimerTopActions(
    state: ActiveUiState,
    actions: TimerActions,
    tools: HeaderTools,
    onEnd: () -> Unit,
    onCoin: () -> Unit,
    onCoinUndo: (Boolean) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val coin = state.tieBreaker
    if (coin != null) {
        TieBreakerChip(
            coin,
            actions.onFlipCoin,
            actionKey = "${state.round}:${state.title}:${state.paused}",
            showLabel = tools.coinLabel,
            onUndoShown = onCoinUndo,
        )
    } else {
        LaunchedEffect(Unit) { onCoinUndo(false) }
    }
    // What doesn't fit beside "Round N" (narrow phones, large text) is in the menu instead.
    val roundView = state.layout == TimerLayout.ROUND
    val switchLayout = { actions.onTimerLayout(if (roundView) TimerLayout.STEPS else TimerLayout.ROUND) }
    if (tools.levels) {
        IconButton(onClick = actions.onOpenLevels) {
            Icon(Icons.Outlined.MilitaryTech, contentDescription = "Levels and defeats")
        }
    }
    if (tools.viewSwitch) {
        IconButton(onClick = switchLayout) {
            Icon(
                if (roundView) Icons.Outlined.Timer else Icons.Outlined.ViewAgenda,
                contentDescription = if (roundView) "Show one step at a time" else "Show the whole round",
            )
        }
    }
    if (tools.endMatch) {
        IconButton(onClick = onEnd) { Icon(Icons.Outlined.Flag, contentDescription = "End match") }
    }
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
            if (!tools.viewSwitch) {
                DropdownMenuItem(
                    text = { Text(if (roundView) "Step-by-step view" else "Whole-round view") },
                    leadingIcon = {
                        Icon(
                            if (roundView) Icons.Outlined.Timer else Icons.Outlined.ViewAgenda,
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        menu = false
                        switchLayout()
                    },
                )
            }
            if (!tools.levels) {
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
 * The step on its title plate with "Previous phase" beside it, then where in the round and the hint.
 * Previous sits up here, away from the bottom edge: with no tab bar on the running timer, a habitual
 * tap where the Heroes tab usually is went back a phase (game night).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PhaseHeading(state: ActiveUiState, onBack: () -> Unit, compact: Boolean = false) {
    val reduced = rememberReducedMotion()
    val fontScale = LocalDensity.current.fontScale
    // Capped, so a one-word step ("Resolve") never breaks mid-word on a small phone at large text.
    val capped = (fontScale.coerceAtMost(PHASE_MAX_SCALE) / fontScale)
    val plateStyle = if (compact) GoaTextStyles.plateTitle else PHASE_STYLE
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(
                Icons.Filled.SkipPrevious,
                contentDescription = "Previous phase",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        AnimatedContent(
            targetState = state.title,
            transitionSpec = {
                val ms = if (reduced) 0 else 250
                fadeIn(tween(ms)) togetherWith fadeOut(tween(ms))
            },
            label = "phase",
            modifier = Modifier.weight(1f),
        ) { title ->
            TitlePlate(
                title,
                style = plateStyle.copy(
                    fontSize = plateStyle.fontSize * capped,
                    lineHeight =
                        plateStyle.lineHeight * capped,
                ),
                heading = false,
                maxLines = 3,
                modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        // Balances the button so the plate stays centred; large text needs that room for the words.
        if (fontScale <= LARGE_FONT_SCALE) Spacer(Modifier.width(48.dp))
    }
    Spacer(Modifier.height(if (compact) Spacing.xs else Spacing.sm))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        itemVerticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            state.context.uppercase(),
            style = CONTEXT_STYLE,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        RoundPips(state.pips, state.round)
    }
    state.hint?.let { hint ->
        Text(
            hint,
            style = MaterialTheme.typography.bodyMedium.copy(shadow = TEXT_SHADOW),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
        )
    }
}

private val PHASE_STYLE = GoaTextStyles.plateTitle.copy(fontSize = 20.sp, lineHeight = 25.sp)
private const val PHASE_MAX_SCALE = 1.5f
private val CONTEXT_STYLE = GoaTextStyles.sectionHead.copy(fontSize = 15.sp, lineHeight = 20.sp, shadow = TEXT_SHADOW)

@Composable
private fun TimerDial(state: ActiveUiState, size: Dp, onToggle: () -> Unit) {
    val tone = when {
        !state.timed -> RingTone.UNTIMED
        state.paused -> RingTone.PAUSED
        state.overtime -> RingTone.OVERTIME
        state.warning -> RingTone.WARNING
        else -> RingTone.NORMAL
    }
    // Paused, overtime and elapsed say so in words; otherwise the digits alone, as the mock-up.
    val status = when {
        state.paused -> "Tap to resume"
        state.overtime -> "Overtime"
        !state.timed -> "Elapsed · tap to pause"
        else -> null
    }
    val digitColor = when (tone) {
        RingTone.OVERTIME -> MaterialTheme.colorScheme.error
        RingTone.WARNING -> MaterialTheme.colorScheme.primary
        RingTone.PAUSED -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> DIGITS
    }
    val density = LocalDensity.current
    // Sized from the ring, not the font scale: the digits are already as big as they can be.
    val digitScale = when {
        state.clock.length <= SHORT_CLOCK -> DIGITS_SHORT
        state.clock.length == SHORT_CLOCK + 1 -> DIGITS_MEDIUM
        else -> DIGITS_LONG
    }
    val digitSize = with(density) { (size * digitScale).toSp() }
    val labelSize = with(density) { (size * 0.042f).coerceAtLeast(11.dp).toSp() }
    val pausedSize = with(density) { (size * 0.075f).coerceAtLeast(14.dp).toSp() }
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
        // The digits keep the dial's centre; the words sit above and below, so nothing jumps.
        Text(
            state.clock,
            style = timerDigits(
                MaterialTheme.typography.displayLarge,
            ).copy(fontSize = digitSize, lineHeight = digitSize, shadow = DIGIT_SHADOW),
            color = digitColor,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.alpha(blink),
        )
        if (state.paused) {
            Row(
                Modifier.align(Alignment.TopCenter).padding(top = size * WORD_INSET),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Pause,
                    contentDescription = null,
                    modifier = Modifier.size(with(density) { pausedSize.toDp() }),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "PAUSED",
                    style = GoaTextStyles.plateTitle.copy(fontSize = pausedSize, lineHeight = pausedSize),
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
        if (status != null) {
            Text(
                status.uppercase(),
                style = MaterialTheme.typography.labelLarge.copy(fontSize = labelSize, lineHeight = labelSize),
                color = with(MaterialTheme.colorScheme) { if (state.overtime) error else onSurfaceVariant },
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = size * WORD_INSET),
            )
        }
    }
}

private val DIGITS = Color(0xFFF3F6F9)
private val DIGIT_SHADOW = Shadow(Color.Black, Offset(0f, 2f), 4f)

/** "0:23" is four characters: up to there the digits take a third of the dial, longer clocks less. */
private const val SHORT_CLOCK = 4
private const val DIGITS_SHORT = 0.31f
private const val DIGITS_MEDIUM = 0.255f
private const val DIGITS_LONG = 0.195f

/** The steel ring and arc take this much of the dial before the words start. */
private const val WORD_INSET = 0.2f

/** The dial's width against the screen's: mock-up 07 leaves the sea around it. */
private const val RING_WIDTH = 0.82f

/** Leaves a small phone room for the heading and Hurry up without scrolling. */
private const val RING_HEIGHT = 0.55f

/** Untimed end-of-round step: a medallion with the step number and the rule text; tap = Next. */
@Composable
private fun ChecklistStepCard(state: ActiveUiState, onNext: () -> Unit) {
    val current = state.checklist?.firstOrNull { it.state == ChecklistState.CURRENT }
    val colors = MaterialTheme.colorScheme
    GoaPlate(
        Modifier
            .fillMaxWidth()
            .clip(PlateShape)
            .clickable(onClickLabel = "Step done, next", role = Role.Button, onClick = onNext),
        contentPadding = PaddingValues(Spacing.xl),
    ) {
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
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
            Text(
                it,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Spacing.sm))
        }
        Text(
            "${state.clock} at this step · tap when done",
            style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun EndOfRoundChecklist(items: List<ChecklistItemUi>, onOpenLevels: (() -> Unit)?) {
    GoaPlate(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = Spacing.sm)) {
        Text(
            "END OF ROUND",
            style = GoaTextStyles.sectionHead,
            color = GoaTheme.colors.heading,
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
                if (item.state ==
                    ChecklistState.CURRENT
                ) {
                    colors.secondary.copy(alpha = 0.14f)
                } else {
                    Color.Transparent
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
                .background(if (item.state == ChecklistState.UPCOMING) Color.Transparent else badge)
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
            val timerName = item.timerName
            if (timerName != null) {
                Text(timerName, style = MaterialTheme.typography.bodySmall, color = colors.secondary)
            }
        }
        onOpenLevels?.let { LevelLadderButton(it) }
    }
}

/** End-of-round id of the Level Up step (match-setup.json). */
internal const val LEVEL_UP_STEP = "level_up"

/** On the current Level Up step: the level ladder (costs, max Tier) one tap away. */
@Composable
internal fun LevelLadderButton(onClick: () -> Unit, label: String = "Levels") {
    SlateButton(onClick = onClick, contentPadding = PaddingValues(horizontal = Spacing.md)) {
        Icon(Icons.Outlined.MilitaryTech, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(Spacing.xs))
        Text(label)
    }
}

/**
 * Pause and Next, large and clear; Next in filled bronze, with the width a long label needs. With
 * large text Pause is its icon alone, leaving that width to Next's words. "Previous phase" is up by
 * the step's plate ([PhaseHeading]), away from the edge.
 */
@Composable
internal fun TimerControls(state: ActiveUiState, actions: TimerActions, compact: Boolean = false) {
    val pauseWord = if (state.paused) "Resume" else "Pause"
    val iconOnly = LocalDensity.current.fontScale > LARGE_FONT_SCALE
    val height = if (compact) 56.dp else CONTROL_HEIGHT
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screen, vertical = if (compact) Spacing.sm else Spacing.md)
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        MetalButton(
            onClick = actions.onTogglePause,
            bronze = false,
            contentPadding = PaddingValues(horizontal = Spacing.sm),
            modifier = (if (iconOnly) Modifier.width(height) else Modifier.weight(PAUSE_SHARE))
                .fillMaxHeight()
                .heightIn(min = height),
        ) {
            Icon(
                if (state.paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                contentDescription = if (iconOnly) pauseWord else null,
            )
            if (!iconOnly) {
                Spacer(Modifier.width(Spacing.xs))
                // One name for TalkBack (and the instrumented tests): the word, as the button's description.
                Text(
                    pauseWord,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = CONTROL_TEXT),
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.clearAndSetSemantics { contentDescription = pauseWord },
                )
            }
        }
        MetalButton(
            onClick = actions.onNext,
            bronze = true,
            modifier = Modifier.weight(1f - PAUSE_SHARE).fillMaxHeight().heightIn(min = height),
        ) {
            Text(
                state.nextLabel,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = CONTROL_TEXT),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private val CONTROL_HEIGHT = 68.dp
private val CONTROL_TEXT = 17.sp
private const val PAUSE_SHARE = 0.38f

internal const val PAUSE_BLINK_MS = 900
