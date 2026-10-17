package dev.quinton.goa.feature.players.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.teamColor
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.core.model.match.Team
import kotlin.math.roundToInt

/** Small outlined pill: "Provisional", "Double pilot", "Handicap"… Text, never colour alone. */
@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    Row(
        modifier
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(50))
            .padding(horizontal = Spacing.sm, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
    }
}

@Composable
fun ProvisionalPill(modifier: Modifier = Modifier) {
    Pill(
        text = "Provisional",
        icon = Icons.Outlined.HourglassEmpty,
        color = MaterialTheme.colorScheme.secondary,
        modifier = modifier,
    )
}

/** Rating trend with an arrow shape and a signed number, e.g. "↗ +24". */
@Composable
fun DeltaIndicator(delta: Double?, modifier: Modifier = Modifier, label: String = "trend") {
    if (delta == null) return
    val rounded = delta.roundToInt()
    val (icon, color) = when {
        rounded > 0 -> Icons.AutoMirrored.Filled.TrendingUp to MaterialTheme.colorScheme.secondary
        rounded < 0 -> Icons.AutoMirrored.Filled.TrendingDown to MaterialTheme.colorScheme.error
        else -> Icons.AutoMirrored.Filled.TrendingFlat to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier.clearAndSetSemantics { contentDescription = "$label ${formatDelta(delta)}" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Text(formatDelta(delta), style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/** Match result: "◆ Red won", "● Blue won", "Pending", "Abandoned". */
@Composable
fun ResultChip(status: MatchStatus, winner: Team?, modifier: Modifier = Modifier) {
    val (text, color, icon) = when {
        status == MatchStatus.COMPLETED && winner != null -> Triple(
            "${if (winner == Team.RED) "◆" else "●"} ${winner.label()} won",
            teamColor(winner),
            Icons.Filled.EmojiEvents,
        )

        status == MatchStatus.ABANDONED -> Triple(
            "Abandoned",
            MaterialTheme.colorScheme.onSurfaceVariant,
            Icons.Outlined.Block,
        )

        status == MatchStatus.COMPLETED -> Triple("No winner", MaterialTheme.colorScheme.error, Icons.Outlined.Block)

        else -> Triple("Pending", MaterialTheme.colorScheme.primary, Icons.Outlined.HourglassEmpty)
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.14f),
        contentColor = color,
    ) {
        Row(
            Modifier.padding(horizontal = Spacing.sm + 2.dp, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
            Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Big number over a caption, for stat rows. */
@Composable
fun StatTile(value: String, caption: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), MaterialTheme.shapes.medium)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
            .semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            caption,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    val heading: @Composable (Modifier) -> Unit = { m ->
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = m.semantics { heading() },
        )
    }
    if (trailing != null && androidx.compose.ui.platform.LocalDensity.current.fontScale >= LARGE_FONT_SCALE) {
        // Large text: stack, so the heading never breaks mid-word.
        Column(modifier.fillMaxWidth().padding(top = Spacing.lg, bottom = Spacing.sm)) {
            heading(Modifier)
            trailing()
        }
    } else {
        Row(
            modifier.fillMaxWidth().padding(top = Spacing.lg, bottom = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            heading(Modifier.weight(1f))
            trailing?.invoke()
        }
    }
}

/** Card frame with the engraved bronze border (docs/DESIGN.md). */
@Composable
fun EngravedCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, GoaTheme.colors.engravedBorder),
        content = content,
    )
}

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().semantics { contentDescription = "Loading" }, contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.secondary)
    }
}

/** Motion tokens: 200–300 ms emphasised easing (docs/DESIGN.md). */
object Motion {
    const val DURATION_MS = 250

    fun <T> spec(): androidx.compose.animation.core.FiniteAnimationSpec<T> =
        androidx.compose.animation.core.tween(DURATION_MS, easing = androidx.compose.animation.core.FastOutSlowInEasing)
}

/** Equal-height row of [StatTile]s; wraps two per row at large font scales. */
@Composable
fun StatRow(tiles: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    val large = androidx.compose.ui.platform.LocalDensity.current.fontScale >= LARGE_FONT_SCALE
    if (large) {
        androidx.compose.foundation.layout.FlowRow(
            modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            maxItemsInEachRow = 2,
        ) {
            tiles.forEach { (value, caption) -> StatTile(value, caption, Modifier.weight(1f)) }
        }
    } else {
        Row(
            modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            tiles.forEach { (value, caption) -> StatTile(value, caption, Modifier.weight(1f).fillMaxHeight()) }
        }
    }
}

/** Font scale from which compact controls switch to roomier, wrapping layouts. */
const val LARGE_FONT_SCALE = 1.3f

/** Brand colours for selected chips: tide-teal tint, never the default Material purple. */
@Composable
fun goaFilterChipColors() = androidx.compose.material3.FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
    selectedLabelColor = MaterialTheme.colorScheme.onSurface,
    selectedLeadingIconColor = MaterialTheme.colorScheme.secondary,
)

@Composable
fun goaSegmentedColors() = androidx.compose.material3.SegmentedButtonDefaults.colors(
    activeContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
    activeContentColor = MaterialTheme.colorScheme.onSurface,
    activeBorderColor = MaterialTheme.colorScheme.primary,
    inactiveContainerColor = Color.Transparent,
    inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    inactiveBorderColor = MaterialTheme.colorScheme.outline,
)
