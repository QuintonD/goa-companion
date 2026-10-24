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
import dev.quinton.goa.core.game.engine.cardValue
import dev.quinton.goa.core.game.pos
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.catalog.Stat
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

/** Min the Dragonmonk: every card, clause by clause (text from `catalog/heroes/min.json`). */
class MinTest {
    private fun Driver.tokens(kind: String) = s.state.pieces.filter { it.kind == kind && it.at != null }

    private fun Driver.value(hero: String, card: String, stat: Stat): Int? =
        Game(ANY_HERO_ENGINE.data, s.state, emptyList(), RunMode.PROBE).cardValue(hero, card, stat)

    // ---- Fast as Lightning (gold) / Chop Down (Handicap) --------------------------------------

    @Test
    fun `Fast as Lightning - target a unit in range, then apply your resolved red card's After the attack text`() {
        lateinit var m: String
        val d = pos {
            clearMinions()
            hero("min", Team.RED, Hex(0, -2)) {
                played("min-gold")
                resolved(1, "min-red-1") // Crane Stance: push an enemy unit adjacent up to 3
            }
            hero("arien", Team.BLUE, Hex(-1, -2))
            m = minion(Team.BLUE, MinionType.MELEE, Hex(1, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -5))
            minion(Team.RED, MinionType.MELEE, Hex(-1, -5))
            turn = 2
            phase = PositionPhase.Resolving(next = "min")
        }
        d.action("min", ActionType.PRIMARY)
        assertThat(d.units()).containsExactly("arien", m) // Range 1
        d.unit(m)
        assertThat(d.s.state.minion(m)).isNull()
        // Crane Stance's text: Arien is the only enemy unit adjacent now.
        d.expect(DecisionKind.CHOOSE_NUMBER, "min")
        d.number(2)
        assertThat(d.at("arien")).isEqualTo(Hex(-3, -2))
    }

    @Test
    fun `Fast as Lightning - a discarded Viper Stance uses that card's radius (4)`() {
        lateinit var bomb: String
        val d = pos {
            clearMinions()
            hero("min", Team.RED, Hex(0, -2)) {
                played("min-gold")
                discard("min-red-2b")
            }
            hero("arien", Team.BLUE, Hex(8, -6))
            minion(Team.BLUE, MinionType.MELEE, Hex(1, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -5))
            minion(Team.RED, MinionType.MELEE, Hex(-1, -5))
            bomb = token(MinHero.SMOKE, Hex(4, -2), owner = "min", team = Team.RED) // 4 away
            phase = PositionPhase.Resolving(next = "min")
        }
        d.action("min", ActionType.PRIMARY)
        d.expect(DecisionKind.CONFIRM, "min")
        d.yes()
        assertThat(d.at("min")).isEqualTo(Hex(4, -2))
        assertThat(d.at(bomb)).isEqualTo(Hex(0, -2))
    }

