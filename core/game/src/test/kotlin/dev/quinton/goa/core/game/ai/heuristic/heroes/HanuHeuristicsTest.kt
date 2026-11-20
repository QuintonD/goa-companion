package dev.quinton.goa.core.game.ai.heuristic.heroes

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

class HanuHeuristicsTest {
    @Test
    fun `finishes 2v2 and 3v3 at strengths 5 and 10`() {
        BatchBFixture.finishes("hanu")
    }

    @Test
    fun `red is not played when no ally is standing on the target`() {
        val session = BatchBFixture.session {
            turn = 2
            hero("hanu", Team.RED, Hex(-5, -6))
            hero("arien", Team.BLUE, Hex(-2, -6))
        }
        val plain = BatchBFixture.pick(session, HanuHeuristics, adjust = false)
        val noted = BatchBFixture.pick(session, HanuHeuristics, adjust = true)
        assertWithMessage("plain=$plain noted=$noted").that(plain).isEqualTo(Choice.Card("hanu-red-1"))
        assertThat(noted).isNotEqualTo(plain)
    }
}
