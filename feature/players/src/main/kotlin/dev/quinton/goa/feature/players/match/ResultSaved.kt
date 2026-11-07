package dev.quinton.goa.feature.players.match

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.GoaButton
import dev.quinton.goa.core.designsystem.component.GoaOutlinedButton
import dev.quinton.goa.core.designsystem.component.GoaSheet
import dev.quinton.goa.core.designsystem.component.PlayerAvatar
import dev.quinton.goa.core.designsystem.component.TeamBadge
import dev.quinton.goa.core.designsystem.component.TeamMark
import dev.quinton.goa.core.designsystem.component.scaledIconSize
import dev.quinton.goa.core.designsystem.component.teamColor
import dev.quinton.goa.core.designsystem.component.teamTextColor
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.domain.match.RematchKind
import dev.quinton.goa.feature.players.common.Dates
import dev.quinton.goa.feature.players.common.EngravedCard
import dev.quinton.goa.feature.players.common.LARGE_FONT_SCALE
import dev.quinton.goa.feature.players.common.ProvisionalMark
import dev.quinton.goa.feature.players.common.RowFigure
import dev.quinton.goa.feature.players.common.SectionHeader
import dev.quinton.goa.feature.players.common.figureStyle
import dev.quinton.goa.feature.players.common.formatDelta
import dev.quinton.goa.feature.players.common.formatRating
import dev.quinton.goa.feature.players.common.label
import kotlin.math.roundToInt

/**
 * After a result is saved: who won and how, each player's rating change (the change itself large,
 * before → after under it, and their recent trend), who leads now, and Rematch.
 */
@Composable
fun ResultSavedContent(
    state: SavedResultUi,
    onDone: () -> Unit,
    onRematch: (RematchKind?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var chooseRematch by rememberSaveable { mutableStateOf(false) }
    val match = state.match
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen)
            .padding(bottom = Spacing.xxl),
    ) {
        match.winner?.let { winner ->
            Row(
                Modifier.padding(top = Spacing.lg).semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = teamColor(winner))
                Spacer(Modifier.width(Spacing.sm))
                TeamBadge(winner)
                Text(
                    " won" + (match.winCondition?.let { " · ${it.label()}" } ?: ""),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = teamTextColor(winner),
                )
            }
        }
        Text(
            listOfNotNull(
                match.mode.label(),
                match.durationMin?.let { "$it min" },
                Dates.short(match.playedAt),
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xs),
        )
        SectionHeader(
            "Rating changes",
            trailing = {
                if (!state.ratingsReady) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp).semantics { contentDescription = "Updating ratings" },
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(
                        "Group Elo",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
        EngravedCard(Modifier.fillMaxWidth()) {
            Column {
                state.players.forEachIndexed { i, line ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    PlayerChangeRow(line, state.ratingsReady)
                }
            }
        }
        // The trend lines are left out at large text (PlayerChangeRow), and so is their key.
        val lines = LocalDensity.current.fontScale < LARGE_FONT_SCALE
        if (lines && state.ratingsReady && state.players.any { it.trend.size > 2 }) {
            Text(
                "Each line: the player's rating over their last few matches, this one last.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.sm),
            )
        }
        val leaderRating = state.leaderRating
        if (state.ratingsReady && state.leaderName != null && leaderRating != null) {
            LeaderLine(state, formatRating(leaderRating.value))
        }
        Spacer(Modifier.size(Spacing.xl))
        GoaButton(
            onClick = { chooseRematch = true },
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) {
            Icon(Icons.Outlined.Replay, contentDescription = null)
            Spacer(Modifier.width(Spacing.sm))
            Text("Rematch")
        }
        GoaOutlinedButton(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(top = Spacing.sm),
        ) { Text("Done") }
    }
    if (chooseRematch) {
        RematchSheet(
            options = state.rematchOptions,
            onPick = {
                chooseRematch = false
                onRematch(it)
            },
            onDismiss = { chooseRematch = false },
        )
    }
}

