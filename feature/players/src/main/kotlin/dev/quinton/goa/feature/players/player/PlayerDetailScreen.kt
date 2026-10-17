package dev.quinton.goa.feature.players.player

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.EmptyState
import dev.quinton.goa.core.designsystem.component.GoaScreen
import dev.quinton.goa.core.designsystem.component.HeroPortrait
import dev.quinton.goa.core.designsystem.component.PlayerAvatar
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.feature.players.common.EngravedCard
import dev.quinton.goa.feature.players.common.LoadingState
import dev.quinton.goa.feature.players.common.Pill
import dev.quinton.goa.feature.players.common.ProvisionalPill
import dev.quinton.goa.feature.players.common.SectionHeader
import dev.quinton.goa.feature.players.common.StatRow
import dev.quinton.goa.feature.players.common.formatPercent
import dev.quinton.goa.feature.players.common.formatRating
import dev.quinton.goa.feature.players.common.spokenRecord
import dev.quinton.goa.feature.players.common.winLoss
import dev.quinton.goa.feature.players.match.MatchCard
import dev.quinton.goa.feature.players.meta.WinConditionBars

data class PlayerDetailActions(
    val onBack: () -> Unit = {},
    val onEdit: () -> Unit = {},
    val onOpenMatch: (String) -> Unit = {},
    val onOpenMatchLog: () -> Unit = {},
    val onSetArchived: (Boolean) -> Unit = {},
    val onMerge: (String) -> Unit = {},
    val onMergeOutcomeShown: () -> Unit = {},
)

