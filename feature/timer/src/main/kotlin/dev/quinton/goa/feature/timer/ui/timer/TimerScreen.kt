package dev.quinton.goa.feature.timer.ui.timer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.quinton.goa.core.designsystem.component.GoaScreen
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.timer.ui.components.notificationSettingsIntent
import dev.quinton.goa.feature.timer.ui.components.rememberStartWithNotificationPermission

/** Callbacks from the timer screens; stateless composables take this instead of a ViewModel. */
data class TimerActions(
    val onSelectPreset: (String) -> Unit = {},
    val onSeatsChange: (Int) -> Unit = {},
    val onStart: () -> Unit = {},
    val onOpenSetup: (matchId: String?) -> Unit = {},
    /** Deletes a pending match nobody is going to play. */
    val onDiscardPending: (matchId: String) -> Unit = {},
    val onManagePresets: () -> Unit = {},
    val onSettings: () -> Unit = {},
    val onTogglePause: () -> Unit = {},
    val onNext: () -> Unit = {},
    val onBack: () -> Unit = {},
    val onRestart: () -> Unit = {},
    val onSkipToEnd: () -> Unit = {},
    val onEndMatch: () -> Unit = {},
    val onFlipCoin: () -> Unit = {},
    val onSetCoin: (Team?) -> Unit = {},
    val onHeroResolves: (Int) -> Unit = {},
    val onShotClock: () -> Unit = {},
    val onCancelShotClock: () -> Unit = {},
    val onTimeOut: (Team) -> Unit = {},
    val onTonightTurn: (Int) -> Unit = {},
    val onSaveTonight: () -> Unit = {},
    val onLogResult: () -> Unit = {},
    val onDone: () -> Unit = {},
    val onOpenNotificationSettings: () -> Unit = {},
    val onDismissAlertsBanner: () -> Unit = {},
    /** Level & defeat ladder (rules reference) for the heroes in play. */
    val onOpenLevels: () -> Unit = {},
    val onOpenRules: () -> Unit = {},
)

@Composable
fun TimerRoute(
    viewModel: TimerViewModel,
    onOpenSetup: (String?) -> Unit,
    onManagePresets: () -> Unit,
    onSettings: () -> Unit,
    onOpenMatch: (matchId: String, startedAt: Long?) -> Unit,
    onNewMatch: (durationMin: Int, mode: String?, startedAt: Long?) -> Unit,
    onOpenLevels: (heroIds: List<String>) -> Unit = {},
    onOpenRules: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleStartEffect(viewModel) {
        viewModel.setVisible(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        onStopOrDispose { viewModel.setVisible(false) }
    }
    LaunchedEffect(viewModel) {
        viewModel.navigation.collect { event ->
            when (event) {
                is TimerEvent.OpenMatch -> onOpenMatch(event.matchId, event.startedAt)
                is TimerEvent.NewMatch -> onNewMatch(event.durationMin, event.mode, event.startedAt)
            }
        }
    }
    val context = LocalContext.current
    val start = rememberStartWithNotificationPermission(viewModel::start)
    TimerScreen(
        state = state,
        actions = TimerActions(
            onSelectPreset = viewModel::selectPreset,
            onSeatsChange = viewModel::setSeats,
            onStart = start,
            onOpenSetup = onOpenSetup,
            onDiscardPending = viewModel::discardPending,
            onManagePresets = onManagePresets,
            onSettings = onSettings,
            onTogglePause = viewModel::togglePause,
            onNext = viewModel::next,
            onBack = viewModel::back,
            onRestart = viewModel::restart,
            onSkipToEnd = viewModel::skipToEndOfRound,
            onEndMatch = viewModel::endMatch,
            onFlipCoin = viewModel::flipTieBreaker,
            onSetCoin = viewModel::setTieBreaker,
            onHeroResolves = viewModel::heroResolves,
            onShotClock = viewModel::startShotClock,
            onCancelShotClock = viewModel::cancelShotClock,
            onTimeOut = viewModel::timeOut,
            onTonightTurn = viewModel::setTonightTurn,
            onSaveTonight = viewModel::saveTonightToPreset,
            onLogResult = viewModel::logResult,
            onDone = viewModel::dismissSummary,
            onOpenNotificationSettings = {
                runCatching { context.startActivity(notificationSettingsIntent(context)) }
            },
            onDismissAlertsBanner = viewModel::dismissAlertsBanner,
            onOpenLevels = { onOpenLevels(state.active?.heroIds.orEmpty()) },
            onOpenRules = onOpenRules,
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
