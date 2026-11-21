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
    fun `red with no target beside an ally is a walk, not the attack the printed range suggests`() {
        val session = BatchBFixture.session {
            turn = 3
            hero("hanu", Team.RED, Hex(-5, -6)) { hand("hanu-red-1", "hanu-green-1") }
            hero("arien", Team.BLUE, Hex(-2, -6))
        }
        val (plain, noted, table) = BatchBFixture.picks(session, HanuHeuristics)
        assertWithMessage(table).that(plain).isEqualTo(Choice.Card("hanu-red-1"))
        assertWithMessage(table).that(noted).isEqualTo(Choice.Card("hanu-green-1"))
    }
}
