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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Bolt
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
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
import dev.quinton.goa.core.designsystem.component.BevelText
import dev.quinton.goa.core.designsystem.component.GoaButton
import dev.quinton.goa.core.designsystem.component.GoaDarkTheme
import dev.quinton.goa.core.designsystem.component.GoaDialog
import dev.quinton.goa.core.designsystem.component.GoaPlate
import dev.quinton.goa.core.designsystem.component.LARGE_FONT_SCALE
import dev.quinton.goa.core.designsystem.component.MetalButton
import dev.quinton.goa.core.designsystem.component.PlateShape
import dev.quinton.goa.core.designsystem.component.SlateButton
import dev.quinton.goa.core.designsystem.component.TitlePlate
import dev.quinton.goa.core.designsystem.component.bannerEndWidth
import dev.quinton.goa.core.designsystem.component.slatePlate
import dev.quinton.goa.core.designsystem.component.steelBanner
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.domain.timer.RoundSection
import dev.quinton.goa.core.model.settings.TimerLayout
import dev.quinton.goa.feature.timer.ui.components.AlertsOffBanner
import dev.quinton.goa.feature.timer.ui.components.CountdownRing
import dev.quinton.goa.feature.timer.ui.components.KeepScreenOn
import dev.quinton.goa.feature.timer.ui.components.RingTone
import dev.quinton.goa.feature.timer.ui.components.RoundPips
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
        GoaDarkTheme {
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
                        // Landscape: one thin line, so the full-width banner costs the dial as little as it can.
                        TimerHeader(
                            state,
                            actions,
                            onEnd = { confirmEnd = true },
                            onCoin = { coinDialog = true },
                            compact = maxWidth > maxHeight,
                        )
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
        GoaDialog(
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
                GoaButton(onClick = {
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
        val short = LocalConfiguration.current.screenHeightDp < SHORT_SCREEN_HEIGHT ||
            LocalDensity.current.fontScale > LARGE_FONT_SCALE
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val ringMax = min(maxWidth * RING_WIDTH, maxHeight * RING_HEIGHT).coerceAtLeast(160.dp)
            val scroll = rememberScrollState()
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
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
            // Text running on under the controls fades there, so it reads as more to scroll, not as cut.
            if (scroll.canScrollForward) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(SCROLL_FADE)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, SCROLL_FADE_COLOR))),
                )
            }
        }
        // A short screen (small phone, large text) may scroll the dial away: there the bar keeps the countdown too.
        PinnedShotClock(state, actions, compact = short, inDial = state.dialShown && !short)
        TimerControls(state, actions)
    }
}

/**
 * A running "Hurry up" sits above the controls, outside the scrolling area, so it is always in view.
 * [inDial]: the dial shows its countdown, so this is only its Restart and Stop.
 */
@Composable
internal fun PinnedShotClock(state: ActiveUiState, actions: TimerActions, compact: Boolean, inDial: Boolean = false) {
    val shot = state.shotClock ?: return
    ShotClockPanel(
        shot,
        onRestart = actions.onShotClock,
        onCancel = actions.onCancelShotClock,
        compact = compact,
        clockInDial = inDial,
        modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.xs),
    )
}

/** The dial is showing (not the untimed end-of-round step card): a running Hurry up takes it. */
private val ActiveUiState.dialShown: Boolean get() = checklist == null || timed

/**
 * Landscape: a thin steel banner across the top; under it the dial with the whole left column, and on the right
 * the phase and extras, then Hurry up, time-outs and a running Hurry up pinned above the controls.
 */
@Composable
private fun LandscapeTimer(state: ActiveUiState, actions: TimerActions, header: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        header()
        Row(Modifier.weight(1f).fillMaxWidth()) {
            LandscapeDialColumn(state, actions, Modifier.weight(0.45f).fillMaxHeight())
            LandscapeStepColumn(state, actions, Modifier.weight(0.55f).fillMaxHeight())
        }
    }
}

@Composable
private fun LandscapeDialColumn(state: ActiveUiState, actions: TimerActions, modifier: Modifier) {
    // The dial takes the column's whole height (across the table it is what must read): Hurry up, time-outs and a
    // running Hurry up's bar are pinned above the controls on the right, outside its scrolling step text.
    Column(modifier) {
        BoxWithConstraints(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm),
            contentAlignment = Alignment.Center,
        ) {
            if (state.checklist != null && !state.timed) {
                Column(Modifier.verticalScroll(rememberScrollState())) { ChecklistStepCard(state, actions.onNext) }
            } else {
                TimerDial(state, min(maxWidth, maxHeight).coerceAtLeast(120.dp), actions.onTogglePause)
            }
        }
        if (state.dialShown) {
            PhaseClockLine(state, Modifier.align(Alignment.CenterHorizontally).padding(bottom = Spacing.sm))
        }
    }
}