    @Test
    fun `Fast as Lightning - Range items count, and with no red card resolved or discarded nothing follows`() {
        lateinit var m: String
        val d = pos {
            clearMinions()
            hero("min", Team.RED, Hex(0, -2)) {
                played("min-gold")
                items("min-green-3b") // +1 Range
            }
            hero("arien", Team.BLUE, Hex(8, -6)) { played("arien-blue-1") }
            m = minion(Team.BLUE, MinionType.MELEE, Hex(2, -2)) // 2 away
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -5))
            minion(Team.RED, MinionType.MELEE, Hex(-1, -5))
            phase = PositionPhase.Resolving(next = "min")
        }
        d.action("min", ActionType.PRIMARY)
        assertThat(d.s.state.minion(m)).isNull()
        d.expect(DecisionKind.CHOOSE_ACTION, "arien") // the red card is in hand: no After the attack text
    }

    @Test
    fun `Chop Down - Target a hero adjacent to you`() {
        val d = pos {
            clearMinions()
            hero("min", Team.RED, Hex(0, -2)) { played("min-handicap") }
            hero("arien", Team.BLUE, Hex(8, -6))
            minion(Team.BLUE, MinionType.MELEE, Hex(1, -2))
            minion(Team.RED, MinionType.MELEE, Hex(-1, -5))
            phase = PositionPhase.Resolving(next = "min")
        }
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
    }

    @Test
    fun `Chop Down - After the attack, the red card's text`() {
        val d = pos {
            clearMinions()
            hero("min", Team.RED, Hex(0, -2)) {
                played("min-handicap")
                discard("min-red-1")
            }
            hero("arien", Team.BLUE, Hex(1, -2)) { hand("arien-blue-1") }
            phase = PositionPhase.Resolving(next = "min")
        }
        d.action("min", ActionType.PRIMARY)
        d.defend("arien-blue-1")
        d.number(1) // Crane Stance's push
        assertThat(d.at("arien")).isEqualTo(Hex(2, -2))
    }

    // ---- Smoke Bomb (silver) ------------------------------------------------------------------

    @Test
    fun `Smoke Bomb - place it in radius, enemy heroes cannot target Min across it`() {
        val d = pos {
            clearMinions()
            hero("min", Team.RED, Hex(0, -2)) { played("min-silver") } // radius 3
            hero("arien", Team.BLUE, Hex(2, -2)) { played("arien-red-2b") } // Rogue Wave, Range 2
            phase = PositionPhase.Resolving(next = "min")
        }
        d.action("min", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_SPACE, "min")
        for (h in d.spaces()) assertThat(HexGrid.distance(h, Hex(0, -2))).isIn(1..3)
        d.space(1, -2) // between Min and Arien
        assertThat(d.tokens(MinHero.SMOKE)).hasSize(1)
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
    }

    @Test
    fun `Smoke Bomb - or another unit on the far side, while units off that line stay targetable`() {
        lateinit var behind: String
        lateinit var aside: String
        val d = pos {
            clearMinions()
            hero("min", Team.RED, Hex(0, -5))
            hero("arien", Team.BLUE, Hex(3, -2)) { played("arien-red-2b") }
            token(MinHero.SMOKE, Hex(2, -2), owner = "min", team = Team.RED)
            behind = minion(Team.RED, MinionType.MELEE, Hex(1, -2)) // the bomb is between
            aside = minion(Team.RED, MinionType.MELEE, Hex(3, -4)) // nothing between
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -7))
            phase = PositionPhase.Resolving(next = "arien")
        }
        d.action("arien", ActionType.PRIMARY)
        assertThat(d.s.state.minion(aside)).isNull()
        assertThat(d.s.state.minion(behind)).isNotNull()
    }

    @Test
    fun `Smoke Bomb - there is only one, so placing it again moves it (p 18)`() {
        val d = pos {
            clearMinions()
            hero("min", Team.RED, Hex(0, -2)) { played("min-silver") }
            hero("arien", Team.BLUE, Hex(8, -6))
            token(MinHero.SMOKE, Hex(4, -7), owner = "min", team = Team.RED)
            phase = PositionPhase.Resolving(next = "min")
        }
        d.action("min", ActionType.PRIMARY)
        d.space(1, -2)
        assertThat(d.tokens(MinHero.SMOKE).map { it.at }).containsExactly(Hex(1, -2))
    }

    // ---- the stances (red) --------------------------------------------------------------------

    @Test
    fun `Crane Stance - After the attack, push an enemy unit adjacent to you up to 3 spaces`() {
        lateinit var m: String
        val d = pos {
            clearMinions()
            hero("min", Team.RED, Hex(0, -2)) { played("min-red-1") }
            hero("arien", Team.BLUE, Hex(-1, -2))
            m = minion(Team.BLUE, MinionType.MELEE, Hex(1, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -5))
            minion(Team.RED, MinionType.MELEE, Hex(-1, -5))
            phase = PositionPhase.Resolving(next = "min")
        }
        d.action("min", ActionType.PRIMARY)
        d.unit(m)
        d.expect(DecisionKind.CHOOSE_NUMBER, "min")
        assertThat(d.d.options).hasSize(4)
        d.number(3)
        assertThat(d.at("arien")).isEqualTo(Hex(-4, -2))
    }

    @Test
    fun `Tiger Stance - you may move 1 space to a space adjacent to the target, then push`() {
        val d = pos {
            clearMinions()
            hero("min", Team.RED, Hex(0, -2)) { played("min-red-2a") }
            hero("arien", Team.BLUE, Hex(1, -2)) { hand("arien-blue-1") }
            phase = PositionPhase.Resolving(next = "min")
        }
        d.action("min", ActionType.PRIMARY)
        d.defend("arien-blue-1") // Defense 5 against Attack 4
        d.expect(DecisionKind.CHOOSE_SPACE, "min")
        assertThat(d.spaces()).containsExactly(Hex(0, -1)) // (1,-3) is a rock
        d.space(0, -1)
        d.number(1)
        assertThat(d.at("arien")).isEqualTo(Hex(2, -3))
    }

    @Test
    fun `Viper Stance - After the attack, you may swap with a Smoke bomb in radius`() {
        lateinit var bomb: String
        val d = pos {
            clearMinions()
            hero("min", Team.RED, Hex(0, -2)) { played("min-red-2b") } // radius 4
            hero("arien", Team.BLUE, Hex(8, -6))
            minion(Team.BLUE, MinionType.MELEE, Hex(1, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -5))
            minion(Team.RED, MinionType.MELEE, Hex(-1, -5))
            bomb = token(MinHero.SMOKE, Hex(3, -5), owner = "min", team = Team.RED)
            phase = PositionPhase.Resolving(next = "min")
        }
        d.action("min", ActionType.PRIMARY)
        d.expect(DecisionKind.CONFIRM, "min")
        d.yes()
        assertThat(d.at("min")).isEqualTo(Hex(3, -5))
        assertThat(d.at(bomb)).isEqualTo(Hex(0, -2))
    }

    @Test
    fun `Dragon Stance - you may move 1 or 2 spaces to a space adjacent to the target`() {
        val d = pos {
            clearMinions()
            hero("min", Team.RED, Hex(0, -2)) { played("min-red-3a") }
            hero("arien", Team.BLUE, Hex(1, -2)) { hand("arien-red-1") } // Defense 6 against Attack 4
            phase = PositionPhase.Resolving(next = "min")
        }
        d.action("min", ActionType.PRIMARY)
        d.defend("arien-red-1")
        assertThat(d.spaces()).containsExactly(Hex(0, -1), Hex(1, -1))
        d.space(1, -1)
        assertThat(d.at("min")).isEqualTo(Hex(1, -1))
    }

    @Test
    fun `Cobra Stance - swap with the Smoke bomb, then you may place it into a space in radius`() {
        lateinit var bomb: String
        val d = pos {
            clearMinions()
            hero("min", Team.RED, Hex(0, -2)) { played("min-red-3b") }
            hero("arien", Team.BLUE, Hex(8, -6))
            minion(Team.BLUE, MinionType.MELEE, Hex(1, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -5))
            minion(Team.RED, MinionType.MELEE, Hex(-1, -5))
            bomb = token(MinHero.SMOKE, Hex(3, -5), owner = "min", team = Team.RED)
            phase = PositionPhase.Resolving(next = "min")
        }
        d.action("min", ActionType.PRIMARY)
        d.yes()
        d.expect(DecisionKind.CHOOSE_SPACE, "min")
        for (h in d.spaces()) assertThat(HexGrid.distance(h, Hex(3, -5))).isIn(1..4)
        d.space(2, -5)
        assertThat(d.at(bomb)).isEqualTo(Hex(2, -5))
    }

    // ---- Poof! / Vanish / Ruse (green) --------------------------------------------------------

    @Test
    fun `Poof - swap with a Smoke bomb in range, if you do block the attack`() {
        lateinit var bomb: String
        val d = pos {
            clearMinions()
            hero("min", Team.RED, Hex(0, -2)) { hand("min-green-1") } // Range 3
            hero("arien", Team.BLUE, Hex(1, -2)) { played("arien-gold") }
            bomb = token(MinHero.SMOKE, Hex(0, -5), owner = "min", team = Team.RED) // 3 away
            phase = PositionPhase.Resolving(next = "arien")
        }
        d.action("arien", ActionType.PRIMARY)
        d.defend("min-green-1")
        assertThat(d.at("min")).isEqualTo(Hex(0, -5))
        assertThat(d.at(bomb)).isEqualTo(Hex(0, -2))
    }

    @Test
    fun `Poof - no Smoke bomb in range, so the block fails`() {
        val d = pos {
            clearMinions()
            hero("min", Team.RED, Hex(0, -2)) { hand("min-green-1") }
            hero("arien", Team.BLUE, Hex(1, -2)) { played("arien-gold") }
            token(MinHero.SMOKE, Hex(0, -6), owner = "min", team = Team.RED) // 4 away
            phase = PositionPhase.Resolving(next = "arien")
        }
        d.action("arien", ActionType.PRIMARY)
        d.defend("min-green-1")
        assertThat(d.at("min")).isNull()
    }

    @Test
    fun `Vanish - Range 4`() {
        val d = pos {
            clearMinions()
            hero("min", Team.RED, Hex(0, -2)) { hand("min-green-2a") }
            hero("arien", Team.BLUE, Hex(1, -2)) { played("arien-gold") }
            token(MinHero.SMOKE, Hex(0, -6), owner = "min", team = Team.RED)
            phase = PositionPhase.Resolving(next = "arien")
        }
        d.action("arien", ActionType.PRIMARY)
        d.defend("min-green-2a")
        assertThat(d.at("min")).isEqualTo(Hex(0, -6))
    }

    @Test
    fun `Ruse - block by swapping, then you may place the Smoke bomb into a space in range`() {
        lateinit var bomb: String
        val d = pos {
            clearMinions()
            hero("min", Team.RED, Hex(0, -2)) { hand("min-green-3a") }
            hero("arien", Team.BLUE, Hex(1, -2)) { played("arien-gold") }
            bomb = token(MinHero.SMOKE, Hex(0, -6), owner = "min", team = Team.RED)
            phase = PositionPhase.Resolving(next = "arien")
        }
        d.action("arien", ActionType.PRIMARY)
        d.defend("min-green-3a")
        d.expect(DecisionKind.CHOOSE_SPACE, "min")
        for (h in d.spaces()) assertThat(HexGrid.distance(h, Hex(0, -6))).isIn(1..4)
        d.space(2, -5)
        assertThat(d.at("min")).isEqualTo(Hex(0, -6))
        assertThat(d.at(bomb)).isEqualTo(Hex(2, -5))
    }

    // ---- Trip Mine / Cluster Mine / Minefield (blue) ------------------------------------------

    @Test
    fun `Trip Mine - 1 blast and 1 dud facedown, units move through, an enemy hero moving through a blast discards`() {
        val d = pos {
            clearMinions()
            hero("min", Team.RED, Hex(0, -2)) { played("min-blue-1") } // radius 3
            hero("arien", Team.BLUE, Hex(3, -2)) {
                played("arien-blue-1")
                hand("arien-gold")
            }
            phase = PositionPhase.Resolving(next = "min")
        }
        d.action("min", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_SPACE, "min")
        d.space(2, -2)
        d.space(2, -3)
        d.expect(DecisionKind.CHOOSE_SPACE, "min") // which one is the blast
        assertThat(d.spaces()).containsExactly(Hex(2, -2), Hex(2, -3))
        d.space(2, -2)
        val mines = d.tokens(MinHero.BLAST) + d.tokens(MinHero.DUD)
        assertThat(mines).hasSize(2)
        assertThat(mines.all { it.faceDown && it.label == "Mine" }).isTrue()
        // The other team sees facedown tokens only.
        assertThat(d.s.view("arien").pieces.map { it.kind }.toSet()).containsExactly("facedown_token")
        assertThat(d.texts().none { it.contains("blast") || it.contains("dud") }).isTrue()
        // Arien moves through the blast: asked space by space.
        d.action("arien", ActionType.MOVEMENT)
        d.space(2, -2)
        d.space(1, -2)
        d.decline() // stop here
        assertThat(d.tokens(MinHero.BLAST)).isEmpty()
        assertThat(d.tokens(MinHero.DUD)).hasSize(1)
        assertThat(d.zoneOf("arien", "arien-gold")).isEqualTo(Zone.DISCARD)
    }

    @Test
    fun `Trip Mine - a dud is only removed, and friendly heroes pass without setting them off`() {
        val d = pos {
            clearMinions()
            hero("min", Team.RED, Hex(0, -5))
            hero("wasp", Team.RED, Hex(3, -2)) { played("wasp-blue-1") } // Min's friend
            hero("arien", Team.BLUE, Hex(3, -5)) {
                played("arien-blue-1")
                hand("arien-gold")
            }
            token(MinHero.BLAST, Hex(2, -2), owner = "min", team = Team.RED, faceDown = true)
            token(MinHero.DUD, Hex(3, -6), owner = "min", team = Team.RED, faceDown = true)
            phase = PositionPhase.Resolving(next = "wasp")
        }
        d.action("wasp", ActionType.MOVEMENT)
        d.space(2, -2)
        d.space(1, -2)
        d.decline()
        assertThat(d.tokens(MinHero.BLAST)).hasSize(1) // a friend: nothing happens
        d.action("arien", ActionType.MOVEMENT)
        d.space(3, -6)
        d.space(3, -7)
        d.decline()
        assertThat(d.tokens(MinHero.DUD)).isEmpty()
        assertThat(d.zoneOf("arien", "arien-gold")).isEqualTo(Zone.HAND)
    }

    @Test
    fun `Cluster Mine - 3 tokens, 1 blast and 2 duds`() {
        val d = pos {
            clearMinions()
            hero("min", Team.RED, Hex(0, -2)) { played("min-blue-2a") }
            hero("arien", Team.BLUE, Hex(8, -6))
            phase = PositionPhase.Resolving(next = "min")
        }
        d.action("min", ActionType.PRIMARY)
        d.space(1, -2)
        d.space(2, -2)
        d.space(3, -2)
        d.space(2, -2) // the blast
        assertThat(d.tokens(MinHero.BLAST).map { it.at }).containsExactly(Hex(2, -2))
        assertThat(d.tokens(MinHero.DUD).map { it.at }).containsExactly(Hex(1, -2), Hex(3, -2))
    }

    @Test
    fun `Minefield - 3 tokens, 2 blasts and 1 dud`() {
        val d = pos {
            clearMinions()
            hero("min", Team.RED, Hex(0, -2)) { played("min-blue-3a") }
            hero("arien", Team.BLUE, Hex(8, -6))
            phase = PositionPhase.Resolving(next = "min")
        }
        d.action("min", ActionType.PRIMARY)
        d.space(1, -2)
        d.space(2, -2)
        d.space(3, -2)
        d.expect(DecisionKind.CHOOSE_SPACE, "min")
        d.space(3, -2) // the dud
        assertThat(d.tokens(MinHero.BLAST).map { it.at }).containsExactly(Hex(1, -2), Hex(2, -2))
        assertThat(d.tokens(MinHero.DUD).map { it.at }).containsExactly(Hex(3, -2))
    }

    @Test
    fun `Mines - an enemy hero pushed through a blast discards (p 18 - pushed through what can be moved through)`() {
        lateinit var m: String
        val d = pos {
            clearMinions()
            hero("min", Team.RED, Hex(0, -2)) { played("min-red-1") }
            hero("arien", Team.BLUE, Hex(-1, -2)) { hand("arien-gold") }
            m = minion(Team.BLUE, MinionType.MELEE, Hex(1, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -5))
            minion(Team.RED, MinionType.MELEE, Hex(-1, -5))
            token(MinHero.BLAST, Hex(-2, -2), owner = "min", team = Team.RED, faceDown = true)
            phase = PositionPhase.Resolving(next = "min")
        }
        d.action("min", ActionType.PRIMARY)
        d.unit(m)
        d.number(3)
        assertThat(d.at("arien")).isEqualTo(Hex(-4, -2))
        assertThat(d.tokens(MinHero.BLAST)).isEmpty()
        assertThat(d.zoneOf("arien", "arien-gold")).isEqualTo(Zone.DISCARD)
    }

    // ---- Death Grenade / Holy Death Grenade (blue alternates) ---------------------------------

    @Test
    fun `Death Grenade - End of turn, up to 1 enemy hero adjacent discards a card, or is defeated, then remove it`() {
        val d = pos {
            clearMinions()
            hero("min", Team.RED, Hex(0, -2)) { played("min-blue-2b") } // radius 2
            hero("arien", Team.BLUE, Hex(3, -2)) { hand() }
            hero("brogan", Team.BLUE, Hex(3, -5)) { hand("brogan-gold") } // not next to it
            phase = PositionPhase.Resolving(next = "min")
        }
        d.action("min", ActionType.PRIMARY)
        for (h in d.spaces()) assertThat(HexGrid.distance(h, Hex(0, -2))).isIn(1..2)
        d.space(2, -2)
        assertThat(d.tokens(MinHero.GRENADE)).hasSize(1)
        // End of turn.
        d.expect(DecisionKind.CHOOSE_UNIT, "min")
        assertThat(d.units()).containsExactly("arien")
        d.unit("arien")
        assertThat(d.at("arien")).isNull() // no card: defeated
        assertThat(d.tokens(MinHero.GRENADE)).isEmpty()
        assertThat(d.zoneOf("brogan", "brogan-gold")).isEqualTo(Zone.HAND)
    }

    @Test
    fun `Death Grenade - FAQ - a hero immune this turn is still immune at the end of turn`() {
        val registry = Registry().also { r ->
            Heroes.all.forEach(r::module)
            // A test-only "This turn: you are immune" effect.
            r.behavior(
                "test-immune",
                object : Behavior {
                    override fun immune(g: Game, src: Src, q: AffectQuery): Boolean =
                        q.targetId == src.owner && q.actorId != null && g.isEnemy(src.owner!!, q.actorId!!)
                },
            )
        }
        val engine = GameEngine(TestData.catalog, TestData.maps, TestData.matchRules, registry, allowIncomplete = true)
        val d = pos(engine = engine) {
            clearMinions()
            hero("min", Team.RED, Hex(0, -2)) { played("min-blue-2b") }
            hero("arien", Team.BLUE, Hex(3, -2)) {
                hand()
                discard("arien-blue-3a")
            }
            effect("arien", "arien-blue-3a", Timing.THIS_TURN, ActionKind.DEFENSE, key = "test-immune")
            phase = PositionPhase.Resolving(next = "min")
        }
        d.action("min", ActionType.PRIMARY)
        d.space(2, -2)
        // No question: Arien cannot be chosen.
        assertThat(d.at("arien")).isEqualTo(Hex(3, -2))
        assertThat(d.tokens(MinHero.GRENADE)).isEmpty()
    }

    @Test
    fun `Holy Death Grenade - up to 2 enemy heroes adjacent discard a card, or are defeated`() {
        val d = pos {
            clearMinions()
            hero("min", Team.RED, Hex(0, -2)) { played("min-blue-3b") }
            hero("arien", Team.BLUE, Hex(3, -2)) { hand("arien-gold") }
            hero("brogan", Team.BLUE, Hex(3, -3)) { hand() }
            phase = PositionPhase.Resolving(next = "min")
        }
        d.action("min", ActionType.PRIMARY)
        d.space(2, -2) // next to both
        d.unit("arien")
        d.unit("brogan")
        assertThat(d.zoneOf("arien", "arien-gold")).isEqualTo(Zone.DISCARD)
        assertThat(d.at("brogan")).isNull()
    }

    // ---- Inner Strength / Perfect Self (green alternates) -------------------------------------

    @Test
    fun `Inner Strength - This round, double your item bonuses`() {
        val d = pos {
            clearMinions()
            hero("min", Team.RED, Hex(0, -2)) {
                played("min-green-2b")
                items("min-red-2a") // +1 Initiative
            }
            hero("arien", Team.BLUE, Hex(8, -6))
            phase = PositionPhase.Resolving(next = "min")
        }
        assertThat(d.value("min", "min-gold", Stat.INITIATIVE)).isEqualTo(14)
        d.action("min", ActionType.PRIMARY)
        assertThat(d.value("min", "min-gold", Stat.INITIATIVE)).isEqualTo(15)
        assertThat(d.s.state.effects.single { it.cardId == "min-green-2b" }.timing).isEqualTo(Timing.THIS_ROUND)
    }

    @Test
    fun `Inner Strength - the doubling lasts the whole round, not only this turn`() {
        val d = pos {
            clearMinions()
            hero("min", Team.RED, Hex(0, -2)) {
                played("min-green-2b")
                items("min-red-2a")
            }
            hero("arien", Team.BLUE, Hex(8, -6))
            phase = PositionPhase.Resolving(next = "min")
        }
        d.action("min", ActionType.PRIMARY)
        d.expect(DecisionKind.PLAY_CARD) // turn 2
        assertThat(d.s.state.turn).isEqualTo(2)
        assertThat(d.value("min", "min-gold", Stat.INITIATIVE)).isEqualTo(15)
    }

    @Test
    fun `Perfect Self - Choose one, or both, take a Tier II card from your deck as a permanent item`() {
        val d = pos {
            clearMinions()
            hero("min", Team.RED, Hex(0, -2)) {
                played("min-green-3b")
                items("min-red-2a") // +1 Initiative
                level = 5
            }
            hero("arien", Team.BLUE, Hex(8, -6))
            phase = PositionPhase.Resolving(next = "min")
        }
        d.action("min", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_OPTION, "min")
        d.option(2) // both
        d.expect(DecisionKind.CHOOSE_CARD, "min")
        assertThat(d.cards()).contains("min-blue-2b")
        assertThat(d.cards()).doesNotContain("min-red-2a") // already an item
        d.card("min-blue-2b") // +1 Initiative
        assertThat(d.zoneOf("min", "min-blue-2b")).isEqualTo(Zone.ITEM)
        // Two Initiative items, doubled.
        assertThat(d.value("min", "min-gold", Stat.INITIATIVE)).isEqualTo(13 + 4)
    }

    // ---- Flurry of Blows (Ultimate) -----------------------------------------------------------

    @Test
    fun `Flurry of Blows - after an attack action, you may repeat it once on a different target`() {
        lateinit var a: String
        lateinit var b: String
        lateinit var c: String
        val d = pos {
            clearMinions()
            hero("min", Team.RED, Hex(0, -2)) {
                played("min-red-1")
                ultimate()
            }
            hero("arien", Team.BLUE, Hex(8, -6))
            a = minion(Team.BLUE, MinionType.MELEE, Hex(1, -2))
            b = minion(Team.BLUE, MinionType.MELEE, Hex(0, -1))
            c = minion(Team.BLUE, MinionType.MELEE, Hex(-1, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -5))
            minion(Team.RED, MinionType.MELEE, Hex(-1, -5))
            phase = PositionPhase.Resolving(next = "min")
        }
        d.action("min", ActionType.PRIMARY)
        d.unit(a)
        d.unit(b) // Crane Stance: push b 0 spaces (it is targeted too)
        d.number(0)
        d.expect(DecisionKind.CONFIRM, "min")
        d.yes()
        // The repeat: a and b were targeted, c is left.
        assertThat(d.s.state.minion(c)).isNull()
        assertThat(d.s.state.minion(b)).isNotNull()
        assertThat(d.hero("min").coins).isEqualTo(4)
    }
}
