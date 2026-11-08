package dev.quinton.goa.feature.board.play

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.AiStrength
import dev.quinton.goa.core.game.GameLength
import dev.quinton.goa.feature.board.play.setup.NewGameForm
import dev.quinton.goa.feature.board.play.setup.NewGameForms
import dev.quinton.goa.feature.board.play.setup.startingCounters
import dev.quinton.goa.feature.board.setup.NewBoardForm
import org.junit.Test

/** The New game form kept across process death (ADR-039: the computer's strength, 1–10). */
class NewGameFormsTest {
    @Test
    fun `the strength and the per-seat strengths round-trip`() {
        val form = NewGameForm(
            lineup = NewBoardForm(red = listOf("arien"), blue = listOf("wasp")),
            strength = 7,
            seatStrengths = mapOf("wasp" to 3),
        )
        assertThat(NewGameForms.decode(NewGameForms.encode(form))).isEqualTo(form)
    }

    @Test
    fun `a form kept before the slider reads its levels as their strengths`() {
        val old = """{"red":["arien"],"blue":["wasp","brogan"],"level":"expert","seatLevels":{"wasp":"easy"}}"""
        val form = NewGameForms.decode(old)!!
        assertThat(form.strength).isEqualTo(10)
        assertThat(form.seatStrengths).containsExactly("wasp", 2)
        assertThat(NewGameForms.decode("""{"red":["arien"]}""")!!.strength).isEqualTo(AiStrength.DEFAULT)
    }

    /** Quick and Long say the counters they start with, as the engine sets them up (p.5; sol #17). */
    @Test
    fun `each length names its starting counters`() {
        assertThat(startingCounters(lanes = 1, length = GameLength.QUICK, heroes = 4)).isEqualTo("3 Waves · 4 Lives")
        assertThat(startingCounters(lanes = 1, length = GameLength.LONG, heroes = 4)).isEqualTo("5 Waves · 6 Lives")
        assertThat(startingCounters(lanes = 1, length = GameLength.QUICK, heroes = 6)).isEqualTo("3 Waves · 5 Lives")
    }
}
