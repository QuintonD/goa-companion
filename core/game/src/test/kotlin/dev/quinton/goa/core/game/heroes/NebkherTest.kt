package dev.quinton.goa.core.game.heroes

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.Driver
import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.core.game.engine.ActionKind
import dev.quinton.goa.core.game.engine.Timing
import dev.quinton.goa.core.game.engine.Zone
import dev.quinton.goa.core.game.pos
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

/** NebKher the Harbinger: every card, clause by clause (text from `catalog/heroes/nebkher.json`). */
class NebkherTest {
    private val me = Hex(1, -2)

    /** NebKher (Blue) at (1,-2) with [card] played; one far minion per team. */
    private fun neb(
        card: String,
        turn: Int = 1,
        ultimate: Boolean = false,
        handicap: Boolean = false,
        block: PositionBuilder.() -> Unit,
    ): Driver = pos {
        clearMinions()
        this.turn = turn
        hero("nebkher", Team.BLUE, me) {
            this.handicap = handicap
            played(card)
            if (ultimate) ultimate()
        }
        minion(Team.RED, MinionType.MELEE, Hex(-1, -6))
        minion(Team.BLUE, MinionType.MELEE, Hex(0, -7))
        block()
        phase = PositionPhase.Resolving(next = "nebkher")
    }

    private fun PositionBuilder.illusion(at: Hex) = token(NebkherHero.ILLUSION, at, owner = "nebkher", team = Team.BLUE)

    private fun illusions(d: Driver) = d.s.state.pieces.filter {
        it.kind == NebkherHero.ILLUSION
    }.mapNotNull { it.at }.toSet()

    private fun option(d: Driver, label: String) = d.answer(
        d.d.options.first {
            it is Choice.Option &&
                it.label.startsWith(label)
        },
    )

    // ---- Mind Grip (gold) / Mind Probe (Handicap) -----------------------------------------------

    @Test
    fun `Mind Grip - perform an action on the card in the previous turn slot of an enemy hero in range`() {
        val d = neb("nebkher-gold", turn = 2) {
            hero("arien", Team.RED, Hex(4, -2)) { resolved(1, "arien-green-1") } // Liquid Leap, 3 away
        }
        d.action("nebkher", ActionType.PRIMARY)
        // The only bullet possible and the only hero: NebKher chooses an action on Liquid Leap.
        d.expect(DecisionKind.CHOOSE_ACTION, "nebkher")
        assertThat(d.actions()).contains(ActionType.PRIMARY)
        assertThat(d.actions()).doesNotContain(ActionType.HOLD)
        d.action("nebkher", ActionType.PRIMARY)
        // "Place yourself into a space in range …": NebKher is placed (Liquid Leap's Range 2).
        d.expect(DecisionKind.CHOOSE_SPACE, "nebkher")
        assertThat(d.spaces().all { HexGrid.distance(it, me) <= 2 }).isTrue()
        val to = d.spaces().first()
        d.space(to)
        assertThat(d.at("nebkher")).isEqualTo(to)
        assertThat(d.at("arien")).isEqualTo(Hex(4, -2))
    }

    @Test
    fun `Mind Grip - only the previous turn slot, only enemy heroes in range`() {
        val d = neb("nebkher-gold", turn = 3) {
            hero("arien", Team.RED, Hex(4, -2)) { resolved(1, "arien-green-1") } // slot 1, not the previous (2)
            hero("xargatha", Team.RED, Hex(-6, -2)) { resolved(2, "xargatha-green-2b") } // out of range (7)
            hero("brogan", Team.BLUE, Hex(2, -2)) { resolved(2, "brogan-blue-1") } // friendly
        }
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
    }

    @Test
    fun `Mind Grip - if you would place any tokens this way, place Illusion tokens instead`() {
        val d = neb("nebkher-gold", turn = 2) {
            tieBreaker = Team.RED // orange: "Place up to 2 Magma tokens in radius"
            hero("ignatia", Team.RED, Hex(4, -2)) { resolved(1, "ignatia-green-1") }
        }
        d.action("nebkher", ActionType.PRIMARY)
        d.action("nebkher", ActionType.PRIMARY)
        d.space(2, -2)
        d.space(0, -2)
        assertThat(illusions(d)).containsExactly(Hex(2, -2), Hex(0, -2))
        assertThat(d.s.state.pieces.none { it.kind == IgnatiaHero.MAGMA }).isTrue()
    }

