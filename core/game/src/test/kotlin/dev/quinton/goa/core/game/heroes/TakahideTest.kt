package dev.quinton.goa.core.game.heroes

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.game.ANY_HERO_ENGINE
import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Controller
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.GameSetup
import dev.quinton.goa.core.game.SeatSetup
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.game.engine.PieceState
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.game.heroes.gydion.HeroRandomGames
import dev.quinton.goa.core.game.pos
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

/**
 * Takahide the Warlord: every card, clause by clause (text from `catalog/heroes/takahide.json`; the hero
 * has no FAQ entries). Readings in rulings/takahide.md. Quiet strip of Forgotten Island: rows r = -2 and
 * r = -5, q = -1..4; terrain at (1,-3), (2,-4), (5,-2).
 */
class TakahideTest {
    private val t0 = Hex(1, -2)
    private val sting = "takahide-gold-extra-1"
    private val strike = "takahide-gold-extra-2"

    // ---- setup: three golds, a silver Handicap ---------------------------------------------------

    @Test
    fun `Setup - Float like a Butterfly starts in hand, Sting like a Bee and Strike like a Tiger in the deck`() {
        val setup = GameSetup(
            TestData.FOREST,
            listOf(
                SeatSetup("takahide", Team.RED, Controller.Human),
                SeatSetup("arien", Team.BLUE, Controller.Human),
            ),
            tieBreaker = Team.RED,
        )
        val s = ANY_HERO_ENGINE.newGame(setup) as EngineSession
        val h = s.state.hero("takahide")!!
        assertThat(h.hand.map { it.id }).containsExactly(
            "takahide-gold",
            "takahide-silver",
            "takahide-red-1",
            "takahide-green-1",
            "takahide-blue-1",
        )
        assertThat(h.card(sting)!!.zone).isEqualTo(Zone.DECK)
        assertThat(h.card(strike)!!.zone).isEqualTo(Zone.DECK)
        // p.20: the Handicap card replaces the basic card of its colour, the silver Bushido.
        val hs = ANY_HERO_ENGINE.newGame(
            setup.copy(
                seats =
                    listOf(SeatSetup("takahide", Team.RED, Controller.Human, handicap = true)) + setup.seats.drop(1),
            ),
        ) as EngineSession
        val hh = hs.state.hero("takahide")!!
        assertThat(hh.card("takahide-silver")!!.zone).isEqualTo(Zone.BOX)
        assertThat(hh.card("takahide-handicap")!!.zone).isEqualTo(Zone.HAND)
        assertThat(hh.card("takahide-gold")!!.zone).isEqualTo(Zone.HAND)
    }

    // ---- Float like a Butterfly ----------------------------------------------------------------------

    @Test
    fun `Float like a Butterfly - Swap this card with a different gold card in your deck, before moving up to 5`() {
        val d = pos {
            clearMinions()
            hero("takahide", Team.RED, t0) { played("takahide-gold") }
            hero("arien", Team.BLUE, Hex(4, -9))
            phase = PositionPhase.Resolving(next = "takahide")
        }
        d.action("takahide", ActionType.PRIMARY)
        // Takahide chooses which of the two deck golds comes in.
        d.expect(DecisionKind.CHOOSE_CARD, "takahide")
        assertThat(d.cards()).containsExactly(sting, strike)
        d.card(strike)
        // Then the Movement (5) of this card's primary action.
        d.expect(DecisionKind.CHOOSE_SPACE, "takahide")
        assertThat(d.spaces().maxOf { HexGrid.distance(t0, it) }).isEqualTo(5)
        d.space(-1, -2)
        assertThat(d.at("takahide")).isEqualTo(Hex(-1, -2))
        // The new gold took the played card's place and is resolved on the Turn slot; Float is in the deck.
        assertThat(d.zoneOf("takahide", "takahide-gold")).isEqualTo(Zone.DECK)
        assertThat(d.zoneOf("takahide", strike)).isEqualTo(Zone.RESOLVED)
        assertThat(d.hero("takahide").card(strike)!!.slot).isEqualTo(1)
    }

    @Test
    fun `Float like a Butterfly - with no gold left in the deck (Ready for War) the primary action cannot start`() {
        val d = pos {
            clearMinions()
            hero("takahide", Team.RED, t0) {
                ultimate()
                played("takahide-gold")
                hand(sting, strike)
                deck("takahide-silver")
            }
            hero("arien", Team.BLUE, Hex(4, -9))
            phase = PositionPhase.Resolving(next = "takahide")
        }
        // p.10: the first (mandatory) instruction cannot be done; Fast Travel replaces the action instead.
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
        assertThat(d.actions()).contains(ActionType.HOLD)
    }

