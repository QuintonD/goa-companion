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
 * Mortimer the Awakener: every card, clause by clause (text from `catalog/heroes/mortimer.json`),
 * with the FAQ and the readings in `core/game/rulings/mortimer.md`. Forgotten Island: row r = −2,
 * q = −1..4 is Battle Zone `lane-1-2`; terrain at (1,−3) and (5,−2); spawn points at (2,−3), (0,−3),
 * (2,−1), (3,−1), (1,−4), (3,−4).
 */
class MortimerTest {
    private fun Driver.zombies(): List<Hex> = s.state.pieces.filter {
        it.kind == MortimerHero.ZOMBIE
    }.mapNotNull { it.at }

    private fun Driver.options(): List<Int> = d.options.filterIsInstance<Choice.Option>().map { it.index }

    // ---- Knife of the Living Dead (gold) / Shallow Graves (Handicap) ---------------------------

    @Test
    fun `Knife of the Living Dead - Before the attack, choose up to three times, +1 Attack per zombie removed`() {
        lateinit var z1: String
        lateinit var z2: String
        val d = pos {
            clearMinions()
            hero("mortimer", Team.RED, Hex(1, -2)) { played("mortimer-gold") } // Attack 4, Radius 4
            hero("brogan", Team.BLUE, Hex(2, -2)) { hand("brogan-blue-1") } // Defense 5
            z1 = token(MortimerHero.ZOMBIE, Hex(3, -2), owner = "mortimer") // next to Brogan
            z2 = token(MortimerHero.ZOMBIE, Hex(-1, -2), owner = "mortimer") // 2 from Mortimer
            phase = PositionPhase.Resolving(next = "mortimer")
        }
        d.action("mortimer", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_OPTION, "mortimer")
        assertThat(d.options()).containsExactly(0, 1)
        d.option(1) // removes the zombie next to Brogan (the only one)
        assertThat(d.at(z1)).isNull()
        d.option(0) // move the other zombie 1 space
        d.space(0, -2)
        d.option(0) // and again
        d.space(0, -1)
        // Three times used: the attack follows, Attack 4 + 1.
        d.expect(DecisionKind.DEFEND, "brogan")
        d.defend("brogan-blue-1")
        assertThat(d.texts()).contains("Mortimer attacks Brogan: Attack 5.")
        assertThat(d.at(z2)).isEqualTo(Hex(0, -1))
    }

    @Test
    fun `Knife of the Living Dead - two zombies removed beat Defense 5`() {
        val d = pos {
            clearMinions()
            hero("mortimer", Team.RED, Hex(1, -2)) { played("mortimer-gold") }
            hero("brogan", Team.BLUE, Hex(2, -2)) { hand("brogan-blue-1") }
            token(MortimerHero.ZOMBIE, Hex(3, -2), owner = "mortimer")
            token(MortimerHero.ZOMBIE, Hex(3, -3), owner = "mortimer")
            phase = PositionPhase.Resolving(next = "mortimer")
        }
        d.action("mortimer", ActionType.PRIMARY)
        d.option(1)
        d.token(d.s.state.pieces.first { it.at == Hex(3, -2) }.id)
        d.option(1) // the last one: no question
        // Nothing left to choose: the attack follows.
        d.defend("brogan-blue-1")
        assertThat(d.texts()).contains("Mortimer attacks Brogan: Attack 6.")
        assertThat(d.at("brogan")).isNull()
    }

    @Test
    fun `Shallow Graves - Target a hero adjacent to you, choose up to two times`() {
        val d = pos {
            clearMinions()
            hero("mortimer", Team.RED, Hex(1, -2)) { played("mortimer-handicap") }
            hero("brogan", Team.BLUE, Hex(2, -2)) { hand("brogan-blue-1") }
            minion(Team.BLUE, MinionType.MELEE, Hex(0, -2)) // a minion adjacent: never the target
            token(MortimerHero.ZOMBIE, Hex(4, -2), owner = "mortimer")
            phase = PositionPhase.Resolving(next = "mortimer")
        }
        d.action("mortimer", ActionType.PRIMARY)
        d.option(0)
        d.space(3, -2) // next to Brogan now
        d.option(1)
        d.expect(DecisionKind.DEFEND, "brogan")
        assertThat(d.d.context.attackTotal).isEqualTo(4) // Attack 3 + 1
    }

