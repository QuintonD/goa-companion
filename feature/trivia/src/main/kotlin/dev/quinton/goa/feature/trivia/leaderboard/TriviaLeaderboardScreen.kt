package dev.quinton.goa.feature.trivia.leaderboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.ConfirmDialog
import dev.quinton.goa.core.designsystem.component.EmptyState
import dev.quinton.goa.core.designsystem.component.GoaFilterChip
import dev.quinton.goa.core.designsystem.component.GoaScreen
import dev.quinton.goa.core.designsystem.component.PlayerAvatar
import dev.quinton.goa.core.designsystem.component.scaledIconSize
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.domain.trivia.LeaderboardPeriod
import dev.quinton.goa.core.domain.trivia.TriviaLeaderboard
import dev.quinton.goa.feature.trivia.home.YouTag

/** Callbacks of the leaderboard screen. */
data class LeaderboardActions(
    val onTab: (LeaderboardTab) -> Unit = {},
    val onPeriod: (LeaderboardPeriod) -> Unit = {},
    val onOpenPlayer: (String) -> Unit = {},
    val onClosePlayer: () -> Unit = {},
    val onRemoveRound: (String) -> Unit = {},
)

/**
 * The trivia leaderboard (ADR-032): Challenge and Practice tabs, All time / Last 30 days, ranked
 * rows; a player's rounds in a sheet, each removable after a confirmation.
 */