    @Test
    fun `Mind Grip - skip giving markers`() {
        val d = neb("nebkher-gold", turn = 2) {
            hero("snorri", Team.RED, Hex(4, -2)) { resolved(1, "snorri-silver") } // Inscribe the Runes
        }
        d.action("nebkher", ActionType.PRIMARY)
        d.action("nebkher", ActionType.PRIMARY)
        repeat(3) { d.answer(d.d.options.first()) } // the rune for slots 1..3
        assertThat(d.s.state.heroes.all { it.markers.isEmpty() }).isTrue()
        assertThat(d.texts().any { it.contains("is not given the rune") }).isTrue()
    }

    @Test
    fun `Mind Grip - FAQ, a copied non-ranged attack is a non-ranged attack`() {
        val d = neb("nebkher-gold", turn = 2) {
            hero("wasp", Team.RED, Hex(2, -2)) {
                resolved(1, "wasp-red-1") // Shock: Target a unit adjacent to you
                hand("wasp-blue-1")
            }
        }
        d.action("nebkher", ActionType.PRIMARY)
        d.action("nebkher", ActionType.PRIMARY)
        d.expect(DecisionKind.DEFEND, "wasp")
        assertThat(d.d.context.rangedAttack).isFalse()
        assertThat(d.d.context.attackTotal).isEqualTo(5) // Shock's Attack value
    }

    @Test
    fun `Mind Grip - FAQ, copied Active effects are NebKher's`() {
        val d = neb("nebkher-gold", turn = 2) {
            hero("brogan", Team.RED, Hex(4, -2)) { resolved(1, "brogan-silver") } // Bulwark
            hero("arien", Team.BLUE, Hex(-6, -2)) { played("arien-gold") } // acts next: the turn goes on
        }
        d.action("nebkher", ActionType.PRIMARY)
        d.action("nebkher", ActionType.PRIMARY)
        val e = d.s.state.effects.single { it.cardId == "brogan-silver" }
        assertThat(e.owner).isEqualTo("nebkher")
    }

    @Test
    fun `Mind Grip - FAQ, NebKher cannot copy the card of an immune hero`() {
        val d = neb("nebkher-gold", turn = 2) {
            hero("snorri", Team.RED, Hex(4, -2)) { resolved(1, "snorri-silver") }
            effect("snorri", "snorri-green-1", Timing.THIS_TURN, ActionKind.DEFENSE, key = SnorriHero.IMMUNE)
        }
        assertThat(d.actions()).doesNotContain(ActionType.PRIMARY)
    }

    @Test
    fun `Mind Grip - defeat a minion adjacent to you (an enemy one)`() {
        val d = neb("nebkher-gold") {
            hero("wasp", Team.RED, Hex(-6, -2))
            minion(Team.RED, MinionType.MELEE, Hex(2, -2))
            minion(Team.BLUE, MinionType.MELEE, Hex(0, -2)) // friendly: never
        }
        d.action("nebkher", ActionType.PRIMARY)
        assertThat(d.s.state.minions.none { it.at == Hex(2, -2) }).isTrue()
        assertThat(d.s.state.minions.any { it.at == Hex(0, -2) }).isTrue()
        assertThat(d.hero("nebkher").coins).isEqualTo(2)
    }

    @Test
    fun `Mind Grip - choose one when both bullets are possible`() {
        val d = neb("nebkher-gold", turn = 2) {
            hero("arien", Team.RED, Hex(4, -2)) { resolved(1, "arien-green-1") }
            minion(Team.RED, MinionType.MELEE, Hex(2, -2))
        }
        d.action("nebkher", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_OPTION, "nebkher")
        assertThat(d.d.options).hasSize(2)
    }