    // ---- Awaken! (silver) ----------------------------------------------------------------------

    @Test
    fun `Awaken - Place up to 4 Zombie tokens next to you or on spawn points in radius`() {
        val d = pos {
            clearMinions()
            hero("mortimer", Team.RED, Hex(1, -2)) { played("mortimer-silver") } // Radius 4
            hero("brogan", Team.BLUE, Hex(4, -5))
            phase = PositionPhase.Resolving(next = "mortimer")
        }
        d.action("mortimer", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_SPACE, "mortimer")
        assertThat(d.spaces()).containsAtLeast(Hex(2, -2), Hex(0, -2), Hex(3, -4), Hex(2, -1))
        assertThat(d.spaces()).doesNotContain(Hex(3, -2)) // 2 away and not a spawn point
        assertThat(d.spaces()).doesNotContain(Hex(1, -3)) // terrain
        assertThat(d.spaces().all { HexGrid.distance(it, Hex(1, -2)) in 1..4 }).isTrue()
        d.space(2, -2)
        d.space(3, -4)
        d.space(0, -2)
        d.space(2, -1)
        assertThat(d.zombies()).containsExactly(Hex(2, -2), Hex(3, -4), Hex(0, -2), Hex(2, -1))
        d.expect(DecisionKind.PLAY_CARD) // four placed: the action is over
    }

    @Test
    fun `Awaken - Zombie tokens are not removed at the end of round`() {
        val d = pos {
            clearMinions()
            turn = 4
            hero("mortimer", Team.RED, Hex(1, -2)) { played("mortimer-silver") }
            hero("brogan", Team.BLUE, Hex(4, -5))
            token("token_rock", Hex(3, -2))
            phase = PositionPhase.Resolving(next = "mortimer")
        }
        d.action("mortimer", ActionType.PRIMARY)
        d.space(2, -2)
        d.decline()
        d.expect(DecisionKind.PLAY_CARD)
        assertThat(d.s.state.round).isEqualTo(2)
        assertThat(d.zombies()).containsExactly(Hex(2, -2))
        assertThat(d.s.state.pieces.none { it.kind == "token_rock" }).isTrue()
    }

    @Test
    fun `Awaken - FAQ - discarded to defend even when its Defense can't win, the zombies are placed`() {
        val d = pos {
            clearMinions()
            hero("mortimer", Team.RED, Hex(1, -2)) { hand("mortimer-silver") } // Defense 3
            hero("brogan", Team.BLUE, Hex(2, -2)) { played("brogan-gold") } // Attack 4
            phase = PositionPhase.Resolving(next = "brogan")
        }
        d.action("brogan", ActionType.PRIMARY)
        d.defend("mortimer-silver")
        d.expect(DecisionKind.CHOOSE_SPACE, "mortimer")
        d.space(0, -2)
        d.decline()
        // 3 against 4: defeated; the zombie stays (tokens are not removed when their hero is defeated).
        assertThat(d.at("mortimer")).isNull()
        assertThat(d.zombies()).containsExactly(Hex(0, -2))
    }

    @Test
    fun `Awaken - FAQ - more zombies than the supply (APP - supply not limited, nothing is taken)`() {
        val d = pos {
            clearMinions()
            hero("mortimer", Team.RED, Hex(1, -2)) { played("mortimer-silver") }
            hero("brogan", Team.BLUE, Hex(4, -5))
            for (h in listOf(Hex(-1, -5), Hex(0, -5), Hex(1, -5), Hex(2, -5), Hex(3, -5), Hex(4, -4))) {
                token(MortimerHero.ZOMBIE, h, owner = "mortimer")
            }
            phase = PositionPhase.Resolving(next = "mortimer")
        }
        d.action("mortimer", ActionType.PRIMARY)
        d.space(2, -2)
        d.space(0, -2)
        d.decline()
        assertThat(d.zombies()).hasSize(8)
    }

