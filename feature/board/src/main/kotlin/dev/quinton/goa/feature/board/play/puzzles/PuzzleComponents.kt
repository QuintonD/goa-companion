package dev.quinton.goa.feature.board.play.puzzles

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.scaledIconSize
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Motion
import dev.quinton.goa.core.designsystem.theme.Spacing
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Difficulty as three small hexes, filled up to [level], and the word (Hard / Harder / Hardest):
 * shape and words, never colour alone.
 */
@Composable
fun DifficultyMark(level: Int, modifier: Modifier = Modifier, showLabel: Boolean = true) {
    val color = MaterialTheme.colorScheme.primary
    val label = PuzzleText.difficulty(level)
    Row(
        modifier.clearAndSetSemantics { contentDescription = "Difficulty: $label" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val pip = scaledIconSize(9.dp, 1.4f)
        repeat(MAX_DIFFICULTY) { i ->
            Canvas(Modifier.size(pip)) {
                val path = hexagon(Offset(size.width / 2f, size.height / 2f), size.minDimension / 2f)
                if (i < level) drawPath(path, color) else drawPath(path, color, style = Stroke(width = 1.2.dp.toPx()))
            }
            Spacer(Modifier.width(2.dp))
        }
        if (showLabel) {
            Spacer(Modifier.width(Spacing.xs))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The goal, floating under the hero strip: what to achieve and who the player controls. */
@Composable
fun GoalBanner(goal: String, playing: List<String>, modifier: Modifier = Modifier) {
    val you = if (playing.isEmpty()) "" else "You play ${PuzzleText.names(playing)}"
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)),
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = "Goal: $goal. $you" },
    ) {
        Row(
            Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Flag,
                null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(scaledIconSize(20.dp, 1.4f)),
            )
            Spacer(Modifier.width(Spacing.sm))
            Column(Modifier.weight(1f)) {
                Text(goal, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (you.isNotEmpty()) {
                    Text(
                        you,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * A gentle nudge (not a dialog): "Nudge 2 of 3", the text, the option it points at once it is the
 * last one, "Another nudge" while there are more, and a close button.
 */
@Composable
fun NudgeCard(nudge: NudgeUi, onMore: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(start = Spacing.md, end = Spacing.xs, top = Spacing.xs, bottom = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Lightbulb, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                Spacer(Modifier.width(Spacing.sm))
                Text(
                    "NUDGE ${nudge.level} OF ${nudge.total}",
                    style = GoaTextStyles.overline,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "Hide the nudge",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            Text(
                nudge.text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(end = Spacing.sm).semantics { liveRegion = LiveRegionMode.Polite },
            )
            nudge.hint?.let { hint ->
                Text(
                    "Outlined in bronze: $hint",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(end = Spacing.sm),
                )
            }
            if (nudge.more) {
                OutlinedButton(
                    onClick = onMore,
                    modifier = Modifier.heightIn(min = 48.dp),
                    contentPadding = PaddingValues(horizontal = Spacing.lg),
                ) { Text("Another nudge") }
            }
        }
    }
}

/**
 * The seal on a solved puzzle: a bronze hex with a tide-teal tick and six points of light, which
 * opens out once (Motion: emphasised deceleration). Original, drawn in code.
 */
@Composable
fun SolvedSeal(modifier: Modifier = Modifier, size: Dp = 64.dp) {
    val bronze = MaterialTheme.colorScheme.primary
    val glow = GoaTheme.colors.bronzeGlow
    val tick = MaterialTheme.colorScheme.secondary
    val grow = remember { Animatable(SEAL_START) }
    LaunchedEffect(Unit) { grow.animateTo(1f, tween(Motion.LONG_MS * 2, easing = Motion.EmphasizedDecelerate)) }
    Canvas(modifier.size(size)) {
        val c = Offset(this.size.width / 2f, this.size.height / 2f)
        val r = this.size.minDimension / 2f
        scale(grow.value, c) {
            for (i in 0 until 6) {
                val a = (PI / 3 * i - PI / 2).toFloat()
                val dir = Offset(cos(a), sin(a))
                drawLine(
                    bronze,
                    c + dir * (r * 0.8f),
                    c + dir * (r * 0.98f),
                    strokeWidth = r * 0.07f,
                    cap = StrokeCap.Round,
                )
            }
            val hex = hexagon(c, r * 0.68f)
            drawPath(hex, glow)
            drawPath(hex, bronze, style = Stroke(width = r * 0.08f, join = StrokeJoin.Round))
            val check = Path().apply {
                moveTo(c.x - r * 0.3f, c.y + r * 0.02f)
                lineTo(c.x - r * 0.08f, c.y + r * 0.24f)
                lineTo(c.x + r * 0.32f, c.y - r * 0.22f)
            }
            drawPath(check, tick, style = Stroke(width = r * 0.13f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** A pointy-top hexagon of corner radius [radius] around [center]. */
private fun hexagon(center: Offset, radius: Float): Path = Path().apply {
    for (i in 0 until 6) {
        val a = (PI / 3 * i - PI / 2).toFloat()
        val p = center + Offset(cos(a), sin(a)) * radius
        if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
    }
    close()
}

private const val MAX_DIFFICULTY = 3
private const val SEAL_START = 0.6f