    @Test
    fun `Mind Probe - only the copy, do not use markers`() {
        val d = neb("nebkher-handicap", turn = 2, handicap = true) {
            hero("snorri", Team.RED, Hex(3, -2)) { resolved(1, "snorri-silver") } // range 3
            minion(Team.RED, MinionType.MELEE, Hex(2, -2)) // no "defeat a minion" bullet
        }
        d.action("nebkher", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_ACTION, "nebkher")
        d.action("nebkher", ActionType.PRIMARY)
        repeat(3) { d.answer(d.d.options.first()) }
        assertThat(d.hero("snorri").markers).isEmpty()
        assertThat(d.hero("nebkher").markers).isEmpty()
        assertThat(d.s.state.minions.any { it.at == Hex(2, -2) }).isTrue()
    }

    // ---- Diabolical Laughter (silver) / What the Hell Are You? (Ultimate) ----------------------

    @Test
    fun `Diabolical Laughter - laugh, then up to three times swap with an Illusion in radius or place one adjacent`() {
        val d = neb("nebkher-silver") {
            hero("wasp", Team.RED, Hex(-6, -2))
            illusion(Hex(4, -2)) // 3 away: in radius 4
        }
        d.action("nebkher", ActionType.PRIMARY)
        assertThat(d.texts()).contains("NebKher laughs diabolically.")
        d.expect(DecisionKind.CHOOSE_OPTION, "nebkher")
        option(d, "Swap with an Illusion token")
        assertThat(d.at("nebkher")).isEqualTo(Hex(4, -2))
        assertThat(illusions(d)).containsExactly(me)
        option(d, "Place an Illusion token")
        d.space(3, -2)
        option(d, "Place an Illusion token") // the same bullet again
        d.space(4, -3)
        assertThat(illusions(d)).containsExactly(me, Hex(3, -2), Hex(4, -3))
        // Three choices made: done.
        assertThat(d.pending?.kind).isNotEqualTo(DecisionKind.CHOOSE_OPTION)
    }

    @Test
    fun `Diabolical Laughter - may stop before three`() {
        val d = neb("nebkher-silver") { hero("wasp", Team.RED, Hex(-6, -2)) }
        d.action("nebkher", ActionType.PRIMARY)
        d.decline()
        assertThat(illusions(d)).isEmpty()
    }

    @Test
    fun `Diabolical Laughter - swap two resolved cards of an enemy hero in radius, without canceling active effects`() {
        val d = neb("nebkher-silver", turn = 3) {
            hero("wasp", Team.RED, Hex(3, -2)) {
                resolved(1, "wasp-silver")
                resolved(2, "wasp-red-1")
            }
            effect("wasp", "wasp-silver", Timing.THIS_ROUND, ActionKind.SKILL, madeTurn = 1)
        }
        d.action("nebkher", ActionType.PRIMARY)
        option(d, "Swap two resolved cards")
        d.expect(DecisionKind.CHOOSE_OPTION, "nebkher")
        d.answer(d.d.options.first { (it as Choice.Option).index == 1 }) // slot 1; slot 2 is then the only other
        assertThat(d.hero("wasp").card("wasp-silver")!!.slot).isEqualTo(2)
        assertThat(d.hero("wasp").card("wasp-red-1")!!.slot).isEqualTo(1)
        assertThat(d.s.state.effects.any { it.cardId == "wasp-silver" }).isTrue()
    }

    @Test
    fun `What the Hell Are You - after laughing, all enemy heroes in radius discard a card, or are defeated`() {
        val d = neb("nebkher-silver", ultimate = true) {
            hero("wasp", Team.RED, Hex(4, -6)) { hand() } // 4 away, no card: defeated
            hero("xargatha", Team.RED, Hex(-4, -2)) { hand("xargatha-gold", "xargatha-silver") } // 5 away
            hero("arien", Team.RED, Hex(-6, -2)) { hand("arien-gold") } // 7 away: not in radius 5
        }
        d.action("nebkher", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_CARD, "xargatha")
        d.card("xargatha-gold")
        assertThat(d.at("wasp")).isNull()
        assertThat(d.zoneOf("arien", "arien-gold")).isEqualTo(Zone.HAND)
        assertThat(d.hero("nebkher").coins).isEqualTo(1) // Wasp's level
    }

