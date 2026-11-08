package dev.quinton.goa.feature.players.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.quinton.goa.core.designsystem.component.GoaPlate
import dev.quinton.goa.core.designsystem.component.GoaPlateTheme
import dev.quinton.goa.core.designsystem.component.PlateShape
import dev.quinton.goa.core.designsystem.component.SectionHeader as RulebookSectionHeader
import dev.quinton.goa.core.designsystem.component.TeamMark
import dev.quinton.goa.core.designsystem.component.scaledIconSize
import dev.quinton.goa.core.designsystem.component.slatePlate
import dev.quinton.goa.core.designsystem.component.teamTextColor
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
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

/**
 * A provisional rating where some are settled: a small hourglass beside the name, not a pill on every row (when
 * every rating is provisional the list says it once). Its own words for TalkBack.
 */
@Composable
fun ProvisionalMark(modifier: Modifier = Modifier, label: Boolean = false) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    if (!label) {
        Icon(
            Icons.Outlined.HourglassEmpty,
            contentDescription = "Provisional rating",
            tint = muted,
            modifier = modifier.size(scaledIconSize(16.dp)),
        )
        return
    }
    // With its word, where no caption explains the hourglass (a match's result).
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Outlined.HourglassEmpty,
            contentDescription = null,
            tint = muted,
            modifier = Modifier.size(scaledIconSize(14.dp)),
        )
        Spacer(Modifier.width(2.dp))
        Text("Provisional", style = MaterialTheme.typography.labelSmall, color = muted, maxLines = 1)
    }
}

/** "All ratings are provisional until 10 games each." */
fun allProvisionalCaption(games: Int): String = "All ratings are provisional until $games games each."

@Composable
fun ProvisionalPill(modifier: Modifier = Modifier) {
    Pill(
        text = "Provisional",
        icon = Icons.Outlined.HourglassEmpty,
        modifier = modifier,
    )
}

/**
 * Rating change with an arrow shape and a signed number, e.g. "↗ +24", optionally followed by a
 * [caption] that says what it covers ("last 5").
 */
