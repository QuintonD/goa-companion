package dev.quinton.goa.feature.timer.ui.timer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.quinton.goa.core.designsystem.component.GoaScreen

/** Callbacks from the timer screens; stateless composables take this instead of a ViewModel. */
data class TimerActions(
    val onSelectPreset: (String) -> Unit = {},
    val onSeatsChange: (Int) -> Unit = {},
    val onStart: () -> Unit = {},
    val onOpenSetup: (matchId: String?) -> Unit = {},
    val onManagePresets: () -> Unit = {},
    val onSettings: () -> Unit = {},
    val onTogglePause: () -> Unit = {},
    val onNext: () -> Unit = {},
    val onBack: () -> Unit = {},
    val onRestart: () -> Unit = {},
    val onSkipToEnd: () -> Unit = {},
    val onEndMatch: () -> Unit = {},
    val onFlipCoin: () -> Unit = {},
    val onHeroResolves: (Int) -> Unit = {},
    val onLogResult: () -> Unit = {},
    val onDone: () -> Unit = {},
)

@Composable
fun TimerRoute(
    viewModel: TimerViewModel,
    onOpenSetup: (String?) -> Unit,
    onManagePresets: () -> Unit,
    onSettings: () -> Unit,
    onOpenMatch: (String) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleStartEffect(viewModel) {
        viewModel.setVisible(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        onStopOrDispose { viewModel.setVisible(false) }
    }
    LaunchedEffect(viewModel) {
        viewModel.navigation.collect { event ->
            when (event) {
                is TimerEvent.OpenMatch -> onOpenMatch(event.matchId)
            }
        }
    }
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        // Start either way; without the permission the timer still works in-app.
        viewModel.start()
    }
    val start = {
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsPermission) permission.launch(Manifest.permission.POST_NOTIFICATIONS) else viewModel.start()
    }
    TimerScreen(
        state = state,
        actions = TimerActions(
            onSelectPreset = viewModel::selectPreset,
            onSeatsChange = viewModel::setSeats,
            onStart = start,
            onOpenSetup = onOpenSetup,
            onManagePresets = onManagePresets,
            onSettings = onSettings,
            onTogglePause = viewModel::togglePause,
            onNext = viewModel::next,
            onBack = viewModel::back,
            onRestart = viewModel::restart,
            onSkipToEnd = viewModel::skipToEndOfRound,
            onEndMatch = viewModel::endMatch,
            onFlipCoin = viewModel::flipTieBreaker,
            onHeroResolves = viewModel::heroResolves,
            onLogResult = viewModel::logResult,
            onDone = viewModel::dismissSummary,
        ),
    )
}

@Composable
fun TimerScreen(state: TimerUiState, actions: TimerActions) {
    when {
        state.active != null -> ActiveTimer(state.active, actions)
        state.summary != null -> MatchSummary(state.summary, actions)
        state.start != null -> TimerStart(state.start, actions)
        else -> GoaScreen(title = "Timer") { }
    }
}
