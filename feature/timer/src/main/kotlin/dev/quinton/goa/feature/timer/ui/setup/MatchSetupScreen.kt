package dev.quinton.goa.feature.timer.ui.setup

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.quinton.goa.core.designsystem.component.GoaScreen
import dev.quinton.goa.core.designsystem.component.TeamBadge
import dev.quinton.goa.core.designsystem.component.teamColor
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.presentation.timer.CoinSource
import dev.quinton.goa.core.presentation.timer.CountersUi
import dev.quinton.goa.core.presentation.timer.LayoutOptionUi
import dev.quinton.goa.core.presentation.timer.LinkedMatchUi
import dev.quinton.goa.core.presentation.timer.MatchSetupText
import dev.quinton.goa.core.presentation.timer.MatchSetupUiState
import dev.quinton.goa.core.presentation.timer.ModeOptionUi
import dev.quinton.goa.feature.timer.ui.components.CoinFace
import dev.quinton.goa.feature.timer.ui.components.goaChipBorder
import dev.quinton.goa.feature.timer.ui.components.goaChipColors
import dev.quinton.goa.feature.timer.ui.components.rememberReducedMotion
import dev.quinton.goa.feature.timer.ui.components.rememberStartWithNotificationPermission
import dev.quinton.goa.feature.timer.ui.components.teamName
import dev.quinton.goa.feature.timer.ui.timer.SectionHeader
import dev.quinton.goa.feature.timer.ui.timer.Stepper
import kotlin.math.cos
import kotlin.math.sin

data class SetupActions(
    val onBack: () -> Unit = {},
    val onPlayers: (Int) -> Unit = {},
    val onLayout: (String) -> Unit = {},
    val onMode: (GameMode) -> Unit = {},
    val onFlip: () -> Unit = {},
    val onSetCoin: (Team) -> Unit = {},
    val onCorrectCoin: () -> Unit = {},
    val onPreset: (String) -> Unit = {},
    val onStart: () -> Unit = {},
)

@Composable
fun MatchSetupRoute(viewModel: MatchSetupViewModel, onBack: () -> Unit, onTimerStarted: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.navigation.collect { event ->
            when (event) {
                SetupEvent.TimerStarted -> onTimerStarted()
            }
        }
    }
    MatchSetupScreen(
        state = state,
        actions = SetupActions(
            onBack = onBack,
            onPlayers = viewModel::setPlayers,
            onLayout = viewModel::selectLayout,
            onMode = viewModel::selectMode,
            onFlip = viewModel::flipCoin,
            onSetCoin = viewModel::setCoin,
            onCorrectCoin = viewModel::correctCoin,
            onPreset = viewModel::selectPreset,
            // Same POST_NOTIFICATIONS request as the Timer tab's start; starts either way.
            onStart = rememberStartWithNotificationPermission(viewModel::startTimer),
        ),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MatchSetupScreen(state: MatchSetupUiState, actions: SetupActions) {
    GoaScreen(title = "Match setup", onBack = actions.onBack) { padding ->
        if (state.loading) return@GoaScreen
        Column(Modifier.fillMaxSize().padding(padding)) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                state.linkedMatch?.let { LinkedMatchBanner(it) }
                SectionHeader("Players")
                PlayersCard(state, actions.onPlayers)
                if (state.layouts.size > 1 || state.layouts.firstOrNull()?.guidance != null) {
                    SectionHeader("Teams")
                    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        state.layouts.forEach { layout ->
                            LayoutCard(layout, layout.id == state.selectedLayoutId, state.layouts.size > 1) {
                                actions.onLayout(layout.id)
                            }
                        }
                    }
                }
                SectionHeader("Mode")
                Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    state.modes.forEach { mode ->
                        ModeCard(mode, mode.mode == state.selectedMode) { actions.onMode(mode.mode) }
                    }
                }
                Note(state.firstGameAdvice)
                state.counters?.let {
                    SectionHeader("Lay out")
                    CountersBoard(it)
                }
                SectionHeader("Tie Breaker coin")
                TieBreakerCard(state, actions)
                SectionHeader("Timer preset")
                // Wraps instead of scrolling sideways, so no preset name is cut off at the edge.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    state.presets.forEach { preset ->
                        FilterChip(
                            selected = preset.id == state.selectedPresetId,
                            onClick = { actions.onPreset(preset.id) },
                            label = { Text(preset.name) },
                            colors = goaChipColors(),
                            border = goaChipBorder(preset.id == state.selectedPresetId),
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                }
                state.presets.firstOrNull { it.id == state.selectedPresetId }?.let {
                    Text(
                        it.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(Spacing.lg))
            }
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
                Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.md)) {
                    if (state.replacesRunningTimer) {
                        Text(
                            "A timer is already running; starting replaces it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = Spacing.sm),
                        )
                    }
                    Button(
                        onClick = actions.onStart,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(Spacing.sm))
                        Text("Start timer", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupCard(
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (highlighted) colors.secondary.copy(alpha = 0.10f) else colors.surface,
        border = BorderStroke(
            if (highlighted) 1.5.dp else 1.dp,
            if (highlighted) colors.secondary else GoaTheme.colors.engravedBorder,
        ),
        modifier = modifier.fillMaxWidth(),
        content = content,
    )
}

@Composable
private fun LinkedMatchBanner(match: LinkedMatchUi) {
    SetupCard(Modifier.padding(top = Spacing.md)) {
        Row(Modifier.padding(Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(match.title, style = MaterialTheme.typography.titleSmall)
                Text(
                    "Mode, Tie Breaker coin and duration are saved to this match.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (match.redSeats + match.blueSeats > 0) {
                Column(horizontalAlignment = Alignment.End) {
                    TeamCount(Team.RED, match.redSeats)
                    TeamCount(Team.BLUE, match.blueSeats)
                }
            }
        }
    }
}

@Composable
private fun TeamCount(team: Team, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TeamBadge(team)
        Spacer(Modifier.width(Spacing.xs))
        Text("$count", style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun PlayersCard(state: MatchSetupUiState, onPlayers: (Int) -> Unit) {
    SetupCard {
        Column(Modifier.padding(Spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${state.players} players", style = MaterialTheme.typography.titleMedium)
                    Text(
                        when {
                            state.seatsSource != null -> state.seatsSource!!
                            state.recommended -> "Recommended count"
                            else -> "Supported: ${state.playerRange.first} to ${state.playerRange.last}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = with(MaterialTheme.colorScheme) {
                            if (state.recommended) secondary else onSurfaceVariant
                        },
                    )
                }
                if (!state.playersLocked) {
                    Stepper(state.players, state.playerRange, onPlayers, label = "players")
                }
            }
            state.extendedNote?.let {
                Spacer(Modifier.height(Spacing.sm))
                Note(it)
            }
        }
    }
}

@Composable
private fun Note(text: String) {
    if (text.isBlank()) return
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            Icons.Outlined.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp).padding(top = 2.dp),
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.14f)) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 2.dp),
        )
    }
}

