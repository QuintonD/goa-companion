package dev.quinton.goa.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp

/**
 * One action of a top bar. [label] is what it does in words: its TalkBack label, its tooltip (long press) and
 * its line in the overflow menu. [badge]: a quiet dot that asks for attention (said by [label]).
 */
data class BarAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val badge: Boolean = false,
)

/** How many glyphs a top bar shows at most, the overflow button included (DESIGN.md "Top bar"). */
const val MAX_BAR_ICONS: Int = 2

/**
 * A top bar's actions: as icons while there are at most [maxIcons], else the first as an icon and the rest
 * in a labelled overflow menu ("More options"), so a bar never shows a row of unlabelled glyphs. [maxIcons] 1 puts
 * every action in the menu, named: for glyphs that would not say where they lead (a history clock, a list).
 */
@Composable
fun RowScope.BarActions(actions: List<BarAction>, maxIcons: Int = MAX_BAR_ICONS) {
    if (actions.size <= maxIcons && maxIcons > 1) {
        actions.forEach { BarIconButton(it) }
        return
    }
    val shown = actions.take((maxIcons - 1).coerceAtLeast(0))
    shown.forEach { BarIconButton(it) }
    BarOverflowMenu(actions.drop(shown.size))
}

/** A top-bar icon with its label as a tooltip on long press and for TalkBack. */
@Composable
fun BarIconButton(action: BarAction, modifier: Modifier = Modifier) {
    BarIconButton(
        label = action.label,
        onClick = action.onClick,
        modifier = modifier,
        enabled = action.enabled,
    ) {
        if (action.badge) {
            BadgedBox(badge = { Badge() }) { Icon(action.icon, contentDescription = action.label) }
        } else {
            Icon(action.icon, contentDescription = action.label)
        }
    }
}

/**
 * A top-bar icon button whose [label] shows as a tooltip on long press. [icon] carries the content description
 * (normally [label]). Disabled (undo with nothing to undo), the glyph stays in sight on the steel banner: white at
 * 50 % over a 1 dp dark halo, not Material's grey that the banner swallows (3.6:1 to 4.7:1 on the steel).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarIconButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: @Composable () -> Unit,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
        modifier = modifier,
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            colors = IconButtonDefaults.iconButtonColors(disabledContentColor = BannerDisabledIcon),
        ) {
            if (enabled) {
                icon()
            } else {
                Box {
                    // The halo: the same glyph in a dark tone, a dp lower, behind it.
                    Box(Modifier.offset(y = 1.dp).clearAndSetSemantics {}) {
                        CompositionLocalProvider(LocalContentColor provides BannerDisabledHalo, content = icon)
                    }
                    icon()
                }
            }
        }
    }
}

/** A disabled glyph on the steel banner, and the dark halo under it. */
private val BannerDisabledIcon = Color.White.copy(alpha = 0.5f)
private val BannerDisabledHalo = Color.Black.copy(alpha = 0.7f)

/** The overflow button ("More options") and its menu: each action as an icon and its label. */
@Composable
fun BarOverflowMenu(actions: List<BarAction>, modifier: Modifier = Modifier) {
    if (actions.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    val attention = actions.any { it.badge }
    Box(modifier) {
        BarIconButton(label = "More options", onClick = { open = true }) {
            val description = if (attention) "More options, one needs attention" else "More options"
            if (attention) {
                BadgedBox(badge = { Badge() }) { Icon(Icons.Outlined.MoreVert, contentDescription = description) }
            } else {
                Icon(Icons.Outlined.MoreVert, contentDescription = description)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            actions.forEach { action ->
                DropdownMenuItem(
                    text = { Text(action.label) },
                    leadingIcon = {
                        if (action.badge) {
                            BadgedBox(badge = { Badge() }) { Icon(action.icon, contentDescription = null) }
                        } else {
                            Icon(action.icon, contentDescription = null)
                        }
                    },
                    enabled = action.enabled,
                    onClick = {
                        open = false
                        action.onClick()
                    },
                )
            }
        }
    }
}
