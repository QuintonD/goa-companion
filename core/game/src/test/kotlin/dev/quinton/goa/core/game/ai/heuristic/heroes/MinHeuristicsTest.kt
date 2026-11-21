package dev.quinton.goa.core.game.ai.heuristic.heroes

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

class MinHeuristicsTest {
    @Test
    fun `finishes 2v2 and 3v3 at strengths 5 and 10`() {
        BatchBFixture.finishes("min")
    }

    @Test
    fun `with the red still in hand, gold is a bare attack 2 and the smoke bomb comes first`() {
        val session = BatchBFixture.session {
            turn = 2
            hero("min", Team.RED, Hex(-5, -6)) { hand("min-silver", "min-gold", "min-red-1") }
            hero("arien", Team.BLUE, Hex(-4, -6))
        }
        val (plain, noted, table) = BatchBFixture.picks(session, MinHeuristics)
        assertWithMessage(table).that(plain).isEqualTo(Choice.Card("min-gold"))
        assertWithMessage(table).that(noted).isEqualTo(Choice.Card("min-silver"))
    }
}
