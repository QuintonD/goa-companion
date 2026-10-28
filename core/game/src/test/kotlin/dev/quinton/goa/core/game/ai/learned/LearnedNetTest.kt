package dev.quinton.goa.core.game.ai.learned

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.ai.AiHarness
import dev.quinton.goa.core.game.ai.AiPlayers
import dev.quinton.goa.core.game.ai.EvalKnobs
import dev.quinton.goa.core.game.ai.EvalSource
import dev.quinton.goa.core.game.ai.Evaluator
import dev.quinton.goa.core.game.ai.Features
import dev.quinton.goa.core.game.ai.LearnedNet
import dev.quinton.goa.core.game.ai.LearnedWeights
import dev.quinton.goa.core.game.ai.NetEvaluator
import dev.quinton.goa.core.game.ai.Searcher
import dev.quinton.goa.core.game.ai.Term
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.game.engine.GameState
import dev.quinton.goa.core.model.match.Team
import kotlin.math.abs
import org.junit.Test

/** The checked-in learned evaluation (AI.md "Learned evaluation", ADR-047 part B). */
class LearnedNetTest {
    @Test
    fun `the weights load and were trained on these features`() {
        assertThat(Features.NAMES).hasSize(Features.SIZE)
        assertThat(Features.NAMES.toSet()).hasSize(Features.SIZE)
        // A changed feature list needs retraining (AI.md "How to retrain").
        assertThat(LearnedWeights.SCHEMA_HASH).isEqualTo(Features.SCHEMA_HASH)
        assertThat(LearnedWeights.INPUTS).isEqualTo(Features.SIZE)
        val net = LearnedNet.CURRENT
        val x = FloatArray(2 * Features.SIZE)
        Features(TestData.engine.data).extract(start(LINEUPS[0], Team.RED), x)
        assertThat(net.forward(x, 0).isFinite()).isTrue()
        assertThat(LearnedWeights.COIN_SCALE).isGreaterThan(0.0)
    }

    @Test
    fun `swapping the teams negates the score`() {
        val ev = NetEvaluator(TestData.engine.data)
        // Along a game: one state, both points of view.
        for (st in states(LINEUPS[1], 120)) {
            assertThat(ev.evaluate(st, Team.RED)).isWithin(1e-9).of(-ev.evaluate(st, Team.BLUE))
        }
        // The same heroes on the other side of the board, the coin with them: Red's view of one game is
        // Blue's view of the other, except what depends on the board (it is not exactly symmetric:
        // the hand evaluation itself scores the opening about 15 in Red's favour, its Push Potential
        // counting heroes within 6 spaces of the Battle Zone).
        val features = Features(TestData.engine.data)
        val geometric = Features.NAMES.indices.filter { i -> GEOMETRY.any { it in Features.NAMES[i] } }.toSet()
        for (lineup in LINEUPS) {
            val (red, blue) = lineup
            val a = FloatArray(2 * Features.SIZE)
            val b = FloatArray(2 * Features.SIZE)
            features.extract(start(red to blue, Team.RED), a)
            features.extract(start(blue to red, Team.BLUE), b)
            for (i in 0 until Features.SIZE) {
                if (i in geometric) continue
                assertThat(a[i]).isWithin(1e-6f).of(b[Features.SIZE + i])
                assertThat(a[Features.SIZE + i]).isWithin(1e-6f).of(b[i])
            }
        }
    }

