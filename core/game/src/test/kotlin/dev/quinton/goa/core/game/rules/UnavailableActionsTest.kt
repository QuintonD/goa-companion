package dev.quinton.goa.core.game.rules

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.Driver
import dev.quinton.goa.core.game.UnavailableAction
import dev.quinton.goa.core.game.pos
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

/**
 * A printed action that isn't offered says why (ADR-041, `DecisionContext.unavailable`): the player
 * who sees only Hold must not think the app is broken. The reasons come from the checks that left
 * the action out (`Actions.actionAvailability`), so each test also checks the options.
 */
class UnavailableActionsTest {
    private fun Driver.unavailable(): List<UnavailableAction> = expect(DecisionKind.CHOOSE_ACTION).context.unavailable

    @Test
    fun `Gydion's Cantrip on turn 1 - no spell to cast, the spellbook is empty until Prepare Spells (BGG 3491113)`() {
        val d = pos {
            clearMinions()
            hero("gydion", Team.RED, Hex(0, 0)) { played("gydion-gold") }
            hero("arien", Team.BLUE, Hex(2, -2)) { hand("arien-blue-1") }
            phase = PositionPhase.Resolving(next = "gydion")
        }
        assertThat(d.actions()).containsExactly(ActionType.HOLD)
        assertThat(d.unavailable()).containsExactly(
            UnavailableAction(
                ActionType.PRIMARY,
                "Its Skill can't start: the spellbook is empty until Prepare Spells is performed",
                "designer, BGG 3491113",
            ),
        )
    }

    @Test
    fun `an Attack with no target can't start - the first instruction can't be done (p_10, FAQ)`() {
        val d = pos {
            clearMinions()
            hero("arien", Team.BLUE, Hex(1, -2)) { played("arien-red-1") } // Dangerous Current: adjacent attack
            hero("wasp", Team.RED, Hex(-3, -2)) { hand("wasp-blue-1") } // 4 spaces away
            phase = PositionPhase.Resolving(next = "arien")
        }
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
        assertThat(d.unavailable()).containsExactly(
            UnavailableAction(ActionType.PRIMARY, "Its Attack can't start: no target adjacent to you", "p.10, FAQ"),
        )
    }

    @Test
    fun `an Attack with a target offers it and explains nothing`() {
        val d = pos {
            clearMinions()
            hero("arien", Team.BLUE, Hex(1, -2)) { played("arien-red-1") }
            hero("wasp", Team.RED, Hex(0, -2)) { hand("wasp-blue-1") } // adjacent
            phase = PositionPhase.Resolving(next = "arien")
        }
        assertThat(d.actions()).contains(ActionType.PRIMARY)
        assertThat(d.unavailable()).isEmpty()
    }

    @Test
    fun `a Skill forbidden by an enemy card names it - Arien's Spell Break`() {
        val d = pos {
            clearMinions()
            hero("arien", Team.BLUE, Hex(1, -2)) { played("arien-silver") } // 13, radius 3
            hero("wasp", Team.RED, Hex(-1, -2)) { played("wasp-blue-1") } // Lift Up: a skill, in radius
            phase = PositionPhase.Resolving(next = "arien")
        }
        d.action("arien", ActionType.PRIMARY)
        assertThat(d.actions()).containsExactly(ActionType.MOVEMENT, ActionType.HOLD)
        assertThat(d.unavailable()).containsExactly(
            UnavailableAction(
                ActionType.PRIMARY,
                "Its Skill is forbidden by Arien's Spell Break",
                null,
                "arien-silver",
            ),
        )
    }

    @Test
    fun `Movement blocked by an enemy card names it - Xargatha's Stone Gaze`() {
        val d = pos {
            clearMinions()
            hero("xargatha", Team.RED, Hex(1, -2)) { played("xargatha-blue-1") } // radius 2
            hero("arien", Team.BLUE, Hex(-5, -4))
            hero("brogan", Team.BLUE, Hex(3, -2)) { played("brogan-red-1") } // 2 away
            phase = PositionPhase.Resolving(next = "xargatha")
        }
        d.action("xargatha", ActionType.PRIMARY)
        d.action("brogan", ActionType.HOLD)
        d.play("xargatha", "xargatha-gold")
        d.play("arien", "arien-gold")
        d.play("brogan", "brogan-blue-1")
        d.action("xargatha", ActionType.HOLD)
        d.action("arien", ActionType.HOLD)
        assertThat(d.actions()).containsExactly(ActionType.PRIMARY, ActionType.HOLD)
        assertThat(d.unavailable()).containsExactly(
            UnavailableAction(
                ActionType.MOVEMENT,
                "Its Movement is forbidden by Xargatha's Stone Gaze",
                null,
                "xargatha-blue-1",
            ),
        )
    }

    @Test
    fun `a played Defense card says its Defense is only for an attack, from the hand (p_14)`() {
        val d = pos {
            clearMinions()
            hero("arien", Team.BLUE, Hex(1, -2)) { played("arien-blue-1") } // Aspiring Duelist
            hero("wasp", Team.RED, Hex(-3, -2)) { hand("wasp-blue-1") }
            phase = PositionPhase.Resolving(next = "arien")
        }
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
        assertThat(d.unavailable().map { it.action to it.source }).containsExactly(ActionType.PRIMARY to "p.14")
    }

    @Test
    fun `the reasons reveal nothing hidden - enemy hands that differ give the same reasons`() {
        fun reasons(enemyHand: String) = pos {
            clearMinions()
            hero("arien", Team.BLUE, Hex(1, -2)) { played("arien-red-1") }
            hero("wasp", Team.RED, Hex(-3, -2)) { hand(enemyHand) }
            phase = PositionPhase.Resolving(next = "arien")
        }.unavailable()
        assertThat(reasons("wasp-blue-1")).isEqualTo(reasons("wasp-gold"))
    }
}
