package dev.quinton.goa.core.game.ai.heuristic.heroes

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

class GydionHeuristicsTest {
    @Test
    fun `finishes 2v2 and 3v3 at strengths 5 and 10`() {
        BatchBFixture.finishes("gydion")
    }

    @Test
    fun `an empty spellbook plays Prepare instead of the walk`() {
        val session = BatchBFixture.session {
            turn = 1
            hero("gydion", Team.RED, Hex(-5, -6))
            hero("arien", Team.BLUE, Hex(11, -7))
        }
        val plain = BatchBFixture.pick(session, GydionHeuristics, adjust = false)
        val noted = BatchBFixture.pick(session, GydionHeuristics, adjust = true)
        assertWithMessage("plain=$plain noted=$noted").that(noted).isEqualTo(Choice.Card("gydion-silver"))
        assertThat(plain).isNotEqualTo(noted)
    }
}
