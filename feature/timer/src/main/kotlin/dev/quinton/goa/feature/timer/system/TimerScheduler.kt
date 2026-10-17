package dev.quinton.goa.feature.timer.system

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Schedules the single pending "phase over" alarm. */
interface TimerScheduler {
    /** Replaces any pending alarm with one at elapsed time [atElapsed] tagged with [stepKey]. */
    fun schedule(atElapsed: Long, stepKey: String)

    fun cancel()
}

/**
 * AlarmManager exact alarms on the `ELAPSED_REALTIME_WAKEUP` clock (ADR-007). Fires in Doze via
 * `setExactAndAllowWhileIdle`; `USE_EXACT_ALARM` makes exact alarms available on API 33+. If
 * exact alarms are ever unavailable (API 31–32 with the permission revoked), falls back to an
 * inexact while-idle alarm rather than failing.
 */
class AlarmTimerScheduler @Inject constructor(@ApplicationContext private val context: Context) : TimerScheduler {
    private val alarms: AlarmManager? get() = context.getSystemService()

    override fun schedule(atElapsed: Long, stepKey: String) {
        val manager = alarms ?: return
        val intent = alarmIntent(stepKey)
        if (canScheduleExact(manager)) {
            manager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, atElapsed, intent)
        } else {
            manager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, atElapsed, intent)
        }
    }

    override fun cancel() {
        alarms?.cancel(alarmIntent(stepKey = null))
    }

    private fun canScheduleExact(manager: AlarmManager): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()

    private fun alarmIntent(stepKey: String?): PendingIntent {
        val intent = Intent(context, TimerAlarmReceiver::class.java).setAction(TimerAlarmReceiver.ACTION_PHASE_END)
        if (stepKey != null) intent.putExtra(TimerAlarmReceiver.EXTRA_STEP_KEY, stepKey)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        const val REQUEST_CODE = 4_201
    }
}
