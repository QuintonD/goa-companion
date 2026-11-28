package dev.quinton.goa.core.game.ai.heuristic

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Controller
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.ai.AiHarness
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.position.position
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

/**
 * Team play (strategy round, Track T; TeamPlay.kt, GENERAL G113–G118) on built positions: each rule fires where
 * it must and stays quiet where it must not; with `team=0` nothing of it runs; a human teammate's facedown card
 * is never read.
 */
class TeamPlayBookTest {
    private val ai = Controller.Ai(AiLevel.EXPERT)

    /**
     * Turn [turn], Red to play from seat 1: Xargatha (seat 0, Red, [allyController]) has played [allyCard]
     * facedown; Wasp (Red, computer) chooses from [ourHand]; Arien (Blue) stands next to both with [arienHand];
     * Brogan (Blue) is far away. Wasp's Gold (initiative 12) and Xargatha's Red (7) both target an adjacent unit.
     */
    @Suppress("LongParameterList")
    private fun build(
        allyCard: String?,
        allyHand: List<String> = listOf("xargatha-blue-1"),
        ourHand: List<String> = listOf("wasp-gold", "wasp-blue-1"),
        arienHand: List<String> = listOf("arien-blue-1", "arien-green-1"),
        turn: Int = 4,
        allyController: Controller = ai,
        fromSeat: Int = 1,
    ): EngineSession = position(TestData.engine, TestData.FOREST) {
        round = 2
        this.turn = turn
        clearMinions()
        hero("xargatha", Team.RED, Hex(2, -3)) {
            controller = allyController
            if (allyCard != null) played(allyCard)
            hand(*allyHand.toTypedArray())
        }
        hero("wasp", Team.RED, Hex(2, -2)) {
            controller = ai
            hand(*ourHand.toTypedArray())
        }
        hero("arien", Team.BLUE, Hex(3, -3)) {
            controller = ai
            hand(*arienHand.toTypedArray())
        }
        hero("brogan", Team.BLUE, Hex(-3, 2)) {
            controller = ai
            hand("brogan-blue-1", "brogan-green-1", "brogan-red-1")
        }
        phase = PositionPhase.PlayCards(fromSeat = fromSeat)
    }

    private fun ctx(s: EngineSession, tune: Map<String, Double> = emptyMap()): HContext {
        val d = s.pending!!
        val p = HeuristicProfile.of(9).copy(tune = tune)
        return HContext(s, d, d.team, p)
    }

    @Test
    fun `a computer teammate's facedown card is its card this turn, a human's is not`() {
        val s = build("xargatha-red-1")
        assertThat(s.pending!!.seat).isEqualTo("wasp")
        val known = ctx(s).possibleCards("xargatha").filter { it.playingNow }.map { it.card.id }
        assertThat(known).containsExactly("xargatha-red-1")
        // Knob off: as before, the facedown card is one of the pool.
        assertThat(ctx(s, mapOf("team" to 0.0)).possibleCards("xargatha").none { it.playingNow }).isTrue()
        assertThat(ctx(s, mapOf("team.known" to 0.0)).possibleCards("xargatha").none { it.playingNow }).isTrue()
        // A person's facedown card stays hidden (p.4).
        val human = build("xargatha-red-1", allyController = Controller.Human)
        assertThat(ctx(human).possibleCards("xargatha").none { it.playingNow }).isTrue()
        assertThat(TeamPlay.knownCard(ctx(human), human.state.hero("xargatha")!!)).isNull()
    }

    /**
     * Arien's turn-4 hand (two cards: one played, one to defend with) that Xargatha's Red alone does not surely
     * defeat, so a strip before it matters.
     */
    private val arienHand: List<String> by lazy {
        val hands = listOf(
            listOf("arien-red-1", "arien-blue-1"),
            listOf("arien-red-1", "arien-green-1"),
            listOf("arien-blue-1", "arien-silver"),
            listOf("arien-blue-1", "arien-green-1"),
        )
        hands.first { hand ->
            val s = build("xargatha-red-1", arienHand = hand)
            val c = ctx(s)
            val arien = s.state.hero("arien")!!
            // Neither our Gold nor Xargatha's Red defeats Arien alone for sure.
            listOf("xargatha" to "xargatha-red-1", "wasp" to "wasp-gold").all { (hero, card) ->
                val k = c.card(card)
                val total = k.attack + c.itemBonus(hero, dev.quinton.goa.core.model.catalog.Stat.ATTACK)
                c.teamKillChance("arien", total, k.ranged) < 0.5 &&
                    c.teamBaseStrike(arien, total, k.ranged, 0) < c.killWorth("arien") - 1.0
            }
        }
    }

