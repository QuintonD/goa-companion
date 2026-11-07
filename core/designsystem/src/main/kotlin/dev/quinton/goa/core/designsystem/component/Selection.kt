package dev.quinton.goa.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButtonColors
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SelectableChipColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.theme.GoaTheme

/*
 * One selected look for every choice (DESIGN.md "Selection"): a chip, a segment, a choice plate or a tile is
 * selected with a bronze edge, a bronze wash and a leading check (a radio on a plate of one-of-several). Unselected
 * chips are small steel plates with a quiet edge. Bronze is selected; teal is next and correct; team colours only
 * mark teams.
 */

/** The bronze edge of a selected chip, segment or plate. */
val SelectedEdge: Dp = 1.5.dp

/** A chip is a full finger target, visibly: never shallower than this, whatever its words. */
val ChipMinHeight: Dp = 48.dp

/** Room above and below a chip's words, so a chip grows with large text instead of squeezing it. */
val ChipLabelPadding: Dp = 8.dp

/** A chip's leading glyph at 100 % text; it grows with the text ([scaledIconSize]). */
val ChipIconSize: Dp = 18.dp

/** Every chip's shape. */
val ChipShape: Shape = RoundedCornerShape(10.dp)

/**
 * A chip's size (pass it as a Material chip's modifier when [GoaFilterChip] can't go): at least [ChipMinHeight] tall,
 * taller when its words need it (with [ChipLabel]).
 */
fun Modifier.chipSize(): Modifier = heightIn(min = ChipMinHeight)

/** A chip's words with [ChipLabelPadding] above and below: pass it as a Material chip's `label`. */
@Composable
fun ChipLabel(content: @Composable () -> Unit) {
    Row(Modifier.padding(vertical = ChipLabelPadding), verticalAlignment = Alignment.CenterVertically) { content() }
}

/** Filter-chip colours for every chip that is selected or not (Material's `FilterChip`). */
@Composable
fun selectableChipColors(): SelectableChipColors {
    val colors = GoaTheme.colors
    val ground = chipGround()
    return FilterChipDefaults.filterChipColors(
        containerColor = ground,
        labelColor = MaterialTheme.colorScheme.onSurface,
        iconColor = colors.selection,
        selectedContainerColor = colors.selectionFill.compositeOver(ground),
        selectedLabelColor = MaterialTheme.colorScheme.onSurface,
        selectedLeadingIconColor = colors.bronzeInk,
        selectedTrailingIconColor = colors.bronzeInk,
    )
}

/**
 * The chip's edge: a steel bevel (lit along the top, shaded along the foot, [ControlSteel]), or the bronze
 * [SelectedEdge] when [selected]. Pass it to every chip, so a chip reads as a small steel plate, not a Material pill.
 */
@Composable
fun selectableChipBorder(selected: Boolean): BorderStroke = if (selected) {
    BorderStroke(SelectedEdge, GoaTheme.colors.selection)
} else {
    BorderStroke(1.dp, ControlSteel.current.bevel())
}

/** The leading check of a selected chip; null (no icon) when not selected. Pass it as the chip's `leadingIcon`. */
fun selectedCheck(selected: Boolean): (@Composable () -> Unit)? = if (selected) {
    { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(scaledIconSize(ChipIconSize))) }
} else {
    null
}

/**
 * Segmented-button colours for Material's `SegmentedButton`, where a [GoaSegmentedRow] can't go (a team-coloured
 * switch): the same bronze wash and edge as a selected chip. Prefer [GoaSegmentedRow].
 */
@Composable
fun selectableSegmentColors(): SegmentedButtonColors {
    val colors = GoaTheme.colors
    val ground = chipGround()
    return SegmentedButtonDefaults.colors(
        activeContainerColor = colors.selectionFill.compositeOver(ground),
        activeContentColor = MaterialTheme.colorScheme.onSurface,
        activeBorderColor = colors.selection,
        inactiveContainerColor = ground,
        inactiveContentColor = MaterialTheme.colorScheme.onSurface,
        inactiveBorderColor = ChipEdge,
    )
}

/** A chip's ground: steel on the slate and the canvas, the paper in the light theme. Opaque, so it reads anywhere. */
@Composable
@ReadOnlyComposable
private fun chipGround(): Color = if (GoaTheme.isDark) ChipSteel else GoaTheme.colors.plate

private val ChipSteel = Color(0xFF2B363E)
private val ChipEdge = Color(0xFF6E7C82)