    // ---- Sting like a Bee / Strike like a Tiger ----------------------------------------------------------

    @Test
    fun `Sting like a Bee - Target a unit at maximum range, After the attack swap this card with a deck gold`() {
        val d = pos {
            clearMinions()
            hero("takahide", Team.RED, t0) {
                played(sting)
                deck("takahide-gold")
            }
            hero("arien", Team.BLUE, Hex(4, -2)) { hand("arien-blue-1") } // 3 away
            hero("brogan", Team.BLUE, Hex(3, -2)) { hand("brogan-blue-1") } // 2 away: not at maximum range
            minion(Team.BLUE, MinionType.MELEE, Hex(1, -5)) // 3 away
            phase = PositionPhase.Resolving(next = "takahide")
        }
        d.action("takahide", ActionType.PRIMARY)
        assertThat(d.units()).containsExactly("arien", d.s.state.minions.single { it.at == Hex(1, -5) }.id)
        d.unit("arien")
        val p = d.expect(DecisionKind.DEFEND, "arien")
        assertThat(p.context.attackTotal).isEqualTo(5)
        assertThat(p.context.rangedAttack).isTrue()
        d.defend("arien-blue-1")
        d.expect(DecisionKind.CHOOSE_CARD, "takahide")
        assertThat(d.cards()).containsExactly("takahide-gold", strike)
        d.card("takahide-gold")
        assertThat(d.zoneOf("takahide", sting)).isEqualTo(Zone.DECK)
        assertThat(d.zoneOf("takahide", "takahide-gold")).isEqualTo(Zone.RESOLVED)
    }

    @Test
    fun `Strike like a Tiger - Target a unit adjacent to you, Attack 7, After the attack swap with a deck gold`() {
        val d = pos {
            clearMinions()
            hero("takahide", Team.RED, t0) {
                played(strike)
                hand("takahide-gold")
            }
            hero("arien", Team.BLUE, Hex(2, -2)) { hand("arien-blue-1") } // Defense 5 < 7
            hero("brogan", Team.BLUE, Hex(3, -2)) // not adjacent
            phase = PositionPhase.Resolving(next = "takahide")
        }
        d.action("takahide", ActionType.PRIMARY)
        assertThat(d.expect(DecisionKind.DEFEND, "arien").context.attackTotal).isEqualTo(7)
        d.defend("arien-blue-1")
        assertThat(d.at("arien")).isNull()
        // "After the attack" also follows a defeat; the only other gold in the deck is Sting like a Bee.
        assertThat(d.zoneOf("takahide", strike)).isEqualTo(Zone.DECK)
        assertThat(d.zoneOf("takahide", sting)).isEqualTo(Zone.RESOLVED)
        assertThat(d.zoneOf("takahide", "takahide-gold")).isEqualTo(Zone.HAND)
    }

    // ---- Bushido (silver) ------------------------------------------------------------------------------

    @Test
    fun `Bushido - Swap your gold card in hand with a different gold card in your deck (hidden swap)`() {
        val d = pos {
            clearMinions()
            hero("takahide", Team.RED, t0) { played("takahide-silver") } // Float is in hand
            hero("arien", Team.BLUE, Hex(4, -9))
            phase = PositionPhase.Resolving(next = "takahide")
        }
        d.action("takahide", ActionType.PRIMARY)
        assertThat(d.cards()).containsExactly(sting, strike)
        d.card(sting)
        assertThat(d.zoneOf("takahide", sting)).isEqualTo(Zone.HAND)
        assertThat(d.zoneOf("takahide", "takahide-gold")).isEqualTo(Zone.DECK)
        // Hand and deck are hidden: the log names neither card.
        assertThat(d.texts()).contains("Takahide swaps the gold card in hand with a gold card from the deck.")
    }

