package dev.quinton.goa.feature.timer.ui.timer

import android.view.KeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.MoreTime
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.GoaDialog
import dev.quinton.goa.core.designsystem.component.GoaPlate
import dev.quinton.goa.core.designsystem.component.HeroPortrait
import dev.quinton.goa.core.designsystem.component.LARGE_FONT_SCALE
import dev.quinton.goa.core.designsystem.component.OfficialArt
import dev.quinton.goa.core.designsystem.component.OfficialIcon
import dev.quinton.goa.core.designsystem.component.PlateShape
import dev.quinton.goa.core.designsystem.component.TeamBadge
import dev.quinton.goa.core.designsystem.component.TeamMark
import dev.quinton.goa.core.designsystem.component.slatePlate
import dev.quinton.goa.core.designsystem.component.teamColor
import dev.quinton.goa.core.designsystem.component.teamTextColor
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.timer.TimerText
import dev.quinton.goa.feature.timer.ui.components.plateEdge
import dev.quinton.goa.feature.timer.ui.components.teamName
import dev.quinton.goa.feature.timer.ui.components.timerDigits
import kotlinx.coroutines.delay

/**
 * Opt-in hands-free control (preset "Volume keys control the timer"): while [enabled], volume up
 * = Next and volume down = Pause/Resume. Most Bluetooth camera shutters send volume up, so they
 * work too. Only the first key-down counts (holding a key doesn't skip phases); both key-down
 * and key-up are consumed so the volume doesn't change. Focus is re-taken on every step change.
 */
@Composable
internal fun VolumeKeyControls(
    enabled: Boolean,
    focusKey: Any,
    onNext: () -> Unit,
    onTogglePause: () -> Unit,
    content: @Composable () -> Unit,
) {
    if (!enabled) {
        content()
        return
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(focusKey) { runCatching { focus.requestFocus() } }
    Box(
        Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                val action: (() -> Unit)? = when (event.key.nativeKeyCode) {
                    KeyEvent.KEYCODE_VOLUME_UP -> onNext
                    KeyEvent.KEYCODE_VOLUME_DOWN -> onTogglePause
                    else -> null
                }
                if (action != null && event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                    action()
                }
                action != null
            }
            .focusRequester(focus)
            .focusable(),
    ) { content() }
}

/**
 * Coin side currently showing; tap when the coin is flipped at the table. Undo stays for
 * [COIN_UNDO_MS] or until the next timer action ([actionKey] changes), whichever comes first.
 */