    // ---- Crawling Dead / Walking Dead / Racing Dead (red) --------------------------------------

    @Test
    fun `Crawling Dead - After the attack, another enemy hero in radius next to a zombie discards`() {
        val d = pos {
            clearMinions()
            hero("mortimer", Team.RED, Hex(1, -2)) { played("mortimer-red-1") } // Attack 6
            hero("brogan", Team.BLUE, Hex(2, -2)) { hand("brogan-red-1", "brogan-gold") } // Defense 7
            hero("arien", Team.BLUE, Hex(4, -2)) { hand("arien-gold", "arien-silver") }
            token(MortimerHero.ZOMBIE, Hex(3, -2), owner = "mortimer") // next to both
            phase = PositionPhase.Resolving(next = "mortimer")
        }
        d.action("mortimer", ActionType.PRIMARY)
        d.defend("brogan-red-1")
        d.expect(DecisionKind.CHOOSE_OPTION, "mortimer")
        d.option(1)
        // "Another": Brogan was the target, so Arien (no question); he picks the card.
        d.expect(DecisionKind.CHOOSE_CARD, "arien")
        d.card("arien-gold")
        assertThat(d.zoneOf("arien", "arien-gold")).isEqualTo(Zone.DISCARD)
        assertThat(d.zoneOf("brogan", "brogan-gold")).isEqualTo(Zone.HAND)
        // Choose one: nothing more.
        assertThat(d.pending?.kind).isNotEqualTo(DecisionKind.CHOOSE_OPTION)
    }

    @Test
    fun `Crawling Dead - Choose one, move a Zombie token in radius 1 space`() {
        lateinit var z: String
        val d = pos {
            clearMinions()
            hero("mortimer", Team.RED, Hex(1, -2)) { played("mortimer-red-1") }
            hero("brogan", Team.BLUE, Hex(2, -2)) { hand("brogan-red-1") }
            z = token(MortimerHero.ZOMBIE, Hex(-2, -2), owner = "mortimer") // 3 away
            phase = PositionPhase.Resolving(next = "mortimer")
        }
        d.action("mortimer", ActionType.PRIMARY)
        d.defend("brogan-red-1")
        // No enemy hero qualifies for the second bullet: the move is taken without a question.
        d.expect(DecisionKind.CHOOSE_SPACE, "mortimer")
        d.space(-1, -2)
        assertThat(d.at(z)).isEqualTo(Hex(-1, -2))
    }

    @Test
    fun `Walking Dead - up to two times, each enemy hero can only be targeted once`() {
        val d = pos {
            clearMinions()
            hero("mortimer", Team.RED, Hex(1, -2)) { played("mortimer-red-2a") }
            hero("brogan", Team.BLUE, Hex(2, -2)) { hand("brogan-red-1") }
            hero("arien", Team.BLUE, Hex(4, -2)) { hand("arien-gold", "arien-silver") }
            token(MortimerHero.ZOMBIE, Hex(4, -3), owner = "mortimer")
            phase = PositionPhase.Resolving(next = "mortimer")
        }
        d.action("mortimer", ActionType.PRIMARY)
        d.defend("brogan-red-1")
        d.option(1)
        d.card("arien-gold")
        // Arien was targeted once: only the zombie move is left.
        d.expect(DecisionKind.CHOOSE_OPTION, "mortimer")
        assertThat(d.options()).containsExactly(0)
        d.decline()
        assertThat(d.zoneOf("arien", "arien-silver")).isEqualTo(Zone.HAND)
    }

