package dev.quinton.goa.core.domain.timer

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.model.match.Team
import kotlin.math.abs
import kotlin.random.Random
import org.junit.Test

class TimerEngineTest {
    private val clocks = FakeClocks()
    private val engine = TimerEngine(clocks, clocks)

    private fun start(preset: TimerPreset = tableRules(), slots: Int = 4, matchId: String? = null) =
        engine.start("session", preset, endOfRound, slots, matchId)

    private fun TimerSnapshot.nextTimes(n: Int): TimerSnapshot {
        var s = this
        repeat(n) { s = engine.next(s) }
        return s
    }

    @Test
    fun `starts running on round 1 strategy with the full countdown`() {
        val s = start(matchId = "m1")
        val r = engine.readout(s)

        assertThat(s.status).isEqualTo(TimerStatus.RUNNING)
        assertThat(s.matchId).isEqualTo("m1")
        assertThat(r.round).isEqualTo(1)
        assertThat(r.step.phase!!.kind).isEqualTo(PhaseKind.STRATEGY)
        assertThat(r.remainingMillis).isEqualTo(90_000)
        assertThat(r.fractionRemaining).isEqualTo(1f)
        assertThat(r.phaseEndElapsed).isEqualTo(clocks.elapsed + 90_000)
    }

    @Test
    fun `remaining time is derived from timestamps`() {
        val s = start()
        clocks.advanceSeconds(30.0)

        val r = engine.readout(s)
        assertThat(r.remainingMillis).isEqualTo(60_000)
        assertThat(r.elapsedInStepMillis).isEqualTo(30_000)
        assertThat(r.fractionRemaining).isWithin(1e-6f).of(2f / 3f)
    }

    @Test
    fun `pause freezes the countdown and resume continues it`() {
        var s = start()
        clocks.advanceSeconds(30.0)
        s = engine.pause(s)
        clocks.advanceSeconds(100.0)

        assertThat(engine.readout(s).remainingMillis).isEqualTo(60_000)
        assertThat(engine.readout(s).phaseEndElapsed).isNull()
        assertThat(engine.pause(s)).isEqualTo(s)

        s = engine.resume(s)
        clocks.advanceSeconds(10.0)
        assertThat(engine.readout(s).remainingMillis).isEqualTo(50_000)
        assertThat(engine.readout(s).phaseEndElapsed).isEqualTo(clocks.elapsed + 50_000)
        assertThat(engine.resume(s)).isEqualTo(s)
    }

    @Test
    fun `toggle pause alternates`() {
        var s = start()
        s = engine.togglePause(s)
        assertThat(s.status).isEqualTo(TimerStatus.PAUSED)
        s = engine.togglePause(s)
        assertThat(s.status).isEqualTo(TimerStatus.RUNNING)
    }

    @Test
    fun `many pause resume cycles do not lose or gain time`() {
        var s = start()
        val random = Random(7)
        var running = 0L
        repeat(500) {
            val run = random.nextLong(1, 120)
            clocks.advance(run)
            running += run
            s = engine.pause(s)
            clocks.advance(random.nextLong(1, 5_000))
            s = engine.resume(s)
        }
        assertThat(engine.readout(s).elapsedInStepMillis).isEqualTo(running)
    }

    @Test
    fun `overtime counts up without moving on`() {
        val s = start()
        clocks.advanceSeconds(100.0)

        val r = engine.readout(s)
        assertThat(r.remainingMillis).isEqualTo(-10_000)
        assertThat(r.overtime).isTrue()
        assertThat(r.fractionRemaining).isEqualTo(0f)
        assertThat(engine.advanceDue(s)).isEqualTo(s)
    }

    @Test
    fun `next starts the following step fresh and running`() {
        var s = start()
        clocks.advanceSeconds(40.0)
        s = engine.pause(s)
        s = engine.next(s)

        val r = engine.readout(s)
        assertThat(s.status).isEqualTo(TimerStatus.RUNNING)
        assertThat(r.step.turn).isEqualTo(1)
        assertThat(r.remainingMillis).isEqualTo(30_000)
    }

