package dev.quinton.goa.feature.timer.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import dev.quinton.goa.feature.timer.session.TimerCommand
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

/** The exact alarm for the end of a timed phase. */
@AndroidEntryPoint
class TimerAlarmReceiver : BroadcastReceiver() {
    @Inject lateinit var controller: TimerController

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_PHASE_END) return
        val stepKey = intent.getStringExtra(EXTRA_STEP_KEY)
        runAsync { controller.onAlarm(stepKey) }
    }

    companion object {
        const val ACTION_PHASE_END = "dev.quinton.goa.timer.PHASE_END"
        const val EXTRA_STEP_KEY = "step_key"
    }
}

/** Pause / Resume / Next / Stop buttons on the ongoing notification. */
@AndroidEntryPoint
class TimerActionReceiver : BroadcastReceiver() {
    @Inject lateinit var controller: TimerController

    override fun onReceive(context: Context, intent: Intent) {
        val command = commandFor(intent.action) ?: return
        runAsync { controller.perform(command) }
    }

    companion object {
        const val ACTION_PAUSE = "dev.quinton.goa.timer.PAUSE"
        const val ACTION_RESUME = "dev.quinton.goa.timer.RESUME"
        const val ACTION_NEXT = "dev.quinton.goa.timer.NEXT"
        const val ACTION_STOP = "dev.quinton.goa.timer.STOP"

        fun commandFor(action: String?): TimerCommand? = when (action) {
            ACTION_PAUSE -> TimerCommand.Pause
            ACTION_RESUME -> TimerCommand.Resume
            ACTION_NEXT -> TimerCommand.Next
            ACTION_STOP -> TimerCommand.EndMatch
            else -> null
        }
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
