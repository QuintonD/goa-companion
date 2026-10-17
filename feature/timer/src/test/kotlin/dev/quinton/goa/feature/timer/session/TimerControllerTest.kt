package dev.quinton.goa.feature.timer.session

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.domain.timer.PhaseKind
import dev.quinton.goa.core.domain.timer.TimerStatus
import dev.quinton.goa.feature.timer.TimerHarness
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class TimerControllerTest {
    private val h = TimerHarness()

    private suspend fun startTable(autoAdvance: Boolean = false) {
        val preset = h.repository.selectedPreset().copy(autoAdvance = autoAdvance)
        h.controller.start(preset, actionSlots = 4)
    }

    @Test
    fun `start persists the session, schedules the phase end and posts the notification`() = runTest {
        startTable()

        val s = h.controller.snapshot.first()!!
        assertThat(s.status).isEqualTo(TimerStatus.RUNNING)
        assertThat(h.scheduler.scheduledAt).isEqualTo(h.clocks.elapsed + 90_000)
        assertThat(h.notifier.ongoing!!.remainingMillis).isEqualTo(90_000)
    }

    @Test
    fun `pause cancels the alarm and resume reschedules it from the remaining time`() = runTest {
        startTable()
        h.clocks.advance(30_000)
        h.controller.perform(TimerCommand.Pause)
        assertThat(h.scheduler.scheduledAt).isNull()
        assertThat(h.notifier.ongoing).isNotNull()

        h.clocks.advance(600_000)
        h.controller.perform(TimerCommand.Resume)
        assertThat(h.scheduler.scheduledAt).isEqualTo(h.clocks.elapsed + 60_000)
    }

    @Test
    fun `end match cancels alarm and notification`() = runTest {
        startTable()
        h.controller.perform(TimerCommand.EndMatch)

        assertThat(h.scheduler.scheduledAt).isNull()
        assertThat(h.notifier.ongoing).isNull()
        assertThat(h.controller.snapshot.first()!!.status).isEqualTo(TimerStatus.ENDED)
    }

    @Test
    fun `alarm at phase end alerts when the screen is hidden and leaves overtime running`() = runTest {
        startTable()
        val key = h.scheduler.stepKey
        h.clocks.advance(90_000)
        h.controller.onAlarm(key)

        assertThat(h.notifier.alerts).containsExactly("Pre-round strategy")
        val s = h.controller.snapshot.first()!!
        assertThat(s.stepIndex).isEqualTo(0)
        assertThat(h.scheduler.scheduledAt).isNull()
        assertThat(h.notifier.ongoing!!.overtime).isFalse() // exactly zero
    }

    @Test
    fun `alarm with auto advance moves to the next phase and schedules its end`() = runTest {
        startTable(autoAdvance = true)
        val key = h.scheduler.stepKey
        h.clocks.advance(90_200)
        h.controller.onAlarm(key)

        val s = h.controller.snapshot.first()!!
        assertThat(s.step.phase!!.kind).isEqualTo(PhaseKind.TURN)
        assertThat(h.scheduler.scheduledAt).isEqualTo(h.clocks.elapsed - 200 + 30_000)
        assertThat(h.notifier.alerts).hasSize(1)
    }

    @Test
    fun `visible timer screen suppresses the background alert`() = runTest {
        startTable()
        h.controller.uiVisible = true
        val key = h.scheduler.stepKey
        h.clocks.advance(91_000)
        h.controller.onAlarm(key)

        assertThat(h.notifier.alerts).isEmpty()
    }

    @Test
    fun `stale alarm from an earlier step is ignored`() = runTest {
        startTable()
        val oldKey = h.scheduler.stepKey
        h.controller.perform(TimerCommand.Next)
        h.clocks.advance(95_000)
        h.controller.onAlarm(oldKey)

        assertThat(h.notifier.alerts).isEmpty()
        assertThat(h.controller.snapshot.first()!!.stepIndex).isEqualTo(1)
    }

    @Test
    fun `early alarm does not alert and is rescheduled`() = runTest {
        startTable()
        val key = h.scheduler.stepKey
        h.clocks.advance(89_000)
        h.controller.onAlarm(key)

        assertThat(h.notifier.alerts).isEmpty()
        assertThat(h.scheduler.scheduledAt).isEqualTo(h.clocks.elapsed + 1_000)
    }

    @Test
    fun `resync after a reboot rebases the session and reschedules on the new timeline`() = runTest {
        startTable()
        h.clocks.advance(20_000)
        // Reboot: elapsed restarts, 40 s of wall time pass in total since the step started.
        h.boot = 2
        h.clocks.elapsed = 10_000
        h.clocks.wall += 20_000
        h.controller.resync()

        val s = h.controller.snapshot.first()!!
        assertThat(h.controller.readout(s).remainingMillis).isEqualTo(50_000)
        assertThat(h.scheduler.scheduledAt).isEqualTo(10_000 + 50_000)
        assertThat(s.bootCount).isEqualTo(2)
    }

    @Test
    fun `commands without a session do nothing`() = runTest {
        assertThat(h.controller.perform(TimerCommand.Next)).isNull()
        assertThat(h.notifier.ongoing).isNull()
    }

    @Test
    fun `clear removes the session`() = runTest {
        startTable()
        h.controller.clear()
        assertThat(h.controller.snapshot.first()).isNull()
        assertThat(h.notifier.ongoing).isNull()
    }
}
