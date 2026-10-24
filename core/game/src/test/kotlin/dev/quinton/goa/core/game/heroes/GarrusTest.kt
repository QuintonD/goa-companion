package dev.quinton.goa.core.game.heroes

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.ANY_HERO_ENGINE
import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.Driver
import dev.quinton.goa.core.game.engine.Game
import dev.quinton.goa.core.game.engine.RunMode
import dev.quinton.goa.core.game.engine.Timing
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.game.pos
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

/** Garrus the Gladiator: every card, clause by clause (text from `catalog/heroes/garrus.json`), with the FAQ. */
class GarrusTest {
    private fun game(d: Driver) = Game(ANY_HERO_ENGINE.data, d.s.state, emptyList(), RunMode.PROBE)

    // ---- Angry Strike (gold) / Angry Swipe (Handicap) ------------------------------------------

    @Test
    fun `Angry Strike - target a unit adjacent to you, +1 Attack for every card in your discard`() {
        val d = pos {
            clearMinions()
            hero("garrus", Team.BLUE, Hex(1, -2)) {
                played("garrus-gold")
                discard("garrus-red-1", "garrus-green-1")
            }
            hero("wasp", Team.RED, Hex(0, -2))
            phase = PositionPhase.Resolving(next = "garrus")
        }
        d.action("garrus", ActionType.PRIMARY)
        assertThat(d.expect(DecisionKind.DEFEND, "wasp").context.attackTotal).isEqualTo(6)
    }

    @Test
    fun `Angry Swipe - target a hero adjacent to you`() {
        val d = pos {
            clearMinions()
            hero("garrus", Team.BLUE, Hex(1, -2)) {
                played("garrus-handicap")
                discard("garrus-red-1")
            }
            hero("wasp", Team.RED, Hex(2, -2))
            minion(Team.RED, MinionType.MELEE, Hex(0, -2)) // adjacent, not a hero
            phase = PositionPhase.Resolving(next = "garrus")
        }
        d.action("garrus", ActionType.PRIMARY)
        assertThat(d.expect(DecisionKind.DEFEND, "wasp").context.attackTotal).isEqualTo(4)
    }

    // ---- Chilling Howl (silver) ----------------------------------------------------------------

    @Test
    fun `Chilling Howl - discard a resolved card, enemy heroes in radius move at most 2, no fast travel`() {
        val d = pos {
            clearMinions()
            turn = 2
            hero("garrus", Team.BLUE, Hex(1, -2)) {
                resolved(1, "garrus-red-1")
                played("garrus-silver")
            }
            hero("wasp", Team.RED, Hex(-2, -2)) { played("wasp-red-1") } // 3 away, in lane-1-1: Movement 4
            phase = PositionPhase.Resolving(next = "garrus")
        }
        d.action("garrus", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_CARD, "garrus")
        d.card("garrus-red-1")
        assertThat(d.zoneOf("garrus", "garrus-red-1")).isEqualTo(Zone.DISCARD)
        assertThat(d.s.state.effects.single { it.cardId == "garrus-silver" }.timing).isEqualTo(Timing.THIS_ROUND)
        assertThat(d.actions()).doesNotContain(ActionType.FAST_TRAVEL)
        d.action("wasp", ActionType.MOVEMENT)
        val g = game(d)
        assertThat(d.spaces().maxOf { g.distance(Hex(-2, -2), it) }).isEqualTo(2)
    }

    @Test
    fun `Chilling Howl - FAQ - a hero moving into the radius from outside completes its full movement`() {
        val d = pos {
            clearMinions()
            hero("garrus", Team.BLUE, Hex(1, -2)) { played("garrus-silver") }
            hero("wasp", Team.RED, Hex(-3, -2)) { played("wasp-red-1") } // 4 away
            phase = PositionPhase.Resolving(next = "garrus")
        }
        d.action("garrus", ActionType.PRIMARY) // nothing resolved yet: no discard question
        d.action("wasp", ActionType.MOVEMENT)
        val g = game(d)
        assertThat(d.spaces().maxOf { g.distance(Hex(-3, -2), it) }).isEqualTo(4)
        assertThat(d.spaces()).contains(Hex(0, -2)) // into the radius, 3 spaces
    }