    @Test
    fun `next across the round boundary starts the next round`() {
        var s = start()
        s = s.nextTimes(s.plan.lastIndex)
        assertThat(s.step.endStep!!.id).isEqualTo("pity_coin")

        s = engine.next(s)
        assertThat(s.round).isEqualTo(2)
        assertThat(s.stepIndex).isEqualTo(0)
        assertThat(s.step.phase!!.kind).isEqualTo(PhaseKind.STRATEGY)
    }

    @Test
    fun `back across the round boundary returns to the previous round's last step`() {
        var s = start().nextTimes(11)
        assertThat(s.round).isEqualTo(2)

        s = engine.back(s)
        assertThat(s.round).isEqualTo(1)
        assertThat(s.stepIndex).isEqualTo(s.plan.lastIndex)
        assertThat(engine.readout(s).elapsedInStepMillis).isEqualTo(0)
    }

    @Test
    fun `back on the very first step restarts it`() {
        var s = start()
        clocks.advanceSeconds(12.0)
        s = engine.back(s)

        assertThat(s.round).isEqualTo(1)
        assertThat(s.stepIndex).isEqualTo(0)
        assertThat(engine.readout(s).remainingMillis).isEqualTo(90_000)
    }

    @Test
    fun `back right after next undoes it and the previous step keeps its time`() {
        var s = start().nextTimes(3)
        clocks.advanceSeconds(20.0)
        s = engine.next(s)
        clocks.advanceSeconds(2.0)
        s = engine.back(s)

        assertThat(s.stepIndex).isEqualTo(3)
        assertThat(s.status).isEqualTo(TimerStatus.RUNNING)
        // 20 s before the accidental Next + 2 s after it: the table never stopped playing.
        assertThat(engine.readout(s).remainingMillis).isEqualTo(8_000)
        assertThat(engine.readout(s).phaseEndElapsed).isEqualTo(clocks.elapsed + 8_000)
        assertThat(s.undo).isNull()

        // Only one level: a second Back goes to the previous step with a fresh timer.
        s = engine.back(s)
        assertThat(s.stepIndex).isEqualTo(2)
        assertThat(engine.readout(s).remainingMillis).isEqualTo(30_000)
    }

    @Test
    fun `undo restores a paused step paused, with the time it had`() {
        var s = start()
        clocks.advanceSeconds(40.0)
        s = engine.pause(s)
        clocks.advanceSeconds(300.0)
        s = engine.next(s)
        clocks.advanceSeconds(3.0)
        s = engine.back(s)

        assertThat(s.stepIndex).isEqualTo(0)
        assertThat(s.status).isEqualTo(TimerStatus.PAUSED)
        assertThat(engine.readout(s).remainingMillis).isEqualTo(50_000)
    }

    @Test
    fun `back after the undo window goes to the previous step with a fresh timer`() {
        var s = start().nextTimes(3)
        clocks.advanceSeconds(20.0)
        s = engine.next(s)
        clocks.advanceSeconds(TimerEngine.UNDO_WINDOW_MS / 1000.0 + 1)
        s = engine.back(s)

        assertThat(s.stepIndex).isEqualTo(3)
        assertThat(engine.readout(s).remainingMillis).isEqualTo(30_000)
    }

    @Test
    fun `undo works across the round boundary and restores the round`() {
        var s = start().nextTimes(10)
        assertThat(s.round).isEqualTo(1)
        s = engine.next(s)
        assertThat(s.round).isEqualTo(2)
        s = engine.back(s)

        assertThat(s.round).isEqualTo(1)
        assertThat(s.stepIndex).isEqualTo(s.plan.lastIndex)
    }

    @Test
    fun `restart and auto advance drop the undo point`() {
        var s = engine.next(start())
        assertThat(engine.back(engine.restartStep(s)).stepIndex).isEqualTo(0)
        assertThat(engine.back(engine.restartStep(s)).undo).isNull()

        s = engine.next(start(tableRules(autoAdvance = true)))
        clocks.advanceSeconds(30.0)
        s = engine.advanceDue(s)
        assertThat(s.undo).isNull()
    }