    @Test
    fun `Bushido - if you swap a resolved or discarded card, place the new card facedown`() {
        val d = pos {
            clearMinions()
            turn = 2
            hero("takahide", Team.RED, t0) {
                resolved(1, "takahide-gold")
                played("takahide-silver")
            }
            hero("arien", Team.BLUE, Hex(4, -9))
            phase = PositionPhase.Resolving(next = "takahide")
        }
        d.action("takahide", ActionType.PRIMARY)
        d.card(strike)
        val c = d.hero("takahide").card(strike)!!
        assertThat(c.zone).isEqualTo(Zone.RESOLVED)
        assertThat(c.slot).isEqualTo(1)
        assertThat(c.faceDown).isTrue()
        assertThat(d.zoneOf("takahide", "takahide-gold")).isEqualTo(Zone.DECK)
        // Facedown cards are hidden: Arien sees a facedown card in slot 1, and the log does not name it.
        val seen = d.s.view("arien").heroes.single { it.heroId == "takahide" }.resolved.single { it.slot == 1 }
        assertThat(seen.cardId).isNull()
        assertThat(d.texts().none { it.contains("Strike like a Tiger") }).isTrue()
    }

    @Test
    fun `Bushido - a gold card placed facedown has no colour, so it is not your gold card (no primary)`() {
        val d = pos {
            clearMinions()
            turn = 2
            hero("takahide", Team.RED, t0) {
                resolved(1, sting, faceDown = true)
                deck("takahide-gold")
                played("takahide-silver")
            }
            hero("arien", Team.BLUE, Hex(4, -9))
            phase = PositionPhase.Resolving(next = "takahide")
        }
        assertThat(d.actions()).containsExactly(ActionType.HOLD)
    }

    // ---- Shameful Display (Handicap) ---------------------------------------------------------------------

    @Test
    fun `Shameful Display - a Block with no condition, You are defeated`() {
        val d = pos {
            clearMinions()
            hero("arien", Team.BLUE, Hex(2, -2)) { played("arien-gold") }
            hero("takahide", Team.RED, t0) {
                handicap = true
                hand("takahide-handicap")
            }
            phase = PositionPhase.Resolving(next = "arien")
        }
        assertThat(d.hero("takahide").card("takahide-silver")!!.zone).isEqualTo(Zone.BOX)
        d.action("arien", ActionType.PRIMARY)
        d.defend("takahide-handicap")
        assertThat(d.at("takahide")).isNull()
        assertThat(d.texts()).contains("Takahide defends with Shameful Display: The Block fails.")
    }

    // ---- Set an Example / Lead from the Front / Hold my Saké ----------------------------------------------

    private fun example(card: String, waspAt: Hex) = pos {
        clearMinions()
        hero("takahide", Team.RED, t0) { played(card) }
        hero("wasp", Team.RED, waspAt) {
            played("wasp-red-1") // initiative 8
            hand("wasp-blue-1") // initiative 10
            discard("wasp-green-1")
        }
        hero("arien", Team.BLUE, Hex(2, -2)) { hand("arien-blue-1") } // the target, Defense 5
        phase = PositionPhase.Resolving(next = "takahide")
    }

    @Test
    fun `Set an Example - After the attack a friendly hero in radius may swap their unresolved card`() {
        val d = example("takahide-red-1", Hex(3, -5)) // 3 away
        d.action("takahide", ActionType.PRIMARY)
        d.defend("arien-blue-1")
        // Takahide chooses the friendly hero (or nobody); that hero chooses the card (p.10).
        d.expect(DecisionKind.CHOOSE_UNIT, "takahide")
        assertThat(d.units()).containsExactly("wasp")
        d.unit("wasp")
        d.expect(DecisionKind.CHOOSE_CARD, "wasp")
        assertThat(d.cards()).containsExactly("wasp-blue-1") // the hand only
        assertThat(d.d.options.any { it is Choice.Decline }).isTrue()
        d.card("wasp-blue-1")
        assertThat(d.zoneOf("wasp", "wasp-blue-1")).isEqualTo(Zone.PLAYED)
        assertThat(d.zoneOf("wasp", "wasp-red-1")).isEqualTo(Zone.HAND)
        // Wasp now acts with Lift Up.
        d.expect(DecisionKind.CHOOSE_ACTION, "wasp")
        assertThat(d.d.sourceCardId).isEqualTo("wasp-blue-1")
    }