@Composable
internal fun TieBreakerChip(
    team: Team,
    onFlip: () -> Unit,
    actionKey: Any? = null,
    showLabel: Boolean = true,
    onUndoShown: (Boolean) -> Unit = {},
) {
    var flips by remember { mutableIntStateOf(0) }
    var showUndo by remember { mutableStateOf(false) }
    LaunchedEffect(flips) {
        if (flips == 0) return@LaunchedEffect
        showUndo = true
        delay(COIN_UNDO_MS)
        showUndo = false
    }
    LaunchedEffect(actionKey) { showUndo = false }
    // The header makes room for Undo by moving optional icons into its menu.
    LaunchedEffect(showUndo) { onUndoShown(showUndo) }
    if (showUndo) {
        TextButton(
            onClick = {
                showUndo = false
                onFlip()
            },
            // It sits on the banner: the banner's own ink, not bronze on steel.
            colors = ButtonDefaults.textButtonColors(contentColor = LocalContentColor.current),
            modifier = Modifier.heightIn(min = 48.dp),
        ) { Text("Undo") }
    }
    // On the banner: a slate chip keeps the team's colour readable on the steel.
    Surface(
        onClick = {
            onFlip()
            flips++
        },
        shape = RoundedCornerShape(50),
        color = Color.Transparent,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .padding(vertical = Spacing.xs)
            .slatePlate(RoundedCornerShape(50), brackets = false)
            .semantics {
                contentDescription = "Tie Breaker coin: ${teamName(team)}"
                onClick(label = "Flip the coin") {
                    onFlip()
                    flips++
                    true
                }
            },
    ) {
        // The coin's own face with what it is and whose side shows ("Coin Red"): not a bare team mark.
        Row(
            Modifier.padding(horizontal = CHIP_INSET, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoinMark(team, Modifier.size(coinChipMark()))
            if (showLabel) {
                Spacer(Modifier.width(COIN_GAP))
                Text(
                    buildAnnotatedString {
                        append(COIN_WORD)
                        withStyle(SpanStyle(color = teamTextColor(team))) { append(teamName(team)) }
                    },
                    style = coinChipLabelStyle(),
                    maxLines = 1,
                )
            }
        }
    }
}

/** The Tie Breaker coin's printed face for [team] (rulebook p.5); the team's mark while it loads. */
@Composable
private fun CoinMark(team: Team, modifier: Modifier = Modifier) {
    OfficialIcon(OfficialArt.tieBreaker(team), modifier) { TeamMark(team, Modifier.matchParentSize()) }
}

/** The coin chip's mark: a little larger with the text, not as fast. */
@Composable
internal fun coinChipMark(): Dp = 20.dp * LocalDensity.current.fontScale.coerceIn(1f, COIN_MAX_SCALE)

@Composable
internal fun coinChipLabelStyle(): TextStyle = MaterialTheme.typography.labelLarge.copy(
    fontWeight = FontWeight.SemiBold,
)

/** The chip's padding at each side. */
internal val CHIP_INSET = 10.dp

/** The chip's word before the team's name: "Coin Red". */
internal const val COIN_WORD = "Coin "

/** Gap between the coin and its words. */
internal val COIN_GAP = 6.dp
private const val COIN_MAX_SCALE = 1.3f

/** Asks which side shows instead of assuming one (rulebook p.7: the coin is flipped at setup). */
@Composable
internal fun CoinSideDialog(current: Team?, onPick: (Team?) -> Unit, onDismiss: () -> Unit) {
    GoaDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tie Breaker coin") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text("Which team's symbol is showing on the coin?")
                Team.entries.forEach { team ->
                    val color = teamColor(team)
                    OutlinedButton(
                        onClick = { onPick(team) },
                        border = BorderStroke(if (team == current) 2.dp else 1.dp, color),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = color.copy(alpha = 0.10f)),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    ) {
                        TeamBadge(team, showLabel = false)
                        Spacer(Modifier.width(Spacing.sm))
                        Text("${teamName(team)} shows", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        dismissButton = if (current != null) {
            { TextButton(onClick = { onPick(null) }) { Text("Stop tracking") } }
        } else {
            null
        },
    )
}

/**
 * Table rules under the dial: the "Hurry up" button and team time-outs. A running Hurry up is not
 * here: it is pinned above the controls ([ShotClockPanel]) so it can't scroll out of sight.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TableRulesBar(state: ActiveUiState, actions: TimerActions) {
    val shot = state.shotClock
    val startable = state.shotClockSeconds != null && shot == null
    if (!startable && state.timeOuts == null) return
    Spacer(Modifier.height(Spacing.md))
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        val seconds = state.shotClockSeconds
        if (seconds != null && shot == null) {
            PillButton(
                icon = Icons.Outlined.Bolt,
                label = "Start hurry-up · ${TimerText.durationLabel(seconds)}",
                spoken = "Start hurry-up, ${TimerText.spokenDuration(seconds.toLong())}",
                onClick = actions.onShotClock,
            )
        }
        state.timeOuts?.let { TimeOutButton(it, actions.onTimeOut) }
    }
}

/**
 * A table-rule action under the dial: an outlined pill with a bronze edge and icon, and a verb, so it reads as
 * a button to press and never as the status pills beside it (the step clock, plain slate). Slate under it so
 * its words keep their contrast on the art.
 */
@Composable
private fun PillButton(
    icon: ImageVector,
    label: String,
    spoken: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val accent = MaterialTheme.colorScheme.primary
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = PILL,
        border = BorderStroke(PILL_EDGE, if (enabled) accent else accent.copy(alpha = DISABLED_EDGE)),
        contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.xs),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurface,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        modifier = Modifier
            .heightIn(min = 48.dp)
            .slatePlate(PILL, brackets = false, edge = false)
            .semantics { contentDescription = spoken },
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (enabled) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

private val PILL = RoundedCornerShape(50)
private val PILL_EDGE = 1.5.dp
private const val DISABLED_EDGE = 0.4f

/**
 * The running "Hurry up" countdown, pinned just above the timer controls (game night: inside the
 * scrolling column it sat behind the controls, digits clipped). [compact]: landscape. [clockInDial]:
 * the dial already shows the countdown as the big clock, so this is a slim bar with Restart and Stop.
 */
@Composable
internal fun ShotClockPanel(
    shot: ShotClockUi,
    onRestart: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    clockInDial: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val accent = if (shot.over) colors.error else colors.primary
    if (clockInDial) {
        ShotClockBar(shot, accent, onRestart, onCancel, modifier)
        return
    }
    GoaPlate(modifier.fillMaxWidth().plateEdge(accent), contentPadding = PaddingValues(0.dp)) {
        Row(
            Modifier.padding(start = Spacing.lg, end = Spacing.xs, top = Spacing.xs, bottom = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                Modifier.weight(1f).semantics(mergeDescendants = true) {
                    contentDescription = shot.spokenClock
                    if (shot.over) liveRegion = LiveRegionMode.Polite
                },
            ) {
                Text(
                    if (shot.over) "HURRY UP · TIME!" else "HURRY UP",
                    style = MaterialTheme.typography.labelLarge,
                    color = accent,
                )
                Text(
                    shot.clock,
                    style = timerDigits(
                        if (compact) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.displayMedium,
                    ),
                    color = if (shot.over || shot.warning) accent else colors.onSurface,
                )
            }
            IconButton(onClick = onRestart, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Outlined.Replay, contentDescription = "Restart Hurry up")
            }
            IconButton(onClick = onCancel, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Stop Hurry up")
            }
        }
    }
}

/** Hurry up's own controls while its countdown is in the dial: named in words, so Stop never reads as the step's. */
@Composable
private fun ShotClockBar(
    shot: ShotClockUi,
    accent: Color,
    onRestart: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier,
) {
    val words = LocalDensity.current.fontScale <= LARGE_FONT_SCALE
    GoaPlate(modifier.fillMaxWidth().plateEdge(accent, 1.dp), contentPadding = PaddingValues(0.dp)) {
        Row(
            Modifier.padding(start = Spacing.lg, end = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Bolt, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.xs))
            Text(
                if (shot.over) "HURRY UP · TIME!" else "HURRY UP",
                style = MaterialTheme.typography.labelLarge,
                color = accent,
                maxLines = 1,
                modifier = Modifier.weight(1f).semantics { contentDescription = shot.spokenClock },
            )
            ShotBarButton(Icons.Outlined.Replay, "Restart", "Restart Hurry up", words, onRestart)
            ShotBarButton(Icons.Filled.Close, "Stop", "Stop Hurry up", words, onCancel)
        }
    }
}

