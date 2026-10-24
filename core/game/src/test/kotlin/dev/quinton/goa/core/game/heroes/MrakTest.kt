package dev.quinton.goa.core.game.heroes

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.ActionType
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
 * Mrak the Rockshaper: every card, clause by clause (text from `catalog/heroes/mrak.json`; Mrak has
 * no hero FAQ), with the readings in `core/game/rulings/mrak.md`. Forgotten Island: row r = −2,
 * q = −1..4 is Battle Zone `lane-1-2`; terrain at (1,−3), (5,−2), (6,−2), (−2,−1). Of that row only
 * (2,−2) and (3,−2) are not adjacent to terrain.
 */
class MrakTest {
    private fun Driver.rocks(): List<Hex> = s.state.pieces.filter { it.kind == MrakHero.ROCK }.mapNotNull { it.at }

    // ---- Fissure (gold) / Lesser Fissure (Handicap) --------------------------------------------

    @Test
    fun `Fissure - After the attack, a Rock token in each of the first three empty spaces in the line`() {
        val d = pos {
            clearMinions()
            hero("mrak", Team.RED, Hex(0, -2)) { played("mrak-gold") }
            hero("brogan", Team.BLUE, Hex(4, -5))
            minion(Team.BLUE, MinionType.MELEE, Hex(1, -2)) // east of Mrak
            phase = PositionPhase.Resolving(next = "mrak")
        }
        d.action("mrak", ActionType.PRIMARY)
        // The minion is defeated: its space is empty and gets the first rock.
        assertThat(d.rocks()).containsExactly(Hex(1, -2), Hex(2, -2), Hex(3, -2))
        assertThat(d.s.state.pieces.all { it.owner == "mrak" }).isTrue()
    }

    @Test
    fun `Fissure - occupied spaces in the line are skipped`() {
        val d = pos {
            clearMinions()
            hero("mrak", Team.RED, Hex(0, -2)) { played("mrak-gold") }
            hero("brogan", Team.BLUE, Hex(1, -2)) { hand("brogan-blue-1") } // defends: stays
            hero("arien", Team.BLUE, Hex(3, -2))
            phase = PositionPhase.Resolving(next = "mrak")
        }
        d.action("mrak", ActionType.PRIMARY)
        d.defend("brogan-blue-1") // 5 against 4
        // (1,-2) Brogan, (3,-2) Arien, (5,-2) and (6,-2) terrain: skipped.
        assertThat(d.rocks()).containsExactly(Hex(2, -2), Hex(4, -2), Hex(7, -2))
    }

    @Test
    fun `Lesser Fissure - Target a hero adjacent to you, not a minion`() {
        val d = pos {
            clearMinions()
            hero("mrak", Team.RED, Hex(0, -2)) { played("mrak-handicap") }
            hero("brogan", Team.BLUE, Hex(4, -5))
            minion(Team.BLUE, MinionType.MELEE, Hex(1, -2))
            phase = PositionPhase.Resolving(next = "mrak")
        }
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
    }

    @Test
    fun `Lesser Fissure - the rocks follow the direction of the attack`() {
        val d = pos {
            clearMinions()
            hero("mrak", Team.RED, Hex(1, -2)) { played("mrak-handicap") }
            hero("brogan", Team.BLUE, Hex(1, -1)) { hand("brogan-blue-1") } // south-east of Mrak
            phase = PositionPhase.Resolving(next = "mrak")
        }
        d.action("mrak", ActionType.PRIMARY)
        d.defend("brogan-blue-1")
        // (1,2) is terrain: skipped.
        assertThat(d.rocks()).containsExactly(Hex(1, 0), Hex(1, 1), Hex(1, 3))
    }

    // ---- Stone Grip (silver) -------------------------------------------------------------------