    @Test
    fun `Set an Example - radius 3, Lead from the Front - radius 4`() {
        assertThat(HexGrid.distance(t0, Hex(5, -5))).isEqualTo(4)
        val d = example("takahide-red-1", Hex(5, -5))
        d.action("takahide", ActionType.PRIMARY)
        d.defend("arien-blue-1")
        // Wasp is 4 away: nobody to offer, Wasp acts with her card.
        d.expect(DecisionKind.CHOOSE_ACTION, "wasp")
        val e = example("takahide-red-2a", Hex(5, -5))
        e.action("takahide", ActionType.PRIMARY)
        e.defend("arien-blue-1")
        e.expect(DecisionKind.CHOOSE_UNIT, "takahide")
    }

    @Test
    fun `Hold my Sake - the friendly hero may swap with a card in their hand, or in their discard`() {
        val d = example("takahide-red-3a", Hex(3, -5))
        d.action("takahide", ActionType.PRIMARY)
        d.defend("arien-blue-1")
        d.unit("wasp")
        // Her hand, and her discard (the cards of the round not placed elsewhere are discarded).
        assertThat(d.cards()).containsExactly("wasp-blue-1", "wasp-gold", "wasp-silver", "wasp-green-1")
        d.card("wasp-green-1")
        assertThat(d.zoneOf("wasp", "wasp-green-1")).isEqualTo(Zone.PLAYED)
        val old = d.hero("wasp").card("wasp-red-1")!!
        assertThat(old.zone).isEqualTo(Zone.DISCARD)
        assertThat(old.faceDown).isFalse()
    }

    // ---- Spinning Blade / Blade Helix ---------------------------------------------------------------------

    @Test
    fun `Spinning Blade - This turn, empty spaces adjacent to you count as obstacles for enemy units`() {
        val d = pos {
            clearMinions()
            hero("takahide", Team.RED, t0) { played("takahide-red-2b") }
            hero("arien", Team.BLUE, Hex(2, -2)) { hand("arien-blue-1") }
            hero("brogan", Team.BLUE, Hex(-1, -2)) { played("brogan-blue-1") } // Movement 2
            phase = PositionPhase.Resolving(next = "takahide")
        }
        d.action("takahide", ActionType.PRIMARY)
        d.defend("arien-blue-1")
        d.action("brogan", ActionType.MOVEMENT)
        val adjacent = HexGrid.neighbors(t0).toSet()
        assertThat(d.spaces()).isNotEmpty()
        assertThat(d.spaces().intersect(adjacent)).isEmpty()
        assertThat(d.spaces()).doesNotContain(Hex(1, -1)) // only reachable through (0,-1) or (0,-2)
    }

    @Test
    fun `Spinning Blade - friendly units are not affected`() {
        val d = pos {
            clearMinions()
            hero("takahide", Team.RED, t0) { played("takahide-red-2b") }
            hero("arien", Team.BLUE, Hex(2, -2)) { hand("arien-blue-1") }
            hero("wasp", Team.RED, Hex(-1, -2)) { played("wasp-red-1") } // Movement 4
            phase = PositionPhase.Resolving(next = "takahide")
        }
        d.action("takahide", ActionType.PRIMARY)
        d.defend("arien-blue-1")
        d.action("wasp", ActionType.MOVEMENT)
        assertThat(d.spaces()).contains(Hex(0, -2))
    }

    @Test
    fun `Blade Helix - empty spaces in radius count as obstacles for enemy units (items widen the radius)`() {
        val d = pos {
            clearMinions()
            hero("takahide", Team.RED, t0) {
                played("takahide-red-3b") // radius 1
                items("takahide-green-3a") // +1 Radius
            }
            hero("arien", Team.BLUE, Hex(2, -2)) { hand("arien-blue-1") }
            hero("brogan", Team.BLUE, Hex(-2, -2)) { played("brogan-blue-1") } // Movement 2
            phase = PositionPhase.Resolving(next = "takahide")
        }
        d.action("takahide", ActionType.PRIMARY)
        d.defend("arien-blue-1")
        d.action("brogan", ActionType.MOVEMENT)
        assertThat(d.spaces()).isNotEmpty()
        for (h in d.spaces()) assertThat(HexGrid.distance(t0, h)).isGreaterThan(2)
    }

    // ---- Come to Aid family (green) ----------------------------------------------------------------------

