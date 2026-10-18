package dev.quinton.goa.core.presentation.timer

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.domain.timer.TimerEngine
import dev.quinton.goa.core.model.settings.TimerLayout
import org.junit.Test

class TimerUiMapperTest {
    private val clocks = FakeClocks()
    private val engine = TimerEngine(clocks, clocks)

    private fun tableRules() = engine.start(
        "s",
        TimerTestData.presets.presets.first { it.id == "table-rules" },
        TimerTestData.rules.endOfRound,
        actionSlots = 4,
    )

    @Test
    fun `round rows show where the round is and read out for TalkBack`() {
        val s = engine.jumpTo(tableRules(), 3) // turn 2, play a card
        clocks.advance(4_000)
        val active = TimerUiMapper.active(s, engine.readout(s), seats = null, layout = TimerLayout.ROUND)
        val rows = active.rows

        assertThat(active.stepIndex).isEqualTo(3)
        assertThat(rows[2].description(active.spokenClock)).isEqualTo("Turn 1, Resolve, done")
        assertThat(rows[3].description(active.spokenClock))
            .isEqualTo("Turn 2, Play a card, current, 26 seconds left")
        assertThat(rows[3].jumpName).isEqualTo("Turn 2 · Play a card")

        // The checklist step that carries the Upgrade timer says so, with its length.
        val levelUp = rows.single { it.title == "Level Up" }
        assertThat(levelUp.context).isEqualTo("Step 5 of 6 · Upgrade timer")
        assertThat(levelUp.length).isEqualTo("2:00")
        assertThat(levelUp.jumpName).isEqualTo("End of round · Level Up")
        assertThat(levelUp.description(active.spokenClock))
            .isEqualTo("End of round, Level Up, Step 5 of 6, Upgrade timer, upcoming, 2 minutes")
        assertThat(rows.last().description("")).endsWith("upcoming, untimed")
    }
}
