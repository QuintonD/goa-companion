package dev.quinton.goa.core.game.heroes

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.engine.ActionKind
import dev.quinton.goa.core.game.engine.Timing
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.game.pos
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

/** Rowenna the Vanguard: every card, clause by clause (text from `catalog/heroes/rowenna.json`). */
class RowennaTest {
    // ---- Code of Chivalry (gold) / Earn the Spurs (Handicap) ----------------------------------

    @Test
    fun `Code of Chivalry - before the attack both you and the target may retrieve (FAQ - it may defend with it)`() {
        val d = pos {
            clearMinions()
            hero("rowenna", Team.RED, Hex(0, -2)) {
                played("rowenna-gold")
                discard("rowenna-red-1")
            }
            hero("arien", Team.BLUE, Hex(1, -2)) { hand("arien-gold") } // the rest is discarded
            phase = PositionPhase.Resolving(next = "rowenna")
        }
        d.action("rowenna", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_CARD, "rowenna")
        d.card("rowenna-red-1")
        assertThat(d.zoneOf("rowenna", "rowenna-red-1")).isEqualTo(Zone.HAND)
        // Then the target chooses.
        d.expect(DecisionKind.CHOOSE_CARD, "arien")
        d.card("arien-blue-1")
        d.expect(DecisionKind.DEFEND, "arien")
        assertThat(d.cards()).contains("arien-blue-1")
        d.defend("arien-blue-1") // Defense 5 against Attack 4
        assertThat(d.at("arien")).isEqualTo(Hex(1, -2))
    }

    @Test
    fun `Code of Chivalry - a minion target retrieves nothing and neither do you`() {
        lateinit var m: String
        val d = pos {
            clearMinions()
            hero("rowenna", Team.RED, Hex(0, -2)) {
                played("rowenna-gold")
                discard("rowenna-red-1")
            }
            hero("arien", Team.BLUE, Hex(8, -6))
            m = minion(Team.BLUE, MinionType.MELEE, Hex(1, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -5))
            minion(Team.RED, MinionType.MELEE, Hex(-1, -5))
            phase = PositionPhase.Resolving(next = "rowenna")
        }
        d.action("rowenna", ActionType.PRIMARY)
        assertThat(d.s.state.minion(m)).isNull()
        assertThat(d.zoneOf("rowenna", "rowenna-red-1")).isEqualTo(Zone.DISCARD)
    }

    @Test
    fun `Code of Chivalry - an enemy hero immune to Rowenna's attack actions cannot be targeted`() {
        val d = pos {
            clearMinions()
            hero("rowenna", Team.RED, Hex(0, -2)) { played("rowenna-gold") }
            hero("arien", Team.BLUE, Hex(1, -2)) { discard("arien-blue-2a") }
            hero("wasp", Team.RED, Hex(8, -6))
            // Expert Duelist: immune to attack actions of all enemy heroes except Wasp.
            effect("arien", "arien-blue-2a", Timing.THIS_TURN, ActionKind.DEFENSE, data = mapOf("attacker" to "wasp"))
            phase = PositionPhase.Resolving(next = "rowenna")
        }
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
    }

    @Test
    fun `Earn the Spurs - Target a hero adjacent to you`() {
        val d = pos {
            clearMinions()
            hero("rowenna", Team.RED, Hex(0, -2)) { played("rowenna-handicap") }
            hero("arien", Team.BLUE, Hex(8, -6))
            minion(Team.BLUE, MinionType.MELEE, Hex(1, -2))
            minion(Team.RED, MinionType.MELEE, Hex(-1, -5))
            phase = PositionPhase.Resolving(next = "rowenna")
        }
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
    }

    @Test
    fun `Earn the Spurs - both you and the target may retrieve before the attack`() {
        val d = pos {
            clearMinions()
            hero("rowenna", Team.RED, Hex(0, -2)) {
                played("rowenna-handicap")
                discard("rowenna-blue-1")
            }
            hero("arien", Team.BLUE, Hex(1, -2)) { hand("arien-gold") }
            phase = PositionPhase.Resolving(next = "rowenna")
        }
        d.action("rowenna", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_CARD, "rowenna")
        d.decline()
        d.expect(DecisionKind.CHOOSE_CARD, "arien")
        d.decline()
        d.defend(null)
        assertThat(d.at("arien")).isNull()
    }

