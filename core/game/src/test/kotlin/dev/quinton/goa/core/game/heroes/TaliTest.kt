package dev.quinton.goa.core.game.heroes

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.game.ANY_HERO_ENGINE
import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.Driver
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.effects.AffectQuery
import dev.quinton.goa.core.game.effects.Behavior
import dev.quinton.goa.core.game.effects.Registry
import dev.quinton.goa.core.game.effects.Src
import dev.quinton.goa.core.game.engine.ActionKind
import dev.quinton.goa.core.game.engine.Game
import dev.quinton.goa.core.game.engine.GameEngine
import dev.quinton.goa.core.game.engine.RunMode
import dev.quinton.goa.core.game.engine.Timing
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.game.engine.initiativeOf
import dev.quinton.goa.core.game.pos
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

/** Tali the Spiritcaller: every card, clause by clause (text from `catalog/heroes/tali.json`). */
class TaliTest {
    private fun Driver.initiative(hero: String): Int? =
        Game(ANY_HERO_ENGINE.data, s.state, emptyList(), RunMode.PROBE).initiativeOf(hero)

    private fun Driver.tokens(kind: String) = s.state.pieces.filter { it.kind == kind && it.at != null }

    // ---- Ice Blast (gold) / Ice Shard (Handicap) ----------------------------------------------

    @Test
    fun `Ice Blast - if the target discards to defend, another enemy hero in radius discards a card of that color`() {
        val d = pos {
            clearMinions()
            hero("tali", Team.RED, Hex(0, -2)) { played("tali-gold") } // radius 4
            hero("arien", Team.BLUE, Hex(1, -2)) { hand("arien-blue-1") }
            hero("brogan", Team.BLUE, Hex(3, -2)) { hand("brogan-gold", "brogan-blue-1") }
            phase = PositionPhase.Resolving(next = "tali")
        }
        d.action("tali", ActionType.PRIMARY)
        d.defend("arien-blue-1") // a blue card
        // Brogan is the only other enemy hero in radius; his only blue card goes.
        assertThat(d.zoneOf("brogan", "brogan-blue-1")).isEqualTo(Zone.DISCARD)
        assertThat(d.zoneOf("brogan", "brogan-gold")).isEqualTo(Zone.HAND)
    }

    @Test
    fun `Ice Blast - no discard to defend, no After the attack`() {
        val d = pos {
            clearMinions()
            hero("tali", Team.RED, Hex(0, -2)) { played("tali-gold") }
            hero("arien", Team.BLUE, Hex(1, -2)) { hand("arien-blue-1") }
            hero("brogan", Team.BLUE, Hex(3, -2)) { hand("brogan-blue-1") }
            phase = PositionPhase.Resolving(next = "tali")
        }
        d.action("tali", ActionType.PRIMARY)
        d.defend(null)
        assertThat(d.at("arien")).isNull()
        assertThat(d.zoneOf("brogan", "brogan-blue-1")).isEqualTo(Zone.HAND)
    }

    @Test
    fun `Ice Shard - Target a hero adjacent to you, then the same After the attack`() {
        val d = pos {
            clearMinions()
            hero("tali", Team.RED, Hex(0, -2)) { played("tali-handicap") } // radius 3
            hero("arien", Team.BLUE, Hex(1, -2)) { hand("arien-gold") }
            hero("brogan", Team.BLUE, Hex(3, -2)) { hand("brogan-gold", "brogan-silver") }
            minion(Team.BLUE, MinionType.MELEE, Hex(0, -1)) // adjacent, but not a hero
            minion(Team.RED, MinionType.MELEE, Hex(-1, -5))
            phase = PositionPhase.Resolving(next = "tali")
        }
        d.action("tali", ActionType.PRIMARY)
        d.defend("arien-gold") // Defense 2 against Attack 2
        assertThat(d.zoneOf("brogan", "brogan-gold")).isEqualTo(Zone.DISCARD)
        assertThat(d.zoneOf("brogan", "brogan-silver")).isEqualTo(Zone.HAND)
    }

