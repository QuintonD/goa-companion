package dev.quinton.goa.feature.catalog.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.cardtext.GameIcon
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing

/** "Unverified" pill — data not yet cross-checked against a second source (DOMAIN.md). */
@Composable
fun UnverifiedBadge(modifier: Modifier = Modifier, compact: Boolean = false) {
    val color = MaterialTheme.colorScheme.error
    Row(
        modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
            .border(1.dp, color.copy(alpha = 0.6f), CircleShape)
            .padding(horizontal = if (compact) 6.dp else Spacing.sm, vertical = 2.dp)
            .semantics(mergeDescendants = true) { contentDescription = "Unverified data" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(
            "Unverified",
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
        )
    }
}

/** Small engraved pill used for product names and other metadata. */
@Composable
fun MetaPill(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, GoaTheme.colors.engravedBorder, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** Icon + number, e.g. a sword and "4"; reads as [spoken] ("Attack 4", "Attack 4 plus"). */
@Composable
fun ValueChip(
    icon: GameIcon,
    value: String,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    iconSize: Dp = 16.dp,
    spoken: String = "${icon.label} $value",
) {
    Row(
        modifier.semantics(mergeDescendants = true) { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(icon.vector, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
        Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}

/** Initiative medallion: the number on a bronze-ringed disc. */
@Composable
fun InitiativeBadge(initiative: Int?, modifier: Modifier = Modifier, accent: Color, size: Dp = 32.dp) {
    if (initiative == null) return
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(accent.copy(alpha = 0.16f))
            .border(1.5.dp, accent, CircleShape)
            .semantics { contentDescription = "Initiative $initiative" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initiative.toString(),
            style = if (size >= 40.dp) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

/**
 * The filter and sort chips: small steel plates, opaque so they read the same on the canvas of either
 * theme and on a sheet (use them under `GoaPlateTheme`); selected is bronze and carries its check or count.
 */
@Composable
fun goaFilterChipColors(): SelectableChipColors = FilterChipDefaults.filterChipColors(
    containerColor = CHIP_STEEL,
    labelColor = MaterialTheme.colorScheme.onSurface,
    iconColor = MaterialTheme.colorScheme.primary,
    selectedContainerColor = CHIP_BRONZE,
    selectedLabelColor = MaterialTheme.colorScheme.onSurface,
    selectedLeadingIconColor = MaterialTheme.colorScheme.primary,
    selectedTrailingIconColor = MaterialTheme.colorScheme.primary,
)

@Composable
fun goaChipBorder(selected: Boolean): BorderStroke = FilterChipDefaults.filterChipBorder(
    enabled = true,
    selected = selected,
    borderColor = CHIP_EDGE,
    selectedBorderColor = MaterialTheme.colorScheme.primary,
)

@Composable
fun goaInputChipColors(): SelectableChipColors = InputChipDefaults.inputChipColors(
    containerColor = CHIP_STEEL,
    selectedContainerColor = CHIP_BRONZE,
    selectedLabelColor = MaterialTheme.colorScheme.onSurface,
    selectedTrailingIconColor = MaterialTheme.colorScheme.primary,
)

@Composable
fun goaInputChipBorder(): BorderStroke = InputChipDefaults.inputChipBorder(
    enabled = true,
    selected = true,
    selectedBorderColor = MaterialTheme.colorScheme.primary,
)

private val CHIP_STEEL = Color(0xFF2B363E)
private val CHIP_BRONZE = Color(0xFF4A3D22)
private val CHIP_EDGE = Color(0xFF5D6B70)
