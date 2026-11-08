package dev.quinton.goa.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * A small word tag inside a plate, a sheet or a button ("p.14", "Tonight", "Rulebook"): the word in [color] on a
 * faint tint of it and no edge of its own, since what it sits in is the frame (DESIGN.md "Framing levels": no
 * outlined chip inside a bordered card inside a plate). [color] must be text-strong on the ground under the tint
 * (the theme's bronze or teal, `bronzeInk`, a dark ink on a bronze button).
 */
@Composable
fun TintTag(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    style: TextStyle = MaterialTheme.typography.labelMedium,
) {
    Text(
        text,
        style = style,
        fontWeight = FontWeight.SemiBold,
        color = color,
        maxLines = 1,
        softWrap = false,
        modifier = modifier
            .background(color.copy(alpha = TINT_TAG_ALPHA), CircleShape)
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

private const val TINT_TAG_ALPHA = 0.16f