    @Test
    fun `jump starts the target fresh and running, clears the Hurry up, and Back undoes it`() {
        var s = start().nextTimes(2) // turn 2
        clocks.advanceSeconds(10.0)
        s = engine.startShotClock(engine.pause(s))
        s = engine.jumpTo(s, 9) // Upgrade on Level Up

        assertThat(s.round).isEqualTo(1)
        assertThat(s.step.endStep!!.id).isEqualTo("level_up")
        assertThat(s.status).isEqualTo(TimerStatus.RUNNING)
        assertThat(engine.readout(s).remainingMillis).isEqualTo(120_000)
        assertThat(s.shotClock).isNull()

        // A mis-tapped row: Back returns to turn 2, paused as it was, with its 20 s left.
        clocks.advanceSeconds(3.0)
        s = engine.back(s)
        assertThat(s.stepIndex).isEqualTo(2)
        assertThat(s.status).isEqualTo(TimerStatus.PAUSED)
        assertThat(engine.readout(s).remainingMillis).isEqualTo(20_000)
    }

    @Test
    fun `jump stays in the round and ignores the current step, unknown steps and ended sessions`() {
        var s = engine.timeOut(start(tableRules().copy(timeOutsPerRound = 1)).nextTimes(1), Team.RED)
        s = engine.jumpTo(engine.jumpTo(s, 9), 0)

        assertThat(s.round).isEqualTo(1)
        assertThat(s.stepIndex).isEqualTo(0)
        // Still this round: a time-out used is not handed back by jumping around.
        assertThat(engine.timeOutsLeft(s, Team.RED)).isEqualTo(0)
        assertThat(engine.jumpTo(s, 0)).isEqualTo(s)
        assertThat(engine.jumpTo(s, -1)).isEqualTo(s)
        assertThat(engine.jumpTo(s, s.plan.lastIndex + 1)).isEqualTo(s)
        val ended = engine.endMatch(s)
        assertThat(engine.jumpTo(ended, 3)).isEqualTo(ended)
    }

    @Test
    fun `jump within a turn keeps the heroes already resolved, another turn starts clean`() {
        var s = start(tableRules(actionEnabled = true), slots = 3).nextTimes(2) // turn 1, slot 0
        s = engine.heroResolves(s, seat = 2)
        s = engine.jumpTo(s, 4) // turn 1, slot 2 (slot 1 skipped: the last hero isn't guessed)
        assertThat(s.step.actionSlot).isEqualTo(2)
        assertThat(s.actors).containsExactly(ActionActor(0, 2))

        s = engine.jumpTo(s, 6) // turn 2, slot 0
        assertThat(s.step.turn).isEqualTo(2)
        assertThat(s.actors).isEmpty()
        assertThat(engine.back(s).actors).containsExactly(ActionActor(0, 2))
    }

    @Test
    fun `a user next is ignored in the first half second of a step`() {
        var s = engine.next(start())
        clocks.advance(TimerEngine.NEXT_DEBOUNCE_MS - 1)
        assertThat(engine.next(s, TimerEngine.NEXT_DEBOUNCE_MS)).isEqualTo(s)

        clocks.advance(1)
        s = engine.next(s, TimerEngine.NEXT_DEBOUNCE_MS)
        assertThat(s.stepIndex).isEqualTo(2)

        // A time-out (negative step time) never blocks Next.
        clocks.advanceSeconds(1.0)
        s = engine.timeOut(s.copy(preset = s.preset.copy(timeOutsPerRound = 1)), Team.RED)
        assertThat(engine.next(s, TimerEngine.NEXT_DEBOUNCE_MS).stepIndex).isEqualTo(3)
    }

    @Test
    fun `a tap landing just after an auto advance does not skip the new step`() {
        var s = engine.next(start(tableRules(autoAdvance = true))) // turn 1, 30 s
        clocks.advanceSeconds(30.2)
        s = engine.advanceDue(s)
        assertThat(s.stepIndex).isEqualTo(2)

        assertThat(engine.next(s, TimerEngine.NEXT_DEBOUNCE_MS).stepIndex).isEqualTo(2)
        clocks.advanceSeconds(0.3)
        assertThat(engine.next(s, TimerEngine.NEXT_DEBOUNCE_MS).stepIndex).isEqualTo(3)
    }