    @Test
    fun `Come to Aid - A friendly hero in range may discard a card, then you may move up to 3 spaces`() {
        val d = pos {
            clearMinions()
            hero("takahide", Team.RED, t0) { played("takahide-green-1") }
            hero("wasp", Team.RED, Hex(3, -2)) // the whole round's hand, empty discard
            hero("arien", Team.BLUE, Hex(4, -9))
            phase = PositionPhase.Resolving(next = "takahide")
        }
        d.action("takahide", ActionType.PRIMARY)
        // The only friendly hero in range is chosen; she decides whether to discard.
        d.expect(DecisionKind.CHOOSE_CARD, "wasp")
        d.card("wasp-gold")
        assertThat(d.zoneOf("wasp", "wasp-gold")).isEqualTo(Zone.DISCARD)
        d.expect(DecisionKind.CHOOSE_SPACE, "takahide")
        for (h in d.spaces()) assertThat(HexGrid.distance(t0, h)).isAtMost(3)
        d.space(-1, -2)
        assertThat(d.at("takahide")).isEqualTo(Hex(-1, -2))
    }

    @Test
    fun `Come to Aid - the friend declines with an empty discard, so no move, with an earlier discard you may move`() {
        val d = pos {
            clearMinions()
            hero("takahide", Team.RED, t0) { played("takahide-green-1") }
            hero("wasp", Team.RED, Hex(3, -2))
            hero("arien", Team.BLUE, Hex(4, -9))
            phase = PositionPhase.Resolving(next = "takahide")
        }
        d.action("takahide", ActionType.PRIMARY)
        d.decline()
        assertThat(d.at("takahide")).isEqualTo(t0)
        assertThat(d.d.kind).isNotEqualTo(DecisionKind.CHOOSE_SPACE)
        val e = pos {
            clearMinions()
            hero("takahide", Team.RED, t0) { played("takahide-green-1") }
            hero("wasp", Team.RED, Hex(3, -2)) { discard("wasp-red-1") }
            hero("arien", Team.BLUE, Hex(4, -9))
            phase = PositionPhase.Resolving(next = "takahide")
        }
        e.action("takahide", ActionType.PRIMARY)
        e.decline()
        e.expect(DecisionKind.CHOOSE_SPACE, "takahide")
    }

    @Test
    fun `Come to Aid - no friendly hero in range, no primary action`() {
        val d = pos {
            clearMinions()
            hero("takahide", Team.RED, t0) { played("takahide-green-1") }
            hero("wasp", Team.RED, Hex(-5, -2)) // 6 away
            hero("arien", Team.BLUE, Hex(4, -9))
            phase = PositionPhase.Resolving(next = "takahide")
        }
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
    }

    @Test
    fun `Bring the Relief - range 4, move up to 4, Commit Reserves - ignoring obstacles`() {
        fun boxedIn(card: String) = pos {
            clearMinions()
            hero("takahide", Team.RED, t0) { played(card) }
            hero("wasp", Team.RED, Hex(5, -5)) { discard("wasp-red-1") } // 4 away
            hero("arien", Team.BLUE, Hex(4, -9))
            for (h in listOf(Hex(2, -2), Hex(0, -2), Hex(1, -1), Hex(2, -3), Hex(0, -1))) token("token_rock", h)
            phase = PositionPhase.Resolving(next = "takahide")
        }
        val d = boxedIn("takahide-green-2a")
        d.action("takahide", ActionType.PRIMARY)
        d.decline() // Wasp keeps her cards; she has a card in the discard already
        // Boxed in by rocks: no move.
        assertThat(d.at("takahide")).isEqualTo(t0)
        val e = boxedIn("takahide-green-3a")
        e.action("takahide", ActionType.PRIMARY)
        e.decline()
        e.expect(DecisionKind.CHOOSE_SPACE, "takahide")
        assertThat(e.spaces()).isNotEmpty()
        for (h in e.spaces()) assertThat(HexGrid.distance(t0, h)).isIn(2..4)
    }

    @Test
    fun `Pledge of Allegiance - both gain 1 coin and you may retrieve a discarded card, Loyal Retainer - 2 coins`() {
        for ((card, coins) in listOf("takahide-green-2b" to 1, "takahide-green-3b" to 2)) {
            val d = pos {
                clearMinions()
                hero("takahide", Team.RED, t0) {
                    played(card)
                    hand("takahide-gold") // silver, red and blue in the discard
                }
                hero("wasp", Team.RED, Hex(3, -2))
                hero("arien", Team.BLUE, Hex(4, -9))
                phase = PositionPhase.Resolving(next = "takahide")
            }
            d.action("takahide", ActionType.PRIMARY)
            d.card("wasp-gold")
            assertThat(d.hero("takahide").coins).isEqualTo(coins)
            assertThat(d.hero("wasp").coins).isEqualTo(coins)
            // "you may retrieve a discarded card": your own discard.
            d.expect(DecisionKind.CHOOSE_CARD, "takahide")
            assertThat(d.cards()).containsExactly("takahide-silver", "takahide-red-1", "takahide-blue-1")
            d.card("takahide-silver")
            assertThat(d.zoneOf("takahide", "takahide-silver")).isEqualTo(Zone.HAND)
        }
    }