@Composable
fun DeltaIndicator(
    delta: Double?,
    modifier: Modifier = Modifier,
    label: String = "rating change",
    caption: String? = null,
) {
    if (delta == null) return
    val rounded = delta.roundToInt()
    val (icon, color) = when {
        rounded > 0 -> Icons.AutoMirrored.Filled.TrendingUp to MaterialTheme.colorScheme.secondary
        rounded < 0 -> Icons.AutoMirrored.Filled.TrendingDown to MaterialTheme.colorScheme.error
        else -> Icons.AutoMirrored.Filled.TrendingFlat to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier.clearAndSetSemantics {
            contentDescription = "$label ${formatDelta(delta)}" + (caption?.let { ", $it" } ?: "")
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Text(formatDelta(delta), style = MaterialTheme.typography.labelMedium, color = color)
        if (caption != null) {
            // Never cut ("last 5" must not become "last"): its words stay together and, if need be, wrap whole.
            Text(
                " ${caption.replace(' ', '\u00A0')}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Match result: the winner's mark and "Red won", "Pending", "Abandoned". */
@Composable
fun ResultChip(status: MatchStatus, winner: Team?, modifier: Modifier = Modifier) {
    val (text, color, icon) = when {
        status == MatchStatus.COMPLETED && winner != null -> Triple(
            "${winner.label()} won",
            teamTextColor(winner),
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
            if (status == MatchStatus.COMPLETED && winner != null) TeamMark(winner, markSize = 12.dp)
            Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Big number over a caption, for stat rows. */
@Composable
fun StatTile(value: String, caption: String, modifier: Modifier = Modifier) {
    GoaPlateTheme { StatTileContent(value, caption, modifier.slatePlate(PlateShape, brackets = false)) }
}

@Composable
private fun StatTileContent(value: String, caption: String, modifier: Modifier) {
    Column(
        modifier
            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
            .semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Shrinks to fit rather than truncating ("69–38", not "69–…").
        BasicText(
            value,
            style = figureStyle(RowFigure).copy(color = MaterialTheme.colorScheme.onSurface),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(
                minFontSize = 12.sp,
                maxFontSize = RowFigure,
            ),
        )
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
    if (trailing != null && androidx.compose.ui.platform.LocalDensity.current.fontScale >= LARGE_FONT_SCALE) {
        // Large text: stack, so the heading never breaks mid-word.
        Column(modifier.fillMaxWidth().padding(bottom = Spacing.sm)) {
            RulebookSectionHeader(title)
            trailing()
        }
    } else {
        RulebookSectionHeader(title, modifier, trailing = trailing)
    }
}

/**
 * A dark plate with a coloured edge and fill for a state (a team, a problem, a choice): both are read
 * in the plate's dark theme, as anything on a plate must be.
 */
@Composable
fun EdgedPlate(
    edge: @Composable () -> BorderStroke,
    modifier: Modifier = Modifier,
    fill: @Composable () -> Color = { Color.Transparent },
    brackets: Boolean = false,
    content: @Composable () -> Unit,
) {
    GoaPlateTheme {
        Box(
            modifier
                .slatePlate(PlateShape, brackets = brackets)
                .background(fill(), PlateShape)
                .border(edge(), PlateShape)
                .clip(PlateShape),
        ) { content() }
    }
}

/** A dark plate with no padding of its own (docs/DESIGN.md). */
@Composable
fun EngravedCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    GoaPlate(modifier, contentPadding = PaddingValues(0.dp)) { content() }
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

/**
 * Big numbers over captions on one plate, divided by hairlines, rather than a framed tile each (DESIGN.md "Framing
 * levels": one frame per section); two per line at large font scales.
 */
@Composable
fun StatRow(tiles: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    val large = androidx.compose.ui.platform.LocalDensity.current.fontScale >= LARGE_FONT_SCALE
    GoaPlateTheme {
        val plate = modifier.fillMaxWidth().slatePlate(PlateShape, brackets = false)
        if (large) {
            androidx.compose.foundation.layout.FlowRow(
                plate.padding(vertical = Spacing.xs),
                maxItemsInEachRow = 2,
            ) {
                tiles.forEach { (value, caption) -> StatTileContent(value, caption, Modifier.weight(1f)) }
            }
        } else {
            Row(plate.height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
                tiles.forEachIndexed { i, (value, caption) ->
                    if (i > 0) {
                        androidx.compose.material3.VerticalDivider(
                            Modifier.padding(vertical = Spacing.sm),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = STAT_DIVIDER_ALPHA),
                        )
                    }
                    StatTileContent(value, caption, Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
}

private const val STAT_DIVIDER_ALPHA = 0.12f

/** Font scale from which compact controls switch to roomier, wrapping layouts. */
const val LARGE_FONT_SCALE = 1.3f

/**
 * The one style for figures across the Players screens (ratings, records, rates, scores): Inter with
 * tabular lining figures, semibold, so the same rating looks the same on every screen. Cinzel stays
 * for headings.
 */
fun figureStyle(size: androidx.compose.ui.unit.TextUnit): androidx.compose.ui.text.TextStyle =
    // The design system's score figures (GoaTextStyles.score), at this screen's size.
    dev.quinton.goa.core.designsystem.theme.GoaTextStyles.score.copy(
        fontSize = size,
        lineHeight = size * FIGURE_LINE,
        fontWeight = FontWeight.SemiBold,
    )

private const val FIGURE_LINE = 1.2f

/** Leaderboard and result rows. */
val RowFigure: androidx.compose.ui.unit.TextUnit = 22.sp

/** A player page's own rating. */
val HeaderFigure: androidx.compose.ui.unit.TextUnit = 36.sp

/** Chips are the design system's steel chips, bronze when selected (pair with `selectableChipBorder`). */
@Composable
fun goaFilterChipColors() = dev.quinton.goa.core.designsystem.component.selectableChipColors()
