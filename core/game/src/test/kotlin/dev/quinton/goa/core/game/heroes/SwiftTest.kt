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

/** Swift the Sharpshooter: every card, clause by clause (text from `catalog/heroes/swift.json`). */
class SwiftTest {
    // ---- Reload! (gold) -----------------------------------------------------------------------

    @Test
    fun `Reload - perform the primary action of your rightmost resolved card (Snipe at maximum range)`() {
        lateinit var east: String
        lateinit var south: String
        val d = pos {
            clearMinions()
            hero("swift", Team.RED, Hex(0, -5)) {
                played("swift-gold")
                resolved(1, "swift-red-1")
            }
            hero("arien", Team.BLUE, Hex(8, -6))
            east = minion(Team.BLUE, MinionType.MELEE, Hex(4, -5)) // 4 away, straight line
            south = minion(Team.BLUE, MinionType.MELEE, Hex(0, -1)) // 4 away, straight line
            minion(Team.BLUE, MinionType.MELEE, Hex(3, -5)) // 3 away: not at maximum range
            minion(Team.BLUE, MinionType.RANGED, Hex(2, -3)) // 4 away, not in a straight line
            minion(Team.RED, MinionType.MELEE, Hex(-1, -2))
            turn = 2
            phase = PositionPhase.Resolving(next = "swift")
        }
        d.action("swift", ActionType.PRIMARY)
        // No minion adjacent: only the first bullet, so no "Choose one" question.
        d.expect(DecisionKind.CHOOSE_UNIT, "swift")
        assertThat(d.units()).containsExactly(east, south)
        d.unit(east)
        assertThat(d.s.state.minion(east)).isNull()
        assertThat(d.hero("swift").coins).isEqualTo(2)
        assertThat(d.zoneOf("swift", "swift-gold")).isEqualTo(Zone.RESOLVED)
    }