    // ---- Commune with Spirits (silver) ---------------------------------------------------------

    @Test
    fun `Commune with Spirits - a friend names a color, you may discard one, then perform one from the discard`() {
        val d = pos {
            clearMinions()
            hero("tali", Team.RED, Hex(0, -2)) { played("tali-silver") }
            hero("wasp", Team.RED, Hex(3, -5))
            hero("arien", Team.BLUE, Hex(1, -2)) { hand("arien-gold") }
            phase = PositionPhase.Resolving(next = "tali")
        }
        d.action("tali", ActionType.PRIMARY)
        // Wasp is the only friendly hero in play: she names the color.
        d.expect(DecisionKind.CHOOSE_OPTION, "wasp")
        d.option(2) // red
        d.expect(DecisionKind.CHOOSE_CARD, "tali")
        d.card("tali-red-1")
        // Winter Dagger is now in the discard, so it has no +3: Attack 2 against Noble Blade's Defense 2.
        d.defend("arien-gold")
        assertThat(d.at("arien")).isEqualTo(Hex(1, -2))
        assertThat(d.texts().any { it.contains("Attack 2.") }).isTrue()
    }

    @Test
    fun `Commune with Spirits - FAQ - with the whole team defeated, no friendly hero can name a color`() {
        val d = pos {
            clearMinions()
            hero("tali", Team.RED, Hex(0, -2)) {
                played("tali-silver")
                discard("tali-red-1")
            }
            hero("wasp", Team.RED, null)
            hero("arien", Team.BLUE, Hex(1, -2))
            phase = PositionPhase.Resolving(next = "tali")
        }
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
    }

    @Test
    fun `Commune with Spirits - a friend immune only to enemy attack actions may still name a color`() {
        val d = pos {
            clearMinions()
            hero("tali", Team.RED, Hex(0, -2)) { played("tali-silver") }
            hero("arien", Team.RED, Hex(3, -5)) { discard("arien-blue-2a") }
            hero("wasp", Team.BLUE, Hex(1, -2))
            // Immune to attack actions of enemy heroes: Tali is a friend and Commune a skill (FAQ "conditional
            // immunity only applies when the sources of an effect matches the condition").
            effect(
                "arien",
                "arien-blue-2a",
                dev.quinton.goa.core.game.engine.Timing.THIS_TURN,
                dev.quinton.goa.core.game.engine.ActionKind.DEFENSE,
                data = mapOf("attacker" to "wasp"),
            )
            phase = PositionPhase.Resolving(next = "tali")
        }
        d.action("tali", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_OPTION, "arien")
    }

    @Test
    fun `Commune with Spirits - FAQ - a friend immune to Tali's action cannot be chosen, so nothing happens`() {
        val registry = Registry().also { r ->
            Heroes.all.forEach(r::module)
            // A test-only "you are immune to every action" effect (Tali's own team has no such card).
            r.behavior(
                "test-immune",
                object : Behavior {
                    override fun immune(g: Game, src: Src, q: AffectQuery): Boolean = q.targetId == src.owner
                },
            )
        }
        val engine = GameEngine(TestData.catalog, TestData.maps, TestData.matchRules, registry, allowIncomplete = true)
        val d = pos(engine = engine) {
            clearMinions()
            hero("tali", Team.RED, Hex(0, -2)) {
                played("tali-silver")
                discard("tali-red-1")
            }
            hero("wasp", Team.RED, Hex(3, -5)) { discard("wasp-blue-3a") }
            hero("arien", Team.BLUE, Hex(1, -2))
            effect("wasp", "wasp-blue-3a", Timing.THIS_TURN, ActionKind.SKILL, key = "test-immune")
            phase = PositionPhase.Resolving(next = "tali")
        }
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
    }

