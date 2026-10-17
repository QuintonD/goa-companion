package dev.quinton.goa.feature.timer.system

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.net.Uri
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

/** How the background "phase over" alert sounds, from Settings → Sounds and Haptics. */
enum class AlertStyle(val sound: Boolean, val haptics: Boolean) {
    CHIME_AND_VIBRATE(sound = true, haptics = true),
    CHIME(sound = true, haptics = false),
    VIBRATE(sound = false, haptics = true),
    SILENT(sound = false, haptics = false),
    ;

    companion object {
        fun of(sound: Boolean, haptics: Boolean): AlertStyle =
            entries.first { it.sound == sound && it.haptics == haptics }
    }
}

/** Posts the ongoing countdown and the "phase over" alert. */
interface TimerNotifier {
    /** False when the countdown and phase-over alerts can't be shown (permission denied or blocked). */
    fun canPost(): Boolean

    fun showOngoing(snapshot: TimerSnapshot, readout: TimerReadout, wallNow: Long)

    fun cancelOngoing()

    /** High-importance alert when a timed phase reaches zero while the timer screen isn't visible. */
    fun alertPhaseOver(snapshot: TimerSnapshot, endedTitle: String, style: AlertStyle = AlertStyle.CHIME_AND_VIBRATE)

    /** Removes a "Time's up" alert the table has already acted on. */
    fun cancelAlert()
}

/**
 * Notification design (ARCHITECTURE.md "Timer design"): an ongoing notification on a
 * default-importance channel with no sound or vibration (low importance hid it from the lock
 * screen and folded it into "Silent"), public on the lock screen, with a system chronometer
 * (counts down to the phase end, up in overtime) and actions, which requests promotion to a Live
 * Update on Android 16+; and high-importance alert channels whose sound (a short bundled chime)
 * uses `USAGE_ALARM` so it is audible in the background and in Do Not Disturb's alarm lane.
 * Channel sound and vibration can't change after creation, so there is one alert channel per
 * Sounds × Haptics combination. No custom views or colorized style, which would block promotion.
 *
 * Actions are Pause/Resume (one tap, also from the lock screen) and Next (needs an unlock on
 * API 31+). There is deliberately no Stop: ending a match is irrecoverable and stays in the app.
 * The running countdown sets no short critical text, so the Android 16 status-bar chip is driven
 * by the chronometer; static states ("Paused", "Over") set one. A swipe-away (allowed for
 * ongoing notifications since Android 14) fires the delete intent, which re-posts it.
 */
class AndroidTimerNotifier @Inject constructor(@ApplicationContext private val context: Context) : TimerNotifier {
    private val manager = NotificationManagerCompat.from(context)

    private val chime: Uri
        get() = Uri.parse("${ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.packageName}/${R.raw.phase_chime}")

    fun ensureChannels() {
        val system = context.getSystemService(NotificationManager::class.java) ?: return
        LEGACY_CHANNELS.forEach { system.deleteNotificationChannel(it) }
        val ongoing = NotificationChannel(
            CHANNEL_ONGOING,
            "Running timer",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Countdown for the current phase, with pause and next controls. Silent."
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val alarm = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val alerts = AlertStyle.entries.map { style ->
            NotificationChannel(
                alertChannel(style),
                alertChannelName(style),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "When a timed phase reaches zero and the timer isn't on screen. " +
                    "Follows Settings → Sounds and Haptics."
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                if (style.sound) setSound(chime, alarm) else setSound(null, null)
                enableVibration(style.haptics)
                if (style.haptics) vibrationPattern = ALERT_VIBRATION
            }
        }
        system.createNotificationChannels(listOf(ongoing) + alerts)
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
        ) + " · " + TimerText.nextText(snapshot)
        val builder = NotificationCompat.Builder(this.context, CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(title)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openAppIntent())
            .setDeleteIntent(actionIntent(TimerActionReceiver.ACTION_REPOST))
            .setRequestPromotedOngoing(true)

        val remaining = readout.remainingMillis
        when {
            snapshot.status == TimerStatus.PAUSED -> {
                builder.setContentText("Paused · ${TimerText.clock(readout)} · $context")
                    .setShowWhen(false)
                    .setShortCriticalText("Paused")
                    .addAction(action("Resume", TimerActionReceiver.ACTION_RESUME, unlock = false))
            }

            remaining == null -> {
                builder.setContentText(context)
                    .setUsesChronometer(true)
                    .setWhen(wallNow - readout.elapsedInStepMillis)
                    .setShowWhen(true)
                    .addAction(action("Pause", TimerActionReceiver.ACTION_PAUSE, unlock = false))
            }

            remaining < 0 -> {
                // Chronometer counting up from the moment the phase hit zero.
                builder.setContentText("Overtime · $context")
                    .setUsesChronometer(true)
                    .setWhen(wallNow + remaining)
                    .setShowWhen(true)
                    .setShortCriticalText("Over")
                    .addAction(action("Pause", TimerActionReceiver.ACTION_PAUSE, unlock = false))
            }

            else -> {
                // No short critical text: a static string would freeze the status-bar chip;
                // the chronometer (setWhen + count-down) keeps it ticking.
                builder.setContentText(context)
                    .setUsesChronometer(true)
                    .setChronometerCountDown(true)
                    .setWhen(wallNow + remaining)
                    .setShowWhen(true)
                    .addAction(action("Pause", TimerActionReceiver.ACTION_PAUSE, unlock = false))
            }
        }
        builder.addAction(action("Next", TimerActionReceiver.ACTION_NEXT, unlock = true))
        manager.notify(ID_ONGOING, builder.build())
    }