    // ---- Phantasmal Sentry / Warrior / Champion (red) ------------------------------------------

    @Test
    fun `Phantasmal Sentry - a hero in range adjacent to an Illusion in range, or a unit adjacent`() {
        val d = neb("nebkher-red-1") {
            hero("wasp", Team.RED, Hex(4, -2)) { hand("wasp-blue-1") } // 3 away, next to the Illusion
            hero("xargatha", Team.RED, Hex(-2, -2)) { hand("xargatha-blue-1") } // 3 away, next to nothing
            illusion(Hex(4, -3))
        }
        d.action("nebkher", ActionType.PRIMARY)
        d.expect(DecisionKind.DEFEND, "wasp") // the only target
        val e = neb("nebkher-red-1") {
            hero("wasp", Team.RED, Hex(2, -2)) { hand("wasp-blue-1") }
            hero("xargatha", Team.RED, Hex(-2, -2)) { hand("xargatha-blue-1") }
            illusion(Hex(-2, -3))
        }
        e.action("nebkher", ActionType.PRIMARY)
        e.expect(DecisionKind.CHOOSE_OPTION, "nebkher")
        option(e, "Target a unit adjacent")
        e.expect(DecisionKind.DEFEND, "wasp")
    }

    @Test
    fun `Phantasmal Warrior - move an Illusion up to 1 space next to an enemy hero in range, target that hero`() {
        val d = neb("nebkher-red-2a") {
            hero("wasp", Team.RED, Hex(4, -2)) { hand("wasp-blue-1") }
            illusion(Hex(3, -3)) // 2 from Wasp: one space brings it next to her, at (4,-3) or (3,-2)
        }
        d.action("nebkher", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_SPACE, "nebkher")
        assertThat(d.spaces()).containsExactly(Hex(4, -3), Hex(3, -2))
        d.space(4, -3)
        assertThat(illusions(d)).containsExactly(Hex(4, -3))
        d.expect(DecisionKind.DEFEND, "wasp")
    }

    @Test
    fun `Phantasmal Champion - up to 2 spaces`() {
        val d = neb("nebkher-red-3a") {
            hero("wasp", Team.RED, Hex(4, -2)) { hand("wasp-blue-1") }
            illusion(Hex(4, -5)) // 3 from Wasp
        }
        d.action("nebkher", ActionType.PRIMARY)
        // (4,-3) is the only space 2 or fewer away next to Wasp: the engine moves the Illusion there.
        assertThat(illusions(d)).containsExactly(Hex(4, -3))
        d.expect(DecisionKind.DEFEND, "wasp")
        // Warrior can't reach: no bullet 1, no adjacent unit, no Primary action.
        val e = neb("nebkher-red-2a") {
            hero("wasp", Team.RED, Hex(4, -2)) { hand("wasp-blue-1") }
            illusion(Hex(4, -5))
        }
        assertThat(e.actions()).doesNotContain(ActionType.PRIMARY)
    }

    // ---- Twist Fate / Devious Scheme (red) ------------------------------------------------------

    @Test
    fun `Twist Fate - after the attack you may swap an enemy unit in range with an Illusion adjacent to you`() {
        val d = neb("nebkher-red-2b") {
            hero("wasp", Team.RED, Hex(3, -2)) { hand("wasp-blue-1") }
            illusion(Hex(0, -2)) // adjacent
            illusion(Hex(-1, -1)) // 2 away: not for Twist Fate
        }
        d.action("nebkher", ActionType.PRIMARY)
        d.defend("wasp-blue-1")
        d.expect(DecisionKind.CHOOSE_UNIT, "nebkher")
        d.unit("wasp")
        assertThat(d.at("wasp")).isEqualTo(Hex(0, -2))
        assertThat(illusions(d)).containsExactly(Hex(3, -2), Hex(-1, -1))
    }

