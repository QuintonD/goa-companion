package dev.quinton.goa.core.game.ai.heuristic

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.engine.Determinizer
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.position.position
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

/** The general book's public-knowledge facts, one case per review finding (REVIEW-core.md). */
class HeuristicReviewCoreTest {
    private val red = Hex(1, -5)
    private val blue = Hex(2, -6)

    private fun ctx(session: EngineSession, team: Team, profile: HeuristicProfile = PLAIN): HContext =
        HContext(session, session.pending, team, profile)

    /** C1: a forced discard in the card's look is priced from the pool, whichever hand was sampled. */
    @Test
    fun `the look at a forced discard does not depend on the sampled hand`() {
        // Arien's two cards in hand and her facedown card are the hidden ones: a sample permutes them.
        // Two in hand, so the engine asks which to discard (a single option it answers itself, Game.ask).
        val session = position(TestData.engine, TestData.FOREST) {
            clearMinions()
            turn = 3
            hero("arien", Team.BLUE, blue) {
                played("arien-blue-1")
                hand("arien-gold", "arien-silver")
            }
            hero("brynn", Team.RED, red)
            phase = PositionPhase.PlayCards(fromSeat = 1)
        }
        val played = HashSet<String>()
        val values = (1L..8L).map { seed ->
            val det = session.determinizeWith(Team.RED, seed, Determinizer.Options(), quiet = true)
            played += det.state.hero("arien")!!.cards.single { it.zone == Zone.PLAYED }.id
            CardSim.value(ctx(det, Team.RED), "brynn-green-1")
        }
        assertWithMessage("the samples differ").that(played.size).isGreaterThan(1)
        assertThat(values.first()).isNotNull()
        assertWithMessage("$values").that(values.distinct()).hasSize(1)
    }

    /** C3: after the reveal, the card being played has left the hand and defends nothing. */
    @Test
    fun `a revealed played card is not a defense`() {
        val session = position(TestData.engine, TestData.FOREST) {
            clearMinions()
            hero("arien", Team.BLUE, blue) {
                played("arien-blue-1")
                hand("arien-gold")
            }
            hero("brynn", Team.RED, red) { played("brynn-red-1") }
            phase = PositionPhase.Resolving(next = "brynn")
        }
        val c = ctx(session, Team.RED)
        // Defense 5 on the played card would hold an attack of 4; the Gold left in hand (2) does not.
        assertThat(c.heroStrikeWorth("arien", 4, ranged = false)).isAtLeast(c.killWorth("arien"))
    }

    /** C4: a hero that already played facedown still holds its last card for this turn's attacks. */
    @Test
    fun `a hero that played facedown with one card left is not defenseless`() {
        val played = position(TestData.engine, TestData.FOREST) {
            clearMinions()
            turn = 3
            hero("arien", Team.BLUE, blue) {
                played("arien-blue-1")
                hand("arien-gold")
            }
            hero("brynn", Team.RED, red)
            phase = PositionPhase.PlayCards(fromSeat = 1)
        }
        assertThat(heroIsDefenseless(ctx(played, Team.RED), "arien")).isFalse()
        val toPlay = position(TestData.engine, TestData.FOREST) {
            clearMinions()
            turn = 3
            hero("brynn", Team.RED, red)
            hero("arien", Team.BLUE, blue) { hand("arien-gold") }
            phase = PositionPhase.PlayCards(fromSeat = 0)
        }
        assertThat(heroIsDefenseless(ctx(toPlay, Team.RED), "arien")).isTrue()
    }

    /** C5: a hero's next Battle Zone is the same whichever team asks. */
    @Test
    fun `the goal zone of an enemy follows its own lane margin`() {
        val probe = position(TestData.engine, TestData.FOREST) {
            clearMinions()
            hero("arien", Team.BLUE, blue)
            hero("brynn", Team.RED, red)
        }
        val lane = probe.state.battleZones.keys.first()
        val bz = probe.state.battleZones.getValue(lane)
        val spaces = ctx(probe, Team.RED).game.geo.zoneSpaces.getValue(bz).filter { it != red && it != blue }
        val session = position(TestData.engine, TestData.FOREST) {
            clearMinions()
            wavesLeft(lane, 3)
            hero("arien", Team.BLUE, blue)
            hero("brynn", Team.RED, red)
            spaces.take(3).forEach { minion(Team.RED, MinionType.MELEE, it, lane) }
        }
        val strong = HeuristicProfile.of(10).copy(heroPlaybooks = false)
        val fromRed = ctx(session, Team.RED, strong)
        val fromBlue = ctx(session, Team.BLUE, strong)
        val retreat = nextZoneOf(fromBlue, lane, Team.RED)
        assertThat(retreat).isNotNull()
        assertThat(goalZoneOf(fromBlue, "arien")).isEqualTo(goalZoneOf(fromRed, "arien"))
        assertThat(goalZoneOf(fromRed, "brynn")).isEqualTo(goalZoneOf(fromBlue, "brynn"))
    }

    /** C6: "discards a card, if able" takes nothing from an empty hand and defeats nobody. */
    @Test
    fun `a discard if able is no defeat for an empty hand`() {
        val session = position(TestData.engine, TestData.FOREST) {
            clearMinions()
            turn = 3
            hero("arien", Team.BLUE, blue) {
                played("arien-blue-1")
                hand()
            }
            hero("swift", Team.RED, red) { hand("swift-green-1") }
            phase = PositionPhase.PlayCards(fromSeat = 1)
        }
        val strong = HeuristicProfile.of(10).copy(heroPlaybooks = false)
        val fromBlue = ctx(session, Team.BLUE, strong)
        val arien = session.state.hero("arien")!!
        assertThat(fromBlue.exposure("arien", blue)).isLessThan(fromBlue.discardWorth(arien))
        val fromRed = ctx(session, Team.RED, strong)
        assertThat(fromRed.strikeFrom("swift", red, fromRed.card("swift-green-1"))).isAtMost(0.0)
    }

    private companion object {
        /** Strength 8 with the general book alone: these are its facts, not a hero's notes. */
        val PLAIN = HeuristicProfile.of(8).copy(heroPlaybooks = false)
    }
}
