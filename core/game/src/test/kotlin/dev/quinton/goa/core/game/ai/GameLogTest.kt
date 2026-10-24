package dev.quinton.goa.core.game.ai

import dev.quinton.goa.core.game.AiLevel
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

/**
 * Opt-in: `AI_LOG=RED_LEVEL:BLUE_LEVEL[:seed[:lineup]]` prints one game's log, to read how a level
 * plays. The line-up is an index into the first-game line-ups, `m<i>` for the i-th mixed line-up, or
 * four hero ids joined by `+` (Red, Red, Blue, Blue). `AI_LOG_DECISIONS` also prints every choice.
 */
class GameLogTest {
    @Test
    fun `print one game`() {
        val spec = System.getenv("AI_LOG")?.split(":") ?: return
        val red = AiLevel.valueOf(spec[0])
        val blue = AiLevel.valueOf(spec[1])
        val seed = spec.getOrNull(2)?.toLong() ?: 1L
        val l = spec.getOrNull(3) ?: "0"
        val (r, b) = when {
            "+" in l -> l.split("+").let { it.take(2) to it.drop(2) }
            l.startsWith("m") -> AiHarness.mixedLineups(l.drop(1).toInt() + 1).last()
            else -> AiHarness.LINEUPS[l.toInt()]
        }
        val s = TestData.engine.newGame(AiHarness.setup(r, b, red, blue, Team.RED))
        val players = mapOf(
            Team.RED to AiPlayers.deterministic(red, seed),
            Team.BLUE to AiPlayers.deterministic(blue, seed + 1),
        )
        val decisions = System.getenv("AI_LOG_DECISIONS") != null
        // AI_LOG_VALUES: Hard-level playout values of every option of card plays and actions.
        val values = System.getenv("AI_LOG_VALUES") != null
        val searcher = Searcher(Evaluator(TestData.engine.data), AiPlayers.profile(AiLevel.HARD))
        println("LOG Red ${r.joinToString()} ($red) vs Blue ${b.joinToString()} ($blue)")
        var printed = 0
        var n = 0
        while (n < 8000) {
            val d = s.pending ?: break
            val c = players.getValue(d.team).choose(s, d)
            if (decisions && d.options.size > 1) println("LOG   ? ${d.seat} ${d.kind} ${d.prompt} -> $c")
            if (values && d.options.size > 1 && d.kind in VALUED) {
                val a = searcher.analyze(s as EngineSession, d, 7L + d.id, { false }, Long.MAX_VALUE)
                a?.options?.sortedByDescending { it.mean }?.forEach {
                    println("LOG       ${"%7.2f".format(it.mean)} ±${"%.1f".format(it.sd)} ${it.choice}")
                }
            }
            s.answer(d.id, c)
            for (e in s.events.drop(printed)) println("LOG ${e.text}")
            printed = s.events.size
            n++
        }
        val v = s.view(null)
        println("LOG winner ${v.winner} ${v.winReason} round ${v.round}")
    }

    companion object {
        private val VALUED = setOf(DecisionKind.PLAY_CARD, DecisionKind.CHOOSE_ACTION)
    }
}
