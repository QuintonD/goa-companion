package dev.quinton.goa.core.game.ai.heuristic.heroes

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

class MrakHeuristicsTest {
    @Test
    fun `finishes 2v2 and 3v3 at strengths 5 and 10`() {
        BatchBFixture.finishes("mrak")
    }

    @Test
    fun `seismic is not played at a hero who is not touching terrain or a rock`() {
        val session = BatchBFixture.session {
            turn = 2
            hero("mrak", Team.RED, Hex(-5, -6))
            hero("arien", Team.BLUE, Hex(-5, -4))
        }
        HeroBookCases.noteAvoids(MrakHeuristics, session, "mrak-red-1")
    }
}
