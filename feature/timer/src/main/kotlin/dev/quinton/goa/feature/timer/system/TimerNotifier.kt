package dev.quinton.goa.feature.timer.system

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.quinton.goa.core.domain.timer.TimerReadout
import dev.quinton.goa.core.domain.timer.TimerSnapshot
import dev.quinton.goa.core.domain.timer.TimerStatus
import dev.quinton.goa.feature.timer.R
import dev.quinton.goa.feature.timer.TimerText
import javax.inject.Inject

/** Posts the ongoing countdown and the "phase over" alert. */
interface TimerNotifier {
    fun showOngoing(snapshot: TimerSnapshot, readout: TimerReadout, wallNow: Long)

    fun cancelOngoing()

    /** High-importance alert when a timed phase reaches zero while the timer screen isn't visible. */
    fun alertPhaseOver(snapshot: TimerSnapshot, endedTitle: String)
}

/**
 * Notification design (ARCHITECTURE.md "Timer design"): a low-importance ongoing notification
 * with a system chronometer (counts down to the phase end, up in overtime) and actions, which
 * requests promotion to a Live Update on Android 16+; and a high-importance alert channel whose
 * sound uses `USAGE_ALARM` so it is audible in the background and in Do Not Disturb's alarm lane.
 * No custom views or colorized style, which would block promotion.
 */
class AndroidTimerNotifier @Inject constructor(@ApplicationContext private val context: Context) : TimerNotifier {
    private val manager = NotificationManagerCompat.from(context)

    fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val system = context.getSystemService(NotificationManager::class.java) ?: return
        val ongoing = NotificationChannel(CHANNEL_ONGOING, "Running timer", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Countdown for the current phase, with pause and next controls."
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        }
        val alerts = NotificationChannel(CHANNEL_ALERTS, "Phase over", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Sounds and vibrates when a timed phase reaches zero."
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            enableVibration(true)
            vibrationPattern = ALERT_VIBRATION
        }
        system.createNotificationChannels(listOf(ongoing, alerts))
    }

    @SuppressLint("MissingPermission") // Checked by canPost().
    override fun showOngoing(snapshot: TimerSnapshot, readout: TimerReadout, wallNow: Long) {
        if (!canPost()) return
        ensureChannels()
        val step = readout.step
        val title = "Round ${readout.round} · ${TimerText.stepTitle(step)}"
        val context = TimerText.stepContext(
            step,
            snapshot.preset,
            snapshot.endOfRound.size,
            snapshot.actionSlots,
        )
        val builder = NotificationCompat.Builder(this.context, CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(title)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openAppIntent())
            .setRequestPromotedOngoing(true)

        val remaining = readout.remainingMillis
        when {
            snapshot.status == TimerStatus.PAUSED -> {
                builder.setContentText("Paused · ${TimerText.clock(readout)} · $context")
                    .setShowWhen(false)
                    .setShortCriticalText("Paused")
                    .addAction(0, "Resume", actionIntent(TimerActionReceiver.ACTION_RESUME))
            }
            remaining == null -> {
                builder.setContentText(context)
                    .setUsesChronometer(true)
                    .setWhen(wallNow - readout.elapsedInStepMillis)
                    .setShowWhen(true)
                    .addAction(0, "Pause", actionIntent(TimerActionReceiver.ACTION_PAUSE))
            }
            remaining < 0 -> {
                // Chronometer counting up from the moment the phase hit zero.
                builder.setContentText("Overtime · $context")
                    .setUsesChronometer(true)
                    .setWhen(wallNow + remaining)
                    .setShowWhen(true)
                    .setShortCriticalText("Over")
                    .addAction(0, "Pause", actionIntent(TimerActionReceiver.ACTION_PAUSE))
            }
            else -> {
                builder.setContentText(context)
                    .setUsesChronometer(true)
                    .setChronometerCountDown(true)
                    .setWhen(wallNow + remaining)
                    .setShowWhen(true)
                    .setShortCriticalText(TimerText.clock(readout))
                    .addAction(0, "Pause", actionIntent(TimerActionReceiver.ACTION_PAUSE))
            }
        }
        builder.addAction(0, "Next", actionIntent(TimerActionReceiver.ACTION_NEXT))
        builder.addAction(0, "Stop", actionIntent(TimerActionReceiver.ACTION_STOP))
        manager.notify(ID_ONGOING, builder.build())
    }

    override fun cancelOngoing() {
        manager.cancel(ID_ONGOING)
    }

    @SuppressLint("MissingPermission") // Checked by canPost().
    override fun alertPhaseOver(snapshot: TimerSnapshot, endedTitle: String) {
        if (!canPost()) return
        ensureChannels()
        val next = snapshot.step
        val nextText = if (snapshot.preset.autoAdvance) {
            "Now: ${TimerText.stepTitle(next)}"
        } else {
            "Overtime is counting. Tap Next when the table is ready."
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle("Time's up: $endedTitle")
            .setContentText(nextText)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setTimeoutAfter(ALERT_TIMEOUT_MS)
            .setContentIntent(openAppIntent())
            .setVibrate(ALERT_VIBRATION)
            .build()
        manager.notify(ID_ALERT, notification)
    }

    private fun canPost(): Boolean {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return granted && manager.areNotificationsEnabled()
    }

    private fun openAppIntent(): PendingIntent? {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        launch.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(context, 0, launch, PendingIntent.FLAG_IMMUTABLE)
    }

    private fun actionIntent(action: String): PendingIntent {
        val intent = Intent(context, TimerActionReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(context, action.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE)
    }

    companion object {
        const val CHANNEL_ONGOING = "timer_ongoing"
        const val CHANNEL_ALERTS = "timer_alerts"
        const val ID_ONGOING = 4_101
        const val ID_ALERT = 4_102
        private const val ALERT_TIMEOUT_MS = 60_000L
        private val ALERT_VIBRATION = longArrayOf(0, 400, 200, 400, 200, 700)
    }
}
