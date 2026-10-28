package dev.quinton.goa.core.game.heroes

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.ANY_HERO_ENGINE
import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.core.game.engine.ActionKind
import dev.quinton.goa.core.game.engine.Game
import dev.quinton.goa.core.game.engine.RunMode
import dev.quinton.goa.core.game.engine.Timing
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.game.engine.cardValue
import dev.quinton.goa.core.game.engine.initiativeOf
import dev.quinton.goa.core.game.pos
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.catalog.Stat
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

/** Tigerclaw the Cutpurse: every card, clause by clause (text from `catalog/heroes/tigerclaw.json`), with the FAQ. */
class TigerclawTest {
    private fun game(
        d: dev.quinton.goa.core.game.Driver,
    ) = Game(ANY_HERO_ENGINE.data, d.s.state, emptyList(), RunMode.PROBE)

    // ---- Blink Strike (gold) / Nimble Strike (Handicap) ---------------------------------------

    @Test
    fun `Blink Strike - move 2 spaces in a straight line through an enemy unit, then target that unit`() {
        val d = pos {
            clearMinions()
            hero("tigerclaw", Team.BLUE, Hex(1, -2)) { played("tigerclaw-gold") }
            hero("wasp", Team.RED, Hex(0, -2)) { hand("wasp-blue-1") }
            hero("arien", Team.BLUE, Hex(2, -2)) // a friendly unit is not passed through
            phase = PositionPhase.Resolving(next = "tigerclaw")
        }
        d.action("tigerclaw", ActionType.PRIMARY)
        // The only legal move (west, through Wasp) and the only target are not asked.
        val p = d.expect(DecisionKind.DEFEND, "wasp")
        assertThat(d.at("tigerclaw")).isEqualTo(Hex(-1, -2))
        assertThat(p.context.attackTotal).isEqualTo(3)
    }

    @Test
    fun `Blink Strike - FAQ - if Tigerclaw cannot move through the target, he cannot attack`() {
        val d = pos {
            clearMinions()
            hero("tigerclaw", Team.BLUE, Hex(1, -2)) { played("tigerclaw-gold") }
            hero("wasp", Team.RED, Hex(0, -2)) { hand("wasp-blue-1") }
            token("token_rock", Hex(-1, -2)) // the space behind Wasp is not empty
            phase = PositionPhase.Resolving(next = "tigerclaw")
        }
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
    }