    @Test
    fun `a known teammate attack after ours turns our strip into its kill`() {
        // Turn 4: Arien holds two cards, plays one, defends with the other; Xargatha's Red acts after our Gold.
        val follow = build("xargatha-red-1", arienHand = arienHand)
        val c = ctx(follow)
        c.teamInit = TeamPlay.initiative(c, "wasp", "wasp-gold")
        val gain = TeamPlay.followUp(c, follow.state.hero("arien")!!, chance = 0.0)
        assertThat(gain).isNotNull()
        assertThat(gain!!).isGreaterThan(5.0)
        // Must not fire: Xargatha's known card is not an attack.
        val move = build("xargatha-blue-1", allyHand = listOf("xargatha-red-1"), arienHand = arienHand)
        val m = ctx(move)
        m.teamInit = c.teamInit
        assertThat(TeamPlay.followUp(m, move.state.hero("arien")!!, chance = 0.0)).isEqualTo(0.0)
        // Unknown (a person's card): the old constant decides (null).
        val human = build("xargatha-red-1", allyController = Controller.Human, arienHand = arienHand)
        val h = ctx(human)
        h.teamInit = c.teamInit
        assertThat(TeamPlay.followUp(h, human.state.hero("arien")!!, chance = 0.0)).isNull()
        // A teammate whose card acts first has met the target already: no follow-up from it.
        val fast = build("xargatha-gold", allyHand = listOf("xargatha-blue-1"), arienHand = arienHand)
        val f = ctx(fast)
        f.teamInit = TeamPlay.initiative(f, "wasp", "wasp-blue-1")
        assertThat(TeamPlay.followUp(f, fast.state.hero("arien")!!, chance = 0.0)).isEqualTo(0.0)
    }

    @Test
    fun `a hit after a teammate's priced hit meets the thinner hand`() {
        // G114: inside a look, Xargatha's hit on Arien was priced first (Arien threw a card, or was defeated).
        val s = build("xargatha-red-1", arienHand = arienHand)
        val gold = ctx(s).card("wasp-gold")
        fun worth(hits: Map<String, Double>, dead: Map<String, Double>): Double {
            val c = ctx(s)
            c.teamHits = hits
            c.teamDead = dead
            // Xargatha's own follow-up is the look's to play (not priced twice).
            c.teamSimulated = setOf("xargatha")
            return c.heroStrikeWorth("arien", gold.attack, gold.ranged)
        }
        val fresh = worth(emptyMap(), emptyMap())
        val thinned = worth(mapOf("arien" to 1.0), mapOf("arien" to 0.0))
        // Turn 4: one card left in hand after the strip, and it is about to be played: our hit is the defeat.
        assertThat(thinned).isGreaterThan(fresh + 5.0)
        assertThat(thinned).isWithin(1e-9).of(ctx(s).killWorth("arien"))
        // Defeated already: nothing left to hit.
        assertThat(worth(mapOf("arien" to 1.0), mapOf("arien" to 1.0))).isWithin(1e-9).of(0.0)
        // The order look's debug line: the teammate's hit after our Gold is priced (a positive strike).
        val lines = ArrayList<String>()
        CardSim.debug = { lines += it }
        try {
            CardSim.value(ctx(s), "wasp-gold")
        } finally {
            CardSim.debug = null
        }
        val strikes = lines.mapNotNull { it.substringAfter("strike ", "").toDoubleOrNull() }
        assertThat(strikes.any { it > 0.0 }).isTrue()
    }

