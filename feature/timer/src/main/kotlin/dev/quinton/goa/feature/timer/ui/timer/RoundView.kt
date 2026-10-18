package dev.quinton.goa.feature.timer.ui.timer

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.EngravedRule
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.domain.timer.TimerEngine
import dev.quinton.goa.feature.timer.ui.components.rememberReducedMotion
import dev.quinton.goa.feature.timer.ui.components.timerDigits

/*
 * Round view (ADR-026): the whole round on one scrolling list, one row per step, the current step
 * expanded with its live clock. Header, Hurry up, time-outs and controls stay pinned; tapping
 * another row asks before jumping there (Back undoes the jump, like an accidental Next).
 */

/** Portrait: header, the round list (current step expanded), then the pinned table rules and controls. */
@Composable
internal fun PortraitRoundTimer(state: ActiveUiState, actions: TimerActions, header: @Composable () -> Unit) {
    var jump by remember { mutableStateOf<RoundRowUi?>(null) }
    Column(Modifier.fillMaxSize()) {
        header()
        // Rows scroll under the header: the rule keeps a clipped row from reading as part of it.
        EngravedRule(Modifier.padding(top = Spacing.xs))
        RoundList(state, actions, expandCurrent = true, onJump = { jump = it }, Modifier.weight(1f).fillMaxWidth())
        PinnedTableRules(state, actions)
        PinnedShotClock(state, actions, compact = false)
        TimerControls(state, actions)
    }
    JumpDialog(state, jump, actions, onDismiss = { jump = null })
}

/** Landscape: the round list on the left; header, the current step's clock and the controls on the right. */
@Composable
internal fun LandscapeRoundTimer(state: ActiveUiState, actions: TimerActions, header: @Composable () -> Unit) {
    var jump by remember { mutableStateOf<RoundRowUi?>(null) }
    Row(Modifier.fillMaxSize()) {
        RoundList(state, actions, expandCurrent = false, onJump = { jump = it }, Modifier.weight(0.45f).fillMaxHeight())
        Column(Modifier.weight(0.55f).fillMaxHeight()) {
            header()
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                // A phone held sideways can leave ~130 dp here (S25 Ultra: 384 dp tall): the digits
                // shrink so the clock stays in view above the pinned buttons instead of scrolling off.
                val digits = (maxHeight - LANDSCAPE_CLOCK_CHROME).coerceIn(LANDSCAPE_DIGITS_MIN, LANDSCAPE_DIGITS)
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Spacing.screen),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    LandscapeCurrentStep(state, actions, digits)
                }
            }
            // Pinned here too: in a short landscape window it scrolled under the controls.
            PinnedTableRules(state, actions)
            PinnedShotClock(state, actions, compact = true)
            TimerControls(state, actions, compact = true)
        }
    }
    JumpDialog(state, jump, actions, onDismiss = { jump = null })
}

