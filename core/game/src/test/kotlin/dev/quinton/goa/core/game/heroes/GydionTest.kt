package dev.quinton.goa.core.game.heroes

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.game.ANY_HERO_ENGINE
import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Controller
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.Driver
import dev.quinton.goa.core.game.GameSetup
import dev.quinton.goa.core.game.SeatSetup
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.WinReason
import dev.quinton.goa.core.game.engine.ActionKind
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.game.engine.Timing
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.game.heroes.gydion.GydionSpells
import dev.quinton.goa.core.game.heroes.gydion.HeroRandomGames
import dev.quinton.goa.core.game.heroes.gydion.Spellbook
import dev.quinton.goa.core.game.heroes.gydion.Spellbook.BANISHMENT
import dev.quinton.goa.core.game.heroes.gydion.Spellbook.BURNING_HANDS
import dev.quinton.goa.core.game.heroes.gydion.Spellbook.CLOUD_KILL
import dev.quinton.goa.core.game.heroes.gydion.Spellbook.CREATE_UNDEAD
import dev.quinton.goa.core.game.heroes.gydion.Spellbook.DIMENSION_DOOR
import dev.quinton.goa.core.game.heroes.gydion.Spellbook.DISINTEGRATE
import dev.quinton.goa.core.game.heroes.gydion.Spellbook.DOMINATE_PERSON
import dev.quinton.goa.core.game.heroes.gydion.Spellbook.ENERGY_DRAIN
import dev.quinton.goa.core.game.heroes.gydion.Spellbook.EXPEDITIOUS_RETREAT
import dev.quinton.goa.core.game.heroes.gydion.Spellbook.FIND_FAMILIAR
import dev.quinton.goa.core.game.heroes.gydion.Spellbook.FIREBALL
import dev.quinton.goa.core.game.heroes.gydion.Spellbook.INVULNERABILITY
import dev.quinton.goa.core.game.heroes.gydion.Spellbook.MAGIC_MISSILE
import dev.quinton.goa.core.game.heroes.gydion.Spellbook.MIDAS_TOUCH
import dev.quinton.goa.core.game.heroes.gydion.Spellbook.POLYMORPH
import dev.quinton.goa.core.game.heroes.gydion.Spellbook.POWER_WORD_KILL
import dev.quinton.goa.core.game.heroes.gydion.Spellbook.REMOVED
import dev.quinton.goa.core.game.heroes.gydion.Spellbook.SHIELD
import dev.quinton.goa.core.game.heroes.gydion.Spellbook.SHOCKING_GRASP
import dev.quinton.goa.core.game.heroes.gydion.Spellbook.SPELLBOOK
import dev.quinton.goa.core.game.heroes.gydion.Spellbook.SUGGESTION
import dev.quinton.goa.core.game.heroes.gydion.Spellbook.SUNBURST
import dev.quinton.goa.core.game.heroes.gydion.Spellbook.VAMPIRIC_TOUCH
import dev.quinton.goa.core.game.heroes.gydion.Spellbook.WISH
import dev.quinton.goa.core.game.pos
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

/**
 * Gydion the Archwizard: every card and every spell, clause by clause (text from
 * `catalog/heroes/gydion.json`), with the FAQ and the designer's BGG rulings (rulings/gydion.md).
 * Quiet strip of Forgotten Island: rows r = -2 and r = -5, q = -1..4; terrain at (1,-3), (2,-4), (5,-2).
 */
class GydionTest {
    private val g0 = Hex(1, -2)

    /** Every spell in the spellbook except [removed] (removed faceup). */
    private fun PositionBuilder.HeroBuilder.prepared(vararg removed: String) {
        extra(*(Spellbook.ALL - removed.toSet()).toTypedArray(), tag = SPELLBOOK)
        if (removed.isNotEmpty()) extra(*removed, tag = REMOVED)
    }

    private fun Driver.tag(spell: String): String? = hero("gydion").card(spell)!!.tag

    private fun Driver.minionAt(h: Hex): String = s.state.minions.first { it.at == h }.id

    // ---- Prepare Spells (silver) and the spellbook ---------------------------------------------

    @Test
    fun `Spellbook - the 22 spell cards are Gydion's extras, each with a script (tested one by one below)`() {
        val ids = listOf(
            "gydion-spell-magic-missile", "gydion-spell-shocking-grasp", "gydion-spell-expeditious-retreat",
            "gydion-spell-burning-hands", "gydion-spell-fireball", "gydion-spell-sunburst",
            "gydion-spell-vampiric-touch", "gydion-spell-create-undead", "gydion-spell-energy-drain",
            "gydion-spell-shield", "gydion-spell-banishment", "gydion-spell-invulnerability",
            "gydion-spell-dimension-door", "gydion-spell-find-familiar", "gydion-spell-cloud-kill",
            "gydion-spell-suggestion", "gydion-spell-dominate-person", "gydion-spell-power-word-kill",
            "gydion-spell-midas-touch", "gydion-spell-disintegrate", "gydion-spell-polymorph", "gydion-spell-wish",
        )
        assertThat(Spellbook.ALL).containsExactlyElementsIn(ids).inOrder()
        assertThat(TestData.catalog.hero("gydion")!!.extras.map { it.id }).containsExactlyElementsIn(ids).inOrder()
        val registry = Heroes.registry()
        for (id in ids) assertThat(registry.script(id)).isNotNull()
    }

