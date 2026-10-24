package dev.quinton.goa.feature.sync

import android.content.Intent
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.quinton.goa.core.domain.sync.GroupInvite
import dev.quinton.goa.core.navigation.GroupSyncRoute
import dev.quinton.goa.core.navigation.NavSheet
import dev.quinton.goa.core.navigation.Navigator
import dev.quinton.goa.core.navigation.SyncFileImportRoute
import dev.quinton.goa.core.navigation.SyncSessionRoute
import dev.quinton.goa.core.navigation.WhoAreYouRoute
import dev.quinton.goa.feature.sync.group.GroupActions
import dev.quinton.goa.feature.sync.group.GroupEvent
import dev.quinton.goa.feature.sync.group.GroupScreen
import dev.quinton.goa.feature.sync.group.GroupViewModel
import dev.quinton.goa.feature.sync.importfile.SyncFileImportActions
import dev.quinton.goa.feature.sync.importfile.SyncFileImportScreen
import dev.quinton.goa.feature.sync.importfile.SyncFileImportViewModel
import dev.quinton.goa.feature.sync.invite.InviteScanner
import dev.quinton.goa.feature.sync.invite.ScanResult
import dev.quinton.goa.feature.sync.me.WhoAreYouActions
import dev.quinton.goa.feature.sync.me.WhoAreYouScreen
import dev.quinton.goa.feature.sync.me.WhoAreYouViewModel
import dev.quinton.goa.feature.sync.server.SyncServerActions
import dev.quinton.goa.feature.sync.session.SyncSessionActions
import dev.quinton.goa.feature.sync.session.SyncSessionEntry
import dev.quinton.goa.feature.sync.session.SyncSessionViewModel
import dev.quinton.goa.feature.sync.share.SyncFiles
import kotlinx.coroutines.launch

/** Group sync (ADR-025): the Group screen, the sync sheet, "Which player are you?" and file import. */
fun EntryProviderScope<NavKey>.syncEntries(navigator: Navigator) {
    entry<GroupSyncRoute> { GroupEntry(navigator) }
    entry<SyncSessionRoute>(metadata = NavSheet.metadata) { key ->
        val vm = hiltViewModel<SyncSessionViewModel, SyncSessionViewModel.Factory>(
            creationCallback = { it.create(key.role, key.firstSync) },
        )
        val state by vm.uiState.collectAsStateWithLifecycle()
        val toGroup = {
            navigator.back()
            if (!key.fromGroup) navigator.navigate(GroupSyncRoute)
        }
        SyncSessionEntry(
            state = state,
            viewModel = vm,
            actions = SyncSessionActions(
                onCancel = navigator::back,
                onClose = navigator::back,
                onWhoAreYou = {
                    navigator.back()
                    navigator.navigate(WhoAreYouRoute)
                },
                onUseFile = toGroup,
                onOpenGroup = toGroup,
            ),
        )
    }
    entry<WhoAreYouRoute> {
        val vm = hiltViewModel<WhoAreYouViewModel>()
        val state by vm.uiState.collectAsStateWithLifecycle()
        LaunchedEffect(state.done) { if (state.done) navigator.back() }
        WhoAreYouScreen(
            state = state,
            actions = WhoAreYouActions(
                onBack = navigator::back,
                onChoose = vm::choose,
                onNameChange = vm::onNameChange,
                onCreate = vm::createMe,
            ),
        )
    }
    entry<SyncFileImportRoute> { key ->
        val vm = hiltViewModel<SyncFileImportViewModel, SyncFileImportViewModel.Factory>(
            creationCallback = { it.create(key.uri) },
        )
        val state by vm.uiState.collectAsStateWithLifecycle()
        SyncFileImportScreen(
            state = state,
            actions = SyncFileImportActions(
                onBack = navigator::back,
                onConfirm = vm::confirm,
                onOpenGroup = {
                    navigator.back()
                    navigator.navigate(GroupSyncRoute)
                },
            ),
        )
    }
}

@Composable
private fun GroupEntry(navigator: Navigator) {
    val vm = hiltViewModel<GroupViewModel>()
    val state by vm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) {
        vm.navigation.collect { event ->
            when (event) {
                GroupEvent.OpenFirstSync -> navigator.navigate(
                    SyncSessionRoute(role = "join", firstSync = true, fromGroup = true),
                )

                is GroupEvent.Share -> context.startActivity(
                    SyncFiles.shareIntent(context, event.bytes, event.fileName, event.title),
                )
            }
        }
    }
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbar.showSnackbar(message)
        vm.messageShown()
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { navigator.navigate(SyncFileImportRoute(it.toString())) }
    }
    GroupScreen(
        state = state,
        snackbarHostState = snackbar,
        actions = GroupActions(
            onBack = navigator::back,
            onStart = vm::start,
            onScan = {
                val host = activity ?: return@GroupActions
                scope.launch {
                    when (val result = InviteScanner.scan(host)) {
                        is ScanResult.Scanned -> vm.join(result.text)
                        is ScanResult.Unavailable -> vm.scanFailed(result.message)
                        ScanResult.Cancelled -> Unit
                    }
                }
            },
            onPaste = vm::join,
            onClearJoinError = vm::clearJoinError,
            onSyncNow = {
                if (state.server.active) vm.syncWithServer() else navigator.navigate(SyncSessionRoute(fromGroup = true))
            },
            onSyncAtTable = { navigator.navigate(SyncSessionRoute(fromGroup = true)) },
            onWhoAreYou = { navigator.navigate(WhoAreYouRoute) },
            onShareFile = vm::shareFile,
            // Some providers report the file as octet-stream or nothing useful, so accept any file.
            onImportFile = { importLauncher.launch(arrayOf("*/*")) },
            onShareInvite = { invite ->
                // With what to do with it: a bare goa-sync: link means nothing in a chat.
                val text = GroupInvite.parseInvite(invite).getOrNull()
                    ?.let { GroupInvite.shareText(it.group, it.serverUrl) } ?: invite
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                context.startActivity(Intent.createChooser(send, "Share invite"))
            },
            onLeave = vm::leave,
            server = SyncServerActions(
                onEnabled = vm::setServerSyncEnabled,
                onSaveAddress = vm::setServerAddress,
                onUseDefault = vm::useDefaultServer,
                onClearAddressError = vm::clearAddressError,
            ),
        ),
    )
}
