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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.EmptyState
import dev.quinton.goa.core.designsystem.component.FootArt
import dev.quinton.goa.core.designsystem.component.GoaButton
import dev.quinton.goa.core.designsystem.component.GoaOutlinedButton
import dev.quinton.goa.core.designsystem.component.PODIUM_PLACES
import dev.quinton.goa.core.designsystem.component.PlayerAvatar
import dev.quinton.goa.core.designsystem.component.Podium
import dev.quinton.goa.core.designsystem.component.PodiumPlace
import dev.quinton.goa.core.designsystem.component.ScreenFoot
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.feature.players.common.DeltaIndicator
import dev.quinton.goa.feature.players.common.EngravedCard
import dev.quinton.goa.feature.players.common.LoadingState
import dev.quinton.goa.feature.players.common.ProvisionalMark
import dev.quinton.goa.feature.players.common.RowFigure
import dev.quinton.goa.feature.players.common.SectionHeader
import dev.quinton.goa.feature.players.common.figureStyle
import dev.quinton.goa.feature.players.common.formatDelta
import dev.quinton.goa.feature.players.common.formatRating
import dev.quinton.goa.feature.players.common.plural
import dev.quinton.goa.feature.players.common.spokenRecord
import dev.quinton.goa.feature.players.common.winLoss
import dev.quinton.goa.feature.players.match.LIST_END_PADDING

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
                message = "Add your play group.",
                action = {
                    GoaButton(onClick = onAddPlayer) {
                        Icon(Icons.Outlined.PersonAdd, contentDescription = null)
                        Spacer(Modifier.width(Spacing.sm))
                        Text("Add player")
                    }
                },
            )
        }

        else -> Box(modifier.fillMaxSize()) {
            // The list's foot is the rulebook's battlefield, so a short group doesn't end in empty navy.
            ScreenFoot(FootArt.HALL)
            Board(state, onOpenPlayer, onAddPlayer, onToggleArchived)
        }
    }
}

