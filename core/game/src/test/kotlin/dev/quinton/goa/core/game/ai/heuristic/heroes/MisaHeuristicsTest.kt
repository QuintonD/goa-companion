package dev.quinton.goa.core.game.ai.heuristic.heroes

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

class MisaHeuristicsTest {
    @Test
    fun `finishes 2v2 and 3v3 at strengths 5 and 10`() {
        BatchBFixture.finishes("misa")
    }

    @Test
    fun `gold places her when she is still outside the battle zone`() {
        val session = BatchBFixture.session {
            turn = 3
            hero("misa", Team.RED, Hex(-5, -6))
            hero("arien", Team.BLUE, Hex(11, -7))
        }
        val plain = BatchBFixture.pick(session, MisaHeuristics, adjust = false)
        val noted = BatchBFixture.pick(session, MisaHeuristics, adjust = true)
        assertWithMessage("plain=$plain noted=$noted").that(noted).isEqualTo(Choice.Card("misa-gold"))
        assertThat(plain).isNotEqualTo(noted)
    }
}
