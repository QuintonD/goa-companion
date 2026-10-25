package dev.quinton.goa.feature.timer.ui.timer

import dev.quinton.goa.core.domain.timer.TimerReadout
import dev.quinton.goa.core.domain.timer.TimerSnapshot
import dev.quinton.goa.core.domain.timer.TimerStatus
import dev.quinton.goa.feature.timer.TimerText

/** The compact "timer running" bar shown on the other tabs. */
data class RunningTimerUi(
    /** Phase, e.g. "Turn". */
    val phase: String,
    /** "Round 2 · Turn 3 of 4". */
    val context: String,
    /** "0:17", "+0:05" in overtime, or elapsed time for untimed steps. */
    val clock: String,
    val paused: Boolean,
    val overtime: Boolean,
    /** TalkBack summary. */
    val description: String,
) {
    companion object {
        fun from(snapshot: TimerSnapshot, readout: TimerReadout): RunningTimerUi {
            val paused = snapshot.status == TimerStatus.PAUSED
            val phase = TimerText.stepTitle(readout.step)
            val where = TimerText.stepContext(
                readout.step,
                snapshot.preset,
                snapshot.endOfRound.size,
                snapshot.actionSlots,
            )
            val context = "Round ${readout.round} · $where"
            val spoken = TimerText.spokenClock(readout)
            return RunningTimerUi(
                phase = phase,
                context = context,
                clock = TimerText.clock(readout),
                paused = paused,
                overtime = readout.overtime,
                description = "Timer ${if (paused) "paused" else "running"}: $phase, $spoken. $context. Open the timer",
            )
        }
    }
}