    @Test
    fun `Racing Dead - up to three times`() {
        lateinit var z: String
        val d = pos {
            clearMinions()
            hero("mortimer", Team.RED, Hex(1, -2)) { played("mortimer-red-3a") }
            hero("brogan", Team.BLUE, Hex(2, -2)) { hand("brogan-red-1") }
            z = token(MortimerHero.ZOMBIE, Hex(-1, -2), owner = "mortimer")
            hero("arien", Team.BLUE, Hex(4, -5)) { hand("arien-gold") }
            phase = PositionPhase.Resolving(next = "mortimer")
        }
        d.action("mortimer", ActionType.PRIMARY)
        d.defend("brogan-red-1")
        d.option(0)
        d.space(-1, -1)
        d.option(0)
        d.space(-1, 0)
        d.option(0)
        d.space(-2, 0)
        assertThat(d.at(z)).isEqualTo(Hex(-2, 0))
        assertThat(d.pending?.kind).isNotEqualTo(DecisionKind.CHOOSE_OPTION)
    }

    // ---- Braains...! / Braaaaaaaains...! (red alternates) --------------------------------------

    @Test
    fun `Braains - Before the attack, if an enemy hero in radius is next to a zombie, retrieve a card`() {
        val d = pos {
            clearMinions()
            hero("mortimer", Team.RED, Hex(1, -2)) {
                played("mortimer-red-2b")
                hand("mortimer-gold") // the rest of the round is in the discard
            }
            hero("brogan", Team.BLUE, Hex(2, -2)) { hand("brogan-red-1") }
            token(MortimerHero.ZOMBIE, Hex(3, -2), owner = "mortimer")
            phase = PositionPhase.Resolving(next = "mortimer")
        }
        d.action("mortimer", ActionType.PRIMARY)
        d.option(1)
        d.expect(DecisionKind.CHOOSE_CARD, "mortimer")
        d.card("mortimer-silver")
        d.option(1) // twice: the same bullet again
        d.card("mortimer-green-1")
        assertThat(d.zoneOf("mortimer", "mortimer-silver")).isEqualTo(Zone.HAND)
        assertThat(d.zoneOf("mortimer", "mortimer-green-1")).isEqualTo(Zone.HAND)
        d.expect(DecisionKind.DEFEND, "brogan")
    }

    @Test
    fun `Braains - FAQ - an immune hero next to a zombie does not count`() {
        val d = pos {
            clearMinions()
            hero("mortimer", Team.RED, Hex(1, -2)) {
                played("mortimer-red-2b")
                hand("mortimer-gold")
            }
            hero("brogan", Team.BLUE, Hex(2, -2)) { hand("brogan-red-1") }
            // Master Duelist: immune to attack actions of enemy heroes other than Wasp this round.
            hero("arien", Team.BLUE, Hex(4, -2))
            effect("arien", "arien-blue-3a", Timing.THIS_ROUND, ActionKind.DEFENSE, data = mapOf("attacker" to "wasp"))
            token(MortimerHero.ZOMBIE, Hex(4, -3), owner = "mortimer") // next to Arien only
            token(MortimerHero.ZOMBIE, Hex(-1, -2), owner = "mortimer")
            phase = PositionPhase.Resolving(next = "mortimer")
        }
        d.action("mortimer", ActionType.PRIMARY)
        assertThat(d.options()).containsExactly(0)
    }

    @Test
    fun `Braaaaaaaains - up to three times`() {
        val d = pos {
            clearMinions()
            hero("mortimer", Team.RED, Hex(1, -2)) {
                played("mortimer-red-3b")
                hand("mortimer-gold")
            }
            hero("brogan", Team.BLUE, Hex(2, -2)) { hand("brogan-red-1") }
            token(MortimerHero.ZOMBIE, Hex(3, -2), owner = "mortimer")
            phase = PositionPhase.Resolving(next = "mortimer")
        }
        d.action("mortimer", ActionType.PRIMARY)
        d.option(1)
        d.card("mortimer-silver")
        d.option(1)
        d.card("mortimer-green-1")
        d.option(1) // the last discarded card: no question
        assertThat(d.zoneOf("mortimer", "mortimer-blue-1")).isEqualTo(Zone.HAND)
        d.expect(DecisionKind.DEFEND, "brogan")
    }

