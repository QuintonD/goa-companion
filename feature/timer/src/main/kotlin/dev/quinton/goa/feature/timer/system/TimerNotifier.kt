package dev.quinton.goa.feature.timer.system

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.quinton.goa.core.domain.timer.TimerReadout
import dev.quinton.goa.core.domain.timer.TimerSnapshot
import dev.quinton.goa.core.domain.timer.TimerStatus
import dev.quinton.goa.core.navigation.EXTRA_OPEN_TAB
import dev.quinton.goa.core.navigation.TopLevelTab
import dev.quinton.goa.core.presentation.timer.TimerText
import dev.quinton.goa.feature.timer.R
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
 * Update on Android 16+; and a high-importance "phase over" alert that is only the visual part:
 * its channel has no sound or vibration, and [sounder] plays the bundled chime and vibrates with
 * alarm usage itself (ADR-018 addendum: the system auto-groups the two notifications and marks
 * the alert silent, so a channel sound was unreliable). No custom views or colorized style,
 * which would block promotion.
 *
 * Actions are Pause/Resume (one tap, also from the lock screen) and Next (needs an unlock on
 * API 31+). There is deliberately no Stop: ending a match is irrecoverable and stays in the app.
 * The running countdown sets no short critical text, so the Android 16 status-bar chip is driven
 * by the chronometer; static states ("Paused", "Over") set one. A swipe-away (allowed for
 * ongoing notifications since Android 14) fires the delete intent, which re-posts it.
 */
class AndroidTimerNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sounder: PhaseAlertSounder,
) : TimerNotifier {
    private val manager = NotificationManagerCompat.from(context)

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
        val alert = NotificationChannel(
            CHANNEL_ALERT,
            "Phase over",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "When a timed phase reaches zero and the timer isn't on screen. The app plays " +
                "the chime and vibration itself, following Settings → Sounds and Haptics."
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setSound(null, null)
            enableVibration(false)
        }
        system.createNotificationChannels(listOf(ongoing, alert))
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

    /**
     * Sounds the alert (unless the "Phase over" channel is turned off in system settings), then
     * posts its silent visual part if notifications are allowed. The sound doesn't need the
     * notification permission: the in-app Sounds and Haptics switches decide.
     */
    @SuppressLint("MissingPermission") // Checked by canPost().
    override fun alertPhaseOver(snapshot: TimerSnapshot, endedTitle: String, style: AlertStyle) {
        ensureChannels()
        if (!alertChannelBlocked()) sounder.sound(style)
        if (!canPost()) return
        val nextText = TimerText.alertBody(snapshot)
        val builder = NotificationCompat.Builder(context, CHANNEL_ALERT)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle("Time's up: $endedTitle")
            .setContentText(nextText)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setTimeoutAfter(ALERT_TIMEOUT_MS)
            .setContentIntent(openAppIntent())
        manager.notify(ID_ALERT, builder.build())
    }

    private fun alertChannelBlocked(): Boolean =
        manager.getNotificationChannel(CHANNEL_ALERT)?.importance == NotificationManager.IMPORTANCE_NONE

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
        // Opens the Timer tab, not whichever tab was last shown (app shell, MainActivity).
        launch.putExtra(EXTRA_OPEN_TAB, TopLevelTab.TIMER.name)
        return PendingIntent.getActivity(
            context,
            0,
            launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
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

        /** High importance, no sound or vibration: the visual "Time's up" (the sounder makes the noise). */
        const val CHANNEL_ALERT = "timer_phase_alert"

        /**
         * Channels of earlier versions, deleted on start: the low-importance countdown, the looping
         * alarm, and the per-Sounds × Haptics alert channels whose sound auto-grouping muted.
         */
        val LEGACY_CHANNELS = listOf(
            "timer_ongoing",
            "timer_alerts",
            "timer_phase_over",
            "timer_phase_over_sound",
            "timer_phase_over_vibrate",
            "timer_phase_over_silent",
            // A pre-release sounding alert channel (emulator builds only).
            "timer_phase_chime",
        )

        /** The visual alert clears itself after a while (or on any action). */
        private const val ALERT_TIMEOUT_MS = 5 * 60_000L
    }
}