    @Test
    fun `restart keeps the paused state`() {
        var s = start()
        clocks.advanceSeconds(20.0)
        s = engine.pause(s)
        s = engine.restartStep(s)

        assertThat(s.status).isEqualTo(TimerStatus.PAUSED)
        assertThat(engine.readout(s).remainingMillis).isEqualTo(90_000)

        s = engine.resume(s)
        clocks.advanceSeconds(5.0)
        s = engine.restartStep(s)
        assertThat(engine.readout(s).remainingMillis).isEqualTo(90_000)
        assertThat(s.status).isEqualTo(TimerStatus.RUNNING)
    }

    @Test
    fun `skip to end of round jumps to minion battle and is a no-op once there`() {
        var s = start().nextTimes(2)
        s = engine.skipToEndOfRound(s)

        assertThat(s.step.endStepNumber).isEqualTo(1)
        assertThat(s.step.endStep!!.id).isEqualTo("minion_battle")
        val again = engine.next(s)
        assertThat(engine.skipToEndOfRound(again)).isEqualTo(again)
    }

    @Test
    fun `end match freezes everything and ignores later operations`() {
        var s = start()
        clocks.advanceSeconds(45.0)
        s = engine.next(s)
        clocks.advanceSeconds(15.0)
        s = engine.endMatch(s)
        clocks.advanceSeconds(600.0)

        assertThat(s.status).isEqualTo(TimerStatus.ENDED)
        assertThat(engine.readout(s).matchElapsedMillis).isEqualTo(60_000)
        assertThat(engine.next(s)).isEqualTo(s)
        assertThat(engine.back(s)).isEqualTo(s)
        assertThat(engine.resume(s)).isEqualTo(s)
        assertThat(engine.restartStep(s)).isEqualTo(s)
        assertThat(engine.skipToEndOfRound(s)).isEqualTo(s)
        assertThat(engine.endMatch(s)).isEqualTo(s)
    }

    @Test
    fun `match time includes pauses`() {
        var s = start()
        clocks.advanceSeconds(10.0)
        s = engine.pause(s)
        clocks.advanceSeconds(50.0)

        assertThat(engine.readout(s).matchElapsedMillis).isEqualTo(60_000)
    }

    @Test
    fun `untimed steps count up and never go into overtime`() {
        val untimed = bundledPresets.presets.single { it.id == "untimed" }
        val s = engine.start("s", untimed, endOfRound, 4)
        clocks.advanceSeconds(500.0)

        val r = engine.readout(s)
        assertThat(r.remainingMillis).isNull()
        assertThat(r.overtime).isFalse()
        assertThat(r.elapsedInStepMillis).isEqualTo(500_000)
        assertThat(r.phaseEndElapsed).isNull()
        assertThat(r.fractionRemaining).isEqualTo(1f)
    }

    @Test
    fun `checklist steps are untimed`() {
        val s = engine.skipToEndOfRound(start())
        val r = engine.readout(s)

        assertThat(r.step.timed).isFalse()
        assertThat(r.remainingMillis).isNull()
    }

    @Test
    fun `auto advance chains timed steps on exact boundaries and stops at the checklist`() {
        var s = start(tableRules(autoAdvance = true))
        val startElapsed = clocks.elapsed
        clocks.advanceSeconds(90.0 + 30 * 4 + 7.5)
        s = engine.advanceDue(s)

        assertThat(s.step.endStep!!.id).isEqualTo("minion_battle")
        assertThat(s.runningSince).isEqualTo(startElapsed + 210_000)
        assertThat(engine.readout(s).elapsedInStepMillis).isEqualTo(7_500)
    }

    @Test
    fun `auto advance does nothing while paused or before zero`() {
        var s = start(tableRules(autoAdvance = true))
        clocks.advanceSeconds(89.0)
        assertThat(engine.advanceDue(s)).isEqualTo(s)
        s = engine.pause(s)
        clocks.advanceSeconds(10.0)
        assertThat(engine.advanceDue(s)).isEqualTo(s)
    }

