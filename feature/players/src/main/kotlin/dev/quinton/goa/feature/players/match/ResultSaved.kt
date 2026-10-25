package dev.quinton.goa.feature.players.match

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.PlayerAvatar
import dev.quinton.goa.core.designsystem.component.TeamBadge
import dev.quinton.goa.core.designsystem.component.teamColor
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.domain.match.RematchKind
import dev.quinton.goa.feature.players.common.DeltaIndicator
import dev.quinton.goa.feature.players.common.EngravedCard
import dev.quinton.goa.feature.players.common.ProvisionalPill
import dev.quinton.goa.feature.players.common.SectionHeader
import dev.quinton.goa.feature.players.common.formatDelta
import dev.quinton.goa.feature.players.common.formatRating
import dev.quinton.goa.feature.players.common.label

/** After a result is saved: who won, each player's rating change and rank, who leads, and Rematch. */
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
                    color = teamColor(winner),
                )
            }
        }
        SectionHeader(
            "Rating changes",
            trailing = {
                if (!state.ratingsReady) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp).semantics { contentDescription = "Updating ratings" },
                        strokeWidth = 2.dp,
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
        val leaderRating = state.leaderRating
        if (state.ratingsReady && state.leaderName != null && leaderRating != null) {
            Text(
                "On top now: ${state.leaderName} · ${formatRating(leaderRating.value)} Elo",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = Spacing.md),
            )
        }
        Spacer(Modifier.size(Spacing.xl))
        Button(
            onClick = { chooseRematch = true },
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) {
            Icon(Icons.Outlined.Replay, contentDescription = null)
            Spacer(Modifier.width(Spacing.sm))
            Text("Rematch")
        }
        OutlinedButton(
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

@Composable
private fun PlayerChangeRow(line: PlayerChangeUi, ready: Boolean) {
    val description = buildString {
        append(line.name)
        append(if (line.won) ", won" else ", lost")
        if (ready) {
            line.delta?.let { append(", rating ${formatDelta(it)}") }
            append(", now ${formatRating(line.rating.value)}")
            line.rank?.let { append(", rank $it") }
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        line.player?.let { PlayerAvatar(it, size = 36.dp) }
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                line.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    buildString {
                        append(if (line.won) "Won" else "Lost")
                        if (ready) line.rank?.let { append(" · #$it") }
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (ready && line.rating.provisional) ProvisionalPill()
            }
        }
        if (ready) {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    formatRating(line.rating.value),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                DeltaIndicator(line.delta, label = "rating")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RematchSheet(options: List<RematchOption>, onPick: (RematchKind?) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
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
