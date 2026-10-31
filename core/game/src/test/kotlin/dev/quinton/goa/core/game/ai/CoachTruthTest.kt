package dev.quinton.goa.core.game.ai

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.CoachReasonKind
import dev.quinton.goa.core.game.Controller
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.engine.ActionKind
import dev.quinton.goa.core.game.engine.Game
import dev.quinton.goa.core.game.engine.RunMode
import dev.quinton.goa.core.game.engine.Timing
import dev.quinton.goa.core.game.engine.cardValue
import dev.quinton.goa.core.game.pos
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.catalog.Stat
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

/** What the rules audit of the coach's sentences found, kept true (ADR-051): each on a position built for it. */
class CoachTruthTest {
    private val ai = Controller.Ai(AiLevel.NORMAL)

    private fun coach(seed: Long = 0x0C0AC4L) = AiCoach(TestData.engine, seed, 3_600_000L)

    private fun teammatePlayed(card: String, computer: Boolean) = pos(engine = TestData.engine) {
        clearMinions()
        hero("xargatha", Team.RED, Hex(0, -2)) {
            if (computer) controller = ai
            played(card)
        }
        hero("wasp", Team.RED, Hex(-1, -2))
        hero("brogan", Team.BLUE, Hex(1, -2)) { controller = ai }
        hero("arien", Team.BLUE, Hex(2, -3)) { controller = ai }
        minion(Team.RED, MinionType.MELEE, Hex(-4, -1))
        minion(Team.BLUE, MinionType.MELEE, Hex(4, -5))
        phase = PositionPhase.PlayCards(fromSeat = 1)
    }

    private fun teammateUpgraded(red: Boolean, computer: Boolean) = pos(engine = TestData.engine) {
        clearMinions()
        hero("wasp", Team.RED, Hex(0, -2)) {
            if (computer) controller = ai
            level = 2
            played("wasp-gold")
            if (red) {
                hand("wasp-silver", "wasp-red-2a", "wasp-green-1", "wasp-blue-1")
                items("wasp-red-2b")
            } else {
                hand("wasp-silver", "wasp-red-1", "wasp-green-1", "wasp-blue-2b")
                items("wasp-blue-2a")
            }
        }
        hero("xargatha", Team.RED, Hex(-1, -2))
        hero("brogan", Team.BLUE, Hex(1, -2)) { controller = ai }
        hero("arien", Team.BLUE, Hex(2, -3)) { controller = ai }
        minion(Team.RED, MinionType.MELEE, Hex(-4, -1))
        minion(Team.BLUE, MinionType.MELEE, Hex(4, -5))
        phase = PositionPhase.PlayCards(fromSeat = 1)
    }

    @Test
    fun `a teammate's facedown card changes nothing the coach says`() {
        for (computer in listOf(true, false)) {
            val a = teammatePlayed("xargatha-gold", computer)
            val b = teammatePlayed("xargatha-red-1", computer)
            val qa = a.s.pending!!
            val qb = b.s.pending!!
            assertThat(b.s.view(qb.seat)).isEqualTo(a.s.view(qa.seat))
            assertThat(coach().suggest(null, b.s, qb)).isEqualTo(coach().suggest(null, a.s, qa))
        }
    }

    @Test
    fun `which card a teammate kept at an upgrade changes nothing the coach says`() {
        for (computer in listOf(true, false)) {
            val a = teammateUpgraded(red = true, computer)
            val b = teammateUpgraded(red = false, computer)
            val qa = a.s.pending!!
            val qb = b.s.pending!!
            assertThat(b.s.view(qb.seat)).isEqualTo(a.s.view(qa.seat))
            assertThat(coach().suggest(null, b.s, qb)).isEqualTo(coach().suggest(null, a.s, qa))
        }
    }