    @Test
    fun `drift stays under 250 ms over 30 minutes of irregular ticks`() {
        // Every step timed (no checklist) so auto-advance runs continuously for 30 minutes.
        val preset = TimerPreset(
            id = "loop",
            name = "Loop",
            autoAdvance = true,
            phases = listOf(
                phase("strategy", PhaseKind.STRATEGY, PhaseScope.ROUND_START, 90),
                phase("turn", PhaseKind.TURN, PhaseScope.TURN, 30),
                phase("upgrade", PhaseKind.UPGRADE, PhaseScope.ROUND_END, 120),
            ),
        )
        var s = engine.start("drift", preset, emptyList(), 4)
        val random = Random(42)
        val total = 30 * 60 * 1000L
        var simulated = 0L
        while (simulated < total) {
            val tick = random.nextLong(1, 1_200).coerceAtMost(total - simulated)
            clocks.advance(tick)
            simulated += tick
            s = engine.advanceDue(s)
        }
        // 330 s per round: 1800 s = 5 rounds (1650 s) + 90 s strategy + 2 turns -> turn 3 at 0 s.
        val r = engine.readout(s)
        assertThat(s.round).isEqualTo(6)
        assertThat(r.step.turn).isEqualTo(3)
        assertThat(abs(r.elapsedInStepMillis)).isLessThan(250)
        assertThat(r.matchElapsedMillis).isEqualTo(total)
    }

    @Test
    fun `cues fire once when their threshold is crossed`() {
        val s = start().nextTimes(1) // 30 s turn, cues 10, 5, 0
        val step = s.step

        assertThat(engine.cuesCrossed(step, 19_900, 20_000)).containsExactly(10)
        assertThat(engine.cuesCrossed(step, 20_000, 20_100)).isEmpty()
        assertThat(engine.cuesCrossed(step, 24_000, 30_500)).containsExactly(5, 0).inOrder()
        assertThat(engine.cuesCrossed(step, 30_000, 31_000)).isEmpty()
        assertThat(engine.cuesCrossed(step, 5_000, 5_000)).isEmpty()
    }

    @Test
    fun `untimed steps never cue`() {
        val s = engine.skipToEndOfRound(start())
        assertThat(engine.cuesCrossed(s.step, 0, 1_000_000)).isEmpty()
    }

    @Test
    fun `heroes resolve slot by slot and the list resets on the next turn`() {
        var s = start(tableRules(actionEnabled = true), slots = 3).nextTimes(2)
        assertThat(s.step.isAction).isTrue()
        assertThat(s.step.actionSlot).isEqualTo(0)

        s = engine.heroResolves(s, seat = 2)
        assertThat(s.actors).containsExactly(ActionActor(0, 2))
        s = engine.heroResolves(s, seat = 0)
        assertThat(s.step.actionSlot).isEqualTo(1)
        assertThat(s.actors).containsExactly(ActionActor(0, 2), ActionActor(1, 0))
        s = engine.next(s)
        assertThat(s.step.actionSlot).isEqualTo(2)
        // Last slot of the turn: the one hero left (seat 1) is picked automatically.
        assertThat(s.actors).containsExactly(ActionActor(0, 2), ActionActor(1, 0), ActionActor(2, 1))

        s = engine.next(s) // turn 2
        assertThat(s.step.turn).isEqualTo(2)
        assertThat(s.actors).isEmpty()
    }

    @Test
    fun `last hero is not guessed when an earlier slot was not recorded`() {
        var s = start(tableRules(actionEnabled = true), slots = 3).nextTimes(2)
        s = engine.heroResolves(s, seat = 2)
        s = engine.next(engine.next(s)) // slot 1 skipped without a hero

        assertThat(s.step.actionSlot).isEqualTo(2)
        assertThat(s.actors).containsExactly(ActionActor(0, 2))
    }

    @Test
    fun `tie breaker coin side flips during play and is kept across steps`() {
        var s = engine.start("s", tableRules(), endOfRound, 4, tieBreaker = Team.RED)
        s = engine.flipTieBreaker(s)
        assertThat(s.tieBreaker).isEqualTo(Team.BLUE)
        s = engine.next(engine.next(s))
        assertThat(s.tieBreaker).isEqualTo(Team.BLUE)
        assertThat(engine.flipTieBreaker(s).tieBreaker).isEqualTo(Team.RED)
        val ended = engine.endMatch(s)
        assertThat(engine.flipTieBreaker(ended)).isEqualTo(ended)
    }