    // ---- Throw the Gauntlet (silver) ----------------------------------------------------------

    @Test
    fun `Throw the Gauntlet - place yourself next to an enemy hero in range, if it moves 1 space gain 2 coins`() {
        val d = pos {
            clearMinions()
            hero("rowenna", Team.RED, Hex(0, -5)) { played("rowenna-silver") } // Range 4
            hero("arien", Team.BLUE, Hex(3, -5))
            phase = PositionPhase.Resolving(next = "rowenna")
        }
        d.action("rowenna", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_SPACE, "rowenna")
        // Spaces next to Arien and in range; (2,-4) is a rock.
        assertThat(d.spaces()).containsExactly(Hex(4, -5), Hex(4, -6), Hex(3, -6), Hex(2, -5), Hex(3, -4))
        d.space(2, -5)
        assertThat(d.at("rowenna")).isEqualTo(Hex(2, -5))
        // Arien decides whether to move.
        d.expect(DecisionKind.CHOOSE_SPACE, "arien")
        for (h in d.spaces()) assertThat(HexGrid.distance(h, Hex(3, -5))).isEqualTo(1)
        d.space(4, -5)
        assertThat(d.hero("rowenna").coins).isEqualTo(2)
    }

    @Test
    fun `Throw the Gauntlet - if the hero stays, no coins`() {
        val d = pos {
            clearMinions()
            hero("rowenna", Team.RED, Hex(0, -5)) { played("rowenna-silver") }
            hero("arien", Team.BLUE, Hex(3, -5))
            hero("brogan", Team.BLUE, Hex(8, -6)) // 8 away: out of range
            phase = PositionPhase.Resolving(next = "rowenna")
        }
        d.action("rowenna", ActionType.PRIMARY)
        d.space(2, -5)
        d.decline()
        assertThat(d.at("arien")).isEqualTo(Hex(3, -5))
        assertThat(d.hero("rowenna").coins).isEqualTo(0)
    }

    @Test
    fun `Throw the Gauntlet - the hero moves itself, so Bulwark does not stop it (rulings 2)`() {
        val d = pos {
            clearMinions()
            hero("rowenna", Team.RED, Hex(0, -5)) { played("rowenna-silver") }
            hero("brogan", Team.BLUE, Hex(3, -5)) { discard("brogan-silver") }
            // Bulwark: "You and friendly units in radius cannot be moved, pushed, swapped or placed by enemy heroes."
            effect("brogan", "brogan-silver", Timing.THIS_TURN, ActionKind.SKILL)
            phase = PositionPhase.Resolving(next = "rowenna")
        }
        d.action("rowenna", ActionType.PRIMARY)
        d.space(2, -5)
        d.expect(DecisionKind.CHOOSE_SPACE, "brogan")
        d.space(4, -5)
        assertThat(d.hero("rowenna").coins).isEqualTo(2)
    }

    // ---- Token of Gratitude / Fair Share / Paragon of Grace (red) -----------------------------

    @Test
    fun `Token of Gratitude - After the attack, a friendly hero in radius gains 1 coin`() {
        lateinit var m: String
        val d = pos {
            clearMinions()
            hero("rowenna", Team.RED, Hex(0, -2)) { played("rowenna-red-1") } // radius 1
            hero("wasp", Team.RED, Hex(-1, -2))
            hero("arien", Team.BLUE, Hex(8, -6))
            m = minion(Team.BLUE, MinionType.MELEE, Hex(1, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -5))
            minion(Team.RED, MinionType.MELEE, Hex(-1, -5))
            phase = PositionPhase.Resolving(next = "rowenna")
        }
        d.action("rowenna", ActionType.PRIMARY)
        assertThat(d.s.state.minion(m)).isNull()
        assertThat(d.hero("rowenna").coins).isEqualTo(2)
        assertThat(d.hero("wasp").coins).isEqualTo(1)
    }