@Composable
private fun LandscapeStepColumn(state: ActiveUiState, actions: TimerActions, modifier: Modifier) {
    Column(modifier) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Compact heading: the step text must fit above the controls in a short window.
            Spacer(Modifier.height(Spacing.sm))
            PhaseHeading(state, actions.onBack, compact = true)
            StepDetail(state, actions.onOpenLevels)
            SecondaryArea(state, actions, tableRules = false)
            Spacer(Modifier.height(Spacing.md))
        }
        Column(Modifier.padding(start = Spacing.screen, end = Spacing.screen, bottom = Spacing.xs)) {
            TableRulesBar(state, actions)
        }
        PinnedShotClock(state, actions, compact = true, inDial = state.dialShown)
        TimerControls(state, actions, compact = true)
    }
}

private const val SHORT_SCREEN_HEIGHT = 640
private val SCROLL_FADE = 44.dp
private val SCROLL_FADE_COLOR = Color(0xE6071019)

/** The dial shrinks where more must fit under it: hero tiles (Action) or the checklist (Upgrade). */
private fun dialScale(state: ActiveUiState): Float = when {
    state.seats != null -> 0.55f
    state.checklist != null -> 0.8f
    else -> 1f
}

@Composable
private fun PrimaryArea(state: ActiveUiState, actions: TimerActions, size: Dp) {
    if (state.dialShown) {
        TimerDial(state, size, actions.onTogglePause)
        PhaseClockLine(state, Modifier.padding(top = Spacing.md))
    } else {
        ChecklistStepCard(state, actions.onNext)
    }
}

/**
 * While Hurry up holds the dial: the step's own clock, smaller, just under it, so it is still there to
 * read but never mistaken for the clock that matters now. TalkBack hears it with the dial.
 */
