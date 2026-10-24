package dev.quinton.goa.core.game.heroes

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.Driver
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.game.heroes.batch2.Batch2Games
import dev.quinton.goa.core.game.pos
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

/** Bain the Bounty Hunter: every card, clause by clause (text from `catalog/heroes/bain.json`). */
class BainTest {
    private val bounty = BainHero.BOUNTY

    private fun Driver.hasBounty(hero: String) = hero(hero).markers.any { it.kind == bounty }

    private fun Driver.minionAt(h: Hex) = s.state.minions.firstOrNull { it.at == h }

    private fun Driver.labels() = d.options.filterIsInstance<Choice.Option>().map { it.label }

    // ---- Dead or Alive (gold) and the Bounty marker ---------------------------------------------

    @Test
    fun `Dead or Alive - After the attack, you may give an enemy hero in radius the Bounty marker`() {
        val d = pos {
            clearMinions()
            hero("bain", Team.RED, Hex(1, -2)) { played("bain-gold") } // radius 4
            hero("arien", Team.BLUE, Hex(2, -2)) { hand("arien-blue-1") }
            hero("brogan", Team.BLUE, Hex(3, -4))
            hero("wasp", Team.RED, Hex(0, -2)) // friendly: not offered
            phase = PositionPhase.Resolving(next = "bain")
        }
        d.action("bain", ActionType.PRIMARY) // Arien is the only adjacent enemy: no question
        d.defend("arien-blue-1")
        d.expect(DecisionKind.CHOOSE_UNIT, "bain")
        assertThat(d.units()).containsExactly("arien", "brogan")
        assertThat(d.d.options.any { it is Choice.Decline }).isTrue()
        d.unit("brogan")
        assertThat(d.hasBounty("brogan")).isTrue()
        assertThat(d.hero("brogan").markers.single().from).isEqualTo("bain")
    }

    @Test
    fun `Bounty marker - giving it again moves the one marker, and its holder is not offered`() {
        val d = pos {
            clearMinions()
            hero("bain", Team.RED, Hex(1, -2)) { played("bain-gold") }
            hero("arien", Team.BLUE, Hex(2, -2)) {
                hand("arien-blue-1")
                marker(BainHero.BOUNTY, from = "bain")
            }
            hero("brogan", Team.BLUE, Hex(3, -4))
            phase = PositionPhase.Resolving(next = "bain")
        }
        d.action("bain", ActionType.PRIMARY) // Arien is the only adjacent enemy: no question
        d.defend("arien-blue-1")
        // Only Brogan: Arien already holds it. One option, but "you may": still asked.
        assertThat(d.units()).containsExactly("brogan")
        d.unit("brogan")
        assertThat(d.hasBounty("arien")).isFalse()
        assertThat(d.hasBounty("brogan")).isTrue()
    }

    @Test
    fun `Bounty marker - a hero with it spends 1 additional Life counter when defeated (FAQ - and returns it)`() {
        val d = pos {
            clearMinions()
            hero("bain", Team.RED, Hex(1, -2)) { played("bain-gold") }
            hero("arien", Team.BLUE, Hex(2, -2)) { marker(BainHero.BOUNTY, from = "bain") }
            lifeCounters(Team.BLUE, 5)
            phase = PositionPhase.Resolving(next = "bain")
        }
        d.action("bain", ActionType.PRIMARY)
        d.defend(null)
        assertThat(d.at("arien")).isNull()
        assertThat(d.s.state.lifeCounters[Team.BLUE]).isEqualTo(3) // lowest Tier 1, +1
        assertThat(d.hasBounty("arien")).isFalse()
    }

    @Test
    fun `Bounty marker - FAQ - not returned when Bain is defeated`() {
        val d = pos {
            clearMinions()
            hero("arien", Team.BLUE, Hex(2, -2)) {
                played("arien-red-1")
                marker(BainHero.BOUNTY, from = "bain")
            }
            hero("bain", Team.RED, Hex(1, -2))
            phase = PositionPhase.Resolving(next = "arien")
        }
        d.action("arien", ActionType.PRIMARY)
        d.defend(null)
        assertThat(d.at("bain")).isNull()
        assertThat(d.hasBounty("arien")).isTrue()
    }

