package dev.quinton.goa.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
        selectedLeadingIconColor = colors.selection,
        selectedTrailingIconColor = colors.selection,
    )
}

/** The chip's edge: quiet steel, or the bronze [SelectedEdge] when [selected]. */
@Composable
fun selectableChipBorder(selected: Boolean): BorderStroke = FilterChipDefaults.filterChipBorder(
    enabled = true,
    selected = selected,
    borderColor = ChipEdge,
    selectedBorderColor = GoaTheme.colors.selection,
    borderWidth = 1.dp,
    selectedBorderWidth = SelectedEdge,
)

/** The leading check of a selected chip; null (no icon) when not selected. Pass it as the chip's `leadingIcon`. */
fun selectedCheck(selected: Boolean): (@Composable () -> Unit)? = if (selected) {
    { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
} else {
    null
}

/** Segmented-button colours: the same bronze wash and edge as a selected chip (the segment shows its own check). */
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
private val ChipEdge = Color(0xFF5D6B70)
