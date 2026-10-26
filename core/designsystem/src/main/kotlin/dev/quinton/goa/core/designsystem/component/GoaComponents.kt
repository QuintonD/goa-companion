package dev.quinton.goa.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.model.catalog.StatRange
import java.util.Locale
import kotlin.math.abs

/** Hero stat bars use the hero card's 1..8 scale. */
const val STAT_SCALE = 8

/**
 * A hero stat (Attack/Defense/Initiative/Movement) as segments on a 1..[scale] track: solid up
 * to [base], hollow bronze up to [max] (reachable with upgrades), empty beyond. Trailing
 * tabular "base → max". Null values render an empty track and "—".
 */
@Composable
fun StatBar(
    label: String,
    base: Int?,
    max: Int?,
    modifier: Modifier = Modifier,
    scale: Int = STAT_SCALE,
    labelWidth: Dp = 92.dp,
) {
    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)
    val description = when {
        base == null -> "$label unknown"
        max == null || max == base -> "$label $base of $scale"
        else -> "$label $base, up to $max, of $scale"
    }
    val value = when {
        base == null -> "—"
        max == null || max == base -> "$base"
        else -> "$base → $max"
    }
    val labelText: @Composable (Modifier) -> Unit = { m ->
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = m,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    val valueText: @Composable (Modifier) -> Unit = { m ->
        Text(
            value,
            style = GoaTextStyles.numeric,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = m,
            maxLines = 1,
        )
    }
    val bar: @Composable (Modifier) -> Unit = { m ->
        Row(m, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            for (i in 1..scale) {
                val shape = RoundedCornerShape(3.dp)
                val segment = Modifier.weight(1f).height(10.dp).clip(shape)
                when {
                    base != null && i <= base -> Box(
                        segment.background(Brush.verticalGradient(listOf(primary, primary.copy(alpha = 0.78f)))),
                    )

                    base != null && max != null && i <= max -> Box(
                        segment
                            .background(primary.copy(alpha = 0.16f))
                            .border(1.dp, primary.copy(alpha = 0.7f), shape),
                    )

                    else -> Box(segment.background(track))
                }
            }
        }
    }
    if (LocalDensity.current.fontScale > LARGE_FONT_SCALE) {
        // Large text: label and value get their own line so the bars keep the full width.
        Column(modifier.clearAndSetSemantics { contentDescription = description }.padding(vertical = 2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                labelText(Modifier.weight(1f))
                valueText(Modifier.padding(start = Spacing.sm))
            }
            bar(Modifier.fillMaxWidth().padding(vertical = Spacing.xs))
        }
    } else {
        Row(
            modifier.clearAndSetSemantics { contentDescription = description },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The label column grows with the text so "Movement" fits up to the stacked layout.
            val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
            labelText(Modifier.width(labelWidth * fontScale).padding(end = Spacing.sm))
            bar(Modifier.weight(1f))
            valueText(Modifier.widthIn(min = 48.dp).padding(start = Spacing.sm))
        }
    }
}

/** Above this font scale, compact rows stack their label over the graphic. */
const val LARGE_FONT_SCALE = 1.3f

/** Icon size that follows the user's font scale (capped) so icons stay in proportion to text. */
@Composable
fun scaledIconSize(base: Dp, maxScale: Float = 1.8f): Dp = base * LocalDensity.current.fontScale.coerceIn(1f, maxScale)

/** [StatBar] for a catalog [StatRange]. */
@Composable
fun StatBar(label: String, range: StatRange?, modifier: Modifier = Modifier, scale: Int = STAT_SCALE) =
    StatBar(label = label, base = range?.base, max = range?.max, modifier = modifier, scale = scale)

/**
 * Section head as the rulebook sets them: Cinzel small caps in copper ("Place the Board", p.5),
 * then a fading copper rule. Marked as a heading for TalkBack. [trailing] holds an optional
 * action (e.g. "See all").
 */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    val copper = GoaTheme.colors.heading
    Row(
        modifier
            .fillMaxWidth()
            .padding(top = Spacing.lg, bottom = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = GoaTextStyles.sectionHead,
            color = copper,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.width(Spacing.md))
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(Brush.horizontalGradient(listOf(copper.copy(alpha = 0.5f), Color.Transparent))),
        )
        if (trailing != null) {
            Spacer(Modifier.width(Spacing.sm))
            trailing()
        }
    }
}

/**
 * The app's card: 12 dp corners, surface fill with a faint bronze sheen at the top and the
 * engraved 1 dp bronze-at-30 % border. Clickable when [onClick] is set.
 */
@Composable
fun GoaCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(Spacing.lg),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.medium
    val border = BorderStroke(1.dp, GoaTheme.colors.engravedBorder)
    val inner: @Composable () -> Unit = {
        Column(
            Modifier
                .background(
                    Brush.verticalGradient(listOf(GoaTheme.colors.bronzeGlow.copy(alpha = 0.10f), Color.Transparent)),
                )
                .padding(contentPadding),
            content = content,
        )
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            color = MaterialTheme.colorScheme.surface,
            border = border,
            content = inner,
        )
    } else {
        Surface(
            modifier = modifier,
            shape = shape,
            color = MaterialTheme.colorScheme.surface,
            border = border,
            content = inner,
        )
    }
}