    @Test
    fun `the card look plays a known teammate card after a faster card of ours and before a slower one`() {
        // Xargatha's Gold (11) facedown: our Gold (12) acts first, our Blue (10) after it.
        val s = build("xargatha-gold", allyHand = listOf("xargatha-blue-1"), ourHand = listOf("wasp-gold", "wasp-blue-1"))
        fun played(card: String, tune: Map<String, Double> = emptyMap()): List<String> {
            val lines = ArrayList<String>()
            CardSim.debug = { lines += it }
            try {
                CardSim.value(ctx(s, tune), card)
            } finally {
                CardSim.debug = null
            }
            return lines.filter { it.startsWith("team: xargatha ") && ("played" in it || "not played" in it) }
        }
        // After our faster Gold: once per action of ours (the postlude).
        assertThat(played("wasp-gold")).isNotEmpty()
        // Before our slower Blue: once for the decision (the prelude, shared by every slower card).
        assertThat(played("wasp-blue-1")).hasSize(1)
        // Knob off: the old G14 prelude only (Xargatha does not surely act before every card of ours).
        assertThat(played("wasp-gold", mapOf("team.order" to 0.0))).isEmpty()
        assertThat(played("wasp-gold", mapOf("team" to 0.0))).isEmpty()
    }

    @Test
    fun `the team keeps a turn-4 finisher for an enemy who will arrive empty-handed`() {
        // Turn 3, before Blue plays: Arien holds two cards, plays one now and starts turn 4 with one.
        val noHelp = build("xargatha-blue-1", allyHand = listOf("xargatha-green-1"), turn = 3)
        val c = ctx(noHelp)
        assertThat(TeamPlay.emptyOnTurn4(c).map { it.id }).containsExactly("arien")
        val alone = TeamPlay.reserve(c, c.card("wasp-gold"))
        assertThat(alone).isLessThan(-0.3 * c.killWorth("arien"))
        // Not the finisher's card: no cost.
        assertThat(TeamPlay.reserve(c, c.card("wasp-blue-1"))).isEqualTo(0.0)
        // A teammate still holds an attack for turn 4: the cost is small.
        val helped = build("xargatha-blue-1", allyHand = listOf("xargatha-red-1", "xargatha-green-1"), turn = 3)
        val h = ctx(helped)
        val shared = TeamPlay.reserve(h, h.card("wasp-gold"))
        assertThat(shared).isLessThan(0.0)
        assertThat(shared).isGreaterThan(alone)
        // Must not fire: Arien keeps two cards into turn 4.
        val full = build(
            "xargatha-blue-1",
            allyHand = listOf("xargatha-green-1"),
            arienHand = listOf("arien-blue-1", "arien-green-1", "arien-red-1"),
            turn = 3,
        )
        assertThat(TeamPlay.reserve(ctx(full), ctx(full).card("wasp-gold"))).isEqualTo(0.0)
        // Knob off.
        val off = ctx(noHelp, mapOf("team" to 0.0))
        assertThat(TeamPlay.reserve(off, off.card("wasp-gold"))).isEqualTo(0.0)
        // Below strength 9 the layer is off.
        val seven = HContext(noHelp, noHelp.pending!!, Team.RED, HeuristicProfile.of(7))
        assertThat(TeamPlay.reserve(seven, seven.card("wasp-gold"))).isEqualTo(0.0)
    }

    @Test
    fun `the teammate asked first plans with the one still to choose, deterministically`() {
        // Turn 2, Red's first seat chooses; Brogan (computer) has not played yet.
        fun first(partner: Controller) = position(TestData.engine, TestData.FOREST) {
            round = 2
            turn = 2
            clearMinions()
            hero("xargatha", Team.RED, Hex(2, -2)) {
                controller = ai
                hand("xargatha-gold", "xargatha-red-1", "xargatha-blue-1", "xargatha-green-1")
            }
            hero("brogan", Team.RED, Hex(2, -3)) {
                controller = partner
                hand("brogan-gold", "brogan-red-1", "brogan-blue-1", "brogan-green-1")
            }
            hero("arien", Team.BLUE, Hex(3, -3)) { controller = ai }
            hero("wasp", Team.BLUE, Hex(4, -4)) { controller = ai }
            phase = PositionPhase.PlayCards(fromSeat = 0)
        }
        fun ask(s: EngineSession, strength: Int, tune: Map<String, Double> = emptyMap()): Pair<Choice, List<String>> {
            val lines = ArrayList<String>()
            val book = HeuristicAi(strength, 5L, tune = tune)
            book.trace = { lines += it }
            return book.choose(s, s.pending!!) to lines
        }
        val s = first(ai)
        assertThat(s.pending!!.seat).isEqualTo("xargatha")
        assertThat(s.pending!!.kind).isEqualTo(DecisionKind.PLAY_CARD)
        val (a1, t1) = ask(s, 9)
        val (a2, t2) = ask(first(ai), 9)
        assertThat(t1.any { " joint " in it }).isTrue()
        assertThat(a2).isEqualTo(a1)
        assertThat(t2).isEqualTo(t1)
        assertThat(ask(first(ai), 8).second.any { " joint " in it }).isTrue()
        // Must not fire: strength 7, the knob off, a person to plan with.
        assertThat(ask(first(ai), 7).second.none { " joint " in it }).isTrue()
        assertThat(ask(first(ai), 9, mapOf("team.joint" to 0.0)).second.none { " joint " in it }).isTrue()
        assertThat(ask(first(ai), 9, mapOf("team" to 0.0)).second.none { " joint " in it }).isTrue()
        assertThat(ask(first(Controller.Human), 9).second.none { " joint " in it }).isTrue()
    }