/** "On top now": the leader's crest, name and rating, on its own plate. */
@Composable
private fun LeaderLine(state: SavedResultUi, rating: String) {
    val name = state.leaderName ?: return
    EngravedCard(Modifier.fillMaxWidth().padding(top = Spacing.lg)) {
        Row(
            Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) { contentDescription = "On top now: $name, $rating Elo" }
                .padding(horizontal = Spacing.md, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.EmojiEvents,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(scaledIconSize(24.dp)),
            )
            Spacer(Modifier.width(Spacing.md))
            state.leader?.player?.let {
                PlayerAvatar(it, size = 32.dp)
                Spacer(Modifier.width(Spacing.sm))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "On top now",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(rating, style = figureStyle(RowFigure), color = MaterialTheme.colorScheme.primary)
            Text(
                " Elo",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun PlayerChangeRow(line: PlayerChangeUi, ready: Boolean) {
    val before = line.trend.getOrNull(line.trend.size - 2)
    // After this match (a match logged in the past has later ones on top: "now" is the leaderboard's).
    val after = line.trend.lastOrNull() ?: line.rating.value
    val transition = before?.let { "${formatRating(it)} → ${formatRating(after)}" } ?: formatRating(after)
    val description = buildString {
        append(line.name)
        append(if (line.won) ", won" else ", lost")
        if (ready) {
            line.delta?.let { append(", rating ${formatDelta(it)}") }
            before?.let { append(", from ${formatRating(it)} to ${formatRating(after)}") }
            append(", now ${formatRating(line.rating.value)}")
            line.rank?.let { append(", rank $it") }
            if (line.rating.provisional) append(", provisional")
        }
    }
    val large = LocalDensity.current.fontScale >= LARGE_FONT_SCALE
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clearAndSetSemantics { contentDescription = description }
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        line.player?.let { PlayerAvatar(it, size = 40.dp, showFace = true) }
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                line.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TeamMark(line.team, markSize = scaledIconSize(12.dp))
                    Spacer(Modifier.width(Spacing.xs))
                    Text(
                        buildString {
                            append(if (line.won) "Won" else "Lost")
                            if (ready) line.rank?.let { append(" · #$it") }
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (ready && line.rating.provisional) ProvisionalMark(label = true)
            }
            // Large text: the figures go under the name, where they have the row's width.
            if (ready && large) {
                FlowRow(
                    Modifier.padding(top = Spacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    DeltaFigure(line.delta)
                    Text(
                        transition,
                        style = figureStyle(MaterialTheme.typography.labelMedium.fontSize),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (ready && !large && line.trend.size > 2) {
            Spacer(Modifier.width(Spacing.sm))
            Sparkline(line.trend, line.delta ?: 0.0)
        }
        if (ready && !large) {
            Spacer(Modifier.width(Spacing.sm))
            Column(horizontalAlignment = Alignment.End) {
                DeltaFigure(line.delta)
                Text(
                    transition,
                    style = figureStyle(MaterialTheme.typography.labelMedium.fontSize),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/** The change as the row's main figure: arrow shape, sign and colour (never colour alone). */
@Composable
private fun DeltaFigure(delta: Double?) {
    if (delta == null) return
    val rounded = delta.roundToInt()
    val color = when {
        rounded > 0 -> MaterialTheme.colorScheme.secondary
        rounded < 0 -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val icon = when {
        rounded > 0 -> Icons.AutoMirrored.Filled.TrendingUp
        rounded < 0 -> Icons.AutoMirrored.Filled.TrendingDown
        else -> Icons.AutoMirrored.Filled.TrendingFlat
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(scaledIconSize(20.dp)))
        Spacer(Modifier.width(Spacing.xs))
        Text(formatDelta(delta), style = figureStyle(RowFigure), color = color, maxLines = 1)
    }
}

/**
 * The player's rating over their last few rated matches as a small line, this match last (its
 * segment in the change's colour). Decorative: the row says the numbers.
 */
@Composable
private fun Sparkline(values: List<Double>, delta: Double) {
    val line = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = SPARK_ALPHA)
    val last = when {
        delta.roundToInt() > 0 -> MaterialTheme.colorScheme.secondary
        delta.roundToInt() < 0 -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Canvas(Modifier.size(width = 56.dp, height = 28.dp)) {
        val low = values.min()
        val span = (values.max() - low).takeIf { it > 0.0 } ?: 1.0
        val stroke = 2.dp.toPx()
        val inset = stroke * 2
        val h = size.height - inset * 2
        fun at(i: Int) = Offset(
            inset + i * (size.width - inset * 2) / (values.size - 1),
            inset + h - ((values[i] - low) / span * h).toFloat(),
        )
        val path = Path().apply {
            moveTo(at(0).x, at(0).y)
            for (i in 1 until values.size - 1) lineTo(at(i).x, at(i).y)
        }
        val style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawPath(path, line, style = style)
        drawLine(last, at(values.size - 2), at(values.size - 1), strokeWidth = stroke, cap = StrokeCap.Round)
        drawCircle(last, radius = stroke * DOT, center = at(values.size - 1))
    }
}

private const val SPARK_ALPHA = 0.7f
private const val DOT = 1.4f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RematchSheet(options: List<RematchOption>, onPick: (RematchKind?) -> Unit, onDismiss: () -> Unit) {
    GoaSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.navigationBarsPadding().padding(bottom = Spacing.lg)) {
            Text(
                "Rematch",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = Spacing.screen).semantics { heading() },
            )
            Text(
                "Same group again. Match setup opens with these seats; flip the Tie Breaker coin there.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.sm),
            )
            options.forEach { option ->
                val enabled = option.unavailable == null
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .clickable(enabled = enabled, role = Role.Button) { onPick(option.kind) }
                        .alpha(if (enabled) 1f else 0.55f)
                        .padding(horizontal = Spacing.screen, vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(option.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            option.unavailable ?: option.body,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (enabled) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
