package dev.quinton.goa.feature.board.play

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.AiStrength
import dev.quinton.goa.feature.board.play.setup.NewGameForm
import dev.quinton.goa.feature.board.play.setup.NewGameForms
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
}