    @Test
    fun `Prepare Spells - the spellbook starts empty, so nothing can be cast before it (designer, BGG)`() {
        val s = ANY_HERO_ENGINE.newGame(
            GameSetup(
                TestData.FOREST,
                listOf(
                    SeatSetup("gydion", Team.RED, Controller.Human),
                    SeatSetup("arien", Team.BLUE, Controller.Human),
                ),
                tieBreaker = Team.RED,
            ),
        ) as EngineSession
        val gydion = s.state.hero("gydion")!!
        for (id in Spellbook.ALL) {
            assertThat(gydion.card(id)!!.zone).isEqualTo(Zone.EXTRA)
            assertThat(gydion.card(id)!!.tag).isEqualTo(Spellbook.BOX)
        }
        // Starting hand: Cantrip, Prepare Spells and the three Tier I cards (no spell card).
        assertThat(gydion.hand.map { it.id }).containsExactly(
            "gydion-gold",
            "gydion-silver",
            "gydion-red-1",
            "gydion-green-1",
            "gydion-blue-1",
        )
        val d = pos {
            clearMinions()
            hero("gydion", Team.RED, g0) { played("gydion-gold") }
            hero("arien", Team.BLUE, Hex(2, -2)) { hand("arien-blue-1") }
            phase = PositionPhase.Resolving(next = "gydion")
        }
        // FAQ: the first instruction (cast a spell) cannot be done: no Primary action.
        assertThat(d.actions()).containsExactly(ActionType.HOLD)
    }

    @Test
    fun `Prepare Spells - Add all spell cards to the spellbook, the ones removed and the ones never added`() {
        val d = pos {
            clearMinions()
            hero("gydion", Team.RED, g0) {
                played("gydion-silver")
                extra(MAGIC_MISSILE, SUNBURST, tag = REMOVED) // the rest are still in the box
            }
            hero("arien", Team.BLUE, Hex(4, -5))
            phase = PositionPhase.Resolving(next = "gydion")
        }
        d.action("gydion", ActionType.PRIMARY)
        for (id in Spellbook.ALL) assertThat(d.tag(id)).isEqualTo(SPELLBOOK)
        assertThat(d.hero("gydion").counters[Spellbook.COUNT_IN_BOOK]).isEqualTo(22)
        assertThat(d.hero("gydion").counters[Spellbook.COUNT_REMOVED]).isEqualTo(0)
    }

    @Test
    fun `Shield - Cancelled if the spell is returned to the spellbook (Prepare Spells)`() {
        val d = pos {
            clearMinions()
            hero("gydion", Team.RED, g0) {
                played("gydion-silver")
                prepared(SHIELD)
            }
            hero("arien", Team.BLUE, Hex(4, -5))
            effect("gydion", SHIELD, Timing.THIS_ROUND, ActionKind.SKILL, madeTurn = 1)
            turn = 2
            phase = PositionPhase.Resolving(next = "gydion")
        }
        assertThat(d.s.state.effects.map { it.cardId }).contains(SHIELD)
        d.action("gydion", ActionType.PRIMARY)
        assertThat(d.tag(SHIELD)).isEqualTo(SPELLBOOK)
        assertThat(d.s.state.effects.map { it.cardId }).doesNotContain(SHIELD)
    }

    // ---- Cantrip (gold) and the three cantrip spells ----------------------------------------------

    @Test
    fun `Cantrip - Choose and cast one, only spells in the spellbook that can be cast are offered`() {
        val d = pos {
            clearMinions()
            hero("gydion", Team.RED, g0) {
                played("gydion-gold")
                prepared(EXPEDITIOUS_RETREAT) // spent: not in the spellbook
            }
            hero("arien", Team.BLUE, Hex(2, -2)) { hand("arien-blue-1") } // adjacent
            hero("brogan", Team.BLUE, Hex(4, -2)) { hand("brogan-blue-1") } // 3 away
            phase = PositionPhase.Resolving(next = "gydion")
        }
        d.action("gydion", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_CARD, "gydion")
        assertThat(d.cards()).containsExactly(SHOCKING_GRASP, MAGIC_MISSILE)
        d.card(MAGIC_MISSILE)
        // "As each spell is cast, remove it from the spellbook faceup."
        assertThat(d.tag(MAGIC_MISSILE)).isEqualTo(REMOVED)
        assertThat(d.texts()).contains("Gydion casts Magic Missile.")
    }

    @Test
    fun `Magic Missile - Target a unit in range and not adjacent to you, Attack 1 (a basic ranged attack)`() {
        val d = pos {
            clearMinions()
            hero("gydion", Team.RED, g0) {
                played("gydion-gold")
                prepared(SHOCKING_GRASP, EXPEDITIOUS_RETREAT)
            }
            hero("arien", Team.BLUE, Hex(2, -2)) { hand("arien-blue-1") } // adjacent: not a target
            hero("brogan", Team.BLUE, Hex(4, -2)) { hand("brogan-blue-1") } // 3 away
            minion(Team.BLUE, MinionType.MELEE, Hex(-1, -2)) // 2 away
            phase = PositionPhase.Resolving(next = "gydion")
        }
        d.action("gydion", ActionType.PRIMARY)
        assertThat(d.units()).containsExactly("brogan", d.minionAt(Hex(-1, -2)))
        d.unit("brogan")
        val p = d.expect(DecisionKind.DEFEND, "brogan")
        assertThat(p.context.attackTotal).isEqualTo(1)
        assertThat(p.context.rangedAttack).isTrue()
    }