    @Test
    fun `Commune with Spirits - nothing of that color to perform, so the action stops`() {
        val d = pos {
            clearMinions()
            hero("tali", Team.RED, Hex(0, -2)) { played("tali-silver") } // full hand, empty discard
            hero("wasp", Team.RED, Hex(3, -5))
            hero("arien", Team.BLUE, Hex(8, -6)) { played("arien-blue-1") }
            phase = PositionPhase.Resolving(next = "tali")
        }
        d.action("tali", ActionType.PRIMARY)
        d.option(3) // green
        d.decline() // keep Glacial Barrier
        assertThat(d.texts().any { it.contains("stops") }).isTrue()
        d.expect(DecisionKind.CHOOSE_ACTION, "arien")
    }

    // ---- Winter Dagger / Spear / Scepter (red) -------------------------------------------------

    @Test
    fun `Winter Dagger - +3 Attack if this card is not in the discard`() {
        val d = pos {
            clearMinions()
            hero("tali", Team.RED, Hex(0, -2)) { played("tali-red-1") }
            hero("arien", Team.BLUE, Hex(1, -2)) { hand("arien-silver") } // Defense 3
            phase = PositionPhase.Resolving(next = "tali")
        }
        d.action("tali", ActionType.PRIMARY)
        d.defend("arien-silver")
        assertThat(d.texts().any { it.contains("Attack 5.") }).isTrue()
        assertThat(d.at("arien")).isNull()
    }

    @Test
    fun `Winter Dagger - an Attack item adds to the icon, the +3 is card text (p 16)`() {
        val d = pos {
            clearMinions()
            hero("tali", Team.RED, Hex(0, -2)) {
                played("tali-red-1")
                items("tali-green-2b") // +1 Attack
            }
            hero("arien", Team.BLUE, Hex(1, -2)) { hand("arien-silver") }
            phase = PositionPhase.Resolving(next = "tali")
        }
        d.action("tali", ActionType.PRIMARY)
        d.defend("arien-silver")
        assertThat(d.texts().any { it.contains("Attack 6.") }).isTrue()
    }

    @Test
    fun `Winter Spear and Winter Scepter - printed Attack 3 and 4, plus 3`() {
        for ((card, total) in listOf("tali-red-2a" to 6, "tali-red-3a" to 7)) {
            val d = pos {
                clearMinions()
                hero("tali", Team.RED, Hex(0, -2)) { played(card) }
                hero("arien", Team.BLUE, Hex(1, -2)) { hand("arien-silver") }
                phase = PositionPhase.Resolving(next = "tali")
            }
            d.action("tali", ActionType.PRIMARY)
            d.defend("arien-silver")
            assertThat(d.texts().any { it.contains("Attack $total.") }).isTrue()
        }
    }

    // ---- Spirit Wolf / Spirit Bear (red alternates) --------------------------------------------

    @Test
    fun `Spirit Wolf - Choose one, a unit in range or a hero adjacent`() {
        lateinit var m: String
        val d = pos {
            clearMinions()
            hero("tali", Team.RED, Hex(0, -2)) { played("tali-red-2b") } // Range 2, ranged
            hero("wasp", Team.BLUE, Hex(1, -2)) { hand("wasp-green-1") } // Stop Projectiles
            m = minion(Team.BLUE, MinionType.MELEE, Hex(2, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -5))
            minion(Team.RED, MinionType.MELEE, Hex(-1, -5))
            phase = PositionPhase.Resolving(next = "tali")
        }
        d.action("tali", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_OPTION, "tali")
        d.option(1) // a hero adjacent
        // The card is ranged, so even the adjacent attack is ranged (RULINGS 19): Stop Projectiles blocks.
        d.defend("wasp-green-1")
        assertThat(d.at("wasp")).isEqualTo(Hex(1, -2))
        assertThat(d.s.state.minion(m)).isNotNull()
    }

