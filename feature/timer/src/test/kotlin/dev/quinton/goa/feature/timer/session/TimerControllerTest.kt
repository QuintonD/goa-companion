package dev.quinton.goa.feature.timer.session

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.domain.timer.MatchDuration
import dev.quinton.goa.core.domain.timer.PhaseKind
import dev.quinton.goa.core.domain.timer.TimerStatus
import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.MatchStatus
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
    fun `wall clock change then reboot keeps the remaining time`() = runTest {
        startTable()
        h.clocks.advance(20_000)
        // TIME_SET: the wall clock jumps +5 min, the system receiver re-syncs.
        h.clocks.wall += 5 * 60_000L
        h.controller.resync()
        // Reboot 20 s of real time later.
        h.boot = 2
        h.clocks.elapsed = 10_000
        h.clocks.wall += 20_000
        h.controller.resync()

        val s = h.controller.snapshot.first()!!
        assertThat(h.controller.readout(s).remainingMillis).isEqualTo(50_000)
        assertThat(h.scheduler.scheduledAt).isEqualTo(10_000 + 50_000)
    }

    @Test
    fun `end match writes the clamped duration and mode to the linked match`() = runTest {
        h.matches.upsert(Match("m1", 1, 1, MatchStatus.PENDING, GameMode.QUICK, participants = emptyList()))
        h.controller.start(h.repository.selectedPreset(), 4, matchId = "m1", mode = GameMode.LONG)
        h.clocks.advance(47 * 60_000L + 10_000)
        h.controller.perform(TimerCommand.EndMatch)

        val match = h.matches.state.value.single()
        assertThat(match.durationMin).isEqualTo(47)
        assertThat(match.mode).isEqualTo(GameMode.LONG)
    }

    @Test
    fun `a forgotten timer logs at most the maximum duration`() = runTest {
        h.matches.upsert(Match("m1", 1, 1, MatchStatus.PENDING, GameMode.QUICK, participants = emptyList()))
        h.controller.start(h.repository.selectedPreset(), 4, matchId = "m1")
        h.clocks.advance(3L * 24 * 60 * 60_000)
        h.controller.perform(TimerCommand.EndMatch)

        assertThat(h.matches.state.value.single().durationMin).isEqualTo(MatchDuration.MAX_MINUTES)
    }

    @Test
    fun `starting over an ended linked session first writes its missing duration`() = runTest {
        h.matches.upsert(Match("m1", 1, 1, MatchStatus.PENDING, GameMode.QUICK, participants = emptyList()))
        h.controller.start(h.repository.selectedPreset(), 4, matchId = "m1")
        h.clocks.advance(30 * 60_000L)
        h.controller.perform(TimerCommand.EndMatch)
        // The write was lost (e.g. ended by an older version); the summary was never dismissed.
        h.matches.upsert(h.matches.state.value.single().copy(durationMin = null))
        h.clocks.advance(5 * 60_000L)

        h.controller.start(h.repository.selectedPreset(), 4)

        assertThat(h.matches.state.value.single().durationMin).isEqualTo(30)
        assertThat(h.controller.snapshot.first()!!.matchId).isNull()
    }

    @Test
    fun `cold start resync after a missed auto-advance alarm alerts once`() = runTest {
        startTable(autoAdvance = true)
        val key = h.scheduler.stepKey
        h.clocks.advance(90_500)
        // Process start: the startup provider re-syncs before the alarm broadcast is delivered.
        h.controller.resync()
        h.controller.onAlarm(key)

        assertThat(h.notifier.alerts).containsExactly("Pre-round strategy")
        assertThat(h.controller.snapshot.first()!!.step.phase!!.kind).isEqualTo(PhaseKind.TURN)
    }

    @Test
    fun `alarm then resync for the same phase end alerts once`() = runTest {
        startTable(autoAdvance = true)
        val key = h.scheduler.stepKey
        h.clocks.advance(90_500)
        h.controller.onAlarm(key)
        h.controller.resync()

        assertThat(h.notifier.alerts).containsExactly("Pre-round strategy")
    }

    @Test
    fun `resync does not alert when nothing ended or the timer screen is visible`() = runTest {
        startTable(autoAdvance = true)
        h.clocks.advance(10_000)
        h.controller.resync()
        assertThat(h.notifier.alerts).isEmpty()

        h.controller.uiVisible = true
        h.clocks.advance(81_000)
        h.controller.resync()
        assertThat(h.notifier.alerts).isEmpty()
        assertThat(h.controller.snapshot.first()!!.step.phase!!.kind).isEqualTo(PhaseKind.TURN)
    }

    @Test
    fun `resync re-posts a swiped-away notification while the session runs`() = runTest {
        startTable()
        h.notifier.cancelOngoing() // user swiped it away (Android 14+)
        h.controller.resync()
        assertThat(h.notifier.ongoing).isNotNull()

        h.controller.perform(TimerCommand.EndMatch)
        h.controller.resync()
        assertThat(h.notifier.ongoing).isNull()
    }

    @Test
    fun `notification access is reported from the notifier`() {
        assertThat(h.controller.notificationsEnabled()).isTrue()
        h.notifier.enabled = false
        assertThat(h.controller.notificationsEnabled()).isFalse()
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