    @Test
    fun `Magic Missile - the caster's Range items widen the spell's Range (p16)`() {
        val d = pos {
            clearMinions()
            hero("gydion", Team.RED, g0) {
                played("gydion-gold")
                items("gydion-blue-3a") // +1 Range
                prepared(SHOCKING_GRASP, EXPEDITIOUS_RETREAT)
            }
            hero("arien", Team.BLUE, Hex(5, -5)) { hand("arien-blue-1") } // 4 away
            phase = PositionPhase.Resolving(next = "gydion")
        }
        d.action("gydion", ActionType.PRIMARY)
        d.expect(DecisionKind.DEFEND, "arien")
    }

    @Test
    fun `Shocking Grasp - Attack 3 on an adjacent unit, After the attack move the target up to 1 space`() {
        val d = pos {
            clearMinions()
            hero("gydion", Team.RED, g0) {
                played("gydion-gold")
                prepared()
            }
            hero("arien", Team.BLUE, Hex(2, -2)) { hand("arien-blue-1") } // Defense 5
            phase = PositionPhase.Resolving(next = "gydion")
        }
        d.action("gydion", ActionType.PRIMARY)
        // Magic Missile has no target: Shocking Grasp and Expeditious Retreat only.
        assertThat(d.cards()).containsExactly(SHOCKING_GRASP, EXPEDITIOUS_RETREAT)
        d.card(SHOCKING_GRASP)
        assertThat(d.expect(DecisionKind.DEFEND).context.attackTotal).isEqualTo(3)
        d.defend("arien-blue-1")
        // Gydion chooses where the target goes (up to 1 space: may stay).
        d.expect(DecisionKind.CHOOSE_SPACE, "gydion")
        assertThat(d.d.options.any { it is Choice.Decline }).isTrue()
        for (h in d.spaces()) assertThat(HexGrid.distance(h, Hex(2, -2))).isEqualTo(1)
        d.space(3, -2)
        assertThat(d.at("arien")).isEqualTo(Hex(3, -2))
        assertThat(d.tag(SHOCKING_GRASP)).isEqualTo(REMOVED)
    }

    @Test
    fun `Expeditious Retreat - Move only in a straight line, up to Movement 5`() {
        val d = pos {
            clearMinions()
            hero("gydion", Team.RED, g0) {
                played("gydion-gold")
                prepared()
            }
            hero("arien", Team.BLUE, Hex(4, -9))
            phase = PositionPhase.Resolving(next = "gydion")
        }
        d.action("gydion", ActionType.PRIMARY)
        // Nothing to attack: the only spell that can be cast is Expeditious Retreat (no question).
        d.expect(DecisionKind.CHOOSE_SPACE, "gydion")
        assertThat(d.spaces()).isNotEmpty()
        for (h in d.spaces()) {
            assertThat(HexGrid.inStraightLine(g0, h)).isTrue()
            assertThat(HexGrid.distance(g0, h)).isAtMost(5)
        }
        assertThat(d.spaces()).contains(Hex(-1, -2))
        assertThat(d.spaces()).doesNotContain(Hex(0, 0)) // 2 away, not in a straight line
        d.space(-1, -2)
        assertThat(d.at("gydion")).isEqualTo(Hex(-1, -2))
        assertThat(d.tag(EXPEDITIOUS_RETREAT)).isEqualTo(REMOVED)
    }

    @Test
    fun `Cheap Trick - Cast the Expeditious Retreat spell (Handicap gold card)`() {
        val d = pos {
            clearMinions()
            hero("gydion", Team.RED, g0) {
                handicap = true
                played("gydion-handicap")
                prepared()
            }
            hero("arien", Team.BLUE, Hex(2, -2)) { hand("arien-blue-1") }
            phase = PositionPhase.Resolving(next = "gydion")
        }
        assertThat(d.hero("gydion").card("gydion-gold")!!.zone).isEqualTo(Zone.BOX)
        d.action("gydion", ActionType.PRIMARY)
        // Only Expeditious Retreat, though Shocking Grasp would have a target.
        for (h in d.spaces()) assertThat(HexGrid.inStraightLine(g0, h)).isTrue()
        d.space(-1, -2)
        assertThat(d.tag(EXPEDITIOUS_RETREAT)).isEqualTo(REMOVED)
        assertThat(d.tag(SHOCKING_GRASP)).isEqualTo(SPELLBOOK)
    }

    // ---- Evocation: Burning Hands, Fireball, Sunburst ----------------------------------------------

    @Test
    fun `Elementary Evocation - Burning Hands, Before the attack up to 1 enemy hero adjacent to the target discards`() {
        val d = pos {
            clearMinions()
            hero("gydion", Team.RED, g0) {
                played("gydion-red-1")
                prepared()
            }
            hero("arien", Team.BLUE, Hex(2, -2)) { hand("arien-blue-1") } // the target, Defense 5
            hero("brogan", Team.BLUE, Hex(3, -2)) { hand("brogan-gold", "brogan-silver") } // adjacent to Arien
            phase = PositionPhase.Resolving(next = "gydion")
        }
        d.action("gydion", ActionType.PRIMARY)
        // Up to 1: Gydion may pick nobody.
        d.expect(DecisionKind.CHOOSE_UNIT, "gydion")
        assertThat(d.units()).containsExactly("brogan")
        assertThat(d.d.options.any { it is Choice.Decline }).isTrue()
        d.unit("brogan")
        d.expect(DecisionKind.CHOOSE_CARD, "brogan")
        d.card("brogan-gold")
        assertThat(d.zoneOf("brogan", "brogan-gold")).isEqualTo(Zone.DISCARD)
        assertThat(d.expect(DecisionKind.DEFEND, "arien").context.attackTotal).isEqualTo(5)
    }