    @Test
    fun `Bounty marker - FAQ - returned at the end of the round`() {
        val d = pos {
            clearMinions()
            hero("bain", Team.RED, Hex(1, -2))
            hero("arien", Team.BLUE, Hex(2, -2)) { marker(BainHero.BOUNTY, from = "bain") }
            minion(Team.RED, MinionType.MELEE, Hex(0, -6))
            minion(Team.BLUE, MinionType.MELEE, Hex(1, -6))
            phase = PositionPhase.EndOfRound
        }
        d.expect(DecisionKind.PLAY_CARD) // round 2 begins
        assertThat(d.hasBounty("arien")).isFalse()
    }

    @Test
    fun `Dead or Alive - immunity - a hero immune to your attack actions cannot be given the marker`() {
        val d = pos {
            clearMinions()
            hero("bain", Team.RED, Hex(1, -2)) { played("bain-gold") }
            hero("brogan", Team.BLUE, Hex(2, -2)) { hand("brogan-red-1") } // Defense 7
            hero("arien", Team.BLUE, Hex(3, -4)) // Expert Duelist: immune to attacks of every enemy but Wasp
            hero("wasp", Team.RED, Hex(-5, -4))
            effect(
                "arien",
                "arien-blue-2a",
                dev.quinton.goa.core.game.engine.Timing.THIS_TURN,
                dev.quinton.goa.core.game.engine.ActionKind.DEFENSE,
                data = mapOf("attacker" to "wasp"),
            )
            phase = PositionPhase.Resolving(next = "bain")
        }
        d.action("bain", ActionType.PRIMARY)
        d.defend("brogan-red-1")
        assertThat(d.units()).containsExactly("brogan")
    }

    @Test
    fun `Another One - its End of turn repeat is cancelled when Bain is defeated first`() {
        val d = pos {
            clearMinions()
            hero("bain", Team.RED, Hex(1, -2)) { played("bain-blue-3b") } // Initiative 10
            hero("arien", Team.BLUE, Hex(2, -2)) { played("arien-red-1") } // Initiative 8
            phase = PositionPhase.Resolving(next = "bain")
        }
        d.action("bain", ActionType.PRIMARY) // no hero in radius with a discard: nothing to retrieve
        d.action("arien", ActionType.PRIMARY)
        d.defend(null)
        assertThat(d.d.kind).isEqualTo(DecisionKind.PLAY_CARD) // no "repeat?" at the end of the turn
    }

    // ---- Side Quest (Handicap) ------------------------------------------------------------------

