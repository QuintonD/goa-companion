package dev.quinton.goa.core.game.ai.heuristic.heroes

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

class IgnatiaHeuristicsTest {
    @Test
    fun `finishes 2v2 and 3v3 at strengths 5 and 10`() {
        BatchBFixture.finishes("ignatia")
    }

    @Test
    fun `gold is held when the face the coin shows has no target`() {
        val session = BatchBFixture.session {
            turn = 2
            // The Blue team's side: gold's blue text needs an adjacent minion, and there is none.
            tieBreaker = Team.BLUE
            hero("ignatia", Team.RED, Hex(-5, -6)) { hand("ignatia-gold", "ignatia-green-1") }
            hero("arien", Team.BLUE, Hex(-2, -6))
        }
        val (plain, noted, table) = BatchBFixture.picks(session, IgnatiaHeuristics)
        assertWithMessage(table).that(plain).isEqualTo(Choice.Card("ignatia-gold"))
        assertWithMessage(table).that(noted).isEqualTo(Choice.Card("ignatia-green-1"))
    }
}
