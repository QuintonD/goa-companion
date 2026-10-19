package dev.quinton.goa.feature.players.meta

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.EmptyState
import dev.quinton.goa.core.designsystem.component.HeroPortrait
import dev.quinton.goa.core.designsystem.component.PlayerAvatar
import dev.quinton.goa.core.designsystem.component.TeamBadge
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.domain.meta.WinConditionMix
import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.match.WinCondition
import dev.quinton.goa.core.presentation.players.Dates
import dev.quinton.goa.core.presentation.players.GameNightUi
import dev.quinton.goa.core.presentation.players.HeroMetaRow
import dev.quinton.goa.core.presentation.players.HeroSort
import dev.quinton.goa.core.presentation.players.MetaUiState
import dev.quinton.goa.core.presentation.players.MetaView
import dev.quinton.goa.core.presentation.players.PairRow
import dev.quinton.goa.core.presentation.players.StreakRow
import dev.quinton.goa.core.presentation.players.formatPercent
import dev.quinton.goa.core.presentation.players.formatRating
import dev.quinton.goa.core.presentation.players.label
import dev.quinton.goa.core.presentation.players.plural
import dev.quinton.goa.core.presentation.players.spokenRecord
import dev.quinton.goa.core.presentation.players.winLoss
import dev.quinton.goa.feature.players.common.DeltaIndicator
import dev.quinton.goa.feature.players.common.EngravedCard
import dev.quinton.goa.feature.players.common.LARGE_FONT_SCALE
import dev.quinton.goa.feature.players.common.LoadingState
import dev.quinton.goa.feature.players.common.SectionHeader
import dev.quinton.goa.feature.players.common.StatRow
import dev.quinton.goa.feature.players.common.goaFilterChipColors
import dev.quinton.goa.feature.players.common.goaSegmentedColors
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Callbacks of the Meta tab. */
data class MetaActions(
    val onOpenRatingSettings: () -> Unit = {},
    val onView: (MetaView) -> Unit = {},
    val onSort: (HeroSort) -> Unit = {},
)

/**
 * Meta dashboard: the last game night, how games end, sides and length, the hero ranking (compact
 * sortable table or cards, Wilson-ranked), rivalries and duos, hero pairs. All from the match log.
 */
@Composable
fun MetaContent(state: MetaUiState, actions: MetaActions, modifier: Modifier = Modifier) {
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
                        state.largerTeamValue to state.largerTeamLabel,
                    ),
                    Modifier.padding(top = Spacing.md),
                )
            }
            state.night?.let { night -> item(key = "night") { GameNightCard(night, state.streaks) } }
            if (state.winConditions.total > 0) {
                item(key = "h-games") { SectionHeader("How games end") }
                item(key = "games") { GamesCard(state) }
            }
            item(key = "h-heroes") { HeroHeader(state, actions) }
            if (state.view == MetaView.TABLE) {
                item(key = "table") { HeroCompactTable(state) }
            } else {
                if (state.ranked.isNotEmpty()) item(key = "ranked") { HeroCards(state.ranked, ranked = true) }
                if (state.unranked.isNotEmpty()) {
                    item(key = "h-unranked") { SectionHeader("Not enough games yet") }
                    item(key = "unranked") { HeroCards(state.unranked, ranked = false) }
                }
            }
            item(key = "explain") {
                Text(
                    state.rankingNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.md),
                )
            }
            if (state.rivals.isNotEmpty()) {
                item(key = "h-rivals") { SectionHeader("Rivalries") }
                item(key = "rivals") { PairsCard(state.rivals, versus = true) }
            }
            if (state.duos.isNotEmpty()) {
                item(key = "h-duos") { SectionHeader("Best teammates") }
                item(key = "duos") { PairsCard(state.duos, versus = false) }
            }
            if (state.synergies.isNotEmpty()) {
                item(key = "h-pairs") { SectionHeader("Strongest hero pairs") }
                item(key = "pairs") {
                    EngravedCard(Modifier.fillMaxWidth()) {
                        Column {
                            state.synergies.forEachIndexed { i, pair ->
                                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                LineRow(
                                    "${pair.heroA} + ${pair.heroB}",
                                    "${winLoss(
                                        pair.record.wins,
                                        pair.record.games,
                                    )} · ${formatPercent(pair.record.winRate)}",
                                )
                            }
                        }
                    }
                }
            }
            item(key = "settings") {
                TextButton(onClick = actions.onOpenRatingSettings, modifier = Modifier.padding(top = Spacing.lg)) {
                    Icon(Icons.Outlined.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Spacing.sm))
                    Text("How ratings are calculated")
                }
            }
        }
    }
}

