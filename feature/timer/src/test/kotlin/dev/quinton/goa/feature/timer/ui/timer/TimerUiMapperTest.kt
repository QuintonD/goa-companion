package dev.quinton.goa.feature.timer.ui.timer

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.domain.timer.TimerEngine
import dev.quinton.goa.core.model.settings.TimerLayout
import dev.quinton.goa.feature.timer.FakeClocks
import dev.quinton.goa.feature.timer.FileTimerAssets
import kotlinx.coroutines.test.runTest
import org.junit.Test

class TimerUiMapperTest {
    private val clocks = FakeClocks()
    private val engine = TimerEngine(clocks, clocks)
    private val assets = FileTimerAssets()

    private suspend fun tableRules() = engine.start(
        "s",
        assets.presetCatalog().presets.first { it.id == "table-rules" },
        assets.rules().endOfRound,
        actionSlots = 4,
    )

    @Test
    fun `round rows show where the round is and read out for TalkBack`() = runTest {
        val s = engine.jumpTo(tableRules(), 6) // turn 2, play a card
        clocks.advance(4_000)
        val active = TimerUiMapper.active(s, engine.readout(s), seats = null, layout = TimerLayout.ROUND)
        val rows = active.rows

        assertThat(active.stepIndex).isEqualTo(6)
        assertThat(rows[5].description(active.spokenClock)).isEqualTo("Turn 1, Action, Hero 4 of 4, done")
        assertThat(rows[6].description(active.spokenClock))
            .isEqualTo("Turn 2, Play a card, current, 26 seconds left")
        assertThat(rows[6].jumpName).isEqualTo("Turn 2 · Play a card")
        assertThat(rows[7].description("")).isEqualTo("Turn 2, Action, Hero 1 of 4, upcoming, 30 seconds")

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