    // ---- Stage Dive / Crowd Drift / Crowd Surf (green) -----------------------------------------

    @Test
    fun `Stage Dive - Choose one, swap with a Zombie token in range`() {
        lateinit var z: String
        val d = pos {
            clearMinions()
            hero("mortimer", Team.RED, Hex(1, -2)) { played("mortimer-green-1") } // Range 4
            hero("brogan", Team.BLUE, Hex(4, -5))
            z = token(MortimerHero.ZOMBIE, Hex(-3, -2), owner = "mortimer") // 4 away
            phase = PositionPhase.Resolving(next = "mortimer")
        }
        d.action("mortimer", ActionType.PRIMARY)
        d.option(1)
        assertThat(d.at("mortimer")).isEqualTo(Hex(-3, -2))
        assertThat(d.at(z)).isEqualTo(Hex(1, -2))
    }

    @Test
    fun `Stage Dive - Choose one, move a Zombie token in range 1 space, and only once`() {
        lateinit var z: String
        val d = pos {
            clearMinions()
            hero("mortimer", Team.RED, Hex(1, -2)) { played("mortimer-green-1") }
            hero("brogan", Team.BLUE, Hex(4, -5)) { played("brogan-red-1") }
            z = token(MortimerHero.ZOMBIE, Hex(3, -2), owner = "mortimer")
            token(MortimerHero.ZOMBIE, Hex(-4, -2), owner = "mortimer") // 5 away: out of range
            phase = PositionPhase.Resolving(next = "mortimer")
        }
        d.action("mortimer", ActionType.PRIMARY)
        d.option(0)
        d.space(4, -2)
        assertThat(d.at(z)).isEqualTo(Hex(4, -2))
        d.expect(DecisionKind.CHOOSE_ACTION, "brogan")
    }

    @Test
    fun `Crowd Drift - up to two times`() {
        lateinit var z: String
        val d = pos {
            clearMinions()
            hero("mortimer", Team.RED, Hex(1, -2)) { played("mortimer-green-2a") }
            hero("brogan", Team.BLUE, Hex(4, -5))
            z = token(MortimerHero.ZOMBIE, Hex(3, -2), owner = "mortimer")
            phase = PositionPhase.Resolving(next = "mortimer")
        }
        d.action("mortimer", ActionType.PRIMARY)
        d.option(1) // swap: Mortimer to (3,-2)
        d.option(1) // and back
        assertThat(d.at("mortimer")).isEqualTo(Hex(1, -2))
        assertThat(d.at(z)).isEqualTo(Hex(3, -2))
        d.expect(DecisionKind.PLAY_CARD)
    }

    @Test
    fun `Crowd Surf - up to three times`() {
        val d = pos {
            clearMinions()
            hero("mortimer", Team.RED, Hex(1, -2)) { played("mortimer-green-3a") }
            hero("brogan", Team.BLUE, Hex(4, -5))
            token(MortimerHero.ZOMBIE, Hex(3, -2), owner = "mortimer")
            phase = PositionPhase.Resolving(next = "mortimer")
        }
        d.action("mortimer", ActionType.PRIMARY)
        d.option(1)
        d.option(1)
        d.option(1)
        assertThat(d.at("mortimer")).isEqualTo(Hex(3, -2))
        d.expect(DecisionKind.PLAY_CARD)
    }

    // ---- Gathering Horde / Army of Darkness (green alternates) ---------------------------------

