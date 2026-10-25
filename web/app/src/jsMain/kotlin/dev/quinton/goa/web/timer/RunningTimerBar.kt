package dev.quinton.goa.web.timer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.quinton.goa.core.domain.timer.TimerSnapshot
import dev.quinton.goa.core.domain.timer.TimerStatus
import dev.quinton.goa.feature.timer.ui.timer.RunningTimerUi
import dev.quinton.goa.web.LocalServices
import dev.quinton.goa.web.nav.Route
import dev.quinton.goa.web.ui.Icon
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.jetbrains.compose.web.attributes.ButtonType
import org.jetbrains.compose.web.attributes.type
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

/**
 * The app shell's view of the timer (the phone's `ShellViewModel`): whether a session is in play, so the
 * running timer hides the tab bar and the other screens show the "timer running" bar.
 */
object TimerShell {
    /**
     * The session that is running or paused (not ended), or null. On the app's first screen (opened
     * cold, at Heroes) a session in play opens the Timer tab, as the phone does on a cold start.
     */
    @Composable
    fun activeSession(): TimerSnapshot? {
        val services = LocalServices.current
        val table = services.data.table
        val flow = remember(table) { table.timer.snapshot.map { s -> s?.takeIf { it.status != TimerStatus.ENDED } } }
        val session by flow.collectAsState(null)
        LaunchedEffect(table) {
            table.timerStore.load()
            val first = flow.first()
            val router = services.router
            if (first != null && router.route == Route.Catalog && router.entry.depth == 0) router.navigate(Route.Timer)
        }
        return session
    }

    /** The running timer is a full-screen table tool: no tab bar beside Next and Pause. */
    fun hidesTabBar(route: Route, session: TimerSnapshot?): Boolean = route == Route.Timer && session != null

    /** The bar shows on every screen with a tab bar but the timer's own. */
    fun showsBar(route: Route, session: TimerSnapshot?, tabBar: Boolean): Boolean =
        session != null && tabBar && route != Route.Timer
}

/**
 * Compact "timer running" bar above the tab bar on every screen but the timer: phase, where in the
 * round, and the clock. Tap returns to the timer. Ticks only while shown.
 */
@Composable
fun RunningTimerBar(session: TimerSnapshot) {
    val services = LocalServices.current
    val timer = services.data.table.timer
    var bar by remember(session) { mutableStateOf(RunningTimerUi.from(session, timer.readout(session))) }
    LaunchedEffect(session) {
        while (session.status == TimerStatus.RUNNING) {
            bar = RunningTimerUi.from(session, timer.readout(session))
            delay(TICK_MS)
        }
        bar = RunningTimerUi.from(session, timer.readout(session))
    }
    Button({
        type(ButtonType.Button)
        classes("goa-table-running-bar")
        if (bar.paused) classes("paused")
        if (bar.overtime) classes("overtime")
        attr("aria-label", bar.description)
        onClick { services.router.navigate(Route.Timer) }
    }) {
        Span({ classes("goa-table-running-icon") }) { Icon(if (bar.paused) "nav_timer" else "nav_timer_filled") }
        Span({ classes("goa-table-row-text") }) {
            Span({
                classes("t-title-small", "goa-table-ellipsis")
            }) { Text(if (bar.paused) "${bar.phase} · Paused" else bar.phase) }
            Span({ classes("t-label-medium", "c-variant", "goa-table-ellipsis") }) { Text(bar.context) }
        }
        Span({ classes("goa-table-running-clock", "t-timer-small") }) { Text(bar.clock) }
        Icon("chevron_right")
    }
}

private const val TICK_MS = 250L
