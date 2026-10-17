package dev.quinton.goa.feature.players

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.quinton.goa.core.data.backup.BackupDocument
import dev.quinton.goa.core.navigation.BackupRoute
import dev.quinton.goa.core.navigation.MatchEditRoute
import dev.quinton.goa.core.navigation.MatchLogRoute
import dev.quinton.goa.core.navigation.MetaRoute
import dev.quinton.goa.core.navigation.Navigator
import dev.quinton.goa.core.navigation.PlayerDetailRoute
import dev.quinton.goa.core.navigation.PlayerEditRoute
import dev.quinton.goa.core.navigation.PlayersRoute
import dev.quinton.goa.core.navigation.RatingSettingsRoute
import dev.quinton.goa.core.navigation.SettingsRoute
import dev.quinton.goa.feature.players.backup.BackupActions
import dev.quinton.goa.feature.players.backup.BackupScreen
import dev.quinton.goa.feature.players.backup.BackupViewModel
import dev.quinton.goa.feature.players.match.MatchEditActions
import dev.quinton.goa.feature.players.match.MatchEditScreen
import dev.quinton.goa.feature.players.match.MatchEditViewModel
import dev.quinton.goa.feature.players.match.MatchLogViewModel
import dev.quinton.goa.feature.players.meta.MetaViewModel
import dev.quinton.goa.feature.players.player.PlayerDetailActions
import dev.quinton.goa.feature.players.player.PlayerDetailScreen
import dev.quinton.goa.feature.players.player.PlayerDetailViewModel
import dev.quinton.goa.feature.players.player.PlayerEditActions
import dev.quinton.goa.feature.players.player.PlayerEditScreen
import dev.quinton.goa.feature.players.player.PlayerEditViewModel
import dev.quinton.goa.feature.players.players.PlayersHomeActions
import dev.quinton.goa.feature.players.players.PlayersHomeScreen
import dev.quinton.goa.feature.players.players.PlayersTab
import dev.quinton.goa.feature.players.players.PlayersViewModel
import dev.quinton.goa.feature.players.ratings.RatingSettingsActions
import dev.quinton.goa.feature.players.ratings.RatingSettingsScreen
import dev.quinton.goa.feature.players.ratings.RatingSettingsViewModel

