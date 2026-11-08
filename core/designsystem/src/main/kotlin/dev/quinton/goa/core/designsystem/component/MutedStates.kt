package dev.quinton.goa.core.designsystem.component

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.theme.GoaTheme

/*
 * Muted states (DESIGN.md "Muted states", round 5 of the hill climb): what is done, past, empty or off but still worth
 * reading (a hero who has acted, a past draft step, an empty Turn slot, a card name under a done hero) steps back by
 * losing decoration, never by fading its words. No fill or a lighter one, a thinner edge in [GoaExtendedColors.outlineMuted]
 * (≥ 3:1), a check for "done", and words in [GoaExtendedColors.textMuted] (≥ 4.5:1 on every ground). Never `alpha` on
 * text, never a black scrim over words.
 */

/** The tick that says "done" beside a muted word (decorative: the word or the row's description carries it). */
@Composable
fun DoneMark(modifier: Modifier = Modifier, size: Dp = 14.dp, tint: Color = GoaTheme.colors.textMuted) {
    Icon(
        Icons.Filled.Check,
        contentDescription = null,
        tint = tint,
        modifier = modifier.size(size).clearAndSetSemantics {},
    )
}

/**
 * "✓ DONE" (or another past-state word) as a muted label: the tick and the word in [GoaExtendedColors.textMuted], no
 * band or fill behind them. [style] sets the size; the colour is the token's.
 */
@Composable
fun DoneLabel(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.labelMedium,
    tick: Boolean = true,
) {
    val color = GoaTheme.colors.textMuted
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (tick) {
            DoneMark(size = DoneTick, tint = color)
            Spacer(Modifier.width(2.dp))
        }
        Text(text, style = style.copy(fontWeight = FontWeight.Bold), color = color, maxLines = 1, softWrap = false)
    }
}

/** A muted thing's own edge: [width] (1 dp, thinner than a live one's) in [GoaExtendedColors.outlineMuted]. */
@Composable
fun Modifier.mutedOutline(shape: Shape, width: Dp = 1.dp): Modifier = border(width, GoaTheme.colors.outlineMuted, shape)

/**
 * An empty place (a Turn slot with no card, a free seat): no dark fill, a dashed [GoaExtendedColors.outlineMuted]
 * edge inset by [inset], so it reads as a place to fill and never as a card or a disabled box. Its number or word
 * goes in [GoaExtendedColors.textMuted].
 */
@Composable
fun Modifier.emptySlotOutline(shape: Shape, inset: Dp = 0.dp, width: Dp = 1.5.dp): Modifier {
    val color = GoaTheme.colors.outlineMuted
    return drawWithCache {
        val stroke = Stroke(
            width = width.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())),
        )
        val pad = inset.toPx() + stroke.width / 2
        val outline = shape.createOutline(
            Size(size.width - pad * 2, size.height - pad * 2),
            layoutDirection,
            this,
        )
        onDrawBehind { translate(pad, pad) { drawOutline(outline, color, style = stroke) } }
    }
}

/** The tick's size beside a 12–13 sp label. */
val DoneTick: Dp = 12.dp