    @Test
    fun `Elementary Evocation - with no unit adjacent Burning Hands cannot be cast (no Primary action)`() {
        val d = pos {
            clearMinions()
            hero("gydion", Team.RED, g0) {
                played("gydion-red-1")
                prepared()
            }
            hero("arien", Team.BLUE, Hex(4, -2))
            phase = PositionPhase.Resolving(next = "gydion")
        }
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
        assertThat(d.actions()).contains(ActionType.MOVEMENT)
    }

    @Test
    fun `Lesser Evocation - Fireball, a unit in range not adjacent to you or to your friendly unit`() {
        val d = pos {
            clearMinions()
            hero("gydion", Team.RED, g0) {
                played("gydion-red-2a")
                prepared()
            }
            hero("arien", Team.BLUE, Hex(3, -2)) { hand("arien-blue-1") } // next to a red minion
            hero("brogan", Team.BLUE, Hex(-1, -2)) { hand("brogan-blue-1") } // 2 away, alone
            minion(Team.RED, MinionType.MELEE, Hex(4, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(2, -2)) // adjacent to Gydion
            minion(Team.BLUE, MinionType.MELEE, Hex(1, -5)) // 3 away, alone
            phase = PositionPhase.Resolving(next = "gydion")
        }
        d.action("gydion", ActionType.PRIMARY)
        assertThat(d.cards()).containsExactly(BURNING_HANDS, FIREBALL)
        d.card(FIREBALL)
        assertThat(d.units()).containsExactly("brogan", d.minionAt(Hex(1, -5)))
        d.unit("brogan")
        val p = d.expect(DecisionKind.DEFEND, "brogan")
        assertThat(p.context.attackTotal).isEqualTo(5)
        assertThat(p.context.rangedAttack).isTrue()
    }

    @Test
    fun `Greater Evocation - Sunburst, exactly at maximum range, +1 Attack and Range per other removed spell`() {
        val d = pos {
            clearMinions()
            hero("gydion", Team.RED, g0) {
                played("gydion-red-3a")
                prepared(MAGIC_MISSILE, SHOCKING_GRASP, EXPEDITIOUS_RETREAT) // 3 removed
            }
            hero("arien", Team.BLUE, Hex(3, -2)) { hand("arien-blue-1") } // 2 away
            hero("brogan", Team.BLUE, Hex(4, -2)) { hand("brogan-blue-1") } // 3 away
            phase = PositionPhase.Resolving(next = "gydion")
        }
        d.action("gydion", ActionType.PRIMARY)
        assertThat(d.cards()).containsExactly(FIREBALL, SUNBURST)
        d.card(SUNBURST)
        // Range 0 + 3 = 3: only Brogan (designer: not an enemy closer than the maximum range).
        val p = d.expect(DecisionKind.DEFEND, "brogan")
        assertThat(p.context.attackTotal).isEqualTo(3)
        assertThat(d.tag(SUNBURST)).isEqualTo(REMOVED)
    }

    @Test
    fun `Sunburst - items add to the printed 0 Range (designer, BGG 3589662)`() {
        val d = pos {
            clearMinions()
            hero("gydion", Team.RED, g0) {
                played("gydion-red-3a")
                items("gydion-blue-3a") // +1 Range
                prepared(MAGIC_MISSILE, SHOCKING_GRASP, EXPEDITIOUS_RETREAT, BURNING_HANDS, FIREBALL)
            }
            hero("arien", Team.BLUE, Hex(4, -2)) { hand("arien-blue-1") } // 3 away
            hero("brogan", Team.BLUE, Hex(-5, -2)) { hand("brogan-blue-1") } // 6 away
            phase = PositionPhase.Resolving(next = "gydion")
        }
        d.action("gydion", ActionType.PRIMARY)
        // Range 0 + 1 (item) + 5 (other removed spells) = 6.
        val p = d.expect(DecisionKind.DEFEND, "brogan")
        assertThat(p.context.attackTotal).isEqualTo(5)
    }

    // ---- Necromancy: Vampiric Touch, Create Undead, Energy Drain -----------------------------------

    @Test
    fun `Lesser Necromancy - Vampiric Touch, After the attack you may retrieve a discarded card`() {
        val d = pos {
            clearMinions()
            hero("gydion", Team.RED, g0) {
                played("gydion-red-2b")
                hand("gydion-gold") // silver, green and blue are in the discard
                prepared()
            }
            hero("arien", Team.BLUE, Hex(2, -2)) { hand("arien-blue-1") }
            phase = PositionPhase.Resolving(next = "gydion")
        }
        d.action("gydion", ActionType.PRIMARY)
        assertThat(d.cards()).containsExactly(VAMPIRIC_TOUCH, CREATE_UNDEAD)
        d.card(VAMPIRIC_TOUCH)
        d.defend("arien-blue-1")
        d.expect(DecisionKind.CHOOSE_CARD, "gydion")
        assertThat(d.cards()).containsExactly("gydion-silver", "gydion-green-1", "gydion-blue-1")
        d.card("gydion-blue-1")
        assertThat(d.zoneOf("gydion", "gydion-blue-1")).isEqualTo(Zone.HAND)
    }

    @Test
    fun `Create Undead - respawn a friendly minion of any type on an empty friendly spawn point in range`() {
        val d = pos {
            clearMinions()
            hero("gydion", Team.RED, g0) {
                played("gydion-red-2b")
                prepared(VAMPIRIC_TOUCH)
            }
            hero("arien", Team.BLUE, Hex(4, -9))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -5))
            phase = PositionPhase.Resolving(next = "gydion")
        }
        d.action("gydion", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_SPACE, "gydion")
        // Red spawn points of the Battle Zone within 3: melee (2,-3), ranged (0,-3), heavy (1,0), melee (-2,1).
        // Blue ones ((1,-4) melee, (3,-4) ranged) are not friendly.
        assertThat(d.spaces()).containsExactly(Hex(2, -3), Hex(0, -3), Hex(1, 0), Hex(-2, 1))
        d.space(2, -3)
        // Dodger FAQ on the same text: any type of friendly minion.
        d.expect(DecisionKind.CHOOSE_OPTION, "gydion")
        d.option(1) // Ranged
        val m = d.s.state.minions.single { it.at == Hex(2, -3) }
        assertThat(m.team).isEqualTo(Team.RED)
        assertThat(m.type).isEqualTo(MinionType.RANGED)
    }

    @Test
    fun `Energy Drain - an enemy hero discards a non-basic card, your team regains 1 spent Life counter`() {
        val d = pos {
            clearMinions()
            hero("gydion", Team.RED, g0) {
                played("gydion-red-3b")
                prepared(VAMPIRIC_TOUCH, CREATE_UNDEAD)
            }
            hero("arien", Team.BLUE, Hex(4, -2)) { hand("arien-gold", "arien-red-1") } // 3 away
            lifeCounters(Team.RED, 1)
            phase = PositionPhase.Resolving(next = "gydion")
        }
        d.action("gydion", ActionType.PRIMARY)
        // Arien chooses, but the only non-basic card is Dangerous Current.
        assertThat(d.zoneOf("arien", "arien-red-1")).isEqualTo(Zone.DISCARD)
        assertThat(d.zoneOf("arien", "arien-gold")).isEqualTo(Zone.HAND)
        assertThat(d.s.state.lifeCounters[Team.RED]).isEqualTo(2)
    }

    @Test
    fun `Energy Drain - FAQ - no targetable enemy hero in range, so no Life counter (the spell cannot be cast)`() {
        val d = pos {
            clearMinions()
            hero("gydion", Team.RED, g0) {
                played("gydion-red-3b")
                prepared(VAMPIRIC_TOUCH, CREATE_UNDEAD)
            }
            hero("arien", Team.BLUE, Hex(4, -9))
            lifeCounters(Team.RED, 1)
            phase = PositionPhase.Resolving(next = "gydion")
        }
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
    }

    @Test
    fun `Energy Drain - nothing non-basic to discard, and no spent Life counter to regain`() {
        val d = pos {
            clearMinions()
            hero("gydion", Team.RED, g0) {
                played("gydion-red-3b")
                prepared(VAMPIRIC_TOUCH, CREATE_UNDEAD)
            }
            hero("arien", Team.BLUE, Hex(4, -2)) { hand("arien-gold") }
            phase = PositionPhase.Resolving(next = "gydion")
        }
        val start = d.s.state.lifeCounters[Team.RED]
        d.action("gydion", ActionType.PRIMARY)
        assertThat(d.zoneOf("arien", "arien-gold")).isEqualTo(Zone.HAND)
        // No Life counter spent yet: nothing to regain.
        assertThat(d.s.state.lifeCounters[Team.RED]).isEqualTo(start)
        assertThat(d.tag(ENERGY_DRAIN)).isEqualTo(REMOVED)
    }

    // ---- Abjuration: Shield, Banishment, Invulnerability --------------------------------------------

    private fun shieldPosition(card: String, attacker: String) = pos {
        clearMinions()
        hero("gydion", Team.RED, g0) {
            played(card)
            prepared(BANISHMENT) // Greater Abjuration: Shield or Invulnerability
        }
        hero("arien", Team.BLUE, Hex(2, -2)) { played(attacker) }
        phase = PositionPhase.Resolving(next = "gydion")
    }

    @Test
    fun `Elementary Abjuration - Shield, This round you are immune to basic attacks`() {
        val d = shieldPosition("gydion-green-1", "arien-gold")
        d.action("gydion", ActionType.PRIMARY)
        assertThat(d.s.state.effects.single { it.cardId == SHIELD }.timing).isEqualTo(Timing.THIS_ROUND)
        // Noble Blade (gold, basic): Gydion is the only adjacent enemy and is immune.
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
    }

    @Test
    fun `Shield - not immune to non-basic attacks`() {
        val d = shieldPosition("gydion-green-1", "arien-red-1")
        d.action("gydion", ActionType.PRIMARY)
        assertThat(d.actions()).contains(ActionType.PRIMARY)
    }

    @Test
    fun `Greater Abjuration - Invulnerability, This round you are immune to non-basic attacks, not to basic ones`() {
        val d = shieldPosition("gydion-green-3a", "arien-red-1")
        d.action("gydion", ActionType.PRIMARY)
        assertThat(d.cards()).containsExactly(SHIELD, INVULNERABILITY)
        d.card(INVULNERABILITY)
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
        val e = shieldPosition("gydion-green-3a", "arien-gold")
        e.action("gydion", ActionType.PRIMARY)
        e.card(INVULNERABILITY)
        assertThat(e.actions()).contains(ActionType.PRIMARY)
    }

    @Test
    fun `Lesser Abjuration - Banishment, place a unit or a token adjacent to you into a space in radius`() {
        val d = pos {
            clearMinions()
            hero("gydion", Team.RED, g0) {
                played("gydion-green-2a")
                prepared()
            }
            hero("arien", Team.BLUE, Hex(2, -2)) // adjacent
            hero("brogan", Team.BLUE, Hex(4, -2)) // 3 away: not adjacent
            token("token_rock", Hex(0, -2))
            phase = PositionPhase.Resolving(next = "gydion")
        }
        d.action("gydion", ActionType.PRIMARY)
        d.card(BANISHMENT)
        val rock = d.s.state.pieces.single().id
        assertThat(d.units()).containsExactly("arien", rock)
        d.unit("arien")
        d.expect(DecisionKind.CHOOSE_SPACE, "gydion")
        for (h in d.spaces()) assertThat(HexGrid.distance(g0, h)).isIn(1..3)
        assertThat(d.spaces()).doesNotContain(Hex(4, -2)) // not empty
        d.space(3, -5)
        assertThat(d.at("arien")).isEqualTo(Hex(3, -5))
    }

    // ---- Conjuration: Find Familiar, Dimension Door, Cloud Kill -------------------------------------

    @Test
    fun `Find Familiar - place a Familiar token in radius, you may remove up to three other spells`() {
        val d = pos {
            clearMinions()
            hero("gydion", Team.RED, g0) {
                played("gydion-green-2b")
                prepared()
            }
            hero("arien", Team.BLUE, Hex(4, -9))
            phase = PositionPhase.Resolving(next = "gydion")
        }
        d.action("gydion", ActionType.PRIMARY)
        // Dimension Door: Radius 0 with no spell removed, so no space: only Find Familiar.
        d.expect(DecisionKind.CHOOSE_SPACE, "gydion")
        for (h in d.spaces()) assertThat(HexGrid.distance(g0, h)).isIn(1..3)
        d.space(3, -2)
        assertThat(d.s.state.pieces.single { it.at == Hex(3, -2) }.kind).isEqualTo(GydionSpells.FAMILIAR)
        // Designer: any spell in the spellbook, chosen by Gydion; never Find Familiar itself.
        d.expect(DecisionKind.CHOOSE_CARD, "gydion")
        assertThat(d.cards()).doesNotContain(FIND_FAMILIAR)
        assertThat(d.cards()).contains(WISH)
        d.card(SUNBURST)
        d.card(POLYMORPH)
        d.decline()
        assertThat(d.tag(SUNBURST)).isEqualTo(REMOVED)
        assertThat(d.tag(POLYMORPH)).isEqualTo(REMOVED)
        assertThat(d.tag(FIND_FAMILIAR)).isEqualTo(REMOVED)
        assertThat(d.hero("gydion").counters[Spellbook.COUNT_REMOVED]).isEqualTo(3)
    }

    @Test
    fun `Find Familiar - up to three, then it stops asking`() {
        val d = pos {
            clearMinions()
            hero("gydion", Team.RED, g0) {
                played("gydion-green-2b")
                prepared()
            }
            hero("arien", Team.BLUE, Hex(4, -9))
            phase = PositionPhase.Resolving(next = "gydion")
        }
        d.action("gydion", ActionType.PRIMARY)
        d.space(3, -2)
        d.card(SUNBURST)
        d.card(POLYMORPH)
        d.card(WISH)
        assertThat(d.hero("gydion").counters[Spellbook.COUNT_REMOVED]).isEqualTo(4)
        assertThat(d.d.kind).isNotEqualTo(DecisionKind.CHOOSE_CARD)
    }

    @Test
    fun `Lesser Conjuration - Dimension Door, place yourself at maximum radius, +1 Radius per other removed spell`() {
        val d = pos {
            clearMinions()
            hero("gydion", Team.RED, g0) {
                played("gydion-green-2b")
                prepared(MAGIC_MISSILE, SHOCKING_GRASP)
            }
            hero("arien", Team.BLUE, Hex(4, -9))
            phase = PositionPhase.Resolving(next = "gydion")
        }
        d.action("gydion", ActionType.PRIMARY)
        assertThat(d.cards()).containsExactly(FIND_FAMILIAR, DIMENSION_DOOR)
        d.card(DIMENSION_DOOR)
        d.expect(DecisionKind.CHOOSE_SPACE, "gydion")
        for (h in d.spaces()) assertThat(HexGrid.distance(g0, h)).isEqualTo(2)
        d.space(3, -2)
        assertThat(d.at("gydion")).isEqualTo(Hex(3, -2))
    }

    @Test
    fun `Greater Conjuration - Cloud Kill, an enemy hero in radius discards a basic card, if able`() {
        val d = pos {
            clearMinions()
            hero("gydion", Team.RED, g0) {
                played("gydion-green-3b")
                prepared()
            }
            hero("arien", Team.BLUE, Hex(3, -2)) { hand("arien-gold", "arien-red-1") }
            phase = PositionPhase.Resolving(next = "gydion")
        }
        d.action("gydion", ActionType.PRIMARY)
        assertThat(d.cards()).containsExactly(FIND_FAMILIAR, CLOUD_KILL)
        d.card(CLOUD_KILL)
        assertThat(d.zoneOf("arien", "arien-gold")).isEqualTo(Zone.DISCARD)
        assertThat(d.zoneOf("arien", "arien-red-1")).isEqualTo(Zone.HAND)
    }

    // ---- Enchantment: Suggestion, Dominate Person, Power Word Kill ------------------------------------

    @Test
    fun `Suggestion - If able, an enemy hero in radius moves 3 spaces in a straight line (you choose)`() {
        val d = pos {
            clearMinions()
            hero("gydion", Team.RED, g0) {
                played("gydion-blue-1")
                prepared()
            }
            hero("arien", Team.BLUE, Hex(3, -2)) // 2 away
            phase = PositionPhase.Resolving(next = "gydion")
        }
        d.action("gydion", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_SPACE, "gydion")
        assertThat(d.d.options.any { it is Choice.Decline }).isFalse() // mandatory when able
        for (h in d.spaces()) {
            assertThat(HexGrid.distance(Hex(3, -2), h)).isEqualTo(3)
            assertThat(HexGrid.inStraightLine(Hex(3, -2), h)).isTrue()
        }
        val to = d.spaces().first()
        d.space(to)
        assertThat(d.at("arien")).isEqualTo(to)
    }

    @Test
    fun `Suggestion - no enemy hero in radius can move 3 in a line, so nothing moves (if able)`() {
        val d = pos {
            clearMinions()
            hero("gydion", Team.RED, g0) {
                played("gydion-blue-1")
                prepared()
            }
            hero("arien", Team.BLUE, Hex(4, -9)) // out of radius
            phase = PositionPhase.Resolving(next = "gydion")
        }
        d.action("gydion", ActionType.PRIMARY)
        assertThat(d.at("arien")).isEqualTo(Hex(4, -9))
        assertThat(d.tag(SUGGESTION)).isEqualTo(REMOVED)
    }

    @Test
    fun `Dominate Person - target an enemy hero, defeat an enemy minion in radius adjacent to it`() {
        val d = pos {
            clearMinions()
            hero("gydion", Team.RED, g0) {
                played("gydion-blue-2a")
                prepared(SUGGESTION)
            }
            hero("arien", Team.BLUE, Hex(3, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -2)) // adjacent to Arien, 3 from Gydion
            minion(Team.BLUE, MinionType.MELEE, Hex(-1, -5)) // far
            minion(Team.RED, MinionType.MELEE, Hex(-1, -2))
            phase = PositionPhase.Resolving(next = "gydion")
        }
        d.action("gydion", ActionType.PRIMARY)
        assertThat(d.s.state.minions.none { it.at == Hex(4, -2) }).isTrue()
        assertThat(d.hero("gydion").coins).isEqualTo(2)
    }

    @Test
    fun `Greater Enchantment - Power Word Kill, defeat an enemy hero in radius with no cards in hand`() {
        val d = pos {
            clearMinions()
            hero("gydion", Team.RED, g0) {
                played("gydion-blue-3a")
                prepared()
            }
            hero("arien", Team.BLUE, Hex(3, -2)) { hand() } // no cards
            hero("brogan", Team.BLUE, Hex(2, -2)) { hand("brogan-blue-1") }
            phase = PositionPhase.Resolving(next = "gydion")
        }
        d.action("gydion", ActionType.PRIMARY)
        d.card(POWER_WORD_KILL)
        assertThat(d.at("arien")).isNull()
        assertThat(d.at("brogan")).isEqualTo(Hex(2, -2))
        assertThat(d.hero("gydion").coins).isEqualTo(1) // Arien's level
    }

    // ---- Transmutation: Midas' Touch, Disintegrate, Polymorph ----------------------------------------

    @Test
    fun `Lesser Transmutation - Midas Touch, gain 1 coin for every two other spell cards removed`() {
        val d = pos {
            clearMinions()
            hero("gydion", Team.RED, g0) {
                played("gydion-blue-2b")
                prepared(MAGIC_MISSILE, SHOCKING_GRASP, EXPEDITIOUS_RETREAT, BURNING_HANDS, FIREBALL)
            }
            hero("arien", Team.BLUE, Hex(4, -9))
            phase = PositionPhase.Resolving(next = "gydion")
        }
        d.action("gydion", ActionType.PRIMARY)
        assertThat(d.hero("gydion").coins).isEqualTo(2) // 5 other removed: 2 coins
        assertThat(d.tag(MIDAS_TOUCH)).isEqualTo(REMOVED)
    }

    @Test
    fun `Lesser Transmutation - Disintegrate, remove a token or an enemy non-heavy minion adjacent to you`() {
        val d = pos {
            clearMinions()
            hero("gydion", Team.RED, g0) {
                played("gydion-blue-2b")
                prepared()
            }
            hero("arien", Team.BLUE, Hex(2, -2)) // a hero: not removable
            minion(Team.BLUE, MinionType.MELEE, Hex(0, -2))
            minion(Team.BLUE, MinionType.HEAVY, Hex(0, -1))
            minion(Team.RED, MinionType.MELEE, Hex(1, -1)) // friendly: not removable
            token("token_rock", Hex(2, -3))
            phase = PositionPhase.Resolving(next = "gydion")
        }
        d.action("gydion", ActionType.PRIMARY)
        d.card(DISINTEGRATE)
        val rock = d.s.state.pieces.single().id
        assertThat(d.units()).containsExactly(d.minionAt(Hex(0, -2)), rock)
        d.token(rock)
        assertThat(d.s.state.pieces).isEmpty()
    }

    @Test
    fun `Greater Transmutation - Polymorph, swap an enemy hero in radius with a token or an enemy minion in radius`() {
        val d = pos {
            clearMinions()
            hero("gydion", Team.RED, g0) {
                played("gydion-blue-3b")
                prepared(MAGIC_MISSILE)
            }
            hero("arien", Team.BLUE, Hex(3, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(1, -5))
            minion(Team.RED, MinionType.MELEE, Hex(-1, -2)) // friendly: not a partner
            token("token_rock", Hex(0, -2))
            phase = PositionPhase.Resolving(next = "gydion")
        }
        d.action("gydion", ActionType.PRIMARY)
        // Midas' Touch (castable for 0 coins), Disintegrate (the rock is adjacent) and Polymorph.
        assertThat(d.cards()).containsExactly(MIDAS_TOUCH, DISINTEGRATE, POLYMORPH)
        d.card(POLYMORPH)
        val rock = d.s.state.pieces.single().id
        assertThat(d.units()).containsExactly(d.minionAt(Hex(1, -5)), rock)
        d.token(rock)
        assertThat(d.at("arien")).isEqualTo(Hex(0, -2))
        assertThat(d.at(rock)).isEqualTo(Hex(3, -2))
    }

    // ---- The Archwizard (Ultimate) and Wish ------------------------------------------------------------

    @Test
    fun `The Archwizard - instead of adding the spells you may cast Wish, which casts any spell in the spellbook`() {
        val d = pos {
            clearMinions()
            hero("gydion", Team.RED, g0) {
                ultimate()
                played("gydion-silver")
                prepared(MAGIC_MISSILE)
            }
            hero("arien", Team.BLUE, Hex(4, -9))
            phase = PositionPhase.Resolving(next = "gydion")
        }
        assertThat(d.hero("gydion").card(Spellbook.ULTIMATE)!!.zone).isEqualTo(Zone.ULTIMATE)
        assertThat(d.hero("gydion").card(WISH)!!.zone).isEqualTo(Zone.EXTRA)
        d.action("gydion", ActionType.PRIMARY)
        d.expect(DecisionKind.CONFIRM, "gydion")
        d.yes()
        // "Cast any spell in the Spellbook": the ones that can be cast now.
        d.expect(DecisionKind.CHOOSE_CARD, "gydion")
        assertThat(d.cards()).contains(SHIELD)
        assertThat(d.cards()).doesNotContain(MAGIC_MISSILE) // not in the spellbook
        assertThat(d.cards()).doesNotContain(WISH)
        d.card(SHIELD)
        assertThat(d.tag(WISH)).isEqualTo(REMOVED)
        assertThat(d.tag(SHIELD)).isEqualTo(REMOVED)
        assertThat(d.tag(MAGIC_MISSILE)).isEqualTo(REMOVED) // nothing was added
        assertThat(d.hero("gydion").counters[Spellbook.COUNT_WISH]).isEqualTo(1)
        assertThat(d.s.state.effects.single { it.cardId == SHIELD }.owner).isEqualTo("gydion")
    }

    @Test
    fun `The Archwizard - declining Wish adds the spells, and without Wish in the spellbook nothing is asked`() {
        val d = pos {
            clearMinions()
            hero("gydion", Team.RED, g0) {
                ultimate()
                played("gydion-silver")
                prepared(MAGIC_MISSILE)
            }
            hero("arien", Team.BLUE, Hex(4, -9))
            phase = PositionPhase.Resolving(next = "gydion")
        }
        d.action("gydion", ActionType.PRIMARY)
        d.decline()
        assertThat(d.tag(MAGIC_MISSILE)).isEqualTo(SPELLBOOK)
        assertThat(d.tag(WISH)).isEqualTo(SPELLBOOK)
        val e = pos {
            clearMinions()
            hero("gydion", Team.RED, g0) {
                ultimate()
                played("gydion-silver")
                prepared(WISH)
            }
            hero("arien", Team.BLUE, Hex(4, -9))
            phase = PositionPhase.Resolving(next = "gydion")
        }
        e.action("gydion", ActionType.PRIMARY)
        assertThat(e.tag(WISH)).isEqualTo(SPELLBOOK)
    }

    @Test
    fun `Wish - After you cast the Wish spell three times your team wins the game`() {
        val d = pos {
            clearMinions()
            hero("gydion", Team.RED, g0) {
                ultimate()
                played("gydion-silver")
                prepared()
                counter(Spellbook.COUNT_WISH, 2)
            }
            hero("arien", Team.BLUE, Hex(4, -9))
            phase = PositionPhase.Resolving(next = "gydion")
        }
        d.action("gydion", ActionType.PRIMARY)
        d.yes()
        d.card(MIDAS_TOUCH)
        val v = d.s.view(null)
        assertThat(v.winner).isEqualTo(Team.RED)
        assertThat(v.winReason).isEqualTo(WinReason.CARD_TEXT)
    }

    // ---- random games ---------------------------------------------------------------------------------

    @Test
    fun `random legal games with Gydion keep every invariant`() {
        HeroRandomGames.run("gydion", games = 150, seed = 81) { st, _ ->
            val gydion = st.hero("gydion") ?: return@run
            for (id in Spellbook.ALL) {
                val c = gydion.card(id)!!
                assertThat(c.zone).isEqualTo(Zone.EXTRA)
                assertThat(c.tag).isIn(listOf(Spellbook.BOX, SPELLBOOK, REMOVED))
            }
        }
    }
}
