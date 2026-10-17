package dev.quinton.goa.feature.timer.system

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
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
    fun `channels separate the silent countdown from the alarm-usage alert`() {
        AndroidTimerNotifier(context).ensureChannels()

        val ongoing = notifications.getNotificationChannel(AndroidTimerNotifier.CHANNEL_ONGOING)
        val alerts = notifications.getNotificationChannel(AndroidTimerNotifier.CHANNEL_ALERTS)
        assertThat(ongoing.importance).isEqualTo(NotificationManager.IMPORTANCE_LOW)
        assertThat(ongoing.sound).isNull()
        assertThat(alerts.importance).isEqualTo(NotificationManager.IMPORTANCE_HIGH)
        assertThat(alerts.audioAttributes.usage).isEqualTo(AudioAttributes.USAGE_ALARM)
        assertThat(alerts.shouldVibrate()).isTrue()
    }

    @Test
    fun `ongoing notification counts down to the phase end with controls`() {
        val s = snapshot()
        clocks.advance(30_000)
        AndroidTimerNotifier(context).showOngoing(s, engine.readout(s), clocks.wall)

        val posted = shadowOf(notifications).getNotification(AndroidTimerNotifier.ID_ONGOING)
        assertThat(posted.flags and Notification.FLAG_ONGOING_EVENT).isNotEqualTo(0)
        assertThat(posted.`when`).isEqualTo(clocks.wall + 60_000)
        assertThat(posted.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER)).isTrue()
        assertThat(posted.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN)).isTrue()
        assertThat(posted.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
            .isEqualTo("Round 1 · Pre-round strategy")
        assertThat(posted.actions.map { it.title.toString() }).containsExactly("Pause", "Next", "Stop").inOrder()
        assertThat(posted.extras.getBoolean("android.requestPromotedOngoing")).isTrue()
    }

    @Test
    fun `paused notification offers resume and no chronometer`() {
        val s = engine.pause(snapshot())
        AndroidTimerNotifier(context).showOngoing(s, engine.readout(s), clocks.wall)

        val posted = shadowOf(notifications).getNotification(AndroidTimerNotifier.ID_ONGOING)
        assertThat(posted.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER)).isFalse()
        assertThat(posted.actions.first().title.toString()).isEqualTo("Resume")
    }

    @Test
    fun `phase over alert uses the alert channel`() {
        AndroidTimerNotifier(context).alertPhaseOver(snapshot(), "Turn")

        val posted = shadowOf(notifications).getNotification(AndroidTimerNotifier.ID_ALERT)
        assertThat(posted.channelId).isEqualTo(AndroidTimerNotifier.CHANNEL_ALERTS)
        assertThat(posted.extras.getCharSequence(Notification.EXTRA_TITLE).toString()).isEqualTo("Time's up: Turn")
    }

    @Test
    fun `nothing is posted without the notification permission`() {
        shadowOf(context as Application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val s = snapshot()
        AndroidTimerNotifier(context).showOngoing(s, engine.readout(s), clocks.wall)

        assertThat(shadowOf(notifications).allNotifications).isEmpty()
    }

    @Test
    fun `notification actions map to timer commands`() {
        assertThat(TimerActionReceiver.commandFor(TimerActionReceiver.ACTION_PAUSE)).isEqualTo(TimerCommand.Pause)
        assertThat(TimerActionReceiver.commandFor(TimerActionReceiver.ACTION_RESUME)).isEqualTo(TimerCommand.Resume)
        assertThat(TimerActionReceiver.commandFor(TimerActionReceiver.ACTION_NEXT)).isEqualTo(TimerCommand.Next)
        assertThat(TimerActionReceiver.commandFor(TimerActionReceiver.ACTION_STOP)).isEqualTo(TimerCommand.EndMatch)
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
}
