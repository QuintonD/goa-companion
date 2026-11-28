package dev.quinton.goa.core.game.ai.heuristic

import com.google.common.truth.Truth.assertWithMessage
import dev.quinton.goa.core.domain.board.HexGrid
import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.AnswerResult
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.ai.AiHarness
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.position.position
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

/**
 * Strategy round, Track W (wave management and positioning): positions where each rule of the wave layer must fire
 * and where it must not, at strength 9 with the layer on and with `wave=0` (the baseline book).
 *
 * The board is forgotten-island (one lane: red-throne, lane-1-1, lane-1-2, lane-1-3, blue-throne). A Red push moves
 * the Battle Zone toward Blue's Throne; the last enemy minion in the Battle Zone pushes at once and a fresh wave
 * spawns in the next zone (RB p.8, `Units.pushLane`).
 */
class WaveRulesTest {
    private val geo = TestData.engine.data.geometry(TestData.FOREST)

    private fun spaces(zone: String): List<Hex> =
        geo.zoneSpaces.getValue(zone).sortedWith(compareBy<Hex> { it.q }.thenBy { it.r })

    /** The space of [zone] nearest [to]. */
    private fun nearest(zone: String, to: String): Hex =
        spaces(zone).minBy { h -> geo.zoneSpaces.getValue(to).minOf { HexGrid.distance(h, it) } }

    /** Spaces of [zone] at least [from] spaces from every space of [away]. */
    private fun far(zone: String, away: String, from: Int): List<Hex> =
        spaces(zone).filter { h -> geo.zoneSpaces.getValue(away).all { HexGrid.distance(h, it) >= from } }

    /** A movement card of [heroId] that does not attack (a reposition card). */
    private fun moveCard(s: EngineSession, heroId: String): String {
        val ctx = HContext(s, null, Team.RED, HeuristicProfile.of(9))
        return TestData.engine.data.catalogHero(heroId).cards.map { it.id }
            .first { id -> runCatching { ctx.card(id) }.getOrNull()?.let { it.hasMovement && !it.attackCard } == true }
    }

    /**
     * Center zone, Red [red] minions against Blue [blue], Red's Brogan at [brogan] resolving [card] (null: the
     * reposition card) on [turn]; every other hero has resolved.
     */
    private fun lane(
        red: Int,
        blue: Int,
        brogan: Hex,
        turn: Int,
        waves: Int = 3,
        card: String? = null,
    ): EngineSession {
        val center = spaces("lane-1-2")
        val probe = position(TestData.engine, TestData.FOREST) {
            clearMinions()
            hero("brogan", Team.RED, brogan)
            hero("arien", Team.BLUE, center[center.size / 2])
        }
        val played = card ?: moveCard(probe, "brogan")
        val free = center.filter { it != brogan }
        return position(TestData.engine, TestData.FOREST) {
            clearMinions()
            this.turn = turn
            wavesLeft("lane-1", waves)
            hero("brogan", Team.RED, brogan) {
                played(played)
                hand("brogan-silver", "brogan-red-1")
            }
            hero("mrak", Team.RED, free[0]) { resolved(turn, "mrak-green-1") }
            hero("arien", Team.BLUE, free[1]) { resolved(turn, "arien-green-1") }
            hero("wasp", Team.BLUE, free[2]) { resolved(turn, "wasp-blue-1") }
            repeat(red) { minion(Team.RED, MinionType.MELEE, free[3 + it]) }
            repeat(blue) { minion(Team.BLUE, MinionType.MELEE, free[3 + red + it]) }
            phase = PositionPhase.Resolving(next = "brogan")
        }
    }

    private fun plan(s: EngineSession, tune: Map<String, Double> = emptyMap()): TurnPlan {
        val d = s.pending ?: error("no question")
        val ctx = HContext(s, d, Team.RED, HeuristicProfile.of(9).copy(tune = tune))
        return TurnPlan.derive(ctx)
    }

    @Test
    fun `the projection reads the wave from each side`() {
        val center = spaces("lane-1-2")
        val s = lane(red = 1, blue = 5, brogan = center.first(), turn = 1)
        val red = waveView(HContext(s, s.pending, Team.RED, HeuristicProfile.of(9)), "lane-1")
        val blue = waveView(HContext(s, s.pending, Team.BLUE, HeuristicProfile.of(9)), "lane-1")
        assertWithMessage("Red 1 v 5").that(red.state).isEqualTo(WaveState.LOST)
        assertWithMessage("Blue 5 v 1").that(blue.state).isEqualTo(WaveState.WINNING)
        assertWithMessage("Blue's push moves into Red's pre-throne").that(red.pushInto).isEqualTo("lane-1-1")
        val even = lane(red = 4, blue = 4, brogan = center.first(), turn = 1)
        val v = waveView(HContext(even, even.pending, Team.RED, HeuristicProfile.of(9)), "lane-1")
        assertWithMessage("4 v 4").that(v.state).isEqualTo(WaveState.CONTESTED)
    }

