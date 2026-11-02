package dev.quinton.goa.feature.board.online

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.EmptyState
import dev.quinton.goa.core.designsystem.component.GoaPlate
import dev.quinton.goa.core.designsystem.component.GoaScreen
import dev.quinton.goa.core.designsystem.component.scaledIconSize
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.feature.board.play.online.leaderboard.LeaderboardRowUi
import dev.quinton.goa.feature.board.play.online.leaderboard.LeaderboardTab
import dev.quinton.goa.feature.board.play.online.leaderboard.LeaderboardUi
import dev.quinton.goa.feature.board.ui.bronzeSegments

/** The public leaderboards (ADR-056): Players and Heroes, your row marked, how old the board is. */
@Composable
fun OnlineLeaderboardScreen(
    state: LeaderboardUi,
    onTab: (LeaderboardTab) -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GoaScreen(
        title = "Leaderboard",
        onBack = onBack,
        modifier = modifier,
        actions = {
            IconButton(onClick = onRefresh, enabled = !state.loading) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Refresh")
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                state.tabs.forEachIndexed { i, tab ->
                    SegmentedButton(
                        selected = state.tab == tab,
                        onClick = { onTab(tab) },
                        shape = SegmentedButtonDefaults.itemShape(i, state.tabs.size),
                        colors = bronzeSegments(),
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text(tab.label) }
                }
            }
            state.status?.let { Status(it, state.stale) }
            when {
                state.rows.isNotEmpty() -> {
                    if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    Rows(state)
                }

                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))

                else -> EmptyState(
                    icon = Icons.Outlined.EmojiEvents,
                    title = "Nothing here yet",
                    message = state.empty.orEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun Status(text: String, stale: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        if (stale) {
            Icon(
                Icons.Outlined.CloudOff,
                contentDescription = null,
                tint = GoaTheme.colors.unverified,
                modifier = Modifier.size(scaledIconSize(16.dp)),
            )
            Spacer(Modifier.width(Spacing.xs))
        }
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = if (stale) GoaTheme.colors.unverified else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** One plate for the board, dividers inside (DESIGN.md). */
@Composable
private fun Rows(state: LeaderboardUi) {
    GoaPlate(Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
        state.rows.forEachIndexed { i, row ->
            if (i > 0) HorizontalDivider(color = GoaTheme.colors.engravedBorder)
            BoardRow(row)
        }
    }
}

@Composable
private fun BoardRow(row: LeaderboardRowUi) {
    val spoken = buildString {
        append("Rank ${row.rank}: ${row.name}")
        if (row.isYou) append(" (you)")
        append(", rating ${row.rating}, ${row.wins} wins in ${row.games} games")
    }
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (row.isYou) Modifier.background(GoaTheme.colors.bronzeGlow) else Modifier)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
            .heightIn(min = 40.dp)
            .clearAndSetSemantics { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            row.rank.toString(),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.widthIn(min = 28.dp),
        )
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (row.isYou) {
                    Spacer(Modifier.width(Spacing.sm))
                    Text("You", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
            Text(
                "${row.wins} wins · ${row.games} games",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(Spacing.sm))
        Text(row.rating.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}