/** Horizontally scrolling multi-select chips (filters). Selected chips show a check. */
@Composable
fun <T> FilterChipRow(
    options: List<T>,
    selected: Set<T>,
    onToggle: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = Spacing.screen),
) {
    LazyRow(
        modifier = modifier,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        items(options, key = { it.toString() }) { option ->
            GoaFilterChip(selected = option in selected, onClick = { onToggle(option) }, label = label(option))
        }
    }
}

/** One themed filter chip, for custom layouts (FlowRow) outside [FilterChipRow]. */
@Composable
fun GoaFilterChip(selected: Boolean, onClick: () -> Unit, label: String, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1) },
        modifier = modifier,
        leadingIcon = if (selected) {
            {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                )
            }
        } else {
            null
        },
        shape = RoundedCornerShape(10.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surface,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = primary.copy(alpha = 0.18f),
            selectedLabelColor = MaterialTheme.colorScheme.onSurface,
            selectedLeadingIconColor = primary,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = GoaTheme.colors.engravedBorder,
            selectedBorderColor = primary.copy(alpha = 0.7f),
            borderWidth = 1.dp,
            selectedBorderWidth = 1.dp,
        ),
    )
}

@Composable
private fun StatusBadge(icon: ImageVector, text: String, color: Color, description: String, modifier: Modifier) {
    Row(
        modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.5f), CircleShape)
            .padding(horizontal = Spacing.sm, vertical = 2.dp)
            .clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(scaledIconSize(14.dp)))
        Spacer(Modifier.width(Spacing.xs))
        Text(text, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.SemiBold)
    }
}

/** Data checked against two official sources. */
@Composable
fun VerifiedBadge(modifier: Modifier = Modifier) = StatusBadge(
    icon = Icons.Outlined.Verified,
    text = "Verified",
    color = GoaTheme.colors.verified,
    description = "Verified data",
    modifier = modifier,
)

/** Data not yet confirmed by two sources (`verified: false`); always visible (AGENTS.md). */
@Composable
fun UnverifiedBadge(modifier: Modifier = Modifier) = StatusBadge(
    icon = Icons.AutoMirrored.Outlined.HelpOutline,
    text = "Unverified",
    color = GoaTheme.colors.unverified,
    description = "Unverified data, may contain errors",
    modifier = modifier,
)

/** A card colour's name (shared with the web app: core:model). */
fun cardColorName(color: CardColor): String = dev.quinton.goa.core.model.catalog.cardColorName(color)

/** A card's colour as a small dot with a hairline ring (so pale colours stay visible). */
@Composable
fun CardColorDot(color: CardColor, modifier: Modifier = Modifier, size: Dp = 12.dp) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(cardColor(color))
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f), CircleShape)
            .semantics { contentDescription = "${cardColorName(color)} card" },
    )
}

/**
 * Two-button confirmation. [destructive] paints the confirm action in the error colour. The
 * dismiss button runs [onDismissButton] when given (a second action, e.g. "Leave"), otherwise
 * [onDismiss]; tapping outside or Back always runs [onDismiss].
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismissLabel: String = "Cancel",
    destructive: Boolean = false,
    icon: ImageVector? = null,
    onDismissButton: (() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        icon = icon?.let { { Icon(it, contentDescription = null) } },
        title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    confirmLabel,
                    color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismissButton ?: onDismiss) { Text(dismissLabel) } },
        containerColor = MaterialTheme.colorScheme.surface,
        iconContentColor = MaterialTheme.colorScheme.primary,
        shape = MaterialTheme.shapes.extraLarge,
    )
}

/**
 * Formats a duration as `m:ss` (or `h:mm:ss` from one hour). Negative values (overtime) get a
 * leading "+" when [overtimeSign] is set, else a minus sign. [roundUp] suits countdowns: 0.4 s
 * left shows 0:01 and 0:00 appears only at zero.
 */
fun formatDuration(millis: Long, roundUp: Boolean = false, overtimeSign: Boolean = false): String {
    val totalSeconds = if (roundUp) (abs(millis) + 999) / 1000 else abs(millis) / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    val body = if (h > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.ROOT, "%d:%02d", m, s)
    }
    return when {
        millis >= 0 || totalSeconds == 0L -> body
        overtimeSign -> "+$body"
        else -> "−$body"
    }
}

/** Spoken form for TalkBack, e.g. "1 minute 30 seconds". */
fun spokenDuration(millis: Long): String {
    val total = abs(millis) / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    val parts = buildList {
        if (h > 0) add(plural(h, "hour"))
        if (m > 0) add(plural(m, "minute"))
        if (s > 0 || isEmpty()) add(plural(s, "second"))
    }
    return parts.joinToString(" ")
}

private fun plural(n: Long, unit: String) = if (n == 1L) "1 $unit" else "$n ${unit}s"

/** A duration in tabular figures (see [formatDuration]) with a spoken content description. */
@Composable
fun DurationText(
    millis: Long,
    modifier: Modifier = Modifier,
    style: TextStyle = GoaTextStyles.numeric,
    color: Color = Color.Unspecified,
    roundUp: Boolean = false,
    overtimeSign: Boolean = false,
) {
    val spoken = spokenDuration(millis) + if (millis < 0 && overtimeSign) " over" else ""
    Text(
        text = formatDuration(millis, roundUp, overtimeSign),
        style = style,
        color = color,
        maxLines = 1,
        modifier = modifier.clearAndSetSemantics { contentDescription = spoken },
    )
}