    /** [unlock]: API 31+ asks for the device credential before a lock-screen tap runs the action. */
    private fun action(title: String, action: String, unlock: Boolean): NotificationCompat.Action =
        NotificationCompat.Action.Builder(0, title, actionIntent(action))
            .setAuthenticationRequired(unlock)
            .build()

    override fun cancelOngoing() {
        manager.cancel(ID_ONGOING)
    }

    @SuppressLint("MissingPermission") // Checked by canPost().
    override fun alertPhaseOver(snapshot: TimerSnapshot, endedTitle: String, style: AlertStyle) {
        if (!canPost()) return
        ensureChannels()
        val nextText = if (snapshot.preset.autoAdvance) {
            "Now: ${TimerText.stepTitle(snapshot.step)}"
        } else {
            "Overtime is counting · ${TimerText.nextText(snapshot)}"
        }
        val builder = NotificationCompat.Builder(context, alertChannel(style))
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle("Time's up: $endedTitle")
            .setContentText(nextText)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setTimeoutAfter(ALERT_TIMEOUT_MS)
            .setContentIntent(openAppIntent())
        // Pre-O style flags mirror the channel (the channel decides on API 26+).
        if (style.haptics) builder.setVibrate(ALERT_VIBRATION) else builder.setSilent(!style.sound)
        manager.notify(ID_ALERT, builder.build())
    }

    override fun cancelAlert() {
        manager.cancel(ID_ALERT)
    }

    override fun canPost(): Boolean {
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
        /** Default importance, silent, public on the lock screen (replaces the low "timer_ongoing"). */
        const val CHANNEL_ONGOING = "timer_running"
        const val ID_ONGOING = 4_101
        const val ID_ALERT = 4_102

        /** Channels of earlier versions, deleted on start: low-importance countdown, looping alarm. */
        val LEGACY_CHANNELS = listOf("timer_ongoing", "timer_alerts")

        /** The chime plays once; the visual alert clears itself after a while (or on any action). */
        private const val ALERT_TIMEOUT_MS = 5 * 60_000L
        private val ALERT_VIBRATION = longArrayOf(0, 400, 200, 400, 200, 700)

        fun alertChannel(style: AlertStyle): String = when (style) {
            AlertStyle.CHIME_AND_VIBRATE -> "timer_phase_over"
            AlertStyle.CHIME -> "timer_phase_over_sound"
            AlertStyle.VIBRATE -> "timer_phase_over_vibrate"
            AlertStyle.SILENT -> "timer_phase_over_silent"
        }

        private fun alertChannelName(style: AlertStyle): String = when (style) {
            AlertStyle.CHIME_AND_VIBRATE -> "Phase over"
            AlertStyle.CHIME -> "Phase over (sound only)"
            AlertStyle.VIBRATE -> "Phase over (vibrate only)"
            AlertStyle.SILENT -> "Phase over (silent)"
        }
    }
}