    @Test
    fun `Fair Share - FAQ - never Rowenna herself, and a friend out of radius gets nothing`() {
        val d = pos {
            clearMinions()
            hero("rowenna", Team.RED, Hex(0, -2)) { played("rowenna-red-2a") } // radius 3
            hero("wasp", Team.RED, Hex(4, -2)) // 4 away
            hero("arien", Team.BLUE, Hex(8, -6))
            minion(Team.BLUE, MinionType.MELEE, Hex(1, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -5))
            minion(Team.RED, MinionType.MELEE, Hex(-1, -5))
            phase = PositionPhase.Resolving(next = "rowenna")
        }
        d.action("rowenna", ActionType.PRIMARY)
        assertThat(d.hero("rowenna").coins).isEqualTo(2) // the minion only
        assertThat(d.hero("wasp").coins).isEqualTo(0)
    }

    @Test
    fun `Paragon of Grace - may repeat once on a different target`() {
        lateinit var a: String
        lateinit var b: String
        val d = pos {
            clearMinions()
            hero("rowenna", Team.RED, Hex(0, -2)) { played("rowenna-red-3a") } // radius 3
            hero("wasp", Team.RED, Hex(-1, -2))
            hero("xargatha", Team.RED, Hex(0, -5))
            hero("arien", Team.BLUE, Hex(8, -6))
            a = minion(Team.BLUE, MinionType.MELEE, Hex(1, -2))
            b = minion(Team.BLUE, MinionType.MELEE, Hex(0, -1))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -5))
            minion(Team.RED, MinionType.MELEE, Hex(-1, -5))
            phase = PositionPhase.Resolving(next = "rowenna")
        }
        d.action("rowenna", ActionType.PRIMARY)
        d.unit(a)
        d.expect(DecisionKind.CHOOSE_UNIT, "rowenna")
        assertThat(d.units()).containsExactly("wasp", "xargatha")
        d.unit("wasp")
        d.expect(DecisionKind.CONFIRM, "rowenna")
        d.yes()
        // The repeat: the other minion; Wasp was targeted already (RULINGS 37), so Xargatha gains the coin.
        assertThat(d.s.state.minion(b)).isNull()
        assertThat(d.hero("wasp").coins).isEqualTo(1)
        assertThat(d.hero("xargatha").coins).isEqualTo(1)
        assertThat(d.hero("rowenna").coins).isEqualTo(4)
    }

    // ---- Feat of Bravery / Paragon of Valor (red alternates) ----------------------------------

    @Test
    fun `Feat of Bravery - After the attack, a friendly hero in radius may retrieve a discarded card`() {
        val d = pos {
            clearMinions()
            hero("rowenna", Team.RED, Hex(0, -2)) { played("rowenna-red-2b") } // radius 4
            hero("wasp", Team.RED, Hex(3, -2)) { hand("wasp-gold") }
            hero("arien", Team.BLUE, Hex(1, -2)) { hand("arien-blue-1") }
            phase = PositionPhase.Resolving(next = "rowenna")
        }
        d.action("rowenna", ActionType.PRIMARY)
        d.defend("arien-blue-1") // Defense 5 against Attack 7: defeated
        d.expect(DecisionKind.CHOOSE_CARD, "wasp")
        d.card("wasp-red-1")
        assertThat(d.zoneOf("wasp", "wasp-red-1")).isEqualTo(Zone.HAND)
    }

    @Test
    fun `Paragon of Valor - if they do, you may repeat once on a different target`() {
        lateinit var b: String
        val d = pos {
            clearMinions()
            hero("rowenna", Team.RED, Hex(0, -2)) { played("rowenna-red-3b") }
            hero("wasp", Team.RED, Hex(3, -2)) { hand("wasp-gold") }
            hero("arien", Team.BLUE, Hex(8, -6))
            minion(Team.BLUE, MinionType.MELEE, Hex(1, -2))
            b = minion(Team.BLUE, MinionType.MELEE, Hex(0, -1))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -5))
            minion(Team.RED, MinionType.MELEE, Hex(-1, -5))
            phase = PositionPhase.Resolving(next = "rowenna")
        }
        d.action("rowenna", ActionType.PRIMARY)
        d.unit(d.units().first { it != b })
        d.card("wasp-red-1")
        d.expect(DecisionKind.CONFIRM, "rowenna")
        d.yes()
        assertThat(d.s.state.minion(b)).isNull()
    }

    @Test
    fun `Paragon of Valor - no retrieve, no repeat`() {
        val d = pos {
            clearMinions()
            hero("rowenna", Team.RED, Hex(0, -2)) { played("rowenna-red-3b") }
            hero("wasp", Team.RED, Hex(3, -2)) { hand("wasp-gold") }
            hero("arien", Team.BLUE, Hex(8, -6)) { played("arien-blue-1") }
            minion(Team.BLUE, MinionType.MELEE, Hex(1, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(0, -1))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -5))
            minion(Team.RED, MinionType.MELEE, Hex(-1, -5))
            phase = PositionPhase.Resolving(next = "rowenna")
        }
        d.action("rowenna", ActionType.PRIMARY)
        d.unit(d.units().first())
        d.decline() // Wasp does not retrieve
        d.expect(DecisionKind.CHOOSE_ACTION, "arien")
    }

    // ---- Close Quarters / Melee / Grand Melee (green) ------------------------------------------

    @Test
    fun `Close Quarters - after movement, next to an enemy hero, place a friendly minion next to that hero`() {
        lateinit var friend: String
        val d = pos {
            clearMinions()
            hero("rowenna", Team.RED, Hex(0, -2)) { played("rowenna-green-1") } // Movement 2, radius 2
            hero("arien", Team.BLUE, Hex(2, -2))
            friend = minion(Team.RED, MinionType.MELEE, Hex(-1, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(1, 0))
            phase = PositionPhase.Resolving(next = "rowenna")
        }
        d.action("rowenna", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_SPACE, "rowenna") // the movement first
        d.space(1, -2)
        d.expect(DecisionKind.CHOOSE_OPTION, "rowenna")
        d.option(0)
        d.expect(DecisionKind.CHOOSE_SPACE, "rowenna")
        assertThat(d.spaces()).containsExactly(Hex(3, -2), Hex(3, -3), Hex(2, -3), Hex(1, -1), Hex(2, -1))
        d.space(3, -2)
        assertThat(d.at(friend)).isEqualTo(Hex(3, -2))
    }

    @Test
    fun `Close Quarters - place an enemy minion next to you, or choose nothing (you may)`() {
        lateinit var enemy: String
        val d = pos {
            clearMinions()
            hero("rowenna", Team.RED, Hex(0, -2)) { played("rowenna-green-1") }
            hero("arien", Team.BLUE, Hex(2, -2))
            minion(Team.RED, MinionType.MELEE, Hex(-1, -5))
            enemy = minion(Team.BLUE, MinionType.MELEE, Hex(1, 0))
            phase = PositionPhase.Resolving(next = "rowenna")
        }
        d.action("rowenna", ActionType.PRIMARY)
        d.space(1, -2)
        // Only the second bullet can be done (no friendly minion in radius).
        d.expect(DecisionKind.CHOOSE_OPTION, "rowenna")
        assertThat(d.d.options.count { it is dev.quinton.goa.core.game.Choice.Option }).isEqualTo(1)
        d.option(0)
        d.space(0, -1)
        assertThat(d.at(enemy)).isEqualTo(Hex(0, -1))
    }

    @Test
    fun `Close Quarters - not adjacent to an enemy hero after movement, nothing more`() {
        val d = pos {
            clearMinions()
            hero("rowenna", Team.RED, Hex(0, -2)) { played("rowenna-green-1") }
            hero("arien", Team.BLUE, Hex(4, -2)) { played("arien-blue-1") }
            minion(Team.RED, MinionType.MELEE, Hex(-1, -2))
            phase = PositionPhase.Resolving(next = "rowenna")
        }
        d.action("rowenna", ActionType.PRIMARY)
        d.decline() // don't move
        d.expect(DecisionKind.CHOOSE_ACTION, "arien")
    }

    @Test
    fun `Melee - radius 3, the enemy minion from 3 spaces away`() {
        lateinit var enemy: String
        val d = pos {
            clearMinions()
            hero("rowenna", Team.RED, Hex(0, -2)) { played("rowenna-green-2a") }
            hero("arien", Team.BLUE, Hex(1, -2))
            minion(Team.RED, MinionType.MELEE, Hex(-1, -5))
            enemy = minion(Team.BLUE, MinionType.MELEE, Hex(3, -2))
            phase = PositionPhase.Resolving(next = "rowenna")
        }
        d.action("rowenna", ActionType.PRIMARY)
        d.decline() // stay: already next to Arien
        d.option(0)
        d.space(-1, -1)
        assertThat(d.at(enemy)).isEqualTo(Hex(-1, -1))
    }

    @Test
    fun `Grand Melee - Choose up to two times, both bullets`() {
        lateinit var friend: String
        lateinit var enemy: String
        val d = pos {
            clearMinions()
            hero("rowenna", Team.RED, Hex(0, -2)) { played("rowenna-green-3a") } // radius 3
            hero("arien", Team.BLUE, Hex(1, -2))
            friend = minion(Team.RED, MinionType.MELEE, Hex(-1, -3))
            enemy = minion(Team.BLUE, MinionType.MELEE, Hex(3, -2))
            phase = PositionPhase.Resolving(next = "rowenna")
        }
        d.action("rowenna", ActionType.PRIMARY)
        d.decline() // no movement
        d.option(0) // friendly minion next to Arien
        d.space(2, -2)
        d.expect(DecisionKind.CHOOSE_OPTION, "rowenna")
        d.option(1) // enemy minion next to Rowenna
        d.space(-1, -2)
        assertThat(d.at(friend)).isEqualTo(Hex(2, -2))
        assertThat(d.at(enemy)).isEqualTo(Hex(-1, -2))
    }

    // ---- Stand Guard / Protect the Weak / Defend the Innocent (blue) --------------------------

    @Test
    fun `Stand Guard - swap with a friendly unit in range next to an enemy hero, or with a card in the discard`() {
        lateinit var guarded: String
        val d = pos {
            clearMinions()
            hero("rowenna", Team.RED, Hex(0, -5)) { played("rowenna-blue-1") } // Range 3
            hero("wasp", Team.RED, Hex(0, -2)) { hand("wasp-gold") } // 3 away, has discarded cards
            hero("xargatha", Team.RED, Hex(2, -5)) // 2 away, nothing discarded, no enemy next to her
            hero("arien", Team.BLUE, Hex(8, -6))
            guarded = minion(Team.RED, MinionType.MELEE, Hex(3, -6)) // next to Brogan below
            hero("brogan", Team.BLUE, Hex(4, -6))
            minion(Team.RED, MinionType.MELEE, Hex(-1, -3)) // 2 away, nothing near it
            phase = PositionPhase.Resolving(next = "rowenna")
        }
        d.action("rowenna", ActionType.PRIMARY)
        assertThat(d.units()).containsExactly("wasp", guarded)
        d.unit(guarded)
        assertThat(d.at("rowenna")).isEqualTo(Hex(3, -6))
        assertThat(d.at(guarded)).isEqualTo(Hex(0, -5))
    }

    @Test
    fun `Protect the Weak - Range 4`() {
        val d = pos {
            clearMinions()
            hero("rowenna", Team.RED, Hex(0, -5)) { played("rowenna-blue-2a") }
            hero("wasp", Team.RED, Hex(4, -5)) { hand("wasp-gold") } // 4 away
            hero("arien", Team.BLUE, Hex(8, -6))
            phase = PositionPhase.Resolving(next = "rowenna")
        }
        d.action("rowenna", ActionType.PRIMARY)
        assertThat(d.at("rowenna")).isEqualTo(Hex(4, -5))
    }

    @Test
    fun `Defend the Innocent - then you may retrieve a discarded card`() {
        val d = pos {
            clearMinions()
            hero("rowenna", Team.RED, Hex(0, -5)) {
                played("rowenna-blue-3a")
                discard("rowenna-red-1")
            }
            hero("wasp", Team.RED, Hex(4, -5)) { hand("wasp-gold") }
            hero("arien", Team.BLUE, Hex(8, -6))
            phase = PositionPhase.Resolving(next = "rowenna")
        }
        d.action("rowenna", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_CARD, "rowenna")
        d.card("rowenna-red-1")
        assertThat(d.zoneOf("rowenna", "rowenna-red-1")).isEqualTo(Zone.HAND)
    }

    // ---- Opening Shots / Opening Volley (green alternates) ------------------------------------

    @Test
    fun `Opening Shots - both you and the enemy hero have no cards in the discard, so it discards, if able`() {
        val d = pos {
            clearMinions()
            hero("rowenna", Team.RED, Hex(0, -2)) { played("rowenna-green-2b") } // radius 3
            hero("arien", Team.BLUE, Hex(3, -2)) // full hand, empty discard
            hero("brogan", Team.BLUE, Hex(0, -5)) { hand("brogan-gold") } // has discarded cards
            phase = PositionPhase.Resolving(next = "rowenna")
        }
        d.action("rowenna", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_CARD, "arien")
        d.card("arien-silver")
        assertThat(d.zoneOf("arien", "arien-silver")).isEqualTo(Zone.DISCARD)
        assertThat(d.zoneOf("brogan", "brogan-gold")).isEqualTo(Zone.HAND)
    }

    @Test
    fun `Opening Shots - with a card in your own discard, no Primary action`() {
        val d = pos {
            clearMinions()
            hero("rowenna", Team.RED, Hex(0, -2)) {
                played("rowenna-green-2b")
                discard("rowenna-red-1")
            }
            hero("arien", Team.BLUE, Hex(3, -2))
            phase = PositionPhase.Resolving(next = "rowenna")
        }
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
    }

    @Test
    fun `Opening Volley - May repeat once, on a hero whose discard is still empty`() {
        val d = pos {
            clearMinions()
            hero("rowenna", Team.RED, Hex(0, -2)) { played("rowenna-green-3b") }
            hero("arien", Team.BLUE, Hex(3, -2))
            hero("brogan", Team.BLUE, Hex(0, -5))
            phase = PositionPhase.Resolving(next = "rowenna")
        }
        d.action("rowenna", ActionType.PRIMARY)
        d.unit("arien")
        d.card("arien-silver")
        d.expect(DecisionKind.CONFIRM, "rowenna")
        d.yes()
        // Arien has a discarded card now: only Brogan qualifies.
        d.expect(DecisionKind.CHOOSE_CARD, "brogan")
        d.card("brogan-silver")
        assertThat(d.zoneOf("brogan", "brogan-silver")).isEqualTo(Zone.DISCARD)
    }

    // ---- Accept Surrender / Glorious Triumph (blue alternates) --------------------------------

    @Test
    fun `Accept Surrender - Defeat an enemy hero adjacent to you with no cards in hand`() {
        val d = pos {
            clearMinions()
            hero("rowenna", Team.RED, Hex(0, -2)) { played("rowenna-blue-2b") }
            hero("wasp", Team.RED, Hex(4, -5))
            hero("arien", Team.BLUE, Hex(1, -2)) {
                hand()
                level = 3
            }
            phase = PositionPhase.Resolving(next = "rowenna")
        }
        d.action("rowenna", ActionType.PRIMARY)
        assertThat(d.at("arien")).isNull()
        assertThat(d.hero("rowenna").coins).isEqualTo(3) // Arien's level
        assertThat(d.hero("wasp").coins).isEqualTo(1) // assist: Arien's lowest Tier
    }

    @Test
    fun `Accept Surrender - an enemy hero with a card in hand cannot be chosen`() {
        val d = pos {
            clearMinions()
            hero("rowenna", Team.RED, Hex(0, -2)) { played("rowenna-blue-2b") }
            hero("arien", Team.BLUE, Hex(1, -2)) { hand("arien-gold") }
            hero("brogan", Team.BLUE, Hex(3, -2)) { hand() } // empty hand, not adjacent
            phase = PositionPhase.Resolving(next = "rowenna")
        }
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
    }

    @Test
    fun `Glorious Triumph - your friendly heroes gain triple assist coins`() {
        val d = pos {
            clearMinions()
            hero("rowenna", Team.RED, Hex(0, -2)) { played("rowenna-blue-3b") }
            hero("wasp", Team.RED, Hex(4, -5))
            hero("xargatha", Team.RED, null) // off the board: still a friendly hero
            hero("arien", Team.BLUE, Hex(1, -2)) { hand() }
            phase = PositionPhase.Resolving(next = "rowenna")
        }
        d.action("rowenna", ActionType.PRIMARY)
        assertThat(d.at("arien")).isNull()
        assertThat(d.hero("rowenna").coins).isEqualTo(1)
        assertThat(d.hero("wasp").coins).isEqualTo(3)
        assertThat(d.hero("xargatha").coins).isEqualTo(3)
    }

    // ---- Fabled Lance (Ultimate) --------------------------------------------------------------

    @Test
    fun `Fabled Lance - attacks are Ranged and target a unit in range, printed Range 2`() {
        val d = pos {
            clearMinions()
            hero("rowenna", Team.RED, Hex(0, -2)) {
                played("rowenna-gold")
                ultimate()
            }
            hero("wasp", Team.BLUE, Hex(2, -2)) { hand("wasp-green-1") } // 2 away; Block a ranged attack
            phase = PositionPhase.Resolving(next = "rowenna")
        }
        d.action("rowenna", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_CARD, "wasp") // Code of Chivalry: the target may retrieve
        d.decline()
        d.defend("wasp-green-1")
        assertThat(d.at("wasp")).isEqualTo(Hex(2, -2)) // blocked: the attack is ranged
    }

    @Test
    fun `Fabled Lance - a Range item adds to the printed Range 2 (p 16)`() {
        lateinit var m: String
        val d = pos {
            clearMinions()
            hero("rowenna", Team.RED, Hex(0, -2)) {
                played("rowenna-red-1")
                items("rowenna-red-3a") // +1 Range
                ultimate()
            }
            hero("arien", Team.BLUE, Hex(8, -6))
            m = minion(Team.BLUE, MinionType.MELEE, Hex(3, -2)) // 3 away
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -7))
            minion(Team.RED, MinionType.MELEE, Hex(-1, -5))
            phase = PositionPhase.Resolving(next = "rowenna")
        }
        d.action("rowenna", ActionType.PRIMARY)
        assertThat(d.s.state.minion(m)).isNull()
    }

    // ---- audit E regressions --------------------------------------------------------------------

    @Test
    fun `Throw the Gauntlet - spaces that count as obstacles for Rowenna are not offered (Takahide's blade)`() {
        val d = pos {
            clearMinions()
            hero("rowenna", Team.RED, Hex(0, -5)) { played("rowenna-silver") } // Range 4
            hero("takahide", Team.BLUE, Hex(3, -5)) { resolved(1, "takahide-red-2b") }
            turn = 2
            // Spinning Blade: "This turn: Empty spaces adjacent to you count as obstacles for enemy units."
            effect("takahide", "takahide-red-2b", Timing.THIS_TURN, ActionKind.ATTACK)
            phase = PositionPhase.Resolving(next = "rowenna")
        }
        // Every space next to Takahide is an obstacle for Rowenna: she cannot be placed there (p.18).
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
    }

    @Test
    fun `Close Quarters - a minion is only placed into a space that is empty for it (Takahide's blade)`() {
        lateinit var enemy: String
        val d = pos {
            clearMinions()
            hero("rowenna", Team.RED, Hex(1, -2)) { played("rowenna-green-1") } // Movement 2, radius 2
            hero("takahide", Team.BLUE, Hex(2, -2)) { resolved(1, "takahide-red-2b") }
            minion(Team.RED, MinionType.MELEE, Hex(0, -2))
            enemy = minion(Team.BLUE, MinionType.MELEE, Hex(1, 0))
            turn = 2
            effect("takahide", "takahide-red-2b", Timing.THIS_TURN, ActionKind.ATTACK)
            phase = PositionPhase.Resolving(next = "rowenna")
        }
        d.action("rowenna", ActionType.PRIMARY)
        d.decline() // stay next to Takahide
        // The spaces next to Takahide are obstacles for Rowenna's (red) minion: only the second bullet.
        d.expect(DecisionKind.CHOOSE_OPTION, "rowenna")
        assertThat(d.d.options.count { it is dev.quinton.goa.core.game.Choice.Option }).isEqualTo(1)
        d.option(0)
        d.space(0, -1)
        assertThat(d.at(enemy)).isEqualTo(Hex(0, -1))
    }
}
