package dev.quinton.goa.core.game.ai.heuristic.heroes

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.board.MinionType
import org.junit.Test

class NebkherHeuristicsTest {
    @Test
    fun `finishes 2v2 and 3v3 at strengths 5 and 10`() {
        BatchBFixture.finishes("nebkher")
    }

    @Test
    fun `on turn 1 with no adjacent minion Mind Grip cannot start`() {
        val session = BatchBFixture.session {
            turn = 1
            hero("nebkher", Team.RED, Hex(-5, -6)) { hand("nebkher-gold", "nebkher-green-1") }
            hero("arien", Team.BLUE, Hex(-2, -6))
        }
        val (plain, noted, table) = BatchBFixture.picks(session, NebkherHeuristics)
        assertWithMessage(table).that(plain).isEqualTo(Choice.Card("nebkher-gold"))
        assertWithMessage(table).that(noted).isEqualTo(Choice.Card("nebkher-green-1"))
    }

    @Test
    fun `on turn 1 Mind Grip still defeats an adjacent minion`() {
        val session = BatchBFixture.session {
            turn = 1
            hero("nebkher", Team.RED, Hex(-5, -6)) { hand("nebkher-gold", "nebkher-green-1") }
            hero("arien", Team.BLUE, Hex(11, -7))
            minion(Team.BLUE, MinionType.MELEE, Hex(-4, -6))
        }
        val (_, noted, table) = BatchBFixture.picks(session, NebkherHeuristics)
        assertWithMessage(table).that(noted).isEqualTo(Choice.Card("nebkher-gold"))
    }
}