@Composable
fun TriviaLeaderboardScreen(
    state: TriviaLeaderboardUiState,
    actions: LeaderboardActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** Screenshots: the remove confirmation for this round id, already open. */
    initialConfirm: String? = null,
) {
    var confirm by rememberSaveable { mutableStateOf(initialConfirm) }
    GoaScreen(title = "Leaderboard", onBack = onBack, modifier = modifier) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                TriviaLeaderboardUiState.Loading -> CircularProgressIndicator(
                    Modifier.align(Alignment.Center).semantics { contentDescription = "Loading the leaderboard" },
                )

                is TriviaLeaderboardUiState.Ready -> BoardContent(state, actions)
            }
        }
    }
    val ready = state as? TriviaLeaderboardUiState.Ready
    val selected = ready?.selected
    if (selected != null) {
        PlayerRoundsSheet(
            rounds = selected,
            period = ready.period,
            onRemove = { confirm = it },
            onDismiss = actions.onClosePlayer,
        )
    }
    val round = confirm?.let { id -> selected?.rounds?.firstOrNull { it.id == id } }
    if (round != null) {
        ConfirmDialog(
            title = "Remove from leaderboard?",
            message = "${round.label} comes off the leaderboard here now, and on the group's other phones " +
                "when they next sync. This can't be undone.",
            confirmLabel = "Remove",
            destructive = true,
            icon = Icons.Outlined.DeleteOutline,
            onConfirm = {
                confirm = null
                actions.onRemoveRound(round.id)
            },
            onDismiss = { confirm = null },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BoardContent(state: TriviaLeaderboardUiState.Ready, actions: LeaderboardActions) {
    Column(Modifier.fillMaxSize()) {
        PrimaryTabRow(
            selectedTabIndex = state.tab.ordinal,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.primary,
        ) {
            LeaderboardTab.entries.forEach { tab ->
                Tab(
                    selected = tab == state.tab,
                    onClick = { actions.onTab(tab) },
                    text = { Text(tabLabel(tab), style = MaterialTheme.typography.titleSmall, maxLines = 1) },
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = Spacing.xxl),
        ) {
            item(key = "header") {
                Column(Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.md)) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        LeaderboardPeriod.entries.forEach { period ->
                            GoaFilterChip(
                                selected = period == state.period,
                                onClick = { actions.onPeriod(period) },
                                label = periodLabel(period),
                            )
                        }
                    }
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        if (state.tab == LeaderboardTab.CHALLENGE) {
                            "Each player's best Challenge: most right answers, then the quicker time."
                        } else {
                            "Every round played as a player, Challenges included: most right answers, then accuracy."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (state.rows.isEmpty()) {
                item(key = "empty") { EmptyBoard(state, Modifier.fillMaxWidth()) }
            } else {
                items(state.rows, key = { "${state.tab}-${it.player.id}" }) { row ->
                    RankedRow(row, onClick = { actions.onOpenPlayer(row.player.id) })
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(start = Spacing.screen + 28.dp + Spacing.md),
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyBoard(state: TriviaLeaderboardUiState.Ready, modifier: Modifier) {
    val challenge = state.tab == LeaderboardTab.CHALLENGE
    val recentOnly = state.period == LeaderboardPeriod.LAST_30_DAYS && state.anyRounds
    when {
        recentOnly -> EmptyState(
            icon = Icons.Outlined.EventBusy,
            title = if (challenge) "No Challenges in the last 30 days" else "No rounds in the last 30 days",
            message = "Older scores are under All time. Play a round to get back on the board.",
            modifier = modifier,
        )

        challenge -> EmptyState(
            icon = Icons.Outlined.EmojiEvents,
            title = "Play a Challenge to get on the board",
            message = "20 questions on every hero, the same quiz for everyone. On Card trivia, pick who's " +
                "playing and tap Challenge.",
            modifier = modifier,
        )

        else -> EmptyState(
            icon = Icons.Outlined.EmojiEvents,
            title = "Play a round as a player to get on the board",
            message = "Pick who's playing on Card trivia: every round they finish counts here. Guest rounds " +
                "aren't recorded.",
            modifier = modifier,
        )
    }
}

@Composable
private fun RankedRow(row: LeaderboardRowUi, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable(onClickLabel = "See rounds", role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = row.spoken }
            .padding(horizontal = Spacing.screen, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlaceBadge(row.place, row.tied)
        Spacer(Modifier.width(Spacing.md))
        PlayerAvatar(row.player, size = scaledIconSize(44.dp, maxScale = 1.4f))
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.player.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (row.isMe) {
                    Spacer(Modifier.width(Spacing.sm))
                    YouTag()
                }
            }
            Text(
                row.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(Spacing.sm))
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthIn(min = 48.dp)) {
            Text(
                row.main,
                style = GoaTextStyles.numeric.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize),
                fontWeight = FontWeight.SemiBold,
                color = if (row.place == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            row.mainCaption?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * The place as a number in a medallion (never colour alone): bronze for the top three, the first
 * filled; a shared place carries "=".
 */
@Composable
private fun PlaceBadge(place: Int, tied: Boolean) {
    val podium = place <= PODIUM
    val primary = MaterialTheme.colorScheme.primary
    val background = when {
        place == 1 -> primary
        podium -> primary.copy(alpha = 0.22f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val content = when {
        place == 1 -> MaterialTheme.colorScheme.onPrimary
        podium -> primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        Modifier
            .size(scaledIconSize(28.dp, maxScale = 1.6f))
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (tied) "=$place" else "$place",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = content,
            maxLines = 1,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerRoundsSheet(
    rounds: PlayerRoundsUi,
    period: LeaderboardPeriod,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        PlayerRoundsContent(rounds, period, onRemove)
    }
}

/** A player's recent rounds, newest first, each with "Remove from leaderboard". */
@Composable
internal fun PlayerRoundsContent(
    rounds: PlayerRoundsUi,
    period: LeaderboardPeriod,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier.fillMaxWidth().navigationBarsPadding(),
        contentPadding = PaddingValues(bottom = Spacing.xl),
    ) {
        item(key = "title") {
            Row(
                Modifier.padding(horizontal = Spacing.screen).padding(bottom = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlayerAvatar(rounds.player, size = scaledIconSize(40.dp, maxScale = 1.4f))
                Spacer(Modifier.width(Spacing.md))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            rounds.player.name,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false).semantics { heading() },
                        )
                        if (rounds.isMe) {
                            Spacer(Modifier.width(Spacing.sm))
                            YouTag()
                        }
                    }
                    Text(
                        "Recent rounds · ${periodLabel(period)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (rounds.rounds.isEmpty()) {
            item(key = "none") {
                Text(
                    "No rounds in this period.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(Spacing.screen),
                )
            }
        }
        items(rounds.rounds, key = { it.id }) { round ->
            RoundRow(round, onRemove = { onRemove(round.id) })
        }
    }
}

@Composable
private fun RoundRow(round: RoundUi, onRemove: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.sm)) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(Spacing.sm))
            Row(
                Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = round.spoken },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        round.kind.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (round.kind == "Challenge") {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Text(
                        round.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(Spacing.sm))
                Text(
                    round.score,
                    style = GoaTextStyles.numeric.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.clearAndSetSemantics {},
                )
            }
            TextButton(
                onClick = onRemove,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = "Remove from leaderboard: ${round.label}" },
            ) {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    contentDescription = null,
                    modifier = Modifier.size(scaledIconSize(18.dp)),
                    tint = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.width(Spacing.xs))
                Text("Remove from leaderboard", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

internal fun tabLabel(tab: LeaderboardTab): String = when (tab) {
    LeaderboardTab.CHALLENGE -> "Challenge"
    LeaderboardTab.PRACTICE -> "Practice"
}

internal fun periodLabel(period: LeaderboardPeriod): String = when (period) {
    LeaderboardPeriod.ALL_TIME -> "All time"
    LeaderboardPeriod.LAST_30_DAYS -> "Last ${TriviaLeaderboard.RECENT_DAYS} days"
}

private const val PODIUM = 3