@Composable
private fun LayoutCard(layout: LayoutOptionUi, selected: Boolean, selectable: Boolean, onClick: () -> Unit) {
    SetupCard(
        modifier = if (selectable) {
            Modifier.selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
        } else {
            Modifier
        },
        highlighted = selected && selectable,
    ) {
        Row(Modifier.padding(Spacing.md), verticalAlignment = Alignment.Top) {
            if (selectable) RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(Spacing.xs))
            Column(Modifier.weight(1f).padding(start = Spacing.xs)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        layout.title,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    if (layout.official) {
                        Badge("Official", MaterialTheme.colorScheme.primary)
                    } else {
                        Badge("House rule", MaterialTheme.colorScheme.secondary)
                    }
                }
                Text(layout.teams, style = MaterialTheme.typography.bodyMedium)
                layout.guidance?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeCard(mode: ModeOptionUi, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    SetupCard(
        modifier = Modifier
            // No alpha dimming: the "why unavailable" text must stay readable (>= 4.5:1). The disabled
            // radio button and muted title already signal that the mode can't be chosen.
            .selectable(selected = selected, enabled = mode.available, onClick = onClick, role = Role.RadioButton),
        highlighted = selected,
    ) {
        Row(Modifier.padding(Spacing.md), verticalAlignment = Alignment.Top) {
            RadioButton(
                selected = selected,
                onClick = null,
                enabled = mode.available,
                modifier = Modifier.padding(Spacing.xs),
            )
            Column(Modifier.weight(1f).padding(start = Spacing.xs)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        mode.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (mode.available) colors.onSurface else colors.onSurfaceVariant,
                    )
                    mode.requiredLabel?.let {
                        Spacer(Modifier.width(Spacing.sm))
                        Badge(it, if (it == "Required") colors.primary else colors.secondary)
                    }
                }
                if (mode.reason != null) {
                    Text(mode.reason!!, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                } else {
                    Text(
                        "${mode.waves} · ${mode.life}",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun CountersBoard(counters: CountersUi) {
    SetupCard {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text("Wave counters", style = MaterialTheme.typography.titleSmall)
            repeat(counters.lanes) { lane ->
                TokenRow(
                    label = if (counters.lanes > 1) "Lane ${lane + 1}" else null,
                    count = counters.wavesPerLane,
                    color = MaterialTheme.colorScheme.primary,
                    hexagon = true,
                    description = if (counters.lanes > 1) {
                        "Lane ${lane + 1}: ${counters.wavesPerLane} Wave counters"
                    } else {
                        "${counters.wavesPerLane} Wave counters"
                    },
                )
            }
            Text("Life counters per team", style = MaterialTheme.typography.titleSmall)
            val life = counters.lifePerTeam
            if (life == null) {
                Note(MatchSetupText.lifeNotSpecified(counters))
            } else {
                Team.entries.forEach { team ->
                    TokenRow(
                        label = null,
                        leading = { TeamBadge(team) },
                        count = life,
                        color = teamColor(team),
                        hexagon = false,
                        description = "${teamName(team)}: $life Life counters",
                    )
                }
            }
            counters.houseRuleNote?.let { HouseRuleNote(it) }
            Text(
                MatchSetupText.seatsLine(counters),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            counters.modeNote?.let { RulesNoteBlock(it.title, it.points) }
        }
    }
}

/** A titled list of rule points (from the rules data), as short bullets that read at a glance. */
@Composable
private fun RulesNoteBlock(title: String, points: List<String>) {
    if (points.isEmpty()) return
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(Spacing.sm))
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        points.forEach { point ->
            Row {
                Text(
                    "•",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(18.dp + Spacing.sm),
                    textAlign = TextAlign.Center,
                )
                Text(
                    point,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** A house-rule label next to the counters it changes (secondary colour, like the layout's badge). */
@Composable
private fun HouseRuleNote(text: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(Spacing.sm),
        )
    }
}

@Composable
private fun TokenRow(
    label: String?,
    count: Int,
    color: Color,
    hexagon: Boolean,
    description: String,
    leading: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(64.dp)) {
            when {
                leading != null -> leading()

                label != null -> Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(count.coerceAtMost(MAX_TOKENS)) { Token(color, hexagon) }
        }
        Text("×$count", style = MaterialTheme.typography.titleSmall, color = color)
    }
}

@Composable
private fun Token(color: Color, hexagon: Boolean, size: Dp = 22.dp) {
    Canvas(Modifier.size(size)) {
        val r = this.size.minDimension / 2f
        val c = Offset(this.size.width / 2, this.size.height / 2)
        if (hexagon) {
            val path = Path()
            for (i in 0 until 6) {
                val a = Math.toRadians((60.0 * i) - 30.0)
                val p = Offset(c.x + r * cos(a).toFloat(), c.y + r * sin(a).toFloat())
                if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            }
            path.close()
            drawPath(path, color.copy(alpha = 0.22f))
            drawPath(path, color, style = Stroke(1.5.dp.toPx()))
        } else {
            drawCircle(color.copy(alpha = 0.22f), r, c)
            drawCircle(color, r - 0.75.dp.toPx(), c, style = Stroke(1.5.dp.toPx()))
            drawCircle(color, r * 0.35f, c)
        }
    }
}

@Composable
private fun TieBreakerCard(state: MatchSetupUiState, actions: SetupActions) {
    val reduced = rememberReducedMotion()
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(state.flips) {
        if (state.flips == 0) return@LaunchedEffect
        if (reduced) {
            rotation.snapTo(0f)
        } else {
            rotation.snapTo(0f)
            rotation.animateTo(COIN_SPIN, tween(COIN_SPIN_MS, easing = FastOutSlowInEasing))
            rotation.snapTo(0f)
        }
    }
    val angle = rotation.value % 360f
    val coin = state.tieBreaker
    val face = if (rotation.isRunning && angle in 90f..270f) coin?.opponent else coin
    SetupCard {
        Column(Modifier.padding(Spacing.lg), horizontalAlignment = Alignment.CenterHorizontally) {
            CoinFace(
                team = face,
                modifier = Modifier.graphicsLayer {
                    rotationY = rotation.value
                    cameraDistance = 12f * density
                },
            )
            Spacer(Modifier.height(Spacing.md))
            Text(
                MatchSetupText.coinMessage(state),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            if (coin != null) {
                Text(
                    MatchSetupText.coinAdvantage(coin),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(Spacing.md))
            when {
                // The coin was flipped once already (rulebook p.5): correct a mistake, don't flip again.
                state.coinPlaced && !state.correctingCoin -> OutlinedButton(
                    onClick = actions.onCorrectCoin,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("Correct the coin") }

                state.coinPlaced -> CoinSideChips(coin, "Which side is showing?", actions.onSetCoin)

                else -> {
                    OutlinedButton(onClick = actions.onFlip, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(if (coin == null) "Flip coin" else "Flip again")
                    }
                    Spacer(Modifier.height(Spacing.sm))
                    CoinSideChips(coin, "Flipped the real coin?", actions.onSetCoin)
                }
            }
            Spacer(Modifier.height(Spacing.md))
            RulesNoteBlock("How the coin is used", state.tieBreakerRule)
        }
    }
}

@Composable
private fun CoinSideChips(selected: Team?, label: String, onSet: (Team) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.xs))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Team.entries.forEach { team ->
                FilterChip(
                    selected = selected == team,
                    onClick = { onSet(team) },
                    label = { Text("${teamName(team)} shows", color = teamColor(team)) },
                    colors = goaChipColors(),
                    border = goaChipBorder(selected == team),
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
    }
}

private const val MAX_TOKENS = 8
private const val COIN_SPIN = 1440f
private const val COIN_SPIN_MS = 900