    // ---- Proven Warrior family (blue) ---------------------------------------------------------------------

    private fun proven(card: String, waspAt: Hex = Hex(3, -2)) = pos {
        clearMinions()
        hero("takahide", Team.RED, t0) {
            played(card)
            hand("takahide-gold") // Takahide's own discard is not offered
        }
        hero("wasp", Team.RED, waspAt) { discard("wasp-red-1", "wasp-blue-1") }
        hero("arien", Team.BLUE, Hex(2, -2)) { hand("arien-red-1", "arien-gold") }
        hero("brogan", Team.BLUE, Hex(-1, -2)) { hand("brogan-red-1", "brogan-blue-1") }
        phase = PositionPhase.Resolving(next = "takahide")
    }

    @Test
    fun `Proven Warrior - a card in a friendly discard, an enemy hero in radius discards one of its color`() {
        val d = proven("takahide-blue-1")
        d.action("takahide", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_CARD, "takahide")
        assertThat(d.cards()).containsExactly("wasp-red-1", "wasp-blue-1")
        d.card("wasp-red-1")
        assertThat(d.units()).containsExactly("arien", "brogan")
        d.unit("arien")
        assertThat(d.zoneOf("arien", "arien-red-1")).isEqualTo(Zone.DISCARD)
        assertThat(d.zoneOf("arien", "arien-gold")).isEqualTo(Zone.HAND)
    }

    @Test
    fun `Proven Warrior - the enemy has no card of the color, so discards nothing (if able)`() {
        val d = proven("takahide-blue-1")
        d.action("takahide", ActionType.PRIMARY)
        d.card("wasp-blue-1")
        d.unit("arien")
        assertThat(d.hero("arien").hand.map { it.id }).containsExactly("arien-red-1", "arien-gold")
    }

    @Test
    fun `Chosen Champion - radius 4 reaches a friend 4 away, Proven Warrior's radius 3 does not`() {
        val d = proven("takahide-blue-1", waspAt = Hex(5, -5))
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
        val e = proven("takahide-blue-2a", waspAt = Hex(5, -5))
        e.action("takahide", ActionType.PRIMARY)
        assertThat(e.cards()).containsExactly("wasp-red-1", "wasp-blue-1")
    }

    @Test
    fun `The Right Hand - up to two enemy heroes in radius discard a card of the same color, if able`() {
        val d = proven("takahide-blue-3a")
        d.action("takahide", ActionType.PRIMARY)
        d.card("wasp-red-1")
        d.expect(DecisionKind.CHOOSE_UNIT, "takahide")
        d.unit("arien")
        d.unit("brogan")
        assertThat(d.zoneOf("arien", "arien-red-1")).isEqualTo(Zone.DISCARD)
        assertThat(d.zoneOf("brogan", "brogan-red-1")).isEqualTo(Zone.DISCARD)
    }

    @Test
    fun `The Right Hand - up to two enemy heroes, never one hero twice (Razzle's figures are one hero)`() {
        val d = pos {
            clearMinions()
            hero("takahide", Team.RED, t0) { played("takahide-blue-3a") }
            hero("wasp", Team.RED, Hex(3, -5)) { discard("wasp-red-1") }
            hero("razzle", Team.BLUE, Hex(3, -2)) { hand("razzle-red-1", "razzle-gold") }
            piece(
                PieceState(
                    "rz2",
                    RazzleHero.DOUBLE,
                    "Razzle",
                    Hex(0, -2),
                    token = false,
                    unit = true,
                    removeAtEndOfRound = false,
                    heroBody = "razzle",
                ),
            )
            phase = PositionPhase.Resolving(next = "takahide")
        }
        d.action("takahide", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_UNIT, "takahide")
        assertThat(d.units()).containsExactly("razzle")
        d.unit("razzle")
        assertThat(d.zoneOf("razzle", "razzle-red-1")).isEqualTo(Zone.DISCARD)
        // No second enemy hero: the text is done (Razzle's other figure is not another hero).
        assertThat(d.s.pending?.kind).isNotEqualTo(DecisionKind.CHOOSE_UNIT)
    }

