package dev.quinton.goa

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.quinton.goa.core.domain.timer.TimerStatus
import dev.quinton.goa.core.presentation.timer.RunningTimerUi
import dev.quinton.goa.feature.timer.session.TimerController
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Whether a timer session is in play, for the app shell (no ticking). */
enum class SessionState {
    /** Not read from storage yet. */
    LOADING,

    /** No session, or an ended one waiting on its summary. */
    NONE,
    RUNNING,
    PAUSED,
    ;

    val active: Boolean get() = this == RUNNING || this == PAUSED
}

/**
 * App-shell state: whether a timer session runs (to open the Timer tab on a cold start, hide
 * the bottom bar on the running timer, keep the screen awake on other tabs) and the ticking
 * readout for the "timer running" bar. Reads the timer's own controller; changes nothing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ShellViewModel @Inject constructor(private val controller: TimerController) : ViewModel() {
    val session: StateFlow<SessionState> = controller.snapshot
        .map { s ->
            when (s?.status) {
                TimerStatus.RUNNING -> SessionState.RUNNING
                TimerStatus.PAUSED -> SessionState.PAUSED
                else -> SessionState.NONE
            }
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, SessionState.LOADING)

    /** Ticks only while collected (the bar is on screen). */
    val runningTimer: Flow<RunningTimerUi?> = controller.snapshot.flatMapLatest { s ->
        if (s == null || s.status == TimerStatus.ENDED) {
            flowOf<RunningTimerUi?>(null)
        } else {
            flow<RunningTimerUi?> {
                while (true) {
                    emit(RunningTimerUi.from(s, controller.readout(s)))
                    if (s.status == TimerStatus.PAUSED) break
                    delay(TICK_MS)
                }
            }
        }
    }.distinctUntilChanged()

    private companion object {
        const val TICK_MS = 250L
    }
}