    @Test
    fun `a same-team tie is played out both ways from strength 9`() {
        fun tie() = position(TestData.engine, TestData.FOREST) {
            round = 2
            turn = 2
            clearMinions()
            hero("brogan", Team.RED, Hex(2, -3)) { controller = ai; played("brogan-gold") }
            hero("arien", Team.RED, Hex(2, -2)) { controller = ai; played("arien-gold") }
            hero("xargatha", Team.BLUE, Hex(3, -3)) { controller = ai; played("xargatha-green-1") }
            hero("wasp", Team.BLUE, Hex(4, -4)) { controller = ai; played("wasp-green-1") }
            phase = PositionPhase.Resolving()
        }
        val s = tie()
        assertThat(s.pending!!.kind).isEqualTo(DecisionKind.ACT_FIRST)
        fun stages(strength: Int, tune: Map<String, Double> = emptyMap()): List<String> {
            val book = HeuristicAi(strength, 1L, tune = tune)
            val out = ArrayList<String>()
            book.scored = { stage, _, _ -> out += stage }
            val t = tie()
            book.choose(t, t.pending!!)
            return out
        }
        assertThat(stages(9)).contains("look")
        assertThat(stages(9, mapOf("team" to 0.0))).doesNotContain("look")
        assertThat(stages(9, mapOf("team.tie" to 0.0))).doesNotContain("look")
        assertThat(stages(8)).doesNotContain("look")
    }

    @Test
    fun `a human teammate's facedown card is never read, with every team layer on`() {
        // 3v3, turn 2: Red's middle seat is a person who played one card or another; the first seat (a computer) has
        // played too, and the third (a computer) chooses: the order look, the follow-up and the reserve all run.
        val (red, blue) = AiHarness.mixedLineups(1, seed = 707L, size = 3)[0]
        val base = AiHarness.setup(red, blue, AiLevel.EXPERT, AiLevel.EXPERT, Team.RED)
        val person = red[1]
        val setup = base.copy(
            seats = base.seats.map { if (it.heroId == person) it.copy(controller = Controller.Human) else it },
        )
        fun played(last: Boolean): EngineSession {
            val s = TestData.engine.newGame(setup) as EngineSession
            while (true) {
                val d = s.pending!!
                if (s.state.turn == 2 && d.seat == red[2] && d.kind == DecisionKind.PLAY_CARD) return s
                val i = if (last && d.seat == person && d.kind == DecisionKind.PLAY_CARD && s.state.turn == 2) {
                    d.options.size - 1
                } else {
                    0
                }
                check(s.answer(d.id, d.options[i]) is dev.quinton.goa.core.game.AnswerResult.Accepted)
            }
        }
        val a = played(last = false)
        val b = played(last = true)
        fun facedown(s: EngineSession) =
            s.state.hero(person)!!.cards.single { it.zone == dev.quinton.goa.core.game.engine.Zone.PLAYED }.id
        assertThat(facedown(a)).isNotEqualTo(facedown(b))
        assertThat(b.view(red[2])).isEqualTo(a.view(red[2]))
        for (strength in listOf(7, 8, 9, 10)) {
            for (seed in 1L..2L) {
                fun values(s: EngineSession): Pair<Choice, List<String>> {
                    val book = HeuristicAi(strength, seed)
                    val out = ArrayList<String>()
                    book.scored = { stage, options, v -> out += "$stage $options ${v.toList()}" }
                    return book.choose(s, s.pending!!) to out
                }
                assertThat(values(b)).isEqualTo(values(a))
            }
        }
    }
}
