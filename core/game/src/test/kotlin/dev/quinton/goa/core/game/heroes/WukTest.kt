package dev.quinton.goa.core.game.heroes

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.Driver
import dev.quinton.goa.core.game.engine.ActionKind
import dev.quinton.goa.core.game.engine.Timing
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.game.heroes.wuk.HeroGames
import dev.quinton.goa.core.game.pos
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

/**
 * Wuk the Grovekeeper: every card, clause by clause (text from `catalog/heroes/wuk.json`), with the
 * FAQ and the readings in `core/game/rulings/wuk.md`. Quiet strip of Forgotten Island: row r = −2,
 * q = −1..4 (Battle Zone `lane-1-2`), terrain at (1,−3) and (5,−2).
 */
class WukTest {
    private fun Driver.trees(): List<Hex> = s.state.pieces.filter { it.kind == WukHero.TREE }.mapNotNull { it.at }

    private fun Driver.options(): List<Int> = d.options.filterIsInstance<Choice.Option>().map { it.index }

    // ---- Tree Slam (gold) / Makeshift Weapon (Handicap) ---------------------------------------

    @Test
    fun `Tree Slam - Choose one, the first bullet targets only a minion adjacent to you`() {
        lateinit var m1: String
        val d = pos {
            clearMinions()
            hero("wuk", Team.RED, Hex(1, -2)) { played("wuk-gold") }
            hero("brogan", Team.BLUE, Hex(2, -2)) { hand("brogan-blue-1") } // an adjacent hero: not a target
            m1 = minion(Team.BLUE, MinionType.MELEE, Hex(0, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(0, -1))
            token(WukHero.TREE, Hex(1, -1), owner = "wuk")
            phase = PositionPhase.Resolving(next = "wuk")
        }
        d.action("wuk", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_OPTION, "wuk")
        assertThat(d.options()).containsExactly(0, 1)
        d.option(0)
        assertThat(d.units()).containsExactly(m1, d.s.state.minions[1].id)
        d.unit(m1)
        assertThat(d.s.state.minion(m1)).isNull()
        assertThat(d.hero("wuk").coins).isEqualTo(2)
        assertThat(d.trees()).containsExactly(Hex(1, -1)) // the tree stays: the other bullet was not chosen
    }

    @Test
    fun `Tree Slam - Remove a Tree token adjacent to you, then target a unit in range`() {
        val d = pos {
            clearMinions()
            hero("wuk", Team.RED, Hex(1, -2)) { played("wuk-gold") }
            minion(Team.BLUE, MinionType.MELEE, Hex(0, -2))
            hero("arien", Team.BLUE, Hex(3, -2)) { hand("arien-blue-1") } // 2 away: Range 2
            hero("brogan", Team.BLUE, Hex(4, -2)) { hand("brogan-blue-1") } // 3 away: out of range
            token(WukHero.TREE, Hex(1, -1), owner = "wuk")
            phase = PositionPhase.Resolving(next = "wuk")
        }
        d.action("wuk", ActionType.PRIMARY)
        d.option(1)
        // The only adjacent tree is removed (one option: no question), then a unit in range is chosen.
        assertThat(d.trees()).isEmpty()
        assertThat(d.units()).containsExactly("arien", d.s.state.minions.single().id)
        d.unit("arien")
        d.defend("arien-blue-1") // Defense 5 against Attack 4
        assertThat(d.at("arien")).isEqualTo(Hex(3, -2))
    }

    @Test
    fun `Tree Slam - no adjacent minion and no adjacent tree, so no primary action`() {
        val d = pos {
            clearMinions()
            hero("wuk", Team.RED, Hex(1, -2)) { played("wuk-gold") }
            hero("brogan", Team.BLUE, Hex(2, -2))
            token(WukHero.TREE, Hex(3, -2), owner = "wuk") // 2 away: not adjacent
            phase = PositionPhase.Resolving(next = "wuk")
        }
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
    }

    @Test
    fun `Makeshift Weapon - first bullet targets a hero adjacent, second a hero in range, never a minion`() {
        val d = pos {
            clearMinions()
            hero("wuk", Team.RED, Hex(1, -2)) { played("wuk-handicap") }
            hero("brogan", Team.BLUE, Hex(2, -2)) { hand("brogan-blue-1") }
            hero("arien", Team.BLUE, Hex(-1, -2)) { hand("arien-blue-1") } // 2 away
            minion(Team.BLUE, MinionType.MELEE, Hex(0, -2)) // adjacent minion: never a target
            token(WukHero.TREE, Hex(1, -1), owner = "wuk")
            phase = PositionPhase.Resolving(next = "wuk")
        }
        d.action("wuk", ActionType.PRIMARY)
        d.option(1)
        assertThat(d.units()).containsExactly("brogan", "arien")
        d.unit("arien")
        d.defend("arien-blue-1")
    }

    @Test
    fun `Makeshift Weapon - with no tree adjacent only the hero bullet, no question`() {
        val d = pos {
            clearMinions()
            hero("wuk", Team.RED, Hex(1, -2)) { played("wuk-handicap") }
            hero("brogan", Team.BLUE, Hex(2, -2)) { hand("brogan-blue-1") }
            minion(Team.BLUE, MinionType.MELEE, Hex(0, -2))
            phase = PositionPhase.Resolving(next = "wuk")
        }
        d.action("wuk", ActionType.PRIMARY)
        d.expect(DecisionKind.DEFEND, "brogan")
    }

    // ---- Mystic Saplings (silver) --------------------------------------------------------------

    @Test
    fun `Mystic Saplings - Place up to 3 Tree tokens in radius, into empty spaces`() {
        val d = pos {
            clearMinions()
            hero("wuk", Team.RED, Hex(1, -2)) { played("wuk-silver") } // Radius 3
            hero("brogan", Team.BLUE, Hex(2, -2))
            phase = PositionPhase.Resolving(next = "wuk")
        }
        d.action("wuk", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_SPACE, "wuk")
        assertThat(d.spaces()).doesNotContain(Hex(2, -2)) // occupied
        assertThat(d.spaces()).doesNotContain(Hex(1, -3)) // terrain
        assertThat(d.spaces().all { HexGrid.distance(it, Hex(1, -2)) in 1..3 }).isTrue()
        assertThat(d.spaces()).contains(Hex(4, -2))
        d.space(4, -2)
        d.space(3, -2)
        d.space(0, -2)
        // Three placed: no fourth question; the trees are Wuk's.
        assertThat(d.trees()).containsExactly(Hex(4, -2), Hex(3, -2), Hex(0, -2))
        assertThat(d.s.state.pieces.all { it.owner == "wuk" }).isTrue()
    }

    @Test
    fun `Mystic Saplings - up to, so Wuk may stop, and Tree tokens are not removed at the end of round`() {
        val d = pos {
            clearMinions()
            turn = 4
            hero("wuk", Team.RED, Hex(1, -2)) { played("wuk-silver") }
            hero("brogan", Team.BLUE, Hex(4, -5))
            phase = PositionPhase.Resolving(next = "wuk")
        }
        d.action("wuk", ActionType.PRIMARY)
        d.space(3, -2)
        d.decline()
        // The round ends: tokens are removed (p.8) except Tree tokens.
        d.expect(DecisionKind.PLAY_CARD)
        assertThat(d.s.state.round).isEqualTo(2)
        assertThat(d.trees()).containsExactly(Hex(3, -2))
    }

    @Test
    fun `Mystic Saplings - p18 three Tree tokens, with two in play one is placed and then one is taken back`() {
        val d = pos {
            clearMinions()
            hero("wuk", Team.RED, Hex(1, -2)) { played("wuk-silver") }
            hero("brogan", Team.BLUE, Hex(4, -5))
            token(WukHero.TREE, Hex(-1, -5), owner = "wuk")
            token(WukHero.TREE, Hex(4, -2), owner = "wuk")
            phase = PositionPhase.Resolving(next = "wuk")
        }
        val far = d.s.state.pieces.single { it.at == Hex(-1, -5) }.id
        d.action("wuk", ActionType.PRIMARY)
        // One left in the supply: placed with no question about the board.
        d.expect(DecisionKind.CHOOSE_SPACE, "wuk")
        d.space(3, -2)
        // Supply empty: "first remove up to that number of tokens of the same type from the board" (or stop).
        d.expect(DecisionKind.CHOOSE_TOKEN, "wuk")
        assertThat(d.d.options.filterIsInstance<Choice.Token>()).hasSize(3)
        d.token(far)
        d.expect(DecisionKind.CHOOSE_SPACE, "wuk")
        assertThat(d.d.options.none { it is Choice.Decline }).isTrue()
        d.space(0, -2)
        d.expect(DecisionKind.CHOOSE_TOKEN, "wuk")
        d.decline()
        assertThat(d.trees()).containsExactly(Hex(4, -2), Hex(3, -2), Hex(0, -2))
    }

    @Test
    fun `Mystic Saplings - a Radius item widens the radius`() {
        val d = pos {
            clearMinions()
            hero("wuk", Team.RED, Hex(1, -2)) {
                played("wuk-silver")
                items("wuk-red-3b") // item: +1 Radius
            }
            hero("brogan", Team.BLUE, Hex(4, -5))
            phase = PositionPhase.Resolving(next = "wuk")
        }
        d.action("wuk", ActionType.PRIMARY)
        assertThat(d.spaces()).contains(Hex(-3, -2)) // 4 away
    }

    // ---- Nature's Protector / Guardian / Champion (red) ----------------------------------------

    @Test
    fun `Nature's Protector - Target a unit in range adjacent to a Tree token, not one away from trees`() {
        lateinit var near: String
        lateinit var far: String
        val d = pos {
            clearMinions()
            hero("wuk", Team.RED, Hex(1, -2)) { played("wuk-red-1") } // Range 2
            hero("brogan", Team.BLUE, Hex(2, -2)) { hand("brogan-blue-1") }
            near = minion(Team.BLUE, MinionType.MELEE, Hex(3, -3)) // 2 away, next to the tree
            far = minion(Team.BLUE, MinionType.MELEE, Hex(-1, -2)) // 2 away, no tree next to it
            token(WukHero.TREE, Hex(4, -3), owner = "wuk")
            minion(Team.RED, MinionType.MELEE, Hex(-1, -5))
            phase = PositionPhase.Resolving(next = "wuk")
        }
        d.action("wuk", ActionType.PRIMARY)
        assertThat(d.options()).containsExactly(0, 1)
        d.option(1)
        // Only one target qualifies: no question.
        assertThat(d.s.state.minion(near)).isNull()
        assertThat(d.s.state.minion(far)).isNotNull()
    }

    @Test
    fun `Nature's Protector - Target a hero adjacent to you`() {
        val d = pos {
            clearMinions()
            hero("wuk", Team.RED, Hex(1, -2)) { played("wuk-red-1") }
            hero("brogan", Team.BLUE, Hex(2, -2)) { hand("brogan-blue-1") }
            minion(Team.BLUE, MinionType.MELEE, Hex(0, -2)) // adjacent minion, no tree: not a target
            phase = PositionPhase.Resolving(next = "wuk")
        }
        d.action("wuk", ActionType.PRIMARY)
        d.expect(DecisionKind.DEFEND, "brogan")
        d.defend("brogan-blue-1") // 5 against 5: defended
        assertThat(d.at("brogan")).isEqualTo(Hex(2, -2))
    }

    @Test
    fun `Nature's Protector - an immune heavy minion next to a tree is not offered`() {
        val d = pos {
            clearMinions()
            hero("wuk", Team.RED, Hex(1, -2)) { played("wuk-red-1") }
            hero("brogan", Team.BLUE, Hex(2, -2)) { hand("brogan-blue-1") }
            minion(Team.BLUE, MinionType.HEAVY, Hex(3, -3)) // immune: a Blue melee is in the Battle Zone
            minion(Team.BLUE, MinionType.MELEE, Hex(-1, -5))
            token(WukHero.TREE, Hex(4, -3), owner = "wuk")
            phase = PositionPhase.Resolving(next = "wuk")
        }
        d.action("wuk", ActionType.PRIMARY)
        // The second bullet has no target, so the first is taken without a question.
        d.expect(DecisionKind.DEFEND, "brogan")
    }

    @Test
    fun `Nature's Protector - a hero immune to Wuk's attacks next to a tree is not offered`() {
        val d = pos {
            clearMinions()
            hero("wuk", Team.RED, Hex(1, -2)) { played("wuk-red-1") }
            hero("brogan", Team.BLUE, Hex(2, -2)) { hand("brogan-blue-1") }
            // Master Duelist: immune to attack actions of enemy heroes other than Wasp this round.
            hero("arien", Team.BLUE, Hex(3, -3))
            effect("arien", "arien-blue-3a", Timing.THIS_ROUND, ActionKind.DEFENSE, data = mapOf("attacker" to "wasp"))
            token(WukHero.TREE, Hex(4, -3), owner = "wuk")
            phase = PositionPhase.Resolving(next = "wuk")
        }
        d.action("wuk", ActionType.PRIMARY)
        d.expect(DecisionKind.DEFEND, "brogan")
    }

    @Test
    fun `Nature's Guardian - Range 3`() {
        lateinit var m: String
        val d = pos {
            clearMinions()
            hero("wuk", Team.RED, Hex(1, -2)) { played("wuk-red-2a") } // Range 3
            hero("brogan", Team.BLUE, Hex(-4, -1))
            m = minion(Team.BLUE, MinionType.MELEE, Hex(4, -2)) // 3 away
            token(WukHero.TREE, Hex(4, -1), owner = "wuk")
            phase = PositionPhase.Resolving(next = "wuk")
        }
        d.action("wuk", ActionType.PRIMARY)
        assertThat(d.s.state.minion(m)).isNull()
    }

    @Test
    fun `Nature's Champion - Choose one, or both, on different targets`() {
        val d = pos {
            clearMinions()
            hero("wuk", Team.RED, Hex(1, -2)) { played("wuk-red-3a") } // Attack 6, Range 3
            hero("brogan", Team.BLUE, Hex(2, -2)) { hand("brogan-red-1") } // adjacent, and next to the tree
            hero("arien", Team.BLUE, Hex(3, -2)) { hand("arien-red-1") } // 2 away, next to the tree
            token(WukHero.TREE, Hex(3, -3), owner = "wuk")
            phase = PositionPhase.Resolving(next = "wuk")
        }
        d.action("wuk", ActionType.PRIMARY)
        d.option(0)
        d.defend("brogan-red-1") // Defense 7
        // The other bullet: Brogan was targeted already, so only Arien.
        d.expect(DecisionKind.CHOOSE_OPTION, "wuk")
        assertThat(d.options()).containsExactly(1)
        d.option(1)
        d.expect(DecisionKind.DEFEND, "arien")
        d.defend("arien-red-1")
    }

    @Test
    fun `Nature's Champion - the second bullet is not offered when only the first target qualifies`() {
        val d = pos {
            clearMinions()
            hero("wuk", Team.RED, Hex(1, -2)) { played("wuk-red-3a") }
            hero("brogan", Team.BLUE, Hex(2, -2)) { hand("brogan-red-1") }
            hero("arien", Team.BLUE, Hex(4, -5)) { played("arien-blue-1") } // far away, acts after Wuk
            token(WukHero.TREE, Hex(3, -3), owner = "wuk")
            phase = PositionPhase.Resolving(next = "wuk")
        }
        d.action("wuk", ActionType.PRIMARY)
        d.option(1) // Brogan is next to the tree too
        d.defend("brogan-red-1")
        // "on different targets": Brogan can't be attacked again; nobody else qualifies.
        d.expect(DecisionKind.CHOOSE_ACTION, "arien")
    }

    // ---- Toss Away / Mighty Throw / Monstrous Throw (green) ------------------------------------

    @Test
    fun `Toss Away - Place a token or an enemy unit adjacent to you into a space in range, never a friend`() {
        lateinit var rock: String
        val d = pos {
            clearMinions()
            hero("wuk", Team.RED, Hex(1, -2)) { played("wuk-green-1") } // Range 3
            hero("brogan", Team.BLUE, Hex(2, -2))
            hero("wasp", Team.RED, Hex(0, -2)) // friendly: not offered
            rock = token("token_rock", Hex(1, -1))
            phase = PositionPhase.Resolving(next = "wuk")
        }
        d.action("wuk", ActionType.PRIMARY)
        assertThat(d.units()).containsExactly("brogan", rock)
        d.unit("brogan")
        assertThat(d.spaces().all { HexGrid.distance(it, Hex(1, -2)) in 1..3 }).isTrue()
        assertThat(d.spaces()).doesNotContain(Hex(2, -2)) // its own space: placing needs a different space
        d.space(4, -2)
        assertThat(d.at("brogan")).isEqualTo(Hex(4, -2))
    }

    @Test
    fun `Toss Away - a space that counts as an obstacle for the unit is not offered (Takahide's blade)`() {
        val d = pos {
            clearMinions()
            hero("wuk", Team.RED, Hex(1, -2)) { played("wuk-green-1") } // Range 3
            hero("takahide", Team.RED, Hex(2, -3)) { resolved(1, "takahide-red-2b") }
            hero("brogan", Team.BLUE, Hex(0, -2))
            turn = 2
            // Spinning Blade: empty spaces adjacent to Takahide count as obstacles for enemy units.
            effect("takahide", "takahide-red-2b", Timing.THIS_TURN, ActionKind.ATTACK)
            phase = PositionPhase.Resolving(next = "wuk")
        }
        d.action("wuk", ActionType.PRIMARY) // Brogan, the only enemy unit adjacent, is chosen without asking
        assertThat(d.spaces()).containsNoneOf(Hex(2, -2), Hex(3, -3))
        d.space(4, -2)
        assertThat(d.at("brogan")).isEqualTo(Hex(4, -2))
    }

    @Test
    fun `Toss Away - an immune heavy minion adjacent cannot be placed`() {
        val d = pos {
            clearMinions()
            hero("wuk", Team.RED, Hex(1, -2)) { played("wuk-green-1") }
            hero("brogan", Team.BLUE, Hex(4, -5))
            minion(Team.BLUE, MinionType.HEAVY, Hex(2, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(-1, -5))
            phase = PositionPhase.Resolving(next = "wuk")
        }
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
    }

    @Test
    fun `Mighty Throw - Range 4`() {
        lateinit var rock: String
        val d = pos {
            clearMinions()
            hero("wuk", Team.RED, Hex(1, -2)) { played("wuk-green-2a") }
            hero("brogan", Team.BLUE, Hex(4, -5))
            rock = token("token_rock", Hex(1, -1))
            phase = PositionPhase.Resolving(next = "wuk")
        }
        d.action("wuk", ActionType.PRIMARY)
        assertThat(d.spaces()).contains(Hex(-3, -2)) // 4 away
        d.space(-3, -2)
        assertThat(d.at(rock)).isEqualTo(Hex(-3, -2))
    }

    @Test
    fun `Monstrous Throw - May repeat once`() {
        lateinit var rock: String
        val d = pos {
            clearMinions()
            hero("wuk", Team.RED, Hex(1, -2)) { played("wuk-green-3a") }
            hero("brogan", Team.BLUE, Hex(2, -2))
            rock = token("token_rock", Hex(1, -1))
            phase = PositionPhase.Resolving(next = "wuk")
        }
        d.action("wuk", ActionType.PRIMARY)
        d.token(rock)
        d.space(-2, -2)
        d.expect(DecisionKind.CONFIRM, "wuk")
        d.yes()
        // The rock is not adjacent any more: Brogan is the only one left.
        d.space(4, -2)
        assertThat(d.at("brogan")).isEqualTo(Hex(4, -2))
        assertThat(d.at(rock)).isEqualTo(Hex(-2, -2))
    }

    // ---- Gifts of Nature / Tree of Plenty / Abundance (blue) -----------------------------------

    @Test
    fun `Gifts of Nature - Remove a Tree token in radius, then you may retrieve a discarded card`() {
        val d = pos {
            clearMinions()
            hero("wuk", Team.RED, Hex(1, -2)) {
                played("wuk-blue-1") // Radius 3
                hand("wuk-gold") // the rest of the round's cards are discarded
            }
            hero("brogan", Team.BLUE, Hex(4, -5))
            token(WukHero.TREE, Hex(4, -2), owner = "wuk") // 3 away
            phase = PositionPhase.Resolving(next = "wuk")
        }
        d.action("wuk", ActionType.PRIMARY)
        assertThat(d.trees()).isEmpty()
        d.expect(DecisionKind.CHOOSE_CARD, "wuk")
        d.card("wuk-red-1")
        assertThat(d.zoneOf("wuk", "wuk-red-1")).isEqualTo(Zone.HAND)
    }

    @Test
    fun `Gifts of Nature - without a Tree token in radius there is no primary action`() {
        val d = pos {
            clearMinions()
            hero("wuk", Team.RED, Hex(1, -2)) {
                played("wuk-blue-1")
                hand("wuk-gold")
            }
            hero("brogan", Team.BLUE, Hex(4, -5))
            token(WukHero.TREE, Hex(-3, -2), owner = "wuk") // 4 away
            phase = PositionPhase.Resolving(next = "wuk")
        }
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
    }

    @Test
    fun `Tree of Plenty - Choose one, a friendly hero in radius may retrieve (that hero picks the card)`() {
        val d = pos {
            clearMinions()
            hero("wuk", Team.RED, Hex(1, -2)) {
                played("wuk-blue-2a") // Radius 4
                hand("wuk-gold")
            }
            hero("wasp", Team.RED, Hex(4, -2)) { hand("wasp-gold") }
            hero("brogan", Team.BLUE, Hex(4, -5)) { hand("brogan-gold") } // an enemy: never offered
            token(WukHero.TREE, Hex(0, -2), owner = "wuk")
            phase = PositionPhase.Resolving(next = "wuk")
        }
        d.action("wuk", ActionType.PRIMARY)
        assertThat(d.trees()).isEmpty()
        d.expect(DecisionKind.CHOOSE_OPTION, "wuk")
        d.option(1)
        // Wasp is the only friendly hero in radius: she chooses, and may decline.
        d.expect(DecisionKind.CHOOSE_CARD, "wasp")
        d.card("wasp-red-1")
        assertThat(d.zoneOf("wasp", "wasp-red-1")).isEqualTo(Zone.HAND)
        // Choose one: Wuk does not retrieve too.
        assertThat(d.hero("wuk").hand.map { it.id }).containsExactly("wuk-gold")
    }

    @Test
    fun `Tree of Plenty - Choose one, you may retrieve a discarded card`() {
        val d = pos {
            clearMinions()
            hero("wuk", Team.RED, Hex(1, -2)) {
                played("wuk-blue-2a")
                hand("wuk-gold")
            }
            hero("wasp", Team.RED, Hex(4, -2)) { hand("wasp-gold") }
            hero("brogan", Team.BLUE, Hex(4, -5))
            token(WukHero.TREE, Hex(0, -2), owner = "wuk")
            phase = PositionPhase.Resolving(next = "wuk")
        }
        d.action("wuk", ActionType.PRIMARY)
        d.option(0)
        d.expect(DecisionKind.CHOOSE_CARD, "wuk")
        d.card("wuk-silver")
        assertThat(d.zoneOf("wuk", "wuk-silver")).isEqualTo(Zone.HAND)
        assertThat(d.zoneOf("wasp", "wasp-red-1")).isEqualTo(Zone.DISCARD)
    }

    @Test
    fun `Abundance - Choose one or both`() {
        val d = pos {
            clearMinions()
            hero("wuk", Team.RED, Hex(1, -2)) {
                played("wuk-blue-3a")
                hand("wuk-gold")
            }
            hero("wasp", Team.RED, Hex(4, -2)) { hand("wasp-gold") }
            hero("brogan", Team.BLUE, Hex(4, -5))
            token(WukHero.TREE, Hex(0, -2), owner = "wuk")
            phase = PositionPhase.Resolving(next = "wuk")
        }
        d.action("wuk", ActionType.PRIMARY)
        d.option(0)
        d.card("wuk-silver")
        d.expect(DecisionKind.CHOOSE_OPTION, "wuk")
        assertThat(d.options()).containsExactly(1)
        d.option(1)
        d.expect(DecisionKind.CHOOSE_CARD, "wasp")
        d.card("wasp-red-1")
        assertThat(d.zoneOf("wuk", "wuk-silver")).isEqualTo(Zone.HAND)
        assertThat(d.zoneOf("wasp", "wasp-red-1")).isEqualTo(Zone.HAND)
    }

    // ---- Claim Dominance / Assert Dominance (blue alternates) ----------------------------------

    @Test
    fun `Claim Dominance - This round, up to 1 adjacent enemy minion does not count, regardless of immunity`() {
        lateinit var heavy: String
        lateinit var melee: String
        lateinit var farOne: String
        val d = pos {
            clearMinions()
            turn = 4
            hero("wuk", Team.RED, Hex(1, -2)) { resolved(1, "wuk-blue-2b") }
            hero("brogan", Team.BLUE, Hex(4, -5))
            heavy = minion(Team.BLUE, MinionType.HEAVY, Hex(0, -2)) // immune (a Blue melee is in the Zone)
            melee = minion(Team.BLUE, MinionType.MELEE, Hex(2, -2))
            farOne = minion(Team.BLUE, MinionType.MELEE, Hex(4, -2)) // not adjacent
            minion(Team.RED, MinionType.MELEE, Hex(-1, -5))
            minion(Team.RED, MinionType.MELEE, Hex(-1, -6))
            effect("wuk", "wuk-blue-2b", Timing.THIS_ROUND, ActionKind.SKILL, madeTurn = 1)
            phase = PositionPhase.EndOfRound
        }
        d.expect(DecisionKind.CHOOSE_UNIT, "wuk")
        assertThat(d.units()).containsExactly(heavy, melee)
        d.unit(heavy)
        // Blue counts 2 (the melee minions), Red 2: a tie, nobody removes a minion.
        assertThat(d.texts()).contains("Minion Battle: 2 against 2, no effect.")
        assertThat(d.s.state.minions).hasSize(5)
        assertThat(d.s.state.minion(farOne)).isNotNull()
    }

    @Test
    fun `Claim Dominance - Up to, so Wuk may let every minion count`() {
        val d = pos {
            clearMinions()
            turn = 4
            hero("wuk", Team.RED, Hex(1, -2)) { resolved(1, "wuk-blue-2b") }
            hero("brogan", Team.BLUE, Hex(4, -5))
            minion(Team.BLUE, MinionType.MELEE, Hex(2, -2))
            minion(Team.RED, MinionType.MELEE, Hex(-1, -5))
            effect("wuk", "wuk-blue-2b", Timing.THIS_ROUND, ActionKind.SKILL, madeTurn = 1)
            phase = PositionPhase.EndOfRound
        }
        d.decline()
        assertThat(d.texts()).contains("Minion Battle: 1 against 1, no effect.")
    }

    @Test
    fun `Claim Dominance - played this round, cancelled when Wuk is defeated`() {
        val d = pos {
            clearMinions()
            hero("wuk", Team.RED, Hex(1, -2)) { played("wuk-blue-2b") }
            hero("arien", Team.BLUE, Hex(2, -2)) { played("arien-red-1") } // 8: after Wuk (9)
            phase = PositionPhase.Resolving()
        }
        d.action("wuk", ActionType.PRIMARY)
        assertThat(d.s.state.effects.map { it.cardId }).contains("wuk-blue-2b")
        d.action("arien", ActionType.PRIMARY)
        d.expect(DecisionKind.DEFEND, "wuk")
        d.defend(null)
        assertThat(d.s.state.effects.map { it.cardId }).doesNotContain("wuk-blue-2b")
    }

    @Test
    fun `Assert Dominance - up to 2 adjacent enemy minions do not count`() {
        val d = pos {
            clearMinions()
            turn = 4
            hero("wuk", Team.RED, Hex(1, -2)) { resolved(1, "wuk-blue-3b") }
            hero("brogan", Team.BLUE, Hex(4, -5))
            val a = minion(Team.BLUE, MinionType.MELEE, Hex(0, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(2, -2))
            minion(Team.RED, MinionType.MELEE, Hex(-1, -5))
            effect("wuk", "wuk-blue-3b", Timing.THIS_ROUND, ActionKind.SKILL, madeTurn = 1)
            phase = PositionPhase.EndOfRound
            check(a.isNotEmpty())
        }
        val blue = d.s.state.minions.filter { it.team == Team.BLUE }.map { it.id }
        val waves = d.s.state.wavesLeft.toMap()
        d.unit(blue[0])
        d.expect(DecisionKind.CHOOSE_UNIT, "wuk")
        assertThat(d.units()).containsExactly(blue[1])
        d.unit(blue[1])
        // Blue counts 0 against 1: Blue would remove 1 but has none that counts; its minions stay, and as
        // it still has minions in the Battle Zone there is no push.
        assertThat(d.texts()).contains("Minion Battle: Red 1, Blue 0. Blue removes 1.")
        assertThat(d.s.state.minions).hasSize(3)
        assertThat(d.s.state.wavesLeft).isEqualTo(waves)
    }

    // ---- Trample / Angry Stampede (red alternates) ---------------------------------------------

    @Test
    fun `Trample - straight line ignoring obstacles, heroes moved through discard, defeat up to one minion`() {
        lateinit var m: String
        val d = pos {
            clearMinions()
            hero("wuk", Team.RED, Hex(1, -2)) { played("wuk-red-2b") } // Movement 4
            hero("brogan", Team.BLUE, Hex(2, -2)) { hand("brogan-gold", "brogan-blue-1") }
            m = minion(Team.BLUE, MinionType.MELEE, Hex(3, -2))
            phase = PositionPhase.Resolving(next = "wuk")
        }
        d.action("wuk", ActionType.PRIMARY)
        d.expect(DecisionKind.CONFIRM, "wuk")
        d.yes()
        // Straight lines only; through units and tokens, never ending on one.
        assertThat(d.spaces()).contains(Hex(4, -2))
        assertThat(d.spaces()).doesNotContain(Hex(2, -2))
        assertThat(d.spaces()).doesNotContain(Hex(3, -1)) // not in a straight line
        d.space(4, -2)
        d.expect(DecisionKind.CHOOSE_CARD, "brogan")
        d.card("brogan-gold")
        d.expect(DecisionKind.CHOOSE_UNIT, "wuk")
        d.unit(m)
        assertThat(d.at("wuk")).isEqualTo(Hex(4, -2))
        assertThat(d.zoneOf("brogan", "brogan-gold")).isEqualTo(Zone.DISCARD)
        assertThat(d.s.state.minion(m)).isNull()
        assertThat(d.hero("wuk").coins).isEqualTo(2)
    }

    @Test
    fun `Trample - a hero moved through with no card is defeated, a friend is not affected`() {
        val d = pos {
            clearMinions()
            hero("wuk", Team.RED, Hex(1, -2)) { played("wuk-red-2b") }
            hero("wasp", Team.RED, Hex(2, -2)) { hand("wasp-gold") } // friendly: nothing
            hero("brogan", Team.BLUE, Hex(3, -2)) { hand() } // no card: defeated
            phase = PositionPhase.Resolving(next = "wuk")
        }
        d.action("wuk", ActionType.PRIMARY)
        d.yes()
        d.space(4, -2)
        assertThat(d.at("brogan")).isNull()
        assertThat(d.zoneOf("wasp", "wasp-gold")).isEqualTo(Zone.HAND)
        assertThat(d.hero("wuk").coins).isEqualTo(1)
    }

    @Test
    fun `Trample - Razzle FAQ - each of her figures moved through is an enemy hero, she discards twice`() {
        val d = pos {
            clearMinions()
            hero("wuk", Team.RED, Hex(1, -2)) { played("wuk-red-2b") }
            hero("razzle", Team.BLUE, Hex(2, -2)) { hand("razzle-gold", "razzle-silver", "razzle-red-1") }
            razzleDouble("rzA", Hex(3, -2), Team.BLUE)
            phase = PositionPhase.Resolving(next = "wuk")
        }
        d.action("wuk", ActionType.PRIMARY)
        d.yes()
        d.space(4, -2)
        d.expect(DecisionKind.CHOOSE_CARD, "razzle")
        d.card("razzle-gold")
        d.expect(DecisionKind.CHOOSE_CARD, "razzle")
        d.card("razzle-silver")
        assertThat(d.zoneOf("razzle", "razzle-gold")).isEqualTo(Zone.DISCARD)
        assertThat(d.zoneOf("razzle", "razzle-silver")).isEqualTo(Zone.DISCARD)
        assertThat(d.zoneOf("razzle", "razzle-red-1")).isEqualTo(Zone.HAND)
    }

    @Test
    fun `Trample - moving normally applies no text and may not pass obstacles`() {
        val d = pos {
            clearMinions()
            hero("wuk", Team.RED, Hex(1, -2)) { played("wuk-red-2b") }
            hero("brogan", Team.BLUE, Hex(2, -2)) { hand("brogan-gold") }
            phase = PositionPhase.Resolving(next = "wuk")
        }
        d.action("wuk", ActionType.PRIMARY)
        d.decline()
        assertThat(d.spaces()).contains(Hex(2, -1)) // not a straight line from (1,-2)
        d.space(3, -2) // around Brogan
        assertThat(d.zoneOf("brogan", "brogan-gold")).isEqualTo(Zone.HAND)
    }

    @Test
    fun `Angry Stampede - defeat up to two minions you moved through`() {
        val d = pos {
            clearMinions()
            hero("wuk", Team.RED, Hex(-1, -2)) { played("wuk-red-3b") }
            hero("brogan", Team.BLUE, Hex(4, -5))
            minion(Team.BLUE, MinionType.MELEE, Hex(0, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(1, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(-1, -6))
            minion(Team.RED, MinionType.MELEE, Hex(-1, -5))
            phase = PositionPhase.Resolving(next = "wuk")
        }
        val (a, b) = d.s.state.minions.take(2).map { it.id }
        d.action("wuk", ActionType.PRIMARY)
        d.yes()
        d.space(2, -2)
        d.unit(a)
        d.expect(DecisionKind.CHOOSE_UNIT, "wuk")
        d.unit(b)
        assertThat(d.s.state.minions).hasSize(2)
        assertThat(d.hero("wuk").coins).isEqualTo(4)
    }

    @Test
    fun `Angry Stampede - FAQ - moving through a heavy and another minion, the heavy stays immune`() {
        lateinit var heavy: String
        lateinit var melee: String
        val d = pos {
            clearMinions()
            hero("wuk", Team.RED, Hex(-1, -2)) { played("wuk-red-3b") }
            hero("brogan", Team.BLUE, Hex(4, -5))
            melee = minion(Team.BLUE, MinionType.MELEE, Hex(0, -2))
            heavy = minion(Team.BLUE, MinionType.HEAVY, Hex(1, -2))
            minion(Team.RED, MinionType.MELEE, Hex(-1, -5))
            phase = PositionPhase.Resolving(next = "wuk")
        }
        d.action("wuk", ActionType.PRIMARY)
        d.yes()
        d.space(2, -2)
        // "Up to two minions" resolves at the same time: only the melee minion can be chosen.
        assertThat(d.units()).containsExactly(melee)
        d.unit(melee)
        assertThat(d.s.state.minion(heavy)).isNotNull()
        assertThat(d.s.state.minion(melee)).isNull()
    }

    // ---- Into the Canopy / Treetop Ride (green alternates) -------------------------------------

    @Test
    fun `Into the Canopy - Swap with a Tree token in radius`() {
        lateinit var tree: String
        val d = pos {
            clearMinions()
            hero("wuk", Team.RED, Hex(1, -2)) { played("wuk-green-2b") } // Radius 2
            hero("wasp", Team.RED, Hex(0, -2))
            hero("brogan", Team.BLUE, Hex(4, -5))
            tree = token(WukHero.TREE, Hex(3, -2), owner = "wuk")
            token(WukHero.TREE, Hex(4, -2), owner = "wuk") // 3 away: not in radius
            phase = PositionPhase.Resolving(next = "wuk")
        }
        d.action("wuk", ActionType.PRIMARY)
        d.option(0)
        assertThat(d.at("wuk")).isEqualTo(Hex(3, -2))
        assertThat(d.at(tree)).isEqualTo(Hex(1, -2))
    }

    @Test
    fun `Into the Canopy - Swap a friendly unit in radius with a Tree token in radius, not an enemy`() {
        lateinit var tree: String
        val d = pos {
            clearMinions()
            hero("wuk", Team.RED, Hex(1, -2)) { played("wuk-green-2b") }
            hero("wasp", Team.RED, Hex(0, -2))
            hero("brogan", Team.BLUE, Hex(2, -2)) // an enemy in radius: not offered
            tree = token(WukHero.TREE, Hex(3, -2), owner = "wuk")
            phase = PositionPhase.Resolving(next = "wuk")
        }
        d.action("wuk", ActionType.PRIMARY)
        d.option(1)
        // Wasp is the only friendly unit and the tree the only one: no question.
        assertThat(d.at("wasp")).isEqualTo(Hex(3, -2))
        assertThat(d.at(tree)).isEqualTo(Hex(0, -2))
        assertThat(d.at("brogan")).isEqualTo(Hex(2, -2))
    }

    @Test
    fun `Treetop Ride - Choose up to two times, the same bullet again allowed`() {
        lateinit var t1: String
        val d = pos {
            clearMinions()
            hero("wuk", Team.RED, Hex(1, -2)) { played("wuk-green-3b") }
            hero("wasp", Team.RED, Hex(0, -2))
            hero("brogan", Team.BLUE, Hex(4, -5))
            t1 = token(WukHero.TREE, Hex(3, -2), owner = "wuk")
            phase = PositionPhase.Resolving(next = "wuk")
        }
        d.action("wuk", ActionType.PRIMARY)
        d.option(0) // Wuk to (3,-2), the tree to (1,-2)
        d.expect(DecisionKind.CHOOSE_OPTION, "wuk")
        // The radius moved with Wuk: Wasp is 3 away now, so only the first bullet again.
        assertThat(d.options()).containsExactly(0)
        d.option(0) // and back
        assertThat(d.at("wuk")).isEqualTo(Hex(1, -2))
        assertThat(d.at(t1)).isEqualTo(Hex(3, -2))
        // Two times used: the action is over.
        d.expect(DecisionKind.PLAY_CARD)
    }

    @Test
    fun `Treetop Ride - up to, so Wuk may stop after one`() {
        val d = pos {
            clearMinions()
            hero("wuk", Team.RED, Hex(1, -2)) { played("wuk-green-3b") }
            hero("wasp", Team.RED, Hex(0, -2))
            hero("brogan", Team.BLUE, Hex(4, -5))
            token(WukHero.TREE, Hex(3, -2), owner = "wuk")
            phase = PositionPhase.Resolving(next = "wuk")
        }
        d.action("wuk", ActionType.PRIMARY)
        d.option(1)
        d.decline()
        assertThat(d.at("wasp")).isEqualTo(Hex(3, -2))
        assertThat(d.at("wuk")).isEqualTo(Hex(1, -2))
    }

    // ---- March of Nature (Ultimate) ------------------------------------------------------------

    @Test
    fun `March of Nature - each time after you resolve a card, you may place a Tree token in radius`() {
        val d = pos {
            clearMinions()
            hero("wuk", Team.RED, Hex(1, -2)) {
                played("wuk-gold")
                ultimate()
            }
            hero("brogan", Team.BLUE, Hex(4, -5)) { played("brogan-red-1") }
            phase = PositionPhase.Resolving(next = "wuk")
        }
        d.action("wuk", ActionType.HOLD)
        d.expect(DecisionKind.CHOOSE_SPACE, "wuk")
        assertThat(d.spaces().all { HexGrid.distance(it, Hex(1, -2)) in 1..3 }).isTrue()
        d.space(3, -2)
        assertThat(d.trees()).containsExactly(Hex(3, -2))
        // Brogan resolving his card does not trigger it.
        d.action("brogan", ActionType.HOLD)
        d.expect(DecisionKind.PLAY_CARD)
    }

    @Test
    fun `March of Nature - may, so Wuk can decline`() {
        val d = pos {
            clearMinions()
            hero("wuk", Team.RED, Hex(1, -2)) {
                played("wuk-gold")
                ultimate()
            }
            hero("brogan", Team.BLUE, Hex(4, -5))
            phase = PositionPhase.Resolving(next = "wuk")
        }
        d.action("wuk", ActionType.HOLD)
        d.decline()
        assertThat(d.trees()).isEmpty()
    }

    @Test
    fun `March of Nature - p18 with all three Tree tokens in play, Wuk may first take one from the board`() {
        val d = pos {
            clearMinions()
            hero("wuk", Team.RED, Hex(1, -2)) {
                played("wuk-gold")
                ultimate()
            }
            hero("brogan", Team.BLUE, Hex(4, -5))
            token(WukHero.TREE, Hex(-1, -5), owner = "wuk")
            token(WukHero.TREE, Hex(0, -5), owner = "wuk")
            token(WukHero.TREE, Hex(4, -2), owner = "wuk")
            phase = PositionPhase.Resolving(next = "wuk")
        }
        val far = d.s.state.pieces.single { it.at == Hex(0, -5) }.id
        d.action("wuk", ActionType.HOLD)
        d.expect(DecisionKind.CHOOSE_TOKEN, "wuk")
        assertThat(d.d.options.any { it is Choice.Decline }).isTrue()
        d.token(far)
        d.space(3, -2)
        assertThat(d.trees()).containsExactly(Hex(-1, -5), Hex(4, -2), Hex(3, -2))
    }

    // ---- random games ----------------------------------------------------------------------------

    @Test
    fun `random legal games with Wuk keep every invariant and end`() {
        val r = HeroGames.play("wuk", games = 300, seed = 51)
        println("WUK: ${r.games} games, ${r.decisions} decisions, cards played ${r.cardsPlayed.sorted()}")
        assertThat(r.cardsPlayed).containsAtLeast("wuk-gold", "wuk-silver", "wuk-red-1", "wuk-green-1", "wuk-blue-1")
    }
}