@Composable
fun PlayerDetailScreen(state: PlayerDetailUiState, actions: PlayerDetailActions, modifier: Modifier = Modifier) {
    GoaScreen(
        title = state.player?.name ?: "Player",
        modifier = modifier,
        onBack = actions.onBack,
        actions = {
            if (state.player != null) {
                IconButton(onClick = actions.onEdit) { Icon(Icons.Outlined.Edit, contentDescription = "Edit player") }
            }
        },
    ) { padding ->
        val player = state.player
        when {
            state.loading -> LoadingState(Modifier.padding(padding))

            player == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyState(Icons.Outlined.PersonOff, "Player not found", "This player no longer exists.")
            }

            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = Spacing.xxl),
            ) {
                item(key = "header") { Header(state) }
                item(key = "stats") { Stats(state) }
                item(key = "h-chart") { SectionHeader("Rating history") }
                item(key = "chart") {
                    EngravedCard(Modifier.fillMaxWidth()) {
                        if (state.history.isEmpty()) {
                            EmptyState(
                                icon = Icons.AutoMirrored.Outlined.ShowChart,
                                title = "No rated matches yet",
                                message = "The chart starts with ${player.name}'s first completed match.",
                            )
                        } else {
                            Column(Modifier.padding(Spacing.md)) {
                                RatingChart(state.history, state.initialRating, dates = state.historyDates)
                                state.peak?.let {
                                    Text(
                                        "Peak ${formatRating(it)} · start ${formatRating(state.initialRating)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = Spacing.sm),
                                    )
                                }
                            }
                        }
                    }
                }
                if (state.heroes.isNotEmpty()) {
                    item(key = "h-heroes") { SectionHeader(if (state.heroesAreBest) "Best heroes" else "Most played") }
                    item(key = "heroes") {
                        EngravedCard(Modifier.fillMaxWidth()) {
                            Column {
                                state.heroes.forEachIndexed { i, hero ->
                                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    HeroRecordItem(hero)
                                }
                            }
                        }
                    }
                }
                if (state.winConditions.total > 0) {
                    item(key = "h-wins") { SectionHeader("How ${player.name} wins") }
                    item(key = "wins") {
                        EngravedCard(Modifier.fillMaxWidth()) {
                            WinConditionBars(state.winConditions, Modifier.padding(Spacing.md))
                        }
                    }
                }
                if (state.rivals.isNotEmpty()) {
                    item(key = "h-rivals") { SectionHeader("Head to head") }
                    item(key = "rivals") { OpponentCard(state.rivals, versus = true) }
                }
                if (state.teammates.isNotEmpty()) {
                    item(key = "h-mates") { SectionHeader("Teammates") }
                    item(key = "mates") { OpponentCard(state.teammates, versus = false) }
                }
                item(key = "h-recent") {
                    SectionHeader(
                        "Recent matches",
                        trailing = {
                            if (state.totalMatches > state.recentMatches.size) {
                                TextButton(onClick = actions.onOpenMatchLog) { Text("All ${state.totalMatches}") }
                            }
                        },
                    )
                }
                if (state.recentMatches.isEmpty()) {
                    item(key = "no-recent") {
                        Text(
                            "No matches logged with ${player.name} yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(state.recentMatches, key = { "m-" + it.id }) { match ->
                    MatchCard(match, onOpen = {
                        actions.onOpenMatch(match.id)
                    }, onDelete = null, modifier = Modifier.padding(bottom = Spacing.sm))
                }
                item(key = "archive") {
                    Column(
                        Modifier.fillMaxWidth().padding(top = Spacing.xl),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        OutlinedButton(onClick = { actions.onSetArchived(!player.archived) }) {
                            Icon(
                                if (player.archived) Icons.Outlined.Unarchive else Icons.Outlined.Archive,
                                contentDescription = null,
                            )
                            Spacer(Modifier.width(Spacing.sm))
                            Text(if (player.archived) "Restore player" else "Archive player")
                        }
                        Text(
                            "Archived players are hidden from the leaderboard and pickers. " +
                                "Their matches and ratings stay in the log.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = Spacing.sm),
                        )
                        if (state.mergeCandidates.isNotEmpty()) MergeSection(state, actions)
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(state: PlayerDetailUiState) {
    val player = state.player ?: return
    val rating = state.rating
    Row(
        Modifier.fillMaxWidth().padding(vertical = Spacing.md).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlayerAvatar(player, size = 72.dp)
        Spacer(Modifier.width(Spacing.lg))
        Column(Modifier.weight(1f)) {
            Text(
                player.name,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                state.rank?.let {
                    Text(
                        "Rank $it of ${state.rankedPlayers}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (rating.games > 0 && rating.provisional) ProvisionalPill()
                if (player.archived) Pill("Archived", icon = Icons.Outlined.Archive)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                if (rating.games > 0) formatRating(rating.value) else "—",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Elo",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Stats(state: PlayerDetailUiState) {
    val rating = state.rating
    StatRow(
        listOf(
            rating.games.toString() to "Games",
            (if (rating.games > 0) winLoss(rating.wins, rating.games) else "—") to "Won–lost",
            (if (rating.games > 0) formatPercent(rating.wins.toDouble() / rating.games) else "—") to "Win rate",
            state.doublePilotGames.toString() to "Double pilot",
        ),
    )
}

@Composable
private fun OpponentCard(rows: List<OpponentRow>, versus: Boolean) {
    EngravedCard(Modifier.fillMaxWidth()) {
        Column {
            rows.forEachIndexed { i, row ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .semantics(mergeDescendants = true) {
                            contentDescription = "${if (versus) "Against" else "With"} ${row.name}: " +
                                spokenRecord(row.record.wins, row.record.losses)
                        }
                        .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    row.player?.let { PlayerAvatar(it, size = 28.dp) }
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        "${if (versus) "vs" else "with"} ${row.name}",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${winLoss(row.record.wins, row.record.games)} · ${formatPercent(row.record.winRate)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroRecordItem(hero: HeroRecordRow) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "${hero.name}, ${spokenRecord(hero.record.wins, hero.record.losses)}, " +
                    "win rate ${formatPercent(hero.record.winRate)}"
            }
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeroPortrait(hero.heroId, hero.portrait, contentDescription = null, modifier = Modifier.size(40.dp))
        Spacer(Modifier.width(Spacing.md))
        Text(
            hero.name,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Column(horizontalAlignment = Alignment.End) {
            Text(formatPercent(hero.record.winRate), style = MaterialTheme.typography.titleMedium)
            Text(
                winLoss(hero.record.wins, hero.record.games),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