@Composable
private fun PhaseClockLine(state: ActiveUiState, modifier: Modifier = Modifier) {
    if (state.shotClock == null) return
    val colors = MaterialTheme.colorScheme
    val clock = when {
        state.overtime -> "overtime ${state.clock}"
        state.timed -> "${state.clock} left"
        else -> "${state.clock} elapsed"
    }
    Row(
        modifier
            .heightIn(min = 40.dp)
            .slatePlate(RoundedCornerShape(50), brackets = false)
            .padding(horizontal = Spacing.lg, vertical = Spacing.xs)
            .clearAndSetSemantics {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Timer,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(
            "${state.title} · $clock",
            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
            color = if (state.overtime) colors.error else colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
private fun TimerHeader(
    state: ActiveUiState,
    actions: TimerActions,
    onEnd: () -> Unit,
    onCoin: () -> Unit,
    compact: Boolean = false,
) {
    var coinUndo by remember { mutableStateOf(false) }
    val banner = GoaTheme.colors
    val largeText = LocalDensity.current.fontScale > LARGE_FONT_SCALE
    BoxWithConstraints(Modifier.fillMaxWidth().steelBanner(banner, MaterialTheme.colorScheme.primary)) {
        val tools = headerTools(state, maxWidth, coinUndo)
        val subtitle = maxWidth >= SMALL_PHONE_WIDTH
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = Spacing.screen, top = if (compact) 0.dp else Spacing.xs)
                .padding(bottom = if (compact) 0.dp else Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Compact (landscape): the round and its subtitle on one line, the bar no taller than its buttons.
            val titleLines: @Composable (@Composable () -> Unit) -> Unit = { content ->
                if (compact) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        content()
                    }
                } else {
                    Column(Modifier.weight(1f)) { content() }
                }
            }
            titleLines {
                // A small phone's banner has no room for a second line: it would land on the metal edge.
                val spoken = "${state.presetName}, match time ${state.matchClock}"
                BevelText(
                    "Round ${state.round}",
                    style = headerTitleStyle(),
                    overflow = TextOverflow.Visible,
                    modifier = Modifier.semantics {
                        heading()
                        if (!subtitle) contentDescription = "Round ${state.round}, $spoken"
                    },
                )
                // Large text: the game clock alone, so it isn't what gets cut (TalkBack still hears both);
                // there "Game" stays with it, so the number never stands without saying what it counts.
                if (subtitle) {
                    if (compact) Spacer(Modifier.width(Spacing.md))
                    HeaderSubtitle(
                        if (largeText) "Game ${state.matchClock}" else "${state.presetName} · ${state.matchClock}",
                        spoken,
                    )
                }
            }
            CompositionLocalProvider(LocalContentColor provides banner.onBanner) {
                TimerTopActions(state, actions, tools, onEnd, onCoin, onCoinUndo = { coinUndo = it })
            }
            BannerEndSpacer()
        }
    }
}

/** Small words over the banner's streaks: light with a dark edge, like the title. */
@Composable
private fun HeaderSubtitle(text: String, spoken: String) {
    val style = MaterialTheme.typography.bodySmall
    Box(Modifier.semantics(mergeDescendants = true) { contentDescription = spoken }) {
        Text(
            text,
            style = style.copy(color = SUBTITLE_EDGE, drawStyle = Stroke(width = SUBTITLE_EDGE_WIDTH)),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clearAndSetSemantics {},
        )
        Text(
            text,
            style = style,
            color = GoaTheme.colors.onBanner,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val TEXT_SHADOW = Shadow(Color.Black, Offset(0f, 1f), 3f)
private val SMALL_PHONE_WIDTH = 360.dp
private val SUBTITLE_EDGE = Color(0xFF05090D)
private const val SUBTITLE_EDGE_WIDTH = 5f
private const val HEADER_TITLE_MAX_SCALE = 1.5f

/** Whether the coin chip keeps its words beside the coin's face. */
private data class HeaderTools(val coinLabel: Boolean)

/**
 * "Round N" must never be clipped (S25 Ultra: 384 dp wide; a small phone has less). The bar holds only the
 * coin chip and the labelled overflow menu (Levels & defeats, End match, the view switch are named there, never
 * bare glyphs); the title, coin chip and its Undo are measured, and where they don't fit the chip keeps only the
 * coin's face (TalkBack still hears "Tie Breaker coin: Red").
 */
@Composable
private fun headerTools(state: ActiveUiState, width: Dp, coinUndo: Boolean): HeaderTools {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val titleStyle = headerTitleStyle()
    val labelStyle = coinChipLabelStyle()
    val coin = coinChipMark()
    val team = state.tieBreaker
    val undo = coinUndo && team != null
    val bannerEnd = bannerEndWidth()
    return remember(state.round, team, undo, width, density, titleStyle, labelStyle, coin, bannerEnd) {
        fun textWidth(text: String, style: TextStyle) =
            with(density) { measurer.measure(text, style, maxLines = 1, softWrap = false).size.width.toDp() }
        val chipMark = if (team != null) CHIP_INSET * 2 + coin else 0.dp
        val chip = team?.let { chipMark + COIN_GAP + textWidth(COIN_WORD + teamName(it), labelStyle) } ?: 0.dp
        val undoButton = if (undo) maxOf(UNDO_MIN_WIDTH, textWidth("Undo", labelStyle) + UNDO_PADDING) else 0.dp
        val fixed = Spacing.screen + bannerEnd + HEADER_SLACK + undoButton + HEADER_ICON
        val room = width - fixed - textWidth("Round ${state.round}", titleStyle)
        HeaderTools(coinLabel = room >= chip)
    }
}

/** Capped: at large text the round must still fit whole beside the coin chip. */
@Composable
private fun headerTitleStyle(): TextStyle = with(LocalDensity.current) {
    val scale = fontScale.coerceAtMost(HEADER_TITLE_MAX_SCALE) / fontScale
    GoaTextStyles.bannerTitle.copy(fontSize = GoaTextStyles.bannerTitle.fontSize * scale)
}

private val HEADER_ICON = 48.dp

private val UNDO_MIN_WIDTH = 58.dp
private val UNDO_PADDING = 24.dp

/** Rounding and font-metric safety margin. */
private val HEADER_SLACK = 4.dp

/**
 * Top-bar actions: the Tie Breaker coin (when tracked) and the overflow menu, where every other tool has its name
 * (Levels & defeats, End match, the view switch, Rules, Settings).
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
    val roundView = state.layout == TimerLayout.ROUND
    Box {
        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "More options") }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            val close: (() -> Unit) -> () -> Unit = { action ->
                {
                    menu = false
                    action()
                }
            }
            // Most used first; End match last, away from a quick tap.
            TimerMenuItem("Levels & defeats", Icons.Outlined.MilitaryTech, close(actions.onOpenLevels))
            TimerMenuItem(
                if (state.tieBreaker == null) "Track Tie Breaker coin…" else "Tie Breaker coin…",
                Icons.Outlined.Toll,
                close(onCoin),
            )
            TimerMenuItem("Restart phase", Icons.Outlined.Replay, close(actions.onRestart))
            TimerMenuItem(
                "Skip to end of round",
                Icons.Outlined.SkipNext,
                close(actions.onSkipToEnd),
                enabled = state.section != RoundSection.ROUND_END,
            )
            TimerMenuItem(
                if (roundView) "Step-by-step view" else "Whole-round view",
                if (roundView) Icons.Outlined.Timer else Icons.Outlined.ViewAgenda,
                close { actions.onTimerLayout(if (roundView) TimerLayout.STEPS else TimerLayout.ROUND) },
            )
            TimerMenuItem("Rules", Icons.AutoMirrored.Outlined.MenuBook, close(actions.onOpenRules))
            TimerMenuItem("Settings", Icons.Outlined.Settings, close(actions.onSettings))
            TimerMenuItem("End match…", Icons.Outlined.Flag, close(onEnd))
        }
    }
}

/** One labelled row of the timer's overflow menu. */
@Composable
private fun TimerMenuItem(label: String, icon: ImageVector, onClick: () -> Unit, enabled: Boolean = true) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        enabled = enabled,
        onClick = onClick,
    )
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

/**
 * The dial. While Hurry up runs its countdown is what the table must read, so it takes the dial: the
 * digits, the arc and a "Hurry up" word above them; the step's own clock moves under the dial ([PhaseClockLine]).
 */
@Composable
private fun TimerDial(state: ActiveUiState, size: Dp, onToggle: () -> Unit) {
    val shot = state.shotClock
    val tone = when {
        shot != null && state.paused -> RingTone.PAUSED
        shot != null && shot.over -> RingTone.OVERTIME
        shot != null -> RingTone.WARNING
        !state.timed -> RingTone.UNTIMED
        state.paused -> RingTone.PAUSED
        state.overtime -> RingTone.OVERTIME
        state.warning -> RingTone.WARNING
        else -> RingTone.NORMAL
    }
    // Paused, overtime and elapsed say so in words; otherwise the digits alone, as the mock-up. Hurry up
    // names its urgency in words too, not by colour alone: left, the last seconds, time.
    val status = when {
        state.paused -> "Paused"

        shot != null && shot.over -> "Time!"

        shot != null && shot.warning -> "Last seconds!"

        shot != null -> "Left"

        state.overtime -> "Overtime"

        // The dial is the pause button (TalkBack hears its click label); the word alone says what the digits are.
        !state.timed -> "Elapsed"

        else -> null
    }
    val urgent = (state.overtime && shot == null) || shot?.over == true
    val digitColor = when {
        tone == RingTone.PAUSED -> MaterialTheme.colorScheme.onSurfaceVariant
        urgent -> MaterialTheme.colorScheme.error
        shot?.warning == true -> MaterialTheme.colorScheme.primary
        shot != null -> DIGITS
        tone == RingTone.WARNING -> MaterialTheme.colorScheme.primary
        else -> DIGITS
    }
    val clock = shot?.clock ?: state.clock
    val density = LocalDensity.current
    // Sized from the ring, not the font scale: the digits are already as big as they can be.
    val digitScale = when {
        clock.length <= SHORT_CLOCK -> DIGITS_SHORT
        clock.length == SHORT_CLOCK + 1 -> DIGITS_MEDIUM
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
        fraction = shot?.fraction ?: state.fraction,
        tone = tone,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .clickable(onClickLabel = if (state.paused) "Resume" else "Pause", role = Role.Button, onClick = onToggle)
            .semantics(mergeDescendants = true) {
                contentDescription = if (shot != null) {
                    "${shot.spokenClock}. ${state.title}, ${state.spokenClock}"
                } else {
                    "${state.title}. ${state.spokenClock}"
                }
                stateDescription = if (state.paused) "Paused" else "Running"
                if (shot?.over == true) liveRegion = LiveRegionMode.Polite
            },
    ) {
        // The digits keep the dial's centre; the words sit above and below, so nothing jumps.
        Text(
            clock,
            style = timerDigits(
                MaterialTheme.typography.displayLarge,
            ).copy(fontSize = digitSize, lineHeight = digitSize, shadow = DIGIT_SHADOW),
            color = digitColor,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.alpha(blink),
        )
        if (state.paused || shot != null) {
            val word = if (state.paused) "PAUSED" else "HURRY UP"
            val wordColor = if (urgent && !state.paused) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            }
            Row(
                Modifier.align(Alignment.TopCenter).padding(top = size * WORD_INSET),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (state.paused) Icons.Filled.Pause else Icons.Filled.Bolt,
                    contentDescription = null,
                    modifier = Modifier.size(with(density) { pausedSize.toDp() }),
                    tint = wordColor,
                )
                Text(
                    word,
                    style = GoaTextStyles.plateTitle.copy(fontSize = pausedSize, lineHeight = pausedSize),
                    color = wordColor,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
        if (status != null) {
            Text(
                status.uppercase(),
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = labelSize,
                    lineHeight = labelSize,
                    fontWeight = if (shot?.warning == true || urgent) FontWeight.Bold else null,
                ),
                color = with(MaterialTheme.colorScheme) {
                    when {
                        state.paused -> onSurfaceVariant
                        urgent -> error
                        shot?.warning == true -> primary
                        else -> onSurfaceVariant
                    }
                },
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
            state.clock,
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