    // ---- Trace / Chase / Hunt Down (red) -------------------------------------------------------

    @Test
    fun `Trace - with a card in the discard you may move 1 space before targeting a hero adjacent to you`() {
        val d = pos {
            clearMinions()
            hero("garrus", Team.BLUE, Hex(2, -2)) {
                played("garrus-red-1")
                discard("garrus-gold")
            }
            hero("wasp", Team.RED, Hex(0, -2))
            minion(Team.RED, MinionType.MELEE, Hex(3, -2)) // adjacent: the second bullet is possible too
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -5))
            phase = PositionPhase.Resolving(next = "garrus")
        }
        d.action("garrus", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_OPTION, "garrus")
        d.option(0)
        d.expect(DecisionKind.CHOOSE_SPACE, "garrus")
        d.space(1, -2)
        d.expect(DecisionKind.DEFEND, "wasp") // the minion is not a hero
    }

    @Test
    fun `Trace - with no card in the discard the first bullet needs a hero already adjacent`() {
        val d = pos {
            clearMinions()
            hero("garrus", Team.BLUE, Hex(2, -2)) { played("garrus-red-1") }
            hero("wasp", Team.RED, Hex(0, -2))
            minion(Team.RED, MinionType.MELEE, Hex(3, -2))
            minion(Team.RED, MinionType.MELEE, Hex(4, -5))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -4))
            phase = PositionPhase.Resolving(next = "garrus")
        }
        d.action("garrus", ActionType.PRIMARY) // only "Target a unit adjacent to you": no choice, one target
        assertThat(d.s.state.minions.none { it.at == Hex(3, -2) }).isTrue()
        assertThat(d.hero("garrus").coins).isEqualTo(2)
    }

    @Test
    fun `Chase moves up to 2, Hunt Down up to 3, before targeting a hero adjacent to you`() {
        for ((card, start) in listOf("garrus-red-2a" to Hex(3, -2), "garrus-red-3a" to Hex(4, -2))) {
            val d = pos {
                clearMinions()
                hero("garrus", Team.BLUE, start) {
                    played(card)
                    discard("garrus-gold")
                }
                hero("wasp", Team.RED, Hex(0, -2))
                phase = PositionPhase.Resolving(next = "garrus")
            }
            d.action("garrus", ActionType.PRIMARY)
            d.space(1, -2)
            d.expect(DecisionKind.DEFEND, "wasp")
        }
    }

    // ---- Blunt Force / Send Flying (red alternates) --------------------------------------------

    @Test
    fun `Blunt Force - you may move 1 space next to an enemy hero, then push it 3 ignoring obstacles`() {
        val d = pos {
            clearMinions()
            hero("garrus", Team.BLUE, Hex(1, -2)) { played("garrus-red-2b") }
            hero("wasp", Team.RED, Hex(0, -2)) { hand("wasp-red-3a") }
            phase = PositionPhase.Resolving(next = "garrus")
        }
        d.action("garrus", ActionType.PRIMARY)
        d.defend("wasp-red-3a") // 7 against 7
        d.expect(DecisionKind.CHOOSE_SPACE, "garrus")
        assertThat(d.spaces()).containsExactly(Hex(0, -1))
        d.space(0, -1)
        assertThat(d.at("wasp")).isEqualTo(Hex(0, -5)) // straight away from Garrus
    }

    @Test
    fun `Blunt Force - FAQ - the move may end next to a different enemy hero, who is pushed`() {
        val d = pos {
            clearMinions()
            hero("garrus", Team.BLUE, Hex(1, -2)) { played("garrus-red-2b") }
            hero("wasp", Team.RED, Hex(0, -2)) { hand("wasp-red-3a") }
            hero("xargatha", Team.RED, Hex(2, -1))
            phase = PositionPhase.Resolving(next = "garrus")
        }
        d.action("garrus", ActionType.PRIMARY) // Wasp is the only unit adjacent
        d.defend("wasp-red-3a")
        assertThat(d.spaces()).containsExactly(Hex(0, -1), Hex(1, -1), Hex(2, -2))
        d.space(1, -1) // next to Xargatha only
        assertThat(d.at("xargatha")).isEqualTo(Hex(5, -1))
        assertThat(d.at("wasp")).isEqualTo(Hex(0, -2))
    }

    @Test
    fun `Blunt Force - FAQ - with no space next to an enemy hero, no move at all`() {
        val d = pos {
            clearMinions()
            hero("garrus", Team.BLUE, Hex(1, -2)) { played("garrus-red-2b") }
            hero("wasp", Team.RED, Hex(0, -2)) { hand() } // defeated: no enemy hero left nearby
            hero("xargatha", Team.RED, Hex(-5, -4)) { played("xargatha-gold") }
            phase = PositionPhase.Resolving(next = "garrus")
        }
        d.action("garrus", ActionType.PRIMARY)
        assertThat(d.at("wasp")).isNull()
        d.expect(DecisionKind.CHOOSE_ACTION, "xargatha")
        assertThat(d.at("garrus")).isEqualTo(Hex(1, -2))
    }

    @Test
    fun `Send Flying - you may move up to 2 spaces next to an enemy hero, then push it 3 ignoring obstacles`() {
        val d = pos {
            clearMinions()
            hero("garrus", Team.BLUE, Hex(1, -2)) { played("garrus-red-3b") }
            hero("wasp", Team.RED, Hex(0, -2)) { hand("wasp-red-3a") }
            phase = PositionPhase.Resolving(next = "garrus")
        }
        d.action("garrus", ActionType.PRIMARY)
        d.defend("wasp-red-3a") // 7 against 7
        assertThat(d.spaces()).containsExactly(Hex(0, -1), Hex(-1, -1))
        d.space(-1, -1)
        // Pushed north-east over the terrain at (1,-3) and (2,-4) to the first empty space, 3 away.
        assertThat(d.at("wasp")).isEqualTo(Hex(3, -5))
    }

    // ---- Hold Ground / Make a Stand / Battle Ready (green) -------------------------------------

    @Test
    fun `Hold Ground - with at least two enemy heroes in radius you may retrieve a discarded card`() {
        for (two in listOf(true, false)) {
            val d = pos {
                clearMinions()
                hero("garrus", Team.BLUE, Hex(1, -2)) {
                    played("garrus-green-1")
                    discard("garrus-red-1")
                }
                hero("wasp", Team.RED, Hex(0, -2))
                hero("xargatha", Team.RED, if (two) Hex(4, -2) else Hex(-5, -4)) // 3 away, or far
                phase = PositionPhase.Resolving(next = "garrus")
            }
            if (two) {
                d.action("garrus", ActionType.PRIMARY)
                d.card("garrus-red-1")
                assertThat(d.zoneOf("garrus", "garrus-red-1")).isEqualTo(Zone.HAND)
            } else {
                assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
            }
        }
    }

    @Test
    fun `Make a Stand reaches radius 4, Battle Ready retrieves up to two cards`() {
        val d = pos {
            clearMinions()
            hero("garrus", Team.BLUE, Hex(1, -2)) {
                played("garrus-green-2a")
                discard("garrus-red-1")
            }
            hero("wasp", Team.RED, Hex(0, -2))
            hero("xargatha", Team.RED, Hex(-3, -2)) // 4 away
            phase = PositionPhase.Resolving(next = "garrus")
        }
        d.action("garrus", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_CARD, "garrus")
        val e = pos {
            clearMinions()
            hero("garrus", Team.BLUE, Hex(1, -2)) {
                played("garrus-green-3a")
                discard("garrus-red-1", "garrus-blue-1")
            }
            hero("wasp", Team.RED, Hex(0, -2))
            hero("xargatha", Team.RED, Hex(-3, -2))
            phase = PositionPhase.Resolving(next = "garrus")
        }
        e.action("garrus", ActionType.PRIMARY)
        e.card("garrus-red-1")
        e.card("garrus-blue-1")
        assertThat(e.zoneOf("garrus", "garrus-blue-1")).isEqualTo(Zone.HAND)
    }

    // ---- Light Pilum / Heavy Pilum (green alternates) ------------------------------------------

    @Test
    fun `Light Pilum - an enemy hero in range discards a card, if able, then you may move 1 space`() {
        val d = pos {
            clearMinions()
            hero("garrus", Team.BLUE, Hex(1, -2)) { played("garrus-green-2b") }
            hero("wasp", Team.RED, Hex(-1, -2)) { hand("wasp-blue-1", "wasp-gold") }
            phase = PositionPhase.Resolving(next = "garrus")
        }
        d.action("garrus", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_CARD, "wasp")
        d.card("wasp-gold")
        d.expect(DecisionKind.CHOOSE_SPACE, "garrus")
        d.space(2, -2)
        assertThat(d.at("garrus")).isEqualTo(Hex(2, -2))
    }

    @Test
    fun `Light Pilum - no enemy hero in range, no primary action`() {
        val d = pos {
            clearMinions()
            hero("garrus", Team.BLUE, Hex(1, -2)) { played("garrus-green-2b") }
            hero("wasp", Team.RED, Hex(-2, -2)) // 3 away
            phase = PositionPhase.Resolving(next = "garrus")
        }
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
    }

    @Test
    fun `Heavy Pilum - an enemy hero in range discards a card, or is defeated, then you may move up to 2 spaces`() {
        val d = pos {
            clearMinions()
            hero("garrus", Team.BLUE, Hex(1, -2)) { played("garrus-green-3b") }
            hero("wasp", Team.RED, Hex(-1, -2)) { hand() }
            phase = PositionPhase.Resolving(next = "garrus")
        }
        d.action("garrus", ActionType.PRIMARY)
        assertThat(d.at("wasp")).isNull()
        assertThat(d.hero("garrus").coins).isEqualTo(1)
        d.expect(DecisionKind.CHOOSE_SPACE, "garrus")
        assertThat(d.spaces()).contains(Hex(3, -2)) // 2 spaces
    }

    // ---- Menace / Threaten / Terrify (blue) ----------------------------------------------------

    @Test
    fun `Menace - move an enemy unit in range 1 space to a space farther away from you`() {
        val d = pos {
            clearMinions()
            hero("garrus", Team.BLUE, Hex(1, -2)) { played("garrus-blue-1") }
            hero("wasp", Team.RED, Hex(0, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(2, -2)) // friendly: not offered
            phase = PositionPhase.Resolving(next = "garrus")
        }
        d.action("garrus", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_SPACE, "garrus")
        assertThat(d.spaces()).containsExactly(Hex(0, -3), Hex(-1, -2), Hex(-1, -1))
        d.space(-1, -2)
        assertThat(d.at("wasp")).isEqualTo(Hex(-1, -2))
        assertThat(d.pending?.kind).isNotEqualTo(DecisionKind.CONFIRM) // Menace has no repeat
    }

    @Test
    fun `Threaten - may repeat once, on the same enemy unit`() {
        val d = pos {
            clearMinions()
            hero("garrus", Team.BLUE, Hex(1, -2)) { played("garrus-blue-2a") }
            hero("wasp", Team.RED, Hex(0, -2))
            phase = PositionPhase.Resolving(next = "garrus")
        }
        d.action("garrus", ActionType.PRIMARY)
        d.space(-1, -2) // 2 away now
        d.expect(DecisionKind.CONFIRM, "garrus")
        d.yes()
        val g = game(d)
        assertThat(d.spaces().all { g.distance(Hex(1, -2), it) == 3 }).isTrue()
        d.space(-2, -2)
        assertThat(d.at("wasp")).isEqualTo(Hex(-2, -2))
        assertThat(d.pending?.kind).isNotEqualTo(DecisionKind.CONFIRM)
    }

    @Test
    fun `Terrify - may repeat up to two times, any enemy unit in range each time`() {
        val d = pos {
            clearMinions()
            hero("garrus", Team.BLUE, Hex(1, -2)) { played("garrus-blue-3a") }
            hero("wasp", Team.RED, Hex(0, -2))
            hero("xargatha", Team.RED, Hex(2, -2))
            phase = PositionPhase.Resolving(next = "garrus")
        }
        d.action("garrus", ActionType.PRIMARY)
        d.unit("wasp")
        d.space(-1, -2)
        d.yes()
        d.unit("xargatha")
        d.space(3, -2)
        d.expect(DecisionKind.CONFIRM, "garrus")
        d.yes()
        d.unit("wasp")
        d.space(-2, -2)
        assertThat(d.at("wasp")).isEqualTo(Hex(-2, -2))
        assertThat(d.at("xargatha")).isEqualTo(Hex(3, -2))
        assertThat(d.pending?.kind).isNotEqualTo(DecisionKind.CONFIRM)
    }

    // ---- Form Up! / Testudo! (blue alternates) -------------------------------------------------

    @Test
    fun `Form Up - move a friendly unit in range 1 space closer to you, may repeat once`() {
        val d = pos {
            clearMinions()
            hero("garrus", Team.BLUE, Hex(1, -2)) { played("garrus-blue-2b") }
            hero("wasp", Team.RED, Hex(-1, -2)) // an enemy is not offered
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -2)) // 3 away
            phase = PositionPhase.Resolving(next = "garrus")
        }
        d.action("garrus", ActionType.PRIMARY) // the only space 1 closer is (3,-2): no question
        val m = d.s.state.minions.single()
        assertThat(m.at).isEqualTo(Hex(3, -2))
        d.expect(DecisionKind.CONFIRM, "garrus")
        d.yes() // again the only closer space, (2,-2)
        assertThat(d.s.state.minions.single().at).isEqualTo(Hex(2, -2))
        assertThat(d.pending?.kind).isNotEqualTo(DecisionKind.CONFIRM)
    }

    @Test
    fun `Testudo - may repeat up to two times`() {
        val d = pos {
            clearMinions()
            hero("garrus", Team.BLUE, Hex(1, -2)) { played("garrus-blue-3b") }
            hero("wasp", Team.RED, Hex(-5, -4))
            hero("arien", Team.BLUE, Hex(4, -2)) // a friendly hero, 3 away
            phase = PositionPhase.Resolving(next = "garrus")
        }
        d.action("garrus", ActionType.PRIMARY) // to (3,-2), the only closer space
        d.yes() // to (2,-2)
        d.expect(DecisionKind.CONFIRM, "garrus")
        d.decline() // the second repeat is optional
        assertThat(d.at("arien")).isEqualTo(Hex(2, -2))
    }

    // ---- Battle Fury (Ultimate) ----------------------------------------------------------------

    @Test
    fun `Battle Fury - after a resolved card is discarded, you may perform its primary action`() {
        val d = pos {
            clearMinions()
            turn = 2
            hero("garrus", Team.BLUE, Hex(1, -2)) {
                ultimate()
                resolved(1, "garrus-blue-1")
                played("garrus-silver")
            }
            hero("wasp", Team.RED, Hex(0, -2))
            phase = PositionPhase.Resolving(next = "garrus")
        }
        d.action("garrus", ActionType.PRIMARY)
        d.card("garrus-blue-1") // Chilling Howl: discard a resolved card
        d.expect(DecisionKind.CONFIRM, "garrus")
        d.yes()
        d.space(-1, -2) // Menace's primary action
        assertThat(d.at("wasp")).isEqualTo(Hex(-1, -2))
        assertThat(d.s.state.effects.any { it.cardId == "garrus-silver" }).isTrue() // Chilling Howl goes on
    }

    @Test
    fun `Battle Fury - a card discarded from the hand does not count`() {
        val d = pos {
            clearMinions()
            hero("garrus", Team.BLUE, Hex(1, -2)) {
                ultimate()
                hand("garrus-blue-1")
            }
            hero("wasp", Team.RED, Hex(0, -2)) { played("wasp-gold") }
            phase = PositionPhase.Resolving(next = "wasp")
        }
        d.action("wasp", ActionType.PRIMARY)
        d.defend("garrus-blue-1") // 6 against 3
        assertThat(d.pending?.kind).isNotEqualTo(DecisionKind.CONFIRM)
    }

    @Test
    fun `random games with Garrus keep every invariant`() {
        batch1RandomGames("garrus", games = 200, seed = 15)
    }
}