@Composable
private fun GameNightCard(night: GameNightUi, streaks: List<StreakRow>) {
    SectionHeader("Last game night · ${Dates.night(night.date)}")
    EngravedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.semantics(mergeDescendants = true) {
                },
            ) {
                Text(plural(night.matches, "match", "matches"), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                TeamBadge(Team.RED)
                Text(" ${night.redWins}  ", style = MaterialTheme.typography.titleMedium)
                TeamBadge(Team.BLUE)
                Text(" ${night.blueWins}", style = MaterialTheme.typography.titleMedium)
            }
            val movers = night.shownMovers
            if (movers.isNotEmpty()) {
                Text(
                    "Biggest movers",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                movers.forEach { mover ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 40.dp).semantics(mergeDescendants = true) {},
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        mover.player?.let { PlayerAvatar(it, size = 28.dp) }
                        Spacer(Modifier.width(Spacing.sm))
                        Text(mover.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        DeltaIndicator(mover.delta, label = "rating change that night")
                    }
                }
            }
            if (streaks.isNotEmpty()) {
                Text(
                    "Streaks",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    streaks.forEach { s ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.semantics(mergeDescendants = true) {
                                contentDescription =
                                    "${s.name}, ${s.length} ${if (s.won) "wins" else "losses"} in a row"
                            },
                        ) {
                            s.player?.let { PlayerAvatar(it, size = 20.dp) }
                            Spacer(Modifier.width(Spacing.xs))
                            Text(
                                "${s.name} ${if (s.won) "W" else "L"}${s.length}",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (s.won) {
                                    MaterialTheme.colorScheme.secondary
                                } else {
                                    MaterialTheme.colorScheme.error
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GamesCard(state: MetaUiState) {
    EngravedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            WinConditionBars(state.winConditions)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            val red = state.red
            Row(
                Modifier.semantics(mergeDescendants = true) {
                    contentDescription = "Red won ${red.wins} of ${red.games}, Blue won ${red.losses}"
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Sides", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                TeamBadge(Team.RED)
                Text(" ${red.wins} (${formatPercent(red.winRate)})   ", style = MaterialTheme.typography.labelLarge)
                TeamBadge(Team.BLUE)
                Text(" ${red.losses}", style = MaterialTheme.typography.labelLarge)
            }
            GameMode.entries.forEach { mode ->
                state.durationByMode[mode]?.let { d ->
                    LineRow(
                        "Average ${mode.label()}",
                        "${d.meanMinutes.roundToInt()} min · ${plural(d.matches, "match", "matches")}",
                        inset = 0.dp,
                    )
                }
            }
        }
    }
}

/** Share of wins per condition as labelled bars ("Not recorded" when no condition was logged). */
@Composable
fun WinConditionBars(mix: WinConditionMix, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        (WinCondition.entries + listOf(null)).forEach { condition ->
            val count = mix.counts[condition] ?: 0
            if (count == 0 && condition == null) return@forEach
            val name = condition?.label() ?: "Not recorded"
            Row(
                Modifier.semantics(mergeDescendants = true) { contentDescription = "$name, $count of ${mix.total}" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(112.dp), maxLines = 1)
                LinearProgressIndicator(
                    progress = { (mix.share(condition) ?: 0.0).toFloat() },
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.secondary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    drawStopIndicator = {},
                )
                Text(
                    "$count",
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(40.dp),
                )
            }
        }
    }
}

@Composable
private fun HeroHeader(state: MetaUiState, actions: MetaActions) {
    Column(Modifier.padding(top = Spacing.lg)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "HERO RANKING",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            SingleChoiceSegmentedButtonRow {
                MetaView.entries.forEachIndexed { i, view ->
                    SegmentedButton(
                        selected = state.view == view,
                        onClick = { actions.onView(view) },
                        shape = SegmentedButtonDefaults.itemShape(i, MetaView.entries.size),
                        colors = goaSegmentedColors(),
                        icon = {},
                    ) { Text(if (view == MetaView.TABLE) "Table" else "Cards", maxLines = 1) }
                }
            }
        }
        if (state.view == MetaView.TABLE) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(vertical = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                HeroSort.entries.forEach { sort ->
                    val selected = state.sort == sort
                    FilterChip(
                        selected = selected,
                        onClick = { actions.onSort(sort) },
                        label = { Text(sort.label) },
                        leadingIcon = if (selected) {
                            { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        } else {
                            null
                        },
                        colors = goaFilterChipColors(),
                        modifier = Modifier.heightIn(min = 48.dp).semantics {
                            contentDescription =
                                "Sort by ${sort.label}"
                        },
                    )
                }
            }
        } else {
            Spacer(Modifier.size(Spacing.sm))
        }
    }
}

/** Compact table: one 48 dp line per hero. Unranked heroes are dimmed and have no rank number. */
@Composable
private fun HeroCompactTable(state: MetaUiState) {
    val wide = LocalDensity.current.fontScale < LARGE_FONT_SCALE
    val rankOf = state.rankOf
    EngravedCard(Modifier.fillMaxWidth()) {
        Column {
            Row(
                Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm).clearAndSetSemantics {},
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Cell("#", 28.dp)
                Spacer(Modifier.width(Spacing.xs))
                Text(
                    "Hero",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Cell("W–L", 60.dp)
                Cell("Win", 48.dp)
                if (wide) {
                    Cell("Pick", 48.dp)
                    Cell("Ban", 44.dp)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            state.sortedHeroes.forEach { row ->
                val rank = rankOf[row.heroId]
                val meta = row.meta
                val dim = if (rank == null) 0.6f else 1f
                val description = buildString {
                    rank?.let { append("Rank $it, ") } ?: append("Not ranked yet, ")
                    append("${row.name}, ${spokenRecord(meta.record.wins, meta.record.losses)}")
                    append(", win rate ${formatPercent(meta.winRate)}, pick rate ${formatPercent(meta.pickRate)}")
                    meta.banRate?.let { append(", ban rate ${formatPercent(it)}") }
                    row.rating?.let { append(", hero rating ${formatRating(it.value)}") }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .semantics(mergeDescendants = true) { contentDescription = description }
                        .padding(horizontal = Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Cell(
                        rank?.toString() ?: "–",
                        28.dp,
                        bold = rank != null,
                        color = if (rank !=
                            null
                        ) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            null
                        },
                    )
                    Spacer(Modifier.width(Spacing.xs))
                    HeroPortrait(row.heroId, row.portrait, contentDescription = null, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        row.name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = dim),
                        modifier = Modifier.weight(1f),
                    )
                    Cell(winLoss(meta.record.wins, meta.record.games), 60.dp)
                    Cell(formatPercent(meta.winRate), 48.dp, bold = true)
                    if (wide) {
                        Cell(formatPercent(meta.pickRate), 48.dp)
                        Cell(meta.banRate?.let { formatPercent(it) } ?: "—", 44.dp)
                    }
                }
            }
        }
    }
}

/** A right-aligned table cell; [width] is for font scale 1 and grows with the text. */
@Composable
private fun Cell(text: String, width: Dp, bold: Boolean = false, color: Color? = null) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        color = color ?: MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.End,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier.width(width * LocalDensity.current.fontScale.coerceAtLeast(1f)),
    )
}

@Composable
private fun PairsCard(rows: List<PairRow>, versus: Boolean) {
    EngravedCard(Modifier.fillMaxWidth()) {
        Column {
            rows.forEachIndexed { i, pair ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                val joiner = if (versus) "vs" else "+"
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .semantics(mergeDescendants = true) {
                            contentDescription = if (versus) {
                                "${pair.aName} against ${pair.bName}: " +
                                    spokenRecord(pair.record.wins, pair.record.losses)
                            } else {
                                "${pair.aName} and ${pair.bName} together: " +
                                    spokenRecord(pair.record.wins, pair.record.losses)
                            }
                        }
                        .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    pair.a?.let { PlayerAvatar(it, size = 24.dp) }
                    Spacer(Modifier.width(Spacing.xs))
                    Text(
                        "${pair.aName} $joiner ${pair.bName}",
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 2,
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
    }
}

@Composable
private fun LineRow(label: String, value: String, inset: Dp = Spacing.md) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .padding(horizontal = inset)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HeroCards(rows: List<HeroMetaRow>, ranked: Boolean) {
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
        append(", ${spokenRecord(meta.record.wins, meta.record.losses)}")
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
                    "${winLoss(meta.record.wins, meta.record.games)} · ${plural(meta.record.games, "game")}",
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
            val bestPlayer = row.bestPlayer
            val bestRecord = row.bestRecord
            if (bestPlayer != null && bestRecord != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Best",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(Spacing.xs))
                    PlayerAvatar(bestPlayer, size = 20.dp)
                    Spacer(Modifier.width(Spacing.xs))
                    Text(
                        "${bestPlayer.name} ${winLoss(bestRecord.wins, bestRecord.games)}",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
        row.winsSummary?.let { wins ->
            Text(
                wins,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs),
            )
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