    @Test
    fun `an untracked coin is never assumed, its side is set explicitly`() {
        var s = start()
        assertThat(engine.flipTieBreaker(s).tieBreaker).isNull()

        s = engine.setTieBreaker(s, Team.BLUE)
        assertThat(s.tieBreaker).isEqualTo(Team.BLUE)
        assertThat(engine.flipTieBreaker(s).tieBreaker).isEqualTo(Team.RED)
        assertThat(engine.setTieBreaker(s, null).tieBreaker).isNull()
    }

    @Test
    fun `shot clock counts down on demand, pauses with the timer and clears on the next step`() {
        var s = start()
        clocks.advanceSeconds(100.0) // deep in overtime
        s = engine.startShotClock(s)
        clocks.advanceSeconds(12.0)
        assertThat(engine.readout(s).shotClockRemainingMillis).isEqualTo(18_000)

        s = engine.pause(s)
        clocks.advanceSeconds(60.0)
        assertThat(engine.readout(s).shotClockRemainingMillis).isEqualTo(18_000)
        s = engine.resume(s)
        clocks.advanceSeconds(20.0)
        assertThat(engine.readout(s).shotClockRemainingMillis).isEqualTo(-2_000)
        // The phase clock is unaffected.
        assertThat(engine.readout(s).remainingMillis).isEqualTo(90_000 - 132_000)

        assertThat(engine.readout(engine.cancelShotClock(s)).shotClockRemainingMillis).isNull()
        assertThat(engine.next(s).shotClock).isNull()
    }

    @Test
    fun `shot clock length comes from the preset and can be turned off`() {
        val s = engine.startShotClock(start(tableRules().copy(shotClockSeconds = 45)))
        assertThat(engine.readout(s).shotClockRemainingMillis).isEqualTo(45_000)

        val off = start(tableRules().copy(shotClockSeconds = null))
        assertThat(engine.startShotClock(off)).isEqualTo(off)
    }

    @Test
    fun `shot clock survives a reboot rebase`() {
        var s = engine.startShotClock(start())
        clocks.advanceSeconds(10.0)
        s = s.rebased(clocks.elapsed, clocks.wall, currentBootCount = null)
        clocks.elapsed = 5_000L // reboot: elapsed restarts
        clocks.wall += 4_000L // 4 s powered off

        val rebased = s.rebased(clocks.elapsed, clocks.wall, currentBootCount = null)
        assertThat(engine.readout(rebased).shotClockRemainingMillis).isEqualTo(16_000)
    }

    @Test
    fun `time-outs add time to the current phase, per team per round`() {
        val preset = tableRules().copy(timeOutsPerRound = 1, timeOutSeconds = 40)
        var s = engine.next(start(preset)) // turn 1, 30 s
        clocks.advanceSeconds(35.0) // 5 s overtime
        s = engine.timeOut(s, Team.RED)

        assertThat(engine.readout(s).remainingMillis).isEqualTo(35_000)
        assertThat(engine.readout(s).phaseEndElapsed).isEqualTo(clocks.elapsed + 35_000)
        assertThat(engine.timeOutsLeft(s, Team.RED)).isEqualTo(0)
        assertThat(engine.timeOut(s, Team.RED)).isEqualTo(s)
        assertThat(engine.timeOutsLeft(s, Team.BLUE)).isEqualTo(1)

        s = engine.next(s) // same round: still used
        assertThat(engine.timeOutsLeft(s, Team.RED)).isEqualTo(0)
        s = engine.next(s.copy(stepIndex = s.plan.lastIndex)) // round 2
        assertThat(s.round).isEqualTo(2)
        assertThat(engine.timeOutsLeft(s, Team.RED)).isEqualTo(1)
    }

    @Test
    fun `time-outs are off by default and ignored on untimed steps`() {
        assertThat(engine.timeOut(start(), Team.RED)).isEqualTo(start())
        val s = engine.skipToEndOfRound(start(tableRules().copy(timeOutsPerRound = 1)))
        assertThat(s.step.timed).isFalse()
        assertThat(engine.timeOut(s, Team.BLUE)).isEqualTo(s)
    }

