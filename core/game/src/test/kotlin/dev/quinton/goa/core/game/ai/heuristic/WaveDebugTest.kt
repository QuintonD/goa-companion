package dev.quinton.goa.core.game.ai.heuristic

import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.AnswerResult
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.ai.AiHarness
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

/**
 * Track W diagnostics, opt-in: `AI_W_DEBUG=2504R:6:3:wuk:CHOOSE_UNIT` plays that game of [WaveAuditTest] (h9 with the
 * wave layer off on both sides, the baseline's course) to the first such question, then for every option plays the
 * rest of the turn with h9 for every seat on a copy and prints the end of turn's [HEval] split into terms (the
 * lane, Life, coins and levels, the heroes' other terms), so one can see why a look prefers an option.
 */
class WaveDebugTest {
    @Test
    fun `explain one question`() {
        val spec = System.getenv("AI_W_DEBUG") ?: return
        for (one in spec.split(",")) explain(one.trim())
    }

    private fun explain(spec: String) {
        val (game, round, turn, seat, kind) = spec.split(":")
        val i = game.dropLast(1).toInt()
        val aRed = game.last() == 'R'
        val all = AiHarness.mixedLineups(i + 1, size = 2)
        val (red, blue) = all[i]
        val coin = if (i % 2 == 0) Team.RED else Team.BLUE
        val seed = (50_000L + i) * 2
        val base = mapOf("wave" to 0.0)
        val pa = HeuristicAi(9, if (aRed) seed else seed + 1, tune = base)
        val pb = HeuristicAi(9, if (aRed) seed + 1 else seed, tune = base)
        var found: Pair<EngineSession, Decision>? = null
        AiHarness.play(
            AiHarness.setup(red, blue, AiLevel.NORMAL, AiLevel.NORMAL, coin),
            mapOf(Team.RED to if (aRed) pa else pb, Team.BLUE to if (aRed) pb else pa),
            observe = { s, d ->
                if (found == null && s.state.round == round.toInt() && s.state.turn == turn.toInt() &&
                    d.seat == seat && d.kind.name == kind
                ) {
                    found = s.forkSession() to d
                }
            },
        )
        val (s, d) = found ?: return println("WDBG $spec not reached")
        println("WDBG $spec options ${d.options}")
        for (o in d.options) {
            val f = s.forkSession()
            check(f.answer(d.id, o) is AnswerResult.Accepted)
            val players = (red + blue).associateWith { HeuristicAi(9, seed, tune = mapOf("wave" to 1.0)) }
            val key = f.state.round * 8 + f.state.turn
            var n = 0
            while (n++ < 300) {
                val q = f.pending ?: break
                if (f.state.round * 8 + f.state.turn != key || q.kind == DecisionKind.PLAY_CARD) break
                f.answer(q.id, players.getValue(q.seat).choose(f, q))
            }
            println("WDBG   $o -> ${terms(f, d.team)}")
        }
    }

    private fun terms(f: EngineSession, team: Team): String {
        fun eval(tune: Map<String, Double>): Double {
            val ctx = HContext(f, f.pending, team, HeuristicProfile.of(9).copy(tune = tune))
            return HEval.evaluate(ctx, team)
        }
        val all = eval(emptyMap())
        val noLane = eval(mapOf("lane" to 0.0))
        val st = f.state
        val lanes = st.battleZones.entries.joinToString { (lane, bz) ->
            "$bz R${st.minions.count { it.lane == lane && it.team == Team.RED && it.at != null }}" +
                " B${st.minions.count { it.lane == lane && it.team == Team.BLUE && it.at != null }} w${st.wavesLeft[lane]}"
        }
        val heroes = st.heroes.joinToString(" ") { h ->
            "${h.id}(${h.team.name[0]}) c${h.coins} L${h.level} hand ${h.handSize} at ${h.at?.let { "${it.q},${it.r}" }}"
        }
        return "HEval %.1f (lane %.1f, rest %.1f) life R%d B%d | %s | %s".format(
            all,
            all - noLane,
            noLane,
            st.lifeCounters[Team.RED],
            st.lifeCounters[Team.BLUE],
            lanes,
            heroes,
        )
    }
}
