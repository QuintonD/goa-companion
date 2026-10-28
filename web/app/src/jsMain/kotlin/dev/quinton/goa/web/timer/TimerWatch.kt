package dev.quinton.goa.web.timer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import dev.quinton.goa.core.domain.timer.TimerSnapshot
import dev.quinton.goa.core.domain.timer.TimerStatus
import dev.quinton.goa.core.model.settings.AppSettings
import dev.quinton.goa.feature.timer.TimerText
import dev.quinton.goa.feature.timer.session.TimerCommand
import dev.quinton.goa.web.LocalServices
import dev.quinton.goa.web.nav.Route
import kotlinx.browser.document
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.w3c.dom.events.Event

/** The "time's up" alert: the phone's notification title and line ([TimerText.timesUpTitle]). */
data class TimesUp(val title: String, val text: String)

/**
 * The running timer while its screen isn't showing: the phone's phase-end alarm
 * (`TimerController.onAlarm`). The timer's own buttons (Levels and defeats, Rules, Settings) and the
 * other tabs leave the timer screen while a phase runs; when that phase runs out, the preset's
 * auto-advance moves on and the table hears and sees "time's up" once, with the phone's words. On the
 * timer screen [TimerModel] plays every cue itself, as the phone's screen does.
 */
class TimerWatch(private val controller: WebTimerController) {
    private var alertedKey: String? = null

    /** Looks at the session once: the alert for a phase that has just run out (auto-advanced first), else null. */
    suspend fun check(): TimesUp? {
        val s = controller.snapshot.first() ?: return null
        val key = overdueKey(s) ?: return null
        if (key == alertedKey) return null
        alertedKey = key
        val after = if (s.preset.autoAdvance) controller.perform(TimerCommand.AdvanceDue) ?: s else s
        return TimesUp(TimerText.timesUpTitle(TimerText.endedTitle(s.step, s.round)), TimerText.timesUpText(after))
    }

    /**
     * Leaving the timer screen: a phase already in overtime there was cued there, so it doesn't alert
     * again; the next one to run out does.
     */
    suspend fun arm() {
        alertedKey = controller.snapshot.first()?.let(::overdueKey)
    }

    /** The running step's key once its time is up, else null. */
    private fun overdueKey(s: TimerSnapshot): String? {
        if (s.status != TimerStatus.RUNNING) return null
        val remaining = controller.readout(s).remainingMillis ?: return null
        if (remaining > 0) return null
        return "${s.sessionId}:${s.round}:${s.stepIndex}:${(s.runningSince ?: 0) - s.accumulatedMillis}"
    }
}

/**
 * The app shell's half of the timer, on every screen: the phase-end watch away from the timer
 * screen ([onTimerScreen] false; [sessionInPlay]: a session runs or is paused) and, back on the page
 * after it was hidden (a locked phone, another app), catching up with the clock and saying so.
 */
@Composable
fun TimerBackground(onTimerScreen: Boolean, sessionInPlay: Boolean) {
    val services = LocalServices.current
    val table = services.data.table
    val scope = rememberCoroutineScope()
    DisposableEffect(table) {
        val listener: (Event) -> Unit = {
            if (document.asDynamic().visibilityState == "visible") {
                scope.launch {
                    if (table.timer.resync()) services.messages.show("A phase ended while the screen was off.")
                }
            }
        }
        document.addEventListener("visibilitychange", listener)
        onDispose { document.removeEventListener("visibilitychange", listener) }
    }
    LaunchedEffect(onTimerScreen, sessionInPlay) {
        if (onTimerScreen || !sessionInPlay) return@LaunchedEffect
        val watch = table.timerWatch
        watch.arm()
        while (true) {
            watch.check()?.let { alert ->
                val prefs = runCatching { services.data.content.settings.settings.first() }.getOrElse { AppSettings() }
                table.cues.cue(0, sound = prefs.soundsEnabled, flash = prefs.hapticsEnabled)
                services.messages.show("${alert.title}. ${alert.text}", "Open timer") {
                    services.router.navigate(Route.Timer)
                }
            }
            delay(WATCH_MS)
        }
    }
    // The cues (and their audio unlock) exist only once a timer is in play. Only flashes from here on:
    // the timer screen's earlier cues don't replay on leaving it.
    if (sessionInPlay && !onTimerScreen) {
        val cues = table.cues
        val shownFrom = remember { cues.flashes }
        if (cues.flashes > shownFrom) Flash(cues.flashes, cues.finalFlash)
    }
}

private const val WATCH_MS = 250L
