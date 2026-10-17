package dev.quinton.goa.feature.timer.ui.components

import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.teamColor
import dev.quinton.goa.core.designsystem.theme.GoaFonts
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.domain.timer.RoundSection
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.timer.ui.timer.RoundPipsUi

/** True when the user turned animations off (Settings → Accessibility → Remove animations). */
@Composable
fun rememberReducedMotion(): Boolean {
    if (LocalInspectionMode.current) return false
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/*
 * The shared theme defines no secondary/tertiary *container* roles, so Material falls back to its
 * lavender defaults. Timer controls use these explicit "Sunken bronze" colours instead.
 */
@Composable
fun goaTonalIconColors(): IconButtonColors = IconButtonDefaults.filledTonalIconButtonColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor = MaterialTheme.colorScheme.onSurface,
)

@Composable
fun goaChipColors(): SelectableChipColors = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f),
    selectedLabelColor = MaterialTheme.colorScheme.onSurface,
    selectedLeadingIconColor = MaterialTheme.colorScheme.secondary,
)

@Composable
fun goaChipBorder(selected: Boolean) = FilterChipDefaults.filterChipBorder(
    enabled = true,
    selected = selected,
    selectedBorderColor = MaterialTheme.colorScheme.secondary,
    selectedBorderWidth = 1.dp,
)

/** Keeps the display awake while composed (timer screen only, ARCHITECTURE.md "Timer design"). */
@Composable
fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

/** Clock digits: tabular figures so the countdown never jitters. */
fun timerDigits(base: TextStyle): TextStyle = base.copy(
    fontFamily = GoaFonts.display,
    fontWeight = FontWeight.SemiBold,
    fontFeatureSettings = "tnum, lnum",
)

enum class RingTone { NORMAL, WARNING, OVERTIME, PAUSED, UNTIMED }

/**
 * Engraved countdown ring: a bronze bezel with minute ticks, a track and a teal arc for the
 * remaining fraction. Warning turns bronze, overtime turns red and gently pulses (unless reduced
 * motion); state is never colour-only — the content slot always carries a text label.
 */
