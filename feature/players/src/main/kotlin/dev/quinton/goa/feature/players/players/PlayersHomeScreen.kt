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
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.quinton.goa.core.designsystem.component.GoaScreen
import dev.quinton.goa.core.designsystem.component.SettingsActionButton
import dev.quinton.goa.feature.players.common.Motion
import dev.quinton.goa.feature.players.match.MatchLogContent
import dev.quinton.goa.feature.players.match.MatchLogUiState
import dev.quinton.goa.feature.players.match.UndoSnackbarHost
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
                PlayersTab.PLAYERS -> ExtendedFloatingActionButton(
                    onClick = actions.onAddPlayer,
                    icon = { Icon(Icons.Outlined.PersonAdd, contentDescription = null) },
                    text = { Text("Add player") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )

                PlayersTab.MATCHES -> if (matches.undo == null) {
                    ExtendedFloatingActionButton(
                        onClick = actions.onLogMatch,
                        icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                        text = { Text("Log match") },
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
                PrimaryTabRow(
                    selectedTabIndex = tab.ordinal,
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.primary,
                ) {
                    PlayersTab.entries.forEach { t ->
                        Tab(
                            selected = t == tab,
                            onClick = { actions.onSelectTab(t) },
                            text = { Text(t.label, style = MaterialTheme.typography.titleSmall) },
                            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
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
                        )

                        PlayersTab.META -> MetaContent(meta, onOpenRatingSettings = actions.onOpenRatingSettings)
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