    @Test
    fun `Stone Grip - exactly 3 Rock tokens next to an enemy hero in range, as far from you as possible`() {
        val d = pos {
            clearMinions()
            hero("mrak", Team.RED, Hex(1, -2)) { played("mrak-silver") } // Range 3
            hero("brogan", Team.BLUE, Hex(3, -2)) // 2 away
            phase = PositionPhase.Resolving(next = "mrak")
        }
        d.action("mrak", ActionType.PRIMARY)
        // Around Brogan, the three spaces 3 away from Mrak: no choice.
        assertThat(d.rocks()).containsExactly(Hex(4, -2), Hex(4, -3), Hex(3, -1))
    }

    @Test
    fun `Stone Grip - Mrak picks among spaces tied for the farthest`() {
        val d = pos {
            clearMinions()
            hero("mrak", Team.RED, Hex(1, -2)) { played("mrak-silver") }
            hero("brogan", Team.BLUE, Hex(3, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(3, -1)) // one of the far spaces is taken
            phase = PositionPhase.Resolving(next = "mrak")
        }
        d.action("mrak", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_SPACE, "mrak")
        // (4,-2) and (4,-3) are placed; the third is one of the two spaces 2 away.
        assertThat(d.spaces()).containsExactly(Hex(3, -3), Hex(2, -1))
        d.space(2, -1)
        assertThat(d.rocks()).containsExactly(Hex(4, -2), Hex(4, -3), Hex(2, -1))
    }

    @Test
    fun `Stone Grip - an enemy hero with fewer than 3 empty spaces around can't be chosen`() {
        val d = pos {
            clearMinions()
            hero("mrak", Team.RED, Hex(1, -2)) { played("mrak-silver") }
            hero("brogan", Team.BLUE, Hex(4, -2)) // terrain at (5,-2)
            minion(Team.BLUE, MinionType.MELEE, Hex(4, -3))
            minion(Team.BLUE, MinionType.MELEE, Hex(5, -3))
            minion(Team.BLUE, MinionType.MELEE, Hex(3, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(3, -1))
            phase = PositionPhase.Resolving(next = "mrak")
        }
        // Brogan has only (4,-1) empty next to him: "exactly 3" can't be done.
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
    }

    // ---- Seismic Slam / Seismic Assault / Epicenter (red) --------------------------------------

    @Test
    fun `Seismic Slam - an enemy hero in radius adjacent to terrain or a Rock token discards or is defeated`() {
        val d = pos {
            clearMinions()
            hero("mrak", Team.RED, Hex(1, -2)) { played("mrak-red-1") } // Radius 3
            hero("brogan", Team.BLUE, Hex(4, -2)) { hand("brogan-gold", "brogan-blue-1") } // next to terrain
            hero("arien", Team.BLUE, Hex(3, -2)) { hand("arien-gold") } // next to the rock
            hero("wasp", Team.BLUE, Hex(2, -2)) { hand("wasp-gold") } // next to neither
            token(MrakHero.ROCK, Hex(4, -3))
            phase = PositionPhase.Resolving(next = "mrak")
        }
        d.action("mrak", ActionType.PRIMARY)
        assertThat(d.units()).containsExactly("brogan", "arien")
        d.unit("brogan")
        d.expect(DecisionKind.CHOOSE_CARD, "brogan")
        d.card("brogan-gold")
        assertThat(d.zoneOf("brogan", "brogan-gold")).isEqualTo(Zone.DISCARD)
    }

    @Test
    fun `Seismic Slam - with no card to discard, the hero is defeated`() {
        val d = pos {
            clearMinions()
            hero("mrak", Team.RED, Hex(1, -2)) { played("mrak-red-1") }
            hero("brogan", Team.BLUE, Hex(4, -2)) { hand() }
            phase = PositionPhase.Resolving(next = "mrak")
        }
        d.action("mrak", ActionType.PRIMARY)
        assertThat(d.at("brogan")).isNull()
        assertThat(d.hero("mrak").coins).isEqualTo(1)
    }

    @Test
    fun `Seismic Slam - the secondary Attack action is non-ranged`() {
        val d = pos {
            clearMinions()
            hero("mrak", Team.RED, Hex(1, -2)) { played("mrak-red-1") }
            hero("brogan", Team.BLUE, Hex(3, -2)) // 2 away, not next to terrain
            phase = PositionPhase.Resolving(next = "mrak")
        }
        assertThat(d.actions()).doesNotContain(ActionType.ATTACK)
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
    }

    @Test
    fun `Seismic Assault - Radius 4`() {
        val d = pos {
            clearMinions()
            hero("mrak", Team.RED, Hex(0, -2)) { played("mrak-red-2a") }
            hero("brogan", Team.BLUE, Hex(4, -2)) { hand("brogan-gold") } // 4 away, next to terrain
            phase = PositionPhase.Resolving(next = "mrak")
        }
        d.action("mrak", ActionType.PRIMARY)
        assertThat(d.zoneOf("brogan", "brogan-gold")).isEqualTo(Zone.DISCARD)
    }

    @Test
    fun `Seismic Slam - a Radius item widens the radius`() {
        val d = pos {
            clearMinions()
            hero("mrak", Team.RED, Hex(0, -2)) {
                played("mrak-red-1") // Radius 3
                items("mrak-green-3a") // item: +1 Radius
            }
            hero("brogan", Team.BLUE, Hex(4, -2)) { hand("brogan-gold") } // 4 away, next to terrain
            phase = PositionPhase.Resolving(next = "mrak")
        }
        d.action("mrak", ActionType.PRIMARY)
        assertThat(d.zoneOf("brogan", "brogan-gold")).isEqualTo(Zone.DISCARD)
    }

    @Test
    fun `Epicenter - May repeat once on a different target`() {
        val d = pos {
            clearMinions()
            hero("mrak", Team.RED, Hex(1, -2)) { played("mrak-red-3a") }
            hero("brogan", Team.BLUE, Hex(4, -2)) { hand("brogan-gold") }
            hero("arien", Team.BLUE, Hex(0, -2)) { hand("arien-gold") } // next to terrain (1,-3)
            phase = PositionPhase.Resolving(next = "mrak")
        }
        d.action("mrak", ActionType.PRIMARY)
        d.unit("brogan")
        d.expect(DecisionKind.CONFIRM, "mrak")
        d.yes()
        // Brogan was the target: only Arien now.
        assertThat(d.zoneOf("arien", "arien-gold")).isEqualTo(Zone.DISCARD)
        assertThat(d.zoneOf("brogan", "brogan-gold")).isEqualTo(Zone.DISCARD)
    }

    // ---- Treacherous Ground / Rockslide / Avalanche (green) ------------------------------------

    @Test
    fun `Treacherous Ground - You may move a unit in range 1 space to a space adjacent to terrain or a rock`() {
        val d = pos {
            clearMinions()
            hero("mrak", Team.RED, Hex(2, -2)) { played("mrak-green-1") } // Range 3
            hero("brogan", Team.BLUE, Hex(3, -2))
            token(MrakHero.ROCK, Hex(1, -1))
            phase = PositionPhase.Resolving(next = "mrak")
        }
        d.action("mrak", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_UNIT, "mrak")
        d.unit("brogan")
        // (4,-2) is next to terrain, (2,-1) next to the rock; Brogan's other neighbours are next to neither.
        assertThat(d.spaces()).containsExactly(Hex(4, -2), Hex(2, -1))
        d.space(2, -1)
        assertThat(d.at("brogan")).isEqualTo(Hex(2, -1))
    }

    @Test
    fun `Treacherous Ground - you may, so Mrak can decline, and a friendly unit can be moved too`() {
        val d = pos {
            clearMinions()
            hero("mrak", Team.RED, Hex(2, -2)) { played("mrak-green-1") }
            hero("wasp", Team.RED, Hex(3, -2))
            hero("brogan", Team.BLUE, Hex(-4, -1))
            phase = PositionPhase.Resolving(next = "mrak")
        }
        d.action("mrak", ActionType.PRIMARY)
        assertThat(d.units()).containsExactly("wasp")
        d.decline()
        assertThat(d.at("wasp")).isEqualTo(Hex(3, -2))
    }

    @Test
    fun `Rockslide - Range 4`() {
        val d = pos {
            clearMinions()
            hero("mrak", Team.RED, Hex(-1, -2)) { played("mrak-green-2a") }
            hero("brogan", Team.BLUE, Hex(3, -2)) // 4 away
            phase = PositionPhase.Resolving(next = "mrak")
        }
        d.action("mrak", ActionType.PRIMARY)
        d.unit("brogan") // (4,-2) is its only space next to terrain: no question
        assertThat(d.at("brogan")).isEqualTo(Hex(4, -2))
    }

    @Test
    fun `Avalanche - May repeat once`() {
        val d = pos {
            clearMinions()
            hero("mrak", Team.RED, Hex(2, -2)) { played("mrak-green-3a") }
            hero("brogan", Team.BLUE, Hex(3, -2))
            phase = PositionPhase.Resolving(next = "mrak")
        }
        d.action("mrak", ActionType.PRIMARY)
        d.unit("brogan") // (4,-2) is the only space: no question
        assertThat(d.at("brogan")).isEqualTo(Hex(4, -2))
        d.expect(DecisionKind.CONFIRM, "mrak")
        d.yes()
        d.unit("brogan")
        assertThat(d.spaces()).isNotEmpty()
    }

    // ---- Boulder Rush / Boulder Blitz / Boulderdozer (blue) ------------------------------------

    @Test
    fun `Boulder Rush - push 1 or 2 ignoring obstacles, then you may move up to 2 in that direction`() {
        val d = pos {
            clearMinions()
            hero("mrak", Team.RED, Hex(-1, -2)) { played("mrak-blue-1") }
            hero("brogan", Team.BLUE, Hex(0, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(1, -2))
            phase = PositionPhase.Resolving(next = "mrak")
        }
        d.action("mrak", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_NUMBER, "mrak")
        d.number(2)
        assertThat(d.at("brogan")).isEqualTo(Hex(2, -2)) // through the minion
        // Only spaces east of Mrak (the push direction), up to 2, ignoring obstacles.
        assertThat(d.spaces()).containsExactly(Hex(0, -2))
        d.space(0, -2)
        assertThat(d.at("mrak")).isEqualTo(Hex(0, -2))
    }

    @Test
    fun `Boulder Rush - a token can be pushed, a friend can't, and a Bulwark stops the push`() {
        lateinit var rock: String
        val d = pos {
            clearMinions()
            hero("mrak", Team.RED, Hex(1, -2)) { played("mrak-blue-1") }
            hero("brogan", Team.BLUE, Hex(2, -2))
            hero("wasp", Team.RED, Hex(0, -2))
            rock = token(MrakHero.ROCK, Hex(1, -1))
            effect("brogan", "brogan-silver", Timing.THIS_TURN, ActionKind.SKILL)
            phase = PositionPhase.Resolving(next = "mrak")
        }
        d.action("mrak", ActionType.PRIMARY)
        // Brogan can't be pushed by an enemy hero (Bulwark), Wasp is friendly: only the rock.
        d.expect(DecisionKind.CHOOSE_NUMBER, "mrak")
        d.number(1)
        assertThat(d.at(rock)).isEqualTo(Hex(1, 0))
    }

    @Test
    fun `Boulder Rush - an immune heavy minion adjacent is not offered`() {
        val d = pos {
            clearMinions()
            hero("mrak", Team.RED, Hex(1, -2)) { played("mrak-blue-1") }
            hero("brogan", Team.BLUE, Hex(4, -5))
            minion(Team.BLUE, MinionType.HEAVY, Hex(2, -2)) // immune: a Blue melee is in the Battle Zone
            minion(Team.BLUE, MinionType.MELEE, Hex(-1, -5))
            phase = PositionPhase.Resolving(next = "mrak")
        }
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
    }

    @Test
    fun `Boulder Blitz - push up to 3, move up to 3`() {
        val d = pos {
            clearMinions()
            hero("mrak", Team.RED, Hex(-1, -2)) { played("mrak-blue-2a") }
            hero("brogan", Team.BLUE, Hex(0, -2))
            phase = PositionPhase.Resolving(next = "mrak")
        }
        d.action("mrak", ActionType.PRIMARY)
        assertThat(d.d.options.size).isEqualTo(3)
        d.number(3)
        assertThat(d.at("brogan")).isEqualTo(Hex(3, -2))
        assertThat(d.spaces()).containsExactly(Hex(0, -2), Hex(1, -2), Hex(2, -2))
        d.decline()
        assertThat(d.at("mrak")).isEqualTo(Hex(-1, -2))
    }

    @Test
    fun `Boulderdozer - push up to 4, move up to 4 through obstacles`() {
        val d = pos {
            clearMinions()
            hero("mrak", Team.RED, Hex(-1, -2)) { played("mrak-blue-3a") }
            hero("brogan", Team.BLUE, Hex(0, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(1, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(2, -2))
            phase = PositionPhase.Resolving(next = "mrak")
        }
        d.action("mrak", ActionType.PRIMARY)
        assertThat(d.d.options.size).isEqualTo(4)
        d.number(4)
        assertThat(d.at("brogan")).isEqualTo(Hex(4, -2)) // through both minions
        assertThat(d.spaces()).containsExactly(Hex(0, -2), Hex(3, -2))
        d.space(3, -2)
        assertThat(d.at("mrak")).isEqualTo(Hex(3, -2))
    }

    // ---- Stone Carapace / Rock Solid (red alternates) ------------------------------------------

    @Test
    fun `Stone Carapace - This round, discard this card instead of a card from your hand`() {
        lateinit var m: String
        val d = pos {
            clearMinions()
            hero("mrak", Team.RED, Hex(1, -2)) {
                resolved(1, "mrak-red-2b")
                hand("mrak-gold", "mrak-silver")
            }
            hero("wasp", Team.BLUE, Hex(3, -2)) { played("wasp-red-1") } // Shock: radius 2
            m = minion(Team.RED, MinionType.MELEE, Hex(4, -2))
            effect("mrak", "mrak-red-2b", Timing.THIS_ROUND, ActionKind.MOVEMENT, madeTurn = 1)
            turn = 2
            phase = PositionPhase.Resolving(next = "wasp")
        }
        d.action("wasp", ActionType.PRIMARY)
        assertThat(d.s.state.minion(m)).isNull()
        // Mrak (2 away, not adjacent) must discard: he picks a card, then may discard Stone Carapace instead.
        d.expect(DecisionKind.CHOOSE_CARD, "mrak")
        d.card("mrak-gold")
        d.expect(DecisionKind.CONFIRM, "mrak")
        d.yes()
        assertThat(d.zoneOf("mrak", "mrak-gold")).isEqualTo(Zone.HAND)
        assertThat(d.zoneOf("mrak", "mrak-red-2b")).isEqualTo(Zone.DISCARD)
        // The card changed state: its effect is over.
        assertThat(d.s.state.effects.map { it.cardId }).doesNotContain("mrak-red-2b")
    }

    @Test
    fun `Stone Carapace - you may, so Mrak can discard the hand card`() {
        val d = pos {
            clearMinions()
            hero("mrak", Team.RED, Hex(1, -2)) {
                resolved(1, "mrak-red-2b")
                hand("mrak-gold")
            }
            hero("wasp", Team.BLUE, Hex(3, -2)) { played("wasp-red-1") }
            minion(Team.RED, MinionType.MELEE, Hex(4, -2))
            effect("mrak", "mrak-red-2b", Timing.THIS_ROUND, ActionKind.MOVEMENT, madeTurn = 1)
            turn = 2
            phase = PositionPhase.Resolving(next = "wasp")
        }
        d.action("wasp", ActionType.PRIMARY)
        d.expect(DecisionKind.CONFIRM, "mrak")
        d.decline()
        assertThat(d.zoneOf("mrak", "mrak-gold")).isEqualTo(Zone.DISCARD)
        assertThat(d.zoneOf("mrak", "mrak-red-2b")).isEqualTo(Zone.RESOLVED)
    }

    @Test
    fun `Stone Carapace - with no card in hand there is nothing to replace, so discard or defeat defeats`() {
        val d = pos {
            clearMinions()
            hero("mrak", Team.RED, Hex(1, -2)) {
                resolved(1, "mrak-red-2b")
                hand()
            }
            hero("wasp", Team.BLUE, Hex(3, -2)) { played("wasp-red-3a") } // Electroblast: discard, or defeated
            minion(Team.RED, MinionType.MELEE, Hex(4, -2))
            effect("mrak", "mrak-red-2b", Timing.THIS_ROUND, ActionKind.MOVEMENT, madeTurn = 1)
            turn = 2
            phase = PositionPhase.Resolving(next = "wasp")
        }
        d.action("wasp", ActionType.PRIMARY)
        assertThat(d.at("mrak")).isNull()
    }

    @Test
    fun `Stone Carapace - discard it to perform its defense action, as if it was in your hand`() {
        val d = pos {
            clearMinions()
            hero("mrak", Team.RED, Hex(1, -2)) {
                resolved(1, "mrak-red-2b") // Defense 6
                hand("mrak-silver") // Defense 4
            }
            hero("arien", Team.BLUE, Hex(2, -2)) { played("arien-red-1") } // Attack 6
            effect("mrak", "mrak-red-2b", Timing.THIS_ROUND, ActionKind.MOVEMENT, madeTurn = 1)
            turn = 2
            phase = PositionPhase.Resolving(next = "arien")
        }
        d.action("arien", ActionType.PRIMARY)
        d.expect(DecisionKind.DEFEND, "mrak")
        assertThat(d.cards()).containsExactly("mrak-silver", "mrak-red-2b")
        d.card("mrak-red-2b")
        assertThat(d.at("mrak")).isEqualTo(Hex(1, -2)) // 6 against 6
        assertThat(d.zoneOf("mrak", "mrak-red-2b")).isEqualTo(Zone.DISCARD)
        assertThat(d.zoneOf("mrak", "mrak-silver")).isEqualTo(Zone.HAND)
    }

    @Test
    fun `Stone Carapace - the primary starts the effect, then Mrak moves`() {
        val d = pos {
            clearMinions()
            hero("mrak", Team.RED, Hex(1, -2)) { played("mrak-red-2b") } // Movement 4
            hero("brogan", Team.BLUE, Hex(4, -5))
            phase = PositionPhase.Resolving(next = "mrak")
        }
        d.action("mrak", ActionType.PRIMARY)
        assertThat(d.s.state.effects.map { it.cardId }).contains("mrak-red-2b")
        d.expect(DecisionKind.CHOOSE_SPACE, "mrak")
        d.space(3, -2)
        assertThat(d.at("mrak")).isEqualTo(Hex(3, -2))
    }

    @Test
    fun `Rock Solid - You may retrieve a discarded card, then the same This round effect`() {
        val d = pos {
            clearMinions()
            hero("mrak", Team.RED, Hex(1, -2)) {
                played("mrak-red-3b")
                hand("mrak-gold")
            }
            hero("brogan", Team.BLUE, Hex(4, -5))
            phase = PositionPhase.Resolving(next = "mrak")
        }
        d.action("mrak", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_CARD, "mrak")
        d.card("mrak-silver")
        assertThat(d.zoneOf("mrak", "mrak-silver")).isEqualTo(Zone.HAND)
        assertThat(d.s.state.effects.map { it.cardId }).contains("mrak-red-3b")
        d.decline() // no movement
    }

    // ---- Rolling Stone / Strolling Stone (green alternates) ------------------------------------

    @Test
    fun `Rolling Stone - any number of spaces straight through obstacles, through at most one empty space`() {
        val d = pos {
            clearMinions()
            hero("mrak", Team.RED, Hex(-1, -2)) { played("mrak-green-2b") }
            hero("brogan", Team.BLUE, Hex(0, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(1, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(3, -2))
            phase = PositionPhase.Resolving(next = "mrak")
        }
        d.action("mrak", ActionType.PRIMARY)
        // East: (2,-2) passes no empty space, (4,-2) passes one, (7,-2) passes two.
        assertThat(d.spaces()).containsAtLeast(Hex(2, -2), Hex(4, -2))
        assertThat(d.spaces()).doesNotContain(Hex(7, -2))
        assertThat(d.spaces()).doesNotContain(Hex(3, -3)) // not a straight line
        d.space(4, -2)
        assertThat(d.at("mrak")).isEqualTo(Hex(4, -2))
    }

    @Test
    fun `Strolling Stone - through at most two empty spaces`() {
        val d = pos {
            clearMinions()
            hero("mrak", Team.RED, Hex(-1, -2)) { played("mrak-green-3b") }
            hero("brogan", Team.BLUE, Hex(0, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(1, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(3, -2))
            phase = PositionPhase.Resolving(next = "mrak")
        }
        d.action("mrak", ActionType.PRIMARY)
        assertThat(d.spaces()).containsAtLeast(Hex(2, -2), Hex(4, -2), Hex(7, -2))
    }

    // ---- Stomping Step / Ground Shaker (blue alternates) ---------------------------------------

    @Test
    fun `Stomping Step - move a unit in radius next to terrain or a rock 1 space, a rock where it stood`() {
        val d = pos {
            clearMinions()
            hero("mrak", Team.RED, Hex(1, -2)) { played("mrak-blue-2b") } // Radius 4
            hero("brogan", Team.BLUE, Hex(4, -2)) // next to terrain (5,-2), 3 away
            hero("arien", Team.BLUE, Hex(3, -2)) // next to neither: not offered
            phase = PositionPhase.Resolving(next = "mrak")
        }
        d.action("mrak", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_SPACE, "mrak")
        d.space(4, -3)
        assertThat(d.at("brogan")).isEqualTo(Hex(4, -3))
        assertThat(d.rocks()).containsExactly(Hex(4, -2))
    }

    @Test
    fun `Ground Shaker - May repeat once on a different target`() {
        val d = pos {
            clearMinions()
            hero("mrak", Team.RED, Hex(1, -2)) { played("mrak-blue-3b") }
            hero("brogan", Team.BLUE, Hex(4, -2))
            hero("arien", Team.BLUE, Hex(0, -2)) // next to terrain (1,-3)
            phase = PositionPhase.Resolving(next = "mrak")
        }
        d.action("mrak", ActionType.PRIMARY)
        d.unit("brogan")
        d.space(4, -3) // now next to the new rock: still a candidate, but excluded on the repeat
        d.yes()
        // Arien is the only other target: no question, his move is asked.
        d.expect(DecisionKind.CHOOSE_SPACE, "mrak")
        assertThat(d.d.context.subjectId).isEqualTo("arien")
    }

    // ---- Rock and a Hard Place (Ultimate) ------------------------------------------------------

    @Test
    fun `Rock and a Hard Place - once per turn, after rocks are placed next to enemy heroes, they discard`() {
        val d = pos {
            clearMinions()
            hero("mrak", Team.RED, Hex(1, -2)) {
                played("mrak-blue-3b")
                ultimate()
            }
            hero("brogan", Team.BLUE, Hex(4, -2)) { hand("brogan-gold", "brogan-silver") }
            hero("arien", Team.BLUE, Hex(0, -2)) { hand("arien-gold", "arien-silver") }
            phase = PositionPhase.Resolving(next = "mrak")
        }
        d.action("mrak", ActionType.PRIMARY)
        d.unit("brogan")
        d.space(4, -3) // the rock goes into (4,-2), next to Brogan
        d.expect(DecisionKind.CHOOSE_CARD, "brogan")
        d.card("brogan-gold")
        d.yes() // repeat on Arien (the only other target)
        d.space(0, -1) // a rock into (0,-2), next to Arien: the Ultimate was used this turn
        assertThat(d.zoneOf("arien", "arien-gold")).isEqualTo(Zone.HAND)
        assertThat(d.zoneOf("arien", "arien-silver")).isEqualTo(Zone.HAND)
    }

    @Test
    fun `Rock and a Hard Place - Fissure's three rocks are one placement, each hero next to one discards`() {
        val d = pos {
            clearMinions()
            hero("mrak", Team.RED, Hex(0, -2)) {
                played("mrak-gold")
                ultimate()
            }
            minion(Team.BLUE, MinionType.MELEE, Hex(1, -2))
            hero("brogan", Team.BLUE, Hex(2, -1)) { hand("brogan-gold") } // next to (2,-2)
            hero("arien", Team.BLUE, Hex(3, -3)) { hand("arien-gold") } // next to (3,-2)
            hero("wasp", Team.BLUE, Hex(4, -5)) { hand("wasp-gold") } // far
            phase = PositionPhase.Resolving(next = "mrak")
        }
        d.action("mrak", ActionType.PRIMARY)
        assertThat(d.zoneOf("brogan", "brogan-gold")).isEqualTo(Zone.DISCARD)
        assertThat(d.zoneOf("arien", "arien-gold")).isEqualTo(Zone.DISCARD)
        assertThat(d.zoneOf("wasp", "wasp-gold")).isEqualTo(Zone.HAND)
    }

    @Test
    fun `Rock and a Hard Place - an immune enemy hero next to the rocks neither counts nor discards`() {
        val d = pos {
            clearMinions()
            hero("mrak", Team.RED, Hex(0, -2)) {
                played("mrak-gold")
                ultimate()
            }
            minion(Team.BLUE, MinionType.MELEE, Hex(1, -2))
            hero("brogan", Team.BLUE, Hex(2, -1)) { hand("brogan-gold") } // next to (2,-2)
            // Master Duelist: immune to attack actions (Fissure) of enemy heroes other than Wasp.
            hero("arien", Team.BLUE, Hex(3, -3)) { hand("arien-gold") }
            effect("arien", "arien-blue-3a", Timing.THIS_ROUND, ActionKind.DEFENSE, data = mapOf("attacker" to "wasp"))
            phase = PositionPhase.Resolving(next = "mrak")
        }
        d.action("mrak", ActionType.PRIMARY)
        assertThat(d.zoneOf("brogan", "brogan-gold")).isEqualTo(Zone.DISCARD)
        assertThat(d.zoneOf("arien", "arien-gold")).isEqualTo(Zone.HAND)
    }

    // ---- random games ----------------------------------------------------------------------------

    @Test
    fun `random legal games with Mrak keep every invariant and end`() {
        val r = HeroGames.play("mrak", games = 300, seed = 52)
        println("MRAK: ${r.games} games, ${r.decisions} decisions, cards played ${r.cardsPlayed.sorted()}")
        assertThat(
            r.cardsPlayed,
        ).containsAtLeast("mrak-gold", "mrak-silver", "mrak-red-1", "mrak-green-1", "mrak-blue-1")
    }
}