    @Test
    fun `Devious Scheme - an Illusion token in range`() {
        val d = neb("nebkher-red-3b") {
            hero("wasp", Team.RED, Hex(3, -2)) { hand("wasp-blue-1") }
            illusion(Hex(-1, -1)) // 2 away: in range
        }
        d.action("nebkher", ActionType.PRIMARY)
        d.defend("wasp-blue-1")
        d.unit("wasp")
        assertThat(d.at("wasp")).isEqualTo(Hex(-1, -1))
    }

    // ---- Fleeting Image / Multiple Projections / Master of Illusions (green) ---------------------

    @Test
    fun `Fleeting Image - place an Illusion in radius, then you may swap with an Illusion in play`() {
        val d = neb("nebkher-green-1") {
            hero("wasp", Team.RED, Hex(-6, -2))
            illusion(Hex(4, -7)) // far away: "in play"
        }
        d.action("nebkher", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_SPACE, "nebkher")
        assertThat(d.d.options.none { it is Choice.Decline }).isTrue() // mandatory
        assertThat(d.spaces().all { HexGrid.distance(it, me) in 1..2 }).isTrue()
        d.space(3, -2)
        d.expect(DecisionKind.CHOOSE_TOKEN, "nebkher")
        d.token(d.s.state.pieces.first { it.at == Hex(4, -7) }.id)
        assertThat(d.at("nebkher")).isEqualTo(Hex(4, -7))
        assertThat(illusions(d)).containsExactly(Hex(3, -2), me)
    }

    @Test
    fun `Multiple Projections and Master of Illusions - up to 2 and up to 3`() {
        for ((card, n) in listOf("nebkher-green-2a" to 2, "nebkher-green-3a" to 3)) {
            val d = neb(card) { hero("wasp", Team.RED, Hex(-6, -2)) }
            d.action("nebkher", ActionType.PRIMARY)
            repeat(n) {
                assertThat(d.d.options.any { it is Choice.Decline }).isTrue()
                d.space(d.spaces().first())
            }
            assertThat(illusions(d)).hasSize(n)
            d.expect(DecisionKind.CHOOSE_TOKEN, "nebkher") // then the optional swap
        }
    }

    // ---- Illusionary Force / Army (green) -------------------------------------------------------

    @Test
    fun `Illusionary Force - This round, while you act, Illusions count as friendly melee minions`() {
        val d = neb("nebkher-green-2b") { hero("wasp", Team.RED, Hex(-6, -2)) }
        d.action("nebkher", ActionType.PRIMARY)
        d.space(2, -2)
        d.space(0, -2)
        assertThat(illusions(d)).hasSize(2)
        assertThat(d.s.state.effects.single { it.cardId == "nebkher-green-2b" }.timing).isEqualTo(Timing.THIS_ROUND)
        // Later this round NebKher attacks Wasp next to an Illusion: -1 Defense (an enemy melee minion for her).
        fun attack(withEffect: Boolean): Int? {
            val e = neb("nebkher-red-2b", turn = 2) {
                hero("wasp", Team.RED, Hex(3, -2)) { hand("wasp-blue-1") }
                illusion(Hex(4, -2))
                if (withEffect) effect("nebkher", "nebkher-green-2b", Timing.THIS_ROUND, ActionKind.SKILL, madeTurn = 1)
            }
            e.action("nebkher", ActionType.PRIMARY)
            return e.expect(DecisionKind.DEFEND, "wasp").context.minionModifier
        }
        assertThat(attack(withEffect = true)).isEqualTo(-1)
        assertThat(attack(withEffect = false)).isEqualTo(0)
    }

    @Test
    fun `Illusionary Army - up to 3, and not while an enemy acts`() {
        val d = neb("nebkher-green-3b") { hero("wasp", Team.RED, Hex(-6, -2)) }
        d.action("nebkher", ActionType.PRIMARY)
        repeat(3) { d.space(d.spaces().first()) }
        assertThat(illusions(d)).hasSize(3)
        // Wasp attacks NebKher next to an Illusion: it is no minion while she acts.
        val e = pos {
            clearMinions()
            hero("nebkher", Team.BLUE, me) { hand("nebkher-blue-1") }
            hero("wasp", Team.RED, Hex(2, -2)) { played("wasp-gold") }
            illusion(Hex(0, -2))
            effect("nebkher", "nebkher-green-3b", Timing.THIS_ROUND, ActionKind.SKILL)
            phase = PositionPhase.Resolving(next = "wasp")
        }
        e.action("wasp", ActionType.PRIMARY)
        assertThat(e.expect(DecisionKind.DEFEND, "nebkher").context.minionModifier).isEqualTo(0)
    }

