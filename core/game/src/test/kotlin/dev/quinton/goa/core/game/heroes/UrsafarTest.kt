package dev.quinton.goa.core.game.heroes

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.game.ANY_HERO_ENGINE
import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.Driver
import dev.quinton.goa.core.game.engine.ActionKind
import dev.quinton.goa.core.game.engine.Game
import dev.quinton.goa.core.game.engine.RunMode
import dev.quinton.goa.core.game.engine.Timing
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.game.heroes.batch2.Batch2Games
import dev.quinton.goa.core.game.pos
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

/** Ursafar the Savage: every card, clause by clause (text from `catalog/heroes/ursafar.json`). */
class UrsafarTest {
    private fun Driver.enraged(): Boolean =
        UrsafarHero.isEnraged(Game(ANY_HERO_ENGINE.data, s.state, emptyList(), RunMode.PROBE), "ursafar")

    private fun Driver.minionAt(h: Hex) = s.state.minions.firstOrNull { it.at == h }

    private fun Driver.labels() = d.options.filterIsInstance<Choice.Option>().map { it.label }

    /** Turn 2; Claws That Catch resolved on turn 1 made Ursafar enraged ("This round"). */
    private fun PositionBuilder.enragedByClaws() {
        effect("ursafar", "ursafar-gold", Timing.THIS_ROUND, ActionKind.ATTACK, madeTurn = 1, key = UrsafarHero.ENRAGED)
    }

    private fun PositionBuilder.HeroBuilder.clawsResolved() {
        resolved(1, "ursafar-gold")
    }

    // ---- Claws That Catch (gold) / Jaws That Bite (Handicap) -----------------------------------

    @Test
    fun `Claws That Catch - not enraged - no move, attack, then This round you are enraged`() {
        val d = pos {
            clearMinions()
            hero("ursafar", Team.RED, Hex(1, -2)) { played("ursafar-gold") }
            hero("arien", Team.BLUE, Hex(3, -2)) { hand("arien-red-1") } // 2 away: a move could reach him
            minion(Team.BLUE, MinionType.MELEE, Hex(1, -1)) // adjacent
            minion(Team.BLUE, MinionType.MELEE, Hex(1, -6))
            minion(Team.RED, MinionType.MELEE, Hex(0, -6))
            phase = PositionPhase.Resolving(next = "ursafar")
        }
        assertThat(d.enraged()).isFalse()
        d.action("ursafar", ActionType.PRIMARY) // no move offered: the only target is attacked at once
        assertThat(d.minionAt(Hex(1, -1))).isNull()
        assertThat(d.at("ursafar")).isEqualTo(Hex(1, -2))
        assertThat(d.enraged()).isTrue()
    }

    @Test
    fun `Claws That Catch - enraged - you may move 1 space to a space adjacent to an enemy hero, then target`() {
        val d = pos {
            clearMinions()
            turn = 2
            hero("ursafar", Team.RED, Hex(1, -2)) {
                resolved(1, "ursafar-red-1") // Prey Drive made him enraged on turn 1
                played("ursafar-gold")
            }
            hero("arien", Team.BLUE, Hex(3, -2)) { hand("arien-red-1") }
            effect(
                "ursafar",
                "ursafar-red-1",
                Timing.THIS_ROUND,
                ActionKind.ATTACK,
                madeTurn = 1,
                key = UrsafarHero.ENRAGED,
            )
            phase = PositionPhase.Resolving(next = "ursafar")
        }
        d.action("ursafar", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_SPACE, "ursafar")
        assertThat(d.spaces()).containsExactly(Hex(2, -2))
        assertThat(d.d.options.any { it is Choice.Decline }).isTrue()
        d.space(2, -2)
        d.expect(DecisionKind.DEFEND, "arien")
    }