    @Test
    fun `losing the wave and far from the zone it moves into - turn 4 repositions, turn 1 farms first`() {
        // Brogan on Blue's side of the center (the far side from lane-1-1), Red down 4 against 5 (LOSING, not LOST).
        val at = far("lane-1-3", "lane-1-1", 7).first()
        val late = lane(red = 4, blue = 5, brogan = at, turn = 4)
        assertWithMessage("turn 4, wave on").that(plan(late).intent).isEqualTo(Intent.RETREAT)
        assertWithMessage("turn 4, wave off").that(plan(late, mapOf("wave" to 0.0)).intent).isNotEqualTo(Intent.RETREAT)
        val early = lane(red = 4, blue = 5, brogan = at, turn = 1)
        assertWithMessage("turn 1 with three turns to walk").that(plan(early).intent).isNotEqualTo(Intent.RETREAT)
        // Already one walk from the zone the push moves into: nothing to do.
        val near = nearest("lane-1-1", "lane-1-2")
        assertWithMessage("in the next zone").that(plan(lane(4, 5, near, 4)).intent).isNotEqualTo(Intent.RETREAT)
        // Winning: no reposition.
        assertWithMessage("winning").that(plan(lane(6, 4, at, 4)).intent).isNotEqualTo(Intent.RETREAT)
    }

    @Test
    fun `the zone a losing push moves into is worth standing in, until the last wave`() {
        // Red 2 against Blue 5 in the center: Blue's push is coming. Brogan in Red's pre-throne (where it lands)
        // or as far on Blue's side: the difference the wave layer adds is positive, and zero when no push follows.
        val home = nearest("lane-1-1", "lane-1-2")
        val away = far("lane-1-3", "lane-1-1", 6).first()
        fun gain(waves: Int, tune: Map<String, Double>): Double {
            val profile = HeuristicProfile.of(9).copy(tune = tune)
            fun value(at: Hex): Double {
                val s = lane(red = 2, blue = 5, brogan = at, turn = 4, waves = waves)
                return HEval.evaluate(HContext(s, s.pending, Team.RED, profile), Team.RED)
            }
            return value(home) - value(away)
        }
        val on = gain(3, emptyMap()) - gain(3, mapOf("wave.next" to 0.0))
        assertWithMessage("3 waves: presence in the next zone").that(on).isGreaterThan(0.5)
        val last = gain(1, emptyMap()) - gain(1, mapOf("wave.next" to 0.0))
        assertWithMessage("last wave: no next push").that(last).isWithin(1e-9).of(0.0)
    }

    @Test
    fun `the safe minion over a poke, and the real kill over the minion`() {
        // Brogan's Gold (Attack 4, a unit adjacent) between a Blue minion and Arien. Arien holds Blue 1
        // (Defense 5): the hit is a discard. Empty-handed, Arien is a real kill (G11), which G59 must not tax.
        val brogan = Hex(3, -6)
        val arien = Hex(2, -6)
        val minion = Hex(4, -6)
        fun spot(arienHand: List<String>): EngineSession = position(TestData.engine, TestData.FOREST) {
            clearMinions()
            turn = 2
            hero("brogan", Team.RED, brogan) {
                played("brogan-gold")
                hand("brogan-silver", "brogan-red-1", "brogan-blue-1")
            }
            hero("mrak", Team.RED, Hex(-1, 1)) { resolved(2, "mrak-green-1") }
            hero("arien", Team.BLUE, arien) {
                resolved(2, "arien-green-1")
                hand(*arienHand.toTypedArray())
            }
            hero("wasp", Team.BLUE, Hex(5, -9)) { resolved(2, "wasp-blue-1") }
            minion(Team.BLUE, MinionType.MELEE, minion)
            minion(Team.BLUE, MinionType.MELEE, Hex(2, -1))
            minion(Team.BLUE, MinionType.MELEE, Hex(3, -1))
            minion(Team.RED, MinionType.MELEE, Hex(-1, -1))
            minion(Team.RED, MinionType.MELEE, Hex(-2, 2))
            minion(Team.RED, MinionType.MELEE, Hex(-2, 1))
            phase = PositionPhase.Resolving(next = "brogan")
        }
        val poke = spot(listOf("arien-blue-1", "arien-silver", "arien-red-1"))
        val m = poke.state.minions.single { it.at == minion }.id
        assertWithMessage("a poke").that(target(poke, 9, emptyMap())).isEqualTo(m)
        // G59 on top of the look: a poke pays, a real kill (an empty hand) does not, and nothing with the layer off.
        val (pf, pd) = unitQuestion(poke)
        val (kf, kd) = unitQuestion(spot(emptyList()))
        fun g59(f: EngineSession, d: Decision, tune: Map<String, Double> = emptyMap()): Double {
            val ctx = HContext(f, d, Team.RED, HeuristicProfile.of(9).copy(tune = tune))
            return UnitRules.commitment(ctx, d, Choice.Unit("arien"))
        }
        assertWithMessage("poke").that(g59(pf, pd)).isLessThan(0.0)
        assertWithMessage("poke, wave off").that(g59(pf, pd, mapOf("wave" to 0.0))).isEqualTo(0.0)
        assertWithMessage("empty hand").that(g59(kf, kd)).isEqualTo(0.0)
    }