    // ---- Imbue Doubt / Time to Reconsider / An Illusion of Choice (blue) -------------------------

    @Test
    fun `Imbue Doubt - name a color, next turn after playing cards an enemy hero in radius discards one`() {
        val d = neb("nebkher-blue-1") { hero("wasp", Team.RED, Hex(-6, -2)) }
        d.action("nebkher", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_OPTION, "nebkher")
        assertThat(
            d.d.options.map {
                (it as Choice.Option).label
            },
        ).containsExactly("Gold", "Silver", "Red", "Green", "Blue").inOrder()
        option(d, "Red")
        val e = d.s.state.effects.single { it.cardId == "nebkher-blue-1" }
        assertThat(e.timing).isEqualTo(Timing.NEXT_TURN_AFTER_PLAYING)
        // Next turn, after the reveal: Wasp (in radius 3) discards her red card.
        val n = pos {
            clearMinions()
            turn = 2
            hero("nebkher", Team.BLUE, me) { played("nebkher-gold") }
            hero("wasp", Team.RED, Hex(3, -2)) {
                played("wasp-silver")
                hand("wasp-red-1", "wasp-blue-1")
            }
            hero("xargatha", Team.RED, Hex(-4, -2)) {
                played("xargatha-gold")
                hand("xargatha-red-1")
            } // 5 away
            effect(
                "nebkher",
                "nebkher-blue-1",
                Timing.NEXT_TURN_AFTER_PLAYING,
                ActionKind.SKILL,
                madeTurn = 1,
                data = mapOf("colour" to "RED"),
            )
            phase = PositionPhase.Resolving(applyRevealEffects = true)
        }
        assertThat(n.zoneOf("wasp", "wasp-red-1")).isEqualTo(Zone.DISCARD)
        assertThat(n.zoneOf("xargatha", "xargatha-red-1")).isEqualTo(Zone.HAND)
    }

    @Test
    fun `Imbue Doubt - FAQ, an effect made on turn 4 does not carry into the next round`() {
        val d = neb("nebkher-blue-1", turn = 4) { hero("wasp", Team.RED, Hex(3, -2)) }
        d.action("nebkher", ActionType.PRIMARY)
        option(d, "Red")
        assertThat(d.s.state.round).isEqualTo(2)
        assertThat(d.s.state.effects.none { it.cardId == "nebkher-blue-1" }).isTrue()
    }

    @Test
    fun `Time to Reconsider - radius 4, if able`() {
        val n = pos {
            clearMinions()
            turn = 2
            hero("nebkher", Team.BLUE, me) { played("nebkher-gold") }
            hero("wasp", Team.RED, Hex(4, -6)) {
                played("wasp-silver")
                hand("wasp-blue-1")
            } // 4 away, no red card
            effect(
                "nebkher",
                "nebkher-blue-2a",
                Timing.NEXT_TURN_AFTER_PLAYING,
                ActionKind.SKILL,
                madeTurn = 1,
                data = mapOf("colour" to "RED"),
            )
            phase = PositionPhase.Resolving(applyRevealEffects = true)
        }
        assertThat(n.zoneOf("wasp", "wasp-blue-1")).isEqualTo(Zone.HAND)
        val m = pos {
            clearMinions()
            turn = 2
            hero("nebkher", Team.BLUE, me) { played("nebkher-gold") }
            hero("wasp", Team.RED, Hex(4, -6)) {
                played("wasp-silver")
                hand("wasp-blue-1")
            }
            effect(
                "nebkher",
                "nebkher-blue-2a",
                Timing.NEXT_TURN_AFTER_PLAYING,
                ActionKind.SKILL,
                madeTurn = 1,
                data = mapOf("colour" to "BLUE"),
            )
            phase = PositionPhase.Resolving(applyRevealEffects = true)
        }
        assertThat(m.zoneOf("wasp", "wasp-blue-1")).isEqualTo(Zone.DISCARD)
    }

