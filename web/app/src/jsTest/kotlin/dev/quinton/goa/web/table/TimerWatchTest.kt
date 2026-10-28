package dev.quinton.goa.web.table

import dev.quinton.goa.core.domain.ElapsedClock
import dev.quinton.goa.core.domain.WallClock
import dev.quinton.goa.core.testing.FakeMatchRepository
import dev.quinton.goa.feature.timer.TimerText
import dev.quinton.goa.feature.timer.session.TimerCommand
import dev.quinton.goa.web.storage.MemoryKeyValueStore
import dev.quinton.goa.web.timer.TimerWatch
import dev.quinton.goa.web.timer.WebTimerController
import dev.quinton.goa.web.timer.WebTimerRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

private fun bundled(path: String): String {
    val root = js("process.env.GOA_REPO_ROOT") as String? ?: error("GOA_REPO_ROOT is not set")
    val fs = js("require('fs')")
    return fs.readFileSync("$root/app/src/main/assets/$path", "utf8") as String
}

private class WatchClock(var now: Long = 1_000_000L) : ElapsedClock, WallClock {
    override fun elapsedMillis() = now

    override fun nowMillis() = now
}

/**
 * A phase that runs out while the timer screen isn't showing (the player opened Levels and defeats, the
 * rules, another tab): the phone's alarm auto-advances and alerts once with these words; so must the web.
 */
class TimerWatchTest {
    private suspend fun started(autoAdvance: Boolean): Triple<WebTimerController, WatchClock, Long> {
        val clock = WatchClock()
        val repository = WebTimerRepository(MemoryKeyValueStore(), TableAssets { bundled(it) })
        val controller = WebTimerController(repository, { FakeMatchRepository() }, clock, clock)
        repository.load()
        val preset = repository.presets.first().first { p -> p.phases.any { it.enabled && it.seconds != null } }
        controller.start(preset.copy(autoAdvance = autoAdvance), actionSlots = 4)
        // Walk to the first timed step.
        while (controller.readout(controller.snapshot.first()!!).remainingMillis == null) {
            controller.perform(TimerCommand.Next)
        }
        val remaining = controller.readout(controller.snapshot.first()!!).remainingMillis!!
        return Triple(controller, clock, remaining)
    }

    @Test
    fun aPhaseThatRunsOutAwayFromTheTimerAutoAdvancesAndAlertsOnce() = runTest {
        val (controller, clock, remaining) = started(autoAdvance = true)
        val watch = TimerWatch(controller)
        val before = controller.snapshot.first()!!
        assertNull(watch.check(), "the phase hasn't run out")

        clock.now += remaining + 1
        val alert = assertNotNull(watch.check())
        val after = controller.snapshot.first()!!
        assertEquals("Time's up: ${TimerText.endedTitle(before.step, before.round)}", alert.title)
        assertEquals("Now: ${TimerText.stepTitle(after.step)}", alert.text)
        assertEquals(true, after.stepIndex != before.stepIndex || after.round != before.round, "auto-advanced")
        assertNull(watch.check(), "one alert per phase")
    }

    @Test
    fun withoutAutoAdvanceTheAlertSaysOvertimeIsCountingAndThePhaseStays() = runTest {
        val (controller, clock, remaining) = started(autoAdvance = false)
        val watch = TimerWatch(controller)
        val before = controller.snapshot.first()!!
        clock.now += remaining + 1
        val alert = assertNotNull(watch.check())
        val after = controller.snapshot.first()!!
        assertEquals(before.stepIndex, after.stepIndex)
        assertEquals("Overtime is counting · ${TimerText.nextText(after)}", alert.text)
        clock.now += 10_000
        assertNull(watch.check(), "overtime doesn't alert again")
    }

    @Test
    fun aPhaseAlreadyInOvertimeOnTheTimerScreenDoesntAlertAgainOnLeavingIt() = runTest {
        val (controller, clock, remaining) = started(autoAdvance = false)
        clock.now += remaining + 5_000 // ran out while the timer screen was showing (it cued there)
        val watch = TimerWatch(controller)
        watch.arm()
        assertNull(watch.check())
        controller.perform(TimerCommand.Next)
        val next = controller.readout(controller.snapshot.first()!!).remainingMillis
        if (next != null) {
            clock.now += next + 1
            assertNotNull(watch.check(), "the next phase to run out alerts")
        }
    }
}