@Composable
private fun Board(
    state: PlayersUiState,
    onOpenPlayer: (String) -> Unit,
    onAddPlayer: () -> Unit,
    onToggleArchived: () -> Unit,
) {
    // Three on a podium while the columns have room for the names; large text keeps the rows.
    val podium = state.ranked.size >= PODIUM_PLACES && LocalDensity.current.fontScale < LARGE_FONT_SCALE
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = LIST_END_PADDING),
    ) {
        if (state.ranked.isNotEmpty()) {
            item(key = "h-ranked") {
                SectionHeader("Group ratings", Modifier.padding(bottom = Spacing.sm))
            }
            item(key = "ranked") {
                EngravedCard(Modifier.fillMaxWidth()) {
                    Column {
                        val rows = if (podium) {
                            Podium(
                                state.ranked.take(PODIUM_PLACES).map { row ->
                                    PodiumPlace(
                                        place = row.rank ?: 0,
                                        label = row.rank.toString(),
                                        description = row.spoken(archived = false),
                                        onClick = { onOpenPlayer(row.player.id) },
                                        onClickLabel = "View player",
                                    ) { PodiumStanding(row) }
                                },
                                Modifier.padding(start = Spacing.sm, end = Spacing.sm, top = Spacing.md),
                            )
                            state.ranked.drop(PODIUM_PLACES)
                        } else {
                            state.ranked
                        }
                        rows.forEachIndexed { i, row ->
                            if (i > 0 || podium) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            LeaderboardRowItem(
                                row,
                                onClick = { onOpenPlayer(row.player.id) },
                            )
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
        // Under the list, never over it (a floating button covered the last rating at large text).
        item(key = "add-player") {
            GoaOutlinedButton(
                onClick = onAddPlayer,
                modifier = Modifier
                    .padding(top = Spacing.lg)
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .semantics { contentDescription = "Add player" },
            ) {
                Icon(Icons.Outlined.PersonAdd, contentDescription = null)
                Spacer(Modifier.width(Spacing.sm))
                Text("Add player", style = MaterialTheme.typography.titleMedium)
            }
        }
        if (state.archived.isNotEmpty()) {
            item(key = "archived-toggle") {
                TextButton(onClick = onToggleArchived, modifier = Modifier.padding(top = Spacing.sm)) {
                    Icon(Icons.Outlined.Inventory2, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Spacing.sm))
                    val count = state.archived.size
                    Text(if (state.showArchived) "Hide archived players" else "Show archived players ($count)")
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

/** The row as TalkBack reads it: rank, name, rating, provisional, record, trend. */
private fun LeaderboardRow.spoken(archived: Boolean): String {
    val row = this
    return buildString {
        row.rank?.let { append("Rank $it, ") }
        append(row.player.name)
        if (row.isMe) append(", you")
        if (row.rating.games > 0) {
            append(", rating ${formatRating(row.rating.value)}")
            if (row.rating.provisional) append(", provisional")
            append(", ${spokenRecord(row.rating.wins, row.rating.losses)}")
            row.trend?.let { append(", ${formatDelta(it)} over the last ${plural(row.trendGames, "game")}") }
        } else {
            append(", no games yet")
        }
        if (archived) append(", archived")
    }
}

/**
 * A podium place: the crest large with the most-played hero badge, the name (two lines if it needs them) and a
 * chevron, the rating, the record and trend.
 */
@Composable
private fun PodiumStanding(row: LeaderboardRow) {
    val rating = row.rating
    PlayerAvatar(row.player, size = if (row.rank == 1) 60.dp else 50.dp, showFace = true)
    Spacer(Modifier.height(Spacing.xs))
    // The chevron says the place opens the player, like the rows under it.
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            row.player.name,
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        OpenChevron(Modifier.size(18.dp))
    }
    if (row.isMe) YouPill()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            formatRating(rating.value),
            style = figureStyle(RowFigure),
            color = if (row.rank == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        if (rating.provisional) {
            Spacer(Modifier.width(2.dp))
            ProvisionalMark()
        }
    }
    Text(
        winLoss(rating.wins, rating.games),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    DeltaIndicator(row.trend, caption = "last ${row.trendGames}")
}

@Composable
private fun LeaderboardRowItem(
    row: LeaderboardRow,
    onClick: () -> Unit,
    archived: Boolean = false,
) {
    val rating = row.rating
    val description = row.spoken(archived)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClickLabel = "View player", role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RankBadge(row.rank)
        Spacer(Modifier.width(Spacing.md))
        PlayerAvatar(row.player, size = 44.dp, showFace = true)
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.player.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (archived) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (rating.provisional) {
                    Spacer(Modifier.width(Spacing.xs))
                    ProvisionalMark()
                }
                if (row.isMe) {
                    Spacer(Modifier.width(Spacing.sm))
                    YouPill()
                }
            }
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
                    // Self-explaining: the change over the last few games, e.g. "↗ +24 last 5".
                    DeltaIndicator(row.trend, caption = "last ${row.trendGames}")
                } else {
                    Text(
                        if (archived) "Archived" else "No games yet",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        // Every row opens the player's page and the chevron says so; with large text the trend needs its width, and
        // the row, clickable and labelled "View player", still says it to TalkBack.
        val chevron = LocalDensity.current.fontScale < LARGE_FONT_SCALE
        if (rating.games > 0) {
            // The rating alone: the section head names it.
            Text(
                formatRating(rating.value),
                style = figureStyle(RowFigure),
                color = if (row.rank == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = Spacing.sm, end = if (chevron) Spacing.sm else 0.dp),
            )
        }
        if (chevron) OpenChevron()
    }
}

@Composable
private fun OpenChevron(modifier: Modifier = Modifier) {
    Icon(
        Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
private fun RankBadge(rank: Int?) {
    val podium = rank != null && rank <= 3
    Box(
        Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(
                if (podium) {
                    MaterialTheme.colorScheme.primary.copy(
                        alpha = if (rank == 1) {
                            1f
                        } else {
                            0.22f
                        },
                    )
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
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

/** Marks this phone's own player (group sync, "Which player are you?"). */
@Composable
private fun YouPill() {
    Text(
        "You",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = Spacing.sm, vertical = 2.dp),
    )
}

/** From this font scale the podium's three columns give way to rows (DESIGN.md "Large text"). */
private const val LARGE_FONT_SCALE = 1.3f