    @Test
    fun `Reload - Choose one, Defeat a minion adjacent to you (never a friendly one)`() {
        lateinit var enemy: String
        lateinit var friend: String
        val d = pos {
            clearMinions()
            hero("swift", Team.RED, Hex(0, -5)) {
                played("swift-gold")
                resolved(1, "swift-red-1")
            }
            hero("arien", Team.BLUE, Hex(8, -6))
            enemy = minion(Team.BLUE, MinionType.MELEE, Hex(1, -5))
            friend = minion(Team.RED, MinionType.MELEE, Hex(-1, -5))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -5))
            turn = 2
            phase = PositionPhase.Resolving(next = "swift")
        }
        d.action("swift", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_OPTION, "swift")
        d.option(1)
        // The friendly minion is not offered: the only enemy minion is defeated without a question.
        assertThat(d.s.state.minion(enemy)).isNull()
        assertThat(d.s.state.minion(friend)).isNotNull()
        assertThat(d.hero("swift").coins).isEqualTo(2)
    }

    @Test
    fun `Reload - the rightmost resolved card is the one in the highest Turn slot`() {
        lateinit var adjacent: String
        val d = pos {
            clearMinions()
            hero("swift", Team.RED, Hex(0, -5)) {
                played("swift-gold")
                resolved(1, "swift-red-1")
                resolved(2, "swift-blue-1") // Steam Jump
            }
            hero("arien", Team.BLUE, Hex(8, -6))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -5))
            adjacent = minion(Team.BLUE, MinionType.MELEE, Hex(0, -1))
            minion(Team.RED, MinionType.MELEE, Hex(-1, -2))
            turn = 3
            phase = PositionPhase.Resolving(next = "swift")
        }
        d.action("swift", ActionType.PRIMARY)
        // Steam Jump's placement, not Snipe's target.
        d.expect(DecisionKind.CHOOSE_SPACE, "swift")
        d.space(0, -2)
        // Steam Jump goes on: push the enemy minion now adjacent up to 1 space.
        d.expect(DecisionKind.CHOOSE_NUMBER, "swift")
        d.number(1)
        assertThat(d.at(adjacent)).isEqualTo(Hex(0, 0))
    }

    @Test
    fun `Reload - nothing resolved and no minion adjacent, so no Primary action`() {
        val d = pos {
            clearMinions()
            hero("swift", Team.RED, Hex(0, -5)) { played("swift-gold") }
            hero("arien", Team.BLUE, Hex(8, -6))
            phase = PositionPhase.Resolving(next = "swift")
        }
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
    }

    @Test
    fun `Reload - a facedown resolved card has no actions (p 10)`() {
        val d = pos {
            clearMinions()
            hero("swift", Team.RED, Hex(0, -5)) {
                played("swift-gold")
                resolved(1, "swift-red-1", faceDown = true)
            }
            hero("arien", Team.BLUE, Hex(8, -6))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -5))
            turn = 2
            phase = PositionPhase.Resolving(next = "swift")
        }
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
    }

    @Test
    fun `Reload - a heavy minion with friendly minions in the Battle Zone is immune (p 13)`() {
        val d = pos {
            clearMinions()
            hero("swift", Team.RED, Hex(0, -5)) { played("swift-gold") }
            hero("arien", Team.BLUE, Hex(8, -6))
            minion(Team.BLUE, MinionType.HEAVY, Hex(1, -5))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -2))
            phase = PositionPhase.Resolving(next = "swift")
        }
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
    }

    // ---- Backup (Handicap) --------------------------------------------------------------------

    @Test
    fun `Backup - perform the rightmost card's primary, an attack this way cannot target minions`() {
        lateinit var minion: String
        val d = pos {
            clearMinions()
            hero("swift", Team.RED, Hex(0, -5)) {
                played("swift-handicap")
                resolved(1, "swift-red-1")
            }
            hero("arien", Team.BLUE, Hex(4, -5)) { hand("arien-blue-1") } // maximum range, straight line
            minion = minion(Team.BLUE, MinionType.MELEE, Hex(0, -1)) // also at maximum range
            minion(Team.RED, MinionType.MELEE, Hex(-1, -2))
            turn = 2
            phase = PositionPhase.Resolving(next = "swift")
        }
        d.action("swift", ActionType.PRIMARY)
        // Only Arien may be targeted.
        d.expect(DecisionKind.DEFEND, "arien")
        d.defend("arien-blue-1")
        assertThat(d.s.state.minion(minion)).isNotNull()
    }

    @Test
    fun `Backup - with only minions to attack the action cannot be performed`() {
        val d = pos {
            clearMinions()
            hero("swift", Team.RED, Hex(0, -5)) {
                played("swift-handicap")
                resolved(1, "swift-red-1")
            }
            hero("arien", Team.BLUE, Hex(8, -6))
            minion(Team.BLUE, MinionType.MELEE, Hex(0, -1))
            minion(Team.RED, MinionType.MELEE, Hex(-1, -2))
            turn = 2
            phase = PositionPhase.Resolving(next = "swift")
        }
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
    }

    // ---- Bounce (silver) ----------------------------------------------------------------------

    @Test
    fun `Bounce - Move 2 spaces in a straight line, ignoring obstacles, no repeat when not yet resolved`() {
        val d = pos {
            clearMinions()
            hero("swift", Team.RED, Hex(0, -5)) { played("swift-silver") }
            hero("arien", Team.BLUE, Hex(1, -5)) { played("arien-blue-1") } // an obstacle to jump over
            phase = PositionPhase.Resolving(next = "swift")
        }
        d.action("swift", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_SPACE, "swift")
        // Exactly 2 spaces along the six lines; (-2,-5) is terrain (not empty); (2,-5) is past Arien.
        assertThat(d.spaces()).containsExactly(Hex(2, -5), Hex(2, -7), Hex(0, -7), Hex(-2, -3), Hex(0, -3))
        d.space(2, -5)
        assertThat(d.at("swift")).isEqualTo(Hex(2, -5))
        // Played this turn, so not resolved yet: no repeat. Arien acts next.
        d.expect(DecisionKind.CHOOSE_ACTION, "arien")
    }

    @Test
    fun `Bounce - FAQ - already resolved when Reload performs it, so it may repeat once`() {
        val d = pos {
            clearMinions()
            hero("swift", Team.RED, Hex(0, -5)) {
                played("swift-gold")
                resolved(1, "swift-silver")
            }
            hero("arien", Team.BLUE, Hex(8, -6))
            turn = 2
            phase = PositionPhase.Resolving(next = "swift")
        }
        d.action("swift", ActionType.PRIMARY)
        d.space(2, -5)
        d.expect(DecisionKind.CONFIRM, "swift")
        d.yes()
        d.space(4, -5)
        assertThat(d.at("swift")).isEqualTo(Hex(4, -5))
    }

    // ---- Snipe / Prepared Shot / Killshot (red) ------------------------------------------------

    @Test
    fun `Snipe - maximum range counts Range items (p 16)`() {
        lateinit var far: String
        val d = pos {
            clearMinions()
            hero("swift", Team.RED, Hex(0, -5)) {
                played("swift-red-1")
                items("swift-green-3a") // +1 Range: Range 5
            }
            hero("arien", Team.BLUE, Hex(8, -6))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -5)) // 4 away: no longer the maximum
            far = minion(Team.BLUE, MinionType.MELEE, Hex(5, -5)) // 5 away
            minion(Team.RED, MinionType.MELEE, Hex(-1, -2))
            phase = PositionPhase.Resolving(next = "swift")
        }
        d.action("swift", ActionType.PRIMARY)
        assertThat(d.s.state.minion(far)).isNull()
        assertThat(d.s.state.minions.count { it.team == Team.BLUE }).isEqualTo(1)
    }

    @Test
    fun `Prepared Shot - Target a unit in range, in a straight line, and not adjacent to you`() {
        lateinit var line3: String
        lateinit var south3: String
        val d = pos {
            clearMinions()
            hero("swift", Team.RED, Hex(0, -5)) { played("swift-red-2a") } // Range 4
            hero("arien", Team.BLUE, Hex(8, -6))
            minion(Team.BLUE, MinionType.MELEE, Hex(1, -5)) // adjacent
            line3 = minion(Team.BLUE, MinionType.MELEE, Hex(3, -5))
            south3 = minion(Team.BLUE, MinionType.MELEE, Hex(0, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(2, -3)) // not in a straight line
            minion(Team.RED, MinionType.MELEE, Hex(-1, -2))
            phase = PositionPhase.Resolving(next = "swift")
        }
        d.action("swift", ActionType.PRIMARY)
        assertThat(d.units()).containsExactly(line3, south3)
    }

    @Test
    fun `Killshot - Range 5, in a straight line, not adjacent`() {
        lateinit var five: String
        val d = pos {
            clearMinions()
            hero("swift", Team.RED, Hex(0, -5)) { played("swift-red-3a") }
            hero("arien", Team.BLUE, Hex(8, -6))
            minion(Team.BLUE, MinionType.MELEE, Hex(0, -4)) // adjacent
            five = minion(Team.BLUE, MinionType.MELEE, Hex(5, -5))
            minion(Team.RED, MinionType.MELEE, Hex(-1, -2))
            phase = PositionPhase.Resolving(next = "swift")
        }
        d.action("swift", ActionType.PRIMARY)
        assertThat(d.s.state.minion(five)).isNull()
    }

    // ---- Shotgun / Super-Shotgun (red alternates) ---------------------------------------------

    @Test
    fun `Shotgun - Before the attack, an enemy hero adjacent to the target discards a card, if able`() {
        lateinit var target: String
        val d = pos {
            clearMinions()
            hero("swift", Team.RED, Hex(0, -2)) { played("swift-red-2b") } // Range 2
            hero("arien", Team.BLUE, Hex(3, -2)) { hand("arien-gold", "arien-blue-1") } // next to the target
            hero("brogan", Team.BLUE, Hex(4, -5)) { hand("brogan-gold") } // not next to the target
            target = minion(Team.BLUE, MinionType.MELEE, Hex(2, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -4))
            minion(Team.RED, MinionType.MELEE, Hex(-1, -5))
            phase = PositionPhase.Resolving(next = "swift")
        }
        d.action("swift", ActionType.PRIMARY)
        // One target in range; Arien is the only enemy hero next to it, and he chooses the card.
        d.expect(DecisionKind.CHOOSE_CARD, "arien")
        d.card("arien-gold")
        assertThat(d.zoneOf("arien", "arien-gold")).isEqualTo(Zone.DISCARD)
        assertThat(d.zoneOf("brogan", "brogan-gold")).isEqualTo(Zone.HAND)
        assertThat(d.s.state.minion(target)).isNull()
    }

    @Test
    fun `Shotgun - with no enemy hero next to the target the action stops before the attack (RULINGS 53)`() {
        lateinit var target: String
        val d = pos {
            clearMinions()
            hero("swift", Team.RED, Hex(0, -2)) { played("swift-red-2b") }
            hero("arien", Team.BLUE, Hex(8, -6))
            target = minion(Team.BLUE, MinionType.MELEE, Hex(2, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -4))
            minion(Team.RED, MinionType.MELEE, Hex(-1, -5))
            phase = PositionPhase.Resolving(next = "swift")
        }
        // goa2-faq (Energy Drain): "you need to be able to target a hero first"; "if able" covers the discard.
        // With the only target taken automatically, nothing can be done: the Primary action is not offered.
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
        assertThat(d.s.state.minion(target)).isNotNull()
    }

    @Test
    fun `Shotgun - an enemy hero immune to Swift's attack actions can't be chosen, so the action stops`() {
        lateinit var target: String
        val d = pos {
            clearMinions()
            hero("swift", Team.RED, Hex(0, -2)) { played("swift-red-2b") }
            hero("arien", Team.BLUE, Hex(3, -2)) {
                hand("arien-gold")
                discard("arien-blue-2a")
            }
            target = minion(Team.BLUE, MinionType.MELEE, Hex(2, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -4))
            minion(Team.RED, MinionType.MELEE, Hex(-1, -5))
            // Expert Duelist: "This turn: You are immune to attack actions of all enemy heroes, except this attacker."
            effect("arien", "arien-blue-2a", Timing.THIS_TURN, ActionKind.DEFENSE, data = mapOf("attacker" to "wasp"))
            phase = PositionPhase.Resolving(next = "swift")
        }
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
        assertThat(d.zoneOf("arien", "arien-gold")).isEqualTo(Zone.HAND)
        assertThat(d.s.state.minion(target)).isNotNull()
    }

    @Test
    fun `Super-Shotgun - discards a card, or is defeated`() {
        lateinit var target: String
        val d = pos {
            clearMinions()
            hero("swift", Team.RED, Hex(0, -2)) { played("swift-red-3b") }
            hero("arien", Team.BLUE, Hex(3, -2)) { hand() } // next to the target, nothing in hand
            target = minion(Team.BLUE, MinionType.MELEE, Hex(2, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -4))
            minion(Team.RED, MinionType.MELEE, Hex(-1, -5))
            phase = PositionPhase.Resolving(next = "swift")
        }
        d.action("swift", ActionType.PRIMARY)
        assertThat(d.at("arien")).isNull()
        assertThat(d.s.state.minion(target)).isNull()
        assertThat(d.hero("swift").coins).isEqualTo(1 + 2) // Arien's level, then the minion
    }

    @Test
    fun `Super-Shotgun - the before-the-attack step is mandatory, so no enemy hero next to the target stops it`() {
        val d = pos {
            clearMinions()
            hero("swift", Team.RED, Hex(0, -2)) { played("swift-red-3b") }
            hero("arien", Team.BLUE, Hex(8, -6))
            minion(Team.BLUE, MinionType.MELEE, Hex(2, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -4))
            minion(Team.RED, MinionType.MELEE, Hex(-1, -5))
            phase = PositionPhase.Resolving(next = "swift")
        }
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
    }

    // ---- Suppress / Pin Down / Killing Ground (green) -----------------------------------------

    @Test
    fun `Suppress - An enemy hero in radius who is not adjacent to terrain discards a card, if able`() {
        val d = pos {
            clearMinions()
            hero("swift", Team.RED, Hex(0, -2)) { played("swift-green-1") } // radius 3
            hero("arien", Team.BLUE, Hex(3, -2)) { hand("arien-gold", "arien-blue-1") } // open ground, 3 away
            hero("brogan", Team.BLUE, Hex(1, -2)) { hand("brogan-gold") } // next to the rock at (1,-3)
            phase = PositionPhase.Resolving(next = "swift")
        }
        d.action("swift", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_CARD, "arien")
        d.card("arien-blue-1")
        assertThat(d.zoneOf("arien", "arien-blue-1")).isEqualTo(Zone.DISCARD)
        assertThat(d.zoneOf("brogan", "brogan-gold")).isEqualTo(Zone.HAND)
    }

    @Test
    fun `Pin Down - radius 4, and every enemy hero next to terrain means no Primary action`() {
        val d = pos {
            clearMinions()
            hero("swift", Team.RED, Hex(0, -2)) { played("swift-green-2a") }
            hero("brogan", Team.BLUE, Hex(1, -2)) // next to the rock at (1,-3)
            hero("arien", Team.BLUE, Hex(-1, -2)) // next to the rock at (-2,-1)
            phase = PositionPhase.Resolving(next = "swift")
        }
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
    }

    @Test
    fun `Killing Ground - discards a card, or is defeated`() {
        val d = pos {
            clearMinions()
            hero("swift", Team.RED, Hex(0, -2)) { played("swift-green-3a") } // radius 4
            hero("arien", Team.BLUE, Hex(4, -2)) { hand() }
            phase = PositionPhase.Resolving(next = "swift")
        }
        // Arien at (4,-2) is next to the rock at (5,-2): not a valid hero. On open ground at (3,-2) he is.
        val e = pos {
            clearMinions()
            hero("swift", Team.RED, Hex(0, -2)) { played("swift-green-3a") }
            hero("arien", Team.BLUE, Hex(3, -2)) { hand() }
            phase = PositionPhase.Resolving(next = "swift")
        }
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
        e.action("swift", ActionType.PRIMARY)
        assertThat(e.at("arien")).isNull()
        assertThat(e.hero("swift").coins).isEqualTo(1)
    }

    // ---- Mark for Death / Hunting Season (green alternates) -----------------------------------

    @Test
    fun `Mark for Death - Move an enemy minion in radius up to 3 spaces to a space in radius`() {
        lateinit var m: String
        val d = pos {
            clearMinions()
            hero("swift", Team.RED, Hex(0, -2)) { played("swift-green-2b") } // radius 4
            hero("arien", Team.BLUE, Hex(8, -6))
            m = minion(Team.BLUE, MinionType.MELEE, Hex(3, -2))
            minion(Team.RED, MinionType.MELEE, Hex(-1, -5))
            phase = PositionPhase.Resolving(next = "swift")
        }
        d.action("swift", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_SPACE, "swift")
        for (h in d.spaces()) {
            assertThat(HexGrid.distance(h, Hex(0, -2))).isIn(1..4)
            assertThat(HexGrid.distance(h, Hex(3, -2))).isAtMost(3)
        }
        assertThat(d.spaces()).doesNotContain(Hex(5, -3)) // 5 from Swift: out of radius
        d.space(1, -1)
        assertThat(d.at(m)).isEqualTo(Hex(1, -1))
        assertThat(d.s.state.effects.single { it.cardId == "swift-green-2b" }.timing)
            .isEqualTo(Timing.NEXT_TURN)
    }

    @Test
    fun `Mark for Death - Next turn, only the first enemy minion defeated in radius gains 1 coin`() {
        lateinit var a: String
        lateinit var b: String
        val d = pos {
            clearMinions()
            hero("swift", Team.RED, Hex(0, -2)) {
                played("swift-green-2b")
                hand("swift-gold")
            }
            hero("wasp", Team.RED, Hex(3, -5)) {
                played("wasp-gold")
                hand("wasp-red-1")
            }
            hero("arien", Team.BLUE, Hex(8, -6)) { hand("arien-blue-1") }
            a = minion(Team.BLUE, MinionType.MELEE, Hex(0, -1)) // next to Swift, 1 away
            b = minion(Team.BLUE, MinionType.MELEE, Hex(4, -5)) // next to Wasp, 4 from Swift
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -4))
            minion(Team.RED, MinionType.MELEE, Hex(-1, -5))
            phase = PositionPhase.Resolving(next = "swift")
        }
        d.action("swift", ActionType.PRIMARY)
        d.unit(a) // choose which minion to move (two in radius)
        d.decline() // and leave it where it is
        // This turn Wasp defeats a minion in radius: not yet "next turn", no coin for Swift.
        d.action("wasp", ActionType.PRIMARY)
        assertThat(d.s.state.minion(b)).isNull()
        assertThat(d.hero("swift").coins).isEqualTo(0)
        // Next turn: Swift's Reload defeats the adjacent minion — the first one, +1 coin.
        d.play("swift", "swift-gold").play("wasp", "wasp-red-1").play("arien", "arien-blue-1")
        d.action("arien", ActionType.HOLD) // initiative 9 before Wasp 8 and Swift 6
        d.action("wasp", ActionType.HOLD)
        d.action("swift", ActionType.PRIMARY)
        d.option(1) // Reload: defeat a minion adjacent to you
        assertThat(d.s.state.minion(a)).isNull()
        assertThat(d.hero("swift").coins).isEqualTo(2 + 1)
    }

    @Test
    fun `Hunting Season - up to two enemy minions, and the first two defeats next turn give a coin each`() {
        lateinit var a: String
        lateinit var b: String
        val d = pos {
            clearMinions()
            hero("swift", Team.RED, Hex(0, -2)) {
                played("swift-green-3b")
                hand("swift-gold")
            }
            hero("wasp", Team.RED, Hex(3, -5)) { hand("wasp-gold") }
            hero("arien", Team.BLUE, Hex(8, -6)) { hand("arien-blue-1") }
            a = minion(Team.BLUE, MinionType.MELEE, Hex(0, 0)) // 2 from Swift
            b = minion(Team.BLUE, MinionType.MELEE, Hex(3, -3)) // 3 from Swift
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -7))
            minion(Team.RED, MinionType.MELEE, Hex(-1, -5))
            phase = PositionPhase.Resolving(next = "swift")
        }
        d.action("swift", ActionType.PRIMARY)
        d.unit(a)
        d.space(0, -1) // next to Swift
        d.unit(b)
        d.space(4, -5) // next to Wasp, still 4 from Swift
        assertThat(d.at(b)).isEqualTo(Hex(4, -5))
        d.play("swift", "swift-gold").play("wasp", "wasp-gold").play("arien", "arien-blue-1")
        d.action("wasp", ActionType.PRIMARY) // 12: Magnetic Dagger on b, the only unit next to Wasp
        assertThat(d.s.state.minion(b)).isNull()
        d.action("arien", ActionType.HOLD)
        d.action("swift", ActionType.PRIMARY)
        d.option(1) // Reload: defeat a
        assertThat(d.s.state.minion(a)).isNull()
        assertThat(d.hero("swift").coins).isEqualTo(2 + 1 + 1)
    }

    // ---- Steam Jump / Assault Jump / Drop Trooper (blue) --------------------------------------

    @Test
    fun `Steam Jump - Place yourself in a straight line in radius, then push an enemy unit adjacent up to 1`() {
        lateinit var m: String
        val d = pos {
            clearMinions()
            hero("swift", Team.RED, Hex(-1, -2)) { played("swift-blue-1") } // radius 3
            hero("arien", Team.BLUE, Hex(8, -6))
            m = minion(Team.BLUE, MinionType.MELEE, Hex(2, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -7))
            minion(Team.RED, MinionType.MELEE, Hex(-1, -5))
            phase = PositionPhase.Resolving(next = "swift")
        }
        d.action("swift", ActionType.PRIMARY)
        assertThat(d.spaces()).isNotEmpty()
        for (h in d.spaces()) {
            assertThat(HexGrid.inStraightLine(Hex(-1, -2), h)).isTrue()
            assertThat(HexGrid.distance(Hex(-1, -2), h)).isIn(1..3)
        }
        assertThat(d.spaces()).doesNotContain(Hex(2, -3)) // in radius but not in a straight line
        d.space(1, -2)
        d.expect(DecisionKind.CHOOSE_NUMBER, "swift")
        assertThat(d.d.options).hasSize(2) // 0 or 1
        d.number(1)
        assertThat(d.at(m)).isEqualTo(Hex(3, -2))
    }

    @Test
    fun `Assault Jump - push up to 2 spaces`() {
        lateinit var m: String
        val d = pos {
            clearMinions()
            hero("swift", Team.RED, Hex(-1, -2)) { played("swift-blue-2a") } // radius 4
            hero("arien", Team.BLUE, Hex(8, -6))
            m = minion(Team.BLUE, MinionType.MELEE, Hex(2, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -7))
            minion(Team.RED, MinionType.MELEE, Hex(-1, -5))
            phase = PositionPhase.Resolving(next = "swift")
        }
        d.action("swift", ActionType.PRIMARY)
        d.space(1, -2)
        assertThat(d.d.options).hasSize(3) // 0, 1 or 2
        d.number(2)
        assertThat(d.at(m)).isEqualTo(Hex(4, -2))
    }

    @Test
    fun `Drop Trooper - Push up to two enemy units adjacent to you up to 2 spaces, the same distance`() {
        lateinit var a: String
        lateinit var b: String
        val d = pos {
            clearMinions()
            hero("swift", Team.RED, Hex(-1, -2)) { played("swift-blue-3a") }
            hero("arien", Team.BLUE, Hex(8, -6))
            a = minion(Team.BLUE, MinionType.MELEE, Hex(2, -2)) // east of (1,-2)
            b = minion(Team.BLUE, MinionType.MELEE, Hex(0, -1)) // south-west of (1,-2)
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -7))
            minion(Team.RED, MinionType.MELEE, Hex(-1, -5))
            phase = PositionPhase.Resolving(next = "swift")
        }
        d.action("swift", ActionType.PRIMARY)
        d.space(1, -2)
        d.unit(a)
        d.unit(b)
        d.number(1)
        assertThat(d.at(a)).isEqualTo(Hex(3, -2))
        assertThat(d.at(b)).isEqualTo(Hex(-1, 0))
    }

    // ---- Delayed Jump / Mobile Scout (blue alternates) ----------------------------------------

    @Test
    fun `Delayed Jump - End of turn, place yourself in radius not in a straight line from you`() {
        val d = pos {
            clearMinions()
            hero("swift", Team.RED, Hex(0, -5)) { played("swift-blue-2b") } // radius 2
            hero("arien", Team.BLUE, Hex(8, -6)) { played("arien-blue-1") }
            phase = PositionPhase.Resolving(next = "swift")
        }
        d.action("swift", ActionType.PRIMARY)
        assertThat(d.at("swift")).isEqualTo(Hex(0, -5)) // nothing happens yet
        d.action("arien", ActionType.HOLD)
        d.expect(DecisionKind.CHOOSE_SPACE, "swift")
        for (h in d.spaces()) {
            assertThat(HexGrid.distance(Hex(0, -5), h)).isEqualTo(2)
            assertThat(HexGrid.inStraightLine(Hex(0, -5), h)).isFalse()
        }
        d.space(1, -7)
        assertThat(d.at("swift")).isEqualTo(Hex(1, -7))
        d.expect(DecisionKind.PLAY_CARD) // the next turn: no Fast Travel offered
    }

    @Test
    fun `Delayed Jump - the End of turn effect is cancelled when Swift is defeated first (p 17)`() {
        val d = pos {
            clearMinions()
            hero("swift", Team.RED, Hex(0, -5)) {
                played("swift-blue-2b")
                hand()
            }
            hero("arien", Team.BLUE, Hex(1, -5)) { played("arien-gold") }
            phase = PositionPhase.Resolving(next = "swift")
        }
        d.action("swift", ActionType.PRIMARY)
        d.action("arien", ActionType.PRIMARY) // Noble Blade; Swift has no card to defend with
        assertThat(d.at("swift")).isNull()
        d.expect(DecisionKind.PLAY_CARD) // next turn; no End of turn placement was asked
        assertThat(d.s.state.effects.none { it.cardId == "swift-blue-2b" }).isTrue()
    }

    @Test
    fun `Mobile Scout - then you may fast travel, if able`() {
        val d = pos {
            clearMinions()
            hero("swift", Team.RED, Hex(0, -5)) { played("swift-blue-3b") }
            hero("arien", Team.BLUE, Hex(8, -6)) { played("arien-blue-1") } // in lane-1-3
            phase = PositionPhase.Resolving(next = "swift")
        }
        d.action("swift", ActionType.PRIMARY)
        d.action("arien", ActionType.HOLD)
        d.space(1, -7)
        d.expect(DecisionKind.CHOOSE_SPACE, "swift")
        // lane-1-2 and its neighbours without enemies; lane-1-3 holds Arien, so it is not offered.
        assertThat(d.spaces()).doesNotContain(Hex(6, -5))
        assertThat(d.spaces()).contains(Hex(-4, -3)) // lane-1-1
        d.space(-4, -3)
        assertThat(d.at("swift")).isEqualTo(Hex(-4, -3))
    }

    // ---- Bullet Time (Ultimate) ---------------------------------------------------------------

    @Test
    fun `Bullet Time - after you resolve a basic card you may perform its primary action again`() {
        val d = pos {
            clearMinions()
            hero("swift", Team.RED, Hex(0, -5)) {
                played("swift-silver")
                ultimate()
            }
            hero("arien", Team.BLUE, Hex(8, -6)) { played("arien-blue-1") }
            phase = PositionPhase.Resolving(next = "swift")
        }
        d.action("swift", ActionType.PRIMARY)
        d.space(2, -5)
        d.expect(DecisionKind.CONFIRM, "swift")
        d.yes()
        // Bounce again; it is resolved now, so this instance may repeat once more.
        d.space(4, -5)
        d.expect(DecisionKind.CONFIRM, "swift")
        d.decline()
        assertThat(d.at("swift")).isEqualTo(Hex(4, -5))
        d.expect(DecisionKind.CHOOSE_ACTION, "arien")
    }

    @Test
    fun `Bullet Time - a non-basic card gives nothing, and declining is allowed`() {
        val d = pos {
            clearMinions()
            hero("swift", Team.RED, Hex(0, -5)) {
                played("swift-blue-1")
                ultimate()
            }
            hero("arien", Team.BLUE, Hex(8, -6)) { played("arien-blue-1") }
            phase = PositionPhase.Resolving(next = "swift")
        }
        d.action("swift", ActionType.PRIMARY)
        d.space(3, -5)
        d.expect(DecisionKind.CHOOSE_ACTION, "arien")
    }

    @Test
    fun `Bullet Time - Reload again repeats the card before it, never on the same enemy hero (rulings 1 and 4)`() {
        lateinit var m: String
        val d = pos {
            clearMinions()
            hero("swift", Team.RED, Hex(0, -5)) {
                played("swift-gold")
                resolved(1, "swift-red-1")
                ultimate()
            }
            hero("arien", Team.BLUE, Hex(4, -5)) { hand("arien-blue-1", "arien-gold") } // maximum range
            m = minion(Team.BLUE, MinionType.MELEE, Hex(0, -1)) // maximum range
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -7))
            minion(Team.RED, MinionType.MELEE, Hex(-1, -2))
            turn = 2
            phase = PositionPhase.Resolving(next = "swift")
        }
        d.action("swift", ActionType.PRIMARY)
        d.unit("arien")
        d.defend("arien-blue-1")
        // Reload is resolved: Bullet Time performs it again; Snipe may not target Arien a second time.
        d.expect(DecisionKind.CONFIRM, "swift")
        d.yes()
        assertThat(d.s.state.minion(m)).isNull()
        assertThat(d.zoneOf("arien", "arien-gold")).isEqualTo(Zone.HAND)
    }

    // ---- audit E regressions --------------------------------------------------------------------

    @Test
    fun `Steam Jump - a space that counts as an obstacle for Swift is not offered (Takahide's blade)`() {
        val d = pos {
            clearMinions()
            hero("swift", Team.RED, Hex(-1, -2)) { played("swift-blue-1") } // radius 3
            hero("takahide", Team.BLUE, Hex(3, -2)) { resolved(1, "takahide-red-2b") }
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -7))
            minion(Team.RED, MinionType.MELEE, Hex(-1, -5))
            turn = 2
            // Spinning Blade: "This turn: Empty spaces adjacent to you count as obstacles for enemy units."
            effect("takahide", "takahide-red-2b", Timing.THIS_TURN, ActionKind.ATTACK)
            phase = PositionPhase.Resolving(next = "swift")
        }
        d.action("swift", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_SPACE, "swift")
        assertThat(d.spaces()).contains(Hex(1, -2))
        assertThat(d.spaces()).doesNotContain(Hex(2, -2)) // next to Takahide: not empty for Swift
        d.space(1, -2)
        assertThat(d.at("swift")).isEqualTo(Hex(1, -2))
    }

    @Test
    fun `Mark for Death - a minion defeated but not removed (Brogan's Shield) still counts as defeated`() {
        lateinit var m: String
        val d = pos {
            clearMinions()
            hero("swift", Team.RED, Hex(0, -5)) {
                played("swift-gold")
                resolved(1, "swift-green-2b")
            }
            hero("brogan", Team.BLUE, Hex(2, -5)) {
                resolved(1, "brogan-green-1")
                hand("brogan-silver")
            }
            m = minion(Team.BLUE, MinionType.MELEE, Hex(1, -5))
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -7))
            minion(Team.RED, MinionType.MELEE, Hex(-1, -2))
            turn = 2
            effect("brogan", "brogan-green-1", Timing.THIS_ROUND, ActionKind.SKILL, madeTurn = 1)
            // Mark for Death played last turn: "Next turn: The first time an enemy minion in radius is
            // defeated, gain 1 coin."
            effect(
                "swift",
                "swift-green-2b",
                Timing.NEXT_TURN,
                ActionKind.SKILL,
                madeTurn = 1,
                data = mapOf("left" to "1"),
            )
            phase = PositionPhase.Resolving(next = "swift")
        }
        d.action("swift", ActionType.PRIMARY)
        d.option(1) // Reload: defeat a minion adjacent to you
        d.expect(DecisionKind.CHOOSE_CARD, "brogan")
        d.card("brogan-silver") // Shield: the minion is not removed
        assertThat(d.s.state.minion(m)).isNotNull()
        // p.12: the minion was still defeated (2 coins), so Mark for Death gives its coin too.
        assertThat(d.hero("swift").coins).isEqualTo(2 + 1)
    }
}