    @Test
    fun `Blink Strike - a minion passed through is the target and is defeated`() {
        val d = pos {
            clearMinions()
            hero("tigerclaw", Team.BLUE, Hex(1, -2)) { played("tigerclaw-gold") }
            hero("wasp", Team.RED, Hex(-5, -4))
            minion(Team.RED, MinionType.MELEE, Hex(0, -2))
            minion(Team.RED, MinionType.MELEE, Hex(4, -5))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -4))
            phase = PositionPhase.Resolving(next = "tigerclaw")
        }
        d.action("tigerclaw", ActionType.PRIMARY)
        assertThat(d.at("tigerclaw")).isEqualTo(Hex(-1, -2))
        assertThat(d.s.state.minions.none { it.at == Hex(0, -2) }).isTrue()
        assertThat(d.hero("tigerclaw").coins).isEqualTo(2)
    }

    @Test
    fun `Blink Strike - APP - moving through an immune heavy minion, there is no target`() {
        val d = pos {
            clearMinions()
            hero("tigerclaw", Team.BLUE, Hex(1, -2)) { played("tigerclaw-gold") }
            hero("wasp", Team.RED, Hex(-5, -4))
            minion(Team.RED, MinionType.HEAVY, Hex(0, -2)) // immune: a Red melee minion is in the Battle Zone
            minion(Team.RED, MinionType.MELEE, Hex(4, -5))
            phase = PositionPhase.Resolving(next = "tigerclaw")
        }
        d.action("tigerclaw", ActionType.PRIMARY)
        assertThat(d.at("tigerclaw")).isEqualTo(Hex(-1, -2))
        assertThat(d.texts()).contains("Tigerclaw's Blink Strike stops: that unit can't be targeted.")
    }

    @Test
    fun `Nimble Strike - only through an enemy hero, then target that hero`() {
        val d = pos {
            clearMinions()
            hero("tigerclaw", Team.BLUE, Hex(1, -2)) { played("tigerclaw-handicap") }
            hero("wasp", Team.RED, Hex(2, -2)) { hand("wasp-blue-1") }
            minion(Team.RED, MinionType.MELEE, Hex(0, -2)) // a minion with an empty space behind: not offered
            minion(Team.RED, MinionType.MELEE, Hex(4, -5))
            phase = PositionPhase.Resolving(next = "tigerclaw")
        }
        d.action("tigerclaw", ActionType.PRIMARY)
        d.expect(DecisionKind.DEFEND, "wasp")
        assertThat(d.at("tigerclaw")).isEqualTo(Hex(3, -2))
    }

    // ---- Blend Into Shadows (silver) ------------------------------------------------------------

    @Test
    fun `Blend Into Shadows - adjacent to terrain, place yourself in radius, and next turn you are immune`() {
        val d = pos {
            clearMinions()
            hero("tigerclaw", Team.BLUE, Hex(1, -2)) { played("tigerclaw-silver") } // (1,-3) is terrain
            hero("wasp", Team.RED, Hex(-5, -4))
            phase = PositionPhase.Resolving(next = "tigerclaw")
        }
        d.action("tigerclaw", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_SPACE, "tigerclaw")
        assertThat(d.spaces()).contains(Hex(3, -2)) // 2 away
        assertThat(d.spaces()).doesNotContain(Hex(4, -2)) // 3 away: outside Radius 2
        d.space(3, -2)
        assertThat(d.at("tigerclaw")).isEqualTo(Hex(3, -2))
        assertThat(
            d.s.state.effects.single { it.cardId == "tigerclaw-silver" }.timing,
        ).isEqualTo(Timing.NEXT_TURN)
    }

    @Test
    fun `Blend Into Shadows - not adjacent to terrain, no primary action`() {
        val d = pos {
            clearMinions()
            hero("tigerclaw", Team.BLUE, Hex(0, -5)) { played("tigerclaw-silver") }
            hero("wasp", Team.RED, Hex(-5, -4))
            phase = PositionPhase.Resolving(next = "tigerclaw")
        }
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
    }

    @Test
    fun `Blend Into Shadows - Next turn, immune to enemy attack actions only on the next turn`() {
        for ((turn, immune) in listOf(2 to true, 1 to false)) {
            val d = pos {
                clearMinions()
                this.turn = turn
                hero("tigerclaw", Team.BLUE, Hex(1, -2)) {
                    if (turn == 2) resolved(1, "tigerclaw-silver") else played("tigerclaw-silver")
                }
                hero("wasp", Team.RED, Hex(0, -2)) { played("wasp-gold") }
                effect("tigerclaw", "tigerclaw-silver", Timing.NEXT_TURN, ActionKind.SKILL, madeTurn = 1)
                phase = PositionPhase.Resolving(next = "wasp")
            }
            if (immune) {
                assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
            } else {
                assertThat(d.actions()).contains(ActionType.PRIMARY)
            }
        }
    }

    // ---- Hit and Run / Combat Reflexes / Leaping Strike (red) ----------------------------------

    @Test
    fun `Hit and Run - target a unit adjacent to you, after the attack you may move 1 space`() {
        val d = pos {
            clearMinions()
            hero("tigerclaw", Team.BLUE, Hex(1, -2)) { played("tigerclaw-red-1") }
            hero("wasp", Team.RED, Hex(0, -2)) { hand("wasp-blue-1") }
            phase = PositionPhase.Resolving(next = "tigerclaw")
        }
        d.action("tigerclaw", ActionType.PRIMARY)
        d.defend("wasp-blue-1") // 5 against 3: defended
        d.expect(DecisionKind.CHOOSE_SPACE, "tigerclaw")
        assertThat(d.spaces()).containsExactly(Hex(2, -2), Hex(2, -3), Hex(0, -1), Hex(1, -1))
        d.space(2, -2)
        assertThat(d.at("tigerclaw")).isEqualTo(Hex(2, -2))
    }

    @Test
    fun `Combat Reflexes - after moving before the attack, no move after it`() {
        val d = pos {
            clearMinions()
            hero("tigerclaw", Team.BLUE, Hex(2, -2)) { played("tigerclaw-red-2a") }
            hero("wasp", Team.RED, Hex(0, -2)) {
                played("wasp-gold")
                hand("wasp-blue-1")
            }
            phase = PositionPhase.Resolving(next = "tigerclaw")
        }
        d.action("tigerclaw", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_SPACE, "tigerclaw")
        d.space(1, -2) // before the attack: now adjacent to Wasp
        d.defend("wasp-blue-1")
        d.expect(DecisionKind.CHOOSE_ACTION, "wasp") // no move after the attack
    }

    @Test
    fun `Combat Reflexes - if you did not move before the attack, you may move 1 space after it`() {
        val d = pos {
            clearMinions()
            hero("tigerclaw", Team.BLUE, Hex(1, -2)) { played("tigerclaw-red-2a") }
            hero("wasp", Team.RED, Hex(0, -2)) {
                played("wasp-gold")
                hand("wasp-blue-1")
            }
            phase = PositionPhase.Resolving(next = "tigerclaw")
        }
        d.action("tigerclaw", ActionType.PRIMARY)
        d.decline() // no move before
        d.defend("wasp-blue-1")
        d.expect(DecisionKind.CHOOSE_SPACE, "tigerclaw")
        d.space(2, -2)
        assertThat(d.at("tigerclaw")).isEqualTo(Hex(2, -2))
    }

    @Test
    fun `Leaping Strike - you may move 1 space before and after the attack`() {
        val d = pos {
            clearMinions()
            hero("tigerclaw", Team.BLUE, Hex(2, -2)) { played("tigerclaw-red-3a") }
            hero("wasp", Team.RED, Hex(0, -2)) { hand("wasp-blue-1") }
            phase = PositionPhase.Resolving(next = "tigerclaw")
        }
        d.action("tigerclaw", ActionType.PRIMARY)
        d.space(1, -2)
        d.defend("wasp-blue-1") // 5 against 4
        d.expect(DecisionKind.CHOOSE_SPACE, "tigerclaw")
        d.space(2, -2)
        assertThat(d.at("tigerclaw")).isEqualTo(Hex(2, -2))
    }

    // ---- Backstab / Backstab with a Ballista (red alternates) -----------------------------------

    @Test
    fun `Backstab - +2 Attack if a friendly unit is adjacent to the target, Tigerclaw himself does not count`() {
        for ((friend, total) in listOf(true to 7, false to 5)) {
            val d = pos {
                clearMinions()
                hero("tigerclaw", Team.BLUE, Hex(1, -2)) { played("tigerclaw-red-2b") }
                hero("wasp", Team.RED, Hex(0, -2)) { hand("wasp-blue-1") }
                if (friend) minion(Team.BLUE, MinionType.MELEE, Hex(-1, -2))
                phase = PositionPhase.Resolving(next = "tigerclaw")
            }
            d.action("tigerclaw", ActionType.PRIMARY)
            assertThat(d.expect(DecisionKind.DEFEND, "wasp").context.attackTotal).isEqualTo(total)
        }
    }

    @Test
    fun `Backstab with a Ballista - with a friend next to the target, +2 and no primary action to defend`() {
        for (friend in listOf(true, false)) {
            val d = pos {
                clearMinions()
                hero("tigerclaw", Team.BLUE, Hex(1, -2)) { played("tigerclaw-red-3b") }
                // Stop Projectiles has a primary Defense action; Magnetic Dagger a secondary one.
                hero("wasp", Team.RED, Hex(0, -2)) { hand("wasp-green-1", "wasp-gold") }
                if (friend) minion(Team.BLUE, MinionType.MELEE, Hex(-1, -2))
                phase = PositionPhase.Resolving(next = "tigerclaw")
            }
            d.action("tigerclaw", ActionType.PRIMARY)
            val p = d.expect(DecisionKind.DEFEND, "wasp")
            assertThat(p.context.rangedAttack).isTrue()
            if (friend) {
                assertThat(p.context.attackTotal).isEqualTo(7)
                assertThat(d.cards()).containsExactly("wasp-gold")
            } else {
                assertThat(p.context.attackTotal).isEqualTo(5)
                assertThat(d.cards()).containsExactly("wasp-green-1", "wasp-gold")
            }
        }
    }

    // ---- Light-Fingered / Pick Pocket / Master Thief (green) ------------------------------------

    @Test
    fun `Light-Fingered - you may move 1 space, take 1 coin from an adjacent enemy hero, then you may move 1 space`() {
        val d = pos {
            clearMinions()
            hero("tigerclaw", Team.BLUE, Hex(2, -2)) { played("tigerclaw-green-1") }
            hero("wasp", Team.RED, Hex(0, -2)) { coins = 3 }
            phase = PositionPhase.Resolving(next = "tigerclaw")
        }
        d.action("tigerclaw", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_SPACE, "tigerclaw")
        d.space(1, -2)
        assertThat(d.hero("tigerclaw").coins).isEqualTo(1)
        assertThat(d.hero("wasp").coins).isEqualTo(2)
        d.expect(DecisionKind.CHOOSE_SPACE, "tigerclaw")
        d.space(2, -2)
        assertThat(d.at("tigerclaw")).isEqualTo(Hex(2, -2))
    }

    @Test
    fun `Light-Fingered - FAQ - no adjacent enemy hero after the first move, the card fizzles before the last move`() {
        val d = pos {
            clearMinions()
            hero("tigerclaw", Team.BLUE, Hex(2, -2)) { played("tigerclaw-green-1") }
            hero("wasp", Team.RED, Hex(-5, -4)) {
                coins = 3
                played("wasp-gold")
            }
            phase = PositionPhase.Resolving(next = "tigerclaw")
        }
        d.action("tigerclaw", ActionType.PRIMARY)
        d.space(1, -2)
        assertThat(d.texts()).contains("Tigerclaw's Light-Fingered stops: no enemy hero adjacent.")
        d.expect(DecisionKind.CHOOSE_ACTION, "wasp")
        assertThat(d.at("tigerclaw")).isEqualTo(Hex(1, -2))
    }

    @Test
    fun `Light-Fingered - APP - an adjacent enemy hero with no coins gives nothing, so no second move`() {
        val d = pos {
            clearMinions()
            hero("tigerclaw", Team.BLUE, Hex(1, -2)) { played("tigerclaw-green-1") }
            hero("wasp", Team.RED, Hex(0, -2)) { played("wasp-gold") }
            phase = PositionPhase.Resolving(next = "tigerclaw")
        }
        d.action("tigerclaw", ActionType.PRIMARY)
        d.decline() // no first move
        d.expect(DecisionKind.CHOOSE_ACTION, "wasp")
        assertThat(d.hero("tigerclaw").coins).isEqualTo(0)
    }

    @Test
    fun `Pick Pocket - move up to 2 spaces, take 1 coin, then you may move 1 space`() {
        val d = pos {
            clearMinions()
            hero("tigerclaw", Team.BLUE, Hex(3, -2)) { played("tigerclaw-green-2a") }
            hero("wasp", Team.RED, Hex(0, -2)) { coins = 2 }
            phase = PositionPhase.Resolving(next = "tigerclaw")
        }
        d.action("tigerclaw", ActionType.PRIMARY)
        assertThat(d.spaces()).contains(Hex(1, -2)) // 2 spaces
        d.space(1, -2)
        assertThat(d.hero("tigerclaw").coins).isEqualTo(1)
        d.expect(DecisionKind.CHOOSE_SPACE, "tigerclaw")
        assertThat(d.spaces().all { game(d).distance(it, Hex(1, -2)) == 1 }).isTrue()
    }

    @Test
    fun `Master Thief - take 1 or 2 coins, then you may move up to 2 spaces`() {
        val d = pos {
            clearMinions()
            hero("tigerclaw", Team.BLUE, Hex(1, -2)) { played("tigerclaw-green-3a") }
            hero("wasp", Team.RED, Hex(0, -2)) { coins = 5 }
            phase = PositionPhase.Resolving(next = "tigerclaw")
        }
        d.action("tigerclaw", ActionType.PRIMARY)
        d.decline() // move 0
        d.expect(DecisionKind.CHOOSE_NUMBER, "tigerclaw")
        d.number(2)
        assertThat(d.hero("tigerclaw").coins).isEqualTo(2)
        assertThat(d.hero("wasp").coins).isEqualTo(3)
        d.expect(DecisionKind.CHOOSE_SPACE, "tigerclaw")
        assertThat(d.spaces()).contains(Hex(3, -2))
    }

    // ---- Poisoned Dagger / Poisoned Dart (green alternates) -------------------------------------

    @Test
    fun `Poisoned Dagger - give a hero in range a Poison marker, -1 Initiative, Attack and Defense`() {
        val d = pos {
            clearMinions()
            hero("tigerclaw", Team.BLUE, Hex(1, -2)) { played("tigerclaw-green-2b") }
            hero("wasp", Team.RED, Hex(-1, -2)) {
                played("wasp-gold")
                hand("wasp-blue-1")
            }
            hero("arien", Team.BLUE, Hex(4, -2)) // a friendly hero in range may be chosen too
            phase = PositionPhase.Resolving(next = "tigerclaw")
        }
        d.action("tigerclaw", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_UNIT, "tigerclaw")
        assertThat(d.units()).containsExactly("wasp", "arien")
        d.unit("wasp")
        assertThat(d.hero("wasp").markers.single().kind).isEqualTo(TigerclawHero.POISON)
        val g = game(d)
        assertThat(g.initiativeOf("wasp")).isEqualTo(11)
        assertThat(g.cardValue("wasp", "wasp-gold", Stat.ATTACK)).isEqualTo(2)
        assertThat(g.cardValue("wasp", "wasp-blue-1", Stat.DEFENSE)).isEqualTo(4)
        assertThat(g.cardValue("arien", "arien-gold", Stat.ATTACK)).isEqualTo(4)
    }

    @Test
    fun `Poisoned Dagger - one Poison marker, giving it again moves it`() {
        val d = pos {
            clearMinions()
            hero("tigerclaw", Team.BLUE, Hex(1, -2)) { played("tigerclaw-green-2b") }
            hero("wasp", Team.RED, Hex(-1, -2))
            hero("xargatha", Team.RED, Hex(-5, -4)) { marker(TigerclawHero.POISON, from = "tigerclaw") }
            phase = PositionPhase.Resolving(next = "tigerclaw")
        }
        d.action("tigerclaw", ActionType.PRIMARY) // Wasp is the only hero in range
        assertThat(d.hero("wasp").markers).hasSize(1)
        assertThat(d.hero("xargatha").markers).isEmpty()
    }

    @Test
    fun `Poisoned Dart - FAQ - -2 to each value, which can drop to 0 and below`() {
        val d = pos {
            clearMinions()
            hero("tigerclaw", Team.BLUE, Hex(2, -2)) { played("tigerclaw-green-3b") }
            hero("wasp", Team.RED, Hex(-1, -2)) {
                played("wasp-green-3a") // Initiative 2
                hand("wasp-handicap") // Defense 1
            }
            hero("brogan", Team.BLUE, Hex(-2, -2)) { played("brogan-gold") } // Initiative 11, out of range 3
            phase = PositionPhase.Resolving(next = "tigerclaw")
        }
        d.action("tigerclaw", ActionType.PRIMARY) // only Wasp is in range
        val g = game(d)
        assertThat(g.initiativeOf("wasp")).isEqualTo(0)
        assertThat(g.cardValue("wasp", "wasp-handicap", Stat.DEFENSE)).isEqualTo(-1)
        assertThat(g.cardValue("wasp", "wasp-handicap", Stat.ATTACK)).isEqualTo(0)
        // A negative Defense total is still calculated (FAQ): Brogan's Attack 4 against -1.
        d.action("brogan", ActionType.PRIMARY)
        assertThat(d.expect(DecisionKind.DEFEND, "wasp").context.defensePreview["wasp-handicap"]).isEqualTo(-1)
    }

    // ---- Dodge / Sidestep / Evade (blue) ---------------------------------------------------------

    @Test
    fun `Dodge - Block a ranged attack, a non-ranged attack defeats Tigerclaw`() {
        for ((card, ranged) in listOf("arien-red-2b" to true, "arien-gold" to false)) {
            val d = pos {
                clearMinions()
                hero("tigerclaw", Team.BLUE, Hex(1, -2)) { hand("tigerclaw-blue-1") }
                hero("arien", Team.RED, if (ranged) Hex(3, -2) else Hex(2, -2)) { played(card) }
                phase = PositionPhase.Resolving(next = "arien")
            }
            d.action("arien", ActionType.PRIMARY)
            d.defend("tigerclaw-blue-1")
            if (ranged) {
                assertThat(d.texts()).contains("Tigerclaw defends with Dodge: Blocked.")
                assertThat(d.at("tigerclaw")).isEqualTo(Hex(1, -2))
            } else {
                assertThat(d.texts()).contains("Tigerclaw defends with Dodge: The Block fails.")
                assertThat(d.at("tigerclaw")).isNull()
            }
        }
    }

    @Test
    fun `Sidestep - Block a ranged attack, then you may move 1 space`() {
        val d = pos {
            clearMinions()
            hero("tigerclaw", Team.BLUE, Hex(1, -2)) { hand("tigerclaw-blue-2a") }
            hero("arien", Team.RED, Hex(3, -2)) { played("arien-red-2b") }
            phase = PositionPhase.Resolving(next = "arien")
        }
        d.action("arien", ActionType.PRIMARY)
        d.defend("tigerclaw-blue-2a")
        d.expect(DecisionKind.CHOOSE_SPACE, "tigerclaw")
        d.space(0, -2)
        assertThat(d.at("tigerclaw")).isEqualTo(Hex(0, -2))
        assertThat(d.texts()).contains("Tigerclaw defends with Sidestep: Blocked.")
    }

    @Test
    fun `Sidestep - APP - against a non-ranged attack the Block fails and the rest of the text is skipped`() {
        val d = pos {
            clearMinions()
            hero("tigerclaw", Team.BLUE, Hex(1, -2)) { hand("tigerclaw-blue-2a") }
            hero("arien", Team.RED, Hex(2, -2)) { played("arien-gold") }
            phase = PositionPhase.Resolving(next = "arien")
        }
        d.action("arien", ActionType.PRIMARY)
        d.defend("tigerclaw-blue-2a") // no move question
        assertThat(d.at("tigerclaw")).isNull()
    }

    @Test
    fun `Evade - Block a ranged attack, may move 1 space and retrieve your resolved or discarded basic skill`() {
        val d = pos {
            clearMinions()
            turn = 2
            hero("tigerclaw", Team.BLUE, Hex(1, -2)) {
                resolved(1, "tigerclaw-silver")
                discard("tigerclaw-gold") // a basic card, but not a skill card
                hand("tigerclaw-blue-3a")
            }
            hero("arien", Team.RED, Hex(3, -2)) { played("arien-red-3b") }
            phase = PositionPhase.Resolving(next = "arien")
        }
        d.action("arien", ActionType.PRIMARY)
        d.defend("tigerclaw-blue-3a")
        d.expect(DecisionKind.CHOOSE_SPACE, "tigerclaw")
        d.decline() // no move
        d.expect(DecisionKind.CHOOSE_CARD, "tigerclaw")
        assertThat(d.cards()).containsExactly("tigerclaw-silver")
        d.card("tigerclaw-silver")
        assertThat(d.zoneOf("tigerclaw", "tigerclaw-silver")).isEqualTo(Zone.HAND)
        assertThat(d.at("tigerclaw")).isEqualTo(Hex(1, -2))
    }

    // ---- Parry / Riposte (blue alternates) -------------------------------------------------------

    @Test
    fun `Parry - Block a non-ranged attack, the attacker discards a card, if able`() {
        val d = pos {
            clearMinions()
            hero("tigerclaw", Team.BLUE, Hex(1, -2)) { hand("tigerclaw-blue-2b") }
            hero("arien", Team.RED, Hex(2, -2)) {
                played("arien-gold")
                hand("arien-blue-1", "arien-red-1")
            }
            phase = PositionPhase.Resolving(next = "arien")
        }
        d.action("arien", ActionType.PRIMARY)
        d.defend("tigerclaw-blue-2b")
        d.expect(DecisionKind.CHOOSE_CARD, "arien") // the attacker chooses the card
        d.card("arien-red-1")
        assertThat(d.zoneOf("arien", "arien-red-1")).isEqualTo(Zone.DISCARD)
        assertThat(d.texts()).contains("Tigerclaw defends with Parry: Blocked.")
        assertThat(d.at("tigerclaw")).isEqualTo(Hex(1, -2))
    }

    @Test
    fun `Parry - APP - a ranged attack is not blocked and the attacker discards nothing`() {
        val d = pos {
            clearMinions()
            hero("tigerclaw", Team.BLUE, Hex(1, -2)) { hand("tigerclaw-blue-2b") }
            hero("arien", Team.RED, Hex(3, -2)) {
                played("arien-red-2b")
                hand("arien-blue-1", "arien-red-1")
            }
            phase = PositionPhase.Resolving(next = "arien")
        }
        d.action("arien", ActionType.PRIMARY)
        d.defend("tigerclaw-blue-2b")
        assertThat(d.at("tigerclaw")).isNull()
        assertThat(d.zoneOf("arien", "arien-red-1")).isEqualTo(Zone.HAND)
    }

    @Test
    fun `Riposte - Block a non-ranged attack, the attacker discards a card, or is defeated`() {
        val d = pos {
            clearMinions()
            hero("tigerclaw", Team.BLUE, Hex(1, -2)) { hand("tigerclaw-blue-3b") }
            hero("arien", Team.RED, Hex(2, -2)) {
                played("arien-gold")
                hand()
            }
            phase = PositionPhase.Resolving(next = "arien")
        }
        d.action("arien", ActionType.PRIMARY)
        d.defend("tigerclaw-blue-3b")
        assertThat(d.at("arien")).isNull()
        assertThat(d.at("tigerclaw")).isEqualTo(Hex(1, -2))
        assertThat(d.hero("tigerclaw").coins).isEqualTo(1) // Arien's level
    }

    // ---- Cloak and Daggers (Ultimate) ------------------------------------------------------------

    @Test
    fun `Cloak and Daggers - after a basic action you may repeat it once`() {
        val d = pos {
            clearMinions()
            hero("tigerclaw", Team.BLUE, Hex(1, -2)) {
                ultimate()
                played("tigerclaw-gold")
            }
            hero("wasp", Team.RED, Hex(-5, -4))
            phase = PositionPhase.Resolving(next = "tigerclaw")
        }
        d.action("tigerclaw", ActionType.MOVEMENT) // a secondary action on a basic card is a basic action (p.9)
        d.space(2, -2)
        d.expect(DecisionKind.CONFIRM, "tigerclaw")
        d.yes()
        d.space(3, -2)
        assertThat(d.at("tigerclaw")).isEqualTo(Hex(3, -2))
        // A repeated action is not repeated again (p.10).
        assertThat(d.pending?.kind).isNotEqualTo(DecisionKind.CONFIRM)
    }

    @Test
    fun `Cloak and Daggers - a repeated attack action cannot target the same unit`() {
        val d = pos {
            clearMinions()
            hero("tigerclaw", Team.BLUE, Hex(2, -2)) {
                ultimate()
                played("tigerclaw-gold")
            }
            hero("wasp", Team.RED, Hex(1, -2)) { hand("wasp-blue-1", "wasp-red-1") }
            minion(Team.RED, MinionType.MELEE, Hex(-1, -2))
            minion(Team.RED, MinionType.MELEE, Hex(4, -5))
            phase = PositionPhase.Resolving(next = "tigerclaw")
        }
        d.action("tigerclaw", ActionType.PRIMARY) // through Wasp to (0,-2)
        d.defend("wasp-blue-1")
        d.expect(DecisionKind.CONFIRM, "tigerclaw")
        d.yes()
        // Back through Wasp, or on through the minion.
        assertThat(d.spaces()).containsExactly(Hex(2, -2), Hex(-2, -2))
        d.space(2, -2)
        assertThat(d.texts()).contains("Tigerclaw's Blink Strike stops: that unit can't be targeted.")
        assertThat(d.s.events.filterIsInstance<GameEvent.Attacked>()).hasSize(1)
    }

    @Test
    fun `Cloak and Daggers - a non-basic action is not repeated`() {
        val d = pos {
            clearMinions()
            hero("tigerclaw", Team.BLUE, Hex(1, -2)) {
                ultimate()
                played("tigerclaw-red-1")
            }
            hero("wasp", Team.RED, Hex(0, -2)) {
                played("wasp-gold")
                hand("wasp-blue-1")
            }
            phase = PositionPhase.Resolving(next = "tigerclaw")
        }
        d.action("tigerclaw", ActionType.PRIMARY)
        d.defend("wasp-blue-1")
        d.decline() // Hit and Run: no move
        d.expect(DecisionKind.CHOOSE_ACTION, "wasp")
    }

    @Test
    fun `random games with Tigerclaw keep every invariant`() {
        batch1RandomGames("tigerclaw", games = 200, seed = 11)
    }
}