@Composable
private fun ShotBarButton(icon: ImageVector, word: String, spoken: String, words: Boolean, onClick: () -> Unit) {
    if (words) {
        TextButton(
            onClick = onClick,
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
            modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = spoken },
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.xs))
            Text(word)
        }
    } else {
        IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) { Icon(icon, contentDescription = spoken) }
    }
}

@Composable
private fun TimeOutButton(timeOuts: TimeOutsUi, onTimeOut: (Team) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val any = timeOuts.redLeft + timeOuts.blueLeft > 0
    Box {
        val label = "Time-out +${TimerText.durationLabel(timeOuts.seconds)}"
        PillButton(
            icon = Icons.Outlined.MoreTime,
            label = label,
            spoken = label,
            onClick = { open = true },
            enabled = timeOuts.usable && any,
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            listOf(Team.RED to timeOuts.redLeft, Team.BLUE to timeOuts.blueLeft).forEach { (team, left) ->
                DropdownMenuItem(
                    text = { Text("${teamName(team)} team · $left left this round") },
                    leadingIcon = { TeamBadge(team, showLabel = false) },
                    enabled = left > 0,
                    onClick = {
                        open = false
                        onTimeOut(team)
                    },
                )
            }
        }
    }
}

/** Columns for the hero tiles: every seat (up to 10) fits in at most two rows. */
private fun seatColumns(count: Int): Int = when {
    count <= 4 -> count.coerceAtLeast(2)
    count <= 6 -> 3
    count <= 8 -> 4
    else -> 5
}

/**
 * Action timer: tap the hero who resolves now (highest unresolved initiative acts first). Naming
 * the slot's hero starts their full time; tapping another hero then moves on to them.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ResolvingHeroes(seats: List<SeatUi>, onResolve: (Int) -> Unit) {
    val columns = seatColumns(seats.size)
    Column(Modifier.fillMaxWidth()) {
        Text(
            "Tap the hero who resolves now to start their time.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Spacing.sm),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            maxItemsInEachRow = columns,
        ) {
            seats.forEach { seat -> SeatChip(seat, Modifier.weight(1f)) { onResolve(seat.seat) } }
            // Keep the last row's tiles the same width as the rows above.
            repeat((columns - seats.size % columns) % columns) { Spacer(Modifier.weight(1f)) }
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
        shape = PlateShape,
        color = if (seat.acting) colors.secondary.copy(alpha = 0.16f) else Color.Transparent,
        border = BorderStroke(
            if (seat.acting) 2.dp else 1.dp,
            if (seat.acting) colors.secondary else team.copy(alpha = 0.6f),
        ),
        modifier = modifier
            .heightIn(min = 48.dp)
            .alpha(if (seat.resolved) 0.5f else 1f)
            .slatePlate(PlateShape, brackets = false)
            .semantics(mergeDescendants = true) {
                contentDescription = "${seat.heroName}, ${teamName(seat.team)} team" +
                    (seat.playerName?.let { ", $it" } ?: "")
                stateDescription = status
            },
    ) {
        Column(
            Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(contentAlignment = Alignment.BottomEnd) {
                HeroPortrait(
                    heroId = seat.heroId ?: "unknown",
                    portrait = seat.portrait,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                )
                if (seat.resolved) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = colors.onSecondary,
                        modifier = Modifier.size(16.dp).padding(1.dp),
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                seat.heroName,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TeamBadge(seat.team, showLabel = false)
                seat.playerName?.let {
                    Spacer(Modifier.width(2.dp))
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

private const val COIN_UNDO_MS = 8_000L
