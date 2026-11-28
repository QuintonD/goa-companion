package dev.quinton.goa.core.game.ai.hybrid

import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.AnswerResult
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.ai.AiHarness
import dev.quinton.goa.core.game.ai.heuristic.HContext
import dev.quinton.goa.core.game.ai.heuristic.HeroPlaybooks
import dev.quinton.goa.core.game.ai.heuristic.HeuristicAi
import dev.quinton.goa.core.game.ai.heuristic.HeuristicProfile
import dev.quinton.goa.core.game.ai.heuristic.planFacts
import dev.quinton.goa.core.game.engine.EngineSession
import org.junit.Test

/**
 * Opt-in (Track V, no games played): `AI_PLAN_REPLAY=1` replays the decision set's stored games (2v2 and 3v3) and at
 * every card play of the computer counts the team plan's facts (plan, jobs, Life race) and whether h9's card differs
 * with the plan on and off. A diagnostic of where the plan acts, by format.
 */
class PlanReplayTest {
    @Test
    fun `where the plan acts`() {
        if (System.getenv("AI_PLAN_REPLAY") == null) return
        val counts = sortedMapOf<String, Int>()
        fun bump(k: String) {
            counts[k] = (counts[k] ?: 0) + 1
        }
        val on = HeuristicAi(9, 1L)
        val off = HeuristicAi(9, 1L, tune = mapOf("plan" to 0.0))
        for (g in DecisionSetTest().load()) {
            val es = TestData.engine.newGame(
                AiHarness.setup(g.red, g.blue, AiLevel.NORMAL, AiLevel.NORMAL, g.coin, g.format.length, g.format.mapId),
            ) as EngineSession
            val f = g.format.label
            for (i in g.answers) {
                val d = es.pending ?: break
                if (d.kind == DecisionKind.PLAY_CARD && d.options.size > 1) {
                    val ctx = HContext(es, d, d.team, HeuristicProfile.of(9))
                    val facts = planFacts(ctx, d.seat)
                    bump("$f n")
                    for (k in facts) bump("$f $k")
                    val base = HeroPlaybooks.forHero(d.seat).style
                    val s = ctx.style(d.seat)
                    val shift = when {
                        s.aggression > base.aggression + 0.02 -> "+"
                        s.aggression < base.aggression - 0.02 -> "-"
                        else -> "="
                    }
                    bump("$f aggr$shift")
                    if (on.choose(es, d) != off.choose(es, d)) {
                        bump("$f differ")
                        for (k in facts) bump("$f differ.$k")
                    }
                }
                check(es.answer(d.id, d.options[i]) is AnswerResult.Accepted)
            }
        }
        for ((k, v) in counts) println("PLAN_REPLAY $k $v")
    }
}
