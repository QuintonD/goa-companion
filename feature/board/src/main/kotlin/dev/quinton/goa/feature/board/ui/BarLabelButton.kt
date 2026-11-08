package dev.quinton.goa.feature.board.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.quinton.goa.core.designsystem.theme.GoaFonts

/**
 * A game screen's top-bar control (the game, a puzzle): its glyph over its name in small words ("Undo", "State",
 * "Log", "More"), so no icon has to be learned and each destination has one glyph in both orientations. [description]
 * is what TalkBack hears and the long-press tooltip says ("Game log"). Disabled (nothing to take back), glyph and
 * word at 40 % on the steel banner. At least 48 dp wide and tall; the word grows with large text no more than 1.15×,
 * so it keeps to the bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BarLabelButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String = label,
    enabled: Boolean = true,
) {
    val density = LocalDensity.current
    val cap = minOf(density.fontScale, LABEL_MAX_SCALE) / density.fontScale
    val color = LocalContentColor.current.let { if (enabled) it else it.copy(alpha = DISABLED_ALPHA) }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
        tooltip = { PlainTooltip { Text(description) } },
        state = rememberTooltipState(),
        modifier = modifier,
    ) {
        Column(
            Modifier
                .heightIn(min = 48.dp)
                .widthIn(min = 48.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .clearAndSetSemantics {
                    contentDescription = description
                    role = Role.Button
                    if (!enabled) disabled()
                    onClick {
                        onClick()
                        true
                    }
                }
                .padding(horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Text(
                label,
                color = color,
                fontFamily = GoaFonts.body,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp * cap,
                lineHeight = 14.sp * cap,
                letterSpacing = 0.2.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

private const val LABEL_MAX_SCALE = 1.15f
private const val DISABLED_ALPHA = 0.5f