    @Test
    fun `Side Quest - if adjacent to an enemy unit, give a hero in radius (a friend too, never you) the marker`() {
        val d = pos {
            clearMinions()
            hero("bain", Team.RED, Hex(1, -2)) {
                handicap = true
                played("bain-handicap")
            }
            hero("wasp", Team.RED, Hex(3, -4))
            hero("arien", Team.BLUE, Hex(-1, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(2, -2)) // the adjacent enemy unit
            phase = PositionPhase.Resolving(next = "bain")
        }
        d.action("bain", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_UNIT, "bain")
        assertThat(d.units()).containsExactly("wasp", "arien")
        assertThat(d.d.options.none { it is Choice.Decline }).isTrue()
        d.unit("wasp")
        assertThat(d.hasBounty("wasp")).isTrue()
    }

    @Test
    fun `Side Quest - not adjacent to an enemy unit, nothing happens`() {
        val d = pos {
            clearMinions()
            hero("bain", Team.RED, Hex(1, -2)) {
                handicap = true
                played("bain-handicap")
            }
            hero("arien", Team.BLUE, Hex(-1, -2))
            phase = PositionPhase.Resolving(next = "bain")
        }
        d.action("bain", ActionType.PRIMARY)
        assertThat(d.d.kind).isEqualTo(DecisionKind.PLAY_CARD)
        assertThat(d.hasBounty("arien")).isFalse()
    }

    // ---- Get over here! (silver) ----------------------------------------------------------------

    private fun pull() = pos {
        clearMinions()
        hero("bain", Team.RED, Hex(1, -2)) { played("bain-silver") } // Range 4
        hero("arien", Team.BLUE, Hex(7, -2))
        minion(Team.BLUE, MinionType.MELEE, Hex(4, -2)) // straight line, nothing between
        minion(Team.BLUE, MinionType.MELEE, Hex(1, -5)) // straight line, terrain (1,-3) between
        minion(Team.BLUE, MinionType.MELEE, Hex(-1, -2)) // straight line, a unit between
        minion(Team.RED, MinionType.MELEE, Hex(0, -2))
        minion(Team.BLUE, MinionType.MELEE, Hex(3, -3)) // not in a straight line
        token("token_rock", Hex(1, 0)) // a token, straight line, nothing between
        phase = PositionPhase.Resolving(next = "bain")
    }

    @Test
    fun `Get over here - a unit or token in range, in a straight line, no obstacles between`() {
        val d = pull()
        d.action("bain", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_UNIT, "bain")
        val at = d.units().map { d.at(it) }.toSet()
        // The adjacent friendly minion at (0,-2) is in a straight line with nothing between, too.
        assertThat(at).containsExactly(Hex(4, -2), Hex(1, 0), Hex(0, -2))
    }

    @Test
    fun `Get over here - Move the target towards you in a straight line, until you are adjacent`() {
        val d = pull()
        d.action("bain", ActionType.PRIMARY)
        d.unit(d.minionAt(Hex(4, -2))!!.id)
        assertThat(d.minionAt(Hex(2, -2))).isNotNull()
        val e = pull()
        e.action("bain", ActionType.PRIMARY)
        e.token(e.s.state.pieces.single().id)
        assertThat(e.s.state.pieces.single().at).isEqualTo(Hex(1, -1))
    }

    // ---- Light Crossbow / Heavy Crossbow / Arbalest (red) ---------------------------------------

    @Test
    fun `Light Crossbow - Target a unit in range and in a straight line with no units or terrain between`() {
        val d = pos {
            clearMinions()
            hero("bain", Team.RED, Hex(1, -2)) { played("bain-red-1") } // Range 2
            hero("arien", Team.BLUE, Hex(7, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(3, -2)) // clear line
            minion(Team.BLUE, MinionType.MELEE, Hex(1, -4)) // terrain (1,-3) between
            minion(Team.BLUE, MinionType.MELEE, Hex(-1, -2)) // only a token between: fine
            token("token_rock", Hex(0, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(1, 0)) // a unit between
            minion(Team.RED, MinionType.MELEE, Hex(1, -1))
            minion(Team.BLUE, MinionType.MELEE, Hex(3, -3)) // not in a straight line
            phase = PositionPhase.Resolving(next = "bain")
        }
        d.action("bain", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_UNIT, "bain")
        assertThat(d.units().map { d.at(it) }.toSet()).containsExactly(Hex(3, -2), Hex(-1, -2))
    }

    @Test
    fun `Heavy Crossbow and Arbalest - Range 3 and 4`() {
        for ((card, h) in listOf("bain-red-2a" to Hex(4, -2), "bain-red-3a" to Hex(-3, -2))) {
            val d = pos {
                clearMinions()
                hero("bain", Team.RED, Hex(1, -2)) { played(card) }
                hero("arien", Team.BLUE, Hex(7, -2))
                minion(Team.BLUE, MinionType.MELEE, h)
                minion(Team.BLUE, MinionType.MELEE, Hex(1, -6))
                minion(Team.RED, MinionType.MELEE, Hex(0, -6))
                phase = PositionPhase.Resolving(next = "bain")
            }
            d.action("bain", ActionType.PRIMARY)
            assertThat(d.minionAt(h)).isNull()
        }
    }

    // ---- Close Call / Narrow Escape / Perfect Getaway (green) -----------------------------------

    private fun escape(card: String, bountyOn: String?, ultimate: Boolean = false) = pos {
        clearMinions()
        hero("arien", Team.BLUE, Hex(2, -2)) { played("arien-red-1") } // Attack 6
        hero("bain", Team.RED, Hex(1, -2)) {
            hand(card, "bain-gold")
            if (ultimate) ultimate()
            if (bountyOn == "bain") marker(BainHero.BOUNTY, from = "bain")
        }
        hero("brogan", Team.BLUE, Hex(7, -2)) { if (bountyOn == "brogan") marker(BainHero.BOUNTY, from = "bain") }
        phase = PositionPhase.Resolving(next = "arien")
    }.also { d ->
        d.action("arien", ActionType.PRIMARY)
        d.defend(card)
    }

    @Test
    fun `Close Call - a Bounty marker in play - block, and that hero gives the marker to you`() {
        val d = escape("bain-green-1", bountyOn = "brogan")
        assertThat(d.texts()).contains("Bain defends with Close Call: Blocked.")
        assertThat(d.at("bain")).isEqualTo(Hex(1, -2))
        assertThat(d.hasBounty("bain")).isTrue()
        assertThat(d.hasBounty("brogan")).isFalse()
    }

    @Test
    fun `Close Call - no Bounty marker in play - the Block fails`() {
        val d = escape("bain-green-1", bountyOn = null)
        assertThat(d.at("bain")).isNull()
    }

    @Test
    fun `Close Call - the marker's effect is applied to you`() {
        val d = pos {
            clearMinions()
            hero("arien", Team.BLUE, Hex(2, -2)) { played("arien-red-1") }
            hero("bain", Team.RED, Hex(1, -2)) { marker(BainHero.BOUNTY, from = "bain") }
            lifeCounters(Team.RED, 5)
            phase = PositionPhase.Resolving(next = "arien")
        }
        d.action("arien", ActionType.PRIMARY)
        d.defend(null)
        assertThat(d.s.state.lifeCounters[Team.RED]).isEqualTo(3)
    }

    @Test
    fun `Narrow Escape - block the attack and retrieve the marker`() {
        val d = escape("bain-green-2a", bountyOn = "brogan")
        assertThat(d.at("bain")).isEqualTo(Hex(1, -2))
        assertThat(d.s.state.heroes.none { h -> h.markers.any { it.kind == bounty } }).isTrue()
    }

    @Test
    fun `Perfect Getaway - block the attack, the marker stays`() {
        val d = escape("bain-green-3a", bountyOn = "brogan")
        assertThat(d.at("bain")).isEqualTo(Hex(1, -2))
        assertThat(d.hasBounty("brogan")).isTrue()
        val e = escape("bain-green-3a", bountyOn = null)
        assertThat(e.at("bain")).isNull()
    }

    @Test
    fun `Close Call, Narrow Escape, Perfect Getaway - designer - a hero in play is not Bain - his own marker does not block`() {
        // Designer, BGG 3676852: "Narrow Escape cannot be used by Bain to block an attack if Bain himself has the
        // marker. Otherwise the card would say '...If you, or a hero in play...'." (p.10)
        for (card in listOf("bain-green-1", "bain-green-2a", "bain-green-3a")) {
            val d = escape(card, bountyOn = "bain")
            assertThat(d.at("bain")).isNull()
            assertThat(d.texts().any { it.startsWith("Bain defends with") && it.contains("The Block fails") }).isTrue()
        }
    }

    // ---- A Game of Chance / Dead Man's Hand / We're Not Done Yet! (blue) ------------------------

    private fun chance(card: String, faceDownRed: Boolean = false) = pos {
        clearMinions()
        hero("bain", Team.RED, Hex(1, -2)) { played(card) }
        hero("arien", Team.BLUE, Hex(2, -2)) {
            hand("arien-gold", "arien-blue-1") // silver, red and green discarded (faceup)
            if (faceDownRed) resolved(1, "arien-red-1", faceDown = true)
        }
        phase = PositionPhase.Resolving(next = "bain")
    }.also { d ->
        d.action("bain", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_CARD, "arien") // the enemy hero chooses one of those cards
        d.card("arien-blue-1")
        d.expect(DecisionKind.CHOOSE_OPTION, "bain")
    }

    @Test
    fun `A Game of Chance - guess only colours that could be in that hand, a correct guess discards it`() {
        val d = chance("bain-blue-1")
        assertThat(d.labels()).containsExactly("Gold", "Blue")
        d.answer(d.d.options.first { it is Choice.Option && it.label == "Blue" })
        assertThat(d.texts()).contains("Bain guesses blue: Arien reveals Aspiring Duelist.")
        assertThat(d.zoneOf("arien", "arien-blue-1")).isEqualTo(Zone.DISCARD)
        assertThat(d.hero("bain").coins).isEqualTo(0)
    }

    @Test
    fun `A Game of Chance - a wrong guess gains 1 coin`() {
        val d = chance("bain-blue-1")
        d.answer(d.d.options.first { it is Choice.Option && it.label == "Gold" })
        assertThat(d.zoneOf("arien", "arien-blue-1")).isEqualTo(Zone.HAND)
        assertThat(d.hero("bain").coins).isEqualTo(1)
    }

    @Test
    fun `A Game of Chance - a facedown card hides its colour, so it may be guessed`() {
        val d = chance("bain-blue-1", faceDownRed = true)
        assertThat(d.labels()).containsExactly("Gold", "Red", "Blue")
    }

    @Test
    fun `A Game of Chance - an enemy hero with fewer than two cards in hand is not a target`() {
        val d = pos {
            clearMinions()
            hero("bain", Team.RED, Hex(1, -2)) { played("bain-blue-1") }
            hero("arien", Team.BLUE, Hex(2, -2)) { hand("arien-gold") }
            phase = PositionPhase.Resolving(next = "bain")
        }
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
    }

    @Test
    fun `Dead Man's Hand - a wrong guess gains 2 coins`() {
        val d = chance("bain-blue-2a")
        d.answer(d.d.options.first { it is Choice.Option && it.label == "Gold" })
        assertThat(d.hero("bain").coins).isEqualTo(2)
    }

    @Test
    fun `We're Not Done Yet - a wrong guess - may repeat once or gain 2 coins (the repeat gains them)`() {
        val d = chance("bain-blue-3a")
        d.answer(d.d.options.first { it is Choice.Option && it.label == "Gold" })
        d.expect(DecisionKind.CHOOSE_OPTION, "bain")
        assertThat(d.labels()).containsExactly("Repeat once", "Gain 2 coins")
        d.option(0)
        d.expect(DecisionKind.CHOOSE_CARD, "arien")
        d.card("arien-gold")
        d.answer(d.d.options.first { it is Choice.Option && it.label == "Blue" })
        assertThat(d.hero("bain").coins).isEqualTo(2) // wrong again: no further repeat
        val e = chance("bain-blue-3a")
        e.answer(e.d.options.first { it is Choice.Option && it.label == "Gold" })
        e.option(1)
        assertThat(e.hero("bain").coins).isEqualTo(2)
    }

    // ---- Hand Crossbow / Hunter-Seeker (red alternates) -----------------------------------------

    private fun hunter(card: String, bounty: Boolean = true) = pos {
        clearMinions()
        hero("bain", Team.RED, Hex(1, -2)) { played(card) } // Range 3
        hero("arien", Team.BLUE, Hex(4, -2)) {
            hand("arien-blue-1")
            if (bounty) marker(BainHero.BOUNTY, from = "bain")
        }
        minion(Team.BLUE, MinionType.MELEE, Hex(1, -1)) // adjacent
        minion(Team.BLUE, MinionType.MELEE, Hex(1, -6))
        minion(Team.RED, MinionType.MELEE, Hex(0, -6))
        phase = PositionPhase.Resolving(next = "bain")
    }

    @Test
    fun `Hand Crossbow - Choose one - a hero in range with a Bounty marker, or a unit adjacent to you`() {
        val d = hunter("bain-red-2b")
        d.action("bain", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_OPTION, "bain")
        assertThat(d.labels()).hasSize(2)
        d.option(0)
        d.expect(DecisionKind.DEFEND, "arien")
        val e = hunter("bain-red-2b")
        e.action("bain", ActionType.PRIMARY)
        e.option(1)
        assertThat(e.minionAt(Hex(1, -1))).isNull()
    }

    @Test
    fun `Hand Crossbow - no Bounty marker in play, only the adjacent bullet (no question)`() {
        val d = hunter("bain-red-2b", bounty = false)
        d.action("bain", ActionType.PRIMARY)
        assertThat(d.minionAt(Hex(1, -1))).isNull()
    }

    @Test
    fun `Hunter-Seeker - Choose one, or both, on different targets, in either order`() {
        val d = hunter("bain-red-3b")
        d.action("bain", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_OPTION, "bain")
        assertThat(d.labels()).hasSize(4)
        d.option(3) // both: the adjacent unit first, then the hero with the Bounty marker
        assertThat(d.minionAt(Hex(1, -1))).isNull()
        d.expect(DecisionKind.DEFEND, "arien")
    }

    @Test
    fun `Hunter-Seeker - both bullets on the same unit only - no both`() {
        val d = pos {
            clearMinions()
            hero("bain", Team.RED, Hex(1, -2)) { played("bain-red-3b") }
            hero("arien", Team.BLUE, Hex(2, -2)) {
                hand("arien-blue-1")
                marker(BainHero.BOUNTY, from = "bain")
            }
            phase = PositionPhase.Resolving(next = "bain")
        }
        d.action("bain", ActionType.PRIMARY)
        assertThat(d.labels()).hasSize(2)
    }

    // ---- Vantage Point / High Ground (green alternates) -----------------------------------------

    private fun vantage(card: String, bounty: Boolean, onBain: Boolean = false) = pos {
        clearMinions()
        hero("bain", Team.RED, Hex(1, -2)) {
            played(card) // Movement 2, boxed in
            if (onBain) marker(BainHero.BOUNTY, from = "bain")
        }
        hero("arien", Team.BLUE, Hex(7, -2)) { if (bounty) marker(BainHero.BOUNTY, from = "bain") }
        for (h in listOf(Hex(2, -2), Hex(2, -3), Hex(0, -2), Hex(0, -1), Hex(1, -1))) {
            minion(Team.RED, MinionType.MELEE, h)
        }
        phase = PositionPhase.Resolving(next = "bain")
    }.also { it.action("bain", ActionType.PRIMARY) }

    @Test
    fun `Vantage Point - Ignore obstacles, +1 movement if a hero in play has a Bounty marker`() {
        val d = vantage("bain-green-2b", bounty = false)
        val far = d.spaces().maxOf { HexGrid.distance(Hex(1, -2), it) }
        assertThat(far).isEqualTo(2)
        assertThat(d.spaces()).doesNotContain(Hex(2, -2)) // never ends on an obstacle
        val e = vantage("bain-green-2b", bounty = true)
        assertThat(e.spaces().maxOf { HexGrid.distance(Hex(1, -2), it) }).isEqualTo(3)
    }

    @Test
    fun `Vantage Point - Bain holding the marker himself is not a hero in play with it (as Close Call)`() {
        val d = vantage("bain-green-2b", bounty = false, onBain = true)
        assertThat(d.spaces().maxOf { HexGrid.distance(Hex(1, -2), it) }).isEqualTo(2)
    }

    @Test
    fun `High Ground - +2 movement`() {
        val d = vantage("bain-green-3b", bounty = true)
        assertThat(d.spaces().maxOf { HexGrid.distance(Hex(1, -2), it) }).isEqualTo(4)
    }

    // ---- Drinking Buddies / Another One! (blue alternates) --------------------------------------

    private fun buddies(card: String) = pos {
        clearMinions()
        hero("bain", Team.RED, Hex(1, -2)) {
            played(card)
            discard("bain-red-1")
        }
        hero("wasp", Team.RED, Hex(2, -2)) { hand("wasp-gold") }
        hero("arien", Team.BLUE, Hex(7, -2))
        phase = PositionPhase.Resolving(next = "bain")
    }

    @Test
    fun `Drinking Buddies - have a hero in radius retrieve, if they do, you may also retrieve`() {
        val d = buddies("bain-blue-2b")
        d.action("bain", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_UNIT, "bain")
        assertThat(d.d.options.any { it is Choice.Decline }).isTrue()
        d.unit("wasp")
        d.expect(DecisionKind.CHOOSE_CARD, "wasp")
        assertThat(d.d.options.none { it is Choice.Decline }).isTrue() // once picked, that hero retrieves
        d.card("wasp-red-1")
        d.expect(DecisionKind.CHOOSE_CARD, "bain")
        d.card("bain-red-1")
        assertThat(d.zoneOf("wasp", "wasp-red-1")).isEqualTo(Zone.HAND)
        assertThat(d.zoneOf("bain", "bain-red-1")).isEqualTo(Zone.HAND)
    }

    @Test
    fun `Drinking Buddies - you may decline`() {
        val d = buddies("bain-blue-2b")
        d.action("bain", ActionType.PRIMARY)
        d.decline()
        assertThat(d.d.kind).isEqualTo(DecisionKind.PLAY_CARD)
        assertThat(d.zoneOf("bain", "bain-red-1")).isEqualTo(Zone.DISCARD)
    }

    @Test
    fun `Another One - End of turn, may repeat once (and only once)`() {
        val d = buddies("bain-blue-3b")
        d.action("bain", ActionType.PRIMARY)
        d.decline()
        d.expect(DecisionKind.CONFIRM, "bain") // end of turn
        d.yes()
        d.unit("wasp")
        d.card("wasp-red-1")
        d.decline() // Bain does not retrieve
        assertThat(d.zoneOf("wasp", "wasp-red-1")).isEqualTo(Zone.HAND)
        assertThat(d.d.kind).isEqualTo(DecisionKind.PLAY_CARD) // the repeat starts no new End of turn effect
    }

    // ---- A Complicated Profession (Ultimate) ----------------------------------------------------

    @Test
    fun `A Complicated Profession - after you give a hero the Bounty marker, that hero discards a card, if able`() {
        val d = pos {
            clearMinions()
            hero("bain", Team.RED, Hex(1, -2)) {
                played("bain-gold")
                ultimate()
            }
            hero("arien", Team.BLUE, Hex(2, -2)) { hand("arien-blue-1", "arien-gold", "arien-silver") }
            phase = PositionPhase.Resolving(next = "bain")
        }
        d.action("bain", ActionType.PRIMARY)
        d.defend("arien-blue-1")
        d.unit("arien")
        d.expect(DecisionKind.CHOOSE_CARD, "arien")
        d.card("arien-gold")
        assertThat(d.zoneOf("arien", "arien-gold")).isEqualTo(Zone.DISCARD)
    }

    @Test
    fun `A Complicated Profession - not when a hero gives the marker to Bain (Close Call)`() {
        val d = escape("bain-green-1", bountyOn = "brogan", ultimate = true)
        assertThat(d.hasBounty("bain")).isTrue()
        assertThat(d.zoneOf("bain", "bain-gold")).isEqualTo(Zone.HAND)
        assertThat(d.d.kind).isEqualTo(DecisionKind.PLAY_CARD)
    }

    // ---- random games ---------------------------------------------------------------------------

    @Test
    fun `random legal games with Bain seated keep every invariant`() {
        Batch2Games.play("bain", games = 300, seed = 41)
    }
}