    @Test
    fun `Spirit Wolf - from the discard only the adjacent hero bullet is left`() {
        lateinit var m: String
        val d = pos {
            clearMinions()
            hero("tali", Team.RED, Hex(0, -2)) {
                played("tali-silver")
                discard("tali-red-2b")
            }
            hero("xargatha", Team.RED, Hex(3, -5))
            hero("arien", Team.BLUE, Hex(1, -2)) { hand("arien-silver") }
            m = minion(Team.BLUE, MinionType.MELEE, Hex(2, -2)) // in range: bullet one would allow it
            minion(Team.RED, MinionType.MELEE, Hex(-1, -5))
            phase = PositionPhase.Resolving(next = "tali")
        }
        d.action("tali", ActionType.PRIMARY)
        d.option(2) // Xargatha names red; Spirit Wolf holds the red slot, so there is nothing to discard
        // No "Choose one": the adjacent hero is the only bullet, so the attack goes to Arien.
        d.expect(DecisionKind.DEFEND, "arien")
        d.defend("arien-silver")
        assertThat(d.s.state.minion(m)).isNotNull()
    }

    @Test
    fun `Spirit Wolf - from the discard with no adjacent enemy hero it cannot be performed`() {
        val d = pos {
            clearMinions()
            hero("tali", Team.RED, Hex(0, -2)) {
                played("tali-silver")
                discard("tali-red-2b")
            }
            hero("xargatha", Team.RED, Hex(3, -5))
            minion(Team.BLUE, MinionType.MELEE, Hex(2, -2))
            minion(Team.RED, MinionType.MELEE, Hex(-1, -5))
            hero("arien", Team.BLUE, Hex(8, -6))
            phase = PositionPhase.Resolving(next = "tali")
        }
        d.action("tali", ActionType.PRIMARY)
        d.option(2)
        assertThat(d.texts().any { it.contains("stops") }).isTrue()
    }

    @Test
    fun `Spirit Bear - Choose one, or both, on different targets`() {
        lateinit var m: String
        val d = pos {
            clearMinions()
            hero("tali", Team.RED, Hex(0, -2)) { played("tali-red-3b") }
            hero("arien", Team.BLUE, Hex(1, -2)) { hand("arien-silver") }
            m = minion(Team.BLUE, MinionType.MELEE, Hex(2, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -5))
            minion(Team.RED, MinionType.MELEE, Hex(-1, -5))
            phase = PositionPhase.Resolving(next = "tali")
        }
        d.action("tali", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_OPTION, "tali")
        assertThat(d.d.options.count { it is dev.quinton.goa.core.game.Choice.Option }).isEqualTo(4)
        d.option(2) // both: in range first, then the adjacent hero
        assertThat(d.units()).containsExactly("arien", m)
        d.unit(m)
        // The second attack: a different target, the adjacent hero.
        d.defend("arien-silver")
        assertThat(d.s.state.minion(m)).isNull()
        assertThat(d.at("arien")).isEqualTo(Hex(1, -2)) // Attack 3 against Defense 3
    }

    // ---- Glacial Barrier / Wall of Frost / Pack Ice (green) ------------------------------------

    @Test
    fun `Glacial Barrier - place an Ice token in radius, enemy heroes next to it have -1 Initiative`() {
        val d = pos {
            clearMinions()
            hero("tali", Team.RED, Hex(0, -2)) { played("tali-green-1") } // radius 3
            hero("wasp", Team.RED, Hex(3, -3)) { played("wasp-red-1") } // 8
            hero("arien", Team.BLUE, Hex(3, -2)) { played("arien-blue-1") } // 9
            phase = PositionPhase.Resolving(next = "tali")
        }
        d.action("tali", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_SPACE, "tali")
        for (h in d.spaces()) assertThat(HexGrid.distance(h, Hex(0, -2))).isIn(1..3)
        d.space(2, -2) // next to both Arien and Wasp
        assertThat(d.tokens(TaliHero.ICE)).hasSize(1)
        assertThat(d.initiative("arien")).isEqualTo(8)
        assertThat(d.initiative("wasp")).isEqualTo(8) // friendly: unchanged
    }

