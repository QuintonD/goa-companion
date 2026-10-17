package dev.quinton.goa.feature.players.meta

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
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import dev.quinton.goa.core.designsystem.component.HeroPortrait
import dev.quinton.goa.core.designsystem.component.PlayerAvatar
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.feature.players.common.EngravedCard
import dev.quinton.goa.feature.players.common.LoadingState
import dev.quinton.goa.feature.players.common.SectionHeader
import dev.quinton.goa.feature.players.common.StatRow
import dev.quinton.goa.feature.players.common.formatPercent
import dev.quinton.goa.feature.players.common.formatRating
import dev.quinton.goa.feature.players.common.winLoss

/** Meta dashboard: hero win/pick/ban rates ranked by Wilson bound, best pilots, pairs. */
@Composable
fun MetaContent(state: MetaUiState, onOpenRatingSettings: () -> Unit, modifier: Modifier = Modifier) {
    when {
        state.loading -> LoadingState(modifier)

        state.isEmpty -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(
                icon = Icons.Outlined.Insights,
                title = "The meta is still forming",
                message = "Win, pick and ban rates appear once completed matches and drafts are logged.",
            )
        }

        else -> LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = Spacing.xxl),
        ) {
            item(key = "summary") {
                StatRow(
                    listOf(
                        state.totalMatches.toString() to "Rated matches",
                        state.draftsWithBans.toString() to "Drafts with bans",
                        (if (state.largerTeam.games == 0) "—" else formatPercent(state.largerTeam.winRate)) to
                            (
                                "Larger team won" + if (state.largerTeam.games > 0) {
                                    " (${state.largerTeam.games})"
                                } else {
                                    ""
                                }
                                ),
                    ),
                    Modifier.padding(top = Spacing.md),
                )
            }
            item(key = "explain") {
                Text(
                    "Heroes are ranked by the 95 % Wilson lower bound of their win rate, so small samples " +
                        "don't top the table. A hero needs ${state.minSample} games to be ranked.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.md),
                )
            }
            if (state.ranked.isNotEmpty()) {
                item(key = "h-ranked") { SectionHeader("Hero ranking") }
                item(key = "ranked") { HeroTable(state.ranked, ranked = true) }
            }
            if (state.unranked.isNotEmpty()) {
                item(key = "h-unranked") { SectionHeader("Not enough games yet") }
                item(key = "unranked") { HeroTable(state.unranked, ranked = false) }
            }
            if (state.synergies.isNotEmpty()) {
                item(key = "h-pairs") { SectionHeader("Strongest pairs") }
                items(state.synergies, key = { "p-" + it.heroA + it.heroB }) { pair ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics(mergeDescendants = true) {},
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${pair.heroA} + ${pair.heroB}",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "${winLoss(pair.record.wins, pair.record.games)} · ${formatPercent(pair.record.winRate)}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item(key = "settings") {
                TextButton(onClick = onOpenRatingSettings, modifier = Modifier.padding(top = Spacing.lg)) {
                    Icon(Icons.Outlined.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Spacing.sm))
                    Text("How ratings are calculated")
                }
            }
        }
    }
}

@Composable
private fun HeroTable(rows: List<HeroMetaRow>, ranked: Boolean) {
    EngravedCard(Modifier.fillMaxWidth()) {
        Column {
            rows.forEachIndexed { i, row ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                HeroMetaItem(row, rank = if (ranked) i + 1 else null)
            }
        }
    }
}

@Composable
private fun HeroMetaItem(row: HeroMetaRow, rank: Int?) {
    val meta = row.meta
    val description = buildString {
        rank?.let { append("Rank $it, ") }
        append(row.name)
        append(", ${meta.record.wins} wins ${meta.record.losses} losses")
        append(", win rate ${formatPercent(meta.winRate)}, at least ${formatPercent(meta.wilsonLower)}")
        append(", pick rate ${formatPercent(meta.pickRate)}")
        meta.banRate?.let { append(", ban rate ${formatPercent(it)}") }
        row.rating?.let { append(", hero rating ${formatRating(it.value)}") }
        row.bestPlayer?.let { append(", best pilot ${it.name}") }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(Spacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (rank != null) {
                Text(
                    "$rank",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(24.dp),
                )
            }
            HeroPortrait(row.heroId, row.portrait, contentDescription = null, modifier = Modifier.size(40.dp))
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    row.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${winLoss(meta.record.wins, meta.record.games)} · ${meta.record.games} games",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatPercent(meta.winRate), style = MaterialTheme.typography.titleLarge)
                Text(
                    "≥ ${formatPercent(meta.wilsonLower)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        LinearProgressIndicator(
            progress = { (meta.winRate ?: 0.0).toFloat() },
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
            color = MaterialTheme.colorScheme.secondary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            drawStopIndicator = {},
        )
        FlowRow(
            Modifier.padding(top = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Metric("Pick", formatPercent(meta.pickRate))
            Metric("Ban", meta.banRate?.let { formatPercent(it) } ?: "—")
            row.rating?.let { Metric("Hero Elo", formatRating(it.value)) }
            if (row.bestPlayer != null && row.bestRecord != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Best",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(Spacing.xs))
                    PlayerAvatar(row.bestPlayer, size = 20.dp)
                    Spacer(Modifier.width(Spacing.xs))
                    Text(
                        "${row.bestPlayer.name} ${winLoss(row.bestRecord.wins, row.bestRecord.games)}",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(Spacing.xs))
        Text(value, style = MaterialTheme.typography.labelLarge)
    }
}