    @Test
    fun `hero resolves is ignored outside action steps`() {
        val s = start()
        assertThat(engine.heroResolves(s, 1)).isEqualTo(s)
    }

    @Test
    fun `snapshot survives a JSON round trip`() {
        var s = start(tableRules(actionEnabled = true), slots = 5, matchId = "m")
        s = engine.heroResolves(s.nextTimes(2), 4)
        clocks.advanceSeconds(3.0)
        s = engine.pause(s)

        val decoded = TimerJson.decodeSnapshot(TimerJson.encodeSnapshot(s))
        assertThat(decoded).isEqualTo(s)
        assertThat(decoded.plan.steps).isEqualTo(s.plan.steps)
        assertThat(engine.readout(decoded)).isEqualTo(engine.readout(s))
    }

    @Test
    fun `reboot rebases the running step using the wall clock`() {
        var s = engine.start("s", tableRules(), endOfRound, 4, bootCount = 3)
        clocks.advanceSeconds(10.0)
        s = engine.pause(engine.resume(s)) // stamp anchors at +10 s
        s = engine.resume(s)

        // Device reboots: elapsed restarts near zero, 60 s of wall time pass.
        clocks.elapsed = 20_000
        clocks.wall += 60_000
        val rebased = s.rebased(clocks.elapsed, clocks.wall, currentBootCount = 4)

        assertThat(engine.readout(rebased).elapsedInStepMillis).isEqualTo(70_000)
        assertThat(engine.readout(rebased).matchElapsedMillis).isEqualTo(70_000)
        assertThat(rebased.bootCount).isEqualTo(4)
    }

    @Test
    fun `rebase without a reboot keeps the timeline and refreshes the anchor`() {
        val s = engine.start("s", tableRules(), endOfRound, 4, bootCount = 3)
        clocks.advanceSeconds(10.0)

        val rebased = s.rebased(clocks.elapsed, clocks.wall, 3)
        assertThat(rebased.copy(anchorElapsed = s.anchorElapsed, anchorWall = s.anchorWall)).isEqualTo(s)
        assertThat(rebased.anchorElapsed).isEqualTo(clocks.elapsed)
        assertThat(rebased.anchorWall).isEqualTo(clocks.wall)
    }

    @Test
    fun `wall clock jump followed by a reboot rebases from the refreshed anchor`() {
        val s = engine.start("s", tableRules(), endOfRound, 4, bootCount = 3)
        clocks.advanceSeconds(20.0)
        // The wall clock is corrected by +5 min (TIME_SET); the session is re-synced and persisted.
        clocks.wall += 5 * 60_000L
        val resynced = s.rebased(clocks.elapsed, clocks.wall, 3)

        // Reboot: elapsed restarts, 20 s more of real time pass.
        clocks.elapsed = 10_000
        clocks.wall += 20_000
        val rebooted = resynced.rebased(clocks.elapsed, clocks.wall, 4)

        assertThat(engine.readout(rebooted).remainingMillis).isEqualTo(50_000)
        assertThat(engine.readout(rebooted).matchElapsedMillis).isEqualTo(40_000)
    }

    @Test
    fun `elapsed going backwards is treated as a reboot even without a boot count`() {
        val s = engine.start("s", tableRules(), endOfRound, 4)
        clocks.elapsed = 5_000
        clocks.wall += 30_000

        val rebased = s.rebased(clocks.elapsed, clocks.wall, null)
        assertThat(engine.readout(rebased).elapsedInStepMillis).isEqualTo(30_000)
    }

    @Test
    fun `match duration rounds to whole minutes and is clamped to a sane range`() {
        assertThat(MatchDuration.minutes(0)).isEqualTo(1)
        assertThat(MatchDuration.minutes(47 * 60_000L + 10_000)).isEqualTo(47)
        assertThat(MatchDuration.minutes(47 * 60_000L + 30_000)).isEqualTo(48)
        assertThat(MatchDuration.minutes(3L * 24 * 60 * 60_000)).isEqualTo(MatchDuration.MAX_MINUTES)
        assertThat(MatchDuration.minutes(-5_000)).isEqualTo(1)
    }
}
