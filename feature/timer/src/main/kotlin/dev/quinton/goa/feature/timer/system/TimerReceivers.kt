package dev.quinton.goa.feature.timer.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import dev.quinton.goa.core.presentation.timer.TimerCommand
import dev.quinton.goa.feature.timer.session.TimerController
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Runs [block] off the main thread while keeping the broadcast alive (goAsync). */
internal fun BroadcastReceiver.runAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
        try {
            block()
        } finally {
            pending.finish()
        }
    }
}

/**
 * The exact alarm for the end of a timed phase. Keeps the broadcast alive (goAsync) until the
 * phase-over chime has played, so a backgrounded or dozing process isn't frozen mid-chime.
 */
@AndroidEntryPoint
class TimerAlarmReceiver : BroadcastReceiver() {
    @Inject lateinit var controller: TimerController

    @Inject lateinit var sounder: PhaseAlertSounder

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_PHASE_END) return
        val stepKey = intent.getStringExtra(EXTRA_STEP_KEY)
        runAsync {
            controller.onAlarm(stepKey)
            sounder.awaitDone()
        }
    }

    companion object {
        const val ACTION_PHASE_END = "dev.quinton.goa.timer.PHASE_END"
        const val EXTRA_STEP_KEY = "step_key"
    }
}

/**
 * Pause / Resume / Next buttons on the ongoing notification, and its delete intent (swipe-away
 * re-posts it while a session runs). There is no Stop action: ending a match stays in the app,
 * behind a confirmation.
 */
@AndroidEntryPoint
class TimerActionReceiver : BroadcastReceiver() {
    @Inject lateinit var controller: TimerController

    override fun onReceive(context: Context, intent: Intent) {
        if (isRepost(intent.action)) {
            runAsync { controller.resync() }
            return
        }
        val command = commandFor(intent.action) ?: return
        runAsync { controller.perform(command) }
    }

    companion object {
        const val ACTION_PAUSE = "dev.quinton.goa.timer.PAUSE"
        const val ACTION_RESUME = "dev.quinton.goa.timer.RESUME"
        const val ACTION_NEXT = "dev.quinton.goa.timer.NEXT"

        /** Delete intent of the ongoing notification. */
        const val ACTION_REPOST = "dev.quinton.goa.timer.REPOST"

        fun commandFor(action: String?): TimerCommand? = when (action) {
            ACTION_PAUSE -> TimerCommand.Pause
            ACTION_RESUME -> TimerCommand.Resume
            ACTION_NEXT -> TimerCommand.Next
            else -> null
        }

        fun isRepost(action: String?): Boolean = action == ACTION_REPOST
    }
}

/** Reboot, clock change and app update: rebase the session and reschedule its alarm. */
@AndroidEntryPoint
class TimerSystemReceiver : BroadcastReceiver() {
    @Inject lateinit var controller: TimerController

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED) return
        runAsync { controller.resync() }
    }

    companion object {
        val HANDLED = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
        )
    }
}