fun EntryProviderScope<NavKey>.playersEntries(navigator: Navigator) {
    entry<PlayersRoute> {
        val playersVm = hiltViewModel<PlayersViewModel>()
        val matchesVm = hiltViewModel<MatchLogViewModel>()
        val metaVm = hiltViewModel<MetaViewModel>()
        val players by playersVm.uiState.collectAsStateWithLifecycle()
        val matches by matchesVm.uiState.collectAsStateWithLifecycle()
        val meta by metaVm.uiState.collectAsStateWithLifecycle()
        var tab by rememberSaveable { mutableStateOf(PlayersTab.PLAYERS) }
        val snackbar = remember { SnackbarHostState() }
        PlayersHomeScreen(
            tab = tab,
            players = players,
            matches = matches,
            meta = meta,
            snackbarHostState = snackbar,
            actions = PlayersHomeActions(
                onSelectTab = { tab = it },
                onOpenPlayer = { navigator.navigate(PlayerDetailRoute(it)) },
                onAddPlayer = { navigator.navigate(PlayerEditRoute()) },
                onToggleArchived = playersVm::toggleArchived,
                onOpenMatch = { navigator.navigate(MatchEditRoute(it)) },
                onLogMatch = { navigator.navigate(MatchEditRoute()) },
                onDeleteMatch = matchesVm::delete,
                onUndoDelete = matchesVm::undoDelete,
                onDismissUndo = matchesVm::dismissUndo,
                onOpenBackup = { navigator.navigate(BackupRoute) },
                onOpenSettings = { navigator.navigate(SettingsRoute) },
                onOpenRatingSettings = { navigator.navigate(RatingSettingsRoute) },
            ),
        )
    }
    entry<PlayerDetailRoute> { key ->
        val vm = hiltViewModel<PlayerDetailViewModel, PlayerDetailViewModel.Factory>(
            creationCallback = { it.create(key.playerId) },
        )
        val state by vm.uiState.collectAsStateWithLifecycle()
        PlayerDetailScreen(
            state = state,
            actions = PlayerDetailActions(
                onBack = navigator::back,
                onEdit = { navigator.navigate(PlayerEditRoute(key.playerId)) },
                onOpenMatch = { navigator.navigate(MatchEditRoute(it)) },
                onOpenMatchLog = { navigator.navigate(MatchLogRoute) },
                onSetArchived = vm::setArchived,
            ),
        )
    }
    entry<PlayerEditRoute> { key ->
        val vm = hiltViewModel<PlayerEditViewModel, PlayerEditViewModel.Factory>(
            creationCallback = { it.create(key.playerId) },
        )
        val state by vm.uiState.collectAsStateWithLifecycle()
        PlayerEditScreen(
            state = state,
            actions = PlayerEditActions(
                onBack = navigator::back,
                onNameChange = vm::onNameChange,
                onColorChange = vm::onColorChange,
                onAvatarChange = vm::onAvatarChange,
                onSave = vm::save,
            ),
        )
    }
    entry<MatchLogRoute> {
        val vm = hiltViewModel<MatchLogViewModel>()
        val state by vm.uiState.collectAsStateWithLifecycle()
        val snackbar = remember { SnackbarHostState() }
        MatchLogScreen(
            state = state,
            snackbarHostState = snackbar,
            onBack = navigator::back,
            onOpenMatch = { navigator.navigate(MatchEditRoute(it)) },
            onLogMatch = { navigator.navigate(MatchEditRoute()) },
            onDeleteMatch = vm::delete,
            onUndoDelete = vm::undoDelete,
            onDismissUndo = vm::dismissUndo,
        )
    }
    entry<MatchEditRoute> { key ->
        val vm = hiltViewModel<MatchEditViewModel, MatchEditViewModel.Factory>(
            creationCallback = { it.create(key.matchId) },
        )
        val state by vm.uiState.collectAsStateWithLifecycle()
        MatchEditScreen(
            state = state,
            actions = MatchEditActions(
                onBack = navigator::back,
                onPlayedAt = vm::setPlayedAt,
                onMode = vm::setMode,
                onMap = vm::setMap,
                onStatus = vm::setStatus,
                onWinner = vm::setWinner,
                onWinCondition = vm::setWinCondition,
                onDuration = vm::setDuration,
                onNotes = vm::setNotes,
                onAddSeat = vm::addSeat,
                onRemoveSeat = vm::removeSeat,
                onPilot = vm::setPilot,
                onHero = vm::setHero,
                onToggleHandicap = vm::toggleHandicap,
                onSwapTeams = vm::swapTeams,
                onSave = vm::save,
                onDelete = vm::delete,
            ),
        )
    }
    entry<MetaRoute> {
        val vm = hiltViewModel<MetaViewModel>()
        val state by vm.uiState.collectAsStateWithLifecycle()
        MetaScreen(state, onBack = navigator::back, onOpenRatingSettings = { navigator.navigate(RatingSettingsRoute) })
    }
    entry<BackupRoute> {
        val vm = hiltViewModel<BackupViewModel>()
        val state by vm.uiState.collectAsStateWithLifecycle()
        val resolver = LocalContext.current.contentResolver
        val exportLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument(BackupDocument.MIME_TYPE),
        ) { uri -> uri?.let { vm.export { resolver.openOutputStream(it, "wt") } } }
        val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { vm.read { resolver.openInputStream(it) } }
        }
        BackupScreen(
            state = state,
            actions = BackupActions(
                onBack = navigator::back,
                onExport = { exportLauncher.launch(vm.suggestedFileName()) },
                // Some file providers report JSON as text/plain or octet-stream.
                onImport = { importLauncher.launch(arrayOf(BackupDocument.MIME_TYPE, "text/plain", "application/octet-stream")) },
                onConfirmImport = vm::confirmImport,
                onCancelImport = vm::cancelImport,
                onMessageShown = vm::messageShown,
            ),
        )
    }
    entry<RatingSettingsRoute> {
        val vm = hiltViewModel<RatingSettingsViewModel>()
        val state by vm.uiState.collectAsStateWithLifecycle()
        RatingSettingsScreen(
            state = state,
            actions = RatingSettingsActions(onBack = navigator::back, onUpdate = vm::update, onReset = vm::reset),
        )
    }
}
