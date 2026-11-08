package dev.quinton.goa.feature.board.online

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.PersistableBundle
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.quinton.goa.core.navigation.Navigator
import dev.quinton.goa.core.navigation.OnlineGameRoute
import dev.quinton.goa.core.navigation.OnlineHomeRoute
import dev.quinton.goa.core.navigation.OnlineLeaderboardRoute
import dev.quinton.goa.core.navigation.OnlineLobbyRoute
import kotlinx.coroutines.launch

/** Online games and leaderboards (ADR-056): the home, a room's lobby, a game, the leaderboards. */
internal fun EntryProviderScope<NavKey>.onlineEntries(navigator: Navigator) {
    entry<OnlineHomeRoute> { key -> OnlineHomeEntry(key, navigator) }
    lobbyEntry(navigator)
    entry<OnlineGameRoute> { key ->
        val viewModel = hiltViewModel<OnlineGameViewModel, OnlineGameViewModel.Factory>(
            creationCallback = { it.create(key) },
        )
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        OnlineResumeEffect(viewModel::resumeNow)
        OnlineGameScreen(
            state = state,
            onAction = viewModel::onAction,
            onTap = viewModel::tap,
            onLeave = {
                // Sent before the screen goes: leaving this screen is what closes the socket.
                viewModel.leave()
                navigator.back()
            },
            onDone = navigator::back,
            onBack = navigator::back,
        )
    }
    entry<OnlineLeaderboardRoute> {
        val viewModel = hiltViewModel<OnlineLeaderboardViewModel>()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        val portraits by viewModel.portraits.collectAsStateWithLifecycle()
        OnlineLeaderboardScreen(
            state = state,
            onTab = viewModel::selectTab,
            onRefresh = viewModel::refresh,
            onBack = navigator::back,
            portraits = portraits,
        )
    }
}

@Composable
private fun OnlineHomeEntry(key: OnlineHomeRoute, navigator: Navigator) {
    val viewModel = hiltViewModel<OnlineHomeViewModel, OnlineHomeViewModel.Factory>(
        creationCallback = { it.create(key) },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val mapNames by viewModel.mapNames.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    // A room just made or joined opens once.
    LaunchedEffect(state.openRoom) {
        val room = state.openRoom ?: return@LaunchedEffect
        viewModel.roomOpened()
        navigator.navigate(OnlineLobbyRoute(room.code))
    }
    // Back from a room or a game: what is running may have changed.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshGames()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val heroes by viewModel.heroes.collectAsStateWithLifecycle()
    OnlineHomeScreen(
        state = state,
        mapNames = mapNames,
        heroes = heroes,
        actions = OnlineHomeActions(
            onName = viewModel::setName,
            onCreateAccount = viewModel::createAccount,
            onLinkCode = viewModel::setLinkCode,
            onLink = viewModel::linkDevice,
            onCodeSaved = viewModel::recoveryCodeSaved,
            onCopyCode = { copySecret(context, it) },
            onMap = viewModel::setMap,
            onLength = viewModel::setLength,
            onTeamSize = viewModel::setTeamSize,
            onRanked = viewModel::setRanked,
            onCreateRoom = viewModel::createRoom,
            onJoinText = viewModel::setJoinText,
            onJoin = { viewModel.join() },
            onScan = {
                activity?.let { host ->
                    scope.launch {
                        when (val result = scanRoomInvite(host)) {
                            is RoomScan.Scanned -> viewModel.join(result.text)
                            RoomScan.Unavailable -> navigator.showMessage(SCANNER_UNAVAILABLE)
                            RoomScan.Cancelled -> Unit
                        }
                    }
                }
            },
            onGame = { navigator.navigate(OnlineGameRoute(it.gameId)) },
            onLeaderboard = { navigator.navigate(OnlineLeaderboardRoute) },
            onRename = viewModel::rename,
            onDelete = viewModel::deleteAccount,
            onBack = navigator::back,
        ),
    )
}

private fun EntryProviderScope<NavKey>.lobbyEntry(navigator: Navigator) {
    entry<OnlineLobbyRoute> { key ->
        val viewModel = hiltViewModel<OnlineLobbyViewModel, OnlineLobbyViewModel.Factory>(
            creationCallback = { it.create(key) },
        )
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        val heroes by viewModel.heroes.collectAsStateWithLifecycle()
        val mapNames by viewModel.mapNames.collectAsStateWithLifecycle()
        val context = LocalContext.current
        // The game replaces the lobby: Back from it returns to the online home.
        LaunchedEffect(state.gameId) {
            val gameId = state.gameId ?: return@LaunchedEffect
            navigator.back()
            navigator.navigate(OnlineGameRoute(gameId))
        }
        LaunchedEffect(state.left) {
            if (state.left) {
                state.message?.let { navigator.showMessage(it) }
                navigator.back()
            }
        }
        OnlineLobbyScreen(
            state = state,
            heroes = heroes,
            mapNames = mapNames,
            actions = OnlineLobbyActions(
                onSeat = viewModel::pickSeat,
                onHero = viewModel::pickHero,
                onReady = viewModel::setReady,
                onStart = viewModel::startGame,
                onShare = { text ->
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                    context.startActivity(Intent.createChooser(send, "Share invite"))
                },
                onRetry = viewModel::retry,
                onLeave = viewModel::leave,
                onBack = navigator::back,
            ),
        )
    }
}

/** The recovery code on the clipboard, marked so that Android 13+ doesn't show it in its preview. */
@SuppressLint("InlinedApi") // older versions ignore the extra
private fun copySecret(context: Context, text: String) {
    val clip = ClipData.newPlainText("Recovery code", text)
    clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(clip)
}