    @Test
    fun `Gathering Horde - replace an enemy minion in range next to two zombies, once per turn`() {
        lateinit var m1: String
        lateinit var m2: String
        val d = pos {
            clearMinions()
            hero("mortimer", Team.RED, Hex(1, -2)) { played("mortimer-green-2b") } // Range 4
            hero("brogan", Team.BLUE, Hex(4, -5))
            m1 = minion(Team.BLUE, MinionType.MELEE, Hex(3, -2))
            m2 = minion(Team.BLUE, MinionType.MELEE, Hex(-1, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(0, -5))
            minion(Team.RED, MinionType.MELEE, Hex(-1, -5))
            token(MortimerHero.ZOMBIE, Hex(4, -2), owner = "mortimer")
            token(MortimerHero.ZOMBIE, Hex(3, -3), owner = "mortimer")
            token(MortimerHero.ZOMBIE, Hex(-1, -1), owner = "mortimer")
            token(MortimerHero.ZOMBIE, Hex(-2, -2), owner = "mortimer")
            phase = PositionPhase.Resolving(next = "mortimer")
        }
        d.action("mortimer", ActionType.PRIMARY)
        d.option(1)
        assertThat(d.units()).containsExactly(m1, m2)
        d.unit(m1)
        assertThat(d.s.state.minion(m1)).isNull()
        assertThat(d.zombies()).contains(Hex(3, -2))
        assertThat(d.hero("mortimer").coins).isEqualTo(0) // replaced, not defeated: no coins
        // Once per turn: the second choice may only move a zombie.
        d.expect(DecisionKind.CHOOSE_OPTION, "mortimer")
        assertThat(d.options()).containsExactly(0)
        assertThat(d.s.state.minion(m2)).isNotNull()
    }

    @Test
    fun `Gathering Horde - a minion next to only one zombie, or an immune heavy, can't be replaced`() {
        val d = pos {
            clearMinions()
            hero("mortimer", Team.RED, Hex(1, -2)) { played("mortimer-green-2b") }
            hero("brogan", Team.BLUE, Hex(4, -5))
            minion(Team.BLUE, MinionType.HEAVY, Hex(3, -2)) // immune: a Blue melee is in the Zone
            minion(Team.BLUE, MinionType.MELEE, Hex(-1, -2)) // next to one zombie
            token(MortimerHero.ZOMBIE, Hex(4, -2), owner = "mortimer")
            token(MortimerHero.ZOMBIE, Hex(3, -3), owner = "mortimer")
            token(MortimerHero.ZOMBIE, Hex(-2, -2), owner = "mortimer")
            phase = PositionPhase.Resolving(next = "mortimer")
        }
        d.action("mortimer", ActionType.PRIMARY)
        assertThat(d.options()).containsExactly(0)
    }

    @Test
    fun `Army of Darkness - up to three times, replacing the last enemy minion pushes the Lane`() {
        val d = pos {
            clearMinions()
            hero("mortimer", Team.RED, Hex(1, -2)) { played("mortimer-green-3b") }
            hero("brogan", Team.BLUE, Hex(4, -5))
            minion(Team.BLUE, MinionType.MELEE, Hex(3, -2))
            minion(Team.RED, MinionType.MELEE, Hex(-1, -5))
            token(MortimerHero.ZOMBIE, Hex(4, -2), owner = "mortimer")
            token(MortimerHero.ZOMBIE, Hex(3, -3), owner = "mortimer")
            phase = PositionPhase.Resolving(next = "mortimer")
        }
        val waves = d.s.state.wavesLeft.values.single()
        d.action("mortimer", ActionType.PRIMARY)
        d.option(1)
        // The last Blue minion of the Battle Zone was removed: Red pushes (p.8).
        assertThat(d.s.state.wavesLeft.values.single()).isEqualTo(waves - 1)
    }

    // ---- Corpse Slam / Morbid Mosh / Macabre Mayhem (blue) -------------------------------------

    @Test
    fun `Corpse Slam - move a zombie in range up to 1 space, then it may push an adjacent unit 1 space`() {
        lateinit var z: String
        val d = pos {
            clearMinions()
            hero("mortimer", Team.RED, Hex(1, -2)) { played("mortimer-blue-1") } // Range 4
            hero("brogan", Team.BLUE, Hex(3, -2))
            z = token(MortimerHero.ZOMBIE, Hex(1, -1), owner = "mortimer")
            phase = PositionPhase.Resolving(next = "mortimer")
        }
        d.action("mortimer", ActionType.PRIMARY)
        d.option(0)
        // Up to 1 space: "Don't move" is offered.
        assertThat(d.d.options.any { it is Choice.Decline }).isTrue()
        d.space(2, -1) // next to Brogan
        d.expect(DecisionKind.CHOOSE_UNIT, "mortimer")
        assertThat(d.units()).doesNotContain("mortimer") // "a unit" never includes you
        d.unit("brogan")
        // Pushed directly away from the zombie: (2,-1) -> (3,-2) -> (4,-3).
        assertThat(d.at("brogan")).isEqualTo(Hex(4, -3))
        assertThat(d.at(z)).isEqualTo(Hex(2, -1))
    }

    @Test
    fun `Corpse Slam - Choose one, Move 1 space`() {
        val d = pos {
            clearMinions()
            hero("mortimer", Team.RED, Hex(1, -2)) { played("mortimer-blue-1") }
            hero("brogan", Team.BLUE, Hex(4, -5))
            phase = PositionPhase.Resolving(next = "mortimer")
        }
        d.action("mortimer", ActionType.PRIMARY)
        // No zombie in range: "Move 1 space" is the only bullet, and exactly 1 space.
        assertThat(d.spaces().all { HexGrid.distance(it, Hex(1, -2)) == 1 }).isTrue()
        d.space(2, -2)
        assertThat(d.at("mortimer")).isEqualTo(Hex(2, -2))
    }

    @Test
    fun `Morbid Mosh - up to two times, a zombie may push a token`() {
        lateinit var rock: String
        val d = pos {
            clearMinions()
            hero("mortimer", Team.RED, Hex(1, -2)) { played("mortimer-blue-2a") }
            hero("brogan", Team.BLUE, Hex(4, -5))
            token(MortimerHero.ZOMBIE, Hex(3, -2), owner = "mortimer")
            rock = token("token_rock", Hex(4, -2))
            phase = PositionPhase.Resolving(next = "mortimer")
        }
        d.action("mortimer", ActionType.PRIMARY)
        d.option(0)
        d.decline() // the zombie stays
        d.token(rock)
        assertThat(d.at(rock)).isEqualTo(Hex(4, -2)) // (5,-2) is terrain: pushed 0 spaces
        d.option(1)
        d.space(0, -2)
        assertThat(d.at("mortimer")).isEqualTo(Hex(0, -2))
    }

    @Test
    fun `Macabre Mayhem - up to three times`() {
        val d = pos {
            clearMinions()
            hero("mortimer", Team.RED, Hex(1, -2)) { played("mortimer-blue-3a") }
            hero("brogan", Team.BLUE, Hex(4, -5))
            phase = PositionPhase.Resolving(next = "mortimer")
        }
        d.action("mortimer", ActionType.PRIMARY)
        d.option(1)
        d.space(2, -2)
        d.option(1)
        d.space(3, -2)
        d.option(1)
        d.space(4, -2)
        assertThat(d.at("mortimer")).isEqualTo(Hex(4, -2))
        d.expect(DecisionKind.PLAY_CARD)
    }

    // ---- Robbing Zombies / Stalking Scalpers (blue alternates) ---------------------------------

    @Test
    fun `Robbing Zombies - move a zombie in range up to 1 space and gain 1 coin, up to two times`() {
        val d = pos {
            clearMinions()
            hero("mortimer", Team.RED, Hex(1, -2)) { played("mortimer-blue-2b") }
            hero("brogan", Team.BLUE, Hex(4, -5))
            token(MortimerHero.ZOMBIE, Hex(3, -2), owner = "mortimer")
            phase = PositionPhase.Resolving(next = "mortimer")
        }
        d.action("mortimer", ActionType.PRIMARY)
        d.option(0)
        d.space(4, -2)
        d.option(0)
        d.decline() // up to 1 space: 0 is allowed, the coin still comes
        assertThat(d.hero("mortimer").coins).isEqualTo(2)
    }

    @Test
    fun `Robbing Zombies - no zombie in range, no coin (APP)`() {
        val d = pos {
            clearMinions()
            hero("mortimer", Team.RED, Hex(1, -2)) { played("mortimer-blue-2b") }
            hero("brogan", Team.BLUE, Hex(4, -5))
            token(MortimerHero.ZOMBIE, Hex(-4, -2), owner = "mortimer") // 5 away
            phase = PositionPhase.Resolving(next = "mortimer")
        }
        d.action("mortimer", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_OPTION, "mortimer")
        assertThat(d.options()).containsExactly(1)
    }

    @Test
    fun `Stalking Scalpers - up to three times`() {
        val d = pos {
            clearMinions()
            hero("mortimer", Team.RED, Hex(1, -2)) { played("mortimer-blue-3b") }
            hero("brogan", Team.BLUE, Hex(4, -5))
            token(MortimerHero.ZOMBIE, Hex(3, -2), owner = "mortimer")
            phase = PositionPhase.Resolving(next = "mortimer")
        }
        d.action("mortimer", ActionType.PRIMARY)
        repeat(3) {
            d.option(0)
            d.decline()
        }
        assertThat(d.hero("mortimer").coins).isEqualTo(3)
        d.expect(DecisionKind.PLAY_CARD)
    }

    // ---- Master of Puppets (Ultimate) ----------------------------------------------------------

    @Test
    fun `Master of Puppets - up to three times becomes up to five times`() {
        val d = pos {
            clearMinions()
            hero("mortimer", Team.RED, Hex(1, -2)) {
                played("mortimer-blue-3b")
                ultimate()
            }
            hero("brogan", Team.BLUE, Hex(4, -5))
            token(MortimerHero.ZOMBIE, Hex(3, -2), owner = "mortimer")
            phase = PositionPhase.Resolving(next = "mortimer")
        }
        d.action("mortimer", ActionType.PRIMARY)
        repeat(5) {
            d.option(0)
            d.decline()
        }
        assertThat(d.hero("mortimer").coins).isEqualTo(5)
        d.expect(DecisionKind.PLAY_CARD)
    }

    @Test
    fun `Master of Puppets - up to two times stays two`() {
        val d = pos {
            clearMinions()
            hero("mortimer", Team.RED, Hex(1, -2)) {
                played("mortimer-blue-2b")
                ultimate()
            }
            hero("brogan", Team.BLUE, Hex(4, -5))
            token(MortimerHero.ZOMBIE, Hex(3, -2), owner = "mortimer")
            phase = PositionPhase.Resolving(next = "mortimer")
        }
        d.action("mortimer", ActionType.PRIMARY)
        repeat(2) {
            d.option(0)
            d.decline()
        }
        assertThat(d.hero("mortimer").coins).isEqualTo(2)
        d.expect(DecisionKind.PLAY_CARD)
    }

    // ---- random games ----------------------------------------------------------------------------

    @Test
    fun `random legal games with Mortimer keep every invariant and end`() {
        val r = HeroGames.play("mortimer", games = 300, seed = 53)
        println("MORTIMER: ${r.games} games, ${r.decisions} decisions, cards played ${r.cardsPlayed.sorted()}")
        assertThat(r.cardsPlayed).containsAtLeast(
            "mortimer-gold",
            "mortimer-silver",
            "mortimer-red-1",
            "mortimer-green-1",
            "mortimer-blue-1",
        )
    }
}