    @Test
    fun `Wall of Frost - up to 2 Ice tokens, each adjacent one counts`() {
        val d = pos {
            clearMinions()
            hero("tali", Team.RED, Hex(0, -2)) { played("tali-green-2a") }
            hero("arien", Team.BLUE, Hex(3, -2)) { played("arien-blue-1") } // 9
            phase = PositionPhase.Resolving(next = "tali")
        }
        d.action("tali", ActionType.PRIMARY)
        d.space(2, -2)
        d.expect(DecisionKind.CHOOSE_SPACE, "tali")
        d.space(3, -3)
        assertThat(d.initiative("arien")).isEqualTo(7)
    }

    @Test
    fun `Wall of Frost - up to, so you may stop after one`() {
        val d = pos {
            clearMinions()
            hero("tali", Team.RED, Hex(0, -2)) { played("tali-green-2a") }
            hero("arien", Team.BLUE, Hex(3, -2)) { played("arien-blue-1") }
            phase = PositionPhase.Resolving(next = "tali")
        }
        d.action("tali", ActionType.PRIMARY)
        d.space(2, -2)
        d.decline()
        assertThat(d.tokens(TaliHero.ICE)).hasSize(1)
    }

    @Test
    fun `Pack Ice - up to 3, and with the supply used up one on the board is moved (p 18)`() {
        val d = pos {
            clearMinions()
            hero("tali", Team.RED, Hex(0, -2)) { played("tali-green-3a") }
            hero("arien", Team.BLUE, Hex(8, -6)) { played("arien-blue-1") }
            token(TaliHero.ICE, Hex(4, -7), owner = "tali", team = Team.RED)
            token(TaliHero.ICE, Hex(3, -7), owner = "tali", team = Team.RED)
            phase = PositionPhase.Resolving(next = "tali")
        }
        d.action("tali", ActionType.PRIMARY)
        d.space(1, -2) // the third token from the supply
        // The supply is empty: placing another first removes one of the two old ones.
        d.expect(DecisionKind.CONFIRM, "tali")
        d.yes()
        d.expect(DecisionKind.CHOOSE_TOKEN, "tali")
        assertThat(d.units()).hasSize(2) // the one just placed is not offered
        d.answer(d.d.options.first())
        d.space(0, -1)
        assertThat(d.tokens(TaliHero.ICE)).hasSize(3)
        d.expect(DecisionKind.CONFIRM, "tali")
        d.decline()
        assertThat(d.tokens(TaliHero.ICE).map { it.at }).containsAtLeast(Hex(1, -2), Hex(0, -1))
    }

    // ---- Ancestral Totem / Venerated Totem (green alternates) ----------------------------------