    // ---- Calculated Risk / Tactical Gambit ----------------------------------------------------------------

    private fun risk(card: String) = pos {
        clearMinions()
        hero("takahide", Team.RED, t0) { played(card) }
        hero("wasp", Team.RED, Hex(3, -2)) { hand("wasp-red-1", "wasp-blue-1") } // the rest is discarded
        hero("arien", Team.BLUE, Hex(4, -9))
        for (h in HexGrid.neighbors(Hex(3, -2))) token("token_rock", h)
        phase = PositionPhase.Resolving(next = "takahide")
    }

    @Test
    fun `Calculated Risk - a friendly hero in radius may discard an attack card, then that hero may move up to 2`() {
        val d = pos {
            clearMinions()
            hero("takahide", Team.RED, t0) { played("takahide-blue-2b") }
            hero("wasp", Team.RED, Hex(3, -2)) { hand("wasp-red-1", "wasp-blue-1") }
            hero("arien", Team.BLUE, Hex(4, -9))
            phase = PositionPhase.Resolving(next = "takahide")
        }
        d.action("takahide", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_CARD, "wasp")
        assertThat(d.cards()).containsExactly("wasp-red-1") // attack cards only
        d.card("wasp-red-1")
        // "that hero may move": Wasp chooses.
        d.expect(DecisionKind.CHOOSE_SPACE, "wasp")
        for (h in d.spaces()) assertThat(HexGrid.distance(Hex(3, -2), h)).isAtMost(2)
        d.space(3, -4)
        assertThat(d.at("wasp")).isEqualTo(Hex(3, -4))
    }

    @Test
    fun `Tactical Gambit - that hero may move up to 2 spaces, ignoring obstacles`() {
        val d = risk("takahide-blue-2b")
        d.action("takahide", ActionType.PRIMARY)
        d.decline()
        assertThat(d.at("wasp")).isEqualTo(Hex(3, -2)) // boxed in
        val e = risk("takahide-blue-3b")
        e.action("takahide", ActionType.PRIMARY)
        e.decline()
        e.expect(DecisionKind.CHOOSE_SPACE, "wasp")
        for (h in e.spaces()) assertThat(HexGrid.distance(Hex(3, -2), h)).isEqualTo(2)
    }

    // ---- Ready for War (Ultimate) ----------------------------------------------------------------------------

    @Test
    fun `Ready for War - on unlocking, return your silver card to the deck and take the two deck golds into hand`() {
        for (handicap in listOf(false, true)) {
            val d = pos {
                clearMinions()
                hero("takahide", Team.RED, t0) {
                    level = 7
                    coins = 7
                    this.handicap = handicap
                }
                hero("arien", Team.BLUE, Hex(4, -9))
                phase = PositionPhase.EndOfRound
            }
            d.expect(DecisionKind.PLAY_CARD, "takahide")
            val h = d.hero("takahide")
            assertThat(h.level).isEqualTo(8)
            assertThat(h.card("takahide-purple-4")!!.zone).isEqualTo(Zone.ULTIMATE)
            val silver = if (handicap) "takahide-handicap" else "takahide-silver"
            assertThat(h.card(silver)!!.zone).isEqualTo(Zone.DECK)
            // "(You now have a total hand size of 6 cards.)"
            assertThat(h.hand).hasSize(6)
            assertThat(
                h.hand.count {
                    TestData.catalog.hero("takahide")!!.allCards.first { c -> c.id == it.id }.color ==
                        CardColor.GOLD
                },
            )
                .isEqualTo(3)
        }
    }

    // ---- random games ----------------------------------------------------------------------------------------

    @Test
    fun `random legal games with Takahide keep every invariant`() {
        HeroRandomGames.run("takahide", games = 150, seed = 82) { st, _ ->
            val t = st.hero("takahide") ?: return@run
            val golds = t.cards.filter { it.id == "takahide-gold" || it.id == sting || it.id == strike }
            val out = golds.count { it.zone != Zone.DECK }
            // Before Ready for War exactly one gold is out of the deck; after it, all three.
            assertThat(out).isEqualTo(if (t.cards.any { it.zone == Zone.ULTIMATE }) 3 else 1)
        }
    }
}
