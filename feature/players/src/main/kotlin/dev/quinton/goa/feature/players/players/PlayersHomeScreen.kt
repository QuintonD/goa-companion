package dev.quinton.goa.feature.players.players

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.quinton.goa.core.designsystem.component.BannerTextButton
import dev.quinton.goa.core.designsystem.component.BarAction
import dev.quinton.goa.core.designsystem.component.BarActions
import dev.quinton.goa.core.designsystem.component.BarOverflowMenu
import dev.quinton.goa.core.designsystem.component.GoaScreen
import dev.quinton.goa.core.designsystem.component.GoaTabRow
import dev.quinton.goa.core.designsystem.icon.Destination
import dev.quinton.goa.core.designsystem.icon.icon
import dev.quinton.goa.feature.players.common.LARGE_FONT_SCALE
import dev.quinton.goa.feature.players.common.LogMatchBar
import dev.quinton.goa.feature.players.common.Motion
import dev.quinton.goa.feature.players.match.MatchFollowUps
import dev.quinton.goa.feature.players.match.MatchLogContent
import dev.quinton.goa.feature.players.match.MatchLogUiState
import dev.quinton.goa.feature.players.match.UndoSnackbarHost
import dev.quinton.goa.feature.players.meta.MetaActions
import dev.quinton.goa.feature.players.meta.MetaContent
import dev.quinton.goa.feature.players.meta.MetaUiState

enum class PlayersTab(val label: String) { PLAYERS("Players"), MATCHES("Matches"), META("Meta") }

/** Callbacks of the Players home screen, grouped to keep signatures short. */
data class PlayersHomeActions(
    val onSelectTab: (PlayersTab) -> Unit = {},
    val onOpenPlayer: (String) -> Unit = {},
    val onAddPlayer: () -> Unit = {},
    val onToggleArchived: () -> Unit = {},
    val onOpenMatch: (String) -> Unit = {},
    val onLogMatch: () -> Unit = {},
    val onDeleteMatch: (String) -> Unit = {},
    val onUndoDelete: () -> Unit = {},
    val onDismissUndo: () -> Unit = {},
    val onOpenBackup: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onOpenRatingSettings: () -> Unit = {},
    /** Meta tab: share the stats page (ADR-027). */
    val onShareStats: () -> Unit = {},
    /** Group sync: the sync sheet when in a group, else the Group sync screen. */
    val onOpenSync: () -> Unit = {},
)

/** Top-level "Players" destination: leaderboard, match log and meta as tabs. */
@Composable
fun PlayersHomeScreen(
    tab: PlayersTab,
    players: PlayersUiState,
    matches: MatchLogUiState,
    meta: MetaUiState,
    actions: PlayersHomeActions,
    snackbarHostState: SnackbarHostState,
    followUps: MatchFollowUps = MatchFollowUps(),
    metaActions: MetaActions = MetaActions(onOpenRatingSettings = actions.onOpenRatingSettings),
    modifier: Modifier = Modifier,
) {
    GoaScreen(
        title = "Players",
        modifier = modifier,
        actions = {
            // Group sync stays in sight and says so in words (its glyph alone reads as "refresh"), with a quiet dot
            // when the server sync needs a person (wrong key, versions, refused data); the rest are labelled lines
            // in the overflow menu. At large text the word gives way to the glyph, labelled for TalkBack and on a
            // long press.
            val sync = BarAction(
                label = when {
                    players.serverNeedsAttention -> "Group sync: the sync server needs attention"
                    players.serverSyncOn -> "Group sync with the sync server"
                    players.inGroup -> "Group sync with the group"
                    else -> "Group sync"
                },
                icon = Destination.GROUP_SYNC.icon,
                onClick = actions.onOpenSync,
                badge = players.serverNeedsAttention,
            )
            val rest = listOfNotNull(
                players.meId?.let { me ->
                    BarAction("Your player page", Icons.Outlined.AccountCircle, { actions.onOpenPlayer(me) })
                },
                BarAction("Share stats page", Icons.Outlined.Share, actions.onShareStats)
                    .takeIf { tab == PlayersTab.META },
                BarAction("Backup and restore", Icons.Outlined.SettingsBackupRestore, actions.onOpenBackup),
                BarAction("Settings", Icons.Outlined.Settings, actions.onOpenSettings),
            )
            if (LocalDensity.current.fontScale < LARGE_FONT_SCALE) {
                BannerTextButton(
                    Destination.GROUP_SYNC.label,
                    onClick = sync.onClick,
                    icon = sync.icon,
                    badge = sync.badge,
                    modifier = Modifier.semantics { contentDescription = sync.label },
                )
                BarOverflowMenu(rest)
            } else {
                BarActions(listOf(sync) + rest)
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabBar(tab, actions.onSelectTab)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AnimatedContent(
                    targetState = tab,
                    transitionSpec = { fadeIn(Motion.spec()) togetherWith fadeOut(Motion.spec()) },
                    label = "players-tab",
                ) { current ->
                    when (current) {
                        PlayersTab.PLAYERS -> LeaderboardContent(
                            state = players,
                            onOpenPlayer = actions.onOpenPlayer,
                            onAddPlayer = actions.onAddPlayer,
                            onToggleArchived = actions.onToggleArchived,
                        )

                        PlayersTab.MATCHES -> MatchLogContent(
                            state = matches,
                            onOpenMatch = actions.onOpenMatch,
                            onDeleteMatch = actions.onDeleteMatch,
                            onLogMatch = actions.onLogMatch,
                            followUps = followUps,
                        )

                        PlayersTab.META -> MetaContent(meta, metaActions)
                    }
                }
                UndoSnackbarHost(
                    hostState = snackbarHostState,
                    undo = matches.undo,
                    onUndo = actions.onUndoDelete,
                    onDismiss = actions.onDismissUndo,
                )
            }
            // Docked under the list, never over it. The empty log carries its own "Log a match".
            if (tab == PlayersTab.MATCHES && !matches.loading && !matches.isEmpty) LogMatchBar(actions.onLogMatch)
        }
    }
}

/** Players · Matches · Meta. */
@Composable
private fun TabBar(tab: PlayersTab, onSelect: (PlayersTab) -> Unit) {
    // The app's one page-tab bar, as on a hero page and the trivia leaderboard.
    GoaTabRow(PlayersTab.entries.map { it.label }, tab.ordinal, onSelect = { onSelect(PlayersTab.entries[it]) })
}
