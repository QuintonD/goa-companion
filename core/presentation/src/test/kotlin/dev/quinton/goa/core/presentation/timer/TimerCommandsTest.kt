package dev.quinton.goa.core.presentation.timer

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.domain.timer.TimerEngine
import org.junit.Test

class TimerCommandsTest {
    private val clocks = FakeClocks()
    private val engine = TimerEngine(clocks, clocks)
    private val start = engine.start(
        "s",
        TimerTestData.presets.presets.first { it.id == "table-rules" },
        TimerTestData.rules.endOfRound,
        actionSlots = 4,
    )

    @Test
    fun `next is debounced and a jump from an earlier round is dropped`() {
        assertThat(TimerCommands.apply(engine, start, TimerCommand.Next)).isSameInstanceAs(start)
        clocks.advance(600)
        val next = TimerCommands.apply(engine, start, TimerCommand.Next)
        assertThat(next.stepIndex).isEqualTo(1)
        assertThat(TimerCommands.advanced(start, next)).isTrue()

        val stale = TimerCommands.apply(engine, next, TimerCommand.JumpTo(round = 2, stepIndex = 3))
        assertThat(stale).isSameInstanceAs(next)
        val jumped = TimerCommands.apply(engine, next, TimerCommand.JumpTo(round = 1, stepIndex = 3))
        assertThat(jumped.stepIndex).isEqualTo(3)
    }

    @Test
    fun `step keys name the session, round, step and phase end`() {
        assertThat(TimerCommands.stepKey(start, 42)).isEqualTo("s:1:0:42")
    }
}
