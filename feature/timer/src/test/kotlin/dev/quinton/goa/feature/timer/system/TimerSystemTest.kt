package dev.quinton.goa.feature.timer.system

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.domain.timer.TimerEngine
import dev.quinton.goa.feature.timer.FakeClocks
import dev.quinton.goa.feature.timer.FileTimerAssets
import dev.quinton.goa.feature.timer.session.TimerCommand
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TimerSystemTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val clocks = FakeClocks()
    private val engine = TimerEngine(clocks, clocks)
    private val sounder = RecordingSounder()

    @Before
    fun grant() {
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun snapshot() = runBlocking {
        val assets = FileTimerAssets()
        engine.start("s", assets.presetCatalog().presets.first(), assets.rules().endOfRound, 4)
    }

    @Test
    fun `scheduler sets one exact elapsed-realtime alarm and cancel removes it`() {
        val scheduler = AlarmTimerScheduler(context)
        scheduler.schedule(123_456L, "key-1")
        scheduler.schedule(223_456L, "key-2")

        val shadow = shadowOf(alarms)
        assertThat(shadow.scheduledAlarms).hasSize(1)
        val alarm = shadow.nextScheduledAlarm!!
        assertThat(alarm.type).isEqualTo(AlarmManager.ELAPSED_REALTIME_WAKEUP)
        assertThat(alarm.triggerAtTime).isEqualTo(223_456L)
        assertThat(alarm.isAllowWhileIdle).isTrue()
        val intent = shadowOf(alarm.operation).savedIntent
        assertThat(intent.action).isEqualTo(TimerAlarmReceiver.ACTION_PHASE_END)
        assertThat(intent.getStringExtra(TimerAlarmReceiver.EXTRA_STEP_KEY)).isEqualTo("key-2")

        scheduler.cancel()
        assertThat(shadow.scheduledAlarms).isEmpty()
    }

    @Test
    fun `countdown channel is silent but visible on the lock screen, old channels are removed`() {
        notifications.createNotificationChannel(
            NotificationChannel("timer_ongoing", "Old", NotificationManager.IMPORTANCE_LOW),
        )
        AndroidTimerNotifier(context, sounder).ensureChannels()

        val ongoing = notifications.getNotificationChannel(AndroidTimerNotifier.CHANNEL_ONGOING)
        // Low importance hid the countdown from the keyguard and folded it into "Silent".
        assertThat(ongoing.importance).isEqualTo(NotificationManager.IMPORTANCE_DEFAULT)
        assertThat(ongoing.sound).isNull()
        assertThat(ongoing.shouldVibrate()).isFalse()
        assertThat(ongoing.lockscreenVisibility).isEqualTo(Notification.VISIBILITY_PUBLIC)
        AndroidTimerNotifier.LEGACY_CHANNELS.forEach {
            assertThat(notifications.getNotificationChannel(it)).isNull()
        }
    }

    @Test
    fun `phase-over channel is a silent heads-up, the per-style sounding channels are removed`() {
        // Regression: auto-grouping with the countdown marked the alert SILENT, muting a channel sound.
        notifications.createNotificationChannel(
            NotificationChannel("timer_phase_over", "Old", NotificationManager.IMPORTANCE_HIGH),
        )
        AndroidTimerNotifier(context, sounder).ensureChannels()

        val alert = notifications.getNotificationChannel(AndroidTimerNotifier.CHANNEL_ALERT)
        assertThat(alert.importance).isEqualTo(NotificationManager.IMPORTANCE_HIGH)
        assertThat(alert.sound).isNull()
        assertThat(alert.shouldVibrate()).isFalse()
        assertThat(alert.lockscreenVisibility).isEqualTo(Notification.VISIBILITY_PUBLIC)
        assertThat(notifications.getNotificationChannel("timer_phase_over")).isNull()
    }

    @Test
    fun `the app sounds the alert itself, following sounds and haptics, even without notification permission`() {
        val s = engine.next(snapshot())
        AndroidTimerNotifier(context, sounder).alertPhaseOver(s, "Turn 2 · Round 1", AlertStyle.VIBRATE)
        shadowOf(context as Application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        AndroidTimerNotifier(context, sounder).alertPhaseOver(s, "Turn 2 · Round 1", AlertStyle.CHIME)

        assertThat(sounder.sounded).containsExactly(AlertStyle.VIBRATE, AlertStyle.CHIME).inOrder()
    }

    @Test
    fun `turning the phase-over channel off in system settings also stops the sound`() {
        val notifier = AndroidTimerNotifier(context, sounder)
        notifier.ensureChannels()
        notifications.createNotificationChannel(
            NotificationChannel(AndroidTimerNotifier.CHANNEL_ALERT, "Phase over", NotificationManager.IMPORTANCE_NONE),
        )
        notifier.alertPhaseOver(engine.next(snapshot()), "Turn 2 · Round 1", AlertStyle.CHIME_AND_VIBRATE)

        assertThat(sounder.sounded).isEmpty()
    }

    @Test
    fun `ongoing notification counts down to the phase end with controls`() {
        val s = snapshot()
        clocks.advance(30_000)
        AndroidTimerNotifier(context, sounder).showOngoing(s, engine.readout(s), clocks.wall)

        val posted = shadowOf(notifications).getNotification(AndroidTimerNotifier.ID_ONGOING)
        assertThat(posted.flags and Notification.FLAG_ONGOING_EVENT).isNotEqualTo(0)
        assertThat(posted.`when`).isEqualTo(clocks.wall + 60_000)
        assertThat(posted.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER)).isTrue()
        assertThat(posted.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN)).isTrue()
        assertThat(posted.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
            .isEqualTo("Round 1 · Pre-round strategy")
        assertThat(posted.extras.getBoolean("android.requestPromotedOngoing")).isTrue()
    }

    @Test
    fun `lock-screen actions are pause and next only, with next behind unlock`() {
        val s = snapshot()
        AndroidTimerNotifier(context, sounder).showOngoing(s, engine.readout(s), clocks.wall)

        val posted = shadowOf(notifications).getNotification(AndroidTimerNotifier.ID_ONGOING)
        // No Stop: ending a match from the lock screen was one tap and irrecoverable.
        assertThat(posted.actions.map { it.title.toString() }).containsExactly("Pause", "Next").inOrder()
        assertThat(posted.actions[0].isAuthenticationRequired).isFalse()
        assertThat(posted.actions[1].isAuthenticationRequired).isTrue()
        val actions = posted.actions.map { shadowOf(it.actionIntent).savedIntent.action }
        assertThat(actions).containsExactly(TimerActionReceiver.ACTION_PAUSE, TimerActionReceiver.ACTION_NEXT).inOrder()
    }

    @Test
    fun `running countdown leaves the status chip to the chronometer`() {
        val s = snapshot()
        clocks.advance(30_000)
        AndroidTimerNotifier(context, sounder).showOngoing(s, engine.readout(s), clocks.wall)

        val posted = shadowOf(notifications).getNotification(AndroidTimerNotifier.ID_ONGOING)
        // A static short critical text would freeze the Android 16 status-bar chip.
        assertThat(posted.extras.getCharSequence(EXTRA_SHORT_CRITICAL_TEXT)).isNull()
        assertThat(posted.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN)).isTrue()
    }

    @Test
    fun `overtime shows a static Over chip`() {
        val s = snapshot()
        clocks.advance(95_000)
        AndroidTimerNotifier(context, sounder).showOngoing(s, engine.readout(s), clocks.wall)

        val posted = shadowOf(notifications).getNotification(AndroidTimerNotifier.ID_ONGOING)
        assertThat(posted.extras.getCharSequence(EXTRA_SHORT_CRITICAL_TEXT).toString()).isEqualTo("Over")
    }

    @Test
    fun `swiping the countdown away re-posts it through the action receiver`() {
        val s = snapshot()
        AndroidTimerNotifier(context, sounder).showOngoing(s, engine.readout(s), clocks.wall)

        val posted = shadowOf(notifications).getNotification(AndroidTimerNotifier.ID_ONGOING)
        val delete = shadowOf(posted.deleteIntent)
        assertThat(delete.isBroadcast).isTrue()
        assertThat(delete.savedIntent.action).isEqualTo(TimerActionReceiver.ACTION_REPOST)
        assertThat(delete.savedIntent.component!!.className).isEqualTo(TimerActionReceiver::class.java.name)
        assertThat(TimerActionReceiver.isRepost(TimerActionReceiver.ACTION_REPOST)).isTrue()
        assertThat(TimerActionReceiver.commandFor(TimerActionReceiver.ACTION_REPOST)).isNull()
    }

    @Test
    fun `paused notification offers resume and no chronometer`() {
        val s = engine.pause(snapshot())
        AndroidTimerNotifier(context, sounder).showOngoing(s, engine.readout(s), clocks.wall)

        val posted = shadowOf(notifications).getNotification(AndroidTimerNotifier.ID_ONGOING)
        assertThat(posted.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER)).isFalse()
        assertThat(posted.actions.first().title.toString()).isEqualTo("Resume")
        assertThat(posted.actions.first().isAuthenticationRequired).isFalse()
        assertThat(posted.extras.getCharSequence(EXTRA_SHORT_CRITICAL_TEXT).toString()).isEqualTo("Paused")
    }

    @Test
    fun `notifier reports whether it can post`() {
        assertThat(AndroidTimerNotifier(context, sounder).canPost()).isTrue()
        shadowOf(context as Application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertThat(AndroidTimerNotifier(context, sounder).canPost()).isFalse()
    }

    @Test
    fun `phase over alert posts on the silent alert channel and can be cleared`() {
        val notifier = AndroidTimerNotifier(context, sounder)
        val s = engine.next(snapshot())
        notifier.alertPhaseOver(s, "Turn 2 · Round 1", AlertStyle.VIBRATE)

        val posted = shadowOf(notifications).getNotification(AndroidTimerNotifier.ID_ALERT)
        assertThat(posted.channelId).isEqualTo(AndroidTimerNotifier.CHANNEL_ALERT)
        assertThat(posted.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
            .isEqualTo("Time's up: Turn 2 · Round 1")
        assertThat(posted.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
            .isEqualTo("Overtime is counting · Next: Cards revealed")

        notifier.cancelAlert()
        assertThat(shadowOf(notifications).getNotification(AndroidTimerNotifier.ID_ALERT)).isNull()
    }

    @Test
    fun `countdown text says what comes next`() {
        val s = engine.next(snapshot())
        AndroidTimerNotifier(context, sounder).showOngoing(s, engine.readout(s), clocks.wall)

        val posted = shadowOf(notifications).getNotification(AndroidTimerNotifier.ID_ONGOING)
        assertThat(posted.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
            .isEqualTo("Round 1 · Play a card")
        assertThat(posted.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
            .isEqualTo("Turn 1 of 4 · Next: Cards revealed")
    }

    @Test
    fun `nothing is posted without the notification permission`() {
        shadowOf(context as Application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val s = snapshot()
        AndroidTimerNotifier(context, sounder).showOngoing(s, engine.readout(s), clocks.wall)

        assertThat(shadowOf(notifications).allNotifications).isEmpty()
    }

    @Test
    fun `notification actions map to timer commands`() {
        assertThat(TimerActionReceiver.commandFor(TimerActionReceiver.ACTION_PAUSE)).isEqualTo(TimerCommand.Pause)
        assertThat(TimerActionReceiver.commandFor(TimerActionReceiver.ACTION_RESUME)).isEqualTo(TimerCommand.Resume)
        assertThat(TimerActionReceiver.commandFor(TimerActionReceiver.ACTION_NEXT)).isEqualTo(TimerCommand.Next)
        // A Stop intent left over from an older notification no longer ends the match.
        assertThat(TimerActionReceiver.commandFor("dev.quinton.goa.timer.STOP")).isNull()
        assertThat(TimerActionReceiver.commandFor("other")).isNull()
    }

    @Test
    fun `system receiver handles boot, clock changes and app updates`() {
        assertThat(TimerSystemReceiver.HANDLED).containsExactly(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
        )
    }

    private companion object {
        /** `Notification.EXTRA_SHORT_CRITICAL_TEXT` (API 36). */
        const val EXTRA_SHORT_CRITICAL_TEXT = "android.shortCriticalText"
    }
}

/** Records the styles the phase-over alert was sounded with. */
class RecordingSounder : PhaseAlertSounder {
    val sounded = mutableListOf<AlertStyle>()

    override fun sound(style: AlertStyle) {
        sounded += style
    }

    override suspend fun awaitDone() = Unit
}