    /** Brogan's Primary, up to his target question. */
    private fun unitQuestion(s: EngineSession): Pair<EngineSession, Decision> {
        val f = s.forkSession()
        repeat(6) {
            val d = f.pending ?: error("no question")
            if (d.kind == DecisionKind.CHOOSE_UNIT) return f to d
            val c = d.options.first { it is Choice.Action && it.action == dev.quinton.goa.core.game.ActionType.PRIMARY }
            check(f.answer(d.id, c) is AnswerResult.Accepted)
        }
        error("no target question")
    }

    /** The book at [strength] answers Brogan's questions; the unit his attack targets. */
    private fun target(s: EngineSession, strength: Int, tune: Map<String, Double>): String? {
        val f = s.forkSession()
        val ai = HeuristicAi(strength, 7L, tune = tune)
        repeat(6) {
            val d = f.pending ?: return null
            if (d.seat != "brogan") return null
            val c = if (d.kind == DecisionKind.CHOOSE_ACTION) {
                d.options.first { it is Choice.Action && it.action == dev.quinton.goa.core.game.ActionType.PRIMARY }
            } else {
                ai.choose(f, d)
            }
            if (d.kind == DecisionKind.CHOOSE_UNIT) return (c as? Choice.Unit)?.unitId
            check(f.answer(d.id, c) is AnswerResult.Accepted)
        }
        return null
    }

    /**
     * Recorded (line-up 2504, Red, the baseline's course): round 4, turn 1, Silverarrow's attack may take Min, who
     * keeps a defense, or the minion m29. Blue still acts after him. The baseline looked at the hit on Min only up to
     * Min's hidden defense and at the minion through the whole turn, and took the poke; with one horizon (`wave.even`)
     * the minion is the better play.
     */
    @Test
    fun `a recorded poke - one horizon for both targets takes the minion`() {
        val (s, d) = recorded(2504, true, 4, 1, "silverarrow", DecisionKind.CHOOSE_UNIT)
        val seed = (50_000L + 2504) * 2
        val base = HeuristicAi(9, seed, tune = mapOf("wave" to 0.0)).choose(s.forkSession(), d)
        val wave = HeuristicAi(9, seed).choose(s.forkSession(), d)
        assertWithMessage("baseline").that(base).isEqualTo(Choice.Unit("min"))
        assertWithMessage("wave layer").that(wave).isEqualTo(Choice.Unit("m29"))
        assertWithMessage("same answer twice").that(HeuristicAi(9, seed).choose(s.forkSession(), d)).isEqualTo(wave)
    }

    /** The first [kind] question of [seat] on [round]/[turn] of a [WaveAuditTest] game, h9 with `wave=0` both sides. */
    private fun recorded(
        i: Int,
        aRed: Boolean,
        round: Int,
        turn: Int,
        seat: String,
        kind: DecisionKind,
    ): Pair<EngineSession, Decision> {
        val (red, blue) = AiHarness.mixedLineups(i + 1, size = 2)[i]
        val coin = if (i % 2 == 0) Team.RED else Team.BLUE
        val seed = (50_000L + i) * 2
        val base = mapOf("wave" to 0.0)
        val pa = HeuristicAi(9, if (aRed) seed else seed + 1, tune = base)
        val pb = HeuristicAi(9, if (aRed) seed + 1 else seed, tune = base)
        var found: Pair<EngineSession, Decision>? = null
        try {
            AiHarness.play(
                AiHarness.setup(red, blue, AiLevel.NORMAL, AiLevel.NORMAL, coin),
                mapOf(Team.RED to if (aRed) pa else pb, Team.BLUE to if (aRed) pb else pa),
                maxDecisions = 4000,
                observe = { s, d ->
                    if (s.state.round == round && s.state.turn == turn && d.seat == seat && d.kind == kind) {
                        found = s.forkSession() to d
                        throw Reached()
                    }
                },
            )
        } catch (_: Reached) {
            // The question is reached: the rest of the game is not needed.
        }
        return found ?: error("not reached")
    }

    private class Reached : RuntimeException()
}
