package dev.quinton.goa.core.game.ai.learned

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.ai.AiHarness
import dev.quinton.goa.core.game.ai.AiPlayers
import dev.quinton.goa.core.game.ai.Evaluator
import dev.quinton.goa.core.game.ai.Features
import dev.quinton.goa.core.game.ai.LearnedNet
import dev.quinton.goa.core.game.ai.LearnedWeights
import dev.quinton.goa.core.game.ai.NetEvaluator
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
        Features(Evaluator(TestData.engine.data)).extract(start(LINEUPS[0], Team.RED), x)
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
        val features = Features(Evaluator(TestData.engine.data))
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
        println("LEARNED evaluation: net ${"%.1f".format(netNs / 1000)} µs, hand ${"%.1f".format(handNs / 1000)} µs per position (3v3)")
        // A playout is 0.1–30 ms on the desktop (AI.md "Determinism"); loose for shared machines.
        assertThat(netNs).isLessThan(2_000_000.0)
    }

    private fun start(lineup: Pair<List<String>, List<String>>, coin: Team): GameState {
        val (red, blue) = lineup
        return (TestData.engine.newGame(AiHarness.setup(red, blue, AiLevel.NORMAL, AiLevel.NORMAL, coin)) as EngineSession).state
    }

    /** States along a seeded strength-5 game. */
    private fun states(lineup: Pair<List<String>, List<String>>, n: Int): List<GameState> {
        val (red, blue) = lineup
        val s = TestData.engine.newGame(AiHarness.setup(red, blue, AiLevel.NORMAL, AiLevel.NORMAL, Team.RED)) as EngineSession
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
