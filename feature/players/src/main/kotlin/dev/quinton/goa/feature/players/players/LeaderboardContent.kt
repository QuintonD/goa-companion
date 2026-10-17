package dev.quinton.goa.feature.players.players

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.EmptyState
import dev.quinton.goa.core.designsystem.component.PlayerAvatar
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.feature.players.common.DeltaIndicator
import dev.quinton.goa.feature.players.common.EngravedCard
import dev.quinton.goa.feature.players.common.LoadingState
import dev.quinton.goa.feature.players.common.ProvisionalPill
import dev.quinton.goa.feature.players.common.SectionHeader
import dev.quinton.goa.feature.players.common.formatDelta
import dev.quinton.goa.feature.players.common.formatRating
import dev.quinton.goa.feature.players.common.winLoss

/** The "Players" tab: Elo leaderboard, then unrated and (optionally) archived players. */
@Composable
fun LeaderboardContent(
    state: PlayersUiState,
    onOpenPlayer: (String) -> Unit,
    onAddPlayer: () -> Unit,
    onToggleArchived: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        state.loading -> LoadingState(modifier)
        state.isEmpty -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(
                icon = Icons.Outlined.Groups,
                title = "Gather your guards",
                message = "Add everyone in your play group. Ratings appear after the first logged match.",
                action = {
                    Button(onClick = onAddPlayer) {
                        Icon(Icons.Outlined.PersonAdd, contentDescription = null)
                        Spacer(Modifier.width(Spacing.sm))
                        Text("Add player")
                    }
                },
            )
        }
        else -> LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = 96.dp),
        ) {
            if (state.ranked.isNotEmpty()) {
                item(key = "h-ranked") {
                    SectionHeader(
                        "Leaderboard",
                        trailing = {
                            Text(
                                "${state.ratedMatches} rated ${if (state.ratedMatches == 1) "match" else "matches"}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                }
                item(key = "ranked") {
                    EngravedCard(Modifier.fillMaxWidth()) {
                        Column {
                            state.ranked.forEachIndexed { i, row ->
                                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                LeaderboardRowItem(row, onClick = { onOpenPlayer(row.player.id) })
                            }
                        }
                    }
                }
            }
            if (state.unrated.isNotEmpty()) {
                item(key = "h-unrated") { SectionHeader("No games yet") }
                items(state.unrated, key = { "u-" + it.player.id }) { row ->
                    LeaderboardRowItem(row, onClick = { onOpenPlayer(row.player.id) })
                }
            }
            if (state.archived.isNotEmpty()) {
                item(key = "archived-toggle") {
                    TextButton(onClick = onToggleArchived, modifier = Modifier.padding(top = Spacing.md)) {
                        Icon(Icons.Outlined.Inventory2, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(Spacing.sm))
                        Text(
                            if (state.showArchived) "Hide archived players" else "Show archived players (${state.archived.size})",
                        )
                    }
                }
                if (state.showArchived) {
                    items(state.archived, key = { "a-" + it.player.id }) { row ->
                        LeaderboardRowItem(row, onClick = { onOpenPlayer(row.player.id) }, archived = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun LeaderboardRowItem(row: LeaderboardRow, onClick: () -> Unit, archived: Boolean = false) {
    val rating = row.rating
    val description = buildString {
        row.rank?.let { append("Rank $it, ") }
        append(row.player.name)
        if (rating.games > 0) {
            append(", rating ${formatRating(rating.value)}")
            if (rating.provisional) append(", provisional")
            append(", ${rating.wins} wins ${rating.losses} losses")
            row.trend?.let { append(", trend ${formatDelta(it)}") }
        } else {
            append(", no games yet")
        }
        if (archived) append(", archived")
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClickLabel = "Open profile", role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RankBadge(row.rank)
        Spacer(Modifier.width(Spacing.md))
        PlayerAvatar(row.player, size = 44.dp)
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                row.player.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (archived) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                if (rating.games > 0) {
                    Text(
                        winLoss(rating.wins, rating.games),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    DeltaIndicator(row.trend)
                    if (rating.provisional) ProvisionalPill()
                } else {
                    Text(
                        if (archived) "Archived" else "Log a match to get rated",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (rating.games > 0) {
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = Spacing.sm)) {
                Text(
                    formatRating(rating.value),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (row.rank == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
                Text("Elo", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RankBadge(rank: Int?) {
    val podium = rank != null && rank <= 3
    Box(
        Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(
                if (podium) MaterialTheme.colorScheme.primary.copy(alpha = if (rank == 1) 1f else 0.22f) else MaterialTheme.colorScheme.surfaceVariant,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            rank?.toString() ?: "–",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = when {
                rank == 1 -> MaterialTheme.colorScheme.onPrimary
                podium -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}
