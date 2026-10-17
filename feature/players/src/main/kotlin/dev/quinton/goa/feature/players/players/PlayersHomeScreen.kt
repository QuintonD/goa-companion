package dev.quinton.goa.feature.players.players

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.quinton.goa.core.designsystem.component.GoaScreen
import dev.quinton.goa.core.designsystem.component.SettingsActionButton
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.feature.players.common.LARGE_FONT_SCALE
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
            IconButton(onClick = actions.onOpenBackup) {
                Icon(Icons.Outlined.SettingsBackupRestore, contentDescription = "Backup and restore")
            }
            SettingsActionButton(actions.onOpenSettings)
        },
        floatingActionButton = {
            when (tab) {
                // The empty state carries its own "Add player" call to action; don't show two.
                PlayersTab.PLAYERS -> if (!players.isEmpty) {
                    ExtendedFloatingActionButton(
                        onClick = actions.onAddPlayer,
                        icon = { Icon(Icons.Outlined.PersonAdd, contentDescription = null) },
                        text = { Text("Add player") },
                        // M3 1.4 leaves the extended FAB's text out of the semantics tree.
                        modifier = Modifier.semantics { contentDescription = "Add player" },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    )
                }

                PlayersTab.MATCHES -> if (matches.undo == null && !matches.isEmpty) {
                    ExtendedFloatingActionButton(
                        onClick = actions.onLogMatch,
                        icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                        text = { Text("Log match") },
                        modifier = Modifier.semantics { contentDescription = "Log match" },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    )
                }

                PlayersTab.META -> Unit
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxSize()) {
                TabBar(tab, actions.onSelectTab)
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
            }
            UndoSnackbarHost(
                hostState = snackbarHostState,
                undo = matches.undo,
                onUndo = actions.onUndoDelete,
                onDismiss = actions.onDismissUndo,
            )
        }
    }
}

/** Fixed tabs; scrollable at large font scales so labels never break mid-word. */
@Composable
private fun TabBar(tab: PlayersTab, onSelect: (PlayersTab) -> Unit) {
    val tabs: @Composable () -> Unit = {
        PlayersTab.entries.forEach { t ->
            Tab(
                selected = t == tab,
                onClick = { onSelect(t) },
                text = { Text(t.label, style = MaterialTheme.typography.titleSmall, maxLines = 1) },
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (LocalDensity.current.fontScale >= LARGE_FONT_SCALE) {
        PrimaryScrollableTabRow(
            selectedTabIndex = tab.ordinal,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.primary,
            edgePadding = Spacing.screen,
            tabs = tabs,
        )
    } else {
        PrimaryTabRow(
            selectedTabIndex = tab.ordinal,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.primary,
            tabs = tabs,
        )
    }
}