    @Test
    fun `An Illusion of Choice - up to two enemy heroes in radius each discard a card of that color`() {
        val n = pos {
            clearMinions()
            turn = 2
            hero("nebkher", Team.BLUE, me) { played("nebkher-gold") }
            hero("wasp", Team.RED, Hex(3, -2)) {
                played("wasp-silver")
                hand("wasp-red-1")
            }
            hero("xargatha", Team.RED, Hex(-2, -2)) {
                played("xargatha-gold")
                hand("xargatha-red-1")
            }
            effect(
                "nebkher",
                "nebkher-blue-3a",
                Timing.NEXT_TURN_AFTER_PLAYING,
                ActionKind.SKILL,
                madeTurn = 1,
                data = mapOf("colour" to "RED"),
            )
            phase = PositionPhase.Resolving(applyRevealEffects = true)
        }
        n.expect(DecisionKind.CHOOSE_UNIT, "nebkher")
        assertThat(n.d.options.any { it is Choice.Decline }).isTrue() // "up to"
        n.unit("wasp")
        n.unit("xargatha")
        assertThat(n.zoneOf("wasp", "wasp-red-1")).isEqualTo(Zone.DISCARD)
        assertThat(n.zoneOf("xargatha", "xargatha-red-1")).isEqualTo(Zone.DISCARD)
    }

    // ---- Crack in Reality / Shift Reality (blue) -----------------------------------------------

    /** NebKher (Blue) on the line r = -2; Wasp (Red) at (0,-3) with Charged Boomerang (range 3, not in line). */
    private fun crack(card: String) = pos {
        clearMinions()
        hero("nebkher", Team.BLUE, me) { played(card) }
        hero("wasp", Team.RED, Hex(0, -3)) { played("wasp-red-2b") }
        hero("arien", Team.BLUE, Hex(1, -1)) { hand("arien-blue-1") } // the other side
        hero("brogan", Team.BLUE, Hex(2, -2)) { hand("brogan-blue-1") } // on the line
        phase = PositionPhase.Resolving(next = "nebkher")
    }

    @Test
    fun `Crack in Reality - units on one side cannot interact with the other side, units on the line with both`() {
        val d = crack("nebkher-blue-2b")
        d.action("nebkher", ActionType.PRIMARY)
        d.expect(DecisionKind.CHOOSE_OPTION, "nebkher")
        d.option(0) // the line r = -2 (through (0,-2) and (2,-2))
        d.action("wasp", ActionType.PRIMARY)
        // Arien (other side) can't be targeted; Brogan and NebKher on the line can (FAQ).
        d.expect(DecisionKind.CHOOSE_UNIT, "wasp")
        assertThat(d.units()).containsExactly("nebkher", "brogan")
    }

    @Test
    fun `Crack in Reality - movement into the other side is blocked`() {
        val d = crack("nebkher-blue-2b")
        d.action("nebkher", ActionType.PRIMARY)
        d.option(0)
        d.action("wasp", ActionType.MOVEMENT)
        d.expect(DecisionKind.CHOOSE_SPACE, "wasp")
        assertThat(d.spaces()).isNotEmpty()
        assertThat(d.spaces().all { it.r <= -2 }).isTrue()
    }

    @Test
    fun `Shift Reality - nor with you`() {
        val d = crack("nebkher-blue-3b")
        d.action("nebkher", ActionType.PRIMARY)
        d.option(0)
        d.action("wasp", ActionType.PRIMARY)
        // NebKher can't be targeted by a unit off the line; Brogan, on the line, still can.
        d.expect(DecisionKind.DEFEND, "brogan")
    }

    // ---- random games ----------------------------------------------------------------------------

    @Test
    fun `random legal games with NebKher run without errors`() {
        Batch7Games.run("nebkher", games = 150, seed = 23)
    }
}