/** Step title (one line: the list beside it already places the step), clock, hint and extras. */
@Composable
private fun LandscapeCurrentStep(state: ActiveUiState, actions: TimerActions, digits: Dp) {
    state.rows.firstOrNull { it.state == RoundRowState.CURRENT }?.let { row ->
        // One line, not the step view's chip and headline: the list beside it already
        // places the step, and the Level Up rule must fit above the controls.
        StepTitle(row, actions.onBack, Modifier.padding(top = Spacing.xs))
        RoundClock(state, row, actions.onTogglePause, digits = digits)
    }
    state.hint?.let {
        Text(
            it,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    CurrentStepExtras(state, actions)
    Spacer(Modifier.height(Spacing.md))
}

private val LANDSCAPE_DIGITS = 56.dp
private val LANDSCAPE_DIGITS_MIN = 36.dp

/** Step title row (48 dp + 4), the clock's padding (16) and progress bar with its gap (~16). */
private val LANDSCAPE_CLOCK_CHROME = 84.dp

/** Hurry up and time-out buttons, pinned above the controls so they never scroll away. */
@Composable
private fun PinnedTableRules(state: ActiveUiState, actions: TimerActions) {
    Column(Modifier.padding(start = Spacing.screen, end = Spacing.screen, bottom = Spacing.xs)) {
        TableRulesBar(state, actions)
    }
}

private sealed interface RoundEntry {
    val key: String

    data class Heading(val title: String) : RoundEntry {
        override val key: String get() = "h:$title"
    }

    data class Step(val row: RoundRowUi) : RoundEntry {
        override val key: String get() = "s:${row.index}"
    }
}

private fun entries(rows: List<RoundRowUi>): List<RoundEntry> = buildList {
    rows.forEachIndexed { i, row ->
        if (i == 0 || rows[i - 1].group != row.group) add(RoundEntry.Heading(row.group))
        add(RoundEntry.Step(row))
    }
}

/**
 * The round, grouped by section. When the step changes the list brings the current row into view
 * with the step before it (and its heading) for context, unless the player is scrolling it right
 * then. A current row too tall for that (Action seats, large text) gives up the context first.
 */
@Composable
private fun RoundList(
    state: ActiveUiState,
    actions: TimerActions,
    expandCurrent: Boolean,
    onJump: (RoundRowUi) -> Unit,
    modifier: Modifier,
) {
    val entries = remember(state.rows) { entries(state.rows) }
    val current = entries.indexOfFirst { it is RoundEntry.Step && it.row.state == RoundRowState.CURRENT }
    val target = (current - 1).let { if (entries.getOrNull(it) is RoundEntry.Heading) it - 1 else it }
        .coerceAtLeast(0)
    val list = rememberLazyListState(initialFirstVisibleItemIndex = target)
    val reduced = rememberReducedMotion()
    LaunchedEffect(state.round, state.stepIndex) {
        if (current < 0 || list.isScrollInProgress) return@LaunchedEffect
        if (reduced) list.scrollToItem(target) else list.animateScrollToItem(target)
        val info = list.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == current } ?: return@LaunchedEffect
        val overflow = item.offset + item.size - info.viewportEndOffset
        val above = item.offset - info.viewportStartOffset
        if (overflow > 0 && above > 0) {
            val by = minOf(overflow, above).toFloat()
            if (reduced) list.scrollBy(by) else list.animateScrollBy(by)
        }
    }
    LazyColumn(
        modifier,
        state = list,
        contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.sm),
    ) {
        items(entries, key = { it.key }, contentType = { it::class }) { entry ->
            when (entry) {
                is RoundEntry.Heading -> GroupHeading(entry.title)

                is RoundEntry.Step -> {
                    val row = entry.row
                    if (row.state == RoundRowState.CURRENT && expandCurrent) {
                        CurrentRow(state, row, actions)
                    } else {
                        RoundRow(row, state, onClick = {
                            if (row.state == RoundRowState.CURRENT) actions.onTogglePause() else onJump(row)
                        })
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupHeading(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Spacing.sm, top = Spacing.md, bottom = Spacing.xs)
            .semantics { heading() },
    )
}

/** Accent of the current step: teal running, bronze in the final seconds, red in overtime. */
@Composable
private fun accent(state: ActiveUiState): Color = with(MaterialTheme.colorScheme) {
    when {
        state.paused -> onSurfaceVariant
        state.overtime -> error
        state.warning -> primary
        else -> secondary
    }
}

/** A done, upcoming, or (landscape) current step: one line, tap to jump (current: pause). */
@Composable
private fun RoundRow(row: RoundRowUi, state: ActiveUiState, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val current = row.state == RoundRowState.CURRENT
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(if (current) colors.secondary.copy(alpha = 0.12f) else Color.Transparent)
            .then(if (current) Modifier.border(2.dp, accent(state), MaterialTheme.shapes.medium) else Modifier)
            .clickable(
                onClickLabel = when {
                    !current -> "Jump to this step"
                    state.paused -> "Resume"
                    else -> "Pause"
                },
                role = Role.Button,
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) {
                contentDescription = row.description(state.spokenClock)
                if (current) stateDescription = if (state.paused) "Paused" else "Running"
            }
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StateBadge(row.state, state.paused)
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                row.title,
                style = MaterialTheme.typography.bodyLarge,
                // Done steps step back by colour, not transparency, so they keep their contrast.
                color = if (row.state == RoundRowState.DONE) colors.onSurfaceVariant else colors.onSurface,
            )
            row.context?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(Spacing.sm))
        Text(
            if (current) state.clock else row.length,
            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
            color = when {
                current && state.overtime -> colors.error
                current -> colors.onSurface
                else -> colors.onSurfaceVariant
            },
        )
    }
}

/** Done: a bronze check. Current: teal, with play/pause. Upcoming: an empty ring. */
@Composable
private fun StateBadge(state: RoundRowState, paused: Boolean) {
    val colors = MaterialTheme.colorScheme
    val ring = when (state) {
        RoundRowState.DONE -> colors.primary
        RoundRowState.CURRENT -> colors.secondary
        RoundRowState.UPCOMING -> colors.onSurfaceVariant.copy(alpha = 0.6f)
    }
    Box(
        Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(if (state == RoundRowState.UPCOMING) Color.Transparent else ring)
            .border(1.5.dp, ring, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        val icon = when (state) {
            RoundRowState.DONE -> Icons.Filled.Check
            RoundRowState.CURRENT -> if (paused) Icons.Filled.Pause else Icons.Filled.PlayArrow
            RoundRowState.UPCOMING -> null
        }
        icon?.let {
            Icon(
                it,
                contentDescription = null,
                tint = if (state == RoundRowState.DONE) colors.onPrimary else colors.onSecondary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/**
 * The current step, expanded: title, live clock with progress (tap to pause), the rules hint, and
 * what the step needs (Level Up rule and ladder, the Action seat chooser). "Previous phase" sits
 * at the top of the card, away from the bottom edge, as in the step view.
 */
@Composable
private fun CurrentRow(state: ActiveUiState, row: RoundRowUi, actions: TimerActions) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.large,
        color = colors.secondary.copy(alpha = 0.10f),
        border = BorderStroke(2.dp, accent(state)),
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
    ) {
        Column(Modifier.padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.md)) {
            StepTitle(row, actions.onBack, Modifier.padding(top = Spacing.md))
            RoundClock(state, row, actions.onTogglePause, digits = 52.dp)
            state.hint?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
            CurrentStepExtras(state, actions)
        }
    }
}

/** Title and context of the current step, with "Previous phase" beside it. */
@Composable
private fun StepTitle(row: RoundRowUi, onBack: () -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(
            modifier
                .weight(1f)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        ) {
            Text(row.title, style = MaterialTheme.typography.titleLarge)
            row.context?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
        }
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous phase", tint = colors.onSurfaceVariant)
        }
    }
}

/**
 * Live clock of the current step with its progress bar; tap toggles pause. Digits are sized in dp,
 * not sp: at large font scales they are already the biggest thing on screen.
 */
@Composable
private fun RoundClock(state: ActiveUiState, row: RoundRowUi, onToggle: () -> Unit, digits: Dp) {
    val colors = MaterialTheme.colorScheme
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
    // Same precedence as the step view's dial: paused reads as paused, even in overtime.
    val digitColor = when {
        state.paused -> colors.onSurfaceVariant
        state.overtime -> colors.error
        state.warning -> colors.primary
        else -> colors.onSurface
    }
    val status = when {
        state.paused -> "Paused · tap to resume"
        state.overtime -> "Overtime"
        !state.timed -> "Elapsed · tap to pause"
        else -> "Tap to pause"
    }
    val size = with(LocalDensity.current) { digits.toSp() }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClickLabel = if (state.paused) "Resume" else "Pause", role = Role.Button, onClick = onToggle)
            .semantics(mergeDescendants = true) {
                contentDescription = row.description(state.spokenClock)
                stateDescription = if (state.paused) "Paused" else "Running"
            }
            .padding(vertical = Spacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text(
                state.clock,
                style = timerDigits(MaterialTheme.typography.displayMedium).copy(fontSize = size, lineHeight = size),
                color = digitColor,
                modifier = Modifier.alpha(blink),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    status.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = when {
                        state.paused -> colors.primary
                        state.overtime -> colors.error
                        else -> colors.onSurfaceVariant
                    },
                )
                if (state.timed) {
                    Text(
                        "of ${row.length}",
                        style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }
        if (state.timed) {
            Spacer(Modifier.height(Spacing.sm))
            LinearProgressIndicator(
                progress = { if (state.overtime) 1f else state.fraction },
                color = accent(state),
                trackColor = colors.onSurfaceVariant.copy(alpha = 0.2f),
                strokeCap = StrokeCap.Round,
                modifier = Modifier.fillMaxWidth().height(8.dp),
            )
        }
    }
}

/** What the current step needs below its clock: end-of-round rule (+ Level ladder), Action seats. */
@Composable
private fun CurrentStepExtras(state: ActiveUiState, actions: TimerActions) {
    state.currentDetail?.let { detail ->
        Text(
            detail,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.sm)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                .border(1.dp, GoaTheme.colors.engravedBorder, MaterialTheme.shapes.medium)
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        )
    }
    if (state.checklist?.any { it.state == ChecklistState.CURRENT && it.id == LEVEL_UP_STEP } == true) {
        Spacer(Modifier.height(Spacing.sm))
        LevelLadderButton(actions.onOpenLevels, label = "Level costs & card Tiers")
    }
    state.seats?.let { seats ->
        Spacer(Modifier.height(Spacing.md))
        ResolvingHeroes(seats, actions.onHeroResolves)
    }
}

/** "Jump to Turn 2 · Play a card?": a mis-tap is cheap to undo, but never silent. */
@Composable
private fun JumpDialog(state: ActiveUiState, target: RoundRowUi?, actions: TimerActions, onDismiss: () -> Unit) {
    // A new round makes the target meaningless (the controller ignores it too).
    LaunchedEffect(state.round) { onDismiss() }
    if (target == null) return
    val from = state.rows.firstOrNull { it.state == RoundRowState.CURRENT }?.jumpName ?: state.title
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Jump to ${target.jumpName}?") },
        text = {
            Text(
                "It starts fresh. Previous phase within ${TimerEngine.UNDO_WINDOW_MS / MILLIS_PER_SECOND} seconds " +
                    "returns to $from with its time.",
            )
        },
        confirmButton = {
            Button(onClick = {
                onDismiss()
                actions.onJumpTo(state.round, target.index)
            }) { Text("Jump") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private const val MILLIS_PER_SECOND = 1_000L