    @Test
    fun `Jaws That Bite - Target a hero adjacent to you`() {
        val d = pos {
            clearMinions()
            hero("ursafar", Team.RED, Hex(1, -2)) {
                handicap = true
                played("ursafar-handicap")
            }
            hero("arien", Team.BLUE, Hex(4, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(1, -1))
            phase = PositionPhase.Resolving(next = "ursafar")
        }
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
    }

    // ---- Angry Roar (silver) --------------------------------------------------------------------

    @Test
    fun `Angry Roar - if enraged, perform the primary action of one of your active cards`() {
        val d = pos {
            clearMinions()
            turn = 3
            hero("ursafar", Team.RED, Hex(1, -2)) {
                clawsResolved()
                resolved(2, "ursafar-red-2b") // Rip, also active ("This round: You are enraged")
                played("ursafar-silver")
            }
            hero("arien", Team.BLUE, Hex(7, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(1, -1))
            minion(Team.BLUE, MinionType.MELEE, Hex(1, -6))
            minion(Team.RED, MinionType.MELEE, Hex(0, -6))
            enragedByClaws()
            effect(
                "ursafar",
                "ursafar-red-2b",
                Timing.THIS_ROUND,
                ActionKind.ATTACK,
                madeTurn = 2,
                key = UrsafarHero.ENRAGED,
            )
            phase = PositionPhase.Resolving(next = "ursafar")
        }
        d.action("ursafar", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_CARD, "ursafar")
        assertThat(d.cards()).containsExactly("ursafar-gold", "ursafar-red-2b")
        d.card("ursafar-red-2b") // Rip: attack the adjacent minion; enraged: +1 coin
        assertThat(d.minionAt(Hex(1, -1))).isNull()
        assertThat(d.hero("ursafar").coins).isEqualTo(3)
        assertThat(d.zoneOf("ursafar", "ursafar-red-2b")).isEqualTo(Zone.RESOLVED) // it stays where it is
    }

    @Test
    fun `Angry Roar - not enraged - nothing is performed, then you are enraged`() {
        val d = pos {
            clearMinions()
            turn = 2
            hero("ursafar", Team.RED, Hex(1, -2)) {
                resolved(1, "ursafar-green-1")
                played("ursafar-silver")
            }
            hero("arien", Team.BLUE, Hex(7, -2))
            phase = PositionPhase.Resolving(next = "ursafar")
        }
        d.action("ursafar", ActionType.PRIMARY)
        assertThat(d.d.kind).isEqualTo(DecisionKind.PLAY_CARD)
        assertThat(d.enraged()).isTrue()
    }

    @Test
    fun `Angry Roar - enraged but no active card can be performed - the action cannot be chosen`() {
        val d = pos {
            clearMinions()
            turn = 2
            hero("ursafar", Team.RED, Hex(1, -2)) {
                clawsResolved() // Claws: no unit adjacent to attack
                played("ursafar-silver")
            }
            hero("arien", Team.BLUE, Hex(7, -2))
            enragedByClaws()
            phase = PositionPhase.Resolving(next = "ursafar")
        }
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
    }

    // ---- Prey Drive / Prey Abundance / Feeding Frenzy (red) ------------------------------------

    private fun prey(card: String, enraged: Boolean = true) = pos {
        clearMinions()
        turn = 2
        hero("ursafar", Team.RED, Hex(1, -2)) {
            clawsResolved()
            played(card)
        }
        hero("arien", Team.BLUE, Hex(2, -2)) { hand("arien-red-1") } // Defense 6
        minion(Team.BLUE, MinionType.MELEE, Hex(0, -2)) // adjacent
        minion(Team.BLUE, MinionType.MELEE, Hex(1, -4)) // 2 away
        minion(Team.BLUE, MinionType.HEAVY, Hex(-1, -2)) // 2 away, immune
        minion(Team.RED, MinionType.MELEE, Hex(0, -6))
        if (enraged) enragedByClaws()
        phase = PositionPhase.Resolving(next = "ursafar")
    }

    @Test
    fun `Prey Drive - enraged and the target not removed - remove up to 1 enemy minion in radius (FAQ - no coins)`() {
        val d = prey("ursafar-red-1")
        d.action("ursafar", ActionType.PRIMARY)
        d.unit("arien")
        d.defend("arien-red-1") // 6 against 5: Arien stays
        d.expect(DecisionKind.CHOOSE_UNIT, "ursafar")
        assertThat(d.units().map { d.at(it) }.toSet()).containsExactly(Hex(0, -2), Hex(1, -4)) // the heavy is immune
        assertThat(d.d.options.any { it is Choice.Decline }).isTrue()
        d.unit(d.minionAt(Hex(1, -4))!!.id)
        assertThat(d.minionAt(Hex(1, -4))).isNull()
        assertThat(d.hero("ursafar").coins).isEqualTo(0)
    }

    @Test
    fun `Prey Drive - the target removed - no minion is removed`() {
        val d = prey("ursafar-red-1")
        d.action("ursafar", ActionType.PRIMARY)
        d.unit(d.minionAt(Hex(0, -2))!!.id)
        assertThat(d.d.kind).isEqualTo(DecisionKind.PLAY_CARD)
        assertThat(d.minionAt(Hex(1, -4))).isNotNull()
    }

    @Test
    fun `Prey Abundance - not enraged - nothing after the attack`() {
        val d = prey("ursafar-red-2a", enraged = false)
        d.action("ursafar", ActionType.PRIMARY)
        d.unit("arien")
        d.defend("arien-red-1") // 6 against 6
        assertThat(d.d.kind).isEqualTo(DecisionKind.PLAY_CARD)
        assertThat(d.enraged()).isTrue() // now, from this card
    }

    @Test
    fun `Feeding Frenzy - remove up to 2 enemy minions`() {
        val d = prey("ursafar-red-3a")
        d.action("ursafar", ActionType.PRIMARY)
        d.unit("arien")
        d.defend("arien-red-1") // 6 against 6
        d.unit(d.minionAt(Hex(1, -4))!!.id)
        d.unit(d.minionAt(Hex(0, -2))!!.id)
        assertThat(d.minionAt(Hex(1, -4))).isNull()
        assertThat(d.minionAt(Hex(0, -2))).isNull()
    }

    @Test
    fun `Prey Drive - a Radius item widens the radius`() {
        val d = pos {
            clearMinions()
            turn = 2
            hero("ursafar", Team.RED, Hex(1, -2)) {
                clawsResolved()
                played("ursafar-red-1") // radius 3
                items("ursafar-blue-3b") // +1 Radius
            }
            hero("arien", Team.BLUE, Hex(2, -2)) { hand("arien-red-1") }
            minion(Team.BLUE, MinionType.MELEE, Hex(-3, -2)) // 4 away
            minion(Team.RED, MinionType.MELEE, Hex(0, -6))
            enragedByClaws()
            phase = PositionPhase.Resolving(next = "ursafar")
        }
        d.action("ursafar", ActionType.PRIMARY)
        d.defend("arien-red-1")
        d.expect(DecisionKind.CHOOSE_UNIT, "ursafar")
        assertThat(d.units().map { d.at(it) }).containsExactly(Hex(-3, -2))
    }

    // ---- Rip / Tear (red alternates) ------------------------------------------------------------

    @Test
    fun `Rip - enraged - After the attack gain 1 coin, not enraged nothing`() {
        for ((enraged, coins) in listOf(true to 3, false to 2)) {
            val d = prey("ursafar-red-2b", enraged)
            d.action("ursafar", ActionType.PRIMARY)
            d.unit(d.minionAt(Hex(0, -2))!!.id) // 2 coins for the minion
            assertThat(d.hero("ursafar").coins).isEqualTo(coins)
        }
    }

    @Test
    fun `Tear - enraged - gain 2 coins, and a defeated hero spends 1 additional Life counter`() {
        val d = pos {
            clearMinions()
            turn = 2
            hero("ursafar", Team.RED, Hex(1, -2)) {
                clawsResolved()
                played("ursafar-red-3b")
            }
            hero("arien", Team.BLUE, Hex(2, -2))
            lifeCounters(Team.BLUE, 5)
            enragedByClaws()
            phase = PositionPhase.Resolving(next = "ursafar")
        }
        d.action("ursafar", ActionType.PRIMARY)
        d.defend(null)
        assertThat(d.at("arien")).isNull()
        assertThat(d.s.state.lifeCounters[Team.BLUE]).isEqualTo(3) // 1 for the defeat (Tier I) + 1
        assertThat(d.hero("ursafar").coins).isEqualTo(3) // 1 for level 1 Arien + 2
    }

    // ---- Sniff Out / Eyes on the Prey / Apex Predator (green) ----------------------------------

    @Test
    fun `Sniff Out - if enraged, an enemy hero in range discards a card, if able`() {
        val d = pos {
            clearMinions()
            turn = 2
            hero("ursafar", Team.RED, Hex(1, -2)) {
                clawsResolved()
                played("ursafar-green-1") // Range 2
            }
            hero("arien", Team.BLUE, Hex(3, -2)) { hand("arien-gold", "arien-silver") }
            hero("brogan", Team.BLUE, Hex(4, -2)) // 3 away
            enragedByClaws()
            phase = PositionPhase.Resolving(next = "ursafar")
        }
        d.action("ursafar", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_CARD, "arien")
        d.card("arien-gold")
        assertThat(d.zoneOf("arien", "arien-gold")).isEqualTo(Zone.DISCARD)
    }

    @Test
    fun `Sniff Out - enraged with no enemy hero in range, the Primary action is not offered (RULINGS 51)`() {
        val d = pos {
            clearMinions()
            turn = 2
            hero("ursafar", Team.RED, Hex(1, -2)) {
                clawsResolved()
                played("ursafar-green-1") // Range 2
            }
            hero("arien", Team.BLUE, Hex(4, -5)) { hand("arien-gold") } // 3 away
            enragedByClaws()
            phase = PositionPhase.Resolving(next = "ursafar")
        }
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
    }

    @Test
    fun `Eyes on the Prey - not enraged - nothing`() {
        val d = pos {
            clearMinions()
            hero("ursafar", Team.RED, Hex(1, -2)) { played("ursafar-green-2a") }
            hero("arien", Team.BLUE, Hex(3, -2)) { hand("arien-gold", "arien-silver") }
            phase = PositionPhase.Resolving(next = "ursafar")
        }
        d.action("ursafar", ActionType.PRIMARY)
        assertThat(d.d.kind).isEqualTo(DecisionKind.PLAY_CARD)
        assertThat(d.enraged()).isFalse() // green cards do not enrage
    }

    @Test
    fun `Apex Predator - discards a card, or is defeated`() {
        val d = pos {
            clearMinions()
            turn = 2
            hero("ursafar", Team.RED, Hex(1, -2)) {
                clawsResolved()
                played("ursafar-green-3a")
            }
            hero("arien", Team.BLUE, Hex(4, -2)) { hand() } // 3 away, no cards
            enragedByClaws()
            phase = PositionPhase.Resolving(next = "ursafar")
        }
        d.action("ursafar", ActionType.PRIMARY)
        assertThat(d.at("arien")).isNull()
    }

    // ---- Instinctive Reaction / Evolutionary Response (green alternates) -----------------------

    private fun instinct(card: String) = pos {
        clearMinions()
        turn = 2
        hero("ursafar", Team.RED, Hex(1, -2)) {
            clawsResolved()
            played(card)
            discard("ursafar-red-1", "ursafar-blue-1")
        }
        hero("arien", Team.BLUE, Hex(7, -2))
        minion(Team.BLUE, MinionType.MELEE, Hex(1, -1))
        minion(Team.BLUE, MinionType.MELEE, Hex(1, -6))
        minion(Team.RED, MinionType.MELEE, Hex(0, -6))
        enragedByClaws()
        phase = PositionPhase.Resolving(next = "ursafar")
    }

    @Test
    fun `Instinctive Reaction - choose one - perform the primary action of a discarded card (it stays discarded)`() {
        val d = instinct("ursafar-green-2b")
        d.action("ursafar", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_OPTION, "ursafar")
        assertThat(d.labels()).hasSize(2)
        d.option(0)
        d.expect(DecisionKind.CHOOSE_CARD, "ursafar")
        assertThat(d.cards()).containsExactly("ursafar-red-1", "ursafar-blue-1")
        d.card("ursafar-red-1") // Prey Drive: attack the adjacent minion
        assertThat(d.minionAt(Hex(1, -1))).isNull()
        assertThat(d.zoneOf("ursafar", "ursafar-red-1")).isEqualTo(Zone.DISCARD)
    }

    @Test
    fun `Instinctive Reaction - choose one - you may retrieve a discarded card`() {
        val d = instinct("ursafar-green-2b")
        d.action("ursafar", ActionType.PRIMARY)
        d.option(1)
        d.card("ursafar-blue-1")
        assertThat(d.zoneOf("ursafar", "ursafar-blue-1")).isEqualTo(Zone.HAND)
    }

    @Test
    fun `Evolutionary Response - choose one, or both, in either order`() {
        val d = instinct("ursafar-green-3b")
        d.action("ursafar", ActionType.PRIMARY)
        assertThat(d.labels()).hasSize(4)
        d.option(2) // both: perform first, then retrieve
        d.card("ursafar-red-1")
        assertThat(d.minionAt(Hex(1, -1))).isNull()
        d.expect(DecisionKind.CHOOSE_CARD, "ursafar")
        d.card("ursafar-red-1") // retrieve the card just performed
        assertThat(d.zoneOf("ursafar", "ursafar-red-1")).isEqualTo(Zone.HAND)
    }

    @Test
    fun `Instinctive Reaction - not enraged - nothing`() {
        val d = pos {
            clearMinions()
            hero("ursafar", Team.RED, Hex(1, -2)) {
                played("ursafar-green-2b")
                discard("ursafar-red-1")
            }
            hero("arien", Team.BLUE, Hex(7, -2))
            phase = PositionPhase.Resolving(next = "ursafar")
        }
        d.action("ursafar", ActionType.PRIMARY)
        assertThat(d.d.kind).isEqualTo(DecisionKind.PLAY_CARD)
        assertThat(d.zoneOf("ursafar", "ursafar-red-1")).isEqualTo(Zone.DISCARD)
    }

    // ---- Prowling Brute / Rampaging Beast / Unstoppable Force (blue) ---------------------------

    private fun prowl(card: String, enraged: Boolean = true) = pos {
        clearMinions()
        turn = 2
        hero("ursafar", Team.RED, Hex(1, -2)) {
            clawsResolved()
            played(card) // Movement 1
        }
        hero("arien", Team.BLUE, Hex(7, -2))
        minion(Team.BLUE, MinionType.MELEE, Hex(3, -2))
        token("token_rock", Hex(1, -1))
        if (enraged) enragedByClaws()
        phase = PositionPhase.Resolving(next = "ursafar")
    }

    @Test
    fun `Prowling Brute - if enraged, after movement, you may swap with a unit or a token adjacent to you`() {
        val d = prowl("ursafar-blue-1")
        d.action("ursafar", ActionType.PRIMARY)
        d.space(2, -2) // the movement first
        d.expect(DecisionKind.CHOOSE_UNIT, "ursafar")
        assertThat(d.units().map { d.at(it) }.toSet()).containsExactly(Hex(3, -2), Hex(1, -1))
        d.unit(d.minionAt(Hex(3, -2))!!.id)
        assertThat(d.at("ursafar")).isEqualTo(Hex(3, -2))
        assertThat(d.minionAt(Hex(2, -2))).isNotNull()
    }

    @Test
    fun `Prowling Brute - not enraged - no swap`() {
        val d = prowl("ursafar-blue-1", enraged = false)
        d.action("ursafar", ActionType.PRIMARY)
        d.space(2, -2)
        assertThat(d.d.kind).isEqualTo(DecisionKind.PLAY_CARD)
    }

    @Test
    fun `Rampaging Beast and Unstoppable Force - if you swap, move up to 1 (2) additional spaces`() {
        for ((card, n) in listOf("ursafar-blue-2a" to 1, "ursafar-blue-3a" to 2)) {
            val d = prowl(card)
            d.action("ursafar", ActionType.PRIMARY)
            d.decline() // no movement
            d.token(d.s.state.pieces.single().id) // swap with the token at (1,-1)
            d.expect(DecisionKind.CHOOSE_SPACE, "ursafar")
            assertThat(d.d.options.any { it is Choice.Decline }).isTrue()
            assertThat(d.spaces().maxOf { HexGrid.distance(Hex(1, -1), it) }).isEqualTo(n)
        }
    }

    // ---- Cold Ire / Eyes of Flame (blue alternates) ---------------------------------------------

    @Test
    fun `Cold Ire - if enraged, +1 Movement (FAQ - for this action only)`() {
        for ((enraged, n) in listOf(true to 2, false to 1)) {
            val d = prowl("ursafar-blue-2b", enraged)
            d.action("ursafar", ActionType.PRIMARY)
            assertThat(d.spaces().maxOf { HexGrid.distance(Hex(1, -2), it) }).isEqualTo(n)
        }
    }

    @Test
    fun `Cold Ire - FAQ - the bonus does not carry to another card's movement`() {
        val d = pos {
            clearMinions()
            turn = 3
            hero("ursafar", Team.RED, Hex(-1, -5)) {
                clawsResolved()
                resolved(2, "ursafar-blue-2b")
                played("ursafar-red-1") // secondary Movement 3
            }
            hero("arien", Team.BLUE, Hex(7, -2))
            enragedByClaws()
            effect(
                "ursafar",
                "ursafar-blue-2b",
                Timing.THIS_ROUND,
                ActionKind.MOVEMENT,
                madeTurn = 2,
                key = UrsafarHero.ENRAGED,
            )
            phase = PositionPhase.Resolving(next = "ursafar")
        }
        d.action("ursafar", ActionType.MOVEMENT)
        assertThat(d.spaces().maxOf { HexGrid.distance(Hex(-1, -5), it) }).isEqualTo(3)
    }

    @Test
    fun `Eyes of Flame - if enraged, +2 Movement`() {
        val d = prowl("ursafar-blue-3b")
        d.action("ursafar", ActionType.PRIMARY)
        assertThat(d.spaces().maxOf { HexGrid.distance(Hex(1, -2), it) }).isEqualTo(3)
    }

    // ---- enraged: duration and cancelling -------------------------------------------------------

    @Test
    fun `Enraged - This round - still on turn 4, gone in the next round`() {
        val d = pos {
            clearMinions()
            hero("ursafar", Team.RED, Hex(1, -2)) { clawsResolved() }
            hero("arien", Team.BLUE, Hex(7, -2))
            minion(Team.RED, MinionType.MELEE, Hex(0, -6))
            minion(Team.BLUE, MinionType.MELEE, Hex(1, -6))
            enragedByClaws()
            phase = PositionPhase.EndOfRound
        }
        d.expect(DecisionKind.PLAY_CARD)
        assertThat(d.s.state.round).isEqualTo(2)
        assertThat(d.enraged()).isFalse()
    }

    @Test
    fun `Enraged - cancelled when Ursafar is defeated`() {
        val d = pos {
            clearMinions()
            turn = 2
            hero("arien", Team.BLUE, Hex(2, -2)) { played("arien-red-1") }
            hero("ursafar", Team.RED, Hex(1, -2)) { clawsResolved() }
            enragedByClaws()
            phase = PositionPhase.Resolving(next = "arien")
        }
        assertThat(d.enraged()).isTrue()
        d.action("arien", ActionType.PRIMARY)
        d.defend(null)
        assertThat(d.enraged()).isFalse()
    }

    // ---- Unbound Fury (Ultimate) ----------------------------------------------------------------

    @Test
    fun `Unbound Fury - you are always enraged`() {
        val d = pos {
            clearMinions()
            hero("ursafar", Team.RED, Hex(1, -2)) {
                played("ursafar-green-1")
                ultimate()
            }
            hero("arien", Team.BLUE, Hex(3, -2)) { hand("arien-gold", "arien-silver") }
            phase = PositionPhase.Resolving(next = "ursafar")
        }
        d.action("ursafar", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_CARD, "arien")
    }

    @Test
    fun `Unbound Fury - all your resolved cards count as active (Angry Roar)`() {
        val d = pos {
            clearMinions()
            turn = 2
            hero("ursafar", Team.RED, Hex(1, -2)) {
                resolved(1, "ursafar-green-1") // Sniff Out: no active effect of its own
                played("ursafar-silver")
                ultimate()
            }
            hero("arien", Team.BLUE, Hex(3, -2)) { hand("arien-gold", "arien-silver") }
            phase = PositionPhase.Resolving(next = "ursafar")
        }
        d.action("ursafar", ActionType.PRIMARY) // the only active card: performed without a question
        d.expect(DecisionKind.CHOOSE_CARD, "arien")
    }

    // ---- random games ---------------------------------------------------------------------------

    @Test
    fun `random legal games with Ursafar seated keep every invariant`() {
        Batch2Games.play("ursafar", games = 300, seed = 51)
    }
}
