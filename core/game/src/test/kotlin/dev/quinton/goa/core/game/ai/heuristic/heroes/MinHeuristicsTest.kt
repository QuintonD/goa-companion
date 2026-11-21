package dev.quinton.goa.core.game.ai.heuristic.heroes

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.engine.ActionKind
import dev.quinton.goa.core.game.engine.Timing
import dev.quinton.goa.core.game.heroes.MinHero
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
        HeroBookCases.noteFavours(MinHeuristics, session, "min-silver", "min-gold")
    }

    @Test
    fun `a grenade counts only the heroes its card can reach`() {
        // Review H6: Death Grenade takes up to 1 enemy hero beside it, Holy Death Grenade up to 2.
        fun value(card: String): Double {
            val session = BatchBFixture.session {
                turn = 2
                hero("min", Team.RED, Hex(-5, -6)) { hand("min-gold") }
                hero("arien", Team.BLUE, Hex(1, -2))
                hero("brogan", Team.BLUE, Hex(2, -3))
                token(MinHero.GRENADE, Hex(2, -2), owner = "min")
                effect("min", card, Timing.END_OF_TURN, ActionKind.SKILL)
            }
            return MinHeuristics.evalTerms(session.state, Team.RED, "min")
        }
        assertThat(value("min-blue-2b")).isEqualTo(2.0)
        assertThat(value("min-blue-3b")).isEqualTo(4.0)
    }
}
