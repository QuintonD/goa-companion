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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.quinton.goa.core.designsystem.component.OfficialArt
import dev.quinton.goa.core.designsystem.component.OfficialIcon
import dev.quinton.goa.core.designsystem.component.teamColor
import dev.quinton.goa.core.designsystem.theme.GoaFonts
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

/** The design system's steel chip, bronze when selected. */
@Composable
fun goaChipColors(): SelectableChipColors = dev.quinton.goa.core.designsystem.component.selectableChipColors()

@Composable
fun goaChipBorder(selected: Boolean) = dev.quinton.goa.core.designsystem.component.selectableChipBorder(selected)

/** Keeps the display awake while composed (timer screen only, ARCHITECTURE.md "Timer design"). */
@Composable
fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

/** Clock digits (Inter, the clearest figures at a distance): tabular so the countdown never jitters. */
fun timerDigits(base: TextStyle): TextStyle = base.copy(
    fontFamily = GoaFonts.body,
    fontWeight = FontWeight.SemiBold,
    letterSpacing = 0.sp,
    fontFeatureSettings = "tnum, lnum",
)

enum class RingTone { NORMAL, WARNING, OVERTIME, PAUSED, UNTIMED }

/**
 * The dial of mock-up 07: a steel double ring around the progress arc, and the digits on a near-black
 * disc. Teal running, bronze in the final seconds, red and gently pulsing in overtime (unless reduced
 * motion); state is never colour-only, the content slot carries a text label. Proportions scale with
 * the dial, so a small dial (landscape, Action) keeps its look.
 */
@Composable
fun CountdownRing(
    fraction: Float,
    tone: RingTone,
    modifier: Modifier = Modifier,
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
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val unit = size.minDimension / DIAL_UNITS
            val center = Offset(size.width / 2f, size.height / 2f)
            val steel = Brush.linearGradient(
                0f to STEEL_LIGHT,
                0.45f to STEEL_DARK,
                0.6f to STEEL_SHINE,
                1f to STEEL_DEEP,
                start = Offset.Zero,
                end = Offset(size.width, size.height),
            )
            val outerEdge = (OUTER_STROKE * unit).coerceAtLeast(1.dp.toPx())
            val innerEdge = (INNER_STROKE * unit).coerceAtLeast(1.dp.toPx())
            drawCircle(DIAL_BACK, radius = OUTER_RADIUS * unit, center = center)
            drawCircle(steel, radius = OUTER_RADIUS * unit, center = center, style = Stroke(outerEdge))
            drawCircle(DIAL_FACE, radius = INNER_RADIUS * unit, center = center)
            drawCircle(steel, radius = INNER_RADIUS * unit, center = center, style = Stroke(innerEdge))
            val radius = ARC_RADIUS * unit
            val stroke = ARC_STROKE * unit
            val arcSize = Size(radius * 2f, radius * 2f)
            val topLeft = Offset(center.x - radius, center.y - radius)
            drawArc(DIAL_TRACK, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
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
                    style = Stroke(stroke),
                )
            }
        }
        content()
    }
}

private const val DIAL_UNITS = 289f
private const val OUTER_RADIUS = 141f
private const val OUTER_STROKE = 5f
private const val INNER_RADIUS = 116f
private const val INNER_STROKE = 3f
private const val ARC_RADIUS = 128f
private const val ARC_STROKE = 16f
private val DIAL_BACK = Color(0xFF0A1219)
private val DIAL_FACE = Color(0xFF060C12)
private val DIAL_TRACK = Color(0xFF17242E)
private val STEEL_LIGHT = Color(0xFF9FB1BD)
private val STEEL_DARK = Color(0xFF46555F)
private val STEEL_SHINE = Color(0xFFB9C6CF)
private val STEEL_DEEP = Color(0xFF3A4750)

/**
 * Round progress: a bronze diamond per turn, filled up to the current turn, the current one larger.
 * Fill and size carry it, not colour; round start and end of round are named by the context line.
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
    val bronze = MaterialTheme.colorScheme.primary
    Row(
        modifier.clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(PIP_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (turn in 1..pips.turns) {
            val done = when (pips.section) {
                RoundSection.ROUND_START -> false
                RoundSection.ROUND_END -> true
                RoundSection.TURN -> turn <= (pips.currentTurn ?: 0)
            }
            val current = pips.section == RoundSection.TURN && turn == pips.currentTurn
            Canvas(Modifier.size(if (current) PIP_CURRENT else PIP)) {
                val r = this.size.minDimension / 2f
                val inset = PIP_EDGE.toPx() / 2f
                val diamond = Path().apply {
                    moveTo(r, inset)
                    lineTo(2 * r - inset, r)
                    lineTo(r, 2 * r - inset)
                    lineTo(inset, r)
                    close()
                }
                if (done) drawPath(diamond, bronze)
                drawPath(diamond, bronze, style = Stroke(PIP_EDGE.toPx()))
            }
        }
    }
}

private val PIP = 13.dp
private val PIP_CURRENT = 17.dp
private val PIP_GAP = 3.dp
private val PIP_EDGE = 1.5.dp

/** The Tie Breaker coin: the printed face of the [team] showing (rulebook p.5), its name said for TalkBack. */
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
        if (team != null) {
            OfficialIcon(OfficialArt.tieBreaker(team), Modifier.size(size * COIN_FACE)) {
                Text(
                    if (team == Team.RED) "◆" else "●",
                    color = color,
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                )
            }
        } else {
            Text(
                "?",
                color = color,
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
            )
        }
    }
}

private const val COIN_FACE = 0.58f

fun teamName(team: Team): String = if (team == Team.RED) "Red" else "Blue"