    @Test
    fun `a Block in hand answers a ranged threat`() {
        val d = pos(engine = TestData.engine) {
            clearMinions()
            hero("wasp", Team.RED, Hex(0, -2)) {
                hand("wasp-green-1", exactly = true)
                played("wasp-red-1")
            }
            hero("bain", Team.BLUE, Hex(2, -2)) {
                controller = ai
                played("bain-red-1")
            }
            minion(Team.RED, MinionType.MELEE, Hex(-4, -1))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -5))
            turn = 3
            phase = PositionPhase.Resolving(next = "wasp")
        }
        val threats = coach().suggest(null, d.s, d.s.pending!!)!!.threats.filter { it.heroId == "bain" && it.thisTurn }
        assertThat(threats).isNotEmpty()
        for (t in threats) {
            assertThat(t.text).contains("Stop Projectiles blocks it")
            assertThat(t.text).doesNotContain("no card that defends")
        }
    }

    @Test
    fun `an enemy the hero is immune to is no threat this turn`() {
        val d = pos(engine = TestData.engine) {
            clearMinions()
            hero("arien", Team.RED, Hex(0, -2)) {
                hand("arien-silver", "arien-gold", exactly = true)
                played("arien-green-1")
                discard("arien-blue-2a")
            }
            hero("brogan", Team.BLUE, Hex(1, -2)) {
                controller = ai
                played("brogan-red-1")
            }
            hero("wasp", Team.BLUE, Hex(-1, -2)) {
                controller = ai
                played("wasp-red-1")
            }
            minion(Team.RED, MinionType.MELEE, Hex(-4, -1))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -5))
            turn = 2
            effect("arien", "arien-blue-2a", Timing.THIS_TURN, ActionKind.DEFENSE, data = mapOf("attacker" to "wasp"))
            phase = PositionPhase.Resolving(next = "arien")
        }
        val threats = coach().suggest(null, d.s, d.s.pending!!)!!.threats
        assertThat(threats.filter { it.heroId == "brogan" && it.thisTurn }).isEmpty()
    }

    @Test
    fun `the Life cost counts the Bounty marker and tells the game is lost`() {
        val d = pos(engine = TestData.engine) {
            clearMinions()
            hero("wasp", Team.RED, Hex(0, -2)) {
                hand("wasp-gold", exactly = true)
                marker("marker_bounty", "bain")
            }
            hero("brogan", Team.BLUE, Hex(1, -2)) {
                controller = ai
                played("brogan-gold")
            }
            hero("bain", Team.BLUE, Hex(3, -3)) {
                controller = ai
                played("bain-silver")
            }
            lifeCounters(Team.RED, 2)
            minion(Team.RED, MinionType.MELEE, Hex(-4, -1))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -5))
            turn = 2
            phase = PositionPhase.Resolving(next = "brogan")
        }
        d.action("brogan", ActionType.PRIMARY)
        val q = d.expect(DecisionKind.DEFEND, "wasp")
        val texts = coach().suggest(null, d.s, q)!!.options.flatMap { it.reasons + it.against }
            .filter { it.kind == CoachReasonKind.LIFE_COST }.map { it.text }
        assertThat(texts).isNotEmpty()
        for (t in texts) assertThat(t).contains("its last Life counter and loses the game")
    }

    @Test
    fun `a threat's numbers are what the engine would resolve`() {
        val d = pos(engine = TestData.engine) {
            clearMinions()
            hero("wasp", Team.RED, Hex(0, -2)) {
                hand("wasp-blue-1", "wasp-gold", "wasp-red-1", exactly = true)
                played("wasp-silver")
                marker("marker_poison", "tigerclaw")
            }
            hero("brogan", Team.BLUE, Hex(1, -2)) {
                controller = ai
                played("brogan-gold")
            }
            hero("tigerclaw", Team.BLUE, Hex(3, -3)) {
                controller = ai
                played("tigerclaw-silver")
            }
            minion(Team.RED, MinionType.MELEE, Hex(-4, -1))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -5))
            turn = 2
            phase = PositionPhase.Resolving(next = "wasp")
        }
        val g = Game(TestData.engine.data, d.s.state, emptyList(), RunMode.PROBE)
        val real = listOf("wasp-blue-1", "wasp-gold", "wasp-red-1")
            .mapNotNull { g.cardValue("wasp", it, Stat.DEFENSE, ActionKind.DEFENSE) }.max()
        val threat = coach().suggest(null, d.s, d.s.pending!!)!!.threats.first { it.heroId == "brogan" && it.thisTurn }
        assertThat(threat.defense).isEqualTo(real)
    }
}