@Composable
fun CountdownRing(
    fraction: Float,
    tone: RingTone,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 14.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val reducedMotion = rememberReducedMotion()
    val animated by animateFloatAsState(
        targetValue = fraction,
        animationSpec = if (reducedMotion) tween(0) else tween(durationMillis = 120, easing = LinearEasing),
        label = "ring",
    )
    val pulse = if (tone == RingTone.OVERTIME && !reducedMotion) {
        val transition = rememberInfiniteTransition(label = "overtime")
        transition.animateFloat(
            initialValue = 1f,
            targetValue = 0.45f,
            animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "pulse",
        ).value
    } else {
        1f
    }
    val colors = MaterialTheme.colorScheme
    val arc = when (tone) {
        RingTone.NORMAL -> colors.secondary
        RingTone.WARNING -> colors.primary
        RingTone.OVERTIME -> colors.error
        RingTone.PAUSED -> colors.onSurfaceVariant
        RingTone.UNTIMED -> colors.secondary.copy(alpha = 0.55f)
    }
    val track = colors.onSurface.copy(alpha = 0.08f)
    val bezel = GoaTheme.colors.engravedBorder
    val tick = colors.primary.copy(alpha = 0.35f)
    val glow = arc.copy(alpha = 0.10f)
    val surface = colors.surface
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val radius = size.minDimension / 2f
            val center = Offset(size.width / 2f, size.height / 2f)
            // Face with a soft inner glow in the arc colour.
            drawCircle(
                brush = Brush.radialGradient(listOf(glow, surface.copy(alpha = 0.6f)), center, radius),
                radius = radius - stroke,
                center = center,
            )
            drawCircle(color = bezel, radius = radius - 1.dp.toPx(), center = center, style = Stroke(1.dp.toPx()))
            // 60 engraved ticks, every 5th longer.
            for (i in 0 until 60) {
                rotate(i * 6f, center) {
                    val long = i % 5 == 0
                    val outer = radius - 4.dp.toPx()
                    val inner = outer - if (long) 7.dp.toPx() else 3.dp.toPx()
                    drawLine(
                        tick,
                        Offset(center.x, center.y - outer),
                        Offset(center.x, center.y - inner),
                        strokeWidth = if (long) 1.5.dp.toPx() else 1.dp.toPx(),
                    )
                }
            }
            val inset = stroke / 2f + 14.dp.toPx()
            val arcSize = Size(size.minDimension - inset * 2, size.minDimension - inset * 2)
            val topLeft = Offset(center.x - arcSize.width / 2, center.y - arcSize.height / 2)
            drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            val sweep = when (tone) {
                RingTone.OVERTIME, RingTone.UNTIMED -> 360f
                else -> 360f * animated.coerceIn(0f, 1f)
            }
            if (sweep > 0f) {
                drawArc(
                    color = arc.copy(alpha = arc.alpha * pulse),
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
        content()
    }
}

/**
 * Round progress: ◇ round start · four turn pips · ▢ end of round. Current = filled + ring,
 * done = filled, upcoming = outline. Shapes differ so position is readable without colour.
 */
@Composable
fun RoundPips(pips: RoundPipsUi, round: Int, modifier: Modifier = Modifier) {
    val description = buildString {
        append("Round $round. ")
        append(
            when (pips.section) {
                RoundSection.ROUND_START -> "Round start"
                RoundSection.TURN -> "Turn ${pips.currentTurn} of ${pips.turns}"
                RoundSection.ROUND_END -> "End of round"
            },
        )
    }
    Row(
        modifier.clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val sectionOrder = pips.section.ordinal
        if (pips.hasRoundStart) {
            Pip(
                state = pipState(sectionOrder, RoundSection.ROUND_START.ordinal),
                shape = RoundedCornerShape(2.dp),
                rotate = true,
                label = null,
            )
            Spacer(Modifier.size(Spacing.xs))
        }
        for (turn in 1..pips.turns) {
            val state = when {
                pips.section == RoundSection.ROUND_START -> PipState.UPCOMING
                pips.section == RoundSection.ROUND_END -> PipState.DONE
                turn < (pips.currentTurn ?: 0) -> PipState.DONE
                turn == pips.currentTurn -> PipState.CURRENT
                else -> PipState.UPCOMING
            }
            Pip(state = state, shape = CircleShape, rotate = false, label = turn.toString())
        }
        Spacer(Modifier.size(Spacing.xs))
        Pip(
            state = pipState(sectionOrder, RoundSection.ROUND_END.ordinal),
            shape = RoundedCornerShape(4.dp),
            rotate = false,
            label = null,
        )
    }
}

private enum class PipState { DONE, CURRENT, UPCOMING }

private fun pipState(current: Int, pip: Int) = when {
    current > pip -> PipState.DONE
    current == pip -> PipState.CURRENT
    else -> PipState.UPCOMING
}

@Composable
private fun Pip(state: PipState, shape: androidx.compose.ui.graphics.Shape, rotate: Boolean, label: String?) {
    val colors = MaterialTheme.colorScheme
    val size = if (state == PipState.CURRENT) 26.dp else 20.dp
    // Decorative (semantics live on the row), so the digit is sized to the pip, not the font scale.
    val digit = with(LocalDensity.current) { 11.dp.toSp() }
    val fill = when (state) {
        PipState.DONE -> colors.primary.copy(alpha = 0.55f)
        PipState.CURRENT -> colors.secondary
        PipState.UPCOMING -> Color.Transparent
    }
    val border = when (state) {
        PipState.UPCOMING -> colors.onSurfaceVariant.copy(alpha = 0.5f)
        PipState.CURRENT -> colors.secondary.copy(alpha = 0.35f)
        PipState.DONE -> colors.primary.copy(alpha = 0.55f)
    }
    Box(
        Modifier
            .size(size)
            .then(if (rotate) Modifier.padding(3.dp) else Modifier)
            .graphicsRotate(if (rotate) 45f else 0f)
            .clip(shape)
            .background(fill)
            .border(if (state == PipState.CURRENT) 3.dp else 1.5.dp, border, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (label != null && state != PipState.UPCOMING) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = digit, lineHeight = digit),
                color = if (state == PipState.CURRENT) colors.onSecondary else colors.onPrimary,
            )
        } else if (label != null) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = digit, lineHeight = digit),
                color = colors.onSurfaceVariant,
            )
        }
    }
}

private fun Modifier.graphicsRotate(degrees: Float): Modifier = if (degrees == 0f) this else rotate(degrees)

/** The Tie Breaker coin face: ◆ Red or ● Blue, with the team name. */
@Composable
fun CoinFace(team: Team?, modifier: Modifier = Modifier, size: Dp = 96.dp) {
    val color = team?.let { teamColor(it) } ?: MaterialTheme.colorScheme.onSurfaceVariant
    val bronze = MaterialTheme.colorScheme.primary
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    listOf(bronze.copy(alpha = 0.35f), bronze.copy(alpha = 0.12f)),
                ),
            )
            .border(3.dp, bronze.copy(alpha = 0.8f), CircleShape)
            .padding(6.dp)
            .border(1.dp, bronze.copy(alpha = 0.5f), CircleShape)
            .semantics {
                contentDescription =
                    team?.let { "Tie Breaker coin showing ${teamName(it)}" } ?: "Coin not flipped"
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = when (team) {
                Team.RED -> "◆"
                Team.BLUE -> "●"
                null -> "?"
            },
            color = color,
            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
        )
    }
}

fun teamName(team: Team): String = if (team == Team.RED) "Red" else "Blue"