    @Test
    fun `Ancestral Totem - a friendly melee minion next to it is saved, the Totem is removed (FAQ - no coins)`() {
        lateinit var melee: String
        val d = pos {
            clearMinions()
            hero("tali", Team.RED, Hex(0, -2)) { played("tali-green-2b") } // Range 2
            hero("brogan", Team.BLUE, Hex(3, -2)) { played("brogan-gold") }
            melee = minion(Team.RED, MinionType.MELEE, Hex(2, -2))
            minion(Team.RED, MinionType.MELEE, Hex(-1, -5))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -5))
            phase = PositionPhase.Resolving(next = "tali")
        }
        d.action("tali", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_SPACE, "tali")
        for (h in d.spaces()) assertThat(HexGrid.distance(h, Hex(0, -2))).isIn(1..2)
        d.space(2, -3) // next to the melee minion
        d.action("brogan", ActionType.PRIMARY) // Onslaught on the minion
        assertThat(d.s.state.minion(melee)).isNotNull()
        assertThat(d.tokens(TaliHero.TOTEM)).isEmpty()
        assertThat(d.hero("brogan").coins).isEqualTo(0)
        assertThat(d.at("brogan")).isEqualTo(Hex(3, -2)) // nothing was defeated: no move into its space
    }

    @Test
    fun `Ancestral Totem - only melee minions are protected`() {
        lateinit var ranged: String
        val d = pos {
            clearMinions()
            hero("tali", Team.RED, Hex(0, -2)) { played("tali-green-2b") }
            hero("brogan", Team.BLUE, Hex(3, -2)) { played("brogan-gold") }
            ranged = minion(Team.RED, MinionType.RANGED, Hex(2, -2))
            minion(Team.RED, MinionType.MELEE, Hex(-1, -5))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -5))
            phase = PositionPhase.Resolving(next = "tali")
        }
        d.action("tali", ActionType.PRIMARY)
        d.space(2, -3)
        d.action("brogan", ActionType.PRIMARY)
        assertThat(d.s.state.minion(ranged)).isNull()
        assertThat(d.tokens(TaliHero.TOTEM)).hasSize(1)
    }

    @Test
    fun `Venerated Totem - any friendly minion, and Totem tokens are immune to enemy actions (no Clear)`() {
        lateinit var ranged: String
        val d = pos {
            clearMinions()
            hero("tali", Team.RED, Hex(1, -2)) { played("tali-green-3b") }
            hero("brogan", Team.BLUE, Hex(3, -2)) { played("brogan-gold") }
            ranged = minion(Team.RED, MinionType.RANGED, Hex(2, -2))
            minion(Team.RED, MinionType.MELEE, Hex(-1, -5))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -5))
            phase = PositionPhase.Resolving(next = "tali")
        }
        d.action("tali", ActionType.PRIMARY)
        d.space(3, -3) // next to Brogan and to the ranged minion
        // Brogan's gold has an Attack action, the Totem is adjacent, yet he may not Clear it.
        assertThat(d.actions()).doesNotContain(ActionType.CLEAR)
        d.action("brogan", ActionType.PRIMARY)
        assertThat(d.s.state.minion(ranged)).isNotNull()
        assertThat(d.tokens(TaliHero.TOTEM)).isEmpty()
    }

    // ---- Cold Snap / Snowstorm / Blizzard (blue) -----------------------------------------------

    @Test
    fun `Cold Snap - move each enemy unit in radius 1 space in a direction, if able`() {
        lateinit var front: String
        lateinit var back: String
        lateinit var blocked: String
        lateinit var friend: String
        val d = pos {
            clearMinions()
            hero("tali", Team.RED, Hex(0, -2)) { played("tali-blue-1") } // radius 2
            hero("arien", Team.BLUE, Hex(8, -6))
            back = minion(Team.BLUE, MinionType.MELEE, Hex(1, -2))
            front = minion(Team.BLUE, MinionType.MELEE, Hex(2, -2)) // in front of the other, east
            blocked = minion(Team.BLUE, MinionType.MELEE, Hex(0, -3)) // east of it is a rock
            friend = minion(Team.RED, MinionType.MELEE, Hex(-1, -2))
            phase = PositionPhase.Resolving(next = "tali")
        }
        d.action("tali", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_OPTION, "tali")
        d.option(0) // east
        assertThat(d.at(front)).isEqualTo(Hex(3, -2))
        assertThat(d.at(back)).isEqualTo(Hex(2, -2)) // moved into the space the other left
        assertThat(d.at(blocked)).isEqualTo(Hex(0, -3))
        assertThat(d.at(friend)).isEqualTo(Hex(-1, -2))
    }

    @Test
    fun `Cold Snap - a heavy minion guarded by its friends is immune and stays (p 13)`() {
        lateinit var heavy: String
        val d = pos {
            clearMinions()
            hero("tali", Team.RED, Hex(0, -2)) { played("tali-blue-1") }
            hero("arien", Team.BLUE, Hex(8, -6))
            heavy = minion(Team.BLUE, MinionType.HEAVY, Hex(1, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -5)) // in the Battle Zone: the heavy is immune
            minion(Team.RED, MinionType.MELEE, Hex(-1, -5))
            phase = PositionPhase.Resolving(next = "tali")
        }
        d.action("tali", ActionType.PRIMARY)
        d.option(0)
        assertThat(d.at(heavy)).isEqualTo(Hex(1, -2))
    }

    @Test
    fun `Snowstorm - radius 3, heroes too`() {
        val d = pos {
            clearMinions()
            hero("tali", Team.RED, Hex(0, -2)) { played("tali-blue-2a") }
            hero("arien", Team.BLUE, Hex(3, -2))
            phase = PositionPhase.Resolving(next = "tali")
        }
        d.action("tali", ActionType.PRIMARY)
        d.option(5) // south-east
        assertThat(d.at("arien")).isEqualTo(Hex(3, -1))
    }

    @Test
    fun `Blizzard - performed from the discard, End of turn - may repeat once`() {
        val d = pos {
            clearMinions()
            hero("tali", Team.RED, Hex(0, -2)) {
                played("tali-silver")
                discard("tali-blue-3a")
            }
            hero("wasp", Team.RED, Hex(3, -5))
            hero("arien", Team.BLUE, Hex(2, -2))
            phase = PositionPhase.Resolving(next = "tali")
        }
        d.action("tali", ActionType.PRIMARY)
        d.option(4) // Wasp names blue
        d.option(0) // Blizzard: east
        assertThat(d.at("arien")).isEqualTo(Hex(3, -2))
        // End of turn.
        d.expect(DecisionKind.CONFIRM, "tali")
        d.yes()
        d.option(0)
        assertThat(d.at("arien")).isEqualTo(Hex(4, -2))
        d.expect(DecisionKind.PLAY_CARD) // the repeat does not repeat again
    }

    @Test
    fun `Blizzard - played normally it is not in the discard, so no End of turn repeat`() {
        val d = pos {
            clearMinions()
            hero("tali", Team.RED, Hex(0, -2)) { played("tali-blue-3a") }
            hero("arien", Team.BLUE, Hex(2, -2))
            phase = PositionPhase.Resolving(next = "tali")
        }
        d.action("tali", ActionType.PRIMARY)
        d.option(0)
        d.expect(DecisionKind.PLAY_CARD)
    }

    // ---- Guardian Spirit / Warrior Spirit (blue alternates) ------------------------------------

    @Test
    fun `Guardian Spirit - up to 1 friendly hero in radius may retrieve`() {
        val d = pos {
            clearMinions()
            hero("tali", Team.RED, Hex(0, -2)) { played("tali-blue-2b") } // radius 4
            hero("wasp", Team.RED, Hex(3, -5)) { hand("wasp-gold") }
            hero("arien", Team.BLUE, Hex(8, -6))
            phase = PositionPhase.Resolving(next = "tali")
        }
        d.action("tali", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_UNIT, "tali")
        d.unit("wasp")
        d.expect(DecisionKind.CHOOSE_CARD, "wasp")
        d.card("wasp-red-1")
        assertThat(d.zoneOf("wasp", "wasp-red-1")).isEqualTo(Zone.HAND)
    }

    @Test
    fun `Guardian Spirit - in the discard, retrieve it`() {
        val d = pos {
            clearMinions()
            hero("tali", Team.RED, Hex(0, -2)) {
                played("tali-silver")
                discard("tali-blue-2b")
            }
            hero("wasp", Team.RED, Hex(3, -5)) // nothing discarded: no retrieve to offer
            hero("arien", Team.BLUE, Hex(8, -6))
            phase = PositionPhase.Resolving(next = "tali")
        }
        d.action("tali", ActionType.PRIMARY)
        d.option(4) // blue
        assertThat(d.zoneOf("tali", "tali-blue-2b")).isEqualTo(Zone.HAND)
    }

    @Test
    fun `Warrior Spirit - in the discard, you may retrieve a discarded card`() {
        val d = pos {
            clearMinions()
            hero("tali", Team.RED, Hex(0, -2)) {
                played("tali-silver")
                discard("tali-blue-3b", "tali-red-1")
            }
            hero("wasp", Team.RED, Hex(3, -5))
            hero("arien", Team.BLUE, Hex(8, -6))
            phase = PositionPhase.Resolving(next = "tali")
        }
        d.action("tali", ActionType.PRIMARY)
        d.option(4) // blue
        d.expect(DecisionKind.CHOOSE_CARD, "tali")
        assertThat(d.cards()).containsExactly("tali-blue-3b", "tali-red-1")
        d.card("tali-red-1")
        assertThat(d.zoneOf("tali", "tali-red-1")).isEqualTo(Zone.HAND)
        assertThat(d.zoneOf("tali", "tali-blue-3b")).isEqualTo(Zone.DISCARD)
    }

    // ---- Reign of Winter (Ultimate) -----------------------------------------------------------

    @Test
    fun `Reign of Winter - your basic attack defeats a minion, an enemy hero in radius discards your color`() {
        lateinit var m: String
        val d = pos {
            clearMinions()
            hero("tali", Team.RED, Hex(0, -2)) {
                played("tali-gold")
                ultimate()
            }
            hero("arien", Team.BLUE, Hex(3, -2)) { hand("arien-gold", "arien-blue-1") }
            m = minion(Team.BLUE, MinionType.MELEE, Hex(1, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -5))
            minion(Team.RED, MinionType.MELEE, Hex(-1, -5))
            phase = PositionPhase.Resolving(next = "tali")
        }
        d.action("tali", ActionType.PRIMARY)
        assertThat(d.s.state.minion(m)).isNull()
        d.expect(DecisionKind.CHOOSE_OPTION, "tali")
        d.option(4) // blue
        assertThat(d.zoneOf("arien", "arien-blue-1")).isEqualTo(Zone.DISCARD)
        assertThat(d.zoneOf("arien", "arien-gold")).isEqualTo(Zone.HAND)
    }

    @Test
    fun `Reign of Winter - a non-basic attack gives nothing`() {
        val d = pos {
            clearMinions()
            hero("tali", Team.RED, Hex(0, -2)) {
                played("tali-red-1")
                ultimate()
            }
            hero("arien", Team.BLUE, Hex(3, -2)) { hand("arien-gold", "arien-blue-1") }
            minion(Team.BLUE, MinionType.MELEE, Hex(1, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -5))
            minion(Team.RED, MinionType.MELEE, Hex(-1, -5))
            phase = PositionPhase.Resolving(next = "tali")
        }
        d.action("tali", ActionType.PRIMARY)
        assertThat(d.hand("arien")).hasSize(2)
    }

    private fun Driver.hand(hero: String) = this.hero(hero).cards.filter { it.zone == Zone.HAND }

    // ---- audit E regressions --------------------------------------------------------------------

    @Test
    fun `Reign of Winter - a minion defeated but not removed (Brogan's Shield) is still defeated`() {
        lateinit var m: String
        val d = pos {
            clearMinions()
            hero("tali", Team.RED, Hex(0, -5)) {
                played("tali-gold")
                ultimate()
            }
            hero("brogan", Team.BLUE, Hex(2, -5)) {
                resolved(1, "brogan-green-1")
                hand("brogan-silver", "brogan-blue-1")
            }
            m = minion(Team.BLUE, MinionType.MELEE, Hex(1, -5))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -7))
            minion(Team.RED, MinionType.MELEE, Hex(-1, -2))
            turn = 2
            effect("brogan", "brogan-green-1", Timing.THIS_ROUND, ActionKind.SKILL, madeTurn = 1)
            phase = PositionPhase.Resolving(next = "tali")
        }
        d.action("tali", ActionType.PRIMARY) // Ice Blast on the only adjacent unit, the minion
        d.expect(DecisionKind.CHOOSE_CARD, "brogan")
        d.card("brogan-silver")
        assertThat(d.s.state.minion(m)).isNotNull()
        // "Each time after you defeat a minion with your basic attack": Tali names the colour Brogan discards.
        d.expect(DecisionKind.CHOOSE_OPTION, "tali")
    }
}
