package dev.quinton.goa.feature.catalog.ui

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
import androidx.compose.ui.semantics.heading
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
 * Section heading as the rulebook sets its heads: Cinzel small caps in copper after a short copper
 * bar, announced as a heading by TalkBack.
 */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    val copper = GoaTheme.colors.heading
    Row(
        modifier.padding(top = Spacing.xl, bottom = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(width = 3.dp, height = 18.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(copper),
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(
            text,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = copper,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        trailing?.invoke()
    }
}

/** Bronze-tinted selected state for filter chips (the Material default is off-palette). */
@Composable
fun goaFilterChipColors(): SelectableChipColors = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
    selectedLabelColor = MaterialTheme.colorScheme.onSurface,
    selectedLeadingIconColor = MaterialTheme.colorScheme.primary,
    selectedTrailingIconColor = MaterialTheme.colorScheme.primary,
    iconColor = MaterialTheme.colorScheme.primary,
)

@Composable
fun goaInputChipColors(): SelectableChipColors = InputChipDefaults.inputChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
    selectedLabelColor = MaterialTheme.colorScheme.onSurface,
    selectedTrailingIconColor = MaterialTheme.colorScheme.primary,
)