    @Test
    fun `the net and strength 10's hand evaluation keep the knobs the net was trained with`() {
        val data = TestData.engine.data
        // What the net was trained on: v0.8's TUNED scales and no team-size knobs (ADR-047).
        assertThat(Features.KNOBS).isEqualTo(EvalKnobs())
        assertThat(Features.SCALES).isEqualTo(mapOf(Term.EXPOSURE to 0.3, Term.OPPORTUNITY to 2.0))
        // Strength 10's hand parts beside the net carry them too, not part A's 3v3 knobs (5–9 do).
        val v08 = AiPlayers.TUNED.copy(pushPerAttackBySize = emptyMap(), defenselessBySize = emptyMap())
        val profile = AiPlayers.profile(10)
        assertThat(profile.learned).isEqualTo(EvalSource.LEAVES)
        assertThat(profile.knobs).isEqualTo(v08)
        assertThat(AiPlayers.profile(9).knobs).isEqualTo(AiPlayers.TUNED)
        // The net's inputs score the same whatever team-size knobs a hand evaluation carries.
        val withA1 = Searcher(Evaluator(data, AiPlayers.TUNED), profile).leaf
        val without = Searcher(Evaluator(data, v08), profile).leaf
        val tuned = Evaluator(data, AiPlayers.TUNED)
        val plain = Evaluator(data, v08)
        var handDiffers = 0
        for (st in states(LINEUPS[1], 150)) {
            assertThat(withA1.evaluate(st, Team.RED)).isEqualTo(without.evaluate(st, Team.RED))
            if (tuned.evaluate(st, Team.RED) != plain.evaluate(st, Team.RED)) handDiffers++
        }
        // Not vacuous: in 3v3 those knobs do change the hand evaluation of these positions.
        assertThat(AiPlayers.TUNED.pushPerAttackBySize).containsKey(3)
        assertThat(handDiffers).isGreaterThan(0)
    }

    @Test
    fun `an evaluation costs far less than a playout`() {
        val net = NetEvaluator(TestData.engine.data)
        val hand = Evaluator(TestData.engine.data, AiPlayers.TUNED)
        val states = states(LINEUPS[2], 400)
        repeat(3) { for (s in states) net.evaluate(s, Team.RED) + hand.evaluate(s, Team.RED) }
        fun time(f: (GameState) -> Double): Double {
            var sink = 0.0
            val t0 = System.nanoTime()
            repeat(5) { for (s in states) sink += f(s) }
            val ns = (System.nanoTime() - t0).toDouble() / (5 * states.size)
            assertThat(abs(sink)).isAtLeast(0.0)
            return ns
        }
        val netNs = time { net.evaluate(it, Team.RED) }
        val handNs = time { hand.evaluate(it, Team.RED) }
        println(
            "LEARNED evaluation: net ${"%.1f".format(
                netNs / 1000,
            )} µs, hand ${"%.1f".format(handNs / 1000)} µs per position (3v3)",
        )
        // A playout is 0.1–30 ms on the desktop (AI.md "Determinism"); loose for shared machines.
        assertThat(netNs).isLessThan(2_000_000.0)
    }

    private fun start(lineup: Pair<List<String>, List<String>>, coin: Team): GameState {
        val (red, blue) = lineup
        return (
            TestData.engine.newGame(
                AiHarness.setup(red, blue, AiLevel.NORMAL, AiLevel.NORMAL, coin),
            ) as EngineSession
            ).state
    }

    /** States along a seeded strength-5 game. */
    private fun states(lineup: Pair<List<String>, List<String>>, n: Int): List<GameState> {
        val (red, blue) = lineup
        val s = TestData.engine.newGame(
            AiHarness.setup(red, blue, AiLevel.NORMAL, AiLevel.NORMAL, Team.RED),
        ) as EngineSession
        val players = mapOf(Team.RED to AiPlayers.deterministic(5, 1L), Team.BLUE to AiPlayers.deterministic(5, 2L))
        val out = ArrayList<GameState>()
        while (out.size < n) {
            val d = s.pending ?: break
            out += s.state
            s.answer(d.id, players.getValue(d.team).choose(s, d))
        }
        return out
    }

    companion object {
        /** Features that depend on where on the board things stand. */
        private val GEOMETRY = listOf(
            "presence", "fightDistance", "inBattleZone", "laneProgress", "exposure", "opportunity", "Within",
            "nearestEnemy", "killThreats", "minionModifier", "pushPotential", "tunedScore", "term:LANE",
            "term:PRESENCE", "term:EXPOSURE", "term:OPPORTUNITY",
        )

        val LINEUPS: List<Pair<List<String>, List<String>>> = listOf(
            listOf("wasp", "xargatha") to listOf("arien", "brogan"),
            listOf("gydion", "razzle", "snorri") to listOf("tali", "widget", "cutter"),
            listOf("bain", "min", "nebkher") to listOf("trinkets", "whisper", "mrak"),
        )
    }
}
